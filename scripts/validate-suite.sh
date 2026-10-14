#!/usr/bin/env bash
# Validates every XML file of an ITB test suite against the GITB TDL schema.
# Usage: scripts/validate-suite.sh <suite-dir>
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
suite="${1:?usage: validate-suite.sh <suite-dir>}"
xsd_dir="$ROOT/.cache/gitb-xsd"
base="https://raw.githubusercontent.com/ISAITB/gitb-types/master/gitb-types-specs/src/main/resources/schema"
mkdir -p "$xsd_dir"
for f in gitb_tdl.xsd gitb_core.xsd; do
  [ -s "$xsd_dir/$f" ] || curl -sfL "$base/$f" -o "$xsd_dir/$f"
done

expected="testSuite.xml scriptlets/normaliseRequest.xml scriptlets/formField.xml scriptlets/emrBaseUrl.xml
scriptlets/receiveAndForward.xml scriptlets/checkTokenRequest.xml scriptlets/checkBearer.xml
scriptlets/checkPatientQuery.xml testCases/AUTH-01.xml testCases/CR-01.xml testCases/CR-02.xml testCases/CR-03.xml"
missing=0
for f in $expected; do
  [ -f "$suite/$f" ] || { echo "missing $suite/$f"; missing=1; }
done
[ "$missing" -eq 0 ] || exit 1

find "$suite" -name '*.xml' -print0 | xargs -0 xmllint --noout --schema "$xsd_dir/gitb_tdl.xsd"
