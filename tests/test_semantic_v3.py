"""Offline safety and recommendation behavior regression suite (no internet/Gemini)."""

from pathlib import Path
import pytest
from fastapi.testclient import TestClient
from music_semantics import song_key, same_song, artist_for, diversify
from recommender import Recommender, music_insights
import main


def source_tracks(query, limit=8):
    artists = [
        "Lana Del Rey",
        "Billie Eilish",
        "The Weeknd",
        "Taylor Swift",
        "Coldplay",
        "Shreya Ghoshal",
        "Arijit Singh",
        "Pritam",
        "Adele",
        "Drake",
        "Atif Aslam",
        "Ed Sheeran",
    ]
    n = sum(map(ord, query)) % 7
    out = []
    for i in range(limit):
        art = artists[(i + n) % len(artists)]
        title = f"{art} - Original Track {i + n * 10} (Official Audio)"
        vid = f"{i + n * 8 + 100:011d}"
        out.append(
            {
                "id": vid,
                "title": title,
                "artist": f"{art} - Topic",
                "duration": 221,
                "image": "",
            }
        )
    return out


def test_cross_publisher_song_identity():
    a = {
        "title": "Lana Del Rey - West Coast (Official Music Video)",
        "artist": "Lana Del Rey",
        "id": "abcde123456",
    }
    b = {
        "title": "West Coast | Lana Del Rey Lyrics",
        "artist": "RandomUploader",
        "id": "another7890",
    }
    c = {
        "title": "Lana Del Rey — Summertime Sadness",
        "artist": "Lana Del Rey",
        "id": "xyz12345678",
    }
    assert same_song(a, b)
    assert song_key(a) == "west coast"
    assert not same_song(a, c)


def test_artist_dominance_cannot_override_caps():
    songs = []
    for i in range(30):
        artist = [
            "Lana Del Rey",
            "Lana Del Rey",
            "Lana Del Rey",
            "Coldplay",
            "Billie Eilish",
            "Taylor Swift",
        ][i % 6]
        songs.append(
            {
                "id": f"{i + 100:011d}",
                "title": f"{artist} - Track Number {i} (Official Audio)",
                "artist": artist,
                "duration": 220,
            }
        )
    profile = {
        "artists": ["Lana Del Rey", "Billie Eilish"],
        "genres": ["pop"],
        "moods": ["chill"],
        "languages": ["English"],
    }
    insight = music_insights(
        profile,
        [
            {
                "kind": "like",
                "track": {"title": "one", "artist": "Lana Del Rey"},
                "at": 0,
            }
            for _ in range(80)
        ],
    )
    feed = diversify(
        songs, insight, current={"title": "West Coast", "artist": "Lana Del Rey"}
    )
    assert feed
    assert len({artist_for(t) for t in feed}) >= 3
    assert sum(artist_for(t) == "Lana Del Rey" for t in feed) <= 4
    assert all(artist_for(a) != artist_for(b) for a, b in zip(feed, feed[1:]))


@pytest.mark.asyncio
async def test_zero_key_fully_functional():
    rec = Recommender(source_tracks)

    async def forbidden(*args, **kwargs):
        raise AssertionError("No Gemini call allowed without a key")

    rec.gemini = forbidden
    p = {
        "artists": ["Lana Del Rey", "Coldplay"],
        "genres": ["indie", "pop"],
        "languages": ["English"],
        "moods": ["dreamy"],
        "discovery": 70,
    }
    result = await rec.feed(
        p,
        [],
        {},
        "",
        current={"title": "Lana Del Rey - West Coast", "artist": "Lana Del Rey"},
    )
    assert result["strategy"] == "hybrid-semantic-v3"
    assert result["ai_used"] is False
    assert result["tracks"]
    assert len({artist_for(t) for t in result["tracks"]}) >= 3
    assert not any(
        same_song(t, {"title": "West Coast", "artist": "Lana Del Rey"})
        for t in result["tracks"]
    )
    nxt = await rec.next_track(
        p, [], {}, "", current=result["tracks"][0], exclude=[result["tracks"][0]["id"]]
    )
    assert nxt["track"] and nxt["track"]["id"] != result["tracks"][0]["id"]
    insight = await rec.analyze(p, [], {}, "")
    assert insight["analysis"]["summary"]
    assert insight["analysis"]["taste_tags"]
    assert not insight.get("ai_used", False)


@pytest.mark.asyncio
async def test_gemini_cannot_force_repeated_song():
    rec = Recommender(source_tracks)

    async def fake_gemini(key, prompt, schema, timeout=13):
        assert key == "a" * 30
        return {
            "ranked_ids": [x["id"] for x in reversed(prompt["candidates"])],
            "reason": "An AI-preferred sequence",
            "mood": "dreamy",
        }

    rec.gemini = fake_gemini
    p = {
        "artists": ["The Weeknd", "Billie Eilish", "Coldplay"],
        "genres": ["pop"],
        "moods": ["chill"],
        "languages": ["English"],
    }
    result = await rec.feed(
        p,
        [],
        {},
        "a" * 30,
        current={"title": "Lana Del Rey - West Coast", "artist": "Lana Del Rey"},
    )
    assert result["ai_used"]
    assert result["strategy"] == "gemini-plus-semantic-safeguards"
    assert len(result["tracks"]) >= 4
    assert all(
        not same_song(a, b)
        for i, a in enumerate(result["tracks"])
        for b in result["tracks"][i + 1 :]
    )
    assert all(
        artist_for(a) != artist_for(b)
        for a, b in zip(result["tracks"], result["tracks"][1:])
    )


def test_real_player_route_and_settings():
    client = TestClient(main.app)
    assert client.get("/player.html").status_code == 200
    html = client.get("/player.html").text
    assert "Up next" in html and "Sound In motion" in html
    assert client.get("/assets/js/player.js").status_code == 200
    assert client.get("/assets/js/player-host.js").status_code == 200
    assert client.get("/assets/css/player.css").status_code == 200
    assert all(
        t in client.get("/settings.html").text
        for t in [
            "Every 10 minutes",
            "Every 30 minutes",
            "Every 1 hour",
            "Every 6 hours",
            "Every 1 day",
        ]
    )


def test_local_artist_photos_retained():
    root = Path(__file__).resolve().parents[1]
    assert (root / "assets/artists/lana-del-rey.jpg").is_file()
    assert (root / "assets/artists/the-weeknd.jpg").is_file()
    for html in ["index.html", "onboarding.html"]:
        text = (root / html).read_text()
        assert "/assets/js/artists.js" in text
    assert "lana-del-rey.jpg" in (root / "assets/js/artists.js").read_text()
