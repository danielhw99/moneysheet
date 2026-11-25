#!/bin/bash
set -euo pipefail
MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" mysql --protocol=socket -uroot -Nse 'SELECT 1' >/dev/null 2>&1
MYSQL_PWD="$(cat /run/secrets/app_password)" mysql --protocol=TCP -h127.0.0.1 -umoneysheet_app moneysheet_prod -Nse 'SELECT 1' >/dev/null 2>&1
MYSQL_PWD="$(cat /run/secrets/migration_password)" mysql --protocol=TCP -h127.0.0.1 -umoneysheet_migration moneysheet_prod -Nse 'SELECT 1' >/dev/null 2>&1
