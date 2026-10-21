/**
 * Pure AG-UI SSE parser & Tri-Protocol (AG-UI + MCP + A2A) state reducer.
 */

export const SCENARIO_PRESETS = [
	{
		id: 'mcp-health',
		label: '1. MCP Cluster Health',
		protocol: 'MCP (:8081)',
		prompt: 'Inspect cluster health across all services'
	},
	{
		id: 'a2a-audit',
		label: '2. A2A Architecture Audit',
		protocol: 'A2A (:8082)',
		prompt: 'Audit Clean Architecture concentric rings and security posture'
	},
	{
		id: 'scale-safe',
		label: '3. Auto-Scale (4 Replicas)',
		protocol: 'MCP + A2A',
		prompt: 'Scale deployment order-processor to 4 replicas and plan incident remediation'
	},
	{
		id: 'tri-protocol-logs',
		label: '4. Logs & Security Posture',
		protocol: 'AG-UI + MCP + A2A',
		prompt: 'Query service logs for payment-gateway and assess security posture'
	}
];

export function createInitialWorkbenchState(threadId = 'thread-opspilot-1') {
	return {
		threadId,
		isStreaming: false,
		statusText: 'Idle — Ready',
		streamBuffer: '',
		messages: [
			{
				id: 'msg-welcome',
				role: 'assistant',
				content:
					'OpsPilot Orchestrator (:8080) connected to quarkus-mcp-cluster-ops (:8081) and Sentinel-A2A-Auditor (:8082). Choose a scenario or enter a prompt.',
				mcpCalls: [],
				a2aSteps: []
			}
		],
		mcpCalls: [],
		a2aSteps: [],
		events: []
	};
}

/**
 * Splits a raw SSE text buffer on double-newline frame boundaries (`\n\n`)
 * and parses each `data: <json>` line.
 *
 * @param {string} buffer
 * @returns {{ events: Array<any>, remainder: string }}
 */
export function parseSseChunk(buffer) {
	if (!buffer) {
		return { events: [], remainder: '' };
	}
	const parts = buffer.split('\n\n');
	const remainder = parts.pop() ?? '';
	const events = [];

	for (const frame of parts) {
		const trimmed = frame.trim();
		if (!trimmed) continue;
		for (const line of trimmed.split('\n')) {
			if (line.startsWith('data:')) {
				const jsonStr = line.slice(5).trim();
				if (!jsonStr) continue;
				try {
					events.push(JSON.parse(jsonStr));
				} catch {
					// ignore malformed partial line
				}
			}
		}
	}

	return { events, remainder };
}

/**
 * Pure reducer updating the 3-Pane Workbench state for each incoming AgUiEvent.
 *
 * @param {ReturnType<typeof createInitialWorkbenchState>} state
 * @param {any} event
 */
export function reduceAgUiEvent(state, event) {
	if (!event || !event.type) return state;

	const nextEvents = [...state.events, event];
	const payload = event.payload ?? {};

	switch (event.type) {
		case 'RUN_STARTED':
			return {
				...state,
				isStreaming: true,
				streamBuffer: '',
				mcpCalls: [],
				a2aSteps: [],
				statusText: `Run started (${payload.llmMode ?? 'deterministic'})`,
				events: nextEvents
			};

		case 'STATUS':
			return {
				...state,
				statusText: payload.message ?? payload.phase ?? 'Thinking...',
				events: nextEvents
			};

		case 'TOOL_CALL':
			return {
				...state,
				statusText: `MCP Tool: ${payload.tool}`,
				mcpCalls: [
					...state.mcpCalls,
					{
						callId: payload.callId,
						tool: payload.tool,
						server: payload.server ?? 'quarkus-mcp-cluster-ops (:8081)',
						args: payload.args ?? {},
						status: 'running',
						result: null
					}
				],
				events: nextEvents
			};

		case 'TOOL_RESULT':
			return {
				...state,
				mcpCalls: state.mcpCalls.map((c) =>
					c.callId === payload.callId || c.tool === payload.tool
						? { ...c, status: 'completed', result: payload.result }
						: c
				),
				events: nextEvents
			};

		case 'STEP_STARTED':
			return {
				...state,
				statusText: `A2A Delegation: ${payload.agentName} (${payload.skillId})`,
				events: nextEvents
			};

		case 'STEP_FINISHED':
			return {
				...state,
				a2aSteps: [
					...state.a2aSteps,
					{
						stepId: payload.stepId,
						agentName: payload.agentName ?? 'Sentinel-A2A-Auditor',
						skillId: payload.skillId,
						summary: payload.summary,
						report: payload.report ?? {}
					}
				],
				events: nextEvents
			};

		case 'TEXT_DELTA':
			return {
				...state,
				streamBuffer: state.streamBuffer + (payload.content ?? ''),
				events: nextEvents
			};

		case 'RUN_FINISHED': {
			const finalReply = state.streamBuffer.trim();
			const assistantMsg = finalReply
				? [
						{
							id: `msg-${event.id ?? Date.now()}`,
							role: 'assistant',
							content: finalReply,
							mcpCalls: state.mcpCalls,
							a2aSteps: state.a2aSteps
						}
					]
				: [];
			return {
				...state,
				isStreaming: false,
				statusText: `Completed (${payload.status ?? 'completed'})`,
				streamBuffer: '',
				messages: [...state.messages, ...assistantMsg],
				events: nextEvents
			};
		}

		default:
			return {
				...state,
				events: nextEvents
			};
	}
}
