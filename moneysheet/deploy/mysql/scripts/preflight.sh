#!/bin/bash
set -euo pipefail

# Validate every launch, before the official entrypoint can touch the data directory.
for name in MYSQL_ROOT_PASSWORD APP_PASSWORD MIGRATION_PASSWORD; do
  if [[ ! "${!name-}" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "${name} must contain exactly 64 hexadecimal characters" >&2
    exit 1
  fi
done

exec /usr/local/bin/docker-entrypoint.sh "$@"
