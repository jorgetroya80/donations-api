# PRD-8: Structured Domain and Security Event Logging

## Problem Statement

The donations API is deployed and running in production, and I have almost no visibility into what it does. Three `log` statements exist in the entire codebase — two in the login path, one catch-all for unexpected errors. If a donation import fails tonight, or a user reports "I saved it but it's not there," I have no way to reconstruct what happened. My only options are to query the database after the fact and guess, or add temporary print statements and wait for the problem to happen again.

Nothing has broken yet. This is preemptive: I want the ability to answer "what happened in this request, and why did it fail?" before I actually need it, and I want to learn to do observability deliberately rather than by scattering log lines wherever they seem useful.

The hard constraint is that this application holds personal data for donors of a church — names, national ID numbers (DNI/NIE), addresses, email addresses. Those must never appear in a log line. Logs leave the machine in ways databases do not, and once an event format is established and being read, widening what it contains is easy while narrowing it is not.

## Solution

A deliberate, closed set of application events — not general-purpose logging — emitted from the service layer, each with a standardized name, a defined level, and a fixed set of non-identifying fields.

Event names follow the **OWASP Application Logging Vocabulary Cheat Sheet** for security events (`authn_login_fail:userid`, `authz_fail:userid,resource`), and extend the same `noun_verb:fields` grammar to domain events (`donation_create:donationId,donorId,amount`). One grammar means one way to search.

Every event carries a request correlation id, so all events emitted during a single HTTP request can be pulled together as a unit. This is what makes post-hoc debugging of one operation possible, which is the point of the whole exercise.

Output is human-readable in the console during development and structured JSON in production, switched by profile. This costs nothing today and means that if the project ever ships logs to a platform like Grafana Loki, the format is already correct and no instrumentation changes.

Personal data is excluded by construction rather than by convention: each event is a distinct type whose fields are declared up front, so there is no field available to put a donor's national ID or address into. Internal entity ids and monetary amounts are permitted. Usernames and client IP addresses appear only in authentication and authorization events, where OWASP explicitly prescribes them as the data that makes brute-force and credential-stuffing attempts detectable.

## User Stories

1. As the operator of the donations API, I want every HTTP request to be assigned a correlation id, so that I can gather all events from one request even when several are interleaved.
2. As the operator, I want the correlation id to be cleared when the request ends, so that events from one request never carry the id of another.
3. As the operator, I want the authenticated username attached to events within a request, so that I can tell which staff member performed an action without querying the database.
4. As the operator, I want a `donation_create` event recording the donation id, donor id, and amount, so that I can confirm a donation was actually written when someone reports it missing.
5. As the operator, I want a `donation_update` event recording the donation id, so that I can see that a record was modified and when.
6. As the operator, I want a `donor_create` event recording the new donor id, so that I can trace where a donor record came from.
7. As the operator, I want a `donor_update` event recording the donor id, so that changes to donor records are attributable after the fact.
8. As the operator, I want an `expense_create` event recording the expense id, amount, and category, so that expense entry is as traceable as donation entry.
9. As the operator, I want an `expense_update` event recording the expense id, so that expense modifications are visible.
10. As the operator, I want read operations — lists, single fetches, and reports — to emit no events at all, so that the log stays signal and does not become a request firehose.
11. As the operator, I want an `error_unexpected` event carrying the correlation id and exception type whenever an unhandled exception is converted to a 500 response, so that I can connect a user's report of a failure to the event stream.
12. As the operator, I want the full stacktrace retained on unexpected errors, so that I can diagnose a failure without needing to reproduce it.
13. As a security-conscious operator, I want successful logins recorded as `authn_login_success` at INFO with the user id, so that I have a baseline of normal access.
14. As a security-conscious operator, I want failed logins recorded as `authn_login_fail` at WARN with the user id, so that credential-guessing attempts are visible.
15. As a security-conscious operator, I want the lockout threshold being reached recorded as `authn_login_fail_max` at WARN with the user id and the configured limit, so that I can distinguish a forgetful user from a sustained attack.
16. As a security-conscious operator, I want account lockouts recorded as `authn_login_lock` at WARN with the user id and a lock reason, so that the cause of a lockout is explicit rather than inferred.
17. As a security-conscious operator, I want password changes recorded as `authn_password_change` at INFO with the user id, so that credential changes are attributable.
18. As a security-conscious operator, I want failed password changes recorded at ERROR with the user id, so that attempts to change another account's credentials surface loudly.
19. As a security-conscious operator, I want authorization failures recorded as `authz_fail` at ERROR with the user id and the resource, so that privilege-probing is visible.
20. As a security-conscious operator, I want administrative user-management actions recorded as `authz_admin` with the acting user and a description, so that role and account changes are traceable.
21. As a security-conscious operator, I want the client IP address on authentication events, so that repeated failures can be correlated to a source.
22. As a data controller for a church, I want donor names, national ID numbers, addresses, and email addresses to be impossible to emit in any event, so that a log file is never a secondary store of personal data.
23. As a data controller, I want passwords and session identifiers never logged in any form, so that logs cannot be used to impersonate a user.
24. As a developer, I want events to be a closed, enumerable set rather than free-form log calls, so that I can see the complete vocabulary of what the application says in one place.
25. As a developer, I want adding a new event to be a local change that does not modify the emitting interface, so that the event vocabulary can grow without churn.
26. As a developer working locally, I want console output to stay human-readable, so that running the app does not mean reading JSON.
27. As a developer, I want production output to be structured JSON, so that fields are queryable rather than parsed out of message strings.
28. As a developer, I want the format to be switched by profile configuration rather than by code, so that changing it is not a code change.
29. As a developer, I want no new logging dependency added to the build, so that the change does not expand the dependency surface.
30. As a developer, I want the event vocabulary and its levels covered by tests, so that a rename or a level change is caught rather than silently shipped.
31. As a developer, I want a test asserting that no event type exposes a forbidden field, so that the PII rule is enforced automatically rather than remembered during review.
32. As a developer, I want the correlation filter tested for both setting and clearing the context, so that leakage between requests is caught.
33. As a future maintainer, I want the decision to exclude personal data recorded as an ADR, so that the reasoning survives past the people who made it.
34. As a future maintainer, I want the accepted risk of stacktraces potentially containing field values written down, so that it is a known tradeoff rather than an oversight.

## Implementation Decisions

**Event vocabulary follows OWASP.** Security event names and levels are taken from the OWASP Application Logging Vocabulary Cheat Sheet rather than invented. Domain events extend the same `noun_verb:field,field` grammar. Where OWASP specifies a CRITICAL level, SLF4J's absence of that level means the event maps to ERROR; this is a documented deviation.

**Events are a sealed type hierarchy.** Each event is a distinct type declaring its own name, level, and fields. This is the core design decision and it does the PII enforcement: a donor's national ID cannot be logged because no event type has a field to hold it. Adding an event means adding a type, not changing an interface. This module is pure Kotlin with no framework dependency and is testable without a Spring context.

**A single emitter is the only holder of a logger.** One component accepts an event and writes it through the SLF4J fluent key-value API at the event's declared level. Its interface is a single method and is expected never to change. No other production code obtains a logger directly.

**Correlation is carried in MDC, populated by a servlet filter.** The filter generates a request id, attaches it along with the authenticated username where available, and clears the context when the request completes. Spring Boot's structured logging includes MDC contents in the JSON output automatically, so no per-event plumbing is required.

**Output format is Spring Boot's built-in structured logging, switched by profile.** No new dependency. The `dev` profile leaves structured output disabled and keeps the default readable console pattern; the `prod` profile enables a JSON format. This is the mechanism that keeps a future move to a log platform a configuration change rather than a reinstrumentation.

**The three existing log statements are migrated, not removed.** The two login-path statements and the lockout statement already log approximately the right things; they are renamed onto the OWASP vocabulary and moved behind the emitter. The lockout event gains the `reason` field the standard requires.

**Unhandled exceptions emit an event and retain the stacktrace.** The global exception handler emits an event carrying the correlation id and exception type, and continues to log the stacktrace. Exception messages can carry field values, which is an accepted risk: removing stacktraces makes post-hoc diagnosis impossible, and sanitizing arbitrary exception messages is speculative complexity. Logs are not shipped off-host, which bounds the exposure.

**Reads emit nothing.** Lists, single-entity fetches, and report generation produce no events. Only state changes and security decisions do.

**Usernames and IPs are scoped to security events.** They appear in authentication and authorization events because OWASP prescribes them there. They are not attached to domain events beyond the MDC actor field.

**An ADR records the personal-data policy.** Per the repository's decision-record process, the exclusion policy, the OWASP adoption, and the stacktrace risk acceptance are recorded as an ADR, since widening what events contain later is easy and narrowing it is not.

## Testing Decisions

A good test here asserts externally observable behavior — the name, level, and field set that appear in output — and not how the emitter is wired or which internal method a service called. Asserting "service X called the logger" would couple tests to implementation and is explicitly not done.

**Tested:**

- **The event hierarchy and emitter.** Each event type produces the expected name, the expected level, and exactly the expected fields. A dedicated test asserts that no event type in the hierarchy declares a forbidden field — national id, name, address, email, password, or session identifier — so the PII constraint fails the build rather than failing review. Because the hierarchy is framework-free, these run as plain unit tests with no Spring context and no container.
- **The correlation filter.** A request id is present during request handling and absent afterwards, so context cannot leak between requests.

**Not tested:** the call sites. Whether `DonationService` emits on create is implementation detail; the value is in the vocabulary being correct and unable to carry personal data.

**Prior art:** `LoginAttemptServiceTest` is the model for the context-free unit tests — it exercises a service with an injected `Clock` and no Spring context. `DeployConfigTest` is the model for any profile-sensitive configuration assertion, using `@ActiveProfiles` with `@SpringBootTest`. The existing integration tests share the Testcontainers PostgreSQL setup via `TestcontainersConfiguration`, which the new tests do not need.

## Out of Scope

- **Metrics.** No Micrometer counters, timers, or gauges. Actuator stays as it is, serving the liveness probe.
- **Distributed tracing.** No OpenTelemetry SDK, no spans, no collector. The correlation id is an MDC value, not a trace context.
- **Dashboards and alerting.** Nothing is visualized and nothing pages anyone.
- **Log shipping.** Events go to stdout and are read wherever the app runs. No Loki, no Elasticsearch, no vendor. The format must not *block* a future move to a platform, but making that move is not part of this work.
- **Paid infrastructure.** Nothing that requires a plan upgrade on Render or a hosted observability vendor.
- **Log retention policy.** IP addresses in authentication events are personal data under GDPR and a retention period is a real question, but with no log shipping there is no retention mechanism to configure. Deferred.
- **Auditing as a product feature.** The `created_by`/`updated_by` audit columns already answer "who last touched this record" from the database. Events are for debugging and security, not a user-facing audit trail.
- **Instrumenting reads.** Deliberately excluded, per the design.

## Further Notes

Entity identifiers in this application are sequential `Long` surrogate keys rather than UUIDs, so events will carry values like `donorId=42`. These are pseudonymous and satisfy the policy, though sequential ids do leak record counts to anyone reading the logs. Acceptable for a single-tenant church deployment.

The `dev` and `prod` profiles already exist and already differ in CORS, cookie security, and springdoc exposure, so adding a format difference follows an established pattern rather than introducing one.

The success criterion for this work is concrete and worth restating: given a report that something failed, it should be possible to find the correlation id, pull every event from that request, and understand what happened — without querying the database and without adding temporary logging and waiting for a recurrence. If that is not achievable when the work is done, the event set is wrong regardless of how clean the implementation is.

## References

- [ADR-005: OWASP-vocabulary event logging with donor data excluded by construction](decisions/ADR-005-event-logging-personal-data-policy.md) — the decision record driven by this PRD
- [OWASP Application Logging Vocabulary Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Vocabulary_Cheat_Sheet.html) — event names, levels, and required fields
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) — what to log and what to exclude
- [Spring Boot Logging Reference](https://docs.spring.io/spring-boot/reference/features/logging.html) — built-in structured logging formats and MDC handling
