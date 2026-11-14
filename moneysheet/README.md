# MoneySheet Backend App

## Category API (Issue #14, WBS 3.1)

`GET /api/v1/categories` returns the authenticated user's categories as `{ "items": [...] }`.
Optional `scope` and `direction` filters are supported. `POST /api/v1/categories` creates or
reuses a category with the same normalized name for that user, scope, and direction. Both new
and reused results return HTTP 200. `fixedBudget` requires `direction: null`; `mainTransaction`
requires `income` or `expense`. Both endpoints require a session, POST requires CSRF, and
responses use `Cache-Control: no-store`.

Verification on 2026-11-13 (Windows): `gradlew.bat test compileIntegrationTestJava bootJar
--no-daemon --console=plain` passed. The Testcontainers MySQL `integrationTest` task was
attempted but could not start because no Docker host is available on this machine; therefore
the concurrency and database-isolation tests are compiled but not yet runtime-verified here.
Run `gradlew.bat check bootJar --no-daemon --console=plain` on a Docker-enabled host before
merge. The GET/POST payload and status were checked against the approved OpenAPI category
schemas; generated `/v3/api-docs` was not runtime-checked without a database.

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
`ddl-auto: validate`로 기존 스키마와 엔티티를 대조하며 테이블을 자동 생성·변경하지 않습니다.
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

최초 SQL은 `src/migration/resources/db/migration/V1__init_schema.sql`에 있습니다.
신규 SQL이 PENDING인 상태에서 단독 dbValidate는 실패할 수 있습니다. dbInfo로 상태를 확인한 후,
승인·백업이 완료된 대상에 dbMigrate를 실행하고 dbInfo/dbValidate로 적용 결과를 확인합니다.
migrate 자체의 적용 전 검증은 활성화되어 있습니다. SQL 없는 migrate는 거부합니다.
마이그레이션 파일은 앱 JAR에 포함하지 않고 Gradle 작업 소스와 함께 관리합니다. 운영 실행 패키징은 배포 단계에서 정합니다.

적용 전 대상 DB·기존 테이블/데이터·Flyway 이력을 재확인하고 백업합니다.
비어 있지 않은 DB에 이력이 없으면 자동 baseline으로 우회하지 않습니다.
MySQL DDL 실패는 일부 변경을 남길 수 있으므로 실패 시 재실행·repair·테이블 삭제를 자동 수행하지 않습니다.
서버 실제 .env는 Git에 포함하지 않으며 로그 공유 전 비밀값을 확인합니다.


### 2차 체크포인트 적용 기록 — 2026-11-11

- 대상: Ubuntu 개발 DB `moneysheet_dev`. 적용 직전 업무 테이블·Flyway 이력이 없는 빈 스키마임을 확인.
- 적용 전 백업: `/home/danielhw99/moneysheet-db-dev/recovery/20261111T081151Z-before-flyway-v1/moneysheet_dev-before-v1.sql` (권한 600, SHA-256 기록). 백업 복원 실습은 이번 작업에서 미수행.
- 최초 SQL은 승인된 ERDCloud DDL과 CREATE TABLE 본문이 동일. 업무 테이블 8개·제약·인덱스·고정여유비 생성 컬럼 반영.
- 마이그레이션 계정으로 V1 적용 성공. 재실행은 up to date, info는 SUCCESS, validate는 1개 성공.
- DB 확인: 업무 테이블 8개 + flyway_schema_history, V1 성공 이력 1개, 설치 계정 moneysheet_migration.
- 트랜잭션 내 수동 검증 20항목: 일반 고정비 복수/생성 컬럼 NULL, 고정여유비 월 연결/중복 거부/미존재 허용, 금액 0/상한/초과, 월 중복/월초 날짜, 식비 0/READY 미입력/DRAFT 미입력, 메모 미완료/당일 완료/이전 날짜/다른 월/초 단위, FK 누락/참조 중 삭제, 이메일 중복.
- 검증 입력은 전부 롤백, 업무 테이블 행 수 모두 0. 롤백해도 AUTO_INCREMENT 번호에 빈 구간이 생길 수 있으며 연속성을 요구하지 않음.
- Gradle 기본 리소스 경로를 중복 등록한 문제는 제거. 단독 validate의 PENDING 거부는 검증 완화 없이 실행 순서를 바로잡음.
- JPA 엔티티·업무 API는 아직 미구현. ddl-auto는 none 유지. 적용된 V1 파일을 수정하지 않고 후속 스키마 변경은 V2 이상으로 추가.
- 자동화 테스트 코드는 추가·실행하지 않음. 위 기록은 실제 수동 DB/명령 검증 결과.


## JPA 매핑 — 이슈 #8 / 1차 체크포인트

- user/category/budget/transaction/memo/payment/cashflow 도메인별 entity 패키지에 업무 엔티티 8개 구성.
- BaseEntity: IDENTITY Long ID, created_at/updated_at DATETIME(6). JPA 생명주기 콜백에서 UTC·마이크로초 정밀도로 시각 관리. 직접 SQL·bulk UPDATE에는 콜백이 적용되지 않음.
- 금액/식비는 Long, 날짜는 LocalDate, 사용 시각은 LocalTime, UTC 저장용 시각은 LocalDateTime. LocalDateTime 필드명은 합의한 Date 접미사 사용.
- ENUM은 EnumType.STRING 및 VARCHAR JDBC 타입으로 매핑하여 기존 MySQL VARCHAR 스키마 유지.
- 부모 관계는 단방향 ManyToOne LAZY. Cascade·orphanRemoval·양방향 컬렉션은 추가하지 않음. 엔티티를 API 응답으로 직접 노출하지 않음.
- allowance_month_id는 DB 생성 컬럼이므로 매핑하지 않음. 월 관계는 monthlyBudget으로 확인.
- Repository·업무 Service·API·업무 입력 검증 및 수정 메서드는 후속 작업. public 무인자 생성자·전체 setter·엔티티 toString은 제공하지 않음.
- 1차에서는 기본 ddl-auto none 유지. 영구 validate 설정 전환과 실제 저장·조회는 2차 체크포인트.


### 1차 검증 기록 — 2026-11-11

- Java 21 clean compileJava bootJar 성공. 자동화 테스트 코드 추가·실행 없음.
- 업무 엔티티 8개의 매핑 컬럼을 V1과 정적 대조. 의도적으로 제외한 allowance_month_id 외 누락 없음.
- 검증용 실행에서만 ddl-auto=validate를 지정하여 실제 MySQL V1 스키마와 대조·기동 성공. 일반 설정은 none 유지.
- 검증용 localhost:13316 SSH 터널과 HTTP 18080 사용 후 종료. 기존 사용자 터널·실행 프로세스는 종료하지 않음.
- 검증 전후 DB 테이블 목록 동일. 이번 단계는 저장/수정·Repository·감사 시각 콜백의 실제 동작 확인을 포함하지 않음(2차 범위).


## JPA 저장 계층 — 이슈 #8 / 2차 체크포인트

- 도메인별 JpaRepository 8개 추가. 현재는 기본 저장·조회 인터페이스만 제공하며 Controller로 노출하지 않음.
- 사용자별 데이터 접근·소유권·기간·금액 등 업무 검증은 후속 Service/API에서 구현. Repository 존재 자체로 접근 통제를 보장하지 않음.
- 기본 ddl-auto는 validate. 최초 DB에서는 별도 dbMigrate로 스키마를 먼저 적용하고 앱을 기동. 앱 실행 시 테이블 자동 생성·수정·Flyway 적용 없음.
- 1차 기록의 none 설정은 당시 상태. 2차부터 validate를 기본으로 유지.
- 수정용 업무 메서드·API는 이번 범위에 추가하지 않음. 시각 콜백은 JPA 변경 감지에 적용되며 직접 SQL·bulk UPDATE에는 적용되지 않음.


### 2차 검증 기록 — 2026-11-11

- Java 21 clean compileJava bootJar 성공. 기본 validate 설정으로 실제 앱 JAR 기동 성공, Spring Data Repository 8개 등록 확인.
- 저장소 외부 임시 실행기에서 실제 Repository 8개로 저장·flush 후 EntityManager.clear하여 재조회. JUnit·테스트 소스·프로덕션 검증용 API는 추가하지 않음.
- 식비 READY/DRAFT 및 NULL, 입출금·메모·고정비·월 수익 종류, 미분류 카테고리·선택 카테고리, 9,999,999,999원 Long, 한글 문자열, DATE/TIME/DATETIME(6) 왕복 확인.
- 단방향 부모 지연 로딩과 실제 관계 조회 확인. 고정여유비 생성 컬럼은 직접 입력하지 않고 DB 계산값·일반 고정비 NULL 확인.
- 수동 실행기에서 Hibernate 프록시를 해제한 실제 엔티티 필드를 반사 방식으로 변경하여 dirty checking 확인. 제품 엔티티의 수정 API는 후속 업무 구현 범위로 유지.
- 생성/수정 시각의 최초 동일 값·UTC·마이크로초 정밀도, 수정 시 created_at 유지·updated_at 증가 확인.
- 자식 거래 삭제 후 부모 월 예산·사용자 보존 확인. 모든 검증 쓰기는 하나의 트랜잭션에서 롤백.
- 검증 전후 업무 행 수 모두 0, 스키마 지문·Flyway 이력 동일. AUTO_INCREMENT 번호는 롤백 후에도 증가할 수 있어 스키마 지문에서 카운터 값만 제외.
- 검증 코드는 앱 JAR에 포함되지 않음. 일반 앱 JAR에도 Flyway 실행기/라이브러리가 없고, V1 SQL·DB 스키마 변경 없음.
- 검증용 BE·SSH 터널 종료. 기존 사용자 실행 프로세스와 터널은 유지.


## API 공통 처리 — 이슈 #10 / 1차 체크포인트

- 성공 DTO 직접 반환·204 본문 없음 유지. ApiResponse 래퍼·ResponseBodyAdvice는 추가하지 않음.
- common.error: ApiError(code/message/fieldErrors/traceId), ApiFieldError, 계약 ErrorCode, ApiException, GlobalExceptionHandler.
- Spring MVC 입력/본문/경로/메서드 오류와 업무 ApiException을 공통 JSON으로 변환. HTTP 상태·Allow/Accept 등 원래 헤더 유지. fieldErrors는 해당 없으면 빈 배열.
- 필드 오류에 rejectedValue나 예외 원문을 넣지 않음. 1차 필드 메시지는 안전한 공통 문구이며 상세 검증 규칙은 2차에 연결.
- 예기치 않은 예외는 INTERNAL_ERROR로 응답. 서버 오류 로그에도 추적 ID·예외 클래스만 기록하며 예외 메시지·SQL·스택을 직접 출력하지 않음.
- common.web.TraceIdFilter: 서버 UUID 생성, 응답 X-Trace-Id와 오류 본문 traceId 일치. 외부 헤더를 신뢰하지 않으며 요청 종료 후 MDC 복원/정리.
- ApiException의 RATE_LIMITED는 양수 재시도 초를 필수로 받아 Retry-After 헤더 반환. 실제 인증·CSRF·요청 제한 필터 구현은 후속 범위.
- 인증 필터/서블릿 컨테이너에서 MVC 밖으로 발생한 오류는 Advice 범위 밖. 인증 필터 연결은 인증 이슈에서 구현.
- 업무 Controller·검증용 endpoint·Swagger·전역 데이터 변환은 이번 1차 범위에서 추가하지 않음. 수동 HTTP 검증용 코드는 저장소 밖에서만 실행.


### API 1차 검증 기록 — 2026-11-11

- Java 21 clean compileJava bootJar 성공. 제품 테스트 코드·업무 Controller 추가 없음.
- 저장소 외부 임시 Controller로 실제 HTTP 확인: 정상 DTO 직접 반환, 204 빈 본문, 잘못된 JSON/필드 검증/타입/필수값(400), 미존재 경로(404), 메서드(405·Allow 유지), 미지원 미디어(415), 업무 충돌(409), 요청 제한 형식(429·Retry-After), 예상하지 못한 오류(500).
- 오류 10종의 code/message/fieldErrors/traceId 형식·JSON·no-store 확인. fieldErrors는 해당 없으면 빈 배열. 입력값·기본 바인딩 메시지·예외 원문 미노출.
- 응답 헤더 X-Trace-Id는 하나이며 오류 본문과 동일. 정상 요청에서 MDC 값과 일치, 요청별 UUID 상이, 외부 X-Trace-Id 무시 확인.
- 실행 환경 DEBUG 설정으로 Spring의 예외 원문 DEBUG 로그가 나오는 것을 발견해 org.springframework.web=INFO 및 Hibernate SQL/bind/extract 로그 OFF를 명시. 재검증에서 예외 원문 미출력 확인. 운영에서 로깅 수준을 다시 높이거나 별도 로거를 추가하면 비밀값 노출을 재점검해야 함.
- 임시 Controller는 앱 JAR에 미포함. DB 테이블·Flyway 이력 동일. 검증용 BE와 SSH 터널 종료.
- Bean Validation 의존성/상세 규칙, ID·날짜 등 변환, Swagger UI는 2차 범위. 인증·CSRF·실제 요청 제한 기능은 미구현.


## API 입력·출력·Swagger — 이슈 #10 / 2차 체크포인트

- Boot 4.1.2의 Jackson 3 API 사용. springdoc-openapi 3.2.0을 명시 고정, Bean Validation은 Boot BOM 관리.
- DTO ID는 ApiId를 사용해 JSON 숫자 문자열로 직렬화. JSON 숫자 ID·0·음수·선행 0·Long 범위 초과 거부. 필수 ID는 DTO 필드에 @NotNull 추가, 선택 ID는 null 허용. 모든 Long을 문자열로 바꾸지 않음.
- 입력 금액 Long에는 @MoneyAmount 적용: 필수·1~9,999,999,999원. JSON 문자열 금액·소수→정수 강제 변환 거부. 부호 있는 합계에는 MoneyAmount를 적용하지 않음.
- JSON·query/path의 LocalDate yyyy-MM-dd, LocalTime HH:mm, YearMonth yyyy-MM 형식을 엄격하게 처리. 잘못된 날짜·초 단위 시간·형식 축약 거부. LocalDateTime 메타데이터 형식은 이번 범위에서 변경하지 않음.
- API ENUM은 DB ENUM과 별도 DTO enum으로 구현하고 ApiEnum.getValue에 계약 값을 명시. JSON 역직렬화용 @JsonCreator 팩토리에서 ApiEnum.fromValue를 호출. MVC query/path는 직접 선언한 ApiEnum 구현 enum 매개변수에 엄격한 바인딩 적용. 선택 필터는 @RequestParam(required=false) enum으로 선언하고 Optional/컬렉션 래퍼 바인딩은 이번 공통 도구 범위에 포함하지 않음. DB enum/ErrorCode를 전역 소문자로 변경하지 않음.
- 이름·제목·닉네임은 DTO에서 ApiText.normalize를 명시 호출하고 @NotBlank/@Size 등 제약을 선언. 전역 String trim은 없으며 비밀번호에는 normalize를 사용하지 않음.
- 승인 계약의 additionalProperties=false에 맞춰 알 수 없는 JSON 필드 거부. nullable 필드의 필수 키 존재 여부는 각 업무 DTO의 @JsonProperty(required=true) 등으로 후속 구현 시 명시.
- local 프로필에서만 Swagger UI /swagger-ui/index.html, /v3/api-docs 활성화. 기본/운영 프로필은 비활성. 문서 API 경로는 업무 API prefix와 별개.
- 현재 실제 업무 Controller가 없으므로 문서 paths가 비어 있는 것이 정상. 승인된 openapi.json의 미구현 endpoint를 생성 문서로 복사하지 않음. 생성 OpenAPI 3.0 문서와 설계 계약의 endpoint/필드/제약을 기능 구현 시 대조.
- 세션·CSRF 요청 지원과 사용자별 소유권·월 상태·금액 합계 검증은 인증/업무 API 구현 단계. API 기반 도구 존재만으로 업무 검증 완료를 뜻하지 않음.


### API 2차 검증 기록 — 2026-11-12

- Java 21 클린 컴파일/bootJar 및 실제 기동 확인. 실제 업무 API·자동화 테스트 소스는 추가하지 않음.
- 저장소 외부 임시 DTO/Controller로 실제 HTTP 확인. 정상 ID/금액/날짜/시간/월/ENUM 왕복, 제목 앞뒤 공백 제거, 비밀번호 공백 유지, 부호 있는 안전 정수 합계의 숫자 반환 확인.
- JSON 입력 거부 32항목: 숫자/0/음수/선행 0/범위 초과/NULL/공백 ID, 금액 0/음수/상한 초과/소수/실수/문자열/불리언/NULL, 잘못된 날짜·윤년·축약·타임스탬프, 24시·초 단위·축약·숫자 시간, 잘못된 월, 대문자/미정 ENUM, 공백/숫자 제목, 미정 필드, 필수 ID/금액 누락. 공통 VALIDATION 400 형식 확인.
- 금액 1원/9,999,999,999원 허용. query/path의 정상 형식과 잘못된 ID/날짜/시간/월/ENUM 5종 거부 확인.
- Spring 바인딩의 enum 이름 fallback을 확인하여 ApiEnumBinding의 전용 편집기로 API enum 이름 우회 입력을 차단. DB enum·오류 코드는 전역 변환하지 않음.
- 기존 500 오류 코드 대문자·예외 원문 미노출 및 204 빈 본문 유지 확인.
- Swagger 생성 문서: ID 문자열·정수 금액 상하한·camelCase enum·날짜/시간/월 패턴·오류 필수 필드·additionalProperties=false 확인. 미지정 자유형 Map 스키마는 DTO 제한 대상으로 바꾸지 않음.
- 실제 제품 앱의 local 프로필에서 UI·OpenAPI 200 및 업무 paths 빈 상태 확인. 비local 프로필에서 /v3/api-docs(.yaml 포함)·swagger-config·/swagger-ui/index.html·/swagger-ui.html 모두 404 확인.
- 승인된 계약 v0.1.1의 경계 규칙과 대조. 전체 42개 업무 API의 HTTP 구현/정합성 검증 완료를 뜻하지 않음.
- DB 테이블·Flyway 이력 변경 없음. 검증용 앱·터널 종료. UI를 직접 보려면 IDE Gradle 새로고침 후 local 앱 재실행, 기존 SSH 터널 유지.

## 인증 구현 및 테스트 — 2026-11-13

- API: GET `/api/v1/auth/csrf`, POST `/api/v1/auth/signup`, POST `/api/v1/auth/login`, POST `/api/v1/auth/logout`, GET `/api/v1/me`.
- 가입 성공 201 후 자동 로그인하지 않음. 성공 응답은 Profile 직접 반환. 개인정보처리방침 현재 버전 `v1`, `moneysheet.auth.privacy-policy-version`으로 설정하며 서버 값과 다르면 거부.
- 이메일 앞뒤 공백 제거·Locale.ROOT 소문자, 닉네임 앞뒤 공백 제거. 비밀번호는 변환하지 않음.
- 비밀번호 15~128 Unicode 코드포인트, 초기 로컬 흔한 비밀번호 차단 목록. Argon2id 19456 KiB/2회/병렬1, salt16/hash32, `{argon2id}` 식별자 저장. BouncyCastle 1.86은 Boot BOM 관리 대상이 아니므로 명시 고정.
- 제한된 초기 차단 목록은 전체 유출 비밀번호 사전을 대체하지 않음. 공개 운영 전 목록 확장 여부를 별도 검토.
- 로그인은 가입 최소 길이를 소급 적용하지 않으며 128 코드포인트 상한 적용. 미등록 계정도 더미 해시 비교. 비밀번호·쿠키·CSRF 토큰 로그 금지.
- CSRF 취득 → 가입 또는 로그인 POST에 응답의 headerName/token 전송. 로그인 성공 후 세션 ID가 교체되고 기존 CSRF 토큰은 폐기되므로 다시 취득.
- GET me는 인증 사용자 ID로 DB 조회. POST logout은 유효한 CSRF로 현재 세션·쿠키를 폐기하고 본문 없는 204 반환.
- 서버 메모리 세션, 유휴30분/최대12시간. 재시작 시 재로그인. 여러 기기 로그인 허용, 자동 로그인 없음. 비밀번호 해시는 세션에 보관하지 않음.
- 운영 쿠키 Secure/HttpOnly/SameSite=Lax/Path=/, Domain 미설정. local HTTP에서만 Secure=false. Servlet 세션 추적은 cookie로 제한.
- 프록시 헤더는 기본 신뢰하지 않음. 요청 제한은 직접 접속 remoteAddr 기준. Caddy 배포 전 신뢰 프록시/IP 처리 구성 및 위조 헤더 검증 필수. 현재 설정을 그대로 프록시 뒤에 두면 사용자들이 같은 IP 제한을 공유할 수 있음.
- 로그인 IP 1분30회, 가입 IP 1시간10회. CSRF를 통과한 인증 POST를 계산. 계정15분 내5회 실패하면 이후15분 제한. 429/Retry-After 반환. 성공 시 계정 실패 초기화.
- 카운터는 가입 IP·로그인 IP·계정별 독립 저장소에 각각 최대10,000키, TTL 경과 시 정리. 가득 차면 임의 제한 기록을 지우지 않고 신규 키 요청을 429로 거부. 재시작 시 초기화.

### 테스트 실행

Windows Java21 (Docker 불필요):
```powershell
.\gradlew.bat test
```

Ubuntu 또는 GitHub-hosted Ubuntu runner (Docker 필요):
```bash
bash ./gradlew integrationTest --no-daemon
bash ./gradlew check bootJar --no-daemon
```

- test는 단위·MVC, integrationTest는 Testcontainers의 MySQL8.4.13을 생성하여 V1을 명시 적용하고 JPA validate 후 실제 가입·동시 가입·인증 흐름 검증.
- 테스트 DB는 임시 `moneysheet_test`이며 개발 DB/비밀값/SSH 터널을 사용하지 않음. 테스트 DB 초기화만 DDL 허용, 일반 앱 Flyway 비활성화 및 migration 전용 실행은 유지.
- Docker 미준비는 실패하며 자동으로 생략하지 않음. Testcontainers Ryuk으로 임시 자원 정리. 일반 앱 JAR에는 테스트/Flyway 실행 의존성이 들어가지 않음.
- GitHub Actions `BE tests and build`가 PR의 전체 테스트·빌드를 실행하고 보고서를7일 보관. branch 보호 필수 검사 설정은 별도 확인 필요.
- 1차 가입/보안 기반8개, 2차 세션 인증까지15개 빠른 테스트 통과. 최종 실행 수·결과는 PR 기록 참조.
- 이메일 인증·분실 복구·계정 변경·탈퇴·FE 연동은 이번 범위 제외. local Swagger에서 구현된 API만 표시, 실제 세션 쿠키와 CSRF 헤더를 사용. 토큰을 문서 예시에 저장하지 않음.

### 최종 검증 기록 — #12

- Windows Java21: `test compileIntegrationTestJava bootJar` 성공, 빠른 테스트23개 통과.
- Ubuntu 격리 환경: `check bootJar` 성공, 빠른 테스트23개 + MySQL 통합 테스트4개 통과.
- 빈 임시 DB Flyway V1→JPA validate, 중복·동시 가입, 실제 DB 로그인, 계정 실패 제한, 실제 HTTP 세션 쿠키 교체·로그아웃 확인.
- 임시 MySQL은 127.0.0.1 임의 포트만 사용. 테스트 컨테이너 정리 및 기존 개발 DB healthy 유지 확인. 개발 DB에 테스트 데이터를 쓰지 않음.
- Argon2id 단일 encode 측정679ms(초기화 비용이 포함될 수 있는 참고 샘플). 부하·동시 로그인 성능 보장은 아니며 운영 자원 점검은 별도.
- bootJar에서 Flyway·JUnit·Mockito·Testcontainers 및 검증용 클래스 제외 확인. 불필요한 Boot 기본 사용자 자동 생성도 차단.
- STRICT boolean 문자열/숫자 자동 변환 거부, 요청 제한 저장소 상한·시간 경과 해제, Swagger 세션+CSRF AND 조건 회귀 검증.
- CI 결과는 PR 체크에서 확인. 개발용 HTTP 쿠키 설정으로 실제 요청 검증했으며 운영 HTTPS·프록시·FE 연동은 배포/연동 단계에서 별도 확인.

### CodeRabbit 리뷰 보완 — #12 / PR #13

- 가입 IP·로그인 IP·계정 키 저장 용량을 독립시켜 한 유형의 저장소 포화가 다른 유형의 IP 제한 용량을 소모하지 않도록 분리.
- Spring Security PathPatternRequestMatcher로 POST 로그인/가입을 판별하여 인코딩된 경로에도 같은 제한 버킷 적용.
- 세션 getAttribute/setAttribute/invalidate의 동시 무효화 예외 시 현재 요청의 인증 컨텍스트를 지우고 체인을 한 번 계속 실행.
- 로그인은 계정의 실패 수+진행 중 예약 수가5 미만일 때 원자적으로 예약한 뒤 해시 검증. 해시 계산 자체는 전역 잠금 밖에서 수행.
- 성공은 실패 수를 초기화하되 다른 진행 중 예약을 유지. 잘못된 자격 증명은 실패로 집계하며, 저장소/해시 검증 예외는 예약만 해제. close는 중복 호출해도 한 번만 반영.
- 실패5회 확정 시 기존15분 제한 유지. 진행 중 예약으로만 슬롯이 찬 경우 Retry-After1초. TTL이 지나도 진행 중 예약을 제거하지 않음.
- Windows 빠른 테스트35개 통과. 동시10요청 중5개만 해시 진입, 각 키 용량 격리·만료, 인코딩 경로·컨텍스트 경로, 세션 동시 무효화, 예약 예외 정리 회귀 검증 포함.

- 리뷰 수정 후 Ubuntu `check bootJar`: 빠른 테스트35개 + MySQL 통합 테스트4개 통과. 실제 HTTP 쿠키·동시 가입·계정 제한 회귀 확인.


## Main 거래 및 도메인 Swagger — #16

- 거래: `POST/GET /api/v1/months/{month}/transactions`, `GET/PUT/DELETE .../transactions/{id}`.
- 소유한 READY 월에서만 기록하며 과거 READY 월도 수정·삭제 가능합니다. 금액1~9,999,999,999, 날짜와 관리 월 일치, 분 단위 시간, 제목255 코드포인트 이내를 검증합니다.
- categoryId는 필수 JSON 키이며 미분류는 null입니다. 카테고리는 본인 소유 mainTransaction과 입출금 방향이 일치해야 합니다.
- 목록의 date/direction은 선택 필터입니다. limit 기본50/최대100, 날짜·시간·ID 내림차순 키셋 커서입니다. expenseTotal/incomeTotal/total은 현재 페이지가 아닌 필터 전체 합계이며 입금-지출 부호를 사용합니다.
- HMAC 서명 커서는 사용자·월·날짜·방향에 묶입니다. 프로세스별 임의 키이므로 서버 재시작 후 기존 커서는400이며 첫 페이지부터 다시 조회합니다. 기록 변경 중 페이지 snapshot은 보장하지 않습니다.
- MySQL SUM은 BigDecimal로 받고 JS 안전 정수 범위를 넘으면 AMOUNT_RANGE_EXCEEDED입니다. JPA 자동 스키마 변경·Flyway V1 수정은 없습니다.
- Main 일반 입금은 Monthly 수익을 자동 생성하지 않습니다. FE 기록 연동 및 메모/지급/Monthly/요약은 후속 작업입니다.

local Swagger UI:
- 인증: `/swagger-ui/index.html?urls.primaryName=auth`
- 카테고리: `/swagger-ui/index.html?urls.primaryName=categories`
- 예산: `/swagger-ui/index.html?urls.primaryName=budgets`
- 내역: `/swagger-ui/index.html?urls.primaryName=transactions`
- UI 상단 문서 선택으로도 전환합니다. 독립 명세는 `/v3/api-docs/{group}`, 전체 명세 `/v3/api-docs`도 유지합니다.
- 그룹에도 공통 오류 스키마·응답, 세션/CSRF·STRICT 표현·닫힌 DTO 스키마를 적용합니다. 서버 주입 CsrfToken은 사용자 입력에서 숨겼습니다.
- 인증 그룹에서 CSRF 취득 → Authorize → 로그인 → CSRF 재취득 후 업무 그룹으로 이동합니다. 브라우저가 세션 쿠키를 유지하며 필요한 CSRF 입력은 최신 토큰을 사용합니다.
- Swagger는 local에서만 노출합니다. 상기 주소는 BE 호스트/포트 뒤에 붙입니다. 실제 브라우저에서 도메인 전환·직접 URL·CSRF Try it out을 검증했습니다.
- 테스트 실제 결과와 CI 상태는 해당 PR에 기록합니다. 통합 테스트는 임시 MySQL을 사용하며 개발 DB 비밀값에 의존하지 않습니다.

## V2 — 고정여유비를 일반 고정비로 통합

- 이 절은 앞선 V1/JPA 검증 기록보다 우선하는 현재 정책입니다.
- `fixed_budget_items.kind`, `allowance_month_id`, 전용 CHECK/UNIQUE를 제거합니다. V1 파일은 변경하지 않습니다.
- 항목 ID·이름·카테고리·금액·지급 참조는 유지됩니다. 과거 고정여유비도 실제 지급 기록이 없으면 미지급입니다.
- 예산 API의 `kind`/`automatic`을 제거합니다. 첫 월 준비는 빈 고정비 목록과 미입력 식비입니다.
- 고정비·내역 카테고리 영역 분리, 양수 식비, 예산 준비/완료 및 지난달 예산 수정 제한은 유지합니다.
- 새 여유비 공식은 모든 월에 동일합니다: 월 1일 기초 0원, 같은 월 전날 마감 + 오늘 식비 - 오늘 본인 지출.
- BE 요약 API 구현은 #20 후속입니다. 이번 변경으로 요약 API가 완성되는 것은 아닙니다.

### 개발 DB 전환 절차 (병합 후 수행)

1. 연결된 FE/BE 쓰기를 중지하고 DB 백업·복원 가능 여부를 확인합니다.
2. 기존 명시적 migration 환경변수 `MIGRATION_DB_URL`, `MIGRATION_DB_USERNAME=moneysheet_migration`, `MIGRATION_DB_PASSWORD`를 설정합니다. 값을 로그나 Git에 남기지 않습니다.
3. Java 21 환경에서 `./gradlew dbInfo dbValidate`로 V1 적용 상태와 V2 대기를 확인합니다.
4. `./gradlew dbMigrate` 실행 후 `./gradlew dbInfo dbValidate`로 V2 성공을 확인합니다. 일반 앱 실행으로 migration을 수행하지 않습니다.
5. FE #23과 BE #28 버전을 함께 전환하고 예산 조회/생성/편집·지급 참조를 확인한 뒤 쓰기를 재개합니다.
6. 이전 BE는 제거된 kind를 요구하므로 V2 뒤에 이전 바이너리만 되돌리지 않습니다. 실패 시 쓰기를 유지 중지하고 백업 또는 별도 검토한 전진 migration으로 복구합니다. MySQL DDL의 트랜잭션 롤백을 가정하지 않습니다.

검증용 V1→V2 테스트는 격리 Testcontainers DB만 사용합니다. 이 PR에서 실제 개발 DB에는 V2를 적용하지 않습니다.
