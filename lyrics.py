"""Read operator-supplied licensed LRC files only; no lyrics scraping."""

from pathlib import Path
import hashlib
import re
from embeddings import fold


def lyrics_key(title, artist):
    return hashlib.sha256((fold(artist) + "|" + fold(title)).encode()).hexdigest()[:24]


def parse_lrc(text):
    lines = []
    for row in str(text).splitlines()[:2500]:
        stamps = re.findall(r"\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]", row)
        value = re.sub(r"\[[^]]*\]", "", row).strip()[:400]
        for minute, second, fraction in stamps:
            if int(second) > 59:
                continue
            at = (
                int(minute) * 60
                + int(second)
                + (int(fraction) / 10 ** len(fraction) if fraction else 0)
            )
            if value:
                lines.append({"at": round(at, 3), "text": value})
    return sorted(lines, key=lambda x: x["at"])[:2500]


def local_lyrics(root, title, artist):
    path = Path(root) / "lyrics" / (lyrics_key(title, artist) + ".lrc")
    if not path.is_file() or path.stat().st_size > 400_000:
        return {
            "available": False,
            "lines": [],
            "source": "No licensed lyrics supplied",
        }
    return {
        "available": True,
        "lines": parse_lrc(path.read_text(encoding="utf-8-sig")),
        "source": "Lyrics supplied by this installation",
    }
