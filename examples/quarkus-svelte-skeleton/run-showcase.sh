#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MODE="${1:---docker}"

if [[ "${MODE}" == "--docker" ]]; then
  echo "🚀 Starting Nomad Tri-Protocol Showcase via Docker Compose..."
  echo "   • MCP Server:          http://localhost:8081"
  echo "   • A2A Specialist:      http://localhost:8082"
  echo "   • AG-UI Orchestrator:  http://localhost:8080"
  echo "   • Nomad Studio UI:     http://localhost:5173"
  docker compose -f "${SCRIPT_DIR}/docker-compose.yml" up --build
elif [[ "${MODE}" == "--local" ]]; then
  echo "🔧 Building Quarkus AG-UI extensions locally..."
  (cd "${ROOT_DIR}" && ./mvnw clean install -DskipTests)

  trap 'echo "Stopping local showcase processes..."; kill 0' INT TERM EXIT

  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/mcp-server/pom.xml quarkus:dev) &
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/a2a-server/pom.xml quarkus:dev) &
  (cd "${ROOT_DIR}" && ./mvnw -f examples/quarkus-svelte-skeleton/backend/pom.xml quarkus:dev) &
  (cd "${SCRIPT_DIR}/frontend" && pnpm install && pnpm run dev) &

  wait
else
  echo "Usage: $0 [--docker | --local]"
  exit 1
fi
