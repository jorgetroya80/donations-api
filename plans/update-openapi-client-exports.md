# Plan: Export Client Factory from API Client Package

> Source PRD: docs/PRD-7.md

## Architectural decisions

- **Package entry**: `dist/index.js` — unchanged, `package.json` needs no edits
- **Generator output**: moves from `src/` → `src/generated/` (gitignored)
- **Public API**: single import path — `@jorgetroya80/donations-api-client`
- **Exports added**: `createClient`, `createConfig`, `Client`, `Config`, `CreateClientConfig`
- **Exports unchanged**: all existing SDK functions and domain types

---

## Phase 1: Restructure output and expose client factory

**User stories**: US-1 through US-12 (all)

### What to build

Three file changes deliver the full feature end-to-end:

1. **Generator config** — point `output` at `src/generated` so all auto-generated files land there and are never confused with hand-written code.

2. **`.gitignore`** — replace the `src/` ignore entry with `src/generated/` so the new hand-written index is tracked in version control while generated files remain ignored.

3. **Hand-written `src/index.ts`** — re-exports everything from the generated barrel plus `createClient`, `createConfig`, and the three client types. This file is the stable public API surface that survives every regeneration cycle.

The existing `tsconfig.json` (`src/**/*.ts`, `rootDir: src`, `outDir: dist`) and `package.json` (`main: dist/index.js`) require no changes.

### Acceptance criteria

- [ ] `pnpm run generate` writes files to `src/generated/` and does not touch `src/index.ts`
- [ ] `pnpm run build` succeeds with no TypeScript errors
- [ ] `dist/index.d.ts` exports `createClient`, `createConfig`, `Client`, `Config`, `CreateClientConfig`
- [ ] `dist/index.d.ts` still exports all previously available SDK functions and domain types
- [ ] `src/index.ts` is tracked by git; `src/generated/` is not
- [ ] Running generate → build sequence twice in a row produces identical output
