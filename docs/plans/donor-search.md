# Plan: Add `search` query param to GET /api/v1/donors

## Context

Issue #37. The donations-frontend donor picker (`DonorPicker`) attaches a donor to a donation,
but `GET /api/v1/donors` accepts only `page` / `size` / `sort` — **no way to search**. As the
donor list grows, users must page/scan to find one donor. Client-side filtering is incorrect
(caps results, drops donors beyond the cap).

Outcome: an optional `search` query param doing case-insensitive partial match over `fullName`
and `nationalId`, combined with existing paging/sort. Once shipped and the OpenAPI client is
regenerated, the frontend upgrades the picker to a debounced server-side typeahead.

---

## SPEC.md content (written to repo root at implement time)

```markdown
# Spec: Donor search on GET /api/v1/donors

## Objective
Add an optional `search` query param to `GET /api/v1/donors` so the frontend donor picker can
look up a donor by name or national ID instead of paging/scanning the whole table.

- User: OPERATOR / TREASURER using the donations-frontend donor picker.
- Behavior: case-insensitive, partial match over `fullName` AND `nationalId` (OR across the two
  columns). Filter first, then paginate, then sort.
- Empty/absent/whitespace-only `search` behaves exactly as today (plain paginated list).
- Response shape unchanged: `Page<DonorResponse>` serialized VIA_DTO (`content` + nested `page`).
- Example: `GET /api/v1/donors?search=per&page=0&size=10&sort=fullName,asc`

Acceptance criteria:
1. `search=per` returns only donors whose `fullName` or `nationalId` contains "per" (any case).
2. Match is case-insensitive (`PER`, `per`, `Per` equivalent).
3. Match is partial/substring, not prefix-only.
4. `search` combines correctly with `page`, `size`, `sort` (totals reflect filtered set).
5. Absent, empty, or whitespace-only `search` => identical to current behavior.
6. Response JSON shape unchanged (`$.content[]`, `$.page.totalElements`, `$.page.number`).

## Tech Stack
Kotlin 2.2 / Java 24, Spring Boot 4.0.5 (Spring MVC + Spring Data JPA), PostgreSQL 18.3,
JUnit 5 + Testcontainers. No new dependencies.

## Commands
- Build:  `./gradlew build`
- Test (single):  `./gradlew test --tests "com.example.donations.DonorManagementTest"`
- Test (all):  `./gradlew test`
- Run (Testcontainers):  `./gradlew -PmainClass=com.example.donations.TestDonationsApplicationKt bootRun`

## Project Structure (touched)
src/main/kotlin/com/example/donations/donor/
  DonorController.kt   → add `@RequestParam(required = false) search: String?`
  DonorService.kt      → branch: blank => findAll, else filtered finder
  DonorRepository.kt    → new @Query finder
  DonorDtos.kt         → unchanged
src/test/kotlin/com/example/donations/
  DonorManagementTest.kt → add search integration tests

## Code Style
Match existing house style. Repository uses `@Query` JPQL with a single `:search` binding reused
across both columns (mirrors `DonationRepository.kt` / `ExpenseRepository.kt`):

    interface DonorRepository : JpaRepository<Donor, Long> {
        fun existsByNationalId(nationalId: String): Boolean

        @Query(
            """
            SELECT d FROM Donor d
            WHERE LOWER(d.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(d.nationalId) LIKE LOWER(CONCAT('%', :search, '%'))
            """
        )
        fun search(@Param("search") search: String, pageable: Pageable): Page<Donor>
    }

Service trims and treats blank as absent (no `%%` match-all):

    @Transactional(readOnly = true)
    fun listDonors(search: String?, pageable: Pageable): Page<Donor> {
        val term = search?.trim()
        return if (term.isNullOrEmpty()) donorRepository.findAll(pageable)
               else donorRepository.search(term, pageable)
    }

Controller binds the optional param alongside `Pageable` (mirrors `DonationController.kt`):

    @GetMapping
    fun listDonors(
        @RequestParam(required = false) search: String?,
        pageable: Pageable,
    ): Page<DonorResponse> =
        donorService.listDonors(search, pageable).map { DonorResponse.from(it) }

## Testing Strategy
Framework: `@SpringBootTest` + `@AutoConfigureMockMvc` + Testcontainers (existing
`DonorManagementTest.kt` style, session auth via `TestAuth`). Seed with `createDonor(...)` helper.
Add tests:
- search matches by fullName substring (case-insensitive).
- search matches by nationalId substring.
- search combines with paging/sort (filtered totals).
- blank/whitespace search == unfiltered list (current behavior).
Assert VIA_DTO shape: `$.content`, `$.content.length()`, `$.page.totalElements`, `$.page.number`.

## Boundaries
- Always: run `./gradlew test` before reporting done; keep response shape unchanged; match
  existing @Query / RequestParam house style.
- Ask first: adding dependencies; introducing JpaSpecificationExecutor/Specifications (new
  pattern — not needed here); DB migrations/indexes.
- Never: change the PagedModel/VIA_DTO serialization; commit (Jorge commits himself); remove or
  weaken existing tests.

## Success Criteria
All six acceptance criteria above pass via DonorManagementTest; `./gradlew build` green;
OpenAPI doc shows the new optional `search` param on GET /api/v1/donors (springdoc auto-picks
up `@RequestParam`); existing donor tests still pass.

## Open Questions
- Index on `lower(full_name)` / `lower(national_id)` for performance at scale? Out of scope for
  this change. LIKE '%term%' won't use a btree index anyway; revisit with pg_trgm if the table
  grows large.
```

---

## Implementation steps (after spec approved)

1. `DonorRepository.kt` → add `search(@Param("search") search: String, pageable: Pageable): Page<Donor>`
   with the JPQL above. Imports: `org.springframework.data.domain.Page`,
   `org.springframework.data.domain.Pageable`, `org.springframework.data.jpa.repository.Query`,
   `org.springframework.data.repository.query.Param`.
   → verify: compiles.
2. `DonorService.kt` → change `listDonors` signature to `(search: String?, pageable)`, trim/blank
   branch. → verify: compiles.
3. `DonorController.kt` → add `@RequestParam(required = false) search: String?`, pass through.
   Import `org.springframework.web.bind.annotation.RequestParam`. → verify: compiles.
4. `DonorManagementTest.kt` → add the 4 search tests. → verify:
   `./gradlew test --tests "com.example.donations.DonorManagementTest"`.
5. Full `./gradlew build`. → verify: green. Report to Jorge (do not commit).

## Verification (end-to-end)
- `./gradlew build` green.
- Run app via TestDonationsApplication, hit
  `GET /api/v1/donors?search=per&page=0&size=10&sort=fullName,asc` with an OPERATOR session →
  filtered, paginated, correctly-shaped response.
- Confirm `/v3/api-docs` (dev profile) lists `search` as an optional query param.
