"""Mousike — Sound In motion. Run: python main.py"""

from __future__ import annotations
import asyncio, re, time, uuid, difflib, unicodedata, hashlib, os
from pathlib import Path
from urllib.parse import urlparse
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator
from fastapi.responses import (
    FileResponse,
    StreamingResponse,
    Response,
    RedirectResponse,
)
import httpx, uvicorn, yt_dlp
import shutil, tempfile
from urllib.parse import quote
from starlette.background import BackgroundTask
from contextlib import asynccontextmanager
from infrastructure import BoundedCache, SEARCH_CACHE, run_worker, singleflight
from security import SecurityMiddleware
from runtime_support import js_runtime_config, ffmpeg_executable, js_runtime_available
from pot_support import pot_options, pot_status
from discovery import interpret_query, ranked_search, suggestions
from embeddings import ENCODER
from lyrics import local_lyrics
from music_semantics import artist_for, NOISE

# Optional local .env is read before model/service configuration. Never served.
try:
    from dotenv import load_dotenv

    load_dotenv(Path(__file__).resolve().parent / ".env")
except ImportError:
    pass


@asynccontextmanager
async def lifespan(app):
    warm = asyncio.create_task(asyncio.to_thread(ENCODER.warm))
    yield
    if not warm.done():
        warm.cancel()


FLIGHTS = {}


ROOT = Path(__file__).resolve().parent
app = FastAPI(title="Mousike — Sound In motion", version="3.0.0", lifespan=lifespan)
app.mount("/assets", StaticFiles(directory=ROOT / "assets"), name="assets")
LIMIT = asyncio.Semaphore(3)
CACHE = BoundedCache(512)
app.add_middleware(SecurityMiddleware)
TTL = 600
VIDEO_ID = re.compile(r"^[a-zA-Z0-9_-]{11}$")
HEADERS = {
    "User-Agent": "MousikeMusic/4.0 (local private music discovery client; https://github.com/yt-dlp/yt-dlp)",
    "Accept": "application/json, image/*;q=0.9, */*;q=0.7",
}
MB_LOCK = asyncio.Lock()
LAST_MB = 0.0
UUID_RE = re.compile(r"^[a-f0-9-]{36}$")


def ydl_options(flat=False):
    return {
        **pot_options(),
        "js_runtimes": js_runtime_config(),
        "remote_components": {"ejs:github"},
        "source_address": "0.0.0.0",
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": flat,
        "noplaylist": True,
        "socket_timeout": 10,
        "retries": 1,
        "fragment_retries": 1,
        "cachedir": False,
        "ignoreerrors": True,
    }


@app.get("/api/pot-status")
async def pot_health():
    return pot_status()


def normalize(item):
    if not item:
        return None
    vid = str(item.get("id") or "")
    if not VIDEO_ID.fullmatch(vid):
        u = item.get("url", "")
        match = re.search(r"(?:v=|youtu\.be/)([\w-]{11})", u)
        if not match:
            return None
        vid = match.group(1)
    thumbs = item.get("thumbnails") or []
    image = next((t.get("url") for t in reversed(thumbs) if t.get("url")), None)
    result = {
        "id": vid,
        "title": item.get("title") or "Unknown title",
        "artist": item.get("channel") or item.get("uploader") or "Unknown artist",
        "duration": item.get("duration") or 0,
        "image": image or f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg",
        "url": f"https://www.youtube.com/watch?v={vid}",
        "views": item.get("view_count") or 0,
        "kind": "long_video"
        if (
            (item.get("duration") or 0) > 480
            or bool(
                re.search(
                    r"full\s+album|complete\s+album|full\s+soundtrack|jukebox|all\s+songs",
                    item.get("title") or "",
                    re.I,
                )
            )
        )
        else "video",
        "album_candidate": (item.get("duration") or 0) > 480,
        "requires_review": True if (item.get("duration") or 0) > 480 else False,
    }
    result["raw_title"] = result["title"]
    result["publisher"] = result["artist"]
    result["performer"] = str(
        item.get("artist") or item.get("creator") or artist_for(result)
    )[:100]
    result["performer_source"] = (
        "metadata"
        if item.get("artist") or item.get("creator")
        else "title"
        if result["performer"].casefold() in result["raw_title"].casefold()
        else "channel"
    )
    result["artist"] = result["performer"]
    if result["kind"] != "long_video":
        clean = NOISE.sub(" ", result["title"])
        clean = re.sub(r"\(\s*\)|\[\s*\]", " ", clean)
        for sep in (" - ", " — ", " – ", " | "):
            if clean.casefold().startswith(result["performer"].casefold() + sep):
                clean = clean[len(result["performer"] + sep) :]
        result["title"] = re.sub(r"\s+", " ", clean).strip(" -–—|") or result["title"]
        result["canonical_title"] = result["title"]
    if item.get("album"):
        result["album"] = str(item["album"])[:180]
    if item.get("isrc"):
        result["isrc"] = str(item["isrc"])[:24]
    return result


def do_search(q, count):
    q = " ".join(str(q).split())[:120]
    count = max(1, min(40, int(count)))
    if not q:
        return []

    def work():
        opts = ydl_options(flat=True)
        with yt_dlp.YoutubeDL(opts) as ydl:
            result = ydl.extract_info(
                f"ytsearch{count}:{q}", download=False
            ) or {}

        return [
            x for entry in result.get("entries", [])
            if (x := normalize(entry))
        ]

    return SEARCH_CACHE.call((q.casefold(), count), work)


async def search_tracks(q, count=18):
    return await run_worker(do_search, q, count)


def do_info(vid):
    with yt_dlp.YoutubeDL(ydl_options()) as ydl:
        info = ydl.extract_info(
            f"https://www.youtube.com/watch?v={vid}", download=False
        )
    if not info:
        raise ValueError("Media unavailable")
    item = normalize(info)
    formats = [
        f
        for f in info.get("formats", [])
        if f.get("url")
        and f.get("acodec") not in (None, "none")
        and f.get("vcodec") == "none"
        and urlparse(f["url"]).scheme == "https"
    ]
    formats.sort(
        key=lambda f: (f.get("ext") in ("m4a", "mp4"), f.get("abr") or 0), reverse=True
    )
    if not formats:
        raise ValueError("No playable audio format found")
    fmt = formats[0]
    return {
        "track": item,
        "url": fmt["url"],
        "mime": "audio/mp4" if fmt.get("ext") in ("m4a", "mp4") else "audio/webm",
        "headers": fmt.get("http_headers") or {},
    }


def cached(key):
    found = CACHE.get(key)
    return found[1] if found and time.time() - found[0] < 1800 else None


def remember(key, value):
    CACHE[key] = (time.time(), value)
    return value


async def mb_get(path, params=None):
    global LAST_MB
    key = ("musicbrainz", path, tuple(sorted((params or {}).items())))
    hit = cached(key)
    if hit is not None:
        return hit
    async with MB_LOCK:
        hit = cached(key)
        if hit is not None:
            return hit
        delay = 1.1 - (time.monotonic() - LAST_MB)
        if delay > 0:
            await asyncio.sleep(delay)
        LAST_MB = time.monotonic()
        async with httpx.AsyncClient(timeout=12, follow_redirects=False) as client:
            response = await client.get(
                "https://musicbrainz.org/ws/2/" + path,
                params={**(params or {}), "fmt": "json"},
                headers=HEADERS,
            )
            response.raise_for_status()
            return remember(key, response.json())


def credit(credits):
    return (
        "".join(
            x.get("name", x.get("artist", {}).get("name", "")) + x.get("joinphrase", "")
            for x in credits or []
            if isinstance(x, dict)
        ).strip()
        or "Various Artists"
    )


def album_card(r):
    rid = r.get("id", "")
    if not UUID_RE.fullmatch(rid):
        return None
    return {
        "id": rid,
        "kind": "album",
        "title": r.get("title", "Unknown album"),
        "artist": credit(r.get("artist-credit")),
        "year": str(r.get("date", ""))[:4],
        "tracks": r.get("track-count") or r.get("count") or 0,
        "image": f"https://coverartarchive.org/release/{rid}/front-250",
        "score": int(r.get("score") or 0),
    }


@app.get("/api/albums")
async def albums(
    q: str = Query(min_length=1, max_length=120), limit: int = Query(12, ge=1, le=20)
):
    key = ("albums", q.lower().strip(), limit)
    if (found := cached(key)) is not None:
        return {"albums": found}
    try:
        data = await mb_get("release/", {"query": q, "limit": limit * 2})
        output = []
        seen = set()
        for r in data.get("releases", []):
            card = album_card(r)
            if (
                card
                and (r.get("release-group") or {}).get("primary-type", "Album")
                in ("Album", "EP", "Single")
                and (card["title"].casefold(), card["artist"].casefold()) not in seen
            ):
                seen.add((card["title"].casefold(), card["artist"].casefold()))
                output.append(card)
            if len(output) >= limit:
                break
        return {"albums": remember(key, output)}
    except Exception as e:
        raise HTTPException(502, f"Album catalog unavailable: {str(e)[:130]}")


@app.get("/api/album/{rid}")
async def album(rid: str):
    if not UUID_RE.fullmatch(rid):
        raise HTTPException(400, "Invalid album ID")
    if (found := cached(("album", rid))) is not None:
        return found
    try:
        r = await mb_get(
            "release/" + rid, {"inc": "recordings+artist-credits+release-groups"}
        )
        card = album_card(r)
        if not card:
            raise ValueError("Album not found")
        tracks = []
        for medium in r.get("media", []):
            for tr in medium.get("tracks", []):
                title = tr.get("title") or tr.get("recording", {}).get("title")
                if not title:
                    continue
                artist = credit(
                    tr.get("artist-credit")
                    or tr.get("recording", {}).get("artist-credit")
                    or r.get("artist-credit")
                )
                tracks.append(
                    {
                        "id": "mb:" + str(tr.get("id") or uuid.uuid4()),
                        "kind": "song",
                        "title": title,
                        "artist": artist,
                        "duration": round(
                            (
                                tr.get("length")
                                or tr.get("recording", {}).get("length")
                                or 0
                            )
                            / 1000
                        ),
                        "image": card["image"],
                        "album": card["title"],
                        "unresolved": True,
                        "recording_id": tr.get("recording", {}).get("id"),
                        "disc": medium.get("position", 1),
                        "position": tr.get("position", len(tracks) + 1),
                    }
                )
        card["tracks"] = len(tracks)
        return remember(("album", rid), {"album": card, "tracks": tracks})
    except Exception as e:
        raise HTTPException(502, f"Album unavailable: {str(e)[:130]}")


def do_playlist(pid):
    options = ydl_options(flat=True)
    options.update(noplaylist=False, playlistend=150)
    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(
            "https://www.youtube.com/playlist?list=" + pid, download=False
        )
    if not info:
        raise ValueError("Playlist unavailable")
    tracks = [t for x in info.get("entries", []) if (t := normalize(x))]
    return {
        "album": {
            "id": pid,
            "kind": "playlist",
            "title": info.get("title") or "Playlist",
            "artist": info.get("uploader") or "YouTube",
            "image": (tracks[0]["image"] if tracks else ""),
            "tracks": len(tracks),
        },
        "tracks": tracks,
    }


@app.get("/api/playlist/{pid}")
async def playlist(pid: str):
    if not re.fullmatch(r"[A-Za-z0-9_-]{10,90}", pid):
        raise HTTPException(400, "Invalid playlist ID")
    if (found := cached(("playlist", pid))) is not None:
        return found
    try:
        async with LIMIT:
            data = await run_worker(do_playlist, pid, media=True)
        return remember(("playlist", pid), data)
    except Exception as e:
        raise HTTPException(502, f"Playlist unavailable: {str(e)[:140]}")


@app.get("/api/resolve")
async def resolve(
    title: str = Query(min_length=1, max_length=180),
    artist: str = Query("", max_length=180),
    duration: int = Query(0, ge=0, le=5000),
):
    key = ("resolve", title.casefold(), artist.casefold(), duration)
    if (found := cached(key)) is not None:
        return found

    def work():
        search_artist = "" if signature(artist) in BLOCKED_ARTIST else artist
        candidates = do_search(f"{search_artist} {title} official audio song", 12)
        if not candidates:
            raise ValueError("No playable match")

        def rank(t):
            text = (t["title"] + " " + t["artist"]).lower()
            similarity = difflib.SequenceMatcher(
                None, title.lower(), t["title"].lower()
            ).ratio()
            artist_tokens = [
                x for x in re.findall(r"\w+", artist.lower()) if len(x) > 2
            ]
            artist_hits = sum(x in text for x in artist_tokens)
            penalty = (
                min(
                    abs((t.get("duration") or duration) - duration) / max(duration, 1),
                    3,
                )
                if duration and t.get("duration")
                else 0
            )
            extra = (
                2
                if re.search(
                    r"full album|1 hour|compilation|reaction|tutorial|cover by", text
                )
                else 0
            )
            return similarity * 6 + artist_hits * 0.85 - penalty * 3 - extra

        candidates = [
            t
            for t in candidates
            if not (
                t.get("duration")
                and t["duration"] > 480
                and (not duration or duration < 480)
            )
            and not (duration and t.get("duration") and t["duration"] > duration * 1.8)
        ]
        if not candidates:
            raise ValueError(
                "Only long-form videos were found; individual track unavailable"
            )
        return max(candidates, key=rank)

    try:
        async with LIMIT:
            track = await run_worker(work, media=True)
        return remember(key, {"track": track})
    except Exception as e:
        raise HTTPException(502, f"Could not find this track: {str(e)[:130]}")


# User details are browser-owned. This backend never saves taste profiles or keys.
PROFILE_ID = re.compile(r"^[a-zA-Z0-9_-]{8,72}$")


class TasteProfile(BaseModel):
    client_id: str = Field(min_length=8, max_length=72)
    name: str = Field(default="Listener", max_length=48)
    avatar: str = Field(default="🎧", max_length=30)
    avatar_data: str = Field(default="", max_length=250000)
    country: str = Field(default="India", max_length=65)
    languages: list[str] = Field(default_factory=list, max_length=8)
    artists: list[str] = Field(default_factory=list, max_length=15)
    genres: list[str] = Field(default_factory=list, max_length=12)
    moods: list[str] = Field(default_factory=list, max_length=10)
    decades: list[str] = Field(default_factory=list, max_length=5)
    discovery: int = Field(default=35, ge=0, le=100)
    palette: str = Field(default="auto", max_length=20)
    updated_at: float = 0


@app.get("/player.html")
def player_file():
    return FileResponse(ROOT / "player.html")


@app.get("/settings.html")
def settings_file():
    return FileResponse(ROOT / "settings.html")


@app.get("/onboarding.html")
def onboarding_file():
    return FileResponse(ROOT / "onboarding.html")


@app.post("/api/profile")
async def validate_profile(profile: TasteProfile):
    """Compatibility endpoint; validation only, no persistence."""
    return {"ok": True, "storage": "browser-only"}


@app.get("/api/profile/{client_id}")
async def get_profile(client_id: str):
    raise HTTPException(404, "Profiles are stored in your browser, not on the server")


class PersonalRecommendationRequest(BaseModel):
    profile: dict = Field(default_factory=dict)
    history: list[str] = Field(default_factory=list, max_length=15)


@app.post("/api/recommendations")
async def recommendations(req: PersonalRecommendationRequest):
    """Compatibility metadata query plan; balanced across all preferences."""
    from recommender import music_insights, query_plan

    ins = music_insights(req.profile, [], {})
    plan = query_plan(ins)
    if not plan:
        plan = ["international indie pop new music official audio"]
    headings = [
        (
            "for-you",
            "A little bit of everything",
            "Balanced music for your selected tastes",
        ),
        (
            "discover",
            "Beyond your usual",
            "Explore neighboring genres and fresh performers",
        ),
        ("mood", "Your mood rotation", "A sound to match your selected moods"),
        ("artist", "Familiar voices", "An occasional favorite, not the whole feed"),
        (
            "region",
            "Global frequencies",
            "Discover music beyond your most played artist",
        ),
    ]
    return {
        "sections": [
            {
                "id": i,
                "title": title,
                "description": sub,
                "query": plan[n % len(plan)],
                "limit": 8,
            }
            for n, (i, title, sub) in enumerate(headings)
        ],
        "profile_found": bool(
            ins["profile"]["artists"]
            or ins["profile"]["genres"]
            or ins["profile"]["languages"]
        ),
        "strategy": "semantic-diverse-query-plan-v3",
    }


@app.get("/")
def home():
    return FileResponse(ROOT / "index.html")


@app.get("/api/health")
def health():
    return {"ok": True, "service": "Mousike"}


@app.get("/api/search")
async def search(
    q: str = Query(min_length=1, max_length=120), limit: int = Query(18, ge=1, le=40)
):
    key = ("search", q.strip().lower(), limit)
    if key in CACHE and time.time() - CACHE[key][0] < TTL:
        return {"tracks": ranked_search(CACHE[key][1], q), **interpret_query(q)}
    try:
        async with LIMIT:
            tracks = await search_tracks(interpret_query(q)["expanded_query"], limit)
        CACHE[key] = (time.time(), tracks)
        return {"tracks": ranked_search(tracks, q), **interpret_query(q)}
    except Exception as e:
        raise HTTPException(502, f"Search unavailable: {str(e)[:180]}")


@app.get("/api/track/{vid}")
async def track(vid: str):
    if not VIDEO_ID.fullmatch(vid):
        raise HTTPException(400, "Invalid video ID")
    try:
        async with LIMIT:
            result = await run_worker(do_info, vid, media=True)
        return result["track"]
    except Exception as e:
        raise HTTPException(502, f"Track unavailable: {str(e)[:160]}")


async def stream_info(vid, refresh=False):
    key = ("stream", vid)
    if not refresh:
        item = CACHE.get(key)
        if item and time.time() - item[0] < 600:
            return item[1]

    async def work():
        result = await run_worker(do_info, vid, media=True)
        remember(key, result)
        return result

    return await singleflight(FLIGHTS, ("stream-info", vid), work)


@app.get("/api/source/{vid}")
async def source_ready(vid: str, refresh: bool = False):
    """Warm source metadata only. No bytes downloaded by this endpoint."""
    if not VIDEO_ID.fullmatch(vid):
        raise HTTPException(400, "Invalid video ID")
    try:
        data = await stream_info(vid, refresh=refresh)
        return {"ready": True, "mime": data["mime"], "track": data["track"]}
    except Exception:
        raise HTTPException(502, "This recording is temporarily unavailable")


@app.api_route("/api/stream/{vid}", methods=["GET", "HEAD"])
async def stream(vid: str, request: Request):
    if not VIDEO_ID.fullmatch(vid):
        raise HTTPException(400, "Invalid video ID")
    range_header = request.headers.get("range", "")
    if range_header and not re.fullmatch(r"bytes=(?:\d+-\d*|-\d+)", range_header):
        raise HTTPException(416, "Only a single byte range is supported")
    upstream = client = None
    for attempt in range(2):
        try:
            data = await stream_info(vid, refresh=bool(attempt))
            headers = {
                **HEADERS,
                **data.get("headers", {}),
                "Accept-Encoding": "identity",
            }
            if range_header:
                headers["Range"] = range_header
            client = httpx.AsyncClient(
                timeout=httpx.Timeout(20, read=60), follow_redirects=True
            )
            upstream = await client.send(
                client.build_request(request.method, data["url"], headers=headers),
                stream=True,
            )
            if upstream.status_code in (401, 403, 404, 410) and attempt == 0:
                await upstream.aclose()
                await client.aclose()
                CACHE.pop(("stream", vid), None)
                continue
            if upstream.status_code == 416:
                await upstream.aclose()
                await client.aclose()
                raise HTTPException(416, "Requested byte range is unavailable")
            if upstream.status_code not in (200, 206):
                raise ValueError("Source unavailable")
            break
        except HTTPException:
            raise
        except Exception:
            if upstream:
                await upstream.aclose()
            if client:
                await client.aclose()
            raise HTTPException(
                502,
                "Playback source unavailable. Retry this song or choose another recording.",
            )
    allowed = (
        "content-range",
        "content-length",
        "accept-ranges",
        "etag",
        "last-modified",
    )
    response_headers = {
        k: v for k, v in upstream.headers.items() if k.lower() in allowed
    }
    response_headers["Cache-Control"] = "private, max-age=60"
    if request.method == "HEAD":
        await upstream.aclose()
        await client.aclose()
        return Response(
            status_code=upstream.status_code,
            headers=response_headers,
            media_type=data["mime"],
        )

    async def chunks():
        try:
            async for chunk in upstream.aiter_bytes(65536):
                if await request.is_disconnected():
                    break
                yield chunk
        finally:
            await upstream.aclose()
            await client.aclose()

    return StreamingResponse(
        chunks(),
        status_code=upstream.status_code,
        media_type=upstream.headers.get("content-type", data["mime"]),
        headers=response_headers,
    )


# ───────────────────────── HYBRID ALBUM ENGINE ─────────────────────────────
# Virtual cuts use timestamps in one original source: no download/re-encode.
# MusicBrainz metadata is searched separately, never used to fabricate timestamps.
MODES = ("auto", "split", "metadata")
TIMESTAMP = re.compile(
    r"(?<![\d:])(?:\(?\[?)(?P<time>(?:\d{1,2}:)?\d{1,2}:\d{2})(?:\]?\)?)(?!\d)"
)
ALBUM_WORDS = re.compile(
    r"(?i)\b(?:full\s+album|complete\s+album|official\s+album|full\s+soundtrack|full\s+audio|audio\s+jukebox|jukebox|all\s+songs|nonstop|non-stop|lyrics|hd|4k|official|hq|audio|video|playlist|songs)\b"
)
BLOCKED_ARTIST = {"unknown artist", "various artists", "topic", "youtube", "music"}


def seconds(stamp):
    parts = [int(p) for p in stamp.split(":")]
    if parts[-1] >= 60 or (len(parts) == 3 and parts[-2] >= 60):
        return -1
    if len(parts) == 2:
        return parts[0] * 60 + parts[1]
    if len(parts) == 3:
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    return -1


def title_clean(s):
    s = re.sub(
        r"\([^)]*(?:official|lyrics|audio|full album|video|4k)[^)]*\)",
        " ",
        str(s or ""),
        flags=re.I,
    )
    s = re.sub(
        r"\[[^]]*(?:official|lyrics|audio|full album|video|4k)[^]]*\]",
        " ",
        s,
        flags=re.I,
    )
    s = ALBUM_WORDS.sub(" ", s)
    s = re.sub(r"[|•_]+", " ", s)
    return re.sub(r"\s+", " ", s).strip(" -–—:;.,")[:130]


def signature(text):
    text = unicodedata.normalize("NFKD", str(text or "")).casefold()
    text = "".join(c for c in text if not unicodedata.combining(c))
    return " ".join(re.findall(r"[\w]+", text))


def score_sim(a, b):
    a, b = signature(a), signature(b)
    return difflib.SequenceMatcher(None, a, b).ratio() if a and b else 0


def parse_description_chapters(description, duration):
    """Recognize creator-published 00:00 Song or Song - 00:00 lines."""
    candidates = []
    for ln in str(description or "").splitlines()[:320]:
        line = ln.strip().strip("|")
        if not 3 <= len(line) <= 170:
            continue
        ts = list(TIMESTAMP.finditer(line))
        if len(ts) != 1:
            continue
        m = ts[0]
        start = seconds(m.group("time"))
        if start < 0 or start >= duration:
            continue
        label = (line[: m.start()] + " " + line[m.end() :]).strip(" -–—:|•.\t#[]()")
        label = re.sub(
            r"^\s*(?:track|song|chapter)\s*\d+\s*[:.\-]?", "", label, flags=re.I
        ).strip()
        if not 2 <= len(label) <= 95:
            continue
        if re.search(
            r"(?i)^(https?://|subscribe|instagram|facebook|social|website|contact|click here|follow us|stream|download|copyright|listen on|spotify)",
            label,
        ):
            continue
        if not candidates or candidates[-1][0] != start:
            candidates.append((start, label))
    return candidates


def chapter_segments(info, source_video, artist, image):
    duration = float(info.get("duration") or 0)
    if duration < 481:
        return [], "none"
    explicit = []
    for chap in info.get("chapters") or []:
        start = float(chap.get("start_time") or 0)
        end = float(chap.get("end_time") or duration)
        name = str(chap.get("title") or "").strip()
        if name and 0 <= start < end <= duration + 2:
            explicit.append((start, name, end))
    explicit.sort(key=lambda t: t[0])
    valid = []
    for st, name, end in explicit:
        if valid and st < valid[-1][0] + 15:
            continue
        valid.append((st, name, end))
    chapter_source = "youtube_chapters"
    if len(valid) < 2:
        entries = parse_description_chapters(info.get("description") or "", duration)
        entries.sort(key=lambda t: t[0])
        valid = []
        for st, name in entries:
            if valid and st <= valid[-1][0] + 15:
                continue
            valid.append((st, name, duration))
        chapter_source = "description_timestamps"
    if len(valid) < 2:
        return [], "none"
    if valid[0][0] > 150:
        return [], "none"  # cannot confidently identify the beginning
    tracks = []
    for n, (start, title, end_hint) in enumerate(valid):
        end = (
            min(duration, valid[n + 1][0])
            if n + 1 < len(valid)
            else min(duration, end_hint)
        )
        if end - start < 18:
            continue
        # Keep all chapters visible, including an Intro, but do not imply audio analysis.
        clean = re.sub(
            r"(?i)^\s*(?:track|chapter|song)\s*\d+\s*[:.\-–]?\s*", "", title
        ).strip()
        clean = re.sub(r"^\s*\d{1,3}[.)-]\s*", "", clean).strip() or title
        tracks.append(
            {
                "id": f"seg:{source_video}:{int(start)}",
                "kind": "song",
                "title": clean,
                "artist": artist,
                "duration": round(end - start, 2),
                "image": image,
                "source_video_id": source_video,
                "segment": True,
                "start": round(start, 2),
                "end": round(end, 2),
                "url": f"https://www.youtube.com/watch?v={source_video}&t={int(start)}s",
                "source": chapter_source,
                "confidence": "timestamped",
                "position": len(tracks) + 1,
            }
        )
    if len(tracks) < 2:
        return [], "none"
    # Some long *single* songs have verse/chorus chapters; those aren't album tracks.
    structural = re.compile(
        r"(?i)^(?:intro|outro|chorus|pre.?chorus|verse\s*\d*|bridge|hook|guitar solo|drum solo|credits|sponsor|subscribe|advertisement|ending|conclusion|breakdown)\b"
    )
    if sum(bool(structural.search(t["title"])) for t in tracks) * 2 >= len(tracks):
        return [], "none"
    return tracks, chapter_source


def video_meta_worker(vid):
    with yt_dlp.YoutubeDL(ydl_options()) as ydl:
        info = ydl.extract_info(
            f"https://www.youtube.com/watch?v={vid}", download=False
        )
    if not info:
        raise ValueError("Video metadata unavailable")
    result = {
        k: info.get(k)
        for k in (
            "id",
            "title",
            "artist",
            "album",
            "track",
            "creator",
            "uploader",
            "channel",
            "duration",
            "description",
            "chapters",
            "thumbnail",
            "thumbnails",
            "playlist_id",
            "release_year",
        )
    }
    result["description"] = str(result.get("description") or "")[:24000]
    return result


async def public_video_meta(vid):
    async def work():
        hit = cached(("video-meta", vid))
        if hit is not None:
            return hit
        value = await run_worker(video_meta_worker, vid, media=True)
        return remember(("video-meta", vid), value)

    return await singleflight(FLIGHTS, ("video-meta", vid), work)


def guess_album(info):
    title = str(info.get("album") or title_clean(info.get("title")) or "Unknown album")
    artist = str(info.get("artist") or info.get("creator") or "").strip()
    if not artist:
        # An uploader is not necessarily a performing artist: do not assume it is.
        artist = (
            str(info.get("uploader") or info.get("channel") or "Various Artists")
            .replace(" - Topic", "")
            .strip()
        )
    # Common upload-title separators often contain the album and performer.
    raw = info.get("title") or ""
    if not info.get("album") and re.search(r"\s+[-–|]\s+", raw):
        lhs, rhs = re.split(r"\s+[-–|]\s+", raw, maxsplit=1)
        if re.search(r"(?i)album|jukebox|soundtrack|all songs", rhs):
            title = title_clean(rhs) or title
            if lhs and not info.get("artist"):
                artist = title_clean(lhs)
        elif re.search(r"(?i)album|jukebox|soundtrack|all songs", lhs):
            title = title_clean(lhs) or title
    return title[:130], artist[:100]


async def catalog_for_upload(title, artist):
    cachekey = ("hybrid-catalog", signature(title), signature(artist))
    entry = CACHE.get(cachekey)
    if entry and time.time() - entry[0] < 1800:
        return entry[1]
    # Avoid MusicBrainz for titles too vague to match responsibly.
    if len(signature(title)) < 4:
        return None
    query = f'release:"{title}"'
    if (
        artist
        and signature(artist) not in BLOCKED_ARTIST
        and "various" not in artist.casefold()
    ):
        query += f' AND artist:"{artist}"'

    async def lookup(q):
        d = await mb_get("release/", {"query": q, "limit": 10})
        ranked = []
        for item in d.get("releases", []):
            album = album_card(item)
            if not album:
                continue
            title_score = score_sim(title, album["title"])
            artist_score = score_sim(artist, album["artist"])
            rank = (
                title_score * 0.84 + artist_score * 0.16
                if artist and signature(artist) not in BLOCKED_ARTIST
                else title_score
            )
            if title_score >= 0.72:
                ranked.append((rank, album))
        ranked.sort(key=lambda x: x[0], reverse=True)
        return ranked

    try:
        matches = await lookup(query)
        if not matches and "AND artist:" in query:
            matches = await lookup(f'release:"{title}"')
        if not matches or matches[0][0] < 0.73:
            return remember(cachekey, None)
        top = matches[0][1]
        data = await album(top["id"])
        return remember(cachekey, data)
    except Exception:
        return None


def map_metadata_songs(albumdata, src):
    art = albumdata["album"].get("image") or src["image"]
    return [
        {
            **tr,
            "image": art,
            "origin_video_id": src["id"],
            "lookup_source": "musicbrainz",
            "segment": False,
            "unresolved": True,
            "album": albumdata["album"]["title"],
        }
        for tr in albumdata["tracks"]
    ]


def chapter_songs_for_individual(tracks):
    return [
        {
            **tr,
            "id": f"lookup:{tr['source_video_id']}:{int(tr['start'])}",
            "segment": False,
            "unresolved": True,
            "origin_video_id": tr["source_video_id"],
            "lookup_source": tr["source"],
            "original_boundary": {"start": tr["start"], "end": tr["end"]},
        }
        for tr in tracks
    ]


async def hybrid_album_data(vid, mode="auto", album_title="", album_artist=""):
    key = ("hybrid", vid, mode, signature(album_title), signature(album_artist))
    if (value := cached(key)) is not None:
        return value
    # Single-flight: identical callers reuse the expensive metadata result.
    vk = ("video-meta", vid)
    data = cached(vk)
    if data is None:
        async with LIMIT:
            data = await public_video_meta(vid)
        remember(vk, data)
    if not data or not data.get("duration") or float(data.get("duration") or 0) <= 480:
        raise HTTPException(
            422,
            "This video is not longer than eight minutes. Use normal song playback.",
        )
    title, artist = guess_album(data)
    if album_title:
        title = album_title.strip()[:130]
    if album_artist:
        artist = album_artist.strip()[:100]
    art = data.get("thumbnail") or f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
    base = {
        "id": vid,
        "kind": "album_video",
        "title": title,
        "artist": artist,
        "duration": data["duration"],
        "image": art,
        "year": str(data.get("release_year") or ""),
        "origin": "youtube",
        "url": f"https://www.youtube.com/watch?v={vid}",
    }
    segments, source = chapter_segments(data, vid, artist, art)
    notes = []
    if not segments:
        notes.append(
            "No reliable track timestamps were found; never inventing song cuts."
        )
    if mode in ("auto", "split") and segments:
        album_info = {
            **base,
            "tracks": len(segments),
            "method": "split",
            "method_label": "Timestamp split",
            "timestamp_source": source,
            "confidence": "published_timestamps",
        }
        return remember(
            key,
            {
                "album": album_info,
                "tracks": segments,
                "mode": mode,
                "method": "split",
                "warnings": [
                    "Cuts are virtual chapter boundaries, not exported audio files."
                ],
                "details": {
                    "timestamp_source": source,
                    "detected_duration": data["duration"],
                    "cuts": [
                        {"title": s["title"], "start": s["start"], "end": s["end"]}
                        for s in segments
                    ],
                },
            },
        )
    if mode == "split":
        raise HTTPException(
            409,
            "No reliable chapter or description timestamps. Switch to Auto or Find individual songs.",
        )
    found = await catalog_for_upload(title, artist)
    if found and len(found.get("tracks", [])) >= 2:
        card = found["album"]
        tracks = map_metadata_songs(found, base)
        album_info = {
            **base,
            "title": card["title"],
            "artist": card["artist"],
            "image": card["image"] or art,
            "tracks": len(tracks),
            "year": card.get("year"),
            "fallback_image": art,
            "method": "metadata",
            "method_label": "Individual song search",
            "catalog_id": card["id"],
            "confidence": "catalog_match",
        }
        return remember(
            key,
            {
                "album": album_info,
                "tracks": tracks,
                "mode": mode,
                "method": "metadata",
                "warnings": notes
                + [
                    "Track durations are catalog estimates. Playback uses separately matched recordings."
                ],
                "details": {
                    "catalog": "MusicBrainz",
                    "track_count": len(tracks),
                    "matched_album": card["title"],
                    "detected_duration": data["duration"],
                },
            },
        )
    if segments and mode == "metadata":
        tracks = chapter_songs_for_individual(segments)
        base.update(
            {
                "tracks": len(tracks),
                "method": "metadata",
                "method_label": "Individual song search",
                "confidence": "timestamp_track_names",
            }
        )
        return remember(
            key,
            {
                "album": base,
                "tracks": tracks,
                "mode": mode,
                "method": "metadata",
                "warnings": [
                    "Using chapter titles to search for individual recordings; album catalog match was unavailable."
                ],
                "details": {
                    "catalog": "description/chapter titles",
                    "detected_duration": data["duration"],
                },
            },
        )
    raise HTTPException(
        404,
        "Could not identify a trustworthy tracklist. Try another album upload with chapters or a clearer album title.",
    )


@app.get("/api/album-video/{vid}")
async def album_video(
    vid: str,
    mode: str = Query("auto", pattern="^(auto|split|metadata)$"),
    album_title: str = Query("", max_length=130),
    album_artist: str = Query("", max_length=100),
):
    if not VIDEO_ID.fullmatch(vid):
        raise HTTPException(400, "Invalid video ID")
    try:
        return await hybrid_album_data(vid, mode, album_title, album_artist)
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(502, f"Album analysis unavailable: {str(e)[:160]}")


@app.get("/api/album-video/{vid}/art")
async def album_video_art(vid: str):
    if not VIDEO_ID.fullmatch(vid):
        raise HTTPException(400, "Invalid video ID")
    try:
        info = cached(("video-meta", vid))
        if info is None:
            async with LIMIT:
                info = await public_video_meta(vid)
            remember(("video-meta", vid), info)
        title, artist = guess_album(info)
        data = await catalog_for_upload(title, artist)
        if not data:
            return {"matched": False, "image": None}
        card = data["album"]
        return {
            "matched": True,
            "image": card["image"],
            "title": card["title"],
            "artist": card["artist"],
            "catalog_id": card["id"],
        }
    except Exception:
        return {"matched": False, "image": None}


@app.get("/api/inspect/{vid}")
async def inspect_video(vid: str):
    if not VIDEO_ID.fullmatch(vid):
        raise HTTPException(400, "Invalid video ID")
    try:
        vk = ("video-meta", vid)
        info = cached(vk)
        if info is None:
            async with LIMIT:
                info = await public_video_meta(vid)
            remember(vk, info)
        d = float(info.get("duration") or 0)
        segments, source = (
            chapter_segments(
                info, vid, *guess_album(info)[1:], info.get("thumbnail") or ""
            )
            if d > 480
            else ([], "none")
        )
        return {
            "video_id": vid,
            "duration": d,
            "long_video": d > 480,
            "detected_chapters": len(segments),
            "chapter_source": source,
            "suggested_mode": "split" if segments else "metadata",
        }
    except Exception as e:
        raise HTTPException(502, f"Inspection unavailable: {str(e)[:160]}")


# ─────────────── ARTIST DETAILS FOR THE PREMIUM ARTIST VIEW ───────────────
@app.get("/api/artist")
async def artist_details(name: str = Query(min_length=1, max_length=120)):
    """Lightweight, cached artist identity; does not invent verified badges or listener totals."""
    key = ("artist-profile", name.strip().casefold())
    if (hit := cached(key)) is not None:
        return hit
    try:
        result = await mb_get("artist/", {"query": name.strip(), "limit": 6})
        choices = result.get("artists", [])
        if not choices:
            return {"name": name, "country": "", "mbid": None}
        normalized = name.strip().casefold()
        best = max(
            choices,
            key=lambda a: (
                (a.get("name") or "").casefold() == normalized,
                a.get("score") or 0,
            ),
        )
        tags = [t.get("name", "") for t in best.get("tags", []) if t.get("name")][:5]
        related = []
        if tags:
            try:
                tag = tags[0].replace(chr(34), "")[:65]
                data = await mb_get("artist/", {"query": f'tag:"{tag}"', "limit": 9})
                related = [
                    a["name"]
                    for a in data.get("artists", [])
                    if a.get("name") and a.get("id") != best.get("id")
                ][:7]
            except Exception:
                pass
        return remember(
            key,
            {
                "tags": tags,
                "related": related,
                "description": best.get("disambiguation", "")[:200],
                "name": best.get("name") or name,
                "country": best.get("country") or "",
                "mbid": best.get("id"),
                "type": best.get("type", ""),
            },
        )
    except Exception:
        # Artist song and album search still function if the catalog is unavailable.
        return {"name": name, "country": "", "mbid": None}


# ────────────────── PUBLIC ARTIST PORTRAITS / WIKIMEDIA ──────────────────
# This endpoint retrieves PUBLIC Wikipedia profile thumbnails. It never stores user
# preferences or user-provided API keys. A short-lived memory cache only contains
# shared, public artist image metadata for faster subsequent onboarding sessions.
ARTIST_WIKI_TITLES = {
    "a.r. rahman": "A. R. Rahman",
    "pritam": "Pritam Chakraborty",
    "kk": "KK (singer)",
    "badshah": "Badshah (rapper)",
    "drake": "Drake (musician)",
    "bts": "BTS",
}


class ArtistPortraitRequest(BaseModel):
    names: list[str] = Field(default_factory=list, max_length=30)


def safe_public_photo(src):
    if not isinstance(src, str) or not src.startswith("https://"):
        return ""
    parsed = urlparse(src)
    return (
        src
        if parsed.hostname in ("upload.wikimedia.org", "commons.wikimedia.org")
        else ""
    )


async def wikipedia_portraits(names):
    """Batched Wikipedia PageImages API; public artist metadata only."""
    titles = {name: ARTIST_WIKI_TITLES.get(name.casefold(), name) for name in names}
    params = {
        "action": "query",
        "format": "json",
        "formatversion": 2,
        "prop": "pageimages",
        "piprop": "thumbnail",
        "pithumbsize": 260,
        "redirects": 1,
        "titles": "|".join(dict.fromkeys(titles.values())),
    }
    headers = {**HEADERS, "Accept": "application/json"}
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(6.0), headers=headers, follow_redirects=True
    ) as client:
        response = await client.get("https://en.wikipedia.org/w/api.php", params=params)
        response.raise_for_status()
        data = response.json().get("query", {})
        redirections = {
            r.get("from", "").casefold(): r.get("to", "")
            for r in data.get("redirects", [])
        }
        normalizations = {
            r.get("from", "").casefold(): r.get("to", "")
            for r in data.get("normalized", [])
        }
        pages = {
            str(page.get("title", "")).casefold(): page
            for page in data.get("pages", [])
        }
        results = {}
        for name, title in titles.items():
            canonical = title
            for _ in range(3):
                changed = redirections.get(canonical.casefold()) or normalizations.get(
                    canonical.casefold()
                )
                if not changed:
                    break
                canonical = changed
            page = pages.get(canonical.casefold()) or pages.get(title.casefold())
            photo = safe_public_photo(
                ((page or {}).get("thumbnail") or {}).get("source")
            )
            if photo:
                actual_title = (page or {}).get("title") or title
                results[name] = {
                    "image": photo,
                    "source": "https://en.wikipedia.org/wiki/"
                    + quote(actual_title.replace(" ", "_")),
                    "provider": "Wikipedia / Wikimedia Commons",
                }
        return results


async def wikipedia_rest_portraits(names):
    """Bounded Wikipedia page summaries as a second lookup strategy."""
    semaphore = asyncio.Semaphore(3)
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(5.0), headers=HEADERS, follow_redirects=True
    ) as client:

        async def one(name):
            title = ARTIST_WIKI_TITLES.get(name.casefold(), name)
            try:
                async with semaphore:
                    r = await client.get(
                        "https://en.wikipedia.org/api/rest_v1/page/summary/"
                        + quote(title.replace(" ", "_"), safe="")
                    )
                    r.raise_for_status()
                obj = r.json()
                photo = safe_public_photo((obj.get("thumbnail") or {}).get("source"))
                if photo:
                    return name, {
                        "image": photo,
                        "source": (obj.get("content_urls") or {})
                        .get("desktop", {})
                        .get("page")
                        or "https://en.wikipedia.org/wiki/"
                        + quote(title.replace(" ", "_")),
                        "provider": "Wikipedia / Wikimedia Commons",
                    }
            except (httpx.HTTPError, ValueError, TypeError, KeyError):
                pass
            return name, {}

        return dict(await asyncio.gather(*(one(n) for n in names)))


# Vercel's deployed code directory is read-only; /tmp is writable but ephemeral.
PORTRAIT_DIR = (
    Path(tempfile.gettempdir()) / "mousike" / "cache" / "portraits"
    if os.getenv("VERCEL")
    else ROOT / "assets" / "cache" / "portraits"
)
try:
    PORTRAIT_DIR.mkdir(parents=True, exist_ok=True)
except OSError:
    # Also cover other serverless/read-only environments without breaking import.
    PORTRAIT_DIR = Path(tempfile.gettempdir()) / "mousike" / "cache" / "portraits"
    PORTRAIT_DIR.mkdir(parents=True, exist_ok=True)
PORTRAIT_LOCKS = {}
PORTRAIT_TTL = 60 * 60 * 24 * 21


def portrait_name(name):
    return (
        hashlib.sha256(name.strip().casefold().encode("utf-8")).hexdigest()[:24]
        + ".jpg"
    )


async def fetch_wiki_metadata(names):
    """Resolve missing public names. Nothing personalized is saved on the server."""
    out = {}
    missing = []
    for name in names:
        key = ("public-portrait", name.casefold())
        hit = cached(key)
        if hit is not None:
            out[name] = hit
        else:
            missing.append(name)
    if missing:
        try:
            found = await wikipedia_portraits(missing)
        except (httpx.HTTPError, ValueError, KeyError):
            found = {}
        absent = [name for name in missing if not found.get(name)]
        # REST lookups are costly; limit to a few per call, progress stays responsive.
        if absent:
            try:
                found.update(await wikipedia_rest_portraits(absent[:8]))
            except (httpx.HTTPError, ValueError, KeyError):
                pass
        for name in missing:
            entry = found.get(name, {})
            if entry:
                remember(("public-portrait", name.casefold()), entry)
            out[name] = entry
    return out


@app.post("/api/artist-portraits")
async def artist_portraits(request: ArtistPortraitRequest):
    names = list(
        dict.fromkeys(str(n).strip()[:80] for n in request.names if str(n).strip())
    )[:30]
    if not names:
        return {"portraits": {}, "source": "Wikipedia"}
    records = await fetch_wiki_metadata(names)
    return {
        "portraits": {
            name: {**entry, "local_image": "/api/portrait?name=" + quote(name)}
            if entry
            else {}
            for name, entry in records.items()
        },
        "source": "Wikipedia / Wikimedia Commons",
    }


@app.get("/api/portrait")
async def portrait(name: str = Query(min_length=1, max_length=80)):
    """Serve Wikimedia Wikipedia portraits through our origin; avoid hotlink/CORS issues.
    Positive public thumbnails are cached on disk; profiles and API keys are never cached here.
    """
    name = name.strip()
    if not name or any(ord(c) < 32 for c in name):
        raise HTTPException(400, "Invalid name")
    path = PORTRAIT_DIR / portrait_name(name)
    if path.is_file() and time.time() - path.stat().st_mtime < PORTRAIT_TTL:
        return FileResponse(
            path,
            media_type=(
                "image/png"
                if path.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
                else (
                    "image/webp" if path.read_bytes()[:4] == b"RIFF" else "image/jpeg"
                )
            ),
            headers={"Cache-Control": "public, max-age=86400"},
        )
    # One lookup/download at a time per artist, so UI renders without duplicate external calls.
    if len(PORTRAIT_LOCKS) > 256:
        for k in list(PORTRAIT_LOCKS):
            if not PORTRAIT_LOCKS[k].locked():
                PORTRAIT_LOCKS.pop(k, None)
    lock = PORTRAIT_LOCKS.setdefault(name.casefold(), asyncio.Lock())
    async with lock:
        if path.is_file() and time.time() - path.stat().st_mtime < PORTRAIT_TTL:
            return FileResponse(
                path,
                media_type=(
                    "image/png"
                    if path.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n"
                    else (
                        "image/webp"
                        if path.read_bytes()[:4] == b"RIFF"
                        else "image/jpeg"
                    )
                ),
                headers={"Cache-Control": "public, max-age=86400"},
            )
        entry = (await fetch_wiki_metadata([name])).get(name, {})
        url = safe_public_photo(entry.get("image"))
        if not url:
            # Explicit SVG fallback is served locally and instantly.
            return RedirectResponse(
                "/assets/images/artist-fallback.svg", status_code=307
            )
        try:
            async with httpx.AsyncClient(
                timeout=httpx.Timeout(8),
                headers={**HEADERS, "Accept": "image/*"},
                follow_redirects=False,
            ) as client:
                response = await client.get(url)
                response.raise_for_status()
                if len(response.content) > 1_000_000 or len(response.content) < 128:
                    raise ValueError("Invalid thumbnail size")
                mime = response.headers.get("content-type", "").split(";")[0].strip()
                if mime not in ("image/jpeg", "image/png", "image/webp"):
                    raise ValueError("Unsupported image format")
                # Keep the original compressed bytes, do not decode/resize on every visit.
                extension = {
                    "image/jpeg": ".jpg",
                    "image/png": ".png",
                    "image/webp": ".webp",
                }[mime]
                target = path.with_suffix(extension)
                # Store one stable asset for each discovered artist (never user profile data).
                target.write_bytes(response.content)
                if extension != ".jpg":
                    path.write_bytes(response.content)
                return Response(
                    content=response.content,
                    media_type=mime,
                    headers={"Cache-Control": "public, max-age=86400"},
                )
        except (httpx.HTTPError, ValueError, TypeError, OSError):
            return RedirectResponse(
                "/assets/images/artist-fallback.svg", status_code=307
            )


# ─────────────────── ONE-CLICK AUDIO DOWNLOADS ───────────────────────────
# Downloads are user initiated; no recurring downloads, no stored listening data.
# For chapter-based tracks, FFmpeg exports exactly the requested timestamp range.


def safe_download_title(title):
    value = re.sub(r'[\\/:*?"<>|\x00-\x1f]+', " ", str(title or "Mousike track"))
    return re.sub(r"\s+", " ", value).strip(" .")[:92] or "Mousike track"


@app.get("/api/download/{vid}")
async def download_audio(
    vid: str,
    request: Request,
    title: str = Query("Mousike track", max_length=140),
    start: float = Query(0, ge=0),
    end: float = Query(0, ge=0),
):
    if not VIDEO_ID.fullmatch(vid):
        raise HTTPException(400, "Invalid YouTube video")
    if end and (end <= start or end - start > 3600):
        raise HTTPException(400, "Invalid chapter boundaries")
    key = ("stream", vid)
    if key not in CACHE or time.time() - CACHE[key][0] > 900:
        try:
            async with LIMIT:
                info = await run_worker(do_info, vid, media=True)
            CACHE[key] = (time.time(), info)
        except Exception as e:
            raise HTTPException(502, f"Could not resolve audio: {str(e)[:140]}")
    data = CACHE[key][1]
    filename = safe_download_title(title)
    if end:
        if not ffmpeg_executable():
            raise HTTPException(
                501,
                "Chapter export requires FFmpeg. Install it locally or deploy the bundled imageio-ffmpeg dependency.",
            )
        path = Path(tempfile.mkdtemp(prefix="mousike_export_"))
        dest = path / (filename + ".mp3")
        # Download clip only when user clicks, never as a search/preloading side effect.
        header_text = "".join(
            f"{k}: {v}\r\n"
            for k, v in data["headers"].items()
            if k.lower()
            in (
                "user-agent",
                "referer",
                "origin",
                "cookie",
                "accept",
                "accept-language",
            )
        )
        cmd = [ffmpeg_executable(), "-nostdin", "-hide_banner", "-loglevel", "error"]
        if header_text:
            cmd += ["-headers", header_text]
        cmd += [
            "-ss",
            str(start),
            "-i",
            data["url"],
            "-t",
            str(round(end - start, 2)),
            "-vn",
            "-codec:a",
            "libmp3lame",
            "-b:a",
            "192k",
            "-y",
            str(dest),
        ]
        try:
            p = await asyncio.create_subprocess_exec(
                *cmd, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE
            )
            _, err = await asyncio.wait_for(p.communicate(), timeout=180)
            if p.returncode != 0 or not dest.exists() or dest.stat().st_size < 1024:
                raise RuntimeError(
                    (err or b"FFmpeg unable to access this source").decode(
                        errors="replace"
                    )[-160:]
                )
            return FileResponse(
                dest,
                media_type="audio/mpeg",
                filename=filename + ".mp3",
                background=BackgroundTask(shutil.rmtree, path, True),
                headers={"Cache-Control": "no-store"},
            )
        except BaseException as e:
            if "p" in locals() and p.returncode is None:
                p.kill()
                await p.wait()
            shutil.rmtree(path, ignore_errors=True)
            if isinstance(e, asyncio.CancelledError):
                raise
            raise HTTPException(
                502, "Could not export this timestamped song: " + str(e)[:120]
            )
    # Direct download retains source AAC/Opus; avoid costly re-encoding and waiting.
    headers = {**HEADERS, **data["headers"]}
    headers.pop("Range", None)
    client = httpx.AsyncClient(
        timeout=httpx.Timeout(25, read=90), follow_redirects=True
    )
    try:
        response = await client.send(
            client.build_request("GET", data["url"], headers=headers), stream=True
        )
        if response.status_code != 200:
            await response.aclose()
            await client.aclose()
            raise HTTPException(502, "Audio source unavailable")
    except HTTPException:
        raise
    except Exception as e:
        await client.aclose()
        raise HTTPException(502, "Audio download connection failed: " + str(e)[:130])
    mime = data.get("mime", "audio/mp4")
    suffix = ".m4a" if "mp4" in mime else ".webm"

    async def read_chunks():
        try:
            async for chunk in response.aiter_bytes(65536):
                if await request.is_disconnected():
                    break
                yield chunk
        finally:
            await response.aclose()
            await client.aclose()

    return StreamingResponse(
        read_chunks(),
        media_type=mime,
        headers={
            "Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename + suffix)}",
            "Cache-Control": "no-store",
            **(
                {"Content-Length": response.headers["content-length"]}
                if response.headers.get("content-length")
                else {}
            ),
        },
    )


# ─────────────────── AURORA INTELLIGENCE / STATELESS GEMINI ───────────────
# Listening events, preferences, model summaries and optional Gemini key remain
# in the user's browser storage. This Python service executes ALL analysis and
# all model calls. No SQLite, key file or per-user server-side profile database.
from recommender import Recommender, MODEL, music_insights, clean_profile

smart = Recommender(search_fn=do_search)


class AIRequest(BaseModel):
    profile: dict = Field(default_factory=dict, max_length=40)
    activity: list[dict] = Field(default_factory=list, max_length=700)
    learned: dict = Field(default_factory=dict, max_length=20)
    api_key: str = Field(default="", max_length=300)
    exclude: list[str] = Field(default_factory=list, max_length=100)
    current: dict = Field(default_factory=dict, max_length=35)

    @field_validator("exclude")
    @classmethod
    def bound_excludes(cls, values):
        if any(len(v) > 100 for v in values):
            raise ValueError("Track ID is too long")
        return values


@app.post("/api/ai/status")
async def ai_status(req: AIRequest):
    return {
        "configured": bool(resolved_key(req)),
        "server_configured": bool(os.getenv("GEMINI_API_KEY", "").strip()),
        "model": MODEL,
        "storage": "browser-only",
        "events": len(req.activity),
        "privacy": "No personal profiles, listening activity, or keys are persisted on the server.",
    }


def resolved_key(req: AIRequest):
    return req.api_key.strip() or os.getenv("GEMINI_API_KEY", "").strip()


@app.post("/api/ai/test")
async def ai_test(req: AIRequest):
    key = resolved_key(req)
    if not key:
        raise HTTPException(400, "Set an optional Gemini API key in Settings")
    try:
        result = await smart.gemini(
            key,
            {"task": "Return a single JSON field status with value ready."},
            {
                "type": "OBJECT",
                "properties": {"status": {"type": "STRING"}},
                "required": ["status"],
            },
            force=True,
        )
        return {
            "ok": True,
            "model": MODEL,
            "response": str(result.get("status", ""))[:65],
        }
    except httpx.TimeoutException:
        raise HTTPException(504, "Gemini timed out. Check your network and retry; local discovery remains available.")
    except httpx.RequestError:
        raise HTTPException(502, "Cannot reach Google Gemini. Check your connection, proxy and TLS certificate setup.")
    except Exception as e:
        raise HTTPException(502, f"Gemini connection failed: {str(e)[:180]}")


@app.post("/api/ai/insights")
async def ai_insights(req: AIRequest):
    return music_insights(
        req.profile, req.activity, req.learned, bool(resolved_key(req))
    )


@app.post("/api/ai/analyze")
async def ai_analyze(req: AIRequest):
    return await smart.analyze(
        req.profile, req.activity, req.learned, resolved_key(req)
    )


@app.post("/api/ai/feed")
async def ai_feed(req: AIRequest):
    return await smart.feed(
        req.profile,
        req.activity,
        req.learned,
        resolved_key(req),
        req.exclude,
        req.current,
    )


@app.post("/api/ai/next")
async def ai_next(req: AIRequest):
    return await smart.next_track(
        req.profile,
        req.activity,
        req.learned,
        resolved_key(req),
        req.exclude,
        req.current,
    )


@app.get("/api/capabilities")
async def capabilities():
    return {
        "version": "3.0.0",
        "embedding_backend": ENCODER.mode,
        "embedding_note": ENCODER.error,
        "gemini_model": MODEL,
        "server_key_configured": bool(os.getenv("GEMINI_API_KEY")),
        "ffmpeg": bool(ffmpeg_executable()),
        "js_runtime_available": js_runtime_available(),
        "hosting": "vercel" if os.getenv("VERCEL") else "self-hosted",
        "lyrics": "licensed-sidecars-and-user-import",
        "preload": "metadata-first; optional media prebuffer",
        "gapless": "same-source chapters; best effort across independent recordings",
        "normalization": "ReplayGain or adaptive stream RMS",
    }


@app.get("/api/suggestions")
async def suggest(q: str = Query(min_length=1, max_length=120)):
    public = []
    for key, (_, value) in list(SEARCH_CACHE.data.items())[-20:]:
        public.extend(value)
    return {"suggestions": suggestions(q, public), **interpret_query(q)}


class DiscoveryRequest(AIRequest):
    existing: list[dict] = Field(default_factory=list, max_length=40)
    query: str = Field(min_length=1, max_length=120)
    limit: int = Field(default=24, ge=1, le=40)


@app.post("/api/discovery/search")
async def discovery_search(req: DiscoveryRequest):
    plan = interpret_query(req.query)
    req.profile = clean_profile(req.profile)
    if not req.query.strip():
        raise HTTPException(422, "Enter a search query")
    try:
        tracks = await search_tracks(plan["expanded_query"], req.limit)
        if not tracks and plan["expanded_query"] != req.query:
            tracks = await search_tracks(req.query, req.limit)
        tracks = await asyncio.to_thread(
            ranked_search, tracks, req.query, req.profile, req.activity
        )
        names = list(
            dict.fromkeys(artist_for(t, req.profile.get("artists", [])) for t in tracks)
        )[:8]
        return {
            "tracks": tracks,
            "artists": [{"name": n} for n in names if n != "Unknown artist"],
            **plan,
        }
    except Exception:
        raise HTTPException(
            502, "Search sources are temporarily unavailable. Please try again."
        )


@app.post("/api/discovery/related")
async def related_discoveries(req: DiscoveryRequest):
    from music_semantics import same_song

    plan = interpret_query(req.query)
    if plan["intent"] != "mood":
        return {"tracks": []}
    query = " ".join(plan["concepts"][:3]) + " new artists official audio"
    try:
        rows = await asyncio.wait_for(search_tracks(query, 12), timeout=15)
        tracks = [
            t
            for t in ranked_search(rows, req.query, clean_profile(req.profile))
            if not any(same_song(t, x) for x in req.existing)
        ][:8]
        return {"tracks": tracks}
    except Exception:
        return {"tracks": []}


@app.get("/api/lyrics")
async def lyrics(
    title: str = Query(min_length=1, max_length=180),
    artist: str = Query("", max_length=120),
):
    return local_lyrics(ROOT, title, artist)


if __name__ == "__main__":
    print("\n  Mousike — Sound In motion → http://127.0.0.1:8000\n")
    uvicorn.run(
        app,
        host=os.getenv("MOUSIKE_HOST", "127.0.0.1"),
        port=int(os.getenv("MOUSIKE_PORT", "8000")),
    )
