"""Actual ASGI endpoints, source refresh, byte ranges and trusted origin rules."""

import asyncio
import time
import pytest
from fastapi.testclient import TestClient
import main
from infrastructure import BoundedCache, PublicSearchCache
from lyrics import lyrics_key, parse_lrc, local_lyrics


@pytest.fixture
def client():
    main.CACHE.clear()
    with TestClient(main.app) as c:
        yield c


def test_static_entrypoints_are_real_local_assets(client):
    from bs4 import BeautifulSoup

    for path in ["/", "/onboarding.html", "/settings.html", "/player.html"]:
        r = client.get(path)
        assert r.status_code == 200
        soup = BeautifulSoup(r.text, "html.parser")
        ids = [e["id"] for e in soup.select("[id]")]
        assert len(ids) == len(set(ids)), path
        assert "script-src 'self'" in r.headers["content-security-policy"]
        for el in soup.find_all("script"):
            assert el.get("src"), "Inline script is forbidden"
            assert client.get(el["src"]).status_code == 200
        for el in soup.find_all("link", rel="stylesheet"):
            assert client.get(el["href"]).status_code == 200
    assert client.get("/.env").status_code == 404


def test_host_origin_and_size_guards(client):
    assert (
        client.get("/api/health", headers={"Host": "attacker.example"}).status_code
        == 400
    )
    assert (
        client.post(
            "/api/ai/feed", json={}, headers={"Origin": "https://attacker.example"}
        ).status_code
        == 403
    )
    assert (
        client.get(
            "/api/search?q=test", headers={"Sec-Fetch-Site": "cross-site"}
        ).status_code
        == 403
    )
    assert client.post("/api/ai/feed", content="x" * 1000001).status_code == 413
    assert client.get("/api/stream/bad").status_code == 400
    assert (
        client.get(
            "/api/stream/abcdefghijk", headers={"Range": "bytes=0-1,5-6"}
        ).status_code
        == 416
    )


def test_rate_budget_returns_retry_after(client, monkeypatch):
    # Repeated lightweight status requests exercise the AI budget without extraction.
    monkeypatch.setenv("MOUSIKE_RATE_LIMIT", "1")
    codes = []
    for _ in range(28):
        response = client.post("/api/ai/status", json={})
        codes.append(response.status_code)
    assert 429 in codes
    assert response.headers.get("retry-after") == "5"


def test_source_expiry_is_refreshed_once_and_ranges_are_forwarded(client, monkeypatch):
    vid = "abcdefghijk"
    count = {"resolves": 0, "closes": 0}
    seen = []

    def info(v):
        count["resolves"] += 1
        return {
            "track": {"id": v, "title": "Authorized fixture"},
            "url": "https://audio.example/" + str(count["resolves"]),
            "mime": "audio/mp4",
            "headers": {},
        }

    class Upstream:
        def __init__(self, status):
            self.status_code = status
            self.headers = {
                "content-range": "bytes 0-4/20",
                "content-length": "5",
                "accept-ranges": "bytes",
                "content-type": "audio/mp4",
            }

        async def aiter_bytes(self, n):
            yield b"audio"

        async def aclose(self):
            count["closes"] += 1

    class Client:
        def __init__(self, **kwargs):
            pass

        def build_request(self, method, url, headers):
            seen.append((method, url, headers))
            return url

        async def send(self, request, stream=True):
            return Upstream(403 if request.endswith("/1") else 206)

        async def aclose(self):
            pass

    monkeypatch.setattr(main, "do_info", info)
    monkeypatch.setattr(main.httpx, "AsyncClient", Client)
    r = client.get("/api/stream/" + vid, headers={"Range": "bytes=0-4"})
    assert r.status_code == 206 and r.content == b"audio"
    assert count["resolves"] == 2 and count["closes"] == 2
    assert all(headers["Range"] == "bytes=0-4" for _, _, headers in seen)
    assert r.headers["content-range"] == "bytes 0-4/20"


@pytest.mark.asyncio
async def test_shared_video_metadata_is_single_flight(monkeypatch):
    main.CACHE.clear()
    calls = []

    def worker(vid):
        time.sleep(0.03)
        calls.append(vid)
        return {"id": vid, "duration": 600}

    monkeypatch.setattr(main, "video_meta_worker", worker)
    values = await asyncio.gather(
        *[main.public_video_meta("abcdefghijk") for _ in range(8)]
    )
    assert len(calls) == 1 and all(v["duration"] == 600 for v in values)


def test_caches_are_bounded_and_failures_are_not_poisoned():
    cache = BoundedCache(2)
    for i in range(8):
        cache[i] = (time.time(), i)
    assert len(cache) == 2
    public = PublicSearchCache(capacity=2, ttl=60)
    calls = []

    def work():
        calls.append(1)
        return [{"title": "Song"}]

    first = public.call("q", work)
    first[0]["title"] = "mutated"
    assert public.call("q", work)[0]["title"] == "Song" and len(calls) == 1
    for i in range(5):
        public.call(i, work)
    assert len(public.data) == 2


@pytest.mark.asyncio
async def test_hybrid_chapters_and_metadata_fallback_preserve_true_boundaries(
    monkeypatch,
):
    main.CACHE.clear()

    def video(vid):
        return {
            "id": vid,
            "title": "Artist - Album (Full Album)",
            "artist": "Artist",
            "duration": 600,
            "description": "00:00 First song\n03:00 Second song\n06:00 Third song",
        }

    monkeypatch.setattr(main, "video_meta_worker", video)

    async def no_catalog(*args):
        return None

    monkeypatch.setattr(main, "catalog_for_upload", no_catalog)
    data = await main.hybrid_album_data("abcdefghijk", "auto")
    assert data["method"] == "split"
    assert [(t["start"], t["end"]) for t in data["tracks"]] == [
        (0, 180),
        (180, 360),
        (360, 600),
    ]
    main.CACHE.clear()

    def no_timestamps(vid):
        return {**video(vid), "description": "No timestamps supplied"}

    monkeypatch.setattr(main, "video_meta_worker", no_timestamps)

    async def catalog(*args):
        return {
            "album": {
                "title": "Album",
                "artist": "Artist",
                "id": "catalog-id",
                "image": "",
                "year": "2024",
            },
            "tracks": [
                {"id": "mb:1", "title": "A", "duration": 123},
                {"id": "mb:2", "title": "B", "duration": 185},
            ],
        }

    monkeypatch.setattr(main, "catalog_for_upload", catalog)
    data = await main.hybrid_album_data("abcdefghijk", "auto")
    assert data["method"] == "metadata" and all(
        t["unresolved"] and not t["segment"] for t in data["tracks"]
    )
    assert all("start" not in t for t in data["tracks"])


def test_description_parser_rejects_promotional_and_structural_chapters():
    info = {"duration": 600, "description": "00:00 Intro\n03:00 Chorus\n06:00 Verse"}
    assert main.chapter_segments(info, "abcdefghijk", "Artist", "")[0] == []
    assert main.parse_description_chapters(
        "00:00 https://example.com\n99:99 Bad\n00:30 Real song", 600
    ) == [(30, "Real song")]


def test_licensed_lyrics_sidecar_and_lrc_parsing(tmp_path):
    assert parse_lrc("[00:01.25]Hello\n[00:02.00][00:03.00]World") == [
        {"at": 1.25, "text": "Hello"},
        {"at": 2.0, "text": "World"},
        {"at": 3.0, "text": "World"},
    ]
    folder = tmp_path / "lyrics"
    folder.mkdir()
    (folder / (lyrics_key("Song", "Artist") + ".lrc")).write_text(
        "[00:01]Permitted test line"
    )
    assert local_lyrics(tmp_path, "Song", "Artist")["available"]
    assert not local_lyrics(tmp_path, "Unknown", "Artist")["available"]


def test_semantic_search_endpoint_uses_real_candidates(client, monkeypatch):
    rows = [
        {
            "id": "abcdefghijk",
            "title": "Dreamy indie track",
            "artist": "Singer A",
            "duration": 200,
        },
        {
            "id": "lmnopqrstuv",
            "title": "Heavy metal track",
            "artist": "Singer B",
            "duration": 210,
        },
    ]
    monkeypatch.setattr(main, "do_search", lambda q, n: rows)
    response = client.post(
        "/api/discovery/search",
        json={
            "query": "dreamy late-night indie songs",
            "profile": {"genres": ["indie"]},
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["tracks"][0]["id"] == "abcdefghijk"
    assert body["artists"][0]["name"] == "Singer A"
    assert "dreamy" in body["concepts"]


def test_numeric_song_names_and_invalid_seconds_are_handled():
    assert main.parse_description_chapters(
        "00:00 1979\n03:00 21\n03:99 Invalid seconds", 600
    ) == [(0, "1979"), (180, "21")]
    assert main.seconds("03:99") == -1
