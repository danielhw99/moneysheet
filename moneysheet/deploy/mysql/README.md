# Ubuntu 개발 MySQL

대상은 Ubuntu 노트북의 개발 DB입니다. Windows에 Docker를 설치하는 구성이 아닙니다.
운영 DB·계정·볼륨과 분리합니다. 비밀값은 예시 파일에 넣지 않습니다.

## 기존 서버 상태와 적용 주의

- 기존 작업 경로: /home/danielhw99/moneysheet-db-dev, 실행 파일 compose.yaml.
- 프로젝트 이름 moneysheet-db-dev, 볼륨 moneysheet-db-dev_mysql_data 유지.
- 이 저장소 파일은 기존 검증 구성에서 정리한 배포 원본. 서버를 자동 동기화·재설치하지 않습니다.
- 기존 .env는 이미 발급·교체된 비밀번호를 보관. 예시로 덮어쓰거나 새 비밀번호로 바꾸지 않습니다.
- 기존 경로에 적용할 때 compose.dev.yaml과 init/만 별도 검토해 복사, 실제 .env는 유지. 포트·프로젝트·볼륨 충돌 여부 확인 후 적용합니다.
- 기존 compose.yaml과 동시에 실행하지 말고 사용할 파일을 명시합니다. 파일명이 달라도 프로젝트·볼륨 이름은 동일합니다.
- 현재 새 저장소 파일의 서버 재적용·Compose 검증은 SSH 재연결 후 수행할 항목입니다.

## 새 Ubuntu 개발 DB 준비

Compose 플러그인과 Docker 권한이 필요합니다. 새 빈 작업 경로에 이 디렉터리 파일을 배치한 뒤:

```bash
cp .env.example .env
chmod 600 .env
# 세 번 실행해 각각 다른 값을 .env에 직접 입력합니다. 출력은 공유하지 않습니다.
openssl rand -hex 32
chmod 755 init
chmod 644 init/01-accounts.sh
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
- 앱·마이그레이션 비밀번호는 64자리 hex 규칙. 기존 서버 비밀번호도 이 규칙으로 생성됐으며 값은 저장소에 복사하지 않습니다.
- TCP healthcheck는 초기화 중 임시 socket-only 서버를 준비 완료로 오인하지 않도록 구성합니다.

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
