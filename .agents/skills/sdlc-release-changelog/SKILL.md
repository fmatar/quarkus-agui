---
name: sdlc-release-changelog
description: Manages Semantic Versioning (SemVer), conventional changelogs (CHANGELOG.md adhering to Keep a Changelog standard), Git tagging, GitHub release creation, Docker containerization, and Kubernetes/Helm deployments.
version: 0.0.1
license: Apache 2.0
allowed-tools: run_command, view_file, write_to_file, replace_file_content, mcp_github-mcp-server_get_latest_release
---

# SDLC Release & Changelog Management Skill (`sdlc-release-changelog`)

Use this skill when preparing releases, tagging production builds, calculating SemVer version bumps, updating `CHANGELOG.md`, and generating GitHub Releases.

---

## 🏷️ Versioning Philosophy (SemVer 2.0.0)

Given a version number `MAJOR.MINOR.PATCH`:
- **MAJOR** (`X.0.0`): Breaking architectural shifts, database schema breaking migrations, or removed public API contracts.
- **MINOR** (`0.X.0`): New features, new service integrations, or non-breaking API/UI additions.
- **PATCH** (`0.0.X`): Bug fixes, performance optimizations, security patches, and minor styling updates.

---

## 📝 Conventional Commits & Changelog Format

We strictly adhere to the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) standard.

### Standard Categories
- `Added` for new features or capabilities.
- `Changed` for changes in existing functionality.
- `Deprecated` for soon-to-be removed features.
- `Removed` for now removed features.
- `Fixed` for any bug fixes.
- `Security` in case of vulnerabilities or data privacy improvements.

### Example `CHANGELOG.md` Entry
```markdown
## [1.1.0] - 2026-09-22

### Added
- feat(auth): implement OIDC token validation and refresh lifecycle ([#101](https://github.com/org/repo/issues/101))
- feat(events): add reactive event publisher and deduplication ([#102](https://github.com/org/repo/issues/102))

### Changed
- chore(test): configure random quarkus http test port (0) to eliminate parallel build collisions ([#103](https://github.com/org/repo/issues/103))

### Fixed
- fix(api): resolve race condition in concurrent user session updates ([#104](https://github.com/org/repo/issues/104))
- style: apply spotless formatting fixes
```

---

## 🚀 Release Workflow Steps

### Turnkey Release Automation (Recommended)
Run the automated release command to execute the quality gate, calculate SemVer bump, update project manifests (`package.json`, `pom.xml`, etc.), roll `CHANGELOG.md`, and commit/tag git:
```bash
# Preview changes without modifying files or git
node .agent/scripts/sdlc-engine.mjs release patch --dry-run

# Execute full release (patch, minor, or major)
node .agent/scripts/sdlc-engine.mjs release patch
```

### Manual Release Steps (Reference)
1. **Calculate Next Version**: Inspect git log since last tag to determine whether change is `patch`, `minor`, or `major`.
2. **Update Project Descriptors**: Update `"version"` in `package.json` (frontend) and `<version>` in `pom.xml` (backend).
3. **Update `CHANGELOG.md`**: Convert `[Unreleased]` changes into a versioned release header with the release date.
4. **Git Commit & Tag**:
   ```bash
   git add package.json pom.xml CHANGELOG.md
   git commit -m "chore(release): vX.Y.Z"
   git tag -a vX.Y.Z -m "Release vX.Y.Z"
   ```
5. **Create GitHub Release**: Publish the release with formatted release notes derived from `CHANGELOG.md`.
6. **Containerization (Docker)**: Build and publish container images tagged with `vX.Y.Z` and `latest`.
7. **Deployment (Kubernetes / Helm)**: Update Helm release values and apply deployment rolling updates.

---

## ⏭️ Next Recommended Step

- Post release verification: Monitor observability dashboards, error trackers, and deployment health.
