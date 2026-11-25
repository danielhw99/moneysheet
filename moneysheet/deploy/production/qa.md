# 운영 구성의 격리 검증


Ubuntu/POSIX 셸에서 실행합니다. **운영 실행 절차를 검증에 사용하지 않습니다.** Docker Compose2.24.4 이상(`!override` 지원), Python3, openssl, curl 및 사전에 빌드/다운로드한 FE·BE·migration 이미지가 필요합니다. 기존 개발/운영 컨테이너·DB·비밀 파일은 사용하지 않습니다. 18022/18443이 이미 사용 중이면 해당 서비스를 종료하지 말고 검증을 중단합니다.

## 1. 시험 환경 준비

현재 디렉토리는 BE 저장소의 `moneysheet/deploy/production`입니다. 다음 블록은 **같은 셸**에서 순서대로 실행합니다. 이미지 태그는 실제 검증할 세 이미지로 바꾸되 운영 데이터 설정은 복사하지 않습니다.

```sh
set -eu
umask 077
docker compose version
qa_project="moneysheet-qa-$(date +%Y%m%d%H%M%S)-$$"
qa_dir=$(mktemp -d "${TMPDIR:-/tmp}/moneysheet-qa.XXXXXX")
printf '%s\n' "$qa_project" > "$qa_dir/project-name"
printf 'QA project: %s\nQA files: %s\n' "$qa_project" "$qa_dir"
mkdir -m 700 "$qa_dir/secrets"
export QA_SECRETS_DIR="$qa_dir/secrets"
for qa_secret in mysql_root_password app_password migration_password smtp_password; do
  openssl rand -hex 32 > "$QA_SECRETS_DIR/$qa_secret"
  chmod 0444 "$QA_SECRETS_DIR/$qa_secret"
done
cat > "$qa_dir/qa.env" <<'ENV'
FE_IMAGE=moneysheet-fe:local
BE_IMAGE=moneysheet-be:local
MIGRATION_IMAGE=moneysheet-migration:local
SITE_ADDRESS=localhost
ACME_EMAIL=qa@example.invalid
SMTP_HOST=smtp.example.invalid
SMTP_USERNAME=qa@example.invalid
MONEYSHEET_MAIL_FROM=qa@example.invalid
ENV
qa() {
  docker compose -p "$qa_project" --env-file "$qa_dir/qa.env" \
    -f compose.prod.yaml -f compose.qa.yaml "$@"
}
qa --profile tools config --format json > "$qa_dir/resolved.json"
python3 - "$qa_project" "$QA_SECRETS_DIR" "$qa_dir/resolved.json" <<'PY'
import json,sys
from pathlib import Path
project,secrets,config=sys.argv[1:]
assert project.startswith('moneysheet-qa-')
c=json.loads(Path(config).read_text())
assert c['name']==project
ports=c['services']['frontend']['ports']
assert {(p['host_ip'],str(p['published']),p['target']) for p in ports}=={('127.0.0.1','18022',80),('127.0.0.1','18443',443)}
assert len(ports)==2
for service in ['be','mysql','migration']:
    assert not c['services'][service].get('ports')
for volume in c['volumes'].values():
    assert not volume.get('external') and volume['name'].startswith(project+'_')
for secret in c['secrets'].values():
    assert Path(secret['file']).parent==Path(secrets)
assert c['services']['frontend']['environment']['SITE_ADDRESS']=='localhost'
print('PASS: QA project, loopback-only ports, isolated secrets/volumes')
PY
```

**성공 기준:** 설정 검사에서 PASS 출력. 실패하면 기동하지 않습니다.

`.env` 대신 시험 전용 `--env-file`을 명시하고, override가 secrets 경로·localhost·시험 SMTP를 고정합니다. 시험 비밀값은 부모700/파일0444로 보호합니다. 같은 DB 이름 `moneysheet_prod`라도 별도 MySQL 컨테이너와 프로젝트별 볼륨을 사용하므로 운영 DB와 분리됩니다. `-p`만으로 포트가 변경되는 것은 아니며 `ports: !override`가 필수입니다.

## 2. 최초 기동과 명시적 마이그레이션

```sh
qa up -d --wait --wait-timeout 180 mysql
qa run --rm migration info
qa run --rm migration migrate
qa run --rm migration validate
qa run --rm migration migrate
qa run --rm migration info > "$qa_dir/migration-before.log"
qa up -d --wait --wait-timeout 240 be frontend
qa ps
```

**성공 기준:** MySQL healthy → V1~최신 SQL(현재V13) migrate/validate 종료코드0 → 두 번째 migrate는 추가 적용 없음 → BE healthy·frontend running. migration은 기본 up에서 실행되지 않습니다. 실패 시 중단하고 원인을 확인하며 자동 repair·볼륨 삭제·반복 migrate로 우회하지 않습니다.

## 3. 로컬 인증서·프록시 확인

시험 설정은 `localhost`이며 Caddy가 내부 CA 인증서를 생성합니다. 공개 도메인 ACME 인증서를 발급하지 않고 호스트 신뢰 저장소도 변경하지 않습니다. 아래 `--insecure`는 **localhost 시험 curl 호출에만** 적용합니다. 운영 FE/BE의 TLS 검증·Secure 쿠키를 끄지 않습니다. 브라우저 확인도 별도 시험 프로필에서만 인증서 예외를 허용합니다.

```sh
curl --insecure --fail --silent --show-error https://localhost:18443/ > "$qa_dir/index.html"
curl --insecure --fail --silent --show-error https://localhost:18443/daily > "$qa_dir/daily.html"
curl --insecure --fail --silent --show-error https://localhost:18443/api/v1/auth/csrf > "$qa_dir/csrf.json"
test "$(curl --insecure --silent --show-error -o /dev/null -w '%{http_code}' https://localhost:18443/api/v1/me)" = 401
test "$(curl --insecure --silent --show-error -o /dev/null -w '%{http_code}' https://localhost:18443/internal/health)" = 404
test "$(curl --insecure --silent --show-error -o /dev/null -w '%{http_code}' https://localhost:18443/assets/qa-missing.js)" = 404
```

**성공 기준:** `/`·SPA는200/HTML, CSRF는200/JSON, 비로그인 me는401/JSON, internal과 없는 assets는404입니다. API 오류가 SPA HTML로 바뀌면 실패입니다. 최초 CA 생성 직후 접속이 안 되면 frontend 로그를 확인한 뒤 재시도합니다. SMTP는 시험 주소이므로 메일 발송 성공 검수는 제외합니다.

## 4. 재기동·데이터 보존 확인

아래 시험 표식 테이블은 **1단계 설정 검사를 통과한 QA DB에서만** 만듭니다.

```sh
qa_sql() {
  qa exec -T mysql bash -c 'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysql --protocol=socket -uroot --batch --skip-column-names moneysheet_prod'
}
printf '%s\n' 'CREATE TABLE qa_container_probe(id INT PRIMARY KEY); INSERT INTO qa_container_probe VALUES (1);' | qa_sql
qa up -d --force-recreate --wait --wait-timeout 240 be
qa stop frontend be mysql
qa up -d --wait --wait-timeout 240 mysql be frontend
test "$(printf '%s\n' 'SELECT COUNT(*) FROM qa_container_probe WHERE id=1;' | qa_sql)" = 1
qa run --rm migration validate
qa run --rm migration info > "$qa_dir/migration-after.log"
grep -E '^[0-9]+ \|' "$qa_dir/migration-before.log" > "$qa_dir/versions-before.txt"
grep -E '^[0-9]+ \|' "$qa_dir/migration-after.log" > "$qa_dir/versions-after.txt"
diff "$qa_dir/versions-before.txt" "$qa_dir/versions-after.txt"
```

**성공 기준:** BE 재생성과 전체 stop/start 후 BE healthy·표식1건 유지·Flyway validate 성공·적용 버전 목록 동일. 새 배포 revision에 SQL이 추가됐다면 같은 검증 세션에서 이미지를 바꾸지 말고 새 QA 프로젝트로 다시 검증합니다. 이는 로그인·금융 기록 전체 회귀 검수의 대체가 아닙니다.

## 5. 시험 프로젝트만 폐기

**위와 같은 셸에서만** 실행합니다. 프로젝트 변수가 없거나 QA 접두사가 아니면 폐기하지 않습니다.

```sh
case "${qa_project-}" in
  moneysheet-qa-*) ;;
  *) echo 'Refusing cleanup: not a QA project' >&2; exit 1 ;;
esac
qa --profile tools config --format json > "$qa_dir/cleanup-config.json"
python3 - "$qa_project" "$qa_dir/cleanup-config.json" <<'PY'
import json,sys
from pathlib import Path
c=json.loads(Path(sys.argv[2]).read_text())
assert c['name']==sys.argv[1]
assert all(not v.get('external') and v['name'].startswith(sys.argv[1]+'_') for v in c['volumes'].values())
PY
docker volume ls --filter "label=com.docker.compose.project=$qa_project"
qa down --volumes --remove-orphans
test -z "$(docker volume ls -q --filter "label=com.docker.compose.project=$qa_project")"
```

**성공 기준:** 해당 QA 프로젝트의 컨테이너·네트워크·명명된 시험 볼륨만 제거됩니다. 운영 프로젝트·기존 개발 볼륨·이미지는 대상으로 하지 않습니다. `docker volume prune`이나 프로젝트 없는 down을 사용하지 않습니다. 시험 디렉토리와 로그·시험 비밀 파일은 `qa_dir`에 남습니다. 필요한 증거를 보관한 뒤 해당 경로를 확인하고 수동 삭제합니다. `.secrets`·`.env` 등 운영 파일을 삭제하는 절차가 아닙니다.

공식 포트 병합 규칙: https://docs.docker.com/reference/compose-file/merge/


셸을 닫았거나 실패로 셸이 종료된 경우, 기록한 시험 디렉토리를 확인해 `qa_dir`에 지정하고 `qa_project=$(cat "$qa_dir/project-name")`, `export QA_SECRETS_DIR="$qa_dir/secrets"`로 복구합니다. 이 문서의 `qa()` 함수도 같은 파일 인자로 다시 정의한 뒤 5단계 보호 검사부터 수행합니다. 운영 디렉토리나 추측한 프로젝트 이름으로 폐기하지 않습니다.

## 이번 문서 보완의 확인 범위

Compose2.40.3에서 두 파일의 실제 config 병합을 확인했습니다. 시험 포트만 남음·BE/MySQL/migration 포트 미공개·고유 프로젝트와 secrets 경로·프로젝트별 비외부 볼륨·QA_SECRETS_DIR 누락 시 실패를 검증했습니다. 문서의 셸 구문과 Python 보호 검사 구문도 확인했습니다. 이 확인에서 전체 스택 기동·마이그레이션·재기동은 실행하지 않았으며, 2~4단계는 위 명령으로 별도 수행해야 합니다.
