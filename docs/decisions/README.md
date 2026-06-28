# Architecture Decision Records

This directory records the **significant, hard-to-reverse technical decisions** behind the
Donations API — and, just as importantly, the alternatives that were rejected and why.

It complements the rest of `docs/`:

- **`docs/PRD-*.md`** — *what* we are building (product requirements).
- **`plans/*.md`** — *how* a given piece of work is sequenced (implementation plans).
- **`docs/decisions/` (here)** — *why* a foundational choice was made the way it was.

Code shows what was built. An ADR captures the context, constraints, and trade-offs that
led to a decision so a future engineer — or agent — does not have to reverse-engineer the
reasoning or re-litigate a settled question.

## When to write an ADR

Write one when a decision is significant **and** expensive to reverse:

- Choosing a framework, library, or major dependency.
- Designing or materially changing the data model / database schema.
- Selecting an authentication or authorization strategy.
- Deciding API shape or making a breaking change to a public contract.
- Choosing a deployment platform, hosting model, or infrastructure approach.

**Do not** write an ADR for reversible, local choices (naming, a one-off refactor, a bug
fix). Those belong in the code, a commit message, or a plan.

## How to write one

1. Copy [`ADR-template.md`](ADR-template.md) to `ADR-NNN-kebab-title.md`.
2. Take the next free number — sequential, **never reused**, even if an ADR is superseded.
3. Fill in all six sections. Cite real files (`path:line`) rather than describing code abstractly.
4. Link the ADR from the PRD or plan that drove it, and link back from the ADR.
5. Add a row to the [index](#index) below.

## Lifecycle

```
Proposed → Accepted → (Superseded by ADR-XXX | Deprecated)
```

**Accepted ADRs are immutable.** When a decision changes, do not edit or delete the old
record — write a new ADR that supersedes it, and set the old one's status to
`Superseded by ADR-XXX`. The history is the point.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [001](ADR-001-session-based-authentication.md) | Session-based authentication over JWT | Accepted |
| [002](ADR-002-deploy-render-neon.md) | Deploy on Render + Neon managed Postgres | Accepted |
| [003](ADR-003-page-serialization-via-dto.md) | Page serialization via `VIA_DTO` | Accepted |
