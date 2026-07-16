# ADR-004: RFC 9457 Problem Details for error responses

## Status

Accepted

## Date

2026-07-16

## Context

A codebase review against API standards (2026-07-16, `plans/api-standards-remediation.md`)
found two problems with the API's error responses:

1. Errors use a bespoke shape — `ErrorResponse {status, error, message, fields?, timestamp}`
   (`src/main/kotlin/com/example/donations/infrastructure/error/ErrorResponse.kt`) — rather
   than the IETF standard for HTTP API errors, RFC 9457 Problem Details (which obsoleted
   RFC 7807). Consumers, gateways, and monitoring tools that understand
   `application/problem+json` cannot parse our errors without custom code, and the shape is
   not documented in the OpenAPI spec that generates the published TypeScript client.
2. The API actually has **two** error shapes. Unauthenticated requests are rejected by the
   Spring Security filter chain before any controller runs, so the `@RestControllerAdvice`
   never fires; instead `SecurityConfig`'s `authenticationEntryPoint`
   (`src/main/kotlin/com/example/donations/infrastructure/config/SecurityConfig.kt:47-54`)
   hand-writes a JSON string whose fields diverge from `ErrorResponse` (no `fields` key,
   different timestamp format). A client that parses a 400 correctly can mis-parse a 401.

Spring Boot 4 / Spring Framework 7 ship first-class `ProblemDetail` support, making the
standard shape essentially free to adopt. The frontend is maintained by the same team, and
the api-client is versioned, so a coordinated contract change is feasible.

## Decision

All error responses — from `GlobalExceptionHandler` **and** from the security filter chain —
return RFC 9457 `ProblemDetail` with content type `application/problem+json`:

- `status`: HTTP status code; `title`: reason phrase; `detail`: human-readable message;
  `type`: `about:blank` (default; no problem-type catalog yet); `instance`: request path.
- Validation errors (400) carry a `fields` **extension property** — a map of field name to
  message — preserving the per-field error UX the frontend relies on.
- The must-change-password 403 emitted by
  `src/main/kotlin/com/example/donations/infrastructure/config/PasswordChangeRequiredFilter.kt`
  keeps its machine-readable discriminator as a `code` extension property
  (`"PASSWORD_CHANGE_REQUIRED"`), so clients can distinguish it from a role denial.
- Implemented in `src/main/kotlin/com/example/donations/infrastructure/error/GlobalExceptionHandler.kt`
  (all handlers), `src/main/kotlin/com/example/donations/infrastructure/config/SecurityConfig.kt`
  (401 entry point), and `PasswordChangeRequiredFilter` (403), the filter-level producers
  serializing with the application `ObjectMapper` so all emit an identical shape. The custom
  `ErrorResponse` class is deleted.

## Alternatives Considered

### Keep the custom `ErrorResponse`, fix only the divergent 401
- Pros: no contract break; no api-client major bump; smallest diff.
- Cons: stays proprietary; error schema remains undocumented/unknown to standard tooling;
  the review finding is only half-addressed and would likely be revisited.
- Rejected: the API is young and has one first-party consumer — the cost of breaking the
  contract will never be lower than now.

### Adopt ProblemDetail alongside `ErrorResponse` via content negotiation
- Pros: backward compatible during a migration window.
- Cons: two shapes to test, document, and eventually remove; complexity is unjustified for a
  single first-party consumer.
- Rejected: transition machinery for a migration that can be done atomically.

### Define a problem-type catalog (custom `type` URIs per error class)
- Pros: machine-distinguishable error kinds beyond the status code.
- Cons: requires designing and hosting stable type URIs; no consumer needs this today.
- Rejected for now: `type` stays `about:blank`; a catalog can be layered on later without
  another breaking change.

## Consequences

- **Breaking change to the error contract.** Every 4xx/5xx body changes shape
  (`message` → `detail`, `error` → `title`, `timestamp` dropped, content type becomes
  `application/problem+json`). The published TypeScript api-client requires a **major
  version bump** on next publish, and the frontend must migrate its error handling.
- 401s produced by the filter chain and errors produced by controllers/advice are now
  byte-for-byte the same shape — one parser everywhere.
- Standard tooling (gateways, log processors, HTTP clients) can consume errors generically.
- The `fields` extension is our own convention on top of the standard; it is the one part a
  generic RFC 9457 consumer won't know about, and should be documented in the OpenAPI spec
  when error schemas are annotated (deferred follow-up in `plans/api-standards-remediation.md`).
- Revisit trigger: if error kinds need machine discrimination beyond status codes, introduce
  a problem-type catalog (non-breaking).
