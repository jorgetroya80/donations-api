# ADR-002: Deploy on Render + Neon managed Postgres

## Status

Accepted

## Date

2026-06-29

## Context

The API is a single Spring Boot service maintained by a small team with limited operations
capacity and a cost-sensitive budget. We need a production home that:

- runs our existing Docker image with minimal platform-specific glue,
- terminates TLS for us (the app should not manage certificates),
- provides a managed PostgreSQL instance (no DB ops),
- is cheap enough to start on a free tier,
- keeps latency between the app and the database low.

## Decision

Deploy the Docker image as a **Render web service**, backed by **Neon** managed PostgreSQL.

- Render service: `type: web`, `runtime: docker`, `plan: free`, `region: frankfurt`,
  auto-deploy on push (`render.yaml:2-8`).
- Neon Postgres in `eu-central-1`, co-located with Render Frankfurt to minimize DB latency;
  credentials are injected via `SPRING_DATASOURCE_*` env vars (`sync: false`, set in the
  Render dashboard).
- The app binds to the platform-provided `$PORT` rather than the fixed local `8081`.
- JVM heap is sized to the 512 MB free tier with `-XX:MaxRAMPercentage=75.0` (`Dockerfile`).
- The `prod` profile sets `server.forward-headers-strategy: framework` so the app trusts
  Render's `X-Forwarded-*` headers for HTTPS detection, marks the session cookie `Secure`,
  and disables Swagger/api-docs (`application.yaml:51-59`).
- The Render health check targets `/actuator/health/liveness`, **not** the full
  `/actuator/health` (`render.yaml:8`).

## Alternatives Considered

### Larger paid Render tier
- Pros: more memory/CPU, no cold starts.
- Cons: ongoing cost not yet justified by traffic.
- Rejected for now: free tier meets current load; upgrading later is trivial.

### Fixed port 8081 instead of `$PORT`
- Pros: matches local dev.
- Cons: Render assigns the port dynamically; a hardcoded port fails to bind.
- Rejected: brittle on the platform.

### Full `/actuator/health` as the liveness probe
- Pros: also catches DB outages.
- Cons: includes the DB check, so a transient Neon blip would fail liveness and trigger an
  unnecessary restart loop.
- Rejected: liveness should reflect the process, not its dependencies.

### Self-managed VM / AWS / Azure
- Pros: full control, more scaling headroom.
- Cons: we'd own OS patching, TLS, and Postgres operations.
- Rejected: too much ops for a small team.

## Consequences

- Low-cost, low-ops production with platform-managed TLS and database.
- **Hard to reverse:** `render.yaml`, the `$PORT` binding, the forwarded-headers/secure-cookie
  prod config, and the env-var secret wiring are platform-specific. Moving to another host
  means redoing this layer (and would warrant a superseding ADR).
- Free-tier trade-offs are accepted: cold starts after idle and constrained CPU/memory.

See also: `plans/deploy-render.md`, `docs/architecture.md`.
