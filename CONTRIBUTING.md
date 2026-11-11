# Contributing to Quarkus AG-UI (`quarkus-agui`)

Thank you for contributing to **Quarkus AG-UI**! This repository is governed by **Robert C. Martin Clean Architecture**, **Dual-Gate TDD (`INV-02`)**, and the **Agentic SDLC Framework (`INV-01`–`INV-08`)**.

---

## 1. Toolchain Prerequisites

- **Java 25** (OpenJDK 25 or GraalVM 25 CE) — builds the entire reactor; reusable library modules (`ag-ui-quarkus`, `ag-ui-langchain4j`, `ag-ui-quarkus-langchain4j`, `ag-ui-a2a`) enforce `--release 17` bytecode (`INV-04`), while `examples/quarkus-svelte-skeleton` targets Java 25 (`INV-08`).
- **`pnpm`** (`>= 9.x` / `10.x`) — manages `packages/agui-svelte` and `examples/quarkus-svelte-skeleton/frontend` via `pnpm-workspace.yaml`.
- **Node.js** (`>= 20+`) — runs headless `@agui/svelte` unit tests and `.agent/scripts/sdlc-engine.mjs`.
- **Optional (Showcase & Native)**:
  - **Ollama** (`ollama pull llama3.2:latest`) for live local LLM streaming in the Nomad showcase.
  - **Docker** for Jib container builds (`--docker`) and Mandrel JDK 25 container native compilation (`-Dnative-container`).

Verify your local toolchain:

```bash
java -version
./mvnw -version
pnpm -v
node .agent/scripts/sdlc-engine.mjs env
```

---

## 2. Local Build, Coverage & Mutation Testing (`INV-02`)

1. **Configure local environment variables** (used by the showcase `a2a-server` for authenticated REST Countries v5 advisories):
   ```bash
   cp .env.example .env
   ```

2. **Install workspace JS packages & run frontend / SDK unit tests**:
   ```bash
   pnpm install
   pnpm --dir packages/agui-svelte test
   pnpm --dir examples/quarkus-svelte-skeleton/frontend test
   ```

3. **Run Maven unit tests & JaCoCo coverage (`>= 85%` line/branch threshold)**:
   ```bash
   ./mvnw test jacoco:report
   ```

4. **Run PITest mutation testing (`>= 80%` mutation kill threshold)**:
   ```bash
   ./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
   ```

---

## 3. Portable GraalVM 25 Native Compilation (`INV-06`)

`ag-ui-quarkus` ships a 57-type GraalVM reflection descriptor (`META-INF/native-image/org.agui/ag-ui-quarkus/reflect-config.json`) requiring zero Quarkus `-deployment` build-time processors (`ADR-006`, `ADR-007`):

```bash
# Uses local GraalVM 25 if installed, or auto-falls back to Mandrel JDK 25 container build
./mvnw verify -Dnative

# Explicitly force Mandrel JDK 25 Linux container build on any OS
./mvnw verify -Dnative-container
```

---

## 4. Clean Architecture & Documentation Checks (`INV-03`)

- **Concentric Ring Governance (`.archlens/policy.json`)**: Source dependencies strictly point inward (`adapters` -> `application` -> `domain`). Domain packages (`org.agui.*.domain`) must remain pure Java 17 records/enums with zero framework or SDK leaks.
- **Documentation & Spec Drift**:
  - Any mutation to dependencies, C4 boundaries, or wire contracts requires an accepted ADR (`node .agent/scripts/sdlc-engine.mjs adr create`).
  - Always verify documentation integrity before opening a Pull Request:
    ```bash
    node .agent/scripts/sdlc-engine.mjs drift check
    node .agent/scripts/sdlc-engine.mjs readme audit --strict
    node .agent/scripts/sdlc-engine.mjs wiki sync
    node .agent/scripts/sdlc-engine.mjs wiki audit --strict
    ```

---

## 5. Branching & Pull Request Workflow (`INV-01`)

Direct commits, merges, or pushes to `main` or `develop` are blocked by the **4-Layer Git Guard**:

```bash
# 1. Start a tracked feature/bugfix branch and SDLC session
node .agent/scripts/sdlc-engine.mjs stage begin --issue <id> --title "<short-title>" --checkout

# 2. Implement via Red-Green-Refactor TDD, then run pre-PR verification
node .agent/scripts/sdlc-engine.mjs stage pre-pr --issue <id> --summary "<change-summary>"

# 3. Execute strict 9-pillar quality gate & mint Stage 7 SHA-256 Merkle Gate Receipt
node .agent/scripts/sdlc-engine.mjs gate --strict
```

---

## 6. Dual License

Contributions to `quarkus-agui` are dual-licensed under the [Apache License, Version 2.0](./LICENSE) and the [MIT License](./LICENSE-MIT).
