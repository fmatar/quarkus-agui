---
name: Feature Request (CAP-XX / SPEC-XX)
about: Propose a constitutional product capability or feature governed by Robert C. Martin TDD
title: "feat: "
labels: feature, status:ready
---

### 🎯 Problem Statement & User Value
A clear and concise description of what the problem is.

### 🚀 Solution & Acceptance Criteria
A clear and concise description of what you want to happen.

### 📐 Linked Specification & Architecture
- **Capability ID**: `CAP-XX`
- **Specification**: `docs/specs/cap-XX.md`
- **Ring Impact**: `Domain Core -> UseCase -> Adapter`

### 🛡️ Clean Code & Dual-Gate Guarantees
- [ ] Failing Red test written before production code (`/sdlc-tdd`)
- [ ] Diff line coverage `>= 85%` and mutation score `>= 80%`
- [ ] Zero concentric ring violations (`.archlens/policy.json`)
