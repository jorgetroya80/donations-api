# PRD: CI/CD Pipeline with GitHub Actions and Automated Releases

## Problem Statement

The donations API has no continuous integration or delivery pipeline. Code merged to `main` is not automatically validated, there is no automated versioning, and Docker images must be built and pushed manually. This slows development, risks merging broken code, and makes releases error-prone.

## Solution

Implement GitHub Actions workflows that automatically build and test the application on every PR and merge to `main`, and automatically version and publish Docker images to Docker Hub when a release is created via `release-please`.

## User Stories

1. As a developer, I want CI to run `./gradlew check` on every PR push, so that I catch failures before merging
2. As a developer, I want CI to run on merge to `main`, so that I know `main` is always green
3. As a developer, I want branch protection on `main` requiring CI to pass, so that broken code cannot be merged
4. As a developer, I want `release-please` to automatically create a Release PR based on conventional commits, so that I don't manually track changelogs or version numbers
5. As a developer, I want the Release PR to auto-bump versions using semantic versioning (feat = minor, fix = patch, breaking = major), so that versions are meaningful and consistent
6. As a developer, I want merging the Release PR to create a git tag and GitHub Release automatically, so that releases are traceable
7. As a developer, I want a Docker image pushed to `[dockerUserName]/donations-api:<version>` on Docker Hub when a release is created, so that versioned images are available for deployment
8. As a developer, I want the initial version to be `1.0.0`, so that the project starts with a stable version number
9. As a developer, I want Docker images tagged with the release version (not `latest` on every merge), so that only releases produce published images
10. As a developer, I want CI tests to use Testcontainers for Postgres, so that integration tests run without external database setup
11. As a developer, I want Docker Hub credentials stored as GitHub secrets (`DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`), so that credentials are not exposed in workflow files

## Implementation Decisions

- **Two workflow files:**
  - `ci.yml` — triggers on `pull_request` and `push` to `main`, runs `./gradlew check` with Java 24 (Temurin)
  - `release.yml` — runs `release-please` on push to `main`; on release created, builds and pushes Docker image to Docker Hub
- **Versioning:** Conventional Commits parsed by `release-please` (Google's `release-please-action`). Starting version: `1.0.0`
- **Docker image:** `[dockerUserName]/donations-api:<version>` — only pushed on release, not on regular merges
- **Docker Hub auth:** Access token (not password), stored as `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` repo secrets
- **CI environment:** GitHub Actions runners with Docker available (needed for Testcontainers)
- **Branch protection:** Require `ci` workflow status check to pass before merging to `main`
- **No remote deployment step** — deployment to AWS/GCR/Supabase is a future concern

## Testing Decisions

- CI runs `./gradlew check` which executes all existing tests
- Tests use Testcontainers with PostgreSQL — no external DB needed
- Existing test suite covers: donations, donors, expenses, financial reports, OpenAPI spec, security, CORS, user management
- Prior art: all tests in `src/test/kotlin/com/example/donations/` follow Spring Boot Test + Testcontainers pattern
- No new application tests needed — this PRD is infrastructure-only

## Out of Scope

- Deployment to any cloud provider (AWS, GCR, Supabase)
- Native image (GraalVM) builds in CI
- Docker Hub image push on non-release merges
- Multi-architecture Docker builds
- Notification integrations (Slack, email)

## Further Notes

- Manual setup required after workflows are created:
  1. Add `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` secrets in GitHub repo settings
  2. Enable branch protection on `main` requiring CI status check to pass
- Project already uses conventional commit messages, so `release-please` will work without changing developer workflow
