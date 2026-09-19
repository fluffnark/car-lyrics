#!/usr/bin/env python3
"""Refresh the private prototype's compact Sing King title and playlist snapshot.

Requires yt-dlp. This stores video IDs/titles and playlist membership, never media.
"""

import json
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


CHANNEL = "https://www.youtube.com/@singkingkaraoke"
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/sing_king_library.json"
COLLECTIONS = [
    ("genre", "Pop", "Pop Karaoke | Sing King Karaoke"),
    ("genre", "Rock", "Rock Karaoke | Sing King Karaoke"),
    ("genre", "Country", "Country Karaoke | Sing King Karaoke"),
    ("genre", "Jazz", "Jazz | Sing King Karaoke"),
    ("genre", "Musicals", "Musicals | Sing King Karaoke"),
    ("genre", "K-pop", "Korean Pop Karaoke | Sing King Karaoke"),
    ("genre", "80s", "80s Karaoke | Sing King Karaoke"),
    ("genre", "90s", "Nothin' But 90s Karaoke | Sing King Karaoke"),
    ("genre", "Indie pop", "Indie Pop Karaoke | Sing King Karaoke"),
    ("genre", "Duets", "Dynamic Duets | Sing King Karaoke"),
    ("album", "Lover · Taylor Swift", "Taylor Swift Full Lover Album | Sing King Karaoke"),
    ("album", "K-12 · Melanie Martinez", "K-12 Full Album | Sing King Karaoke"),
]


def rows(url, limit):
    output = subprocess.check_output(
        ["yt-dlp", "--flat-playlist", "--playlist-end", str(limit),
         "--print", "%(id)s\t%(title)s", url], text=True
    )
    return [line.split("\t", 1) for line in output.splitlines() if "\t" in line]


def main():
    songs = {}
    for video_id, title in rows(f"{CHANNEL}/videos", 5000):
        if len(video_id) == 11 and title:
            songs[video_id] = title

    playlists = dict(rows(f"{CHANNEL}/playlists", 250))
    by_title = {title: playlist_id for playlist_id, title in playlists.items()}

    def load_collection(spec):
        kind, label, title = spec
        playlist_id = by_title.get(title)
        if not playlist_id:
            raise RuntimeError(f"Playlist missing: {title}")
        videos = [(video_id, video_title) for video_id, video_title in
                  rows(f"https://www.youtube.com/playlist?list={playlist_id}", 250)
                  if len(video_id) == 11]
        return kind, label, playlist_id, videos

    with ThreadPoolExecutor(max_workers=4) as pool:
        loaded = list(pool.map(load_collection, COLLECTIONS))

    collections = []
    for kind, label, playlist_id, videos in loaded:
        ids = []
        for video_id, title in videos:
            songs.setdefault(video_id, title)
            if video_id not in ids:
                ids.append(video_id)
        collections.append({"kind": kind, "title": label, "playlistId": playlist_id, "ids": ids})

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps({"songs": [{"id": key, "title": value} for key, value in songs.items()],
                                  "collections": collections}, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"Wrote {len(songs)} song titles and {len(collections)} collections to {OUTPUT}")


if __name__ == "__main__":
    main()
