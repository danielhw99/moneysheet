# MoneySheet Backend App

이 디렉터리는 MoneySheet 백엔드 실제 애플리케이션 루트입니다.

## Convention Summary

- 공식 문서: [../../docs/planning/project-convention.md](../../docs/planning/project-convention.md)
- Branch: `feature/<Issue_Number>-<Domain>`
- Commit: `[<Prefix>] #<Issue_Number> <Description>`
- Pull Request: `[<Prefix>] #<Issue_Number> <Description>`

## Before You Start

1. issue를 생성하고 milestone, label, project를 설정합니다.
2. feature branch를 생성합니다.
3. 작업 후 commit / push / PR을 진행합니다.
4. `develop` branch로 squash merge 합니다.


## 로컬 MySQL 연결 — 이슈 #3

- Java 21, Gradle Wrapper 사용. MySQL Connector/J 버전은 Spring Boot BOM으로 관리합니다.
- Windows에서 BE를 실행하고 Ubuntu의 MySQL에는 SSH 터널로 연결합니다.
- 먼저 Tailscale 로그인 후 별도 터미널에서 아래 터널을 유지합니다.

```powershell
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:13306:127.0.0.1:3306 ubuntu-tailscale
```

IntelliJ 내부 SSH 터널은 BE 프로세스와 공유되지 않습니다. 13306이 이미 사용 중이면 기존 터널의 대상부터 확인하세요.

IntelliJ 실행 설정의 환경변수에 다음을 설정합니다.

```text
SPRING_PROFILES_ACTIVE=local
DB_USERNAME=moneysheet_app
DB_PASSWORD=<Ubuntu .env의 APP_PASSWORD 값>
```

기본 DB URL은 `jdbc:mysql://127.0.0.1:13306/moneysheet_dev?sslMode=REQUIRED`입니다.
다른 터널 포트를 사용하면 `DB_URL`로 변경합니다. `sslMode=REQUIRED`는 DB TLS 암호화를 요구하며,
서버 신원 확인은 기존 호스트 키를 검증하는 SSH 터널로 보장합니다. DB URL을 외부 DB 직접 접속용으로 사용하지 않습니다.
root나 마이그레이션 계정을 애플리케이션 실행에 사용하지 않습니다.

`.env.example`은 입력 항목 안내이며 Spring Boot가 `.env`를 자동으로 읽지는 않습니다.
실제 비밀번호를 README·커밋·명령 인자에 넣지 마세요. 기존 `application-secret.yml`은 자동 포함하지 않습니다.

```powershell
.\gradlew.bat clean compileJava bootJar -x test --no-daemon --console=plain
# 위 환경변수를 실행 프로세스에 설정한 뒤
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

local 프로필의 HTTP 서버는 `127.0.0.1:8080`에만 바인딩합니다.
`ddl-auto: none`으로 Hibernate의 테이블 자동 생성·변경을 막습니다.
Flyway 및 업무 테이블 생성은 후속 작업이며 현재 업무 API가 없어 `/`는 404를 반환할 수 있습니다.


## Flyway 전용 실행 — 이슈 #6 / 1차 체크포인트

일반 `bootRun`과 `bootJar`에는 Flyway 실행기를 포함하지 않습니다.
`src/migration/java`는 별도 소스 영역이며 Boot BOM으로 의존성 버전을 관리합니다.
전용 실행기는 Spring 컨텍스트·웹 서버·JPA를 시작하지 않고 명령 처리 후 종료합니다.

SSH 터널을 유지하고 실행 프로세스에 아래 환경변수를 지정하세요.
`.env`는 자동으로 읽지 않으며 비밀번호는 명령 인자에 넣지 않습니다.

```text
MIGRATION_DB_URL=jdbc:mysql://127.0.0.1:13306/moneysheet_dev?sslMode=REQUIRED
MIGRATION_DB_USERNAME=moneysheet_migration
MIGRATION_DB_PASSWORD=<서버 MIGRATION_PASSWORD 값>
```

```powershell
.\gradlew.bat dbInfo --no-daemon --console=plain
.\gradlew.bat dbValidate --no-daemon --console=plain
# 대상 DB 상태 확인·백업 후 명시적으로 실행하는 변경 명령
.\gradlew.bat dbMigrate --no-daemon --console=plain
```

일반 앱은 기존 DB_* 환경변수만 사용합니다. 마이그레이션은 MIGRATION_DB_* 세 값을 필수로 요구하며 앱 계정으로 대체하지 않습니다.
태스크의 안전 설정은 Java 실행기에 직접 적용합니다. `spring.flyway.*` 설정에 의존하지 않습니다.
clean 금지·스키마 자동 생성 금지·자동 baseline 금지·적용 전 검증 및 파일명 검증을 사용합니다.
clean/baseline/repair 명령은 제공하지 않습니다.

1차에서는 SQL이 없으므로 info/validate는 연결·빈 이력 확인용이며, 스키마 검증 완료를 뜻하지 않습니다.
SQL 없는 migrate는 거부합니다. 2차에서 최초 SQL을 `src/migration/resources/db/migration/V1__init_schema.sql`에 추가합니다.
마이그레이션 파일은 앱 JAR에 포함하지 않고 Gradle 작업 소스와 함께 관리합니다. 운영 실행 패키징은 배포 단계에서 정합니다.

적용 전 대상 DB·기존 테이블/데이터·Flyway 이력을 재확인하고 백업합니다.
비어 있지 않은 DB에 이력이 없으면 자동 baseline으로 우회하지 않습니다.
MySQL DDL 실패는 일부 변경을 남길 수 있으므로 실패 시 재실행·repair·테이블 삭제를 자동 수행하지 않습니다.
서버 실제 .env는 Git에 포함하지 않으며 로그 공유 전 비밀값을 확인합니다.
