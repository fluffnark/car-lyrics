#!/usr/bin/env python3
"""Refresh additional public karaoke title metadata used by Spotify matching.

Requires yt-dlp. Downloads no video/audio. Existing browse collections stay intact.
Only replaces the asset after every configured provider has returned valid titles.
"""

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess


PROVIDERS = {
    "KaraFun": "https://www.youtube.com/@karafun/videos",
    "Sing King": "https://www.youtube.com/@singkingkaraoke/videos",
    "Sing2Piano": "https://www.youtube.com/@sing2piano/videos",
    "Funbox Karaoke": "https://www.youtube.com/@FunboxKaraoke/videos",
}
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/karaoke_matching_library.json"


def fetch(spec, limit):
    name, url = spec
    result = subprocess.run(
        ["yt-dlp", "--flat-playlist", "--playlist-end", str(limit),
         "--socket-timeout", "15", "--retries", "1", "--extractor-retries", "1",
         "--print", "%(id)s\t%(title)s", url],
        capture_output=True, text=True, check=True, timeout=900,
    )
    songs = []
    for line in result.stdout.splitlines():
        video_id, separator, title = line.partition("\t")
        if separator and re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id) and title.strip():
            songs.append({"id": video_id, "title": title.strip()})
    if not songs:
        raise RuntimeError(f"No valid title metadata returned by {name}")
    return {"name": name, "url": url, "count": len(songs), "limit": limit}, songs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--limit", type=int, default=15000, help="Maximum titles per provider (default: 15000)")
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    if args.limit < 1:
        parser.error("--limit must be positive")
    with ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(lambda spec: fetch(spec, args.limit), PROVIDERS.items()))
    songs = {song["id"]: song for _, titles in results for song in titles}
    root = {"updatedAt": datetime.now(timezone.utc).isoformat(),
            "providers": [metadata for metadata, _ in results], "songs": list(songs.values())}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix(".tmp")
    temporary.write_text(json.dumps(root, ensure_ascii=False, separators=(",", ":")) + "\n")
    temporary.replace(args.output)
    print(f"Wrote {len(songs)} titles from {len(results)} providers to {args.output}")


if __name__ == "__main__":
    main()
