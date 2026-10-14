#!/usr/bin/env bash
# Fails when mock-hie's generated Client Registry fixtures don't match
# dha-hie-suite/resources/fixtures.json. Pass --write to regenerate them.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ "${1:-}" = "--write" ]; then
  python3 -I "$ROOT/mock-hie/generate_fixtures.py" "$ROOT/dha-hie-suite/resources/fixtures.json" "$ROOT/mock-hie"
else
  python3 -I "$ROOT/mock-hie/generate_fixtures.py" "$ROOT/dha-hie-suite/resources/fixtures.json" "$ROOT/mock-hie" --check
fi
