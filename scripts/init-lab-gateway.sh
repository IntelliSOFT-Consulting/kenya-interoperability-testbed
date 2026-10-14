#!/usr/bin/env bash
# Creates nginx/lab/ (gitignored) from the committed examples, without
# overwriting files that already exist.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/nginx/lab"
for f in lab-vendors.map lab-allowlist.conf; do
  if [ ! -f "$ROOT/nginx/lab/$f" ]; then
    cp "$ROOT/nginx/$f.example" "$ROOT/nginx/lab/$f"
    echo "created nginx/lab/$f"
  fi
done
