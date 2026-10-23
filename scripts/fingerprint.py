"""Optional fingerprint metadata for a recording you have permission to analyze.

Requires Chromaprint's fpcalc executable. This tool reads a local file only; it
never downloads audio or sends it to a service. Exact Chromaprint matches are an
additional identity signal, not a substitute for duration/version checks.
"""

import argparse
import json
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path)
    args = parser.parse_args()
    if not args.file.is_file():
        parser.error("Choose an existing local audio file")
    tool = shutil.which("fpcalc")
    if not tool:
        parser.error("Install Chromaprint and put fpcalc on PATH")
    result = subprocess.run(
        [tool, "-json", str(args.file.resolve())],
        check=True,
        capture_output=True,
        text=True,
        timeout=90,
    )
    data = json.loads(result.stdout)
    print(
        json.dumps(
            {
                "fingerprint_type": "chromaprint",
                "fingerprint": data["fingerprint"],
                "duration": data.get("duration"),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
