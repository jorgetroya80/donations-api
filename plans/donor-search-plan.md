# Plan: Donor search on GET /api/v1/donors

Source spec: `plans/donor-search.md` (issue #37).

## Goal
Optional `search` query param on `GET /api/v1/donors` — case-insensitive partial match over
`fullName` + `nationalId`, combined with existing `page`/`size`/`sort`. Blank/absent == today.
Response shape unchanged (`Page<DonorResponse>`, VIA_DTO).

## Dependency graph
```
DonorRepository.search()  ← JPQL @Query, no deps
        │
        ▼
DonorService.listDonors(search, pageable)  ← branch blank→findAll
        │
        ▼
DonorController.listDonors(search, pageable)  ← @RequestParam
        │
        ▼
DonorManagementTest  ← integration, exercises full path
```
Strictly bottom-up: repo → service → controller → tests. But sliced **vertically** so each
slice is a runnable end-to-end path, not a horizontal layer.

## Vertical slices

### Slice 1 — Tracer bullet: search by fullName end-to-end
Thinnest complete path: repo query + service branch + controller param + ONE passing
integration test (fullName substring match). Proves the whole wiring before adding cases.

### Slice 2 — Extend: nationalId, paging combo, blank behavior
Same code path already exists; add remaining test cases that the JPQL/branch already satisfy.
No new production code expected beyond Slice 1 (the OR-clause already covers nationalId).

## Checkpoints
- **CP1** (after Slice 1): `./gradlew test --tests "*DonorManagementTest"` green, fullName
  search test passes. Confirms repo→service→controller wiring.
- **CP2** (after Slice 2): full `./gradlew build` green; all 4 search cases + existing donor
  tests pass. Confirms acceptance criteria 1–6.
- **CP3** (manual, optional): run app, hit example URL, confirm filtered/paginated/correct
  shape and `search` param in `/v3/api-docs`.

## Out of scope
SPEC.md file creation (deliverable tracked separately); DB index/pg_trgm; OpenAPI client
regen (frontend follow-up); commit (Jorge commits himself).

## Boundaries
- Always: run tests before done; keep VIA_DTO response shape; match @Query/@RequestParam house style.
- Ask first: dependencies, Specifications, DB migrations/indexes.
- Never: change PagedModel serialization; commit; weaken existing tests.
