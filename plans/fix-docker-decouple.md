# Plan: Fix Docker Publish — Reliable Release-Please Gating

> Source PRD: Conversation — Docker image not published after `ci:` commit merged; release-please gating broken by permissions leak and missing escape hatch

## Architectural decisions

- **Trigger**: release-please remains the gate for docker publish (no change to intent)
- **Escape hatch**: `workflow_dispatch` with `inputs.tag` for edge cases (e.g. CI-only commits already on main)
- **Permissions**: scoped per-job — release-please job gets `contents: write` + `pull-requests: write`; docker job gets `contents: read` only
- **Tag source**: `${{ needs.release-please.outputs.tag_name || inputs.tag }}` — works for both automatic and manual triggers
- **File**: `.github/workflows/release.yml` only

---

## Phase 1: Fix `release.yml` structure

**User stories**:
- Docker job fires reliably whenever release-please creates a release
- Permissions are least-privilege (docker job does not inherit write perms)
- Manual escape hatch available via `workflow_dispatch` when CI-only commits land without triggering a release

### What to build

Update `.github/workflows/release.yml`:
1. Add `workflow_dispatch` trigger with `inputs.tag` (required string, e.g. `1.1.1`)
2. Move `contents: write` and `pull-requests: write` from workflow-level `permissions:` into the `release-please` job block; set workflow-level to `contents: read`
3. In docker job: replace tag derivation with `${{ needs.release-please.outputs.tag_name || inputs.tag }}` (strip leading `v` if needed)

### Acceptance criteria

- [ ] `workflow_dispatch` trigger present with required `tag` input
- [ ] Workflow-level permissions: `contents: read`
- [ ] `release-please` job has `permissions: contents: write, pull-requests: write`
- [ ] `docker` job has `permissions: contents: read`
- [ ] Docker image tag derived from `needs.release-please.outputs.tag_name || inputs.tag`
- [ ] Manually dispatching the workflow with a tag input pushes the correct image tag to Docker Hub

---

## Phase 2: Unblock current ARM64 image

**User stories**:
- ARM64-capable image published for the current codebase (ARM64 fix is on main but no image was published because `ci:` commit didn't trigger release-please)

### What to build

After Phase 1 is merged: push an empty `fix:` commit to main with message `fix: publish ARM64 docker image`. Release-please picks it up, creates v1.1.1, docker job fires using the Phase 1 workflow, multi-arch image pushed to Docker Hub.

### Acceptance criteria

- [ ] `fix:` commit pushed to main
- [ ] Release-please creates GitHub release v1.1.1
- [ ] Docker job runs and succeeds
- [ ] `docker buildx imagetools inspect <user>/donations-api:1.1.1` shows `linux/amd64` and `linux/arm64`
