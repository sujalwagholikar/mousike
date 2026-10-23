"""Canonical song identity, multilingual metadata ranking and diverse sequencing."""

from __future__ import annotations
from collections import Counter
from difflib import SequenceMatcher
import hashlib
import math
import re
import time
from embeddings import ENCODER, fold, music_tags, metadata_text

KNOWN_ARTISTS = (
    "Lana Del Rey",
    "Arijit Singh",
    "The Weeknd",
    "Taylor Swift",
    "Billie Eilish",
    "A. R. Rahman",
    "Shreya Ghoshal",
    "Sonu Nigam",
    "Pritam",
    "Atif Aslam",
    "Diljit Dosanjh",
    "KK",
    "Kishore Kumar",
    "Lata Mangeshkar",
    "Badshah",
    "AP Dhillon",
    "Drake",
    "Coldplay",
    "BTS",
    "Ed Sheeran",
    "Adele",
    "SZA",
    "Dua Lipa",
    "Olivia Rodrigo",
    "Harry Styles",
    "Bruno Mars",
    "Justin Bieber",
    "Kendrick Lamar",
    "Tame Impala",
    "Cigarettes After Sex",
    "Lorde",
    "Hozier",
    "Arctic Monkeys",
    "Ariana Grande",
    "Mohit Chauhan",
    "Sunidhi Chauhan",
    "Shankar Mahadevan",
    "Neha Kakkar",
    "Jubin Nautiyal",
)
NOISE = re.compile(
    r"\b(?:official(?:ly)?|music\s+video|lyric(?:s|al)?\s+video|visuali[sz]er|lyrics|audio|video|hd|hq|4k|8k|vevo|remastered|remaster|topic|full\s+song|copyright|provided\s+to\s+youtube|subtitles?|english\s+translation)\b",
    re.I,
)
BAD = re.compile(
    r"\b(?:interview|reaction|tutorial|explained|review|trailer|podcast|full\s+album|jukebox|compilation|playlist|mashup|karaoke|instrumental\s+cover|live\s+stream|1\s*hour|10\s*hours)\b",
    re.I,
)
VERSION = re.compile(
    r"\b(?:live|remix|acoustic|cover|instrumental|slowed|reverb|sped\s+up|nightcore)\b",
    re.I,
)
GENRE_ADJ = {
    "pop": ("indie pop", "dream pop", "alternative pop", "synth pop"),
    "indie": ("indie pop", "alternative", "dream pop", "indie folk"),
    "rock": ("alternative rock", "indie rock", "soft rock", "classic rock"),
    "hip hop": ("alternative hip hop", "r&b", "neo soul", "melodic rap"),
    "r&b": ("neo soul", "alternative r&b", "soul", "downtempo"),
    "bollywood": ("indie Hindi", "Bollywood acoustic", "Indian pop", "Hindi romantic"),
    "lofi": ("chillhop", "downtempo", "ambient", "lofi jazz"),
    "electronic": ("synth pop", "house", "downtempo", "electronica"),
    "classical": ("piano", "instrumental", "orchestral", "contemporary classical"),
    "jazz": ("neo soul", "soft jazz", "bossa nova", "soul"),
    "folk": ("indie folk", "acoustic", "singer songwriter", "soft rock"),
}
MOOD_ADJ = {
    "chill": ("relaxed", "downtempo", "dreamy"),
    "focus": ("ambient", "instrumental"),
    "energetic": ("upbeat", "dance"),
    "melancholic": ("introspective", "dreamy"),
    "romantic": ("intimate", "soft"),
    "dreamy": ("dream pop", "atmospheric"),
}


def artist_for(track, favorites=()):
    # Explicit performer metadata takes precedence over publishers/channels.
    explicit = track.get("performer") or track.get("track_artist")
    if explicit:
        return str(explicit).strip()[:100]
    text, label = str(track.get("title") or ""), str(track.get("artist") or "").strip()
    candidates = tuple(str(x) for x in favorites if x) + KNOWN_ARTISTS
    combined = " " + fold(text + " " + label) + " "
    for artist in sorted(set(candidates), key=len, reverse=True):
        name = fold(artist)
        if len(name) >= 2 and " " + name + " " in combined:
            return artist
    left = re.split(r"\s+[-–—|]\s+", text, maxsplit=1)
    if len(left) == 2 and 1 < len(left[0]) < 60 and not NOISE.search(left[0]):
        # Standard Artist - Title uploads are useful even for unknown performers.
        return left[0].strip()
    return (
        re.sub(r"(?i)\s*(?:-\s*topic|vevo|official)$", "", label).strip()
        or "Unknown artist"
    )


def song_title(track, favorites=()):
    text = str(
        track.get("canonical_title") or track.get("track") or track.get("title") or ""
    )
    text = re.sub(r"https?://\S+", " ", text)
    text = NOISE.sub(" ", text)
    # Strip publisher decoration without erasing legitimate parenthetical titles.
    text = re.sub(r"\(\s*\)|\[\s*\]", " ", text)
    text = re.sub(r"\b(?:ft\.?|feat\.?|featuring)\s+.*$", " ", text, flags=re.I)
    for artist in sorted(
        set(
            tuple(favorites)
            + KNOWN_ARTISTS
            + (artist_for(track, favorites), str(track.get("artist") or ""))
        ),
        key=len,
        reverse=True,
    ):
        if len(fold(artist)) >= 2:
            text = re.sub(
                r"(?<!\w)" + re.escape(artist) + r"(?!\w)", " ", text, flags=re.I
            )
    parts = [p.strip(" _/\"'") for p in re.split(r"\s+[-–—|]\s+", text) if fold(p)]
    # Once an artist is removed, the first remaining field is the track, not a label suffix.
    return fold(parts[0] if parts else text)


def song_key(track, favorites=()):
    return song_title(track, favorites) or fold(track.get("id", ""))


def same_song(a, b, favorites=()):
    if a.get("id") and a["id"] == b.get("id"):
        return True
    da, db = float(a.get("duration") or 0), float(b.get("duration") or 0)
    duration_close = not (da and db) or abs(da - db) <= max(8.0, min(da, db) * 0.055)
    if a.get("isrc") and a["isrc"] == b.get("isrc") and duration_close:
        return True
    # Fingerprint hashes may be supplied by authorized catalog/local-file tools.
    if (
        a.get("fingerprint")
        and a.get("fingerprint_type") == b.get("fingerprint_type")
        and a["fingerprint"] == b.get("fingerprint")
        and duration_close
    ):
        return True
    x, y = song_key(a, favorites), song_key(b, favorites)
    if not x or not y or not duration_close:
        return False
    aa, bb = fold(artist_for(a, favorites)), fold(artist_for(b, favorites))
    if aa and bb and aa != bb and aa != "unknown artist" and bb != "unknown artist":
        # A random publisher is ambiguous. Two explicit/recognized performers aren't.
        known = {fold(v) for v in KNOWN_ARTISTS + tuple(favorites)}
        if (aa in known and bb in known) or (
            a.get("performer")
            and b.get("performer")
            and a.get("performer_source") != "channel"
            and b.get("performer_source") != "channel"
        ):
            return False
        ambiguous = (
            a.get("performer_source") == "channel"
            or b.get("performer_source") == "channel"
        )
        if ambiguous and (
            min(len(x), len(y)) < 8
            or (da and db and abs(da - db) > max(4, min(da, db) * 0.02))
        ):
            return False
        if not ambiguous and not any(
            k in " ".join((aa, bb))
            for k in ("upload", "music", "label", "lyrics", "unknown", "records")
        ):
            return False
    va = set(fold(v) for v in VERSION.findall(str(a.get("title") or "")))
    vb = set(fold(v) for v in VERSION.findall(str(b.get("title") or "")))
    if va != vb:
        return False
    parts = lambda v: re.findall(
        r"\b(?:part|pt|vol|volume|no)\s+(?:[ivxlcdm]+|\d+)\b", v
    )
    if parts(x) != parts(y):
        return False
    if re.findall(r"\d+", x) != re.findall(r"\d+", y):
        return False
    return x == y or (
        min(len(x), len(y)) >= 8 and SequenceMatcher(None, x, y).ratio() >= 0.96
    )


def query_plan(ins, nonce=None):
    p = ins["profile"]
    seed = int(nonce if nonce is not None else time.time() // 1800)
    artists = list(
        dict.fromkeys(p["artists"] + [x["name"] for x in ins.get("top_artists", [])])
    )
    avoided = {fold(v) for v in ins.get("less_artists", [])}
    artists = [a for a in artists if fold(a) not in avoided]
    genres = (
        p["genres"]
        or list(ins.get("preferences", {}).get("genre", {}))
        or ["indie", "pop"]
    )
    moods = p["moods"] or list(ins.get("preferences", {}).get("mood", {})) or ["chill"]
    langs = p["languages"] or ["international"]
    g, m, lang = (
        genres[seed % len(genres)],
        moods[(seed // 2) % len(moods)],
        langs[(seed // 3) % len(langs)],
    )
    related = next(
        (v for k, v in GENRE_ADJ.items() if k in fold(g)),
        ("indie pop", "acoustic", "alternative"),
    )
    other_lang = langs[(seed + 1) % len(langs)]
    raw = [
        f"{lang} {g} {m} official audio",
        f"{other_lang} {related[seed % len(related)]} songs",
        f"{lang} {genres[(seed + 1) % len(genres)]} new artists official audio",
        f"{langs[(seed + 2) % len(langs)]} {moods[(seed + 1) % len(moods)]} music songs",
    ]
    if artists:
        raw.insert(1, f"{artists[seed % len(artists)]} official audio")
    if len(artists) > 1:
        raw.append(f"{artists[(seed + 1) % len(artists)]} official audio")
    # At most two artist-specific searches. AI is allowed expansion, not monopoly.
    raw += [
        q
        for q in ins.get("analysis", {}).get("queries", [])[:2]
        if not any(fold(a) in fold(q) for a in artists)
    ]
    return list(dict.fromkeys(" ".join(str(q).split())[:105] for q in raw))[:7]


def relevance(track, ins, current=None):
    p = ins["profile"]
    artist = fold(artist_for(track, p["artists"]))
    value = 1.0
    for a in ins.get("top_artists", []):
        if fold(a["name"]) == artist:
            value += min(2.0, math.log1p(max(0.0, a["score"])) * 0.5)
            break
    tags = music_tags(track)
    prefs = ins.get("preferences", {})
    for axis in ("genre", "mood", "language"):
        value += min(3.0, sum(prefs.get(axis, {}).get(t, 0.0) for t in tags) * 0.2)
    if artist in {fold(v) for v in ins.get("less_artists", [])}:
        value -= 12.0
    if any(
        same_song(
            track, {"title": s, "artist": artist_for(track, p["artists"])}, p["artists"]
        )
        for s in ins.get("avoid_titles", [])
    ):
        value -= 100.0
    duration = float(track.get("duration") or 0)
    value += 1.2 if 90 <= duration <= 450 else 0
    if BAD.search(str(track.get("title") or "")) or duration > 480:
        value -= 100.0
    if current and artist == fold(artist_for(current, p["artists"])):
        value -= 4.0
    recent_artist = ins.get("preferences", {}).get("short_term", {}).get(artist, 0)
    # A saturated short session doesn't overwhelm long-term taste.
    value -= max(0, recent_artist - 3.0) * 0.12
    return value


def diversify(tracks, ins, exclude=None, current=None, limit=48, ai_order=None):
    p, now = ins["profile"], time.time()
    fav = p["artists"]
    recent = ins.get("recent", [])
    blocked = [r for r in recent if now - float(r.get("at") or now) < 6 * 3600]
    if current and current.get("title"):
        blocked = [current] + blocked
    reject = ins.get("rejected", [])
    ids = set(str(x) for x in (exclude or []))
    pool = []
    for t in tracks:
        if (
            not isinstance(t, dict)
            or not t.get("title")
            or not t.get("id")
            or t["id"] in ids
        ):
            continue
        if float(t.get("duration") or 0) > 480 or BAD.search(str(t.get("title") or "")):
            continue
        if any(same_song(t, r, fav) for r in blocked + reject + pool):
            continue
        pool.append(t)
    if not pool:
        return []
    user_text = " ".join(
        p["genres"]
        + p["moods"]
        + p["languages"]
        + ins.get("analysis", {}).get("taste_tags", [])
    )
    texts = [user_text, metadata_text(current or {})] + [metadata_text(t) for t in pool]
    vectors = ENCODER.encode(texts)
    vector_map = {t["id"]: v for t, v in zip(pool, vectors[2:])}
    base = {}
    ai_pos = {vid: i for i, vid in enumerate(ai_order or [])}
    known = {fold(a["name"]) for a in ins.get("top_artists", [])}
    discovery = p["discovery"] / 100.0
    for t in pool:
        v = vector_map[t["id"]]
        score = relevance(t, ins, current) + 3.0 * ENCODER.similarity(v, vectors[0])
        if current:
            score += 3.0 * ENCODER.similarity(v, vectors[1])
        if fold(artist_for(t, fav)) not in known:
            score += discovery * 2.5
        # Recent repeats outside the hard six-hour window remain softly penalized.
        for r in recent:
            if same_song(t, r, fav):
                score -= 4.0 * 2 ** (
                    -max(0, now - float(r.get("at") or now)) / (7 * 86400)
                )
        if t["id"] in ai_pos:
            score += 1.4 * (1 - ai_pos[t["id"]] / max(1, len(ai_pos)))
        salt = hashlib.blake2b(
            (t["id"] + str(int(now // 1800))).encode(), digest_size=4
        ).digest()
        base[t["id"]] = score + int.from_bytes(salt, "big") / 2**32 * 0.08
    picked, counts = [], Counter()
    artist_cap = max(2, math.ceil(limit * 0.09))
    while pool and len(picked) < limit:
        choices = []
        available = {fold(artist_for(t, fav)) for t in pool}
        for t in pool:
            a = fold(artist_for(t, fav))
            if counts[a] >= artist_cap:
                continue
            if picked and a == fold(artist_for(picked[-1], fav)):
                continue
            if (
                current
                and not picked
                and a == fold(artist_for(current, fav))
                and len(available) > 1
            ):
                continue
            # Protect the first screen as well as the full feed.
            if len(picked) < 12 and counts[a] >= 2:
                continue
            similarity = max(
                (
                    ENCODER.similarity(vector_map[t["id"]], vector_map[z["id"]])
                    for z in picked[-8:]
                ),
                default=0,
            )
            proximity = sum(a == fold(artist_for(z, fav)) for z in picked[-5:])
            score = (
                base[t["id"]]
                - counts[a] * 1.8
                - proximity * 3.0
                - similarity * (0.6 + discovery)
            )
            choices.append((score, t))
        if not choices:
            break
        best = max(choices, key=lambda x: x[0])[1]
        picked.append(best)
        counts[fold(artist_for(best, fav))] += 1
        pool.remove(best)
    return picked


def local_analysis(ins):
    p = ins["profile"]
    tags = list(dict.fromkeys(p["genres"] + p["moods"] + p["languages"]))[:9]
    genres = ", ".join(p["genres"][:3]) or "a broad mix of styles"
    moods = ", ".join(p["moods"][:3]) or "different listening moods"
    languages = ", ".join(p["languages"][:3]) or "multiple languages"
    return {
        "summary": f"Your selections favor {genres}, {moods} and {languages}. Likes and sustained listening refine this mix; artist rotation keeps it varied."[
            :240
        ],
        "vibe": " · ".join(tags[:3]).title()[:65] or "An open listening palette",
        "taste_tags": tags,
        "queries": query_plan({**ins, "analysis": {}})[:5],
        "recommendation_reason": "Blending your selected tastes, recent context and fresh artists with repeat protection.",
    }
