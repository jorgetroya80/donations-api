# Plan: OpenAPI Client Package Generation on Release

> Source PRD: [docs/PRD-5.md](../docs/PRD-5.md) · [GitHub Issue #18](https://github.com/jorgetroya80/donations-api/issues/18)

## Architectural decisions

- **Package name**: `@jorgetroya80/donations-api-client` (GitHub Packages, `@jorgetroya80` scope)
- **Package location**: `packages/api-client/` inside this repo — co-located with the spec source
- **Spec format**: OpenAPI 3.x YAML, generated to `build/openapi/openapi.yaml` by the Gradle plugin
- **Client generator**: `@hey-api/openapi-ts` + `@hey-api/client-fetch` — native fetch, no HTTP library bundled
- **Package manager**: pnpm (not npm)
- **Version sync**: package version always matches the backend release version (managed by release-please)
- **Registry auth**: `GITHUB_TOKEN` — no extra secrets required
- **CI job name**: `publish-npm-client`, `needs: [release-please]`

---

## Phase 1: Gradle Spec Generation

**User stories**: 7, 12

### What to build

Add the `springdoc-openapi-gradle-plugin` to the Gradle build. When run, the plugin boots an embedded Spring context (with SpringDoc already configured at `OpenApiConfig.kt`), hits `/v3/api-docs`, and writes the result to `build/openapi/openapi.yaml`. No app or Docker stack needs to be running.

### Acceptance criteria

- [ ] `./gradlew generateOpenApiDocs` completes without error
- [ ] `build/openapi/openapi.yaml` is produced and contains all 6 controllers (auth, users, donations, donors, expenses, reports)
- [ ] The file is valid OpenAPI 3.x (can be opened in Swagger Editor or validated with a CLI tool)
- [ ] Existing `./gradlew check` still passes — plugin addition causes no test regressions

---

## Phase 2: Client Package Scaffold and Local Generation

**User stories**: 1, 2, 5, 6, 10

### What to build

Create `packages/api-client/` with everything needed to generate and compile the TypeScript client locally. The generator reads `build/openapi/openapi.yaml` (produced by Phase 1) and outputs typed API functions and models using `@hey-api/openapi-ts`. The compiled output in `dist/` is what will eventually be published.

Files to create:
- `packages/api-client/package.json` — scoped package name, pnpm packageManager, `@hey-api/client-fetch` as peer dep, publishConfig pointing to GitHub Packages
- `packages/api-client/openapi-ts.config.ts` — input from `../../build/openapi/openapi.yaml`, output to `src/`, plugins: `@hey-api/client-fetch` + `@hey-api/typescript`
- `packages/api-client/tsconfig.json` — compiles `src/**/*.ts` to `dist/`, emits declarations
- `packages/api-client/.npmrc` — routes `@jorgetroya80` scope to `https://npm.pkg.github.com`, reads `NODE_AUTH_TOKEN`

### Acceptance criteria

- [ ] `pnpm install` inside `packages/api-client/` succeeds
- [ ] `pnpm run generate` produces typed files in `packages/api-client/src/` covering all API endpoints and models
- [ ] `pnpm run build` compiles without TypeScript errors and produces `dist/` with `.js` and `.d.ts` files
- [ ] The generated types match the backend DTOs (spot-check: `DonationResponse`, `CreateDonorRequest`, `PageResponse<T>`, role enums)
- [ ] `packages/api-client/` is added to `.gitignore` exclusions for `src/` and `dist/` (generated — not committed)

---

## Phase 3: CI Publish Job

**User stories**: 3, 7, 8, 9, 11

### What to build

Add a `publish-npm-client` job to `.github/workflows/release.yml`. The job runs only when release-please creates a release (or when manually triggered via `workflow_dispatch`). It generates the spec, generates the client, compiles it, sets the package version to match the release, and publishes to GitHub Packages.

The existing `workflow_dispatch` trigger already has a `tag` input for Docker re-publish — add a `version` input alongside it for npm re-publish recovery.

Job sequence: checkout → Java 24 → `./gradlew generateOpenApiDocs` → Node 24 + pnpm 11.1.1, → set version → `pnpm install` → `pnpm run generate` → `pnpm run build` → `pnpm publish --no-git-checks`.

The job has `permissions: packages: write` and authenticates via `GITHUB_TOKEN`.

### Acceptance criteria

- [ ] Merging a release-please PR triggers `publish-npm-client` after `release-please` job completes
- [ ] Package appears at `https://github.com/jorgetroya80?tab=packages` with the correct version
- [ ] `docker` job and `publish-npm-client` job are independent — failure in one does not affect the other
- [ ] Running `workflow_dispatch` with a `version` input republishes that version successfully (manual recovery path)
- [ ] "Re-run failed jobs" in the GitHub Actions UI reruns only `publish-npm-client`, not the docker job

---

## Phase 4: Dependabot on Frontend

**User stories**: 4

### What to build

Add `.github/dependabot.yml` to the `donations-frontend` repo. Configure it to watch the npm ecosystem, weekly schedule, scoped only to `@jorgetroya80/donations-api-client`. Dependabot opens a PR on `donations-frontend` whenever a new version is published to GitHub Packages.

### Acceptance criteria

- [ ] `.github/dependabot.yml` exists in `donations-frontend` with npm ecosystem config
- [ ] Dependabot is scoped to `@jorgetroya80/donations-api-client` only (not all npm deps)
- [ ] After a new version is published (Phase 3), Dependabot opens a PR on `donations-frontend` within one week
- [ ] The PR updates `package.json` to the new exact version
