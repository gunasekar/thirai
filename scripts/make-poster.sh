#!/usr/bin/env bash
#
# Standardize a show poster for Thirai.
#
# Every poster in the config should be the same shape and size so the app's
# show list and the home-screen widget render them uniformly (the widget tiles
# are locked to a 3:4 portrait ratio). This produces a 600x800 PNG,
# centre-cropped to 3:4 — big enough to stay crisp (the app displays at ~300x400)
# without bloating the gist.
#
# Usage:
#   scripts/make-poster.sh <input-image> <output.png>
#
# Example:
#   scripts/make-poster.sh ~/Downloads/neeya-naana-raw.jpg neeya-naana.png
#
# Then add the output to the shows gist (it's a git repo):
#   git clone https://gist.github.com/<gist-id>.git && cp neeya-naana.png <clone>/
#   cd <clone> && git add neeya-naana.png && git commit -m "Add poster" && git push
# and reference it as
#   https://gist.githubusercontent.com/<user>/<gist-id>/raw/neeya-naana.png

set -euo pipefail

readonly WIDTH=600
readonly HEIGHT=800   # 3:4 portrait — matches the widget's aspect-locked tiles

if [ "$#" -ne 2 ]; then
  echo "usage: $0 <input-image> <output.png>" >&2
  exit 1
fi

input="$1"
output="$2"

if [ ! -f "$input" ]; then
  echo "error: input '$input' not found" >&2
  exit 1
fi

# Pick ImageMagick 7 (magick) or fall back to the v6 'convert'.
if command -v magick >/dev/null 2>&1; then
  im=(magick)
elif command -v convert >/dev/null 2>&1; then
  im=(convert)
else
  echo "error: ImageMagick not found (install with: brew install imagemagick)" >&2
  exit 1
fi

# -resize WxH^  : scale so the image fills the box (shortest side matches)
# -gravity center -extent WxH : centre-crop to exactly WxH
# -strip        : drop metadata to keep the file small
"${im[@]}" "$input" \
  -resize "${WIDTH}x${HEIGHT}^" \
  -gravity center \
  -extent "${WIDTH}x${HEIGHT}" \
  -strip \
  "$output"

echo "wrote $output (${WIDTH}x${HEIGHT})"
