# ADR-001: Session-based authentication over JWT

## Status

Accepted

## Date

2026-06-29

## Context

The API serves a single, same-origin web frontend operated by a small organization. It runs
as a **single instance** (one Render web service — see [ADR-002](ADR-002-deploy-render-neon.md))
with limited operational capacity. We need authentication that:

- supports immediate, server-side session revocation (e.g. forced logout after a password
  change or an admin reset),
- enforces a first-login password rotation for the seeded admin account,
- resists brute-force and session-fixation attacks,

without standing up external infrastructure (token signing services, a session store, an
identity provider) that a small single-instance deployment cannot justify.

## Decision

Use **Spring Security with server-side HTTP sessions** (`JSESSIONID` cookie), not stateless tokens.

- Session cookie is `HttpOnly` and `SameSite=Lax`, and `Secure` in the `prod` profile
  (`src/main/resources/application.yaml:24` and `:55`).
- CSRF token protection is disabled (`SecurityConfig.kt:32`); `SameSite=Lax` plus the
  same-origin frontend is the CSRF mitigation.
- Brute-force throttling is an in-memory `LoginAttemptService`: **5 consecutive failures lock
  the account for 15 minutes** (`LoginAttemptService.kt:55-56`), with generic `401`s to avoid
  account enumeration.
- The session is rotated on successful login to prevent session fixation, and live sessions
  are tracked in-memory by `UserSessionTracker` so they can be revoked on password change
  (`AuthController.kt`, `UserSessionTracker.kt`).
- A seeded admin (`V3__seed_admin_user.sql`) carries a `must_change_password` flag
  (`V7__add_must_change_password.sql`); `PasswordChangeRequiredFilter` blocks all endpoints
  except login, logout, and the password-change endpoint until it is rotated.

## Alternatives Considered

### JWT / stateless tokens
- Pros: no server-side session state; scales horizontally without shared storage.
- Cons: revocation requires a denylist or short TTLs + refresh tokens — i.e. you reintroduce
  server state to get the revocation we need on day one.
- Rejected: the revocation complexity buys nothing for a single same-origin frontend.

### OAuth2 / external identity provider
- Pros: offloads credential handling; standard SSO.
- Cons: external dependency and integration overhead; the org manages a handful of internal
  accounts, not a federated user base.
- Rejected: overkill for the user model.

### Redis-backed lockout / Spring `SessionRegistry`
- Pros: cluster-safe brute-force state and session tracking.
- Cons: adds an external datastore to operate.
- Rejected **for now**: unnecessary at single-instance scale (see limitation below).

## Consequences

- Simple, stateful, production-grade auth for one instance, with no extra infrastructure.
- Immediate session revocation and forced password rotation work out of the box.
- **Known limitation:** the brute-force counters (`LoginAttemptService`) and session tracker
  (`UserSessionTracker`) are **in-memory**. They reset on restart and are **not cluster-safe**.
  Horizontal scaling is the trigger to revisit this decision — moving that state to Redis or
  the database (and likely adopting `SessionRegistry`) would be the follow-up, recorded as a
  new ADR that supersedes this one.

See also: `docs/architecture.md`, `docs/security-hardening.md`, `plans/security-hardening.md`.
