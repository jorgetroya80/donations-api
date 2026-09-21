# Implementation Plan: Kotlin 2.2.21 → 2.4.20

## Overview

Move the project from Kotlin 2.2.21 to 2.4.20 (verified latest on the Gradle plugin portal,
2026-09-21). Three plugins carry the version — `kotlin.jvm`, `kotlin.plugin.spring`,
`kotlin.plugin.jpa` (`build.gradle:2-4`) — but the bump is not a one-line change, because Spring
Boot's BOM pins the *libraries* independently of the *compiler*.

No application behaviour should change. Success is: same tests, same build output, newer compiler.

## Architecture Decisions

- **The BOM pin must be overridden explicitly.** `spring-boot-dependencies-4.0.5.pom` sets
  `<kotlin.version>2.2.21</kotlin.version>`, and `io.spring.dependency-management` applies it to
  `kotlin-stdlib`, `kotlin-reflect` and `kotlin-test-junit5` (confirmed: `runtimeClasspath`
  currently resolves `kotlin-stdlib:2.2.21`). Bumping only the plugins leaves a 2.4.20 compiler
  compiling against a 2.2.21 standard library. The fix is `ext['kotlin.version'] = '2.4.20'` in
  `build.gradle`, which `io.spring.dependency-management` reads.
- **Language level stays at 2.2** (Jorge, 2026-09-21). The compiler moves to 2.4;
  `languageVersion` and `apiVersion` are pinned to `KOTLIN_2_2`, so this change is a toolchain
  upgrade only and the language-level move is a separate, separately-revertable piece of work.
  Raising those two lines — and dropping the then-redundant `-Xannotation-default-target` flag — is
  the whole of that later change.
- **`freeCompilerArgs` was the likeliest breakage, and it held.** `-Xannotation-default-target=param-property`
  is a 2.2-era migration flag; 2.4 kept it but made the behaviour default, so at language level 2.4
  it warned as redundant once per compile task. With the language level pinned to 2.2 the flag is
  meaningful again and the warnings are gone. It becomes redundant — and should be deleted — only
  when the language level moves.
- **No new dependencies, no source changes expected.** If the upgrade requires editing application
  code, that is a finding to report, not something to absorb quietly into this work.

## Dependency Graph

```
build.gradle: plugin versions + ext['kotlin.version']
    │
    ├── compileKotlin / compileTestKotlin        ← Task 1 (fails fast here)
    │       │
    │       ├── test suite incl. Testcontainers  ← Task 2
    │       └── AOT tasks (processAot, compileAotKotlin)
    │
    ├── language level decision                  ← Task 3 (informed by Task 2's output)
    │
    └── docs stating "Kotlin 2.2"                ← Task 4 (independent, parallelizable)

CI (./gradlew check) and the optional native image   ← Task 5
```

Nothing in `src/` depends on the Kotlin version at source level, so there is no vertical slice to
cut here — the honest ordering is by risk gate: prove the compiler accepts the build, then prove the
tests pass, then tidy.

## Task List

### Phase 1: Make it compile

- [x] Task 1: Bump the three plugins and override the BOM's `kotlin.version`

### Checkpoint: Compiles — passed

- [x] `./gradlew compileKotlin compileTestKotlin` clean
- [x] `kotlin-stdlib`, `kotlin-reflect` and `kotlin-test-junit5` resolve to 2.4.20 (2.2.21 before
      the override, so the BOM pin was real)
- [x] No unrecognised-compiler-argument error — the flag is accepted, only redundant

### Phase 2: Prove nothing changed

- [x] Task 2: Full build green, including Testcontainers and AOT
- [x] Task 3: Pin the language level to 2.2

### Checkpoint: Verified — passed

- [x] `./gradlew clean build` green: 208 tests / 21 classes both before and after the bump,
      baseline measured on this branch rather than copied
- [x] No compiler warnings to triage — the language-level pin left the build clean
- [x] No application source changed

### Phase 3: Tidy

- [ ] Task 4: Update the stack version in docs
- [ ] Task 5: Confirm CI, and decide on the native image check

### Checkpoint: Complete

- [ ] CI green on the branch
- [ ] No stale "Kotlin 2.2" in current docs
- [ ] Report to Jorge

---

## Task 1: Bump the three plugins and override the BOM's `kotlin.version`

**Description:** Set all three Kotlin plugins to 2.4.20 in `build.gradle:2-4`, and add
`ext['kotlin.version'] = '2.4.20'` so `io.spring.dependency-management` stops pinning the Kotlin
libraries to Boot's 2.2.21. Compile only — do not run the suite yet. This task exists to fail fast
on the two things that can break at configuration/compile time: the stdlib/compiler mismatch and the
`-Xannotation-default-target` flag.

**Acceptance criteria:**
- [ ] All three plugins read `2.4.20`; no other version in the file changes.
- [ ] `kotlin-stdlib` and `kotlin-reflect` resolve to 2.4.20, not 2.2.21.
- [ ] Main and test sources compile.

**Verification:**
- [ ] `./gradlew compileKotlin compileTestKotlin`
- [ ] `./gradlew -q dependencies --configuration runtimeClasspath | grep kotlin-` shows 2.4.20
- [ ] Manual check: read the compiler output for warnings, don't just check the exit code — a
      stdlib mismatch is a *warning*, not an error, and would otherwise pass silently.

**Dependencies:** None

**Files likely touched:**
- `build.gradle`

**Estimated scope:** XS (1 file)

---

## Task 2: Full build green, including Testcontainers and AOT

**Description:** Run the whole suite and the AOT tasks against the new compiler. `./gradlew build`
already exercises `compileAotKotlin` / `compileAotTestKotlin` in this project, so no extra step is
needed to cover AOT. Triage every *new* warning: fix it, or state why it is accepted. Do not fix
pre-existing warnings — that is unrelated cleanup (CLAUDE.md §3).

**Acceptance criteria:**
- [ ] `./gradlew clean build` green with the same test count as the baseline, where the baseline is
      measured on **this** branch by stashing the `build.gradle` change — not copied from anywhere.
- [ ] No application source file changed. If the compiler demands a source change, stop and report.
- [ ] Each new warning is either resolved or listed with a reason.

**Verification:**
- [ ] Tests pass: `./gradlew clean build` — `clean` is not optional, see the note below.
- [ ] Manual check: diff the test count and the warning list against the pre-bump run.
- [ ] A whole test class failing at context load is the known Testcontainers flake — re-run once
      before investigating.

**Counting the tests correctly.** Two traps, both found in review rather than in the build:

1. **The baseline cannot be borrowed from another branch.** This branch is cut from `main`, which
   does not contain the balance-timeseries work; `242` came from `feat/balance-timeseries` and is
   meaningless here. Measure the baseline by stashing the `build.gradle` change on this branch,
   running the suite, and recording the number.
2. **`build/test-results/` keeps orphans.** Gradle rewrites the XML for classes that run but never
   deletes results for classes that no longer exist. That directory currently holds 24 files from
   2026-08-27, including `BalancePeriodsTest.xml` (19 tests) and `ValidationClockZoneTest.xml` (2)
   for classes absent from this branch. Summing the directory would silently add ~21 phantom tests
   to both sides of the comparison and could report a match built from a branch that is not even
   checked out. Use `clean`, or count only classes present in `src/test`.

**Dependencies:** Task 1

**Files likely touched:**
- none expected; `build.gradle` only if a warning needs a compiler flag

**Estimated scope:** XS (0–1 files)

---

## Task 3: Pin the language level to 2.2 — DONE

**Description:** Decided by Jorge before Task 2 rather than after it, which is why it is applied
first: pinning now means one verification run covers both changes instead of two. `languageVersion`
and `apiVersion` are set to `KOTLIN_2_2` in `kotlin { compilerOptions { … } }`, with a comment
stating that raising them is the whole of the deferred language-level move.

**Acceptance criteria:**
- [x] Both pinned to `KOTLIN_2_2`, with a comment saying what the later move consists of.
- [x] The `-Xannotation-default-target` redundancy warnings are gone (the flag is meaningful again
      at language level 2.2).

**Verification:**
- [x] `./gradlew compileKotlin compileTestKotlin --rerun-tasks` — clean, no warnings. `--rerun-tasks`
      because the first run finished in 3s and a silent UP-TO-DATE would have looked like success.

**Dependencies:** none as it turned out — the decision came from Jorge, not from Task 2's output

**Files likely touched:**
- `build.gradle` (only if pinning)

**Estimated scope:** XS (0–1 files)

---

## Task 4: Update the stack version in docs

**Description:** Three current documents state "Kotlin 2.2": `README.md:7`, `CLAUDE.md:77`,
`docs/architecture.md:3` and `:68`. Update those. Leave `docs/donor-search.md:41` alone — it is a
historical plan describing the stack as it was when that work shipped, and rewriting past plans to
match the present erases the record (Decision 4).

**Acceptance criteria:**
- [ ] The three current documents say Kotlin 2.4.
- [ ] Historical plan documents under `plans/` and `docs/*-spec.md` are untouched.

**Verification:**
- [ ] `grep -rn "Kotlin 2\.2" --include="*.md" .` returns only historical documents.

**Dependencies:** None (parallelizable with Tasks 1–3; sequence it last so it does not have to be
redone if the upgrade is abandoned)

**Files likely touched:**
- `README.md`, `CLAUDE.md`, `docs/architecture.md`

**Estimated scope:** XS (3 files)

---

## Task 5: Confirm CI, and decide on the native image check

**Description:** CI runs `./gradlew check` on Java 24 with no Kotlin version of its own
(`.github/workflows/ci.yml:18-30`), so no workflow edit should be needed — confirm that rather than
assume it. Separately: the GraalVM plugin (`org.graalvm.buildtools.native:0.11.5`) is configured but
`nativeCompile` runs in no workflow, so native is currently unverified on every commit, not just
this one. Decide whether this upgrade is the moment to run it once locally.

**Acceptance criteria:**
- [ ] CI is green on the branch with no workflow change, or the needed change is identified.
- [ ] A decision on the native check is recorded, either way.

**Verification:**
- [ ] CI run on the pushed branch
- [ ] Manual check (optional): `./gradlew nativeCompile` locally — slow, and it proves something CI
      never checks, so treat a failure as pre-existing until shown otherwise.

**Dependencies:** Task 2

**Files likely touched:**
- none expected

**Estimated scope:** XS (0 files)

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Boot 4.0.5's BOM keeps Kotlin libs at 2.2.21 while the compiler is 2.4.20 | High — silent, warning-only | `ext['kotlin.version']` override in Task 1, then *read* the resolved graph rather than trusting the build's exit code |
| `-Xannotation-default-target=param-property` removed or defaulted in 2.4 | Medium — build fails at compile | Task 1 is a compile-only probe precisely to surface this before anything else moves |
| Spring Boot 4.0.5 not officially tested against Kotlin 2.4 | Medium | The suite is the evidence — full Testcontainers integration across every layer. If something subtle breaks, it shows there |
| `kotlin.plugin.spring` / `.jpa` behaviour change (all-open, no-arg) | Medium | JPA entities and `@Configuration` classes are exercised by the integration tests — a regression in either plugin fails loudly at context load |
| Testcontainers flake read as an upgrade regression | Low — wasted investigation | Whole class failing at context load → re-run once before investigating |
| Native image regression | Low | Not in CI today, so it is not a regression *this* change introduces; Task 5 decides whether to look |
| **PR #52 (`feat/balance-timeseries`) is still open** | Low — coordination, not correctness | Decision 1: left alone. Whichever merges second rebases across two additive `build.gradle` lines — mechanical, not a per-file replay |

## Decisions (Jorge, 2026-09-21)

1. **PR #52 is left alone.** This upgrade proceeds on its own branch and does not wait for it. The
   consequence, accepted: whichever merges second rebases across a `build.gradle` change — and
   since `ext['kotlin.version']` and the language-level pin are additive lines, that conflict is
   small and mechanical rather than a per-file replay.
2. **No ADR.** A Kotlin minor bump reverts in one line; the reasoning lives in this plan and in the
   commit message.
3. **Language level pinned to 2.2**, language-level move deferred — see Task 3, applied.
4. **Historical docs left as they are.** `docs/donor-search.md` and the `plans/` documents keep
   saying "Kotlin 2.2"; they describe what was true when written. Only the four current references
   in Task 4 change.

## Definition of Done

- `./gradlew clean build` green, same test count as the baseline measured on this branch.
- No application source changed.
- CI green on the branch.
- Current docs state the new version; historical documents untouched.
- Report to Jorge — do not commit unless he says so.
