# ADR-005: OWASP-vocabulary event logging with donor data excluded by construction

## Status

Accepted

## Date

2026-08-04

## Context

The API is deployed (ADR-002) and has almost no operational visibility. Three log statements
exist in the whole codebase: two in the login path
(`src/main/kotlin/com/example/donations/user/AuthService.kt:27`, `:37`), one on account lockout
(`src/main/kotlin/com/example/donations/user/LoginAttemptService.kt:40`), and a catch-all
stacktrace on unhandled exceptions
(`src/main/kotlin/com/example/donations/infrastructure/error/GlobalExceptionHandler.kt:61`).
There is no way to reconstruct what happened during a failed request without querying the
database after the fact or adding temporary logging and waiting for a recurrence.

Three forces constrain the shape of any answer:

1. **The application holds personal data.** Donors have names, national ID numbers
   (`src/main/kotlin/com/example/donations/donor/Donor.kt`), addresses, and email addresses.
   Logs leave the machine in ways a database does not. Once an event format is established
   and being read, widening what it contains is cheap and narrowing it is not — which is what
   makes this decision hard to reverse and therefore ADR-worthy.
2. **Security events have the opposite requirement.** The OWASP Logging Cheat Sheet prescribes
   logging user identity and source IP for authentication successes and failures, because
   those are the fields that make brute-force and credential-stuffing detectable. A blanket
   "no identifiers in logs" rule would gut the security value of the very statements that
   already exist.
3. **There is no budget for observability infrastructure.** The deployment is on Render's free
   tier (ADR-002). Any design that assumes a collector, a hosted backend, or a paid plan is
   not viable, and the format chosen today must not foreclose adopting one later.

PRD-8 (issue #47) drives this decision.

## Decision

Application events are a **closed, enumerable set of typed events**, named with the
**OWASP Application Logging Vocabulary**, emitted through a single component, and carrying a
per-request correlation id in MDC.

- **Naming and levels come from OWASP**, not from us: `authn_login_success:userid` (INFO),
  `authn_login_fail:userid,locked` (WARN), `authn_login_lock:userid,reason,maxlimit` (WARN),
  `authn_password_change:userid` (INFO), `authz_fail:userid,resource`, `authz_admin:userid,event`. Domain events extend the same
  `noun_verb:field,field` grammar — `donation_create:donationId,donorId,amount`,
  `expense_create:expenseId,amount,category`. SLF4J has no CRITICAL level, so events the
  standard marks CRITICAL map to ERROR; this is a deliberate, documented deviation.
- **Login failures map to three moments, not two.** `LoginAttemptService.kt:39` locks the account
  at exactly the instant the failure count reaches `MAX_FAILURES`, so OWASP's
  `authn_login_fail_max` and `authn_login_lock` would fire together — the standard separates them
  only because a lock can have causes other than max retries, which is what its `reason` field is
  for. `authn_login_fail_max` is therefore **not implemented**. Instead `authn_login_fail` carries
  a `locked` field, distinguishing a rejected credential (`AuthService.kt:37`, `locked=false`)
  from an attempt against an already-locked account (`AuthService.kt:26-28`, `locked=true`) —
  the latter being the strongest brute-force signal available, and otherwise indistinguishable
  from a typo'd password.
- **Personal data is excluded by construction, not by convention.** Each event is a distinct
  type declaring its own fields, so donor names, national IDs, addresses, and email addresses
  cannot be emitted — there is no field to hold them. Passwords and session identifiers are
  likewise unrepresentable. A test asserts no event type declares a forbidden field, so the
  policy fails the build rather than failing code review.
- **Permitted identifiers are internal entity ids and monetary amounts.** Client IP addresses
  appear **only** in authentication events, where OWASP prescribes them.
- **Every event carries the acting operator's username** as an `actor` field, attached
  centrally by the emitter rather than declared per event. Security events additionally carry
  `userid` explicitly, per the OWASP field lists, which is why the two duplicate there.
  This is deliberate: the personal-data rule protects **donors**, who did not consent to
  appearing in logs, not **operator accounts**, whose actions this application already audits
  in `created_by`/`updated_by` (`AuditableEntity.kt:25-35`). Removing `actor` from domain
  events would mean answering "who recorded this donation?" with a database query — the exact
  thing PRD-8's success criterion exists to avoid.
- **Output format is Spring Boot's built-in structured logging, switched by profile** — the
  readable console pattern under `dev`, JSON under `prod`, configured in
  `src/main/resources/application.yaml` alongside the existing CORS and cookie-security
  profile splits. No new dependency.
- **The existing four statements are migrated, not deleted.** The three security statements are
  renamed onto the vocabulary (the lockout event gaining the `reason` field the standard
  requires); the catch-all handler emits an event **and keeps its stacktrace**.
- **Read operations emit nothing.** Only state changes and security decisions produce events.

Implementation is pending; see PRD-8 (issue #47).

## Alternatives Considered

### Free-form `log.info` at call sites (the conventional approach)
- Pros: no new abstraction; every developer already knows how; zero design cost.
- Cons: the PII rule survives only as review discipline, and one `log.info("saved donor $donor")`
  puts a national ID in production logs permanently. No enumerable vocabulary — answering
  "what does this application say?" means grepping. Names and levels drift.
- Rejected: the personal-data constraint is the whole point, and this design cannot enforce it.

### Invent our own event names
- Pros: fits the domain exactly; no mapping to an external taxonomy.
- Cons: discards a standard that already supplies names, levels, and required fields for the
  security half; anyone reading the logs later has to learn a bespoke vocabulary; loses the
  recognizability that makes security events useful to an outside reviewer.
- Rejected: OWASP costs nothing to adopt and the security events benefit most from being
  conventional. Extending its grammar to domain events keeps one query language.

### Strip usernames and IPs from security events too
- Pros: the strictest reading of "no personal data in logs"; nothing to justify under GDPR.
- Cons: contradicts explicit OWASP guidance; a brute-force attempt becomes unattributable
  noise, which removes the main reason to log auth events at all. Usernames are already stored
  in `created_by`/`updated_by` (`AuditableEntity.kt:25-35`), so they are not secret in this system.
- Rejected: the rule is about **donors**, who did not consent to appearing in logs, not about
  **operator accounts**, whose actions are already audited.

### Drop or sanitize stacktraces on unhandled exceptions
- Pros: closes the last channel through which a field value could reach a log line, since
  constraint-violation messages can embed the value that failed.
- Cons: makes post-hoc diagnosis of an unreproduced failure impossible, defeating the goal.
  A sanitizer for arbitrary exception messages is unbounded, speculative work.
- Rejected: stacktraces stay; the residual risk is accepted below.

### Emit both `authn_login_fail_max` and `authn_login_lock`
- Pros: literal fidelity to the OWASP catalogue.
- Cons: two log lines for one occurrence, permanently; a reader must learn that they always
  co-occur in this system. The alternative — splitting `LoginAttemptService` so counting and
  locking are distinct operations — means refactoring working, tested code to satisfy a logging
  taxonomy, the inversion `CLAUDE.md` §3 warns against.
- Rejected: the standard's `reason` field already covers the distinction it was drawing, and a
  `locked` field on `authn_login_fail` captures the signal that mapping actually lost.

### OpenTelemetry, metrics, and a hosted backend
- Pros: the complete observability story — traces, metrics, dashboards, alerting.
- Cons: requires a collector and a backend, neither of which fits the free-tier deployment;
  and the substantial part of the work would be pipeline wiring rather than deciding what is
  worth recording.
- Rejected for now: the format chosen here is standard JSON, so adopting a platform later is a
  configuration change, not a reinstrumentation.

### `logstash-logback-encoder` with a hand-written `logback-spring.xml`
- Pros: fine-grained control over the JSON shape.
- Cons: a new dependency and an XML configuration file to own, to do what the framework
  already does.
- Rejected: Spring Boot 4 has native structured logging with per-profile switching.

## Consequences

- **The personal-data policy is enforced by the compiler and one test**, not by remembering it.
  Adding an event is adding a type; there is no path by which a donor's national ID reaches a
  log line short of deliberately declaring a field for it.
- **Operator usernames are present on every event, including domain events.** This is a
  deliberate narrowing of "no personal data in logs" to mean donor data specifically. If the
  church ever needs operator activity to be pseudonymous — a works-council agreement, or staff
  objecting to their actions being individually attributable in logs — this is the bullet that
  has to change, and the change is one line in the emitter rather than a rework of the events.
- **A sustained attack against a locked account is queryable** — filter `authn_login_fail` on
  `locked=true`. A mapping that emitted `authn_login_fail_max` instead would have left this
  invisible.
- **The vocabulary has one fewer name than the OWASP catalogue defines.** Anyone diffing our
  events against the standard will find `authn_login_fail_max` missing; this ADR is the reason.
- **The event vocabulary is discoverable in one place** — the set of event types *is* the list
  of everything the application says.
- **Adding an event requires touching the event hierarchy**, which is slightly more ceremony
  than a bare `log.info`. This is the intended trade.
- **Accepted risk: stacktraces may contain field values.** Exception messages can embed the
  data that caused them. This is bounded by logs not leaving the host — an assumption that
  **expires the moment log shipping is introduced**. Revisit this ADR then, not later.
- **Client IP addresses are personal data under GDPR.** Retaining them in auth events is
  defensible on legitimate-interest grounds for security, but no retention period is defined
  because, with no log shipping, there is no retention mechanism to configure. This is a known
  gap, not an oversight, and becomes a real obligation if logs are ever shipped or persisted.
- **Console output differs between `dev` and `prod`.** A formatting-specific defect can exist in
  one profile and not the other; the format is asserted by a profile-sensitive test, following
  the pattern in `src/test/kotlin/com/example/donations/DeployConfigTest.kt`.
- **Entity ids are sequential `Long` surrogate keys**, not UUIDs, so events carry values like
  `donorId=42`. Pseudonymous and policy-compliant, but sequential ids leak record counts to
  anyone reading the logs. Acceptable for a single-tenant deployment.
- **Questions about aggregate behaviour remain unanswerable.** No metrics means "how slow is
  this endpoint" and "how often does this fail" cannot be answered from this work. Revisit
  trigger: if that question is asked twice in production, metrics are the next ADR.
