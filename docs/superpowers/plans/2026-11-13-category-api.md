# Category API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement WBS 3.1 authenticated category listing and create-or-reuse APIs for the backend.

**Architecture:** Keep API values separate from DB enums. Controller accepts contract DTOs and obtains the user ID from `AuthPrincipal`; service owns validation, normalization, lookup, and insertion. The database unique key remains the final concurrency guard; a duplicate-insert recovery must query in a fresh transaction.

**Tech Stack:** Java 21, Spring Boot 4.1.2, Spring Data JPA, MySQL 8.4, JUnit, MockMvc, Testcontainers.

**Spec:** `../../../../moneysheet_docs/docs/contracts/api-contract.md`, `../../../../moneysheet_docs/docs/contracts/openapi.json`, `../../../../moneysheet_docs/docs/engineering/erd.md`, WBS 3.1. These paths are relative to this plan; the documents are in the sibling `moneysheet_docs` repository.

## Global Constraints

- Implement only `GET /api/v1/categories` and `POST /api/v1/categories`; no category update/delete, FE work, or V1 migration edit.
- API `fixedBudget` maps to DB `FIXED_BUDGET` + `NONE` and API `direction: null`; `mainTransaction` requires `income` or `expense`.
- Both new and reused POST results are HTTP 200. IDs are decimal strings. Authenticated personal-data responses use `Cache-Control: no-store`.
- Normalize a display name by stripping outer whitespace; derive `name_key` by lowercasing with `Locale.ROOT`. Preserve internal whitespace. Validate both against DB length 100.
- Existing `ErrorCode`, `ApiEnum`, CSRF, session and trace-ID behavior remain in force. Never treat an unrelated integrity failure as a duplicate.
- Windows fast tests and isolated MySQL integration tests are separate; do not use the development or production database for tests.

## Review Focus

- A name with only Unicode whitespace must fail validation; cover in Task 2.
- An input whose normalized key exceeds the 100-character DB limit must fail before insert; cover in Task 2.
- Same normalized name under another user or another scope/direction must remain a separate category; cover in Task 3.
- A concurrent duplicate insert must return the existing row rather than 500 or two rows; cover in Task 3.
- A non-duplicate database integrity error must not be reported as successful reuse; cover in Task 3.

---

### Task 1: Read-only category API

**Files:** Create `moneysheet/src/main/java/com/danielhw99/category/api/CategoryScopeValue.java`, `CategoryDirectionValue.java`, `CategoryResponse.java`, `CategoryController.java`, `moneysheet/src/main/java/com/danielhw99/category/service/CategoryService.java`; modify `moneysheet/src/main/java/com/danielhw99/category/repository/CategoryRepository.java`; add focused MVC/service tests.

**Interfaces:** `CategoryService.list(Long userId, CategoryScopeValue scope, CategoryDirectionValue direction)` returns category response DTOs. GET wraps them in `{ "items": [...] }` per OpenAPI `Categories`. API enum values use `ApiEnum` and `@JsonCreator`; DB enums remain unchanged.

- [ ] Write failing tests for authenticated own-only GET, scope/direction filtering, empty list, invalid enum, anonymous GET, and no-store response.
- [ ] Run the focused tests and confirm expected failures.
- [ ] Implement Repository owner-filtered queries, enum adapters, response DTO, service and controller GET; do not load all users' categories and filter in memory.
- [ ] Run focused tests and `gradlew.bat test`; confirm pass.
- [ ] Commit as `[feat] #14 카테고리 사용자별 조회 API 구현`.

### Task 2: Create-or-reuse and validation

**Files:** Create `moneysheet/src/main/java/com/danielhw99/category/api/CategoryInput.java`; extend `CategoryController`, `CategoryService`, and `CategoryRepository`; add focused unit/MVC tests.

**Interfaces:** `CategoryService.createOrReuse(Long userId, CategoryInput input)` returns `CategoryResponse`. `CategoryInput` requires `name`, `scope`, and `direction` including an explicit nullable direction field.

- [ ] Write failing tests for new 200 response, same-key reuse, fixedBudget/null and mainTransaction/income|expense combinations, blank/overlong/unknown-field inputs, and CSRF/auth failures.
- [ ] Run focused tests and confirm expected failures.
- [ ] Implement normalized display/key values, explicit combination checks, owner-key lookup and insert with the existing user relationship.
- [ ] Run focused tests and `gradlew.bat test`; confirm pass.
- [ ] Commit as `[feat] #14 카테고리 생성 및 이름 중복 재사용 구현`.

### Task 3: Concurrent uniqueness, contract and final verification

**Files:** Extend `CategoryService` with isolated duplicate recovery if needed; add `moneysheet/src/integrationTest/java/com/danielhw99/category/CategoryIntegrationTest.java`; add or extend OpenAPI contract checks; update `moneysheet/README.md` with actual verification results.

**Interfaces:** Both racing POST requests return the same category ID. Recovery occurs after a failed insertion transaction has ended; only MySQL duplicate-key 1062 for `uq_categories_name` is eligible for reuse lookup.

- [ ] Write a failing isolated-MySQL integration test for simultaneous same-key creation and tests for other user/scope/direction separation and unrelated integrity errors.
- [ ] Run the focused integration test against Testcontainers and confirm the expected failure.
- [ ] Implement transaction-safe duplicate recovery with a fresh read; keep non-duplicate database errors visible as server errors.
- [ ] Verify GET/POST generated OpenAPI path, DTO shape and security against the approved contract; run `test`, `integrationTest`, `check bootJar` where the required Docker host exists.
- [ ] Record exact passed and unrun verification in README/PR; commit as `[test] #14 카테고리 동시성 및 API 계약 검증` (or `[fix]` if production logic changes materially).

## Execution note

The issue already exists as #14; do not create a duplicate. The branch is `feature/14-category-api`. Before implementing, recheck the issue and the approved contract. No production code, tests or implementation commits have been made by this plan.
