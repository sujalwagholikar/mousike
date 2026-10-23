"""Deterministic offline checks of Mousike portrait + download endpoints."""

import math, struct, time, wave
import pytest
from fastapi.testclient import TestClient
import main


@pytest.fixture
def client():
    return TestClient(main.app)


def test_onboarding_assets(client):
    for path in [
        "/onboarding.html",
        "/assets/js/onboarding-lite.js",
        "/assets/css/onboarding-lite.css",
        "/assets/images/artist-fallback.svg",
        "/assets/css/system.css",
    ]:
        assert client.get(path).status_code == 200
    html = client.get("/onboarding.html").text
    assert "Let’s start with" in html
    assert "Sound In motion" in html
    assert "showcase" not in html


def test_download_rejects_invalid_id_and_segment(client):
    assert client.get("/api/download/bad").status_code == 400
    assert client.get("/api/download/abcdefghijk?start=80&end=50").status_code == 400
    assert client.get("/api/download/abcdefghijk?start=0&end=5000").status_code == 400


def test_regular_download_is_streamed_without_conversion(client, monkeypatch):
    vid = "qwertyuiopa"
    main.CACHE[("stream", vid)] = (
        time.time(),
        {"url": "https://audio.example/stream", "mime": "audio/mp4", "headers": {}},
    )

    class Upstream:
        status_code = 200
        headers = {"content-length": "5"}

        async def aiter_bytes(self, _):
            yield b"audio"

        async def aclose(self):
            pass

    class FakeClient:
        def __init__(self, *args, **kwargs):
            pass

        def build_request(self, *args, **kwargs):
            return object()

        async def send(self, *args, **kwargs):
            return Upstream()

        async def aclose(self):
            pass

    monkeypatch.setattr(main.httpx, "AsyncClient", FakeClient)
    r = client.get("/api/download/" + vid + "?title=My%20Song")
    assert r.status_code == 200
    assert r.content == b"audio"
    assert "attachment" in r.headers["content-disposition"]
    assert "My%20Song.m4a" in r.headers["content-disposition"]


def test_chapter_download_cut_with_ffmpeg(client, tmp_path):
    if not main.shutil.which("ffmpeg"):
        pytest.skip("ffmpeg missing")
    vid = "asdfghjklzx"
    wav = tmp_path / "audio.wav"
    with wave.open(str(wav), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(12000)
        data = b"".join(
            struct.pack("<h", int(4000 * math.sin(i * math.pi * 440 / 6000)))
            for i in range(24000)
        )
        f.writeframes(data)
    main.CACHE[("stream", vid)] = (
        time.time(),
        {"url": str(wav), "mime": "audio/mp4", "headers": {}},
    )
    r = client.get("/api/download/" + vid + "?title=Chapter&start=0.3&end=1.1")
    assert r.status_code == 200, r.text[:200]
    assert r.headers["content-type"].startswith("audio/mpeg")
    assert r.content.startswith(b"ID3")
    assert len(r.content) > 1000


@pytest.mark.asyncio
async def test_wikipedia_batch_mocks(monkeypatch):
    async def fake_portraits(names):
        return {
            name: {
                "image": "https://upload.wikimedia.org/testing.jpg",
                "source": "https://en.wikipedia.org/wiki/Artist",
            }
            for name in names
        }

    monkeypatch.setattr(main, "wikipedia_portraits", fake_portraits)
    result = await main.artist_portraits(
        main.ArtistPortraitRequest(names=["Test Singer"])
    )
    assert (
        result["portraits"]["Test Singer"]["local_image"]
        == "/api/portrait?name=Test%20Singer"
    )
