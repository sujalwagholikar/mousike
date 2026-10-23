"""Offline integration tests. Run with: python -m pytest -q test_aurora_intelligence.py

Provider calls are mocked; all real runtime dependencies must be installed.
It does NOT verify live YouTube or real Gemini credentials.
"""

from __future__ import annotations
from pathlib import Path

from fastapi.testclient import TestClient
import main
import recommender

APP = TestClient(main.app)
PROFILE = {
    "client_id": "test_browser_id_0123456789",
    "name": "Test User",
    "avatar_data": "secret-photo-do-not-send",
    "artists": ["Arijit Singh", "Shreya Ghoshal"],
    "languages": ["Hindi"],
    "genres": ["Romantic", "Bollywood"],
    "moods": ["Chill"],
    "discovery": 38,
    "country": "India",
}
TRACKS = [
    {
        "id": f"AbcdEf{i:05d}"[:11],
        "title": title,
        "artist": artist,
        "duration": 190,
        "image": "https://example.com/img.png",
        "kind": "video",
    }
    for i, (title, artist) in enumerate(
        [
            ("Tum Hi Ho", "Arijit Singh"),
            ("Sun Raha Hai", "Shreya Ghoshal"),
            ("Dil Diyan Gallan", "Atif Aslam"),
            ("Chaleya", "Arijit Singh"),
            ("Kesariya", "Arijit Singh"),
            ("Lofi Waves", "Unknown"),
            ("Happy", "Other"),
        ]
    )
]
# exactly 11-char valid YouTube-like IDs
for i, t in enumerate(TRACKS):
    t["id"] = f"AbCdEf{str(i).zfill(5)}"
ACTIVITY = [
    {"kind": "play", "track": TRACKS[0], "at": 1700000000},
    {"kind": "like", "track": TRACKS[0], "at": 1999999999},
    {"kind": "skip", "track": TRACKS[2], "at": 1999999999},
    {"kind": "dislike", "track": TRACKS[2], "at": 1999999999},
]
BODY = {"profile": PROFILE, "activity": ACTIVITY, "learned": {}}


def test_no_sqlite_or_profile_on_disk():
    code = (Path(__file__).resolve().parents[1] / "recommender.py").read_text().lower()
    assert "import sqlite3" not in code
    assert "sqlite3.connect" not in code
    result = APP.post("/api/profile", json=PROFILE)
    assert result.status_code == 200
    assert not (Path(__file__).resolve().parents[1] / "aurora_profiles.json").exists()
    assert APP.get("/api/profile/" + PROFILE["client_id"]).status_code == 404


def test_browser_owned_home_plan():
    reply = APP.post(
        "/api/recommendations", json={"profile": PROFILE, "history": ["Arijit Singh"]}
    )
    assert reply.status_code == 200
    data = reply.json()
    assert data["profile_found"]
    assert any(
        any(a in row["query"] for a in PROFILE["artists"]) for row in data["sections"]
    )
    assert "secret-photo" not in str(data)


def test_profile_and_local_analytics():
    data = APP.post("/api/ai/insights", json=BODY).json()
    assert data["model"] is None and data["events"] == 4
    assert data["top_artists"][0]["name"] == "Arijit Singh"
    assert "avatar_data" not in str(data)
    assert "secret-photo" not in str(data)
    assert "name" not in data["profile"]
    assert APP.post("/api/ai/status", json=BODY).json()["storage"] == "browser-only"


def test_feed_no_key(monkeypatch):
    monkeypatch.setattr(main.smart, "search_fn", lambda q, n: TRACKS)
    data = APP.post("/api/ai/feed", json=BODY)
    assert data.status_code == 200, data.text
    assert data.json()["strategy"] == "hybrid-semantic-v3"
    assert len(data.json()["tracks"]) >= 3
    assert data.json()["tracks"][0]["id"] in {t["id"] for t in TRACKS}
    assert APP.post(
        "/api/ai/next", json={**BODY, "exclude": [t["id"] for t in TRACKS[:3]]}
    ).json()["track"]["id"] not in {t["id"] for t in TRACKS[:3]}


def test_gemini_reranks_real_ids_and_never_invents(monkeypatch):
    monkeypatch.setattr(main.smart, "search_fn", lambda q, n: TRACKS)
    observed = {}

    async def fake_gemini(key, prompt, schema, timeout=13.0):
        observed["key"] = key
        observed["payload"] = prompt
        observed["system"] = recommender.SYSTEM_INSTRUCTION
        if prompt.get("task", "").startswith("Rank") or "Sequence" in prompt.get(
            "task", ""
        ):
            return {
                "ranked_ids": ["invented_fake_video_id"]
                + [x["id"] for x in reversed(prompt["candidates"])],
                "reason": "Focused on your preferred artists.",
                "mood": "Chill",
            }
        return {
            "summary": "Likes calm Hindi songs",
            "vibe": "Chill Hindi mix",
            "taste_tags": ["soft"],
            "queries": ["Hindi music songs"],
            "recommendation_reason": "Artist and genre choices",
        }

    monkeypatch.setattr(main.smart, "gemini", fake_gemini)
    data = APP.post(
        "/api/ai/feed", json={**BODY, "api_key": "FAKE_A_GEMINI_TEST_KEY"}
    ).json()
    assert data["ai_used"] and data["tracks"][0]["id"] in {t["id"] for t in TRACKS}
    assert not any(x["id"] == "invented_fake_video_id" for x in data["tracks"])
    assert observed["key"] == "FAKE_A_GEMINI_TEST_KEY"
    assert "secret-photo" not in str(observed["payload"])
    assert "FAKE_A_GEMINI_TEST_KEY" not in str(observed["payload"])
    assert (
        "not invent" in observed["system"].lower()
        or "do not fabricate" in observed["system"].lower()
    )
    ins = APP.post(
        "/api/ai/analyze", json={**BODY, "api_key": "FAKE_A_GEMINI_TEST_KEY"}
    ).json()
    assert ins["ai_used"] and "soft" in ins["analysis"]["taste_tags"]


def test_origin_guard():
    r = APP.post(
        "/api/ai/feed",
        json=BODY,
        headers={"Origin": "https://evil.example", "Host": "testserver"},
    )
    assert r.status_code == 403


def test_static_assets():
    assert APP.get("/").status_code == 200
    assert APP.get("/onboarding.html").status_code == 200
    assert APP.get("/assets/js/smart.js").status_code == 200
    assert APP.get("/assets/css/system.css").status_code == 200
