# TODO — Kotlin 2.2.21 → 2.4.20

Plan: [kotlin-2.4.20.md](kotlin-2.4.20.md)

Nothing blocked. Decisions (Jorge, 2026-09-21): PR #52 is left alone and this branch does not wait
for it; no ADR; **language level moves to 2.4 along with the compiler** (reversing an earlier pin —
the point of the upgrade is to learn whether the codebase runs on 2.4); historical docs keep saying
"Kotlin 2.2".

## Phase 1: Make it compile

- [x] **Task 1 — Bump plugins + override the BOM pin** (XS · deps: none)
  - [x] `kotlin.jvm`, `kotlin.plugin.spring`, `kotlin.plugin.jpa` → `2.4.20`
  - [x] `ext['kotlin.version'] = '2.4.20'` so dependency-management stops pinning 2.2.21
  - [x] Verify: `./gradlew compileKotlin compileTestKotlin` — BUILD SUCCESSFUL
  - [x] Verify: `kotlin-stdlib`, `kotlin-reflect` and `kotlin-test-junit5` all resolve to 2.4.20
        (they were 2.2.21 before the override, so the pin was real, not theoretical)
  - [x] ⚠ Flag not dropped, but now redundant — the compiler says:
        `w: The argument '-Xannotation-default-target=param-property' is redundant for the current
        language version 2.4.` Two warnings, one per compile task. **Removing it is a semantic
        no-op** (2.4's default already is `param-property`), but it belongs to Task 3's language
        decision, not here. Carried forward.

### Checkpoint: Compiles

- [x] Both compile tasks clean, Kotlin libs at 2.4.20, no unrecognised-argument error

## Phase 2: Prove nothing changed

- [x] **Task 2 — Full build incl. Testcontainers and AOT** (XS · deps: 1)
  - [x] Baseline measured on this branch by stashing the change: **208 tests, 21 classes, 0
        failures** at 2.2.21. The plan's original 242 would have been wrong by 34 — those tests
        live on `feat/balance-timeseries`, which this branch does not contain
  - [x] Post-bump `./gradlew clean build`: **208 tests, 21 classes, 0 failures** — identical
  - [x] `clean` did its job: the pre-`clean` directory held 24 classes, three of them orphans from
        2026-08-27 that no longer exist here
  - [x] No application source file changed (`git status --porcelain src/` empty)
  - [x] No compiler warnings at all — the language-level pin removed the two from Task 1

- [x] **Task 3 — Adopt language level 2.4** (XS · pinned to 2.2 first, then unpinned on Jorge's
      call: a pin would have left the actual question — does this codebase work on 2.4? — untested)
  - [x] No `languageVersion` / `apiVersion` pin; compiler defaults apply
  - [x] `-Xannotation-default-target=param-property` deleted, not silenced: 2.4 applies that
        annotation placement by default
  - [x] Verify: `./gradlew clean build` at 2.4 → 208 tests / 21 classes, 0 failures, no warnings,
        no source change. Annotation placement is covered for real, since the suite asserts
        validation rejections, JPA persistence and Jackson serialization

### Checkpoint: Verified

- [ ] Build green, warnings triaged, **review with Jorge before the docs sweep**

## Phase 3: Tidy

- [x] **Task 4 — Docs** (XS · deps: none, sequenced last)
  - [x] `README.md:7`, `CLAUDE.md:77`, `docs/architecture.md:3` and `:68` now say Kotlin 2.4
  - [x] `CLAUDE.md` also states the language level, since a future reader compiling at 2.2 on a 2.4
        toolchain would otherwise find the flag list confusing
  - [x] Historical docs left alone per Decision 4: `docs/donor-search.md` still says 2.2
  - [x] Verify: the only remaining "Kotlin 2.2" mentions are that historical plan and these two
        planning documents, which describe the upgrade itself

- [x] **Task 5 — CI + native decision** (XS · deps: 2)
  - [x] No workflow change needed: `ci.yml` pins Java 24 and sets up Gradle, with no Kotlin version
        of its own, so the plugin bump carries the compiler
  - [x] **CI green on PR #53** (`./gradlew check`, run 35634388239, success). Note for next time:
        `ci.yml` triggers only on `push: [main]` and `pull_request: [main]` (lines 3-7), so a pushed
        branch with no PR runs nothing at all — opening the PR is what made CI execute
  - [x] Unrelated annotation on the run, worth knowing but not acting on here: `ubuntu-latest`
        migrates to Ubuntu 26 from 2026-10-19
  - [x] Native: **skipped, deliberately.** `nativeCompile` runs in no workflow, so native is
        unverified on every commit, not just this one. Running it here would take ~10 minutes to
        test something this change did not touch, and a failure would most likely be pre-existing
        and indistinguishable from an upgrade regression. Worth its own ticket: either wire it into
        CI or drop the plugin

### Checkpoint: Complete — passed

- [x] CI green on [PR #53](https://github.com/jorgetroya80/donations-api/pull/53), docs current,
      all five tasks committed (Jorge approved per-task commits for this run)

## Deferred, not forgotten

- **PR #52's rebase** across this `build.gradle` change, whenever the second of the two merges.
- **Native image** is unverified project-wide: `nativeCompile` runs in no workflow. Wire it into CI
  or drop the `org.graalvm.buildtools.native` plugin.
- **Spring Boot's own Kotlin expectations** — Boot 4.0.5 ships a BOM pinned to Kotlin 2.2.21, and
  we override it. Worth checking, when a Boot upgrade next comes up, whether it wants anything from
  a 2.4 toolchain that this override is papering over.
