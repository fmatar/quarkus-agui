#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MODE="${1:---docker}"
ENV_FILE_ARGS=()
if [[ -f "${ROOT_DIR}/.env" ]]; then
  ENV_FILE_ARGS=(--env-file "${ROOT_DIR}/.env")
fi

if [[ "${MODE}" == "--docker" ]]; then
  echo "📦 Building AG-UI modules & Jib layered containers (with Quinoa SPA bundled)..."
  (cd "${ROOT_DIR}" && ./mvnw clean install -DskipTests -pl ag-ui-bom,ag-ui-a2a,ag-ui-langchain4j,ag-ui-quarkus,ag-ui-quarkus-langchain4j -am)
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/pom.xml clean package -Dquarkus.container-image.build=true -DskipTests)

  echo "🚀 Starting Nomad Tri-Protocol Showcase via Docker Compose (Jib + Quinoa + SmallRye Health)..."
  echo "   • MCP Server:                        http://localhost:8081/q/health/ready"
  echo "   • A2A Specialist (REST Countries v5):http://localhost:8082/q/health/ready"
  echo "   • Nomad Studio UI + AG-UI Backend:   http://localhost:8080/q/health/ready"
  docker compose -f "${SCRIPT_DIR}/docker-compose.yml" "${ENV_FILE_ARGS[@]}" up --remove-orphans
elif [[ "${MODE}" == "--docker-native" ]]; then
  echo "📦 Building AG-UI modules & GraalVM/Mandrel Native Jib containers (-Pnative)..."
  (cd "${ROOT_DIR}" && ./mvnw clean install -DskipTests -pl ag-ui-bom,ag-ui-a2a,ag-ui-langchain4j,ag-ui-quarkus,ag-ui-quarkus-langchain4j -am)
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/pom.xml clean package -Pnative -Dquarkus.container-image.build=true -DskipTests)

  echo "🚀 Starting Nomad Tri-Protocol Native Showcase via Docker Compose..."
  echo "   • MCP Server:                        http://localhost:8081/q/health/ready"
  echo "   • A2A Specialist (REST Countries v5):http://localhost:8082/q/health/ready"
  echo "   • Nomad Studio UI + AG-UI Backend:   http://localhost:8080/q/health/ready"
  docker compose -f "${SCRIPT_DIR}/docker-compose.yml" "${ENV_FILE_ARGS[@]}" up --remove-orphans
elif [[ "${MODE}" == "--local" ]]; then
  echo "🔧 Building AG-UI modules locally..."
  (cd "${ROOT_DIR}" && ./mvnw clean install -DskipTests -pl ag-ui-bom,ag-ui-a2a,ag-ui-langchain4j,ag-ui-quarkus,ag-ui-quarkus-langchain4j -am)

  if [[ -f "${ROOT_DIR}/.env" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "${ROOT_DIR}/.env"
    set +a
  fi

  trap 'echo "Stopping local showcase processes..."; kill 0' INT TERM EXIT

  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/mcp-server/pom.xml quarkus:dev) &
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/a2a-server/pom.xml quarkus:dev) &
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/backend/pom.xml quarkus:dev) &

  wait
else
  echo "Usage: $0 [--docker | --docker-native | --local]"
  exit 1
fi
