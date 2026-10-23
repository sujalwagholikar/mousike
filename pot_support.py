"""Vercel-compatible BgUtils script provider integration for yt-dlp.
Does not store PO tokens or user credentials. Provider source is fetched and
compiled at build time, not downloaded dynamically during an API request.
"""
from pathlib import Path
import os
import importlib.util
from runtime_support import node_executable

ROOT = Path(__file__).resolve().parent
SERVER = ROOT / "pot-provider" / "server"
SCRIPT = SERVER / "build" / "generate_once.js"

def pot_status():
    enabled = os.getenv("MOUSIKE_POT_ENABLED", "true").lower() in ("1", "true", "yes")
    return {
        "enabled": enabled,
        "plugin_installed": importlib.util.find_spec("yt_dlp_plugins.extractor.getpot_bgutil") is not None if importlib.util.find_spec("yt_dlp_plugins") else False,
        "compiled_script_present": SCRIPT.is_file(),
        "node_available": bool(node_executable()),
        "provider": "bgutil script-node",
        "client": "mweb" if enabled else "default",
        "ready": enabled and SCRIPT.is_file() and bool(node_executable()),
        "note": "This checks setup, not successful YouTube access."
    }

def pot_options():
    if os.getenv("MOUSIKE_POT_ENABLED", "true").lower() not in ("1", "true", "yes"):
        return {}
    # yt-dlp plugin discovers the installed Python provider automatically.
    # BgUtils script provider requires compiled JS, build-time node_modules,
    # and an explicit server_home outside the user's home directory.
    return {
        "extractor_args": {
            "youtube": {"player_client": ["mweb"]},
            "youtubepot-bgutilscript": {"server_home": [str(SERVER)]},
        }
    }
