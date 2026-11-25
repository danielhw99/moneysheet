#!/bin/sh
set -eu
read_secret() {
  if [ ! -r "$2" ] || [ ! -s "$2" ]; then
    echo "Required secret file is missing or empty: $1" >&2
    exit 1
  fi
  value=$(cat "$2")
  if [ -z "$value" ]; then echo "Required secret is empty: $1" >&2; exit 1; fi
}
case "${1-}" in
  app)
    read_secret APP_PASSWORD "${APP_PASSWORD_FILE:-/run/secrets/app_password}"
    export DB_PASSWORD="$value"
    read_secret SMTP_PASSWORD "${SMTP_PASSWORD_FILE:-/run/secrets/smtp_password}"
    export SMTP_PASSWORD="$value"
    unset value
    exec java -jar /opt/moneysheet/app.jar
    ;;
  migration)
    shift
    read_secret MIGRATION_PASSWORD "${MIGRATION_PASSWORD_FILE:-/run/secrets/migration_password}"
    export MIGRATION_DB_PASSWORD="$value"
    unset value
    exec java -cp '/opt/migration/classes:/opt/migration/lib/*' com.danielhw99.migration.DatabaseMigration "$@"
    ;;
  *) echo 'Use app or migration info|validate|migrate' >&2; exit 1 ;;
esac
