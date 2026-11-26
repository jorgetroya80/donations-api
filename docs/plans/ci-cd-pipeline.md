# Plan: CI/CD Pipeline with GitHub Actions and Automated Releases

> Source PRD: GitHub issue jorgetroya80/donations-api#3

## Architectural decisions

Durable decisions that apply across all phases:

- **Workflow files**: `.github/workflows/ci.yml` and `.github/workflows/release.yml`
- **CI runner**: `ubuntu-latest` with Java 24 (Temurin) via `actions/setup-java`
- **Test command**: `./gradlew check` (all tests, Testcontainers Postgres auto-spins)
- **Versioning**: Conventional Commits + `google-github-actions/release-please-action`, starting at `1.0.0`
- **Docker image**: `[dockerUserName]/donations-api:<version>` — only on release
- **Secrets**: `DOCKERHUB_USERNAME` + `DOCKERHUB_TOKEN` as GitHub repo secrets
- **Project version in `build.gradle`**: currently `0.0.1-SNAPSHOT` — release-please will manage this

---

## Phase 1: CI workflow — build & test on PR and merge

**User stories**: #1, #2, #10

### What to build

Create `ci.yml` workflow triggered on `pull_request` and `push` to `main`. Sets up Java 24 Temurin, caches Gradle dependencies, runs `./gradlew check`. GitHub Actions runners have Docker pre-installed, so Testcontainers works out of the box.

### Acceptance criteria

- [ ] `./gradlew check` runs on every PR push
- [ ] `./gradlew check` runs on every push to `main`
- [ ] Tests pass using Testcontainers PostgreSQL (no external DB config)
- [ ] Gradle dependencies are cached between runs
- [ ] CI status check appears on PRs

---

## Phase 2: release-please — automated versioning & changelog

**User stories**: #4, #5, #6, #8

### What to build

Add `release-please` configuration to `release.yml`. On push to `main`, release-please analyzes conventional commits and opens/updates a Release PR with changelog and version bump. Merging that PR creates a git tag and GitHub Release. Configure initial version as `1.0.0`. Project type is `simple` (no package manager to publish to).

### Acceptance criteria

- [ ] release-please action runs on push to `main`
- [ ] Release PR is auto-created with changelog from conventional commits
- [ ] Merging Release PR creates git tag (`v1.0.0`) and GitHub Release
- [ ] Semantic versioning rules apply: `feat:` = minor, `fix:` = patch, `BREAKING CHANGE` = major
- [ ] Initial release version is `1.0.0`

---

## Phase 3: Docker publish on release

**User stories**: #7, #9, #11

### What to build

Extend `release.yml` with a job that triggers when release-please creates a release. Logs in to Docker Hub using repo secrets, builds Docker image using existing multi-stage Dockerfile, tags with release version, and pushes to Docker Hub. No image push on regular merges — only on release.

### Acceptance criteria

- [ ] Docker image built only when release-please creates a release
- [ ] Image tagged with version number (e.g., `1.0.0`)
- [ ] Image pushed to `[dockerUserName]/donations-api` on Docker Hub
- [ ] Docker Hub auth uses `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` secrets
- [ ] No Docker image pushed on regular PR merges

---

## Phase 4: Branch protection

**User stories**: #3

### What to build

Manual configuration in GitHub repo settings. Enable branch protection rule on `main` requiring the CI status check (from Phase 1 workflow) to pass before merging.

### Acceptance criteria

- [ ] Branch protection enabled on `main`
- [ ] CI status check required to pass before merge
- [ ] PRs with failing CI cannot be merged
