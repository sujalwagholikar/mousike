"""Independent, decayed preference axes built from bounded meaningful events."""

from __future__ import annotations
from collections import Counter
import math
import time
from embeddings import fold, music_tags, GENRES, LANGUAGES
from music_semantics import artist_for

WEIGHTS = {
    "play": 0.15,
    "listen": 1.4,
    "complete": 2.5,
    "skip": -1.0,
    "like": 6.0,
    "unlike": -3.0,
    "dislike": -9.0,
    "not_interested": -12.0,
    "more_like": 5.0,
    "less_artist": -8.0,
    "follow": 6.0,
    "unfollow": -5.0,
    "playlist_add": 3.0,
    "search": 0.1,
}


def preference_axes(profile, rows):
    now = time.time()
    axes = {
        k: Counter()
        for k in ("long_term", "short_term", "genre", "mood", "language", "artist")
    }
    for category, field in [
        ("genre", "genres"),
        ("mood", "moods"),
        ("language", "languages"),
    ]:
        for value in profile.get(field, []):
            axes[category][fold(value)] += 6.0
    for artist in profile.get("artists", []):
        axes["artist"][fold(artist)] += 6.0
        axes["long_term"][fold(artist)] += 6.0
    # Cap repetitive signals per day/recording/type: 200 plays of one song do not
    # contribute 200 times as much evidence as one meaningful listen.
    counts = Counter()
    for row in rows:
        age = max(0, now - row["at"])
        artist = fold(artist_for(row, profile.get("artists", [])))
        identity = (int(row["at"] // 86400), fold(row["title"]), artist, row["kind"])
        counts[identity] += 1
        weight = WEIGHTS.get(row["kind"], 0) / math.sqrt(counts[identity])
        weight *= 2 ** (-age / (45 * 86400))
        if artist and artist != "unknown artist":
            axes["long_term"][artist] += weight
            axes["artist"][artist] += weight
            axes["short_term"][artist] += weight * 2 ** (-age / (3 * 3600))
        tags = music_tags(row)
        for tag in tags:
            category = (
                "genre" if tag in GENRES else "language" if tag in LANGUAGES else "mood"
            )
            axes[category][tag] += weight

    def clamp(values):
        return {
            k: round(math.copysign(min(12.0, math.log1p(abs(v)) * 3.0), v), 3)
            for k, v in values.most_common(40)
            if abs(v) > 0.02
        }

    return {k: clamp(v) for k, v in axes.items()}
