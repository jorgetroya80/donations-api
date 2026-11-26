# TODO — API Standards & Architecture Remediation

Plan: [api-standards-remediation.md](api-standards-remediation.md)

## Phase 1: Error Contract
- [x] Task 1: ADR-004 — ProblemDetail error responses
- [x] Task 2: Migrate GlobalExceptionHandler to ProblemDetail (+ test updates)
- [x] Task 3: Unify filter-level 401 in SecurityConfig
- [x] CHECKPOINT: full tests green, one error shape everywhere, report to Jorge

## Phase 2: Auth Hardening
- [x] Task 4: Validate LoginRequest + move DTOs to AuthDtos.kt
- [x] Task 5: Extract AuthService from AuthController
- [x] CHECKPOINT: full tests green, manual login flow, report to Jorge

## Phase 3: Consistency & Robustness
- [x] Task 6: Standardize UserController (201 + @PreAuthorize)
- [x] Task 7: Verify/fix lazy donor access (test + fetch join if needed)
- [x] Task 8: Deduplicate default date-range helper
- [x] CHECKPOINT: full suite green, smoke test, NO COMMIT — report and wait
