# ADR-003: Page serialization via `VIA_DTO`

## Status

Accepted

## Date

2026-06-29

## Context

Several list endpoints return Spring Data `Page<T>` results. By default Spring serializes the
`PageImpl` object directly, and on Spring Boot 4 / Jackson this emits a warning that the
default page JSON structure is **unstable across versions** and may change without notice.

We publish a generated TypeScript client (`@jorgetroya80/donations-api-client`) whose types are
derived from the OpenAPI contract. A silent change to the pagination shape on a future
Spring/Jackson upgrade would break that client unannounced — the worst kind of breakage.

## Decision

Enable the stable DTO serialization mode globally on the application entry point:

```kotlin
@EnableSpringDataWebSupport(
    pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO
)
```

(`DonationsApplication.kt:8`)

Paginated responses are serialized via a stable `PagedModel` DTO instead of the raw
`PageImpl`. Pagination metadata moves under a `page` object — e.g. `totalElements` and
`totalPages` are at `$.page.totalElements` / `$.page.totalPages` rather than the response root.

## Alternatives Considered

### Ignore the warning, keep default `PageImpl` serialization
- Pros: zero work, no contract change today.
- Cons: the JSON shape can change silently on the next Spring/Jackson bump, breaking clients
  without warning.
- Rejected: trades a controlled change now for an uncontrolled one later.

### Spring HATEOAS
- Pros: standardized, hypermedia-rich pagination envelope.
- Cons: heavier dependency and response format than this API needs.
- Rejected: disproportionate to a simple paginated list.

### Hand-rolled page-wrapper DTO per endpoint
- Pros: full control of the shape.
- Cons: boilerplate on every paginated endpoint; easy to drift out of sync.
- Rejected: `VIA_DTO` gives a consistent stable shape with one annotation.

## Consequences

- Pagination JSON is now stable and upgrade-safe; new paginated endpoints inherit the shape
  automatically.
- **Breaking change:** `totalElements` / `totalPages` moved from the response root to
  `$.page.*`. This requires a **major** version bump of the generated TypeScript client; the
  package version is coupled to the backend release, so consumers upgrade deliberately.

See also: `plans/page-serialization-via-dto.md`.
