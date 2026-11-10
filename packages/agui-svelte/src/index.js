/**
 * @agui/svelte — Standalone AG-UI SSE stream parser, Protocol Call Taxonomy,
 * Wire Telemetry filters, and Svelte 5 Runes agent controller (`useAgUiAgent`).
 */

export const PROTOCOL_CALL_TAXONOMY = {
	'tool:function': {
		callType: 'tool:function',
		label: 'Local Function',
		family: 'LOCAL',
		iconName: 'Wrench',
		badgeClass: 'badge-local'
	},
	'tool:ui': {
		callType: 'tool:ui',
		label: 'AG-UI Surface',
		family: 'AG-UI',
		iconName: 'LayoutGrid',
		badgeClass: 'badge-ui'
	},
	'mcp:tool': {
		callType: 'mcp:tool',
		label: 'MCP Tool',
		family: 'MCP',
		iconName: 'Hammer',
		badgeClass: 'badge-mcp'
	},
	'mcp:resource': {
		callType: 'mcp:resource',
		label: 'MCP Resource',
		family: 'MCP',
		iconName: 'Database',
		badgeClass: 'badge-mcp'
	},
	'mcp:prompt': {
		callType: 'mcp:prompt',
		label: 'MCP Prompt',
		family: 'MCP',
		iconName: 'FileText',
		badgeClass: 'badge-mcp'
	},
	'mcp:sampling': {
		callType: 'mcp:sampling',
		label: 'MCP Sampling',
		family: 'MCP',
		iconName: 'Cpu',
		badgeClass: 'badge-mcp'
	},
	'a2a:discovery': {
		callType: 'a2a:discovery',
		label: 'A2A Discovery',
		family: 'A2A',
		iconName: 'Compass',
		badgeClass: 'badge-a2a'
	},
	'a2a:task': {
		callType: 'a2a:task',
		label: 'A2A Task',
		family: 'A2A',
		iconName: 'Network',
		badgeClass: 'badge-a2a'
	},
	'a2a:artifact': {
		callType: 'a2a:artifact',
		label: 'A2A Artifact',
		family: 'A2A',
		iconName: 'FileCheck2',
		badgeClass: 'badge-a2a'
	},
	'hitl:approval': {
		callType: 'hitl:approval',
		label: 'HITL Gate',
		family: 'HITL',
		iconName: 'ShieldAlert',
		badgeClass: 'badge-hitl'
	},
	'guardrail:input': {
		callType: 'guardrail:input',
		label: 'Input Guardrail',
		family: 'GUARDRAIL',
		iconName: 'ShieldCheck',
		badgeClass: 'badge-a2a'
	},
	'guardrail:output': {
		callType: 'guardrail:output',
		label: 'Output Guardrail',
		family: 'GUARDRAIL',
		iconName: 'ShieldCheck',
		badgeClass: 'badge-a2a'
	},
	'rag:retrieval': {
		callType: 'rag:retrieval',
		label: 'Cultural RAG',
		family: 'RAG',
		iconName: 'BookOpen',
		badgeClass: 'badge-mcp'
	},
	'agentic:specialist': {
		callType: 'agentic:specialist',
		label: 'Specialist Council',
		family: 'AGENTIC',
		iconName: 'Sparkles',
		badgeClass: 'badge-ui'
	}
};

export function resolveProtocolCallMeta(callType, fallbackProtocol = 'MCP') {
	if (callType && PROTOCOL_CALL_TAXONOMY[callType]) {
		return PROTOCOL_CALL_TAXONOMY[callType];
	}
	const upper = String(fallbackProtocol ?? 'MCP').toUpperCase();
	if (upper.includes('GUARDRAIL')) {
		return PROTOCOL_CALL_TAXONOMY['guardrail:input'];
	}
	if (upper.includes('RAG')) {
		return PROTOCOL_CALL_TAXONOMY['rag:retrieval'];
	}
	if (upper.includes('AGENTIC')) {
		return PROTOCOL_CALL_TAXONOMY['agentic:specialist'];
	}
	if (upper.includes('A2A')) {
		return PROTOCOL_CALL_TAXONOMY['a2a:task'];
	}
	if (upper.includes('HITL')) {
		return PROTOCOL_CALL_TAXONOMY['hitl:approval'];
	}
	if (upper.includes('LOCAL')) {
		return PROTOCOL_CALL_TAXONOMY['tool:function'];
	}
	if (upper.includes('UI')) {
		return PROTOCOL_CALL_TAXONOMY['tool:ui'];
	}
	return PROTOCOL_CALL_TAXONOMY['mcp:tool'];
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
	const normalized = buffer.replace(/\r\n/g, '\n');
	const parts = normalized.split('\n\n');
	const remainder = parts.pop() ?? '';
	const events = [];

	for (const frame of parts) {
		const trimmed = frame.trim();
		if (!trimmed) continue;
		for (const line of trimmed.split('\n')) {
			if (line.startsWith('data:')) {
				const jsonStr = line.replace(/^(?:data:\s*)+/, '').trim();
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
 * Selects or toggles an option inside a turn's StructuredResponseBlock (`single_select` or `multi_select`).
 */
export function selectTurnOption(state, messageId, optionId) {
	if (!state?.messages) return state;
	return {
		...state,
		messages: state.messages.map((msg) => {
			if (msg.id !== messageId || !msg.interaction || msg.interaction.submitted) {
				return msg;
			}
			const isMulti = msg.interaction.mode === 'multi_select';
			const current = msg.interaction.selectedValues ?? [];
			if (isMulti) {
				const exists = current.includes(optionId);
				const nextValues = exists
					? current.filter((v) => v !== optionId)
					: [...current, optionId];
				return {
					...msg,
					interaction: {
						...msg.interaction,
						selectedValues: nextValues
					}
				};
			}
			return {
				...msg,
				interaction: {
					...msg.interaction,
					selectedValues: [optionId],
					submitted: true
				}
			};
		})
	};
}

/**
 * Locks a `multi_select` interaction block after the user confirms their selection.
 */
export function lockTurnInteraction(state, messageId) {
	if (!state?.messages) return state;
	return {
		...state,
		messages: state.messages.map((msg) =>
			msg.id === messageId && msg.interaction
				? {
						...msg,
						interaction: {
							...msg.interaction,
							submitted: true
						}
					}
				: msg
		)
	};
}

/**
 * Filters protocol inspector events by protocol family ('ALL', 'AG-UI', 'MCP', 'A2A', 'CLIENT').
 *
 * @param {Array<any>} events
 * @param {string} filter
 */
export function filterInspectorEvents(events, filter = 'ALL') {
	if (!Array.isArray(events) || filter === 'ALL') {
		return events ?? [];
	}
	if (filter === 'CLIENT') {
		return events.filter((e) => e.direction === 'client');
	}
	if (filter === 'MCP') {
		return events.filter((e) => e.type === 'TOOL_CALL' || e.type === 'TOOL_RESULT');
	}
	if (filter === 'A2A') {
		return events.filter((e) => e.type === 'STEP_STARTED' || e.type === 'STEP_FINISHED');
	}
	return events.filter(
		(e) =>
			e.type !== 'TOOL_CALL' &&
			e.type !== 'TOOL_RESULT' &&
			e.type !== 'STEP_STARTED' &&
			e.type !== 'STEP_FINISHED'
	);
}

/**
 * Records an outbound client-to-server WebSocket command frame in the Wire Telemetry log.
 */
export function recordClientOutboundEvent(state, commandType, payload = {}) {
	if (!state) return state;
	const evt = {
		id: `ws-up-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
		type: commandType,
		direction: 'client',
		payload
	};
	return {
		...state,
		events: [...(state.events ?? []), evt]
	};
}

function defaultInitialAgentState(threadId = 'thread-agui-1') {
	return {
		threadId,
		isStreaming: false,
		streamBuffer: '',
		messages: [],
		events: []
	};
}

function defaultAgentReducer(state, event) {
	if (!event || !event.type) return state;
	const tagged = event.direction ? event : { ...event, direction: 'server' };
	const events = [...(state.events ?? []), tagged];
	if (event.type === 'RUN_STARTED') {
		return { ...state, isStreaming: true, streamBuffer: '', events };
	}
	if (event.type === 'TEXT_DELTA') {
		const chunk = event.payload?.content ?? event.payload?.delta ?? '';
		return { ...state, streamBuffer: (state.streamBuffer ?? '') + chunk, events };
	}
	if (event.type === 'RUN_FINISHED') {
		const reply = (state.streamBuffer ?? '').trim();
		const nextMessages = reply
			? [
					...(state.messages ?? []),
					{ id: `msg-${event.id ?? Date.now()}`, role: 'assistant', status: 'completed', content: reply }
				]
			: state.messages ?? [];
		return { ...state, isStreaming: false, streamBuffer: '', messages: nextMessages, events };
	}
	return { ...state, events };
}

/**
 * Svelte 5 Runes & Node-compatible AG-UI reactive agent controller composable.
 */
export function useAgUiAgent(options = {}) {
	const threadId = options.threadId ?? 'thread-agui-1';
	const endpoint = options.endpoint ?? '/agui/stream';
	const fetchImpl = options.fetchImpl ?? globalThis.fetch;
	const factory = options.createInitialState ?? defaultInitialAgentState;
	const reducer = options.reducer ?? defaultAgentReducer;
	let currentState = options.initialState ?? factory(threadId);
	let sseRemainder = '';
	const listeners = new Set();

	function notify() {
		for (const listener of listeners) {
			listener(currentState);
		}
	}

	function setState(nextState) {
		currentState = nextState;
		notify();
		return currentState;
	}

	return {
		get state() {
			return currentState;
		},
		getState() {
			return currentState;
		},
		subscribe(listener) {
			if (typeof listener === 'function') {
				listeners.add(listener);
				listener(currentState);
				return () => listeners.delete(listener);
			}
			return () => {};
		},
		dispatch(event) {
			return setState(reducer(currentState, event));
		},
		ingestSseChunk(chunk) {
			const { events, remainder } = parseSseChunk(sseRemainder + (chunk ?? ''));
			sseRemainder = remainder;
			let next = currentState;
			for (const evt of events) {
				next = reducer(next, evt);
			}
			return setState(next);
		},
		async sendPrompt(prompt, extraPayload = {}) {
			if (!fetchImpl) {
				throw new Error('fetch implementation is required for sendPrompt');
			}
			const response = await fetchImpl(endpoint, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
				body: JSON.stringify({
					threadId: currentState?.threadId ?? threadId,
					message: prompt,
					...extraPayload
				})
			});
			if (!response.ok || !response.body) {
				throw new Error(`AG-UI stream request failed with status ${response.status}`);
			}
			const reader = response.body.getReader();
			const decoder = new TextDecoder();
			while (true) {
				const { value, done } = await reader.read();
				if (done) break;
				this.ingestSseChunk(decoder.decode(value, { stream: true }));
			}
			return currentState;
		},
		recordOutbound(commandType, payload = {}) {
			return setState(recordClientOutboundEvent(currentState, commandType, payload));
		},
		selectOption(messageId, optionId) {
			return setState(selectTurnOption(currentState, messageId, optionId));
		},
		lockInteraction(messageId) {
			return setState(lockTurnInteraction(currentState, messageId));
		},
		reset(nextThreadId = currentState?.threadId ?? threadId) {
			sseRemainder = '';
			return setState(factory(nextThreadId));
		}
	};
}
