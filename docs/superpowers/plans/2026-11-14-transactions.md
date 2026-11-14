# #16 Main transactions and grouped Swagger

Goal: implement issue #16 using the existing schema, direct DTO responses and existing 18 error codes.
Execution: single agent, inline. User authorizes issue/branch/commit/push/PR and CodeRabbit fixes, not merge.

1. Swagger: add four local GroupedOpenApi beans to OpenApiConfiguration; apply AuthOpenApiConfiguration and closed schemas globally. Test group declarations, security customization and local-only docs. Commit.
2. CRUD: TransactionDtos and TransactionDirectionValue; entity update method; owner/month/READY guards in TransactionService; TransactionController returns 201/200/204 and no-store. Past READY months are writable. Nullable categoryId is required, referenced scope/direction must match. Amount 1..9999999999; title1..255 code points. Test validation and ownership. Commit.
3. Queries: Criteria repository keyset order date,time,id DESC; date/direction filters; limit default50/max100; query aggregate BigDecimal sums independently of cursor/page. HMAC-signed cursor bound to owner,month,date,direction; process-local key, restart invalidates old cursors with400. MySQL integration covers CRUD, category validation, pagination and totals; grouped OpenAPI is validated at real runtime. Document optional direction filter as additive contract change. Commit.

No schema/Flyway changes. No FE changes. Unit/MVC tests on Windows; isolated MySQL in Ubuntu and CI. Check real local Swagger using a separate port if user BE is running. Review CodeRabbit feedback against code before fixes. Keep actual verification limits explicit.
