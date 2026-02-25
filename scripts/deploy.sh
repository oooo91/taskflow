#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$HOME/taskflow"
cd "$APP_DIR"

echo "==> (0) update repo files"
# 설정 파일(compose/nginx/prom/scripts)이 레포에 있으므로 최신화
git pull --rebase

echo "==> (1) docker login (optional)"
# GHCR이 public이면 GHCR_USER/TOKEN 없어도 됨
if [[ -n "${GHCR_USER:-}" && -n "${GHCR_TOKEN:-}" ]]; then
  docker login ghcr.io -u "$GHCR_USER" -p "$GHCR_TOKEN"
else
  echo "skip docker login (GHCR public or creds not provided)"
fi

echo "==> (2) pull images"
docker compose --env-file .env.prod -f compose.yml -f compose.prod.yml pull

echo "==> (3) up -d"
docker compose --env-file .env.prod -f compose.yml -f compose.prod.yml up -d

echo "==> (4) prune old images"
docker image prune -f
