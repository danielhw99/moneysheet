# Monthly budget implementation plan

> **For Codex:** Use superpowers:executing-plans. Implement each task with a failing test, production change, passing test, then commit. Requirements: issue #15, WBS 3.2, API contract v0.1.1. The user opted to proceed after confirming Asia/Seoul.

## Scope

Implement the seven monthly-budget endpoints using the existing Spring/JPA schema. Current-month writes use `Asia/Seoul`; historical READY budgets remain readable. No migration or FE changes.

## Tasks and commits

1. `feat: #15 prepare current-month budget drafts` — preparation endpoint, prior-month copy, first-month placeholder, idempotence, month guard; tests.
2. `feat: #15 finalize setup and read ready budgets` — atomic setup, validated categories/items/food, ready summary and installment math; tests.
3. `feat: #15 edit ready budget items and food` — READY CRUD, payment references, ownership, kind immutability; tests.
4. `test: #15 cover budget API and document security` — HTTP/CSRF/ownership and MySQL integration, OpenAPI security, verification. Push branch and open PR against dev; observe CodeRabbit and CI.

## Verification

Run Gradle unit tests and integration-test compilation locally, then full MySQL integration via GitHub Actions. Confirm no unstaged files, commit boundaries, PR review/CI status. Do not merge.
