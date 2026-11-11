# Ubuntu 개발 MySQL

대상은 Ubuntu 노트북의 개발 DB입니다. Windows에 Docker를 설치하는 구성이 아닙니다.
운영 DB·계정·볼륨과 분리합니다. 비밀값은 예시 파일에 넣지 않습니다.

## 기존 서버 상태와 적용 주의

- 기존 작업 경로: /home/danielhw99/moneysheet-db-dev, 실행 파일 compose.yaml.
- 프로젝트 이름 moneysheet-db-dev, 볼륨 moneysheet-db-dev_mysql_data 유지.
- 이 저장소 파일은 기존 검증 구성에서 정리한 배포 원본. 서버를 자동 동기화·재설치하지 않습니다.
- 기존 .env는 이미 발급·교체된 비밀번호를 보관. 예시로 덮어쓰거나 새 비밀번호로 바꾸지 않습니다.
- 기존 경로에 적용할 때 compose.dev.yaml과 init/ 및 scripts/를 별도 검토해 복사, 실제 .env는 유지. 포트·프로젝트·볼륨 충돌 여부 확인 후 적용합니다.
- 기존 compose.yaml과 동시에 실행하지 말고 사용할 파일을 명시합니다. 파일명이 달라도 프로젝트·볼륨 이름은 동일합니다.
- 최종 파일은 Compose 구문 및 별도 격리 컨테이너에서 검증했습니다. 기존 개발 서버에도 권한 교정·Compose/preflight/healthcheck 적용을 완료하고 재기동 검증했습니다.

## 새 Ubuntu 개발 DB 준비

Compose 플러그인과 Docker 권한이 필요합니다. 새 빈 작업 경로에 이 디렉터리 파일을 배치한 뒤:

```bash
cp .env.example .env
chmod 600 .env
# 세 번 실행해 각각 다른 값을 .env에 직접 입력합니다. 출력은 공유하지 않습니다.
openssl rand -hex 32
chmod 755 init scripts
chmod 644 init/01-accounts.sh scripts/*.sh
docker compose -f compose.dev.yaml --env-file .env config --quiet
docker compose -f compose.dev.yaml --env-file .env up -d --wait --wait-timeout 180
docker compose -f compose.dev.yaml --env-file .env ps
```

`.sh`는 LF 줄바꿈을 유지합니다. 읽기 권한이 있으면 공식 이미지 초기화 과정에서 실행 권한 없이도 source됩니다.
초기화 스크립트는 비어 있는 데이터 볼륨에서만 실행됩니다. 실패 시 기존 볼륨을 삭제하지 말고 초기화 상태와 계정을 먼저 확인합니다.

## 접속·계정

- MySQL 8.4.13, moneysheet_dev, Ubuntu 127.0.0.1:3306.
- root: 컨테이너 내부 관리용. moneysheet_app: 해당 DB SELECT/INSERT/UPDATE/DELETE만.
- moneysheet_migration: 같은 DB DML+CREATE/ALTER/DROP/INDEX/REFERENCES. 서버 전체 관리자 권한 없음.
- root·앱·마이그레이션 비밀번호는 모두 64자리 hex 규칙. 기존 서버 비밀번호도 이 규칙으로 생성됐으며 값은 저장소에 복사하지 않습니다.
- healthcheck는 root를 로컬 socket으로, 앱·마이그레이션을 TCP로 확인합니다. 앱 TCP 연결이 초기화 중 임시 socket-only 서버를 제외하며 root@localhost의 DNS 의존 호스트 매칭도 피합니다. 권한 전체를 증명하지는 않으므로 GRANT도 별도 검토합니다.

Windows 별도 터미널에서 BE용 터널을 유지:

```powershell
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:13306:127.0.0.1:3306 ubuntu
```

IntelliJ 데이터 소스의 자체 SSH 터널과 BE 터널은 별개입니다. Windows의13306은 SSH 터널용, Ubuntu의3306은 DB 포트입니다.
BE URL: jdbc:mysql://127.0.0.1:13306/moneysheet_dev. 실제 JDBC 옵션과 환경변수는 BE 연결 체크포인트에서 정리합니다.
공유기3306 포트포워딩 금지. SSH 접근 경로는 현재 네트워크 설정에 맞춰 확인합니다.

## 종료·보존

```bash
docker compose -f compose.dev.yaml --env-file .env stop
docker compose -f compose.dev.yaml --env-file .env start
# 컨테이너 교체, 동일 프로젝트의 데이터 볼륨 유지
docker compose -f compose.dev.yaml --env-file .env up -d --force-recreate --wait --wait-timeout 180
```

`down -v`는 데이터를 지우므로 사용하지 않습니다. .env 변경만으로 실제 DB 비밀번호가 바뀌지 않습니다.
비밀번호 변경은 ALTER USER·.env·컨테이너 환경값을 함께 갱신하고 이전 값 차단을 확인해야 합니다.
비밀값 없는 구문 확인은 config --quiet 사용. 일반 config와 inspect의 전체 출력은 자격증명을 노출할 수 있습니다.

## 작업 경계

여기서는 DB 실행·권한·영속화·접속 경로만 다룹니다. Flyway·업무 테이블·인증·운영 배포는 후속 작업입니다.
서버 기존 구성의 성공 기록과 이 파일의 최신 재적용 검증을 구분합니다.

## 보안 검토 보완

- 권한 DB 패턴은 SQL에서 `moneysheet\_dev`로 지정. 백틱 인용만으로 와일드카드가 제거되지 않으며 Bash heredoc의 역슬래시 처리도 고려합니다.
- 이 구성은 MySQL 기본 partial_revokes=OFF를 전제로 합니다. 다른 서버 옵션으로 바꿀 때 권한 패턴 의미를 재검토합니다.
- preflight.sh는 공식 이미지 entrypoint보다 먼저 세 비밀번호 형식을 검사. 초기 데이터 유무와 관계없이 매번 실행, 비정상 값은 DB 초기화 전에 종료합니다.
- 형식 오류로 종료하면 unless-stopped 정책상 재시도될 수 있지만 DB 초기화 단계로 넘어가지 않습니다. config --quiet는 값의 존재만 검사하며 형식 검사를 대신하지 않습니다.
- 정상 형식의 .env도 실제 DB 비밀번호와 일치한다는 보장은 없습니다. 기존 DB는 ALTER USER와 환경값을 함께 갱신해야 하며 healthcheck로 계정 접속 실패를 드러냅니다.
- 기존 볼륨에서는 초기화 SQL이 재실행되지 않으므로 파일 수정만으로 이미 저장된 와일드카드 권한은 제거되지 않습니다. SHOW GRANTS와 partial_revokes를 확인하고 기존 넓은 GRANT를 명시적으로 REVOKE한 뒤 좁은 GRANT로 교체해야 합니다. 새 GRANT 추가만으로 기존 권한이 사라지지 않습니다.
- 저장소 수정은 기존 서버 계정/비밀번호/데이터에 자동 적용하지 않습니다.

### 새 개발 볼륨의 부분 초기화 복구

1. `docker compose -f compose.dev.yaml --env-file .env stop mysql`로 재시도 중단.
2. `docker volume inspect moneysheet-db-dev_mysql_data`로 해당 프로젝트·볼륨인지 확인하고 기존 업무 데이터/다른 사용자가 없는지 확인.
3. 비어 있던 새 개발 볼륨이 맞더라도 DB 파일이 생성됐을 수 있으므로 로그·계정·DB 상태를 확인. 기존 데이터가 있거나 불확실하면 삭제하지 말고 스냅샷/백업 후 수동 계정·권한 복구.
4. 폐기 가능한 신규 실패 볼륨임을 사용자가 확인한 경우에만 컨테이너를 내린 뒤 정확히 확인한 해당 볼륨 하나를 삭제하고 올바른 .env로 최초 초기화를 다시 수행. 일반 절차에 down -v/볼륨 전체 정리 명령을 넣지 않음.
5. 재시작 후 세 계정 접속·SHOW GRANTS·비슷한 이름의 다른 DB 접근 거부를 확인. health만으로 잘못된 권한 범위를 판정하지 않음.

형식 검증을 추가해도 디스크/SQL 오류 등 모든 초기화 실패가 원자적으로 복구되는 것은 아닙니다. 첫 DB 초기화는 트랜잭션 전체 롤백을 보장하지 않습니다.

- 초기화 스크립트는 source/직접 실행 양쪽에서 서브셸로 shell option을 격리합니다. set -u가 공식 entrypoint로 전파되면 선택 환경변수 접근에서 초기화가 실패할 수 있습니다. 셸 파일 실제 바이트도 LF로 유지합니다.

### 실제 확인한 보안 검증 결과

- MySQL 8.4.13 공식 이미지의 Linux Bash에서 세 스크립트 문법 확인 및 Compose config --quiet 통과.
- 빈 데이터 경로와 mysql 하위 디렉터리가 있는 경로 모두에서 root/앱/마이그레이션 형식 오류가 entrypoint 전에 거부되고 파일 추가 없음.
- source 방식의 최초 초기화 정상 완료. set -u가 공식 entrypoint로 전파되지 않음.
- 임시 메모리 DB·외부 포트 없는 격리 컨테이너에서 해당 DB 접근 허용, moneysheetXdev 접근은 두 계정 모두 거부.
- 앱 테이블 생성 거부, 마이그레이션 테이블 생성 허용. 실제 권한 이름·집합 유지.
- 최종 healthcheck는 정상 계정 통과, 잘못된 앱 비밀번호로 실패. root socket 및 앱/마이그레이션 TCP 사용.
- 격리 검증 컨테이너는 제거. 기존 개발 DB 데이터·계정·비밀번호·권한·볼륨 변경 없음.
- 기존 개발 DB 읽기 전용 감사에서는 partial_revokes=0 및 두 계정의 이스케이프되지 않은 DB 권한 패턴 확인. 이후 기존 넓은 권한을 회수하고 정확한 DB 권한으로 교정·재기동 후 검증 완료.
- CodeRabbit CLI·자동 테스트 프레임워크는 실행하지 않음. 위 항목은 실제 컨테이너/SQL 동작 확인 결과.

### 실제 개발 서버 적용 결과

- 권한 변경 전 GRANT 및 Compose/초기화 스크립트는 서버 recovery/ 하위 비공개 경로에 보관.
- 정확한 DB 권한 부여 후 기존 넓은 권한을 회수. 두 계정의 권한 패턴 HEX 값으로 확인.
- 비밀번호·테이블 목록은 변경하지 않음. 기존 이름의 데이터 볼륨 유지.
- 컨테이너 재생성 후 새 entrypoint·세 계정 healthcheck·healthy·localhost 바인딩 확인.
- root/앱/마이그레이션 접속 성공, 비관리자 두 계정의 유사 이름 DB 접근 거부 확인.
