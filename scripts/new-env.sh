#!/usr/bin/env bash
# Creates .env for `docker compose up` with fresh random secrets (Linux, macOS, Git Bash, WSL).
#   scripts/new-env.sh [--force]
set -euo pipefail

ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env"
if [ -e "$ENV_FILE" ] && [ "${1:-}" != "--force" ]; then
  echo ".env already exists. To replace it run with --force, then 'docker compose down -v' so MySQL and Redis start with the new passwords." >&2
  exit 1
fi

secret() { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }
cat > "$ENV_FILE" <<ENV
MYSQL_PASSWORD=$(secret 16)
REDIS_PASSWORD=$(secret 16)
ONEDAY_JWT_SECRET=$(secret 48)
ONEDAY_LOCATION_SECRET=$(secret 48)
MEDIA_ACCESS_KEY=oneday$(secret 6)
MEDIA_SECRET_KEY=$(secret 20)
ONEDAY_BOOTSTRAP_ADMIN_IDS=
ENV
echo "Wrote $ENV_FILE"
