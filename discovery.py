"""Typo-tolerant query interpretation and personalized search, without an AI key."""

from __future__ import annotations
from difflib import get_close_matches, SequenceMatcher
from embeddings import CONCEPTS, concepts, fold, ENCODER, metadata_text
from music_semantics import KNOWN_ARTISTS, same_song, artist_for


def interpret_query(raw):
    query = " ".join(str(raw).split())[:120]
    normalized = fold(query)
    artist = next((a for a in KNOWN_ARTISTS if fold(a) == normalized), None)
    correction = ""
    if not artist and len(normalized) > 4:
        # Correct only high-confidence whole-artist matches, never arbitrary titles.
        word_matches = [
            a
            for a in KNOWN_ARTISTS
            if any(
                SequenceMatcher(None, normalized, w).ratio() >= 0.85
                for w in fold(a).split()
                if len(w) > 3
            )
        ]
        if len(word_matches) == 1:
            artist = word_matches[0]
            correction = artist
        matches = get_close_matches(
            normalized, [fold(a) for a in KNOWN_ARTISTS], n=1, cutoff=0.84
        )
        if matches:
            artist = next(a for a in KNOWN_ARTISTS if fold(a) == matches[0])
            correction = artist
    tags = concepts(query)
    descriptive = bool(tags) and (
        len(normalized.split()) >= 3 or any(ord(c) > 127 for c in normalized)
    )
    if artist:
        expanded = artist + " official audio"
    elif descriptive:
        # Concepts are shared across supported scripts, helping English metadata
        # retrieval for requests such as "ऊर्जावान हिंदी सफर".
        parts = sorted(tags)
        names = [a for a in KNOWN_ARTISTS if fold(a) in normalized]
        expanded = " ".join(names + parts) + " songs official audio"
    else:
        expanded = query
    return {
        "query": query,
        "expanded_query": expanded[:120],
        "correction": correction,
        "concepts": sorted(tags),
        "intent": "mood" if descriptive else "artist" if artist else "title",
    }


def ranked_search(tracks, query, profile=None, activity=None):
    profile = profile or {}
    fav = profile.get("artists", []) if isinstance(profile.get("artists"), list) else []
    vectors = ENCODER.encode([query] + [metadata_text(t) for t in tracks])
    query_words = set(fold(query).split())
    scored = []
    for t, v in zip(tracks, vectors[1:]):
        words = set(fold(t.get("title", "") + " " + t.get("artist", "")).split())
        overlap = len(query_words & words) / max(1, len(query_words))
        score = overlap * 3 + ENCODER.similarity(vectors[0], v) * 3
        score += (
            0.4 if artist_for(t, fav).casefold() in {a.casefold() for a in fav} else 0
        )
        score += (
            SequenceMatcher(None, fold(query), fold(t.get("title", ""))).ratio() * 0.5
        )
        scored.append((score, t))
    output = []
    for _, t in sorted(scored, key=lambda x: x[0], reverse=True):
        if not any(same_song(t, x, fav) for x in output):
            output.append(t)
    return output


def suggestions(query, public_tracks=()):
    normalized = fold(query)
    if len(normalized) < 2:
        return []
    values = []
    for artist in KNOWN_ARTISTS:
        name = fold(artist)
        if (
            normalized in name
            or SequenceMatcher(None, normalized, name).ratio() > 0.66
            or any(
                SequenceMatcher(None, normalized, w).ratio() >= 0.78
                for w in name.split()
                if len(w) > 3
            )
        ):
            values.append({"label": artist, "type": "artist", "query": artist})
    for track in public_tracks:
        if normalized in fold(track.get("title", "")):
            values.append(
                {
                    "label": track["title"][:120],
                    "type": "song",
                    "query": track["title"][:120],
                    "id": track.get("id"),
                }
            )
    for concept in CONCEPTS:
        if normalized in fold(concept) and len(values) < 7:
            values.append(
                {
                    "label": concept.title() + " music",
                    "type": "query",
                    "query": concept + " music",
                }
            )
    seen = set()
    return [x for x in values if not (x["query"] in seen or seen.add(x["query"]))][:8]
