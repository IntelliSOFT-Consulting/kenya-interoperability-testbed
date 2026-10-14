#!/usr/bin/env bash
# Checks that every lab-* network alias resolves inside the compose network.
set -uo pipefail
fail=0
for a in lab-service lab-ui lab-ui-proxy lab-db lab-cache lab-fhir; do
  if docker compose exec -T mock-hie getent hosts "$a" >/dev/null; then echo "ok   $a"; else echo "FAIL $a"; fail=1; fi
done
exit "$fail"
