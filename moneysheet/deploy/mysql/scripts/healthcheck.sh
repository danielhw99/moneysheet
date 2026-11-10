#!/bin/bash
set -euo pipefail

# TCP excludes the temporary socket-only initialization server.
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot -Nse 'SELECT 1' >/dev/null 2>&1
MYSQL_PWD="$APP_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -umoneysheet_app moneysheet_dev -Nse 'SELECT 1' >/dev/null 2>&1
MYSQL_PWD="$MIGRATION_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -umoneysheet_migration moneysheet_dev -Nse 'SELECT 1' >/dev/null 2>&1
