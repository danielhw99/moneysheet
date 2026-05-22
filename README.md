# MoneySheet Backend

MoneySheet 백엔드 프로젝트입니다.

## Convention

- 공식 기준 문서: [../docs/planning/project-convention.md](../docs/planning/project-convention.md)
- Branch Rule: `feature/<Issue_Number>-<Domain>`
- Commit Rule: `[<Prefix>] #<Issue_Number> <Description>`
- PR Rule: `[<Prefix>] #<Issue_Number> <Description>`

## Working Rules

1. 작업 시작 전 issue를 생성합니다.
2. `develop` branch에서 직접 작업하지 않습니다.
3. feature branch에서 작업 후 PR을 생성합니다.
4. PR은 `develop`으로 squash merge 합니다.
5. merge 전 빌드/테스트 상태를 확인합니다.
