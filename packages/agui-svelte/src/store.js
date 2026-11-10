import { randomUUID } from '@ag-ui/client';
import { EventType } from '@ag-ui/core';
import { applyJsonPatch } from './json-patch.js';
import { resolveEventProtocolMeta } from './taxonomy.js';

export function parseSseChunk(buffer) {
	if (!buffer) {
		return { events: [], remainder: '' };
	}
	const parts = buffer.replace(/\r\n/g, '\n').split('\n\n');
	const remainder = parts.pop();
	const events = [];
	for (const frame of parts) {
		extractFrameEvents(frame, events);
	}
	return { events, remainder };
}

function extractFrameEvents(frame, sink) {
	const trimmed = frame.trim();
	if (!trimmed) return;
	for (const line of trimmed.split('\n')) {
		parseDataLine(line, sink);
	}
}

function parseDataLine(line, sink) {
	if (!line.startsWith('data:')) return;
	const jsonStr = line.replace(/^(?:data:\s*)+/, '').trim();
	if (!jsonStr) return;
	try {
		sink.push(JSON.parse(jsonStr));
	} catch {
		// ignore malformed partial line
	}
}

export function selectTurnOption(state, messageId, optionId) {
	if (!state?.messages) return state;
	return {
		...state,
		messages: state.messages.map((msg) => toggleMessageOption(msg, messageId, optionId))
	};
}

function toggleMessageOption(msg, messageId, optionId) {
	if (msg.id !== messageId || !msg.interaction || msg.interaction.submitted) {
		return msg;
	}
	if (msg.interaction.mode === 'multi_select') {
		return toggleMultiSelectValue(msg, optionId);
	}
	return {
		...msg,
		interaction: { ...msg.interaction, selectedValues: [optionId], submitted: true }
	};
}

function toggleMultiSelectValue(msg, optionId) {
	const current = msg.interaction.selectedValues ?? [];
	const nextValues = current.includes(optionId)
		? current.filter((v) => v !== optionId)
		: [...current, optionId];
	return { ...msg, interaction: { ...msg.interaction, selectedValues: nextValues } };
}

export function lockTurnInteraction(state, messageId) {
	if (!state?.messages) return state;
	return {
		...state,
		messages: state.messages.map((msg) =>
			msg.id === messageId && msg.interaction
				? { ...msg, interaction: { ...msg.interaction, submitted: true } }
				: msg
		)
	};
}

export function recordClientOutboundEvent(state, commandType, payload = {}) {
	if (!state) return state;
	const evt = {
		id: `ws-up-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
		type: commandType,
		direction: 'client',
		payload
	};
	return { ...state, events: [...state.events, evt] };
}

export function createDefaultAgentState(threadId = 'thread-agui-1', initialAgentState = {}) {
	return {
		threadId,
		isStreaming: false,
		streamBuffer: '',
		reasoningBuffer: '',
		messages: [],
		toolCalls: [],
		steps: [],
		activities: {},
		agentState: initialAgentState,
		pendingInterrupts: [],
		lastError: null,
		events: []
	};
}

function tryParseJson(raw) {
	if (typeof raw !== 'string') return raw;
	try {
		return JSON.parse(raw);
	} catch {
		return raw;
	}
}

function appendToolArgs(tc, delta) {
	const argsBuffer = tc.argsBuffer + delta;
	const parsed = tryParseJson(argsBuffer);
	const args = parsed && typeof parsed === 'object' ? parsed : tc.args;
	return { ...tc, argsBuffer, args };
}

function extractTextChunk(event) {
	if (typeof event.delta === 'string') return event.delta;
	return event.payload?.content ?? event.payload?.delta ?? '';
}

function buildAssistantMessage(event, reply) {
	const suffix = event.runId ?? event.id ?? Date.now();
	return { id: `msg-${suffix}`, role: 'assistant', status: 'completed', content: reply };
}

const EVENT_HANDLERS = new Map([
	[
		EventType.RUN_STARTED,
		(state, _event, events) => ({
			...state,
			isStreaming: true,
			streamBuffer: '',
			reasoningBuffer: '',
			lastError: null,
			events
		})
	],
	[
		EventType.TEXT_MESSAGE_CONTENT,
		(state, event, events) => ({
			...state,
			streamBuffer: state.streamBuffer + extractTextChunk(event),
			events
		})
	],
	[
		'TEXT_DELTA',
		(state, event, events) => ({
			...state,
			streamBuffer: state.streamBuffer + extractTextChunk(event),
			events
		})
	],
	[
		EventType.REASONING_MESSAGE_CONTENT,
		(state, event, events) => ({
			...state,
			reasoningBuffer: state.reasoningBuffer + event.delta,
			events
		})
	],
	[
		EventType.STATE_SNAPSHOT,
		(state, event, events) => ({
			...state,
			agentState: event.snapshot ?? event.payload ?? {},
			events
		})
	],
	[
		EventType.STATE_DELTA,
		(state, event, events) => ({
			...state,
			agentState: Array.isArray(event.delta)
				? applyJsonPatch(state.agentState, event.delta)
				: state.agentState,
			events
		})
	],
	[
		EventType.TOOL_CALL_START,
		(state, event, events) => ({
			...state,
			toolCalls: [
				...state.toolCalls,
				{
					toolCallId: event.toolCallId,
					toolCallName: event.toolCallName,
					argsBuffer: '',
					args: {},
					result: null,
					status: 'running',
					rawEvent: event.rawEvent ?? null,
					meta: resolveEventProtocolMeta(event)
				}
			],
			events
		})
	],
	[
		EventType.TOOL_CALL_ARGS,
		(state, event, events) => ({
			...state,
			toolCalls: state.toolCalls.map((tc) =>
				tc.toolCallId === event.toolCallId ? appendToolArgs(tc, event.delta) : tc
			),
			events
		})
	],
	[
		EventType.TOOL_CALL_RESULT,
		(state, event, events) => ({
			...state,
			toolCalls: state.toolCalls.map((tc) =>
				tc.toolCallId === event.toolCallId
					? { ...tc, status: 'completed', result: tryParseJson(event.content) }
					: tc
			),
			events
		})
	],
	[
		EventType.STEP_STARTED,
		(state, event, events) => ({
			...state,
			steps: [
				...state.steps,
				{
					stepName: event.stepName,
					status: 'running',
					rawEvent: event.rawEvent ?? null,
					meta: resolveEventProtocolMeta(event)
				}
			],
			events
		})
	],
	[
		EventType.STEP_FINISHED,
		(state, event, events) => ({
			...state,
			steps: state.steps.map((s) =>
				s.stepName === event.stepName && s.status === 'running' ? { ...s, status: 'completed' } : s
			),
			events
		})
	],
	[
		EventType.ACTIVITY_SNAPSHOT,
		(state, event, events) => ({
			...state,
			activities: {
				...state.activities,
				[event.messageId]: {
					messageId: event.messageId,
					activityType: event.activityType,
					content: event.content,
					rawEvent: event.rawEvent ?? null
				}
			},
			events
		})
	],
	[
		EventType.ACTIVITY_DELTA,
		(state, event, events) => {
			const prev = state.activities[event.messageId] ?? {
				messageId: event.messageId,
				activityType: event.activityType,
				content: {}
			};
			return {
				...state,
				activities: {
					...state.activities,
					[event.messageId]: { ...prev, content: applyJsonPatch(prev.content, event.patch) }
				},
				events
			};
		}
	],
	[
		EventType.RUN_ERROR,
		(state, event, events) => ({
			...state,
			isStreaming: false,
			lastError: { message: event.message, code: event.code ?? null },
			events
		})
	],
	[
		EventType.RUN_FINISHED,
		(state, event, events) => {
			const interrupts = event.outcome?.type === 'interrupt' ? event.outcome.interrupts : [];
			const reply = state.streamBuffer.trim();
			const messages = reply
				? [...state.messages, buildAssistantMessage(event, reply)]
				: state.messages;
			return {
				...state,
				isStreaming: false,
				streamBuffer: '',
				pendingInterrupts: interrupts,
				messages,
				events
			};
		}
	]
]);

export function reduceCanonicalAgUiEvent(state, event) {
	if (!event || !event.type) return state;
	const tagged = event.direction ? event : { ...event, direction: 'server' };
	const events = [...state.events, tagged];
	const handler = EVENT_HANDLERS.get(event.type);
	return handler ? handler(state, event, events) : { ...state, events };
}

function normalizeStoreConfig(options = {}) {
	const sdkAgent = options.agent ?? null;
	const threadId = resolveInitialThreadId(options, sdkAgent);
	const factory = resolveStateFactory(options, sdkAgent);
	return {
		sdkAgent,
		threadId,
		endpoint: options.endpoint ?? '/agui/stream',
		fetchImpl: options.fetchImpl === undefined ? globalThis.fetch : options.fetchImpl,
		factory,
		reducer: options.reducer ?? reduceCanonicalAgUiEvent,
		initialState: options.initialState ?? factory(threadId)
	};
}

function resolveInitialThreadId(options, sdkAgent) {
	return options.threadId ?? sdkAgent?.threadId ?? 'thread-agui-1';
}

function resolveStateFactory(options, sdkAgent) {
	return options.createInitialState ?? ((tid) => createDefaultAgentState(tid, sdkAgent?.state ?? {}));
}

class AgUiStoreController {
	#sdkAgent;
	#endpoint;
	#fetchImpl;
	#factory;
	#reducer;
	#currentState;
	#sseRemainder = '';
	#listeners = new Set();

	constructor(options = {}) {
		const cfg = normalizeStoreConfig(options);
		this.#sdkAgent = cfg.sdkAgent;
		this.#endpoint = cfg.endpoint;
		this.#fetchImpl = cfg.fetchImpl;
		this.#factory = cfg.factory;
		this.#reducer = cfg.reducer;
		this.#currentState = cfg.initialState;
	}

	get state() {
		return this.#currentState;
	}

	get agent() {
		return this.#sdkAgent;
	}

	getState() {
		return this.#currentState;
	}

	subscribe(listener) {
		if (typeof listener !== 'function') return () => {};
		this.#listeners.add(listener);
		listener(this.#currentState);
		return () => this.#listeners.delete(listener);
	}

	dispatch(event) {
		return this.#setState(this.#reducer(this.#currentState, event));
	}

	ingestSseChunk(chunk) {
		const { events, remainder } = parseSseChunk(this.#sseRemainder + (chunk ?? ''));
		this.#sseRemainder = remainder;
		let next = this.#currentState;
		for (const evt of events) {
			next = this.#reducer(next, evt);
		}
		return this.#setState(next);
	}

	async runAgent(prompt = null, parameters = {}) {
		if (!this.#sdkAgent) {
			return this.sendPrompt(prompt ?? '', parameters);
		}
		this.#appendPromptIfPresent(prompt);
		await this.#sdkAgent.runAgent(parameters, {
			onEvent: ({ event }) => {
				this.dispatch(event);
			}
		});
		return this.#currentState;
	}

	resumeInterrupt(interruptId, status = 'resolved', payload = undefined, parameters = {}) {
		const entry = payload !== undefined ? { interruptId, status, payload } : { interruptId, status };
		return this.runAgent(null, { ...parameters, resume: [entry] });
	}

	abortRun() {
		this.#sdkAgent?.abortRun();
		return this.#setState({ ...this.#currentState, isStreaming: false });
	}

	async sendPrompt(prompt, extraPayload = {}) {
		if (!this.#fetchImpl) {
			throw new Error('fetch implementation is required for sendPrompt');
		}
		const response = await this.#fetchImpl(this.#endpoint, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
			body: JSON.stringify({
				threadId: this.#currentState.threadId,
				message: prompt,
				...extraPayload
			})
		});
		return this.#consumeSseStream(response);
	}

	recordOutbound(commandType, payload = {}) {
		return this.#setState(recordClientOutboundEvent(this.#currentState, commandType, payload));
	}

	selectOption(messageId, optionId) {
		return this.#setState(selectTurnOption(this.#currentState, messageId, optionId));
	}

	lockInteraction(messageId) {
		return this.#setState(lockTurnInteraction(this.#currentState, messageId));
	}

	reset(nextThreadId = this.#currentState.threadId) {
		this.#sseRemainder = '';
		if (this.#sdkAgent) {
			this.#sdkAgent.threadId = nextThreadId;
			this.#sdkAgent.setMessages([]);
		}
		return this.#setState(this.#factory(nextThreadId));
	}

	#setState(nextState) {
		this.#currentState = nextState;
		for (const listener of this.#listeners) {
			listener(this.#currentState);
		}
		return this.#currentState;
	}

	#appendPromptIfPresent(prompt) {
		if (typeof prompt === 'string' && prompt.length > 0) {
			const userMessage = { id: `msg-user-${randomUUID()}`, role: 'user', content: prompt };
			this.#sdkAgent.addMessage(userMessage);
			this.#setState({ ...this.#currentState, messages: [...this.#currentState.messages, userMessage] });
		}
	}

	async #consumeSseStream(response) {
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
		return this.#currentState;
	}
}

export function createAgUiStore(options = {}) {
	return new AgUiStoreController(options);
}

export const useAgUiAgent = createAgUiStore;
