"""Resolve packaged Node.js and FFmpeg in local and Vercel Python environments.

No dynamic binary downloads; build-time Python dependencies provide bundled binaries.
"""
from __future__ import annotations

from functools import lru_cache
import os
from pathlib import Path
import shutil


@lru_cache(maxsize=1)
def node_executable() -> str | None:
    """Use bundled Node on Vercel, otherwise the user-installed Node."""
    configured = os.getenv("MOUSIKE_NODE_PATH", "").strip()
    if configured:
        return configured if Path(configured).is_file() else None
    # The PyPI nodejs-wheel package stores the actual ELF executable here.
    # Using the real file (not the console Python wrapper) works with yt-dlp's
    # persistent stdio communication with the EJS challenge solver.
    try:
        from nodejs_wheel.executable import ROOT_DIR
        binary = Path(ROOT_DIR) / ("node.exe" if os.name == "nt" else "bin/node")
        if binary.is_file():
            return str(binary)
    except ImportError:
        pass
    return shutil.which("node")


def js_runtime_config() -> dict:
    binary = node_executable()
    return {"node": {"path": binary}} if binary else {"node": {}}


def js_runtime_available() -> bool:
    return bool(node_executable())


@lru_cache(maxsize=1)
def ffmpeg_executable() -> str | None:
    configured = os.getenv("MOUSIKE_FFMPEG_PATH", "").strip()
    if configured:
        return configured if Path(configured).is_file() else None
    binary = shutil.which("ffmpeg")
    if binary:
        return binary
    try:
        import imageio_ffmpeg
        binary = imageio_ffmpeg.get_ffmpeg_exe()
        return binary if binary and Path(binary).is_file() else None
    except (ImportError, RuntimeError, OSError):
        return None
