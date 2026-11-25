#!/usr/bin/env bash
# Pull only: no Compose up, DB access, migration, or port changes.
set -euo pipefail
[[ $# == 4 ]] || { echo 'Usage: bash pull-images.sh FE_DIGEST BE_DIGEST MIGRATION_DIGEST ABS_PRODUCTION_DIR' >&2; exit 2; }
refs=("$1" "$2" "$3")
names=(moneysheet-fe moneysheet-be moneysheet-migration)
for i in 0 1 2; do
  [[ "${refs[$i]}" =~ ^ghcr\.io/danielhw99/${names[$i]}@sha256:[a-f0-9]{64}$ ]] || { echo 'Expected approved image with a full sha256 digest.' >&2; exit 2; }
done
production="$4"
[[ "$production" == /* && -d "$production" && -f "$production/.env" && ! -L "$production/.env" ]] || { echo 'Existing absolute production directory and regular .env required.' >&2; exit 2; }
config="$HOME/.config/moneysheet-registry"
[[ -f "$config/config.json" ]] || { echo 'Run registry-login.sh first.' >&2; exit 2; }
revisions=()
for i in 0 1 2; do
  docker --config "$config" pull --platform linux/amd64 "${refs[$i]}"
  platform=$(docker --config "$config" image inspect "${refs[$i]}" --format '{{.Os}}/{{.Architecture}}')
  [[ "$platform" == linux/amd64 ]] || { echo 'Unexpected platform.' >&2; exit 1; }
  revision=$(docker --config "$config" image inspect "${refs[$i]}" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')
  [[ "$revision" =~ ^[a-f0-9]{40}$ ]] || { echo 'Source revision missing.' >&2; exit 1; }
  revisions+=("$revision")
  source=$(docker --config "$config" image inspect "${refs[$i]}" --format '{{index .Config.Labels "org.opencontainers.image.source"}}')
  expected=https://github.com/danielhw99/moneysheet
  if [[ "$i" == 0 ]]; then expected=https://github.com/danielhw99/moneysheet_FE; fi
  [[ "$source" == "$expected" ]] || { echo 'Unexpected source repository.' >&2; exit 1; }
done
[[ "${revisions[1]}" == "${revisions[2]}" ]] || { echo 'BE and migration revisions must match; .env unchanged.' >&2; exit 1; }
# Update image keys only after every pull and validation succeeds. Never source .env.
python3 - "$production" "${refs[@]}" "${revisions[@]}" <<'PY'
import fcntl, os, re, sys, tempfile, uuid
from pathlib import Path
from datetime import datetime, timezone
root=Path(sys.argv[1]); refs=sys.argv[2:5]; revisions=sys.argv[5:8]
keys=('FE_IMAGE','BE_IMAGE','MIGRATION_IMAGE')
path=root/'.env'
# Keep this sidecar file: locking .env itself would lock the old inode after replace.
lock_fd=os.open(root/'.env.lock',os.O_RDWR|os.O_CREAT,0o600)
with os.fdopen(lock_fd,'a') as lock:
    fcntl.flock(lock,fcntl.LOCK_EX)
    snapshot=path.read_bytes()
    original=snapshot.decode('utf-8')
    for key in keys:
        # Reject alternate Compose declarations, including colon, export and whitespace.
        declarations=re.findall(r'^[ \t]*(?:export[ \t]+)?'+key+r'(?=[ \t]*(?:[=:]|$|#))[^\r\n]*',original,re.M)
        if len(declarations)!=1 or not declarations[0].startswith(key+'='):
            raise SystemExit('Expected exactly one canonical '+key+'= declaration; no changes made.')
    updated=original
    for key,ref in zip(keys,refs):
        updated=re.sub(r'^'+key+r'=.*$',lambda _:key+'='+ref,updated,flags=re.M)
    releases=root/'releases'; releases.mkdir(mode=0o700,exist_ok=True)
    stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:8]
    def private_write(path,content):
        fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as f:f.write(content)
    fd,tmp=tempfile.mkstemp(prefix='.env-images-',dir=root)
    try:
        with os.fdopen(fd,'w') as f:f.write(updated)
        private_write(releases/(stamp+'.env.backup'),original)
        if path.is_symlink() or path.read_bytes()!=snapshot:
            raise SystemExit('.env changed during update; replacement aborted. Retry after reviewing concurrent edits.')
        os.replace(tmp,path)
        private_write(releases/(stamp+'-images.env'),'# FE revision='+revisions[0]+'\n# BE/migration revision='+revisions[1]+'\n'+'\n'.join(k+'='+v for k,v in zip(keys,refs))+'\n')
    finally:
        if os.path.exists(tmp):os.unlink(tmp)
print('Three digest references recorded; previous .env backed up privately. No services started.')
PY
