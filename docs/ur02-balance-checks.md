# UR02 Daily 실제 계좌 잔액

GET/PUT `/api/v1/daily-balance-checks/{date}` (로그인 및 쓰기 CSRF 필요).
응답: `date`, `actualBalance` (null 또는 0~9,999,999,999), `locked`.
PUT 본문: `actualBalance`, `locked` (필수 boolean).
미입력 날짜는 금액 null, locked true. 사용자·날짜당 한 행을 갱신한다.
미래 날짜는 VALIDATION으로 거부하며 지난 날짜는 조회·수정할 수 있다.
잠금은 UI 편집 상태이며 서버 접근 권한을 대신하지 않는다.
동시 쓰기는 사용자 잠금으로 직렬화하고 마지막 저장을 반영한다.
잔액은 요약 계산에 포함되지 않는다. 탈퇴 시 삭제한다.

배포 전 V4 마이그레이션이 필요하다. 기존 명시적 dbInfo/dbMigrate/dbValidate 절차를 사용한다.
일반 앱 시작은 마이그레이션하지 않는다. 기존 개발 DB에는 이번 작업에서 적용하지 않는다.
