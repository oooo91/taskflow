#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$HOME/taskflow"
cd "$APP_DIR"

echo "==> healthcheck (inside app container): http://localhost:8081/actuator/health"

for i in $(seq 1 30); do
  if docker compose --env-file .env.prod -f compose.yml -f compose.prod.yml exec -T app \
    curl -fsS http://localhost:8081/actuator/health | grep -q '"status":"UP"'; then
    echo "UP"
    exit 0
  fi
  echo "waiting... ($i)"
  sleep 2
done

echo "healthcheck failed"
docker compose --env-file .env.prod -f compose.yml -f compose.prod.yml logs --tail=200 app || true
exit 1
