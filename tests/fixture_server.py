"""Deterministic integration server; generated audio and explicit TEST fixtures.

Run only for browser tests: python tests/fixture_server.py. Production main.py
never imports this module and never uses these catalog responses.
"""

from pathlib import Path
import os

os.environ["NO_PROXY"] = "127.0.0.1,localhost"
import io
import math
import re
import struct
import sys
import wave

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import main
from fastapi import Request
from fastapi.responses import Response
import uvicorn

ARTISTS = [
    "Lana Del Rey",
    "Arijit Singh",
    "The Weeknd",
    "Billie Eilish",
    "Coldplay",
    "Shreya Ghoshal",
    "Taylor Swift",
    "Atif Aslam",
    "Drake",
    "Ed Sheeran",
    "Adele",
    "Lorde",
]
IMAGES = [
    "lana-del-rey",
    "arijit-singh",
    "the-weeknd",
    "billie-eilish",
    "coldplay",
    "shreya-ghoshal",
    "taylor-swift",
    "atif-aslam",
    "drake",
    "ed-sheeran",
    "billie-eilish",
    "lana-del-rey",
]
TRACKS = [
    {
        "id": f"Test{i:07d}",
        "title": f"Test recording {i + 1:02d}",
        "artist": a,
        "performer": a,
        "duration": 12,
        "kind": "video",
        "image": f"/assets/artists/{IMAGES[i % 12]}.jpg",
        "genres": ["indie" if i % 2 else "pop"],
        "languages": ["English" if i % 3 else "Hindi"],
        "moods": ["dreamy" if i % 2 else "energetic"],
        "views": (100 - i) * 10000,
        "url": f"https://www.youtube.com/watch?v=Test{i:07d}",
    }
    for i, a in enumerate(ARTISTS * 6)
]
RID = "12345678-1234-1234-1234-123456789012"
ALBUM_VID = "AlbumDemo01"


def search(query, count):
    if "long album" in query.lower():
        return [
            {
                "id": ALBUM_VID,
                "title": "Test album upload",
                "artist": "Test Artist",
                "duration": 600,
                "kind": "long_video",
                "image": "/assets/artists/coldplay.jpg",
            }
        ] + TRACKS[: count - 1]
    return TRACKS[: max(count, 72)]


def info(vid):
    t = next((t for t in TRACKS if t["id"] == vid), None) or {
        "id": vid,
        "title": "Test album upload",
        "artist": "Test Artist",
        "duration": 100,
        "image": "/assets/artists/coldplay.jpg",
    }
    return {
        "track": t,
        "url": "http://127.0.0.1:8765/fixture/audio/" + vid,
        "mime": "audio/wav",
        "headers": {},
    }


def video_meta(vid):
    return {
        "id": vid,
        "title": "Test Artist - Test Album (Full Album)",
        "artist": "Test Artist",
        "album": "Test Album",
        "duration": 600,
        "thumbnail": "/assets/artists/coldplay.jpg",
        "chapters": [
            {"start_time": 0, "end_time": 30, "title": "Test chapter one"},
            {"start_time": 30, "end_time": 60, "title": "Test chapter two"},
            {"start_time": 60, "end_time": 90, "title": "Test chapter three"},
        ],
    }


async def mb(path, params=None):
    if path.startswith("release/") and path != "release/":
        return {
            "id": RID,
            "title": "Test catalog album",
            "artist-credit": [{"name": "Test Artist"}],
            "media": [
                {
                    "position": 1,
                    "tracks": [
                        {
                            "id": f"row-{i}",
                            "title": t["title"],
                            "length": 12000,
                            "position": i + 1,
                            "artist-credit": [{"name": t["artist"]}],
                        }
                        for i, t in enumerate(TRACKS[:10])
                    ],
                }
            ],
        }
    if path.startswith("release/"):
        return {
            "releases": [
                {
                    "id": RID,
                    "title": "Test catalog album",
                    "artist-credit": [
                        {"name": (params or {}).get("query", "Test Artist")}
                    ],
                    "date": "2024",
                    "track-count": 10,
                    "score": "100",
                }
            ]
        }
    if path.startswith("artist/"):
        return {
            "artists": [
                {
                    "id": RID,
                    "name": (params or {}).get("query", "Test Artist"),
                    "country": "IN",
                    "score": "100",
                }
            ]
        }
    return {}


original_card = main.album_card


def card(row):
    value = original_card(row)
    if value:
        value["image"] = "/assets/artists/coldplay.jpg"
    return value


main.album_card = card
main.do_search = search
main.smart.search_fn = search
main.do_info = info
main.video_meta_worker = video_meta
main.mb_get = mb


async def no_catalog(*args, **kwargs):
    return None


main.catalog_for_upload = no_catalog


async def portraits(names):
    return {}


main.fetch_wiki_metadata = portraits


def wav(seconds):
    out = io.BytesIO()
    with wave.open(out, "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(8000)
        frame = b"".join(
            struct.pack("<h", int(600 * math.sin(i * 2 * math.pi * 220 / 8000)))
            for i in range(8000)
        )
        f.writeframes(frame * seconds)
    return out.getvalue()


AUDIO = {12: wav(12), 100: wav(100)}


@main.app.api_route("/fixture/audio/{vid}", methods=["GET", "HEAD"])
async def generated_audio(vid: str, request: Request):
    data = AUDIO[100 if vid == ALBUM_VID else 12]
    header = request.headers.get("range", "")
    start, end = 0, len(data) - 1
    if header:
        match = re.fullmatch(r"bytes=(\d+)-(\d*)", header)
        if match:
            start = int(match[1])
            end = min(end, int(match[2]) if match[2] else end)
    if start > len(data) - 1:
        return Response(status_code=416)
    chunk = data[start : end + 1]
    headers = {"Accept-Ranges": "bytes", "Content-Length": str(len(chunk))}
    if header:
        headers["Content-Range"] = f"bytes {start}-{end}/{len(data)}"
    return Response(
        content=chunk if request.method == "GET" else b"",
        status_code=206 if header else 200,
        media_type="audio/wav",
        headers=headers,
    )


if __name__ == "__main__":
    uvicorn.run(main.app, host="127.0.0.1", port=8765, log_level="warning")
