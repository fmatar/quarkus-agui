# SPEC-01: Quarkus MCP Server (`:8081`) & Quarkus A2A Server (`:8082`) in Reference Showcase

- **Linked Capability**: `CAP-01`
- **Ticket**: `#2`
- **Modules**: `examples/quarkus-svelte-skeleton/mcp-server` & `examples/quarkus-svelte-skeleton/a2a-server`
- **Stack**: Java 25 + Quarkus `3.40.1.1` + Portable GraalVM 25 Native (`-Dnative` / `-Dnative-container`)
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Before building the AG-UI orchestrator and extracting the extension, the showcase needs real, standalone **MCP (Model Context Protocol)** and **A2A (Agent-to-Agent)** servers built on Quarkus `3.40.1.1` and Java 25 so the orchestrator can execute real tool and multi-agent delegations.

## 2. Module Specifications

### 2.1 `mcp-server` (Port `8081` — `io.quarkiverse.mcp:quarkus-mcp-server-http`)
- **MCP `@Tool` Methods & HTTP Tool Execution Endpoints (`/api/mcp/tools`)**:
  1. `inspectClusterHealth(String region)`: Returns cluster status, CPU/Memory utilization, and pod statuses for `api-gateway`, `payment-service`, and `auth-service`.
  2. `queryServiceLogs(String serviceName, String severity, int limit)`: Returns structured log entries matching the service and severity.
  3. `scaleDeployment(String serviceName, int targetReplicas)`: Mutates live cluster replica count and returns updated deployment state.
- **MCP `@Resource` (`cluster://topology`) & `/api/mcp/topology`**: Returns current cluster state snapshot.

### 2.2 `a2a-server` (Port `8082` — `org.a2aproject.sdk` / A2A JSON-RPC & REST)
- **A2A Discovery (`GET /.well-known/agent.json`)**:
  Returns standard A2A `AgentCard` JSON (`name`, `description`, `url`, `version`, `capabilities` with `streaming: true`, and `skills`: `audit-clean-architecture`, `assess-security-posture`, `plan-incident-remediation`).
- **A2A Task Execution (`POST /api/a2a/tasks/send`)**:
  Accepts `A2aTaskRequest(String taskId, String skillId, String prompt, Map<String, Object> context)` and returns `A2aTaskResponse(String taskId, String agentName, String status, List<A2aArtifact> artifacts)` with detailed security & Clean Architecture audit findings.

## 3. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: Both `mcp-server` and `a2a-server` compile and run on Java 25 and Quarkus `3.40.1.1` with ephemeral test port `0` (`quarkus.http.test-port=0`).
- **AC-02**: `mcp-server` exposes MCP tools (`inspectClusterHealth`, `queryServiceLogs`, `scaleDeployment`) and topology snapshot with full input validation.
- **AC-03**: `a2a-server` exposes `GET /.well-known/agent.json` and `POST /api/a2a/tasks/send` returning structured A2A artifacts.
- **AC-04**: Dual-Gate verification passes with $\ge 85\%$ line/branch coverage and $\ge 80\%$ PITest mutation kill score.
