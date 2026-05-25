# PRD-7: Export Client Factory from API Client Package

## Problem Statement

Frontend developers consuming `@jorgetroya80/donations-api-client` cannot configure the HTTP client for their environment. The package's main entry point only exports SDK functions and domain types. `createClient` and `createConfig` — needed to set a production `baseUrl` and inject auth token interceptors — are buried in an internal subpath and not accessible from the package root.

The generated singleton client inside the package hardcodes `http://localhost:8081`, making it unusable in any non-local environment without workarounds.

## Solution

Restructure the package so that `createClient`, `createConfig`, and the essential client types (`Client`, `Config`, `CreateClientConfig`) are exported from the package's main entry point alongside the existing SDK functions.

To avoid generated files being overwritten on each `pnpm run generate` run, the generator output directory moves from `src/` to `src/generated/`. A stable, hand-written `src/index.ts` (committed to version control) re-exports everything the frontend needs from one import path.

## User Stories

1. As a frontend developer, I want to import `createClient` from the package root, so that I don't need to know internal package structure.
2. As a frontend developer, I want to import `createConfig` from the package root, so that I can configure the base URL and client options in one place.
3. As a frontend developer, I want to point the client at a production API URL, so that the app works in environments beyond localhost.
4. As a frontend developer, I want to inject an auth token interceptor into the client, so that all API calls include a Bearer token without manual header setup per call.
5. As a frontend developer, I want to use a single import path for both SDK functions and client utilities, so that imports are consistent and easy to maintain.
6. As a frontend developer, I want the `Client` type exported from the package, so that I can type client instances correctly in TypeScript.
7. As a frontend developer, I want the `Config` and `CreateClientConfig` types exported, so that I can type my configuration objects.
8. As a frontend developer, I want all existing SDK function imports to continue working unchanged, so that this change requires no updates to existing call sites.
9. As a backend developer, I want `pnpm run generate` to not overwrite the public API surface, so that re-generating the client from an updated spec does not break exports.
10. As a maintainer, I want the generated files isolated in a subdirectory, so that it is clear which files are generated and which are hand-written.
11. As a maintainer, I want `.gitignore` to exclude only generated files and not the hand-written index, so that the public API surface is tracked in version control.
12. As a CI pipeline, I want the generate → build → publish sequence to work without manual intervention after this change, so that releases remain fully automated.

## Implementation Decisions

- **Generator output directory**: Change from `src/` to `src/generated/`. All files produced by `@hey-api/openapi-ts` land there and remain gitignored.
- **Hand-written entry point**: Create `src/index.ts` (committed). Re-exports all SDK functions and types via `export * from './generated/index'`, plus `createClient`, `createConfig`, and `Client`/`Config`/`CreateClientConfig` types from `./generated/client`.
- **Export scope**: Minimal — only `createClient`, `createConfig`, and the three types needed to configure and type a client instance. Internal utilities (body serializers, param builders, interceptor internals) are not re-exported.
- **`.gitignore` update**: Replace the `src/` entry with `src/generated/` so `src/index.ts` is tracked.
- **`tsconfig.json`**: No changes. The `src/**/*.ts` glob and `rootDir: src` already cover both `src/index.ts` and `src/generated/**`.
- **`package.json`**: No changes. Main entry `dist/index.js` maps to compiled `src/index.ts`.
- **CI workflow**: No changes. The existing generate → build → publish sequence is unaffected.

## Testing Decisions

A good test for this change verifies the public API surface, not internal file structure:

- After `pnpm run generate && pnpm run build`, assert that `dist/index.d.ts` exports `createClient`, `createConfig`, `Client`, `Config`, and `CreateClientConfig`.
- Assert that all previously exported SDK functions are still present in `dist/index.d.ts`.
- Assert that `src/index.ts` is NOT overwritten by `pnpm run generate` (i.e. its content remains the hand-written re-export barrel).

No dedicated test file is required — the TypeScript compiler (`pnpm run build`) acts as the primary correctness gate. A broken re-export or missing generated file will produce a compile error.

## Out of Scope

- Providing a pre-configured client with interceptor logic inside the package (frontend configures its own instance).
- Exporting internal utilities: body serializers, path/query serializers, SSE client.
- Changes to how SDK functions select a client instance (they already accept a `client` option).
- Frontend implementation of the auth interceptor (belongs in the frontend repo).
- Changes to the Spring Boot backend or OpenAPI spec generation.

## Further Notes

Frontend usage pattern after this change:

```ts
import { createClient, createConfig, login } from '@jorgetroya80/donations-api-client';
import type { Client } from '@jorgetroya80/donations-api-client';

const client: Client = createClient(createConfig({
  baseUrl: process.env.API_BASE_URL,
}));

const { data } = await login({ body: { username, password }, client });
```

The singleton client baked into the generated `client.gen.ts` (pointing to `localhost:8081`) is not removed — it remains the default for any SDK call that does not explicitly pass a `client` option.
