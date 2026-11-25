# 운영 Compose 기반 — BE #22

이 디렉토리는 운영 구성을 정의합니다. 실제 DDNS/공유기/공개 HTTPS 전환·GHCR 게시·승인형 CD는 후속 작업입니다. 개발 DB와 다른 `moneysheet_prod`, 프로젝트별 볼륨, 비밀 파일을 사용합니다. 개발 Compose·DB 볼륨을 재사용하지 않습니다.

## 이미지 빌드 — 개발 PC 또는 CI

BE 저장소 루트에서:

```sh
docker build --target app -t moneysheet-be:local .
docker build --target migration -t moneysheet-migration:local .
```

FE #79 이미지는 FE 저장소 루트에서 빌드합니다. 세 이미지의 태그/digest 조합을 배포 기록으로 남기고, BE 앱/마이그레이션 이미지는 동일한 소스 revision으로 빌드합니다. 운영 서버는 CI가 게시한 이미지를 pull하여 실행하며 소스를 빌드하지 않습니다. 이미지의 registry/repository 예시는 최종 GHCR 권한·게시 파이프라인에서 확정합니다.

Dockerfile은 Java 21 JDK/JRE digest를 고정하고, 최종 이미지를 비루트 UID/GID10001로 실행합니다. app에는 bootJar/상태 probe만, migration에는 전용 실행 도구/의존성/SQL만 포함합니다. 앱 시작에 Flyway가 포함되지 않으며 JPA는 validate입니다. .dockerignore는 필요한 main/migration/build 파일과 entrypoint만 허용합니다.

## 환경과 비밀 파일

1. `.env.example`을 `.env`로 복사하고 이미지 참조·실제 도메인·메일 설정을 채웁니다. `.env`는 자동 게시하지 않습니다.
2. 이 디렉토리에 `.secrets`를 만들고 호스트에서 디렉토리 권한을700으로 유지합니다.
3. 아래 파일을 생성합니다. DB 세 파일은 각각 독립적인64자리 hex 난수, SMTP는 실제 앱 비밀번호입니다. 실제 값은 로그·Git·대화에 넣지 않습니다.

| 파일 | 연결되는 서비스 |
| --- | --- |
| .secrets/mysql_root_password | mysql만 |
| .secrets/app_password | mysql, be |
| .secrets/migration_password | mysql, migration |
| .secrets/smtp_password | be만 |

Compose의 파일 secrets는 암호화 저장소가 아니라 컨테이너별 파일 마운트입니다. 호스트 디렉토리 권한700과 안전한 백업으로 보호합니다. 로컬 Compose bind secret의 uid/gid/mode 재매핑에 의존하지 않습니다. 비루트 앱이 마운트 파일을 읽도록 개별 파일은0444, 부모 디렉토리는700으로 구성하거나 운영자가 적절한 소유권/ACL을 설정해야 합니다. secrets 디렉토리 전체를 BE에 마운트하지 않습니다. 비밀번호는 Docker build ARG/ENV에 굽지 않고 entrypoint가 필요한 파일만 읽어 실행 프로세스에 전달합니다.

초기화는 새 MySQL 데이터 볼륨에서만 계정을 만듭니다. 비밀 파일을 바꿔도 기존 계정 비밀번호가 자동 변경되지 않습니다. 기존 볼륨 변경은 별도의 DB 계정 변경 절차가 필요합니다. 잘못된 DB 비밀번호 형식은 매 시작 시 공식 MySQL 초기화 전에 거부합니다. 초기화 중 실패한 새 볼륨은 로그·스키마/계정 상태·데이터 필요 여부를 확인하고 백업한 뒤 복구합니다. 자동으로 기존 볼륨을 삭제하거나 init 스크립트를 무작정 재실행하지 않습니다.

## 운영 환경의 명시적 실행 순서 — 격리 검증에는 사용하지 않음

이 디렉토리에서 실행합니다. 실제 데이터가 있는 대상은 반드시 대상 DB/버전·백업을 확인한 뒤 마이그레이션합니다.

```sh
docker compose --env-file .env -f compose.prod.yaml config --quiet
docker compose --env-file .env -f compose.prod.yaml --profile tools pull
docker compose --env-file .env -f compose.prod.yaml up -d --wait mysql
docker compose --env-file .env -f compose.prod.yaml run --rm migration info
docker compose --env-file .env -f compose.prod.yaml run --rm migration migrate
docker compose --env-file .env -f compose.prod.yaml run --rm migration validate
docker compose --env-file .env -f compose.prod.yaml up -d --wait be frontend
```

마이그레이션 작업은 하나의 배포 주체만 실행합니다. 일반 up/BE restart에서 migrate를 호출하지 않습니다. tools 프로필은 기본 up에서 빠지며, migration의 기본 동작도 변경 없는info입니다. clean/자동 baseline/DB 생성은 허용하지 않습니다. 실패 시 자동 repair·반복 적용·볼륨 삭제를 하지 않습니다.

## 서비스 연결·접근

- Caddy가 FE 이미지 `/usr/share/caddy`와 이 디렉토리의 Caddyfile을 사용합니다. `/api`와 `/api/*`는 prefix와 오류 상태/본문을 유지해 BE로 전달합니다. SPA fallback은 그 외 화면 경로에만 적용하며 없는 assets는404입니다. HTML은 no-cache입니다.
- Caddy↔BE는 web, BE↔MySQL 및 migration↔MySQL은 외부 연결 없는 database 네트워크입니다. 앱은 web 네트워크를 통해 SMTP에 연결합니다. Caddy는 DB 네트워크에 들어가지 않습니다.
- BE/MySQL에는 host ports가 없습니다. Caddy만80/443을 게시하며 관리 API2019·DB3306·BE8080을 공개하지 않습니다. 공유기 외부 웹 포워딩은 후속 단계입니다.
- 운영 프로필은 Secure/HttpOnly/SameSite=Lax 쿠키·Swagger 비공개를 유지합니다. Caddy가 외부 X-Forwarded 헤더를 신뢰하지 않고 설정하며, private BE가 Tomcat native forwarded headers로 원래 클라이언트 IP/HTTPS를 인식합니다. 임의의 외부 프록시/CDN은 별도 신뢰 설정 없이 추가하지 않습니다.
- `/internal/health`는 DB 연결 유효성에 따라200 UP/503 DOWN만 반환합니다. Caddy는 internal·actuator·Swagger 경로를404로 차단합니다. BE는 내부 healthcheck에 이 경로를 사용하며 health 조회로 세션을 생성하지 않습니다.
- 앱/작업은 read-only root filesystem과/tmp tmpfs, cap_drop ALL로 실행합니다. JVM·DB·로그 제한값은 초기값이며 운영 서버 실제 RAM과 부하 측정 후 조정합니다. 현재 앱768MiB·DB768MiB·Caddy128MiB, 작업512MiB입니다.
- mysql_data·caddy_data·caddy_config는 영속 볼륨입니다. `stop`/`down`과 `down --volumes`를 혼동하지 않습니다. 실제 운영 데이터에서 --volumes를 사용하지 않습니다.

## 격리 검증과 후속

격리 검증은 [전용 설정·실행·성공 기준·폐기 절차](qa.md)를 따릅니다. `compose.prod.yaml` 단독 실행이나 `-p`만으로는 포트가 격리되지 않습니다. `compose.qa.yaml`을 함께 사용해 호스트80/443을 loopback18022/18443으로 완전히 교체하고, 시험 프로젝트·비밀 파일·볼륨·localhost 인증서를 분리합니다. 운영 실행 명령을 검증 명령으로 사용하지 않습니다.

실제 공개 DDNS의 인증서 발급·외부망 접속·재부팅 자동 기동·정기 백업/격리 복원·앱 이미지 롤백·운영 CD는 아직 후속입니다. 이미지/Compose 기반 검증이 전체 운영 배포 완료를 의미하지 않습니다. 정기 백업과 복구 절차는 별도 이슈에서 구성합니다.

공식 참고: https://docs.docker.com/compose/how-tos/use-secrets/ , https://caddyserver.com/docs/caddyfile/directives/reverse_proxy
