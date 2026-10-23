"""Behavioral regressions: identity, taste, diversity, multilingual vectors."""

from collections import Counter
import time
import pytest
from embeddings import MetadataEncoder, concepts, fold
from music_semantics import artist_for, same_song, song_key, diversify
from recommender import Recommender, music_insights
from discovery import interpret_query, suggestions


def track(i, artist="Lana Del Rey", title=None, **extra):
    return {
        "id": f"{i:011d}",
        "title": title or f"Original recording {i}",
        "artist": artist,
        "performer": artist,
        "duration": 220,
        **extra,
    }


def test_unicode_song_identity_preserves_devanagari_marks():
    a = track(1, "अरिजीत सिंह", "तुम ही हो")
    b = track(2, "अरिजीत सिंह", "तुम ही हो (Official Audio)")
    c = track(3, "अरिजीत सिंह", "तुम हो")
    assert song_key(a) == "तुम ही हो"
    assert same_song(a, b)
    assert not same_song(a, c)
    assert fold("हिंदी") != "हद"


def test_duplicate_uploads_durations_versions_and_same_title_different_artists():
    a = {
        "id": "a",
        "title": "Lana Del Rey - West Coast (Official Audio)",
        "artist": "Lana Del Rey",
        "duration": 252,
    }
    b = {
        "id": "b",
        "title": "West Coast | Lana Del Rey Lyrics",
        "artist": "RandomUploader",
        "duration": 255,
    }
    assert same_song(a, b)
    assert not same_song(a, {**b, "duration": 390})
    assert not same_song(
        a, {"title": "West Coast (Live)", "artist": "Lana Del Rey", "duration": 252}
    )
    assert not same_song(track(1, "Singer A", "Home"), track(2, "Singer B", "Home"))
    assert not same_song(
        track(1, title="Piano Concerto No. 1"), track(2, title="Piano Concerto No. 2")
    )
    assert not same_song(
        track(1, title="Dream (Part I)"), track(2, title="Dream (Part II)")
    )


def test_optional_isrc_and_fingerprint_evidence():
    assert same_song(
        track(1, "Singer A", "Name", isrc="TEST00000001"),
        track(2, "Singer B", "Other", isrc="TEST00000001"),
    )
    assert same_song(
        track(1, fingerprint="hash", fingerprint_type="chromaprint"),
        track(2, fingerprint="hash", fingerprint_type="chromaprint"),
    )
    assert not same_song(
        track(1, fingerprint="hash", fingerprint_type="chromaprint"),
        track(
            2, fingerprint="hash", fingerprint_type="sha256", title="Different title"
        ),
    )


def test_long_term_short_term_and_taste_axes_are_independent():
    now = time.time()
    activity = [
        {
            "kind": "like",
            "track": track(
                1, "Coldplay", genres=["rock"], languages=["English"], moods=["chill"]
            ),
            "at": now - 30 * 86400,
        },
        {
            "kind": "listen",
            "track": track(
                2,
                "Arijit Singh",
                genres=["bollywood"],
                languages=["Hindi"],
                moods=["romantic"],
            ),
            "at": now,
        },
    ]
    ins = music_insights(
        {"artists": ["Coldplay"], "genres": ["Indie"], "languages": ["English"]},
        activity,
        {},
    )
    axes = ins["preferences"]
    assert axes["long_term"]["coldplay"] > 0
    assert axes["short_term"].get("coldplay", 0) < axes["short_term"]["arijit singh"]
    assert axes["genre"]["indie"] > 0 and axes["genre"]["rock"] > 0
    assert axes["mood"]["romantic"] > 0 and axes["language"]["hindi"] > 0


def test_artist_flood_never_dominates_first_screen_or_full_feed():
    artists = ["Lana Del Rey"] * 9 + [
        "Coldplay",
        "Billie Eilish",
        "The Weeknd",
        "Arijit Singh",
        "Adele",
        "Lorde",
        "Ed Sheeran",
    ]
    pool = [track(i, artists[i % len(artists)]) for i in range(150)]
    now = time.time()
    activity = [
        {"kind": "like", "track": track(900 + i, "Lana Del Rey"), "at": now}
        for i in range(250)
    ]
    ins = music_insights({"artists": ["Lana Del Rey"], "genres": ["pop"]}, activity, {})
    picks = diversify(pool, ins, limit=54)
    assert len(picks) >= 30
    assert max(Counter(artist_for(t) for t in picks[:12]).values()) <= 2
    assert max(Counter(artist_for(t) for t in picks).values()) <= 5
    assert all(artist_for(a) != artist_for(b) for a, b in zip(picks, picks[1:]))


def test_recent_duplicate_and_explicit_negative_feedback_survive_reranking():
    current = track(1, title="West Coast")
    pool = [
        current,
        track(2, title="West Coast (Official Audio)"),
        track(3, "Coldplay"),
        track(4, "Billie Eilish"),
        track(5, "Adele"),
    ]
    events = [{"kind": "not_interested", "track": pool[2], "at": time.time()}]
    ins = music_insights({}, events, {})
    picks = diversify(pool, ins, current=current, ai_order=[t["id"] for t in pool])
    assert picks and {t["id"] for t in picks}.issubset({pool[3]["id"], pool[4]["id"]})


def test_multilingual_concepts_align_across_scripts_without_model_download():
    assert {"dreamy", "indie"} <= concepts("स्वप्निल इंडी गाने")
    encoder = MetadataEncoder()
    a, b, c = encoder.encode(
        [
            "energetic hindi road trip music",
            "ऊर्जावान हिंदी सफर",
            "calm classical piano focus",
        ]
    )
    assert encoder.similarity(a, b) > encoder.similarity(a, c)
    assert all(abs(sum(v * v for v in vector) - 1) < 1e-6 for vector in (a, b, c))


def test_typo_tolerance_and_natural_language_intent():
    plan = interpret_query("dreamy late-night indie songs")
    assert {"dreamy", "sleep", "indie"} <= set(plan["concepts"])
    assert interpret_query("Arjit Singh")["correction"] == "Arijit Singh"
    assert any(s["label"] == "Arijit Singh" for s in suggestions("arjit"))
    assert interpret_query("ऊर्जावान हिंदी सफर")["intent"] == "mood"


@pytest.mark.asyncio
async def test_no_gemini_dependency_and_real_id_guardrails():
    names = [
        "Lana Del Rey",
        "Coldplay",
        "Billie Eilish",
        "The Weeknd",
        "Arijit Singh",
        "Adele",
        "Lorde",
        "Ed Sheeran",
    ]
    pool = [track(i, names[i % len(names)]) for i in range(80)]
    rec = Recommender(lambda q, n: pool)

    async def forbidden(*args, **kwargs):
        raise AssertionError("no key, no call")

    rec.gemini = forbidden
    local = await rec.feed(
        {"artists": names[:2], "genres": ["indie"], "languages": ["English"]}, [], {}
    )
    assert len(local["tracks"]) >= 30 and not local["ai_used"]
    assert {t["id"] for t in local["tracks"]} <= {t["id"] for t in pool}
    flat = [t for s in local["sections"] for t in s["tracks"]]
    assert len({t["id"] for t in flat}) == len(flat)

    async def malicious(key, prompt, schema, timeout=13):
        return {
            "ranked_ids": ["made-up"] + [t["id"] for t in prompt.get("candidates", [])],
            "reason": "Test ranking",
        }

    rec.gemini = malicious
    ai = await rec.feed(
        {"artists": names[:2]},
        [],
        {"summary": "Existing taste summary"},
        "a" * 30,
        current=pool[0],
    )
    assert ai["ai_used"] and "made-up" not in {t["id"] for t in ai["tracks"]}
    assert not any(same_song(pool[0], t) for t in ai["tracks"])


@pytest.mark.asyncio
async def test_gemini_failure_keeps_local_recommendations():
    pool = [
        track(i, ["Coldplay", "Adele", "Lana Del Rey", "Billie Eilish"][i % 4])
        for i in range(24)
    ]
    rec = Recommender(lambda q, n: pool)

    async def fail(*args, **kwargs):
        raise RuntimeError("HTTP 429")

    rec.gemini = fail
    result = await rec.feed({"genres": ["indie"]}, [], {}, "a" * 30)
    assert result["tracks"] and result["ai_used"] is False
    assert result["strategy"] == "hybrid-semantic-fallback"
