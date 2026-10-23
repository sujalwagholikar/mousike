"""Start the deterministic test server and run the Playwright integration checks."""

from pathlib import Path
import os
import subprocess
import sys
import time
import httpx

ROOT = Path(__file__).resolve().parents[1]
browser_script = sys.argv[1] if len(sys.argv) > 1 else "browser.cjs"
if browser_script not in {"browser.cjs", "experience.cjs", "track_fixes.cjs", "search_home.cjs"}:
    raise SystemExit("Choose browser.cjs, experience.cjs, track_fixes.cjs or search_home.cjs")
env = {**os.environ, "NO_PROXY": "127.0.0.1,localhost"}
server = subprocess.Popen(
    [sys.executable, str(ROOT / "tests" / "fixture_server.py")], cwd=ROOT, env=env
)
try:
    for _ in range(80):
        try:
            if (
                httpx.get(
                    "http://127.0.0.1:8765/api/health", trust_env=False
                ).status_code
                == 200
            ):
                break
        except httpx.HTTPError:
            time.sleep(0.1)
    else:
        raise RuntimeError("Test server did not start")
    result = subprocess.run(
        ["node", str(ROOT / "tests" / browser_script)], cwd=ROOT, env=env
    )
    sys.exit(result.returncode)
finally:
    server.terminate()
    server.wait(timeout=10)
