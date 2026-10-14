#!/usr/bin/env bash
# Checks the merged public overlay: 443 on lab-gateway is the only published port, and
# CALLBACK_ROOT_URL points at the public UI host.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ITB_UI_HOST="${ITB_UI_HOST:-ui.lab.localhost}"
actual=$(docker compose -f "$ROOT/docker-compose.yml" -f "$ROOT/docker-compose.public.yml" config --format json \
  | jq -c '{srv: .services["gitb-srv"].ports, ui: .services["gitb-ui-proxy"].ports, mock: .services["mock-hie"].ports,
            gw: [.services["lab-gateway"].ports[].published], cb: .services["gitb-srv"].environment.CALLBACK_ROOT_URL,
            published: [.services[] | .ports[]?.published]}')
expected="{\"srv\":null,\"ui\":null,\"mock\":null,\"gw\":[\"443\"],\"cb\":\"https://$ITB_UI_HOST\",\"published\":[\"443\"]}"
if [ "$actual" = "$expected" ]; then echo "ok   public overlay: $actual"; else echo "FAIL public overlay: expected $expected, got $actual"; exit 1; fi
