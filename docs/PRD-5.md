# PRD-5: Generate and Publish Typed API Client npm Package on Release

> GitHub Issue: https://github.com/jorgetroya80/donations-api/issues/18

## Problem Statement

The frontend (`donations-frontend`) manually maintains TypeScript type definitions in `lib/api-types.ts` and hand-codes all API calls. Every time the backend API changes — new endpoints, renamed fields, changed request/response shapes — the frontend developer must manually discover the change and update types and call sites. This process is error-prone, slow, and creates a silent drift risk where the frontend and backend become out of sync without any compile-time signal.

## Solution

On every backend release, automatically generate a versioned TypeScript client package from the OpenAPI spec produced by SpringDoc and publish it to GitHub Packages as `@jorgetroya80/donations-api-client`. The package version matches the backend release version (e.g., `1.3.0`). The frontend pins an exact version and uses Dependabot to receive a PR whenever a new version is published, allowing a deliberate, reviewed upgrade path.

The generated client uses native `fetch` (via `@hey-api/client-fetch`) with no HTTP library dependency, so the frontend is free to wrap it with any HTTP layer now or in the future.

## User Stories

1. As a frontend developer, I want to install a typed API client package, so that I get compile-time errors when the backend API changes instead of discovering mismatches at runtime.
2. As a frontend developer, I want the client package to use native fetch with no external HTTP dependency, so that I can wrap it with any HTTP library without conflicts.
3. As a frontend developer, I want the package version to match the backend release version, so that I know exactly which API contract I am consuming.
4. As a frontend developer, I want to receive a Dependabot PR when a new client version is published, so that I can review the API diff and upgrade deliberately rather than being surprised.
5. As a frontend developer, I want the generated package to include typed request and response models, so that I can replace manual type definitions in `lib/api-types.ts` with imports from the package.
6. As a frontend developer, I want the generated package to include typed API functions for every endpoint, so that I can replace manual `ky` call sites with generated calls that are always in sync with the backend.
7. As a backend developer, I want spec generation to happen automatically at release time with no manual steps, so that the published package always reflects the exact released API.
8. As a backend developer, I want the npm publish to be a separate CI job from the Docker build, so that a publish failure does not affect the Docker image release and vice versa.
9. As a backend developer, I want to manually re-trigger the npm publish job for a specific version, so that I can recover from a failed publish without cutting a new release.
10. As a backend developer, I want the client package source to live inside the backend repo under `packages/api-client/`, so that the spec, generation config, and package are co-located and versioned together.
11. As a project maintainer, I want the package published to GitHub Packages under the `@jorgetroya80` scope, so that authentication reuses the existing `GITHUB_TOKEN` with no additional secrets.
12. As a project maintainer, I want the spec to be generated via the `springdoc-openapi-gradle-plugin` at build time, so that CI does not need to run a full Docker stack to produce the spec.

## Implementation Decisions

### Modules to build or modify

**1. Gradle spec generation (modify `build.gradle`)**
- Add `org.springdoc.openapi-gradle-plugin` plugin
- Configure output directory (`build/openapi/openapi.yaml`) and active Spring profile for the embedded server startup
- SpringDoc is already configured (`OpenApiConfig.kt`) and serves `/v3/api-docs` — the plugin hooks into this

**2. API client package (new `packages/api-client/` directory)**
- `package.json` — scoped as `@jorgetroya80/donations-api-client`, points to GitHub Packages registry, declares `@hey-api/client-fetch` as a peer dependency, uses `pnpm` as package manager
- `openapi-ts.config.ts` — configures `@hey-api/openapi-ts` to read `../../build/openapi/openapi.yaml`, output to `src/`, use `@hey-api/client-fetch` plugin and `@hey-api/typescript` plugin
- `tsconfig.json` — compiles `src/**/*.ts` to `dist/`, generates declaration files
- `.npmrc` — routes `@jorgetroya80` scope to `https://npm.pkg.github.com`, reads auth token from `NODE_AUTH_TOKEN` env var

**3. Release workflow (modify `.github/workflows/release.yml`)**
- Add `workflow_dispatch` trigger with a `version` input for manual re-publish
- Add `publish-npm-client` job:
  - `needs: [release]`
  - Only runs when release-please created a release OR `workflow_dispatch` was used
  - Permissions: `contents: read`, `packages: write`
  - Steps: checkout → Java 24 setup → `./gradlew generateOpenApiDocs` → Node 20 + pnpm 9 setup → set version from release-please outputs (or `workflow_dispatch` input) → `pnpm install` → `pnpm run generate` → `pnpm run build` → `pnpm publish --no-git-checks`
  - Authenticates to GitHub Packages via `GITHUB_TOKEN`

**4. Dependabot config (new file in `donations-frontend` repo)**
- `ecosystem: npm`, weekly schedule, scoped to `@jorgetroya80/donations-api-client` only

### Technical decisions

- **Client generator:** `@hey-api/openapi-ts` — active TS-first project, generates typed functions and models, supports `@hey-api/client-fetch` for native fetch output
- **HTTP layer:** `@hey-api/client-fetch` (peer dep) — no `ky`, no `axios`, no bundled HTTP logic; frontend configures base URL and credentials on the client instance
- **pnpm** used for all Node operations in CI and local dev (not npm)
- **Spec format:** YAML (OpenAPI 3.x), generated from the live Spring context via the Gradle plugin
- **Version sync:** release-please outputs `major`, `minor`, `patch` — these are passed directly to `pnpm version` on the package before publish

## Testing Decisions

Good tests verify the generated package is usable and correct, not the internal wiring of the generator.

**What makes a good test here:**
- Tests should confirm that the generated TypeScript compiles without errors against the current spec
- Tests should NOT test the generator's internals or snapshot the full generated output

**Modules to test:**

1. **Gradle spec generation** — integration test: run `./gradlew generateOpenApiDocs` in CI and assert `build/openapi/openapi.yaml` is non-empty and valid OpenAPI 3.x. This can be added as a step in `ci.yml`.
2. **Generated client build** — CI step: after generation, run `pnpm run build` and assert exit code 0. TypeScript compilation errors = test failure.
3. **Existing Spring integration tests** — no change; the existing Testcontainers-based tests already verify endpoint correctness and implicitly validate the spec content.

## Out of Scope

- Migrating the frontend's existing `lib/api-types.ts` or `ky` call sites to use the generated package — that is a separate frontend task after the pipeline is working
- Publishing to the public npm registry
- Generating clients in other languages
- API versioning strategy beyond matching the backend release version
- Mock server generation from the spec

## Further Notes

- The `springdoc.api-docs.enabled: false` production setting does not affect spec generation — the Gradle plugin uses a separate embedded context with a test/dev profile
- `pnpm publish --no-git-checks` is required because the `packages/api-client/` subdirectory is not a git root; pnpm would otherwise error on the dirty working tree during CI
- Dependabot config goes in the `donations-frontend` repo, not here — it must be filed as a separate PR there
- The frontend's existing `ky` client instance in `lib/api.ts` can be reused to configure the generated `@hey-api/client-fetch` instance (same base URL pattern and credentials-include behavior)
