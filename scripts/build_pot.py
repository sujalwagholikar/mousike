"""Build BgUtils provider on Vercel (pinned tag 2.0.2)."""
from pathlib import Path
import os
import subprocess
import shutil

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT / "pot-provider"
SERVER = REPO / "server"

def run(args, cwd=None):
    print("[Mousike POT build]", " ".join(map(str,args)), flush=True)
    subprocess.run(args, cwd=cwd, check=True, timeout=240)

def main():
    if os.getenv("MOUSIKE_POT_ENABLED", "true").lower() in ("false","0","no"):
        print("PO Token build skipped by MOUSIKE_POT_ENABLED")
        return
    if not (SERVER / "package.json").is_file():
        run(["git", "clone", "--depth", "1", "--branch", "2.0.2", "https://github.com/Brainicism/bgutil-ytdlp-pot-provider.git", str(REPO)])
    npm = shutil.which("npm")
    npx = shutil.which("npx")
    if not npm or not npx:
        raise RuntimeError("Node.js npm/npx not available in build environment")
    run([npm, "ci", "--no-audit", "--no-fund"], cwd=SERVER)
    run([npx, "tsc"], cwd=SERVER)
    if not (SERVER / "build" / "generate_once.js").is_file():
        raise RuntimeError("BgUtils transpilation did not produce build/generate_once.js")
    print("[Mousike POT build] provider built successfully", flush=True)

if __name__ == "__main__":
    main()
