# Silence PageImpl warning: stable page serialization (VIA_DTO)

## Context

On startup, Spring Data warns that serializing `PageImpl` directly does not guarantee a stable JSON structure: it is an internal class, its JSON shape is derived from getter introspection and may change between versions (a real risk given the Jackson 3 migration in Boot 4). Four controllers return `Page<...>`:

- `src/main/kotlin/com/example/donations/donation/DonationController.kt:32`
- `src/main/kotlin/com/example/donations/expense/ExpenseController.kt:32`
- `src/main/kotlin/com/example/donations/user/UserController.kt:30`
- `src/main/kotlin/com/example/donations/donor/DonorController.kt:25`

Analysis validated by the spring-boot-engineer agent. Options discarded: HATEOAS (overkill), custom DTO (more code for the same result), ignoring it (the generated TS client depends on the current shape and could break silently on an upgrade).

## Change

1. Add to `DonationsApplication.kt` (or an existing web `@Configuration` class):

```kotlin
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
```

Import: `org.springframework.data.web.config.EnableSpringDataWebSupport`. Controllers stay untouched: they keep returning `Page<...>`; Spring wraps it in `PagedModel` at serialization time.

2. Update ~5 test assertions (4 files, `$.totalElements` → `$.page.totalElements`; the `$.content[...]` ones do not change):
   - `src/test/kotlin/com/example/donations/DonorManagementTest.kt:185`
   - `src/test/kotlin/com/example/donations/UserManagementTest.kt:141`
   - `src/test/kotlin/com/example/donations/ExpenseRecordingTest.kt:138,156`
   - `src/test/kotlin/com/example/donations/DonationRecordingTest.kt:132,150`

3. Regenerate OpenAPI spec + TypeScript client (existing springdoc-openapi-gradle-plugin + openapi-ts pipeline). springdoc 3.0.2 supports VIA_DTO and documents the nested `page` schema.

## JSON contract impact (breaking for consumers)

Before (root level): `totalElements`, `totalPages`, `pageable`, `sort`, `first`, `last`, `numberOfElements`, `empty`...
After: `{ "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }`

`packages/api-client/src/generated/types.gen.ts` changes (`PageDonationResponse` types, etc.) → **major bump of the `api-client` package**; consumers migrate `totalElements` → `page.totalElements`. `docs/PRD.md` does not pin the JSON shape, no documentation impact.

## Verification

1. `./gradlew test` — updated integration tests pass.
2. Start the app (`TestDonationsApplicationKt bootRun`), `GET /api/donations`: new `page{}` format present, warning gone from the log.
3. Regenerate the TS client and confirm it compiles.
