#!/bin/bash
set -euo pipefail

# Run only against a new data volume. Generated hex values are safe SQL literals.
for name in APP_PASSWORD MIGRATION_PASSWORD; do
  if [[ ! "${!name}" =~ ^[0-9a-fA-F]{64}$ ]]; then
    echo "${name} must contain exactly 64 hexadecimal characters" >&2
    exit 1
  fi
done

export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
mysql --protocol=socket -uroot <<SQL
CREATE USER 'moneysheet_app'@'%' IDENTIFIED BY '${APP_PASSWORD}';
CREATE USER 'moneysheet_migration'@'%' IDENTIFIED BY '${MIGRATION_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON moneysheet_dev.* TO 'moneysheet_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON moneysheet_dev.* TO 'moneysheet_migration'@'%';
SQL
unset MYSQL_PWD
