#!/usr/bin/env bash
# Builds an ITB test suite archive with files at the zip root and no macOS
# metadata (__MACOSX, .DS_Store). The suite README stays out of the archive.
# Usage: scripts/package-suite.sh <suite-dir> [output.zip]
set -euo pipefail
suite_dir="$(cd "${1:?usage: package-suite.sh <suite-dir> [output.zip]}" && pwd)"
out="${2:-$suite_dir.zip}"
case "$out" in /*) ;; *) out="$PWD/$out" ;; esac
rm -f "$out"
(cd "$suite_dir" && zip -q -r -X "$out" . -x '.DS_Store' '*/.DS_Store' '__MACOSX/*' 'README.md')
echo "$out"
