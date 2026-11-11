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
