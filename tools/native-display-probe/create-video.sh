#!/usr/bin/env bash
set -euo pipefail
probe_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$probe_dir/src/main/res/raw"
ffmpeg -hide_banner -loglevel error -y \
  -f lavfi -i 'testsrc2=size=800x400:rate=30:duration=6' \
  -vf "drawbox=x=0:y=150:w=800:h=100:color=black@0.8:t=fill,drawtext=text='NATIVE VIDEO - NO SCREEN CAPTURE':fontcolor=white:fontsize=30:x=(w-tw)/2:y=(h-th)/2" \
  -c:v libx264 -pix_fmt yuv420p -crf 24 -an -movflags +faststart \
  "$probe_dir/src/main/res/raw/native_probe.mp4"
