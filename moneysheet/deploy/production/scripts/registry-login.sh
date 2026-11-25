#!/usr/bin/env bash
# Run interactively on Ubuntu. Never put the PAT in arguments, env files or chat.
set +x
set -euo pipefail
[[ $# == 1 && "$1" =~ ^[a-zA-Z0-9-]+$ ]] || { echo 'Usage: bash registry-login.sh GITHUB_USERNAME' >&2; exit 2; }
[[ -t 0 ]] || { echo 'Interactive terminal required.' >&2; exit 2; }
umask 077
config="$HOME/.config/moneysheet-registry"
mkdir -p "$config"
chmod 700 "$config"
trap 'unset token' EXIT
read -rsp 'Dedicated PAT classic (read:packages only): ' token
printf '\n'
[[ -n "$token" ]] || exit 2
printf '%s' "$token" | docker --config "$config" login ghcr.io --username "$1" --password-stdin >/dev/null
unset token
chmod 600 "$config/config.json"
echo 'GHCR authentication saved in a dedicated private Docker config.'
