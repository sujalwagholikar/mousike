"""Mousike — Sound In motion. Run: python main.py"""

from __future__ import annotations
import asyncio, re, time, uuid, difflib, unicodedata, hashlib, os
from pathlib import Path
from urllib.parse import urlparse
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field, field_validator
from fastapi.responses import (
    FileResponse,
    StreamingResponse,
    Response,
    RedirectResponse,
)
import httpx, uvicorn, yt_dlp
import shutil, tempfile
from urllib.parse import quote
from starlette.background import BackgroundTask
from contextlib import asynccontextmanager
from infrastructure import BoundedCache, SEARCH_CACHE, run_worker, singleflight
from security import SecurityMiddleware
from runtime_support import js_runtime_config, ffmpeg_executable, js_runtime_available
from discovery import interpret_query, ranked_search, suggestions
from embeddings import ENCODER
from lyrics import local_lyrics
from music_semantics import artist_for, NOISE

# Optional local .env is read before model/service configuration. Never served.
try:
    from dotenv import load_dotenv

    load_dotenv(Path(__file__).resolve().parent / ".env")
except ImportError:
    pass


@asynccontextmanager
async def lifespan(app):
    warm = asyncio.create_task(asyncio.to_thread(ENCODER.warm))
    yield
    if not warm.done():
        warm.cancel()


FLIGHTS = {}


ROOT = Path(__file__).resolve().parent
app = FastAPI(title="Mousike — Sound In motion", version="3.0.0", lifespan=lifespan)
app.mount("/assets", StaticFiles(directory=ROOT / "assets"), name="assets")
LIMIT = asyncio.Semaphore(3)
CACHE = BoundedCache(512)
app.add_middleware(SecurityMiddleware)
TTL = 600
VIDEO_ID = re.compile(r"^[a-zA-Z0-9_-]{11}$")
HEADERS = {
    "User-Agent": "MousikeMusic/4.0 (local private music discovery client; https://github.com/yt-dlp/yt-dlp)",
    "Accept": "application/json, image/*;q=0.9, */*;q=0.7",
}
MB_LOCK = asyncio.Lock()
LAST_MB = 0.0
UUID_RE = re.compile(r"^[a-f0-9-]{36}$")


def ydl_options(flat=False):
    return {
        "js_runtimes": js_runtime_config(),
        "remote_components": {"ejs:github"},
        "source_address": "0.0.0.0",
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": flat,
        "noplaylist": True,
        "socket_timeout": 10,
        "retries": 1,
        "fragment_retries": 1,
        "cachedir": False,
        "ignoreerrors": True,
    }


def normalize(item):
    if not item:
        return None
    vid = str(item.get("id") or "")
    if not VIDEO_ID.fullmatch(vid):
        u = item.get("url", "")
        match = re.search(r"(?:v=|youtu\.be/)([\w-]{11})", u)
        if not match:
            return None
        vid = match.group(1)
    thumbs = item.get("thumbnails") or []
    image = next((t.get("url") for t in reversed(thumbs) if t.get("url")), None)
    result = {
