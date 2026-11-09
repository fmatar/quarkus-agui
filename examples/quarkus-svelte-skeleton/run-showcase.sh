#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MODE="${1:---docker}"

if [[ "${MODE}" == "--docker" ]]; then
  echo "📦 Building Quarkus AG-UI extensions & Jib layered containers (with Quinoa SPA bundled)..."
  (cd "${ROOT_DIR}" && ./mvnw clean install -DskipTests -pl ag-ui-core,quarkus-extension/common,quarkus-extension/common-deployment,quarkus-extension/sse,quarkus-extension/sse-deployment,quarkus-extension/websockets,quarkus-extension/websockets-deployment,quarkus-extension/runtime,quarkus-extension/deployment,quarkus-ag-ui-bom)
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/pom.xml clean package -Dquarkus.container-image.build=true -DskipTests)

  echo "🚀 Starting Nomad Tri-Protocol Showcase via Docker Compose (Jib + Quinoa)..."
  echo "   • MCP Server:                        http://localhost:8081"
  echo "   • A2A Specialist:                    http://localhost:8082"
  echo "   • Nomad Studio UI + AG-UI Backend:   http://localhost:8080"
  docker compose -f "${SCRIPT_DIR}/docker-compose.yml" up --remove-orphans
elif [[ "${MODE}" == "--docker-native" ]]; then
  echo "📦 Building Quarkus AG-UI extensions & GraalVM/Mandrel Native Jib containers (-Pnative)..."
  (cd "${ROOT_DIR}" && ./mvnw clean install -DskipTests -pl ag-ui-core,quarkus-extension/common,quarkus-extension/common-deployment,quarkus-extension/sse,quarkus-extension/sse-deployment,quarkus-extension/websockets,quarkus-extension/websockets-deployment,quarkus-extension/runtime,quarkus-extension/deployment,quarkus-ag-ui-bom)
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/pom.xml clean package -Pnative -Dquarkus.container-image.build=true -DskipTests)

  echo "🚀 Starting Nomad Tri-Protocol Native Showcase via Docker Compose..."
  echo "   • MCP Server:                        http://localhost:8081"
  echo "   • A2A Specialist:                    http://localhost:8082"
  echo "   • Nomad Studio UI + AG-UI Backend:   http://localhost:8080"
  docker compose -f "${SCRIPT_DIR}/docker-compose.yml" up --remove-orphans
elif [[ "${MODE}" == "--local" ]]; then
  echo "🔧 Building Quarkus AG-UI extensions locally..."
  (cd "${ROOT_DIR}" && ./mvnw clean install -DskipTests -pl ag-ui-core,quarkus-extension/common,quarkus-extension/common-deployment,quarkus-extension/sse,quarkus-extension/sse-deployment,quarkus-extension/websockets,quarkus-extension/websockets-deployment,quarkus-extension/runtime,quarkus-extension/deployment,quarkus-ag-ui-bom)

  trap 'echo "Stopping local showcase processes..."; kill 0' INT TERM EXIT

  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/mcp-server/pom.xml quarkus:dev) &
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/a2a-server/pom.xml quarkus:dev) &
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/backend/pom.xml quarkus:dev) &

  wait
else
  echo "Usage: $0 [--docker | --docker-native | --local]"
  exit 1
fi
