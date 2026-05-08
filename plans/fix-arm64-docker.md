# Plan: Fix ARM64 Docker Image Support

> Source PRD: Conversation — Mac Apple Silicon users see platform mismatch warning when pulling Docker image

## Architectural decisions

- **CI runner**: `ubuntu-latest` (AMD64) — unchanged; QEMU enables cross-platform build
- **Target platforms**: `linux/amd64`, `linux/arm64`
- **Registry**: Docker Hub (unchanged)
- **Build action**: `docker/build-push-action@v6` (unchanged, add `platforms` key)
- **Base image**: `eclipse-temurin:24-jre-noble` — already has ARM64 layers, no Dockerfile changes needed

---

## Phase 1: Multi-platform Docker build

**User stories**:
- As a Mac Apple Silicon user, I can pull the Docker image without a platform mismatch warning
- As an AMD64 user, existing behavior is unchanged

### What to build

Update the `docker` job in `.github/workflows/release.yml` to build and push a multi-arch manifest covering both `linux/amd64` and `linux/arm64`. Add QEMU emulation setup and Buildx builder setup before the existing build-push step, then add a `platforms` key to that step.

### Acceptance criteria

- [ ] `release.yml` docker job includes `docker/setup-qemu-action@v3` step
- [ ] `release.yml` docker job includes `docker/setup-buildx-action@v3` step
- [ ] `docker/build-push-action@v6` has `platforms: linux/amd64,linux/arm64`
- [ ] After a release, `docker buildx imagetools inspect <user>/donations-api:<version>` shows both `linux/amd64` and `linux/arm64` platforms
- [ ] `docker pull` on Mac Apple Silicon produces no platform warning
