# SPEC-06: Reference Quarkus + LangChain4j Agent Backend (`examples/quarkus-svelte-skeleton`)

- **Linked Capability**: `CAP-06`
- **Depends On**: `CAP-03`, `CAP-04`
- **Module**: `examples/quarkus-svelte-skeleton/backend`
- **Status**: `ACTIVE`

## 1. Problem Statement & Technical Scope
Provides the reference Quarkus backend exposing `POST /api/agent/run` (`Produces(MediaType.SERVER_SENT_EVENTS)`), orchestrating a LangChain4j agent with `@Tool` capabilities and streaming AG-UI events via `quarkus-ag-ui`.

## 2. API Contract
- **Endpoint**: `POST /api/agent/run`
- **Request Body (`AgentRunRequest`)**: `{ "threadId": "thread-1", "prompt": "Check system architecture health" }`
- **Response Stream (`text/event-stream`)**:
  - `data: {"id":"...","type":"RUN_STARTED","threadId":"thread-1","timestamp":...,"payload":{"threadId":"thread-1","status":"started"}}`
  - `data: {"id":"...","type":"STATUS","threadId":"thread-1","timestamp":...,"payload":{"state":"thinking","message":"Analyzing prompt..."}}`
  - `data: {"id":"...","type":"TOOL_CALL","threadId":"thread-1","timestamp":...,"payload":{"callId":"call-1","tool":"lookupArchitectureStats","args":{...}}}`
  - `data: {"id":"...","type":"TOOL_RESULT","threadId":"thread-1","timestamp":...,"payload":{"callId":"call-1","tool":"lookupArchitectureStats","result":{...},"isError":false}}`
  - `data: {"id":"...","type":"TEXT_DELTA","threadId":"thread-1","timestamp":...,"payload":{"content":"..."}}`
  - `data: {"id":"...","type":"RUN_FINISHED","threadId":"thread-1","timestamp":...,"payload":{"threadId":"thread-1","status":"completed","error":null}}`

## 3. Acceptance Criteria (Gherkin / DoD)
- **AC-01**: `POST /api/agent/run` validates `prompt` and `threadId`, defaulting `threadId` to a generated UUID when omitted.
- **AC-02**: Supports both live LLM (`OPENAI_API_KEY`) and deterministic local LangChain4j streaming mode so developers can run the showcase in $< 5$ minutes without paid API keys.
- **AC-03**: Dual-Gate test suite achieves $\ge 85\%$ coverage and $\ge 80\%$ mutation kill score.
