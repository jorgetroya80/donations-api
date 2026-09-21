# TODO — Kotlin 2.2.21 → 2.4.20

Plan: [kotlin-2.4.20.md](kotlin-2.4.20.md)

Nothing blocked. Decisions (Jorge, 2026-09-21): PR #52 is left alone and this branch does not wait
for it; no ADR; language level pinned to 2.2 with the language-level move deferred; historical docs
keep saying "Kotlin 2.2".

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

- [ ] **Task 2 — Full build incl. Testcontainers and AOT** (XS · deps: 1)
  - [ ] Baseline first: `git stash` the `build.gradle` change, `./gradlew clean build`, record the
        count, `git stash pop`. Do **not** reuse 242 — that came from `feat/balance-timeseries`,
        which this branch does not contain
  - [ ] `./gradlew clean build` green, same count as that baseline
  - [ ] ⚠ `clean` is required: `build/test-results/` keeps XML for deleted classes (24 stale files
        from 2026-08-27 right now, incl. 21 tests from classes absent here), so summing the
        directory counts phantoms on both sides of the comparison
  - [ ] No application source file changed — if the compiler demands one, stop and report
  - [ ] New warnings triaged: fixed, or listed with a reason
  - [ ] Whole class failing at context load = known Testcontainers flake; re-run once first

- [x] **Task 3 — Pin the language level to 2.2** (XS · applied before Task 2, so one verification
      run covers both changes)
  - [x] `languageVersion` and `apiVersion` = `KOTLIN_2_2`, with a comment saying that raising them
        is the whole of the deferred language-level move
  - [x] `-Xannotation-default-target=param-property` kept — meaningful again at 2.2, and the
        redundancy warnings are gone. Delete it when the language level moves
  - [x] Verify: `./gradlew compileKotlin compileTestKotlin --rerun-tasks` clean, no warnings

### Checkpoint: Verified

- [ ] Build green, warnings triaged, **review with Jorge before the docs sweep**

## Phase 3: Tidy

- [ ] **Task 4 — Docs** (XS · deps: none, sequenced last)
  - [ ] `README.md:7`, `CLAUDE.md:77`, `docs/architecture.md:3` and `:68`
  - [ ] Leave historical plans alone (`docs/donor-search.md:41`, `plans/*`)
  - [ ] Verify: `grep -rn "Kotlin 2\.2" --include="*.md" .` returns only historical docs

- [ ] **Task 5 — CI + native decision** (XS · deps: 2)
  - [ ] CI green with no workflow change (it pins Java 24 only, no Kotlin version)
  - [ ] Decide whether to run `./gradlew nativeCompile` once — it is in no workflow today

### Checkpoint: Complete

- [ ] CI green, docs current, report to Jorge — **do not commit unless he says so**

## Deferred, not forgotten

- **The language-level move to 2.4**: raise the two `KOTLIN_2_2` lines and delete the
  `-Xannotation-default-target` flag. Its own change, its own verification.
- **PR #52's rebase** across this `build.gradle` change, whenever the second of the two merges.
