export type AgUiEventType =
	| 'WS_CONNECTED'
	| 'RUN_STARTED'
	| 'STATE_SNAPSHOT'
	| 'STATE_DELTA'
	| 'STATUS'
	| 'TOOL_CALL'
	| 'TOOL_RESULT'
	| 'STEP_STARTED'
	| 'STEP_FINISHED'
	| 'TEXT_DELTA'
	| 'RUN_FINISHED'
	| string;

export interface AgUiEvent<TPayload = Record<string, any>> {
	id?: string;
	type: AgUiEventType;
	threadId?: string;
	timestamp?: number;
	direction?: 'server' | 'client';
	payload?: TPayload;
}

export interface AgUiProtocolCallMeta {
	callType: string;
	label: string;
	family: 'LOCAL' | 'AG-UI' | 'MCP' | 'A2A' | 'HITL' | 'GUARDRAIL' | 'RAG' | 'AGENTIC';
	iconName: string;
	badgeClass: string;
}

export interface AgUiSseParseResult {
	events: AgUiEvent[];
	remainder: string;
}

export interface UseAgUiAgentOptions<TState = any> {
	threadId?: string;
	initialState?: TState;
	createInitialState?: (threadId: string) => TState;
	reducer?: (state: TState, event: AgUiEvent) => TState;
}

export interface AgUiAgentController<TState = any> {
	readonly state: TState;
	getState(): TState;
	subscribe(listener: (state: TState) => void): () => void;
	dispatch(event: AgUiEvent): TState;
	ingestSseChunk(chunk: string): TState;
	recordOutbound(commandType: string, payload?: Record<string, any>): TState;
	selectOption(messageId: string, optionId: string): TState;
	lockInteraction(messageId: string): TState;
	reset(threadId?: string): TState;
}

export const PROTOCOL_CALL_TAXONOMY: Record<string, AgUiProtocolCallMeta>;

export function resolveProtocolCallMeta(
	callType?: string | null,
	fallbackProtocol?: string
): AgUiProtocolCallMeta;

export function parseSseChunk(buffer: string): AgUiSseParseResult;

export function selectTurnOption<TState extends { messages?: any[] }>(
	state: TState,
	messageId: string,
	optionId: string
): TState;

export function lockTurnInteraction<TState extends { messages?: any[] }>(
	state: TState,
	messageId: string
): TState;

export function filterInspectorEvents(events: AgUiEvent[], filter?: string): AgUiEvent[];

export function recordClientOutboundEvent<TState extends { events?: AgUiEvent[] }>(
	state: TState,
	commandType: string,
	payload?: Record<string, any>
): TState;

export function useAgUiAgent<TState = any>(
	options?: UseAgUiAgentOptions<TState>
): AgUiAgentController<TState>;
