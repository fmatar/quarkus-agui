import {
	AbstractAgent,
	type AgentConfig,
	type AgentSubscriber,
	HttpAgent,
	type HttpAgentConfig,
	type RunAgentParameters,
	type RunAgentResult,
	buildResumeArray,
	compactEvents,
	getRunOutcome,
	isInterruptExpired,
	isRecognizedEvent,
	randomUUID
} from '@ag-ui/client';
import {
	type BaseEvent,
	type Event,
	EventType,
	type Interrupt,
	type JsonPatchOperation,
	type Message,
	type ResumeStatus,
	type RunAgentInput,
	type State
} from '@ag-ui/core';
import { Observable } from 'rxjs';

export {
	AbstractAgent,
	type AgentConfig,
	type AgentSubscriber,
	type BaseEvent,
	type Event,
	EventType,
	HttpAgent,
	type HttpAgentConfig,
	type Interrupt,
	type JsonPatchOperation,
	type Message,
	type ResumeStatus,
	type RunAgentInput,
	type RunAgentParameters,
	type RunAgentResult,
	type State,
	buildResumeArray,
	compactEvents,
	getRunOutcome,
	isInterruptExpired,
	isRecognizedEvent,
	randomUUID
};

export interface AgUiProtocolCallMeta {
	callType: string;
	label: string;
	family: 'LOCAL' | 'AG-UI' | 'MCP' | 'A2A' | 'HITL' | 'GUARDRAIL' | 'RAG' | 'AGENTIC';
	iconName: string;
	badgeClass: string;
}

export interface AgUiResolvedEventMeta extends AgUiProtocolCallMeta {
	protocol: string;
	server: string | null;
	agentName: string | null;
	skillId: string | null;
}

export interface AgUiSseParseResult {
	events: any[];
	remainder: string;
}

export interface WebSocketAgentConfig extends AgentConfig {
	url: string;
	protocols?: string | string[];
	inactivityTimeoutMs?: number;
	webSocketFactory?: (url: string, protocols?: string | string[]) => any;
	onControlEvent?: (frame: Record<string, any>) => void;
	onSpontaneousEvent?: (event: BaseEvent) => void;
	onConnectionChange?: (connected: boolean) => void;
}

export declare class WebSocketAgent extends AbstractAgent {
	url: string;
	protocols?: string | string[];
	inactivityTimeoutMs: number;
	webSocketFactory: (url: string, protocols?: string | string[]) => any;
	onControlEvent: ((frame: Record<string, any>) => void) | null;
	onSpontaneousEvent: ((event: BaseEvent) => void) | null;
	onConnectionChange: ((connected: boolean) => void) | null;
	constructor(config: WebSocketAgentConfig);
	readonly isConnected: boolean;
	connectSocket(): any;
	disconnectSocket(): void;
	sendPing(): boolean;
	sendControlFrame(type: string, payload?: Record<string, any>): boolean;
	run(input: RunAgentInput): Observable<BaseEvent>;
	abortRun(): void;
	clone(): WebSocketAgent;
}

export interface AgUiDefaultStoreState<TAgentState = Record<string, any>> {
	threadId: string;
	isStreaming: boolean;
	streamBuffer: string;
	reasoningBuffer: string;
	messages: any[];
	toolCalls: any[];
	steps: any[];
	activities: Record<string, any>;
	agentState: TAgentState;
	pendingInterrupts: Interrupt[];
	lastError: { message: string; code: string | null } | null;
	events: any[];
}

export interface UseAgUiAgentOptions<TState = AgUiDefaultStoreState> {
	agent?: AbstractAgent | null;
	threadId?: string;
	endpoint?: string;
	fetchImpl?: typeof fetch;
	initialState?: TState;
	createInitialState?: (threadId: string) => TState;
	reducer?: (state: TState, event: any) => TState;
}

export interface AgUiAgentController<TState = AgUiDefaultStoreState> {
	readonly state: TState;
	readonly agent: AbstractAgent | null;
	getState(): TState;
	subscribe(listener: (state: TState) => void): () => void;
	dispatch(event: any): TState;
	ingestSseChunk(chunk: string): TState;
	runAgent(prompt?: string | null, parameters?: RunAgentParameters): Promise<TState>;
	resumeInterrupt(
		interruptId: string,
		status?: ResumeStatus,
		payload?: unknown,
		parameters?: RunAgentParameters
	): Promise<TState>;
	abortRun(): TState;
	sendPrompt(prompt: string, extraPayload?: Record<string, any>): Promise<TState>;
	recordOutbound(commandType: string, payload?: Record<string, any>): TState;
	selectOption(messageId: string, optionId: string): TState;
	lockInteraction(messageId: string): TState;
	reset(threadId?: string): TState;
}

export declare const PROTOCOL_CALL_TAXONOMY: Record<string, AgUiProtocolCallMeta>;

export declare function unescapeJsonPointerToken(token: string): string;
export declare function applyJsonPatchOperation<TState = any>(
	state: TState,
	operation: JsonPatchOperation
): TState;
export declare function applyJsonPatch<TState = any>(
	state: TState,
	operations: JsonPatchOperation[] | null | undefined
): TState;

export declare function resolveProtocolCallMeta(
	callTypeOrEvent?: string | Record<string, any> | null,
	fallbackProtocol?: string
): AgUiProtocolCallMeta;

export declare function resolveEventProtocolMeta(
	event: Record<string, any> | null | undefined
): AgUiResolvedEventMeta;

export declare function filterInspectorEvents(events: any[], filter?: string): any[];

export declare function parseSseChunk(buffer: string): AgUiSseParseResult;

export declare function selectTurnOption<TState extends { messages?: any[] }>(
	state: TState,
	messageId: string,
	optionId: string
): TState;

export declare function lockTurnInteraction<TState extends { messages?: any[] }>(
	state: TState,
	messageId: string
): TState;

export declare function recordClientOutboundEvent<TState extends { events?: any[] }>(
	state: TState,
	commandType: string,
	payload?: Record<string, any>
): TState;

export declare function createDefaultAgentState<TAgentState = Record<string, any>>(
	threadId?: string,
	initialAgentState?: TAgentState
): AgUiDefaultStoreState<TAgentState>;

export declare function reduceCanonicalAgUiEvent<TState = AgUiDefaultStoreState>(
	state: TState,
	event: any
): TState;

export declare function createAgUiStore<TState = AgUiDefaultStoreState>(
	options?: UseAgUiAgentOptions<TState>
): AgUiAgentController<TState>;

export declare function useAgUiAgent<TState = AgUiDefaultStoreState>(
	options?: UseAgUiAgentOptions<TState>
): AgUiAgentController<TState>;

export declare const AGUI_ACTIVITY_TYPES: Readonly<{
	WEATHER: 'agui:weather';
	MAP_PLACES: 'agui:map-places';
	SECURITY_ADVISORY: 'agui:security-advisory';
	FX_BUDGET: 'agui:fx-budget';
	CULTURE_AUDIO: 'agui:culture-audio';
}>;

export interface AgUiPlaceMapLinkOptions {
	name?: string;
	city?: string;
	country?: string;
	latitude?: number | null;
	longitude?: number | null;
	wikiUrl?: string;
	zoom?: number;
}

export interface AgUiPlaceMapLinks {
	searchQuery: string;
	coordinatesLabel: string | null;
	coordinatesRaw: string | null;
	googleMapsUrl: string;
	appleMapsUrl: string;
	openStreetMapUrl: string;
	openStreetMapEmbedUrl: string | null;
	wikipediaUrl: string;
}

export interface AgUiActivityRegistry<TRenderer = any> {
	register(activityType: string, renderer: TRenderer): this;
	has(activityType: string): boolean;
	resolve(activityTypeOrEvent?: string | { activityType?: string } | null): TRenderer | null;
	listTypes(): string[];
}

export interface AgUiActivityEntry<TContent = Record<string, any>> {
	messageId: string;
	activityType: string;
	content: TContent;
	replace: boolean;
	timestamp: number | null;
	rawEvent: any;
}

export declare function sanitizeExternalUrl(
	rawUrl?: unknown,
	fallback?: string | null
): string | null;

export declare function formatCoordinatesLabel(
	latitude?: number | null,
	longitude?: number | null
): string | null;

export declare function buildPlaceMapLinks(
	options?: AgUiPlaceMapLinkOptions
): AgUiPlaceMapLinks;

export declare function createActivityRegistry<TRenderer = any>(
	initialEntries?: Record<string, TRenderer>
): AgUiActivityRegistry<TRenderer>;

export declare function upsertActivityList(
	existingList?: AgUiActivityEntry[],
	event?: Record<string, any> | null
): AgUiActivityEntry[];
