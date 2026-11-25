#!/bin/bash
set -euo pipefail
for pair in MYSQL_ROOT_PASSWORD:mysql_root_password APP_PASSWORD:app_password MIGRATION_PASSWORD:migration_password; do
 name=${pair%%:*}; file=/run/secrets/${pair#*:}
 if [[ ! -r "$file" ]]; then echo "Required secret file is missing: $name" >&2; exit 1; fi
 value=$(cat "$file")
 if [[ ! "$value" =~ ^[0-9a-fA-F]{64}$ ]]; then echo "$name must contain exactly 64 hexadecimal characters" >&2; exit 1; fi
 export "$name=$value"
done
unset value
exec /usr/local/bin/docker-entrypoint.sh "$@"
