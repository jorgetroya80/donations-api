# Todo: clean-code refactors (see clean-code-refactor.md)

- [x] T1 `getOrThrow` extension + adopt in 5 services + expression getters
- [x] T2 Collapse `createDonation` (needs T1)
- [ ] **Checkpoint 1:** `./gradlew test` green, review with Jorge
- [x] T3 Shared problem-JSON writer (SecurityConfig + PasswordChangeRequiredFilter)
- [ ] T4 `AuthService.login` split + `ROLE_PREFIX`
- [ ] T5 `ReportService.orZero()`
- [ ] **Checkpoint 2:** full `./gradlew test`, diff review, Jorge commits

Skipped (decided): summary merge, typed projections, patch-block abstraction, JSESSIONID const, `!!` removal.
