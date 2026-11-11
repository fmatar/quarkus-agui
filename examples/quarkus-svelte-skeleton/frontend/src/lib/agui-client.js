/**
 * Canonical AG-UI Workbench Adapter, Envelope Builders & Tri-Protocol (AG-UI + MCP + A2A)
 * State Reducer powering the Nomad Cultural Concierge & AG-UI Svelte 5 Component Kit.
 * Consumes headless `@ag-ui/client` + `@ag-ui/core` primitives from `@agui/svelte`.
 */

import {
	AGUI_ACTIVITY_TYPES,
	EventType,
	HttpAgent,
	PROTOCOL_CALL_TAXONOMY,
	WebSocketAgent,
	applyJsonPatch,
	buildPlaceMapLinks,
	createActivityRegistry,
	filterInspectorEvents,
	formatCoordinatesLabel,
	lockTurnInteraction,
	parseSseChunk,
	randomUUID,
	recordClientOutboundEvent,
	resolveEventProtocolMeta,
	resolveProtocolCallMeta,
	sanitizeExternalUrl,
	selectTurnOption,
	upsertActivityList,
	useAgUiAgent as createBaseAgUiAgent
} from '@agui/svelte';

export {
	AGUI_ACTIVITY_TYPES,
	EventType,
	HttpAgent,
	PROTOCOL_CALL_TAXONOMY,
	WebSocketAgent,
	applyJsonPatch,
	buildPlaceMapLinks,
	createActivityRegistry,
	filterInspectorEvents,
	formatCoordinatesLabel,
	lockTurnInteraction,
	parseSseChunk,
	recordClientOutboundEvent,
	resolveEventProtocolMeta,
	resolveProtocolCallMeta,
	sanitizeExternalUrl,
	selectTurnOption,
	upsertActivityList
};

export const NOMAD_DESTINATIONS = [
	{
		id: 'beirut',
		city: 'Beirut',
		country: 'Lebanon',
		currency: 'LBP',
		region: 'Levant',
		tagline: 'Levantine Limestone Arcades, Sursock Modernism & Oud Acoustics',
		prompt: 'I want to plan a trip to Beirut, Lebanon'
	},
	{
		id: 'kyoto',
		city: 'Kyoto',
		country: 'Japan',
		currency: 'JPY',
		region: 'East Asia',
		tagline: 'Arashiyama Moss Gardens, Gion Machiya & Koto Acoustics',
		prompt: 'I want to plan a trip to Kyoto, Japan'
	},
	{
		id: 'lisbon',
		city: 'Lisbon',
		country: 'Portugal',
		currency: 'EUR',
		region: 'Atlantic',
		tagline: 'Alfama Azulejo Terraces, Miradouros & Acoustic Fado',
		prompt: 'I want to plan a trip to Lisbon, Portugal'
	},
	{
		id: 'reykjavik',
		city: 'Reykjavik',
		country: 'Iceland',
		currency: 'ISK',
		region: 'Sub-Arctic',
		tagline: 'Harpa Basalt Geometry, Geothermal Lagoons & Nordic Strings',
		prompt: 'I want to plan a trip to Reykjavik, Iceland'
	}
];

export const SLASH_COMMANDS = [
	{
		command: '/beirut',
		label: 'Beirut Trip (Interactive Slot Fill)',
		description: 'Start planning Beirut — Nomad asks for duration, budget & focus first',
		protocol: 'Slot Elicitation',
		prompt: 'I want to plan a trip to Beirut, Lebanon'
	},
	{
		command: '/kyoto',
		label: 'Kyoto Complete Dossier',
		description: 'Full 4-night Kyoto architecture & koto music run with $2,400 USD',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 4-night architecture and koto music trip in Kyoto with $2,400 USD'
	},
	{
		command: '/lisbon',
		label: 'Lisbon Complete Dossier',
		description: 'Full 5-night Lisbon Fado, azulejo & culinary run with €2,200 EUR',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 5-night Fado, azulejo and culinary trip in Lisbon with €2,200 EUR'
	},
	{
		command: '/reykjavik',
		label: 'Reykjavik Complete Dossier',
		description: 'Full 4-night Reykjavik architecture, nature & music run with $2,800 USD',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 4-night architecture, nature and music trip in Reykjavik with $2,800 USD'
	},
	{
		command: '/media',
		label: 'Destination Photography & Audio Preview',
		description: 'Fetch Wikipedia landmark photography & 30s Apple iTunes local soundtrack',
		protocol: 'MCP Media',
		prompt: 'Play local Fado music from Lisbon'
	},
	{
		command: '/choices',
		label: 'Interactive Multi-Select Choices',
		description: 'Compare and select multiple neighborhood experiences',
		protocol: 'AG-UI ChoiceGroup',
		prompt: 'Give me 4 multi_select evening options in Beirut to pick from'
	},
	{
		command: '/mcp',
		label: 'Live Weather Check',
		description: 'Query live Open-Meteo geocoding & forecast via quarkus-mcp-server',
		protocol: 'MCP',
		prompt: 'What is the weather in Oaxaca right now?'
	},
	{
		command: '/a2a',
		label: 'Travel Advisory Verification',
		description: 'Run live A2A REST Countries v5 advisory check via api.restcountries.com',
		protocol: 'A2A',
		prompt: 'Check the travel advisory for Beirut'
	},
	{
		command: '/hitl',
		label: 'Sanctuary Reservation Hold',
		description: 'Request traveler authorization for a >$1,500 sanctuary suite hold',
		protocol: 'HITL Gate',
		prompt: 'Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)'
	}
];

export const SCENARIO_PRESETS = [
	{
		id: 'nomad-paris-slots',
		label: 'Paris (Slot Fill)',
		protocol: 'Zero Defaults',
		prompt: 'I want a trip in Paris'
	},
	{
		id: 'nomad-kyoto',
		label: 'Kyoto Full Run',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 4-night architecture and koto music trip in Kyoto with $2,400 USD'
	},
	{
		id: 'nomad-lisbon',
		label: 'Lisbon Full Run',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 5-night Fado, azulejo and culinary trip in Lisbon with €2,200 EUR'
	},
	{
		id: 'nomad-advisory',
		label: 'Check Advisory',
		protocol: 'A2A Health',
		prompt: 'Check the travel advisory for Beirut'
	},
	{
		id: 'nomad-hitl',
		label: 'Reserve Sanctuary',
		protocol: 'HITL Approval',
		prompt: 'Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)'
	}
];

const CLEARED_TRIP_DESTINATION = {
	activeCity: null,
	country: null,
	currency: null,
	exchangeRate: null,
	temperatureC: null,
	windSpeedKph: null,
	latitude: null,
	longitude: null,
	weatherCondition: null,
	stopTitle: null,
	allocatedUsd: null,
	heroImageUrl: null,
	soundtrack: null
};

const ACTIVITY_TO_SECTION_KEY = Object.freeze({
	[AGUI_ACTIVITY_TYPES.WEATHER]: 'weather',
	[AGUI_ACTIVITY_TYPES.MAP_PLACES]: 'mapPlaces',
	[AGUI_ACTIVITY_TYPES.SECURITY_ADVISORY]: 'securityAdvisory',
	[AGUI_ACTIVITY_TYPES.FX_BUDGET]: 'fxBudget',
	[AGUI_ACTIVITY_TYPES.CULTURE_AUDIO]: 'cultureAudio'
});

function createEmptyActiveTurn() {
	return {
		reasoning: null,
		timeline: [],
		activities: [],
		media: [],
		interaction: null,
		sources: [],
		followUps: []
	};
}

export function createInitialWorkbenchState(threadId = 'thread-nomad-1') {
	const initialDossier = {
		uri: 'itinerary://active-trip',
		nomadTrip: {
			uri: 'itinerary://active-trip',
			...CLEARED_TRIP_DESTINATION,
			nights: null,
			budgetUsd: null,
			homeCurrency: null
		},
		preferences: {
			uri: 'traveler://preferences',
			travelerName: null,
			style: null,
			dietary: null,
			musicTaste: null,
			pace: null,
			homeCurrency: null
		},
		reportSections: {}
	};
	return {
		threadId,
		transportMode: 'ws',
		wsConnected: false,
		memoryTurns: 0,
		isStreaming: false,
		statusText: 'Ready — Nomad Travel Concierge (Zero Assumed Defaults)',
		streamBuffer: '',
		pendingApproval: null,
		activeTurn: createEmptyActiveTurn(),
		tripDossierState: initialDossier,
		clusterState: initialDossier,
		messages: [createWelcomeAssistantMessage()],
		mcpCalls: [],
		a2aSteps: [],
		events: []
	};
}

function createWelcomeAssistantMessage() {
	return {
		id: 'msg-welcome',
		role: 'assistant',
		status: 'completed',
		content:
			'### Welcome to Nomad — Conversational Travel & Local Culture Concierge\n\nTell me **where you want to travel**, ask a focused question (live weather, currency conversion, regional music, or travel advisories), or pick a destination below.\n\n- **Zero Assumed Defaults**: When planning a trip, Nomad never assumes 3 or 4 days or a preset budget—it asks for **duration**, **budget & currency**, and **travel focus** first.\n- **Quarkus LangChain4j `@McpToolBox("nomad")` (`:8081/mcp`) + Official `A2A` (`:8082`)**: Live Open-Meteo weather, Wikipedia & iTunes 30s audio, Frankfurter ECB FX, and REST Countries v5 (`api.restcountries.com`).\n- **MicroProfile SmallRye Health (`/q/health/ready`)**: Live readiness across MCP clients, A2A upstreams, and Ollama is surfaced transparently in the Top-Bar Health Pill, Execution Spine, and chat.',
		reasoning: null,
		timeline: [],
		mcpCalls: [],
		a2aSteps: [],
		activities: [],
		media: [],
		interaction: {
			id: 'welcome-city-picker',
			mode: 'single_select',
			prompt: 'Choose a destination to start planning (Nomad will ask for your trip details next):',
			selectedValues: [],
			submitted: false,
			options: [
				{
					id: 'pick-beirut',
					label: 'Beirut, Lebanon',
					description: 'Levantine limestone arcades, Sursock modernism, coastal terroir & Oud acoustics',
					badge: 'LBP • Mediterranean',
					prompt: 'I want to plan a trip to Beirut, Lebanon'
				},
				{
					id: 'pick-kyoto',
					label: 'Kyoto, Japan',
					description: 'Arashiyama cedar pavilions, Gion machiya joinery, kaiseki & Koto strings',
					badge: 'JPY • East Asia',
					prompt: 'I want to plan a trip to Kyoto, Japan'
				},
				{
					id: 'pick-lisbon',
					label: 'Lisbon, Portugal',
					description: 'Alfama azulejo terraces, Siza Vieira concrete geometry & acoustic Fado',
					badge: 'EUR • Atlantic',
					prompt: 'I want to plan a trip to Lisbon, Portugal'
				},
				{
					id: 'pick-reykjavik',
					label: 'Reykjavik, Iceland',
					description: 'Harpa basalt facades, geothermal lagoons & Nordic chamber strings',
					badge: 'ISK • Sub-Arctic',
					prompt: 'I want to plan a trip to Reykjavik, Iceland'
				}
			]
		},
		sources: [],
		followUps: [
			'I want a trip in Paris',
			'What is the weather in Beirut right now?',
			'Check the travel advisory for Beirut',
			'Plan a 4-night architecture and koto music trip in Kyoto with $2,400 USD'
		]
	};
}

/**
 * Builds a canonical AG-UI `RunAgentInput` envelope for HTTP SSE or WebSocket execution.
 */
export function buildRunAgentInput(options = {}) {
	const { threadId, runId, prompt, state, llmMode, messages } = options;
	const resolvedMessages =
		messages ?? (prompt ? [{ id: `msg-user-${randomUUID()}`, role: 'user', content: prompt }] : []);
	return {
		threadId: threadId || 'thread-nomad-1',
		runId: runId || `run-${randomUUID()}`,
		state: state ?? {},
		messages: resolvedMessages,
		tools: [],
		context: [],
		forwardedProps: llmMode ? { llmMode } : {}
	};
}

/**
 * Builds a canonical AG-UI `RunAgentInput` containing a `resume` decision for a staged HITL `Interrupt`.
 */
export function buildHitlResumeInput(options = {}) {
	const { threadId, runId, interruptId, approved, modifiedArgs, reason, state, llmMode } = options;
	const resolvedReason = resolveResumeReason(approved, reason);
	const payload = buildResumePayload(approved, resolvedReason, modifiedArgs);
	return {
		threadId: threadId || 'thread-nomad-1',
		runId: runId || `resume-${randomUUID()}`,
		state: state ?? {},
		messages: [],
		tools: [],
		context: [],
		forwardedProps: llmMode ? { llmMode } : {},
		resume: [
			{
				interruptId: interruptId || 'hitl-approval',
				status: approved ? 'resolved' : 'cancelled',
				payload
			}
		]
	};
}

function resolveResumeReason(approved, reason) {
	if (reason) return reason;
	return approved ? 'Traveler authorized sanctuary hold' : 'Traveler declined reservation hold';
}

function buildResumePayload(approved, reason, modifiedArgs) {
	if (approved && modifiedArgs && typeof modifiedArgs === 'object') {
		return { reason, ...modifiedArgs };
	}
	return { reason };
}

function escapeJsonPointerSegment(segment) {
	return String(segment).replace(/~/g, '~0').replace(/\//g, '~1');
}

/**
 * Converts a root key-value delta object into RFC 6902 JSON Patch `add` operations.
 */
export function toRootStateDeltaOps(fields) {
	if (!fields || typeof fields !== 'object' || Array.isArray(fields)) return [];
	return Object.entries(fields).map(([key, value]) => ({
		op: 'add',
		path: `/${escapeJsonPointerSegment(key)}`,
		value
	}));
}

function tryParseJson(raw) {
	if (typeof raw !== 'string') return raw;
	try {
		return JSON.parse(raw);
	} catch {
		return raw;
	}
}

function uniqueTimelineId(timeline, preferredId, fallbackIndex) {
	const base = preferredId || `tl-${fallbackIndex}`;
	return timeline.some((item) => item.id === base) ? `${base}-${fallbackIndex}` : base;
}

function resolveCurrentDossier(state) {
	return state.tripDossierState ?? state.clusterState;
}

function handleWsConnected(state, event, nextEvents) {
	const meta = event.rawEvent ?? event;
	const incomingTurns = meta.memoryTurns ?? state.memoryTurns ?? 0;
	const currentDossier = resolveCurrentDossier(state);
	const incomingDossier = meta.tripDossierState ?? meta.clusterState ?? currentDossier;
	const keepIncoming = incomingTurns > 0 || Boolean(currentDossier?.nomadTrip?.activeCity);
	const resolvedDossier = keepIncoming ? incomingDossier : currentDossier;
	return {
		...state,
		wsConnected: true,
		transportMode: 'ws',
		memoryTurns: incomingTurns,
		tripDossierState: resolvedDossier,
		clusterState: resolvedDossier,
		statusText: 'WebSocket Full-Duplex Connected (/api/agent/ws)',
		events: nextEvents
	};
}

function handleRunStarted(state, event, nextEvents) {
	const meta = event.rawEvent ?? {};
	return {
		...state,
		isStreaming: true,
		streamBuffer: '',
		pendingApproval: null,
		mcpCalls: [],
		a2aSteps: [],
		activeTurn: createEmptyActiveTurn(),
		statusText: `Run started (${meta.llmMode ?? 'ollama:llama3.2:latest'})`,
		events: nextEvents
	};
}

function handleStateSnapshot(state, event, nextEvents) {
	const snap = event.snapshot ?? event.rawEvent ?? {};
	return applyMergedStateFields(state, snap, nextEvents);
}

function handleStateDelta(state, event, nextEvents) {
	const base = {
		tripDossierState: resolveCurrentDossier(state),
		clusterState: resolveCurrentDossier(state),
		memoryTurns: state.memoryTurns ?? 0,
		pendingApproval: state.pendingApproval
	};
	const patched = Array.isArray(event.delta) ? applyJsonPatch(base, event.delta) : base;
	const merged = { ...patched, ...(event.rawEvent ?? {}) };
	return applyMergedStateFields(state, merged, nextEvents);
}

function applyMergedStateFields(state, fields, nextEvents) {
	if (fields.status === 'memory_reset') {
		return applyMemoryResetState(state, nextEvents);
	}
	if (fields.status === 'params_updated') {
		return applyParamsUpdatedState(state, fields, nextEvents);
	}
	return applyTurnStateDelta(state, fields, nextEvents);
}

function applyMemoryResetState(state, nextEvents) {
	const initial = createInitialWorkbenchState(state.threadId);
	return {
		...state,
		tripDossierState: initial.tripDossierState,
		clusterState: initial.clusterState,
		memoryTurns: 0,
		isStreaming: false,
		pendingApproval: null,
		streamBuffer: '',
		activeTurn: createEmptyActiveTurn(),
		messages: initial.messages,
		statusText: 'Conversational memory & traveler profile reset',
		events: nextEvents
	};
}

function applyParamsUpdatedState(state, fields, nextEvents) {
	const currentDossier = resolveCurrentDossier(state);
	const incomingCluster = fields.tripDossierState ?? fields.clusterState ?? currentDossier;
	const incomingTurns = fields.memoryTurns ?? state.memoryTurns ?? 0;
	const keepCity = Boolean(currentDossier?.nomadTrip?.activeCity) || incomingTurns > 0;
	const nextDossier = keepCity
		? incomingCluster
		: {
				...incomingCluster,
				nomadTrip: { ...(incomingCluster?.nomadTrip ?? {}), ...CLEARED_TRIP_DESTINATION }
			};
	return {
		...state,
		tripDossierState: nextDossier,
		clusterState: nextDossier,
		memoryTurns: incomingTurns,
		statusText: 'Trip parameters updated via live WebSocket (<50ms)',
		events: nextEvents
	};
}

function applyTurnStateDelta(state, fields, nextEvents) {
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();
	const currentDossier = resolveCurrentDossier(state);
	const incomingCluster = fields.tripDossierState ?? fields.clusterState ?? currentDossier;
	const nextClusterState = fields.awaitingDestination
		? {
				...incomingCluster,
				nomadTrip: { ...(incomingCluster?.nomadTrip ?? {}), ...CLEARED_TRIP_DESTINATION, nights: null },
				reportSections: {}
			}
		: incomingCluster;
	const nextPending =
		'pendingApproval' in fields ? fields.pendingApproval : state.pendingApproval;
	return {
		...state,
		tripDossierState: nextClusterState,
		clusterState: nextClusterState,
		memoryTurns: fields.memoryTurns ?? state.memoryTurns ?? 0,
		pendingApproval: nextPending,
		activeTurn: mergeTurnArtifacts(currentTurn, fields),
		events: nextEvents
	};
}

function mergeTurnArtifacts(currentTurn, fields) {
	const nextInteraction = fields.interaction
		? {
				...fields.interaction,
				selectedValues: fields.interaction.selectedValues ?? [],
				submitted: Boolean(fields.interaction.submitted)
			}
		: currentTurn.interaction;
	return {
		...currentTurn,
		media: nonEmptyArrayOrFallback(fields.media, currentTurn.media),
		interaction: nextInteraction,
		sources: nonEmptyArrayOrFallback(fields.sources, currentTurn.sources),
		followUps: nonEmptyArrayOrFallback(fields.followUps, currentTurn.followUps)
	};
}

function nonEmptyArrayOrFallback(candidate, fallback) {
	return Array.isArray(candidate) && candidate.length > 0 ? candidate : fallback;
}

function handleReasoningContent(state, event, nextEvents) {
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();
	const chunk = event.delta ?? '';
	const prevThought = currentTurn.reasoning?.thought ?? '';
	const coalesced = coalesceDeltaEvents(state.events, nextEvents, EventType.REASONING_MESSAGE_CONTENT, chunk);
	return {
		...state,
		statusText: 'Synthesizing multi-protocol plan...',
		activeTurn: {
			...currentTurn,
			reasoning: {
				thought: prevThought + chunk,
				durationMs: currentTurn.reasoning?.durationMs ?? 950
			}
		},
		events: coalesced
	};
}

function coalesceDeltaEvents(prevEvents, fallbackEvents, eventType, chunk) {
	const prev = prevEvents.at(-1);
	if (!prev || prev.type !== eventType) {
		return fallbackEvents;
	}
	return [
		...prevEvents.slice(0, -1),
		{
			...prev,
			delta: (prev.delta ?? '') + chunk,
			chunks: (prev.chunks ?? 1) + 1
		}
	];
}

function resolveToolCallStartInfo(event) {
	const meta = event.rawEvent ?? {};
	const callId = event.toolCallId ?? meta.callId;
	const tool = event.toolCallName ?? meta.tool ?? 'mcp-tool';
	const requiresApproval = Boolean(meta.requiresApproval || meta.callType === 'hitl:approval');
	return {
		callId,
		tool,
		args: meta.args ?? {},
		requiresApproval,
		callType: resolveToolCallType(meta.callType, requiresApproval),
		server: meta.server ?? 'nomad-mcp-concierge (:8081)',
		status: requiresApproval ? 'awaiting_approval' : 'running'
	};
}

function resolveToolCallType(explicitCallType, requiresApproval) {
	if (explicitCallType) return explicitCallType;
	return requiresApproval ? 'hitl:approval' : 'mcp:tool';
}

function handleToolCallStart(state, event, nextEvents) {
	const currentTurn = resolveActiveTurn(state);
	const info = resolveToolCallStartInfo(event);
	const statusText = info.requiresApproval
		? 'HITL Gate — Awaiting Traveler Approval'
		: `MCP Tool: ${info.tool}`;
	const pendingApproval = info.requiresApproval
		? buildPendingFromArgs(info.callId, info.tool, info.args)
		: state.pendingApproval;

	return {
		...state,
		statusText,
		pendingApproval,
		mcpCalls: [
			...state.mcpCalls,
			{
				callId: info.callId,
				tool: info.tool,
				callType: info.callType,
				server: info.server,
				args: info.args,
				argsBuffer: '',
				status: info.status,
				result: null
			}
		],
		activeTurn: {
			...currentTurn,
			timeline: [
				...currentTurn.timeline,
				{
					id: uniqueTimelineId(currentTurn.timeline, info.callId, nextEvents.length),
					callType: info.callType,
					title: info.tool,
					subtitle: info.server,
					args: info.args,
					argsBuffer: '',
					status: info.status,
					detail: null
				}
			]
		},
		events: nextEvents
	};
}

function resolveActiveTurn(state) {
	return state.activeTurn ?? createEmptyActiveTurn();
}

function buildPendingFromArgs(callId, toolName, args) {
	return {
		interruptId: callId,
		callId,
		toolName,
		city: args?.city ?? null,
		stopTitle: args?.stopTitle ?? null,
		nights: args?.nights ?? null,
		costUsd: args?.costUsd ?? null
	};
}

function handleToolCallArgs(state, event, nextEvents) {
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();
	const delta = event.delta ?? '';
	const mcpCalls = state.mcpCalls.map((c) =>
		c.callId === event.toolCallId ? appendJsonArgs(c, delta) : c
	);
	const timeline = currentTurn.timeline.map((item) =>
		item.id === event.toolCallId ? appendJsonArgs(item, delta) : item
	);
	const matchedCall = mcpCalls.find((c) => c.callId === event.toolCallId);
	const pendingApproval =
		state.pendingApproval?.callId === event.toolCallId && matchedCall?.args
			? buildPendingFromArgs(event.toolCallId, state.pendingApproval.toolName, matchedCall.args)
			: state.pendingApproval;
	return {
		...state,
		pendingApproval,
		mcpCalls,
		activeTurn: { ...currentTurn, timeline },
		events: nextEvents
	};
}

function appendJsonArgs(entry, delta) {
	const argsBuffer = (entry.argsBuffer ?? '') + delta;
	const parsed = tryParseJson(argsBuffer);
	const args = parsed && typeof parsed === 'object' ? parsed : entry.args;
	return { ...entry, argsBuffer, args };
}

function resolveToolCallOutcome(state, event) {
	const meta = event.rawEvent ?? {};
	const callId = event.toolCallId ?? meta.callId;
	const tool = resolveCompletedToolName(state.mcpCalls, callId, meta.tool);
	const result = resolveToolResultObject(event, meta);
	const isError = isErrorPayload(meta) || isErrorPayload(result);
	const errorText = extractErrorDetail(meta, result);
	return {
		callId,
		tool,
		result,
		isError,
		nextStatus: isError ? 'error' : 'completed',
		errorText,
		detail: isError ? errorText : summarizeToolResult(tool, result)
	};
}

function resolveCompletedToolName(mcpCalls, callId, explicitTool) {
	if (explicitTool) return explicitTool;
	const matched = mcpCalls.find((c) => c.callId === callId);
	return matched?.tool ?? 'mcp-tool';
}

function applyToolOutcomeToMcpCalls(mcpCalls, out) {
	return mcpCalls.map((c) =>
		c.callId === out.callId || c.tool === out.tool
			? { ...c, status: out.nextStatus, isError: out.isError, error: out.isError ? out.errorText : null, result: out.result }
			: c
	);
}

function applyToolOutcomeToTimeline(timeline, out) {
	return timeline.map((item) =>
		item.id === out.callId || item.title === out.tool
			? { ...item, status: out.nextStatus, isError: out.isError, result: out.result, detail: out.detail }
			: item
	);
}

function resolveTurnMediaAfterTool(currentMedia, out) {
	if (out.isError || out.tool !== 'discoverLocalCultureAndMedia' || currentMedia.length > 0) {
		return currentMedia;
	}
	const extracted = extractCultureMediaItems(out.result);
	return extracted.length > 0 ? extracted : currentMedia;
}

function handleToolCallResult(state, event, nextEvents) {
	const currentTurn = resolveActiveTurn(state);
	const out = resolveToolCallOutcome(state, event);
	const nextDossier = enrichDossierFromMediaTool(
		resolveCurrentDossier(state),
		out.tool,
		out.result,
		out.isError
	);
	return {
		...state,
		tripDossierState: nextDossier,
		clusterState: nextDossier,
		statusText: out.isError ? `Tool Error: ${out.errorText}` : state.statusText,
		mcpCalls: applyToolOutcomeToMcpCalls(state.mcpCalls, out),
		activeTurn: {
			...currentTurn,
			timeline: applyToolOutcomeToTimeline(currentTurn.timeline, out),
			media: resolveTurnMediaAfterTool(currentTurn.media, out)
		},
		events: nextEvents
	};
}

function resolveToolResultObject(event, meta) {
	if (meta.result !== undefined) return meta.result;
	const parsed = tryParseJson(event.content);
	return parsed && typeof parsed === 'object' ? parsed : { value: parsed };
}

function isMediaToolObject(tool, result, isError) {
	return !isError && tool === 'discoverLocalCultureAndMedia' && Boolean(result && typeof result === 'object');
}

function enrichDossierFromMediaTool(currentDossier, tool, result, isError) {
	if (!isMediaToolObject(tool, result, isError)) {
		return currentDossier;
	}
	const prevTrip = currentDossier?.nomadTrip ?? {};
	return {
		...currentDossier,
		nomadTrip: mergeMediaDossierFields(prevTrip, result)
	};
}

function mergeMediaDossierFields(prevTrip, result) {
	return {
		...prevTrip,
		activeCity: prevTrip.activeCity ?? nonBlankString(result.destination),
		heroImageUrl: nonBlankString(result.heroImageUrl) ?? prevTrip.heroImageUrl ?? null,
		soundtrack: extractDossierSoundtrack(result.audioTrack) ?? prevTrip.soundtrack ?? null
	};
}

function nonBlankString(val) {
	return typeof val === 'string' && val.trim().length > 0 ? val.trim() : null;
}

function extractDossierSoundtrack(track) {
	const previewUrl = nonBlankString(track?.previewUrl);
	if (!previewUrl) return null;
	return {
		trackName: track.trackName ?? 'Regional Track',
		artistName: track.artistName ?? 'Regional Artist',
		previewUrl,
		durationSeconds: 30
	};
}

function handleStepStarted(state, event, nextEvents) {
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();
	const meta = event.rawEvent ?? {};
	const callType = meta.callType ?? 'a2a:task';
	const title = meta.skillId ?? event.stepName ?? 'a2a-skill';
	const agentName = meta.agentName ?? 'Nomad-A2A-Specialist';
	const stepEntry = {
		id: uniqueTimelineId(currentTurn.timeline, meta.stepId ?? event.stepName, nextEvents.length),
		callType,
		title,
		subtitle: `${agentName} (:8082)`,
		status: 'running',
		detail: null
	};
	return {
		...state,
		statusText: `A2A Delegation: ${agentName} (${title})`,
		activeTurn: {
			...currentTurn,
			timeline: [...currentTurn.timeline, stepEntry]
		},
		events: nextEvents
	};
}

function resolveStepFinishedInfo(event, fallbackIndex) {
	const meta = event.rawEvent ?? {};
	const isError = isErrorPayload(meta);
	return {
		meta,
		stepId: pickField(meta.stepId, event.stepName),
		callType: pickField(meta.callType, null, 'a2a:task'),
		title: pickField(meta.skillId, event.stepName, 'a2a-skill'),
		agentName: pickField(meta.agentName, null, 'Nomad-A2A-Specialist'),
		isError,
		nextStatus: isError ? 'error' : 'completed',
		detailText: isError ? extractErrorDetail(meta, meta.report) : meta.summary,
		result: pickField(meta.report, meta.result),
		fallbackIndex
	};
}

function appendA2aStepRecord(a2aSteps, info) {
	const isA2a = info.meta.protocol === 'A2A' || info.callType.startsWith('a2a:');
	if (!isA2a) return a2aSteps;
	return [
		...a2aSteps,
		{
			stepId: info.stepId,
			callType: info.callType,
			agentName: info.agentName,
			skillId: info.title,
			status: info.nextStatus,
			isError: info.isError,
			summary: info.detailText,
			report: info.meta.report ?? {}
		}
	];
}

function handleStepFinished(state, event, nextEvents) {
	const currentTurn = resolveActiveTurn(state);
	const info = resolveStepFinishedInfo(event, nextEvents.length);
	return {
		...state,
		statusText: info.isError ? `Service Error: ${info.detailText}` : state.statusText,
		a2aSteps: appendA2aStepRecord(state.a2aSteps, info),
		activeTurn: {
			...currentTurn,
			timeline: upsertFinishedStepTimeline(currentTurn.timeline, info)
		},
		events: nextEvents
	};
}

function upsertFinishedStepTimeline(timeline, info) {
	const matches = (item) =>
		item.id === info.stepId || (item.title === info.title && item.status === 'running');
	if (timeline.some(matches)) {
		return timeline.map((item) =>
			matches(item)
				? {
						...item,
						callType: info.callType,
						status: info.nextStatus,
						isError: info.isError,
						detail: info.detailText,
						result: info.result
					}
				: item
		);
	}
	return [
		...timeline,
		{
			id: uniqueTimelineId(timeline, info.stepId, info.fallbackIndex),
			callType: info.callType,
			title: info.title,
			subtitle: `${info.agentName} (:8082)`,
			status: info.nextStatus,
			isError: info.isError,
			detail: info.detailText,
			result: info.result
		}
	];
}

function syncDossierFromActivity(currentDossier, event) {
	const sectionKey = ACTIVITY_TO_SECTION_KEY[event.activityType];
	if (!sectionKey || !event.content || typeof event.content !== 'object') {
		return currentDossier;
	}
	const prevTrip = currentDossier?.nomadTrip ?? {};
	const prevSections = currentDossier?.reportSections ?? {};
	const city = nonBlankString(event.content.city) ?? prevTrip.activeCity ?? null;
	const country = nonBlankString(event.content.country) ?? prevTrip.country ?? null;
	return {
		...currentDossier,
		nomadTrip: {
			...prevTrip,
			activeCity: city,
			country
		},
		reportSections: {
			...prevSections,
			[sectionKey]: structuredClone(event.content)
		}
	};
}

function handleActivitySnapshot(state, event, nextEvents) {
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();
	const meta = event.rawEvent ?? {};
	const isAgUiCard = typeof event.activityType === 'string' && event.activityType.startsWith('agui:');
	const isErr = Boolean(event.content?.isError);
	const preferredId = event.messageId || `act-${nextEvents.length}`;
	const entry = {
		id: preferredId,
		callType: meta.callType ?? (isAgUiCard ? 'agui:activity' : 'a2a:artifact'),
		title: meta.artifactName ?? event.activityType ?? 'activity',
		subtitle: meta.agentName ?? (isAgUiCard ? 'AG-UI Activity Surface' : 'Nomad-A2A-Specialist (:8082)'),
		status: isErr ? 'error' : 'completed',
		isError: isErr,
		detail: null,
		result: event.content
	};
	const existingIdx = currentTurn.timeline.findIndex((item) => item.id === preferredId);
	const nextTimeline =
		existingIdx >= 0
			? currentTurn.timeline.map((item, idx) => (idx === existingIdx ? entry : item))
			: [...currentTurn.timeline, entry];
	const nextActivities = isAgUiCard
		? upsertActivityList(currentTurn.activities ?? [], event)
		: (currentTurn.activities ?? []);
	const nextDossier = isAgUiCard
		? syncDossierFromActivity(resolveCurrentDossier(state), event)
		: resolveCurrentDossier(state);
	return {
		...state,
		tripDossierState: nextDossier,
		clusterState: nextDossier,
		activeTurn: {
			...currentTurn,
			timeline: nextTimeline,
			activities: nextActivities
		},
		events: nextEvents
	};
}

function handleTextMessageContent(state, event, nextEvents) {
	const chunk = event.delta ?? '';
	const coalesced = coalesceDeltaEvents(state.events, nextEvents, EventType.TEXT_MESSAGE_CONTENT, chunk);
	return {
		...state,
		streamBuffer: state.streamBuffer + chunk,
		events: coalesced
	};
}

function handleRunFinished(state, event, nextEvents) {
	if (event.outcome?.type === 'interrupt') {
		return finalizeInterruptedRun(state, event, nextEvents);
	}
	return finalizeCompletedRun(state, event, nextEvents);
}

function pickField(primary, secondary, defaultVal = null) {
	if (primary !== undefined && primary !== null) return primary;
	if (secondary !== undefined && secondary !== null) return secondary;
	return defaultVal;
}

function finalizeInterruptedRun(state, event, nextEvents) {
	const firstInterrupt = event.outcome?.interrupts?.[0] ?? {};
	const meta = firstInterrupt.metadata ?? {};
	const fallback = state.pendingApproval ?? {};
	const interruptId = pickField(firstInterrupt.id, meta.callId, fallback.interruptId || 'hitl-approval');
	return {
		...state,
		isStreaming: false,
		pendingApproval: {
			interruptId,
			callId: pickField(meta.callId, firstInterrupt.toolCallId, interruptId),
			toolName: pickField(meta.toolName, fallback.toolName, 'updateTripItinerary'),
			city: pickField(meta.city, fallback.city),
			stopTitle: pickField(meta.stopTitle, fallback.stopTitle),
			nights: pickField(meta.nights, fallback.nights),
			costUsd: pickField(meta.costUsd, fallback.costUsd)
		},
		statusText: 'HITL Gate — Awaiting Traveler Approval',
		events: nextEvents
	};
}

function finalizeCompletedRun(state, event, nextEvents) {
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();
	const res = event.result ?? event.rawEvent ?? {};
	const status = res.status ?? 'completed';
	const finalReply = resolveFinalReplyText(state.streamBuffer, status);
	const assistantMsg = finalReply
		? [buildCompletedAssistantTurn(event, finalReply, currentTurn, state)]
		: [];
	return {
		...state,
		isStreaming: false,
		pendingApproval: null,
		memoryTurns: res.memoryTurns ?? state.memoryTurns ?? 0,
		statusText: `Completed (${status})`,
		streamBuffer: '',
		activeTurn: createEmptyActiveTurn(),
		messages: [...state.messages, ...assistantMsg],
		events: nextEvents
	};
}

function resolveFinalReplyText(streamBuffer, status) {
	const trimmed = streamBuffer.trim();
	if (trimmed) return trimmed;
	return status === 'cancelled'
		? 'Traveler declined sanctuary reservation hold. Run cancelled safely.'
		: '';
}

function buildCompletedAssistantTurn(event, content, currentTurn, state) {
	return {
		id: `msg-${event.runId ?? event.id ?? randomUUID()}`,
		role: 'assistant',
		status: 'completed',
		content,
		reasoning: currentTurn.reasoning,
		timeline: currentTurn.timeline,
		mcpCalls: state.mcpCalls,
		a2aSteps: state.a2aSteps,
		activities: currentTurn.activities ?? [],
		media: currentTurn.media,
		interaction: currentTurn.interaction,
		sources: currentTurn.sources,
		followUps: currentTurn.followUps
	};
}

function handleRunError(state, event, nextEvents) {
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();
	const message = event.message ?? 'Agent run failed';
	const statusText =
		event.code === 'GUARDRAIL_BLOCKED'
			? `Blocked by Guardrail (${event.code})`
			: `Error: ${message}`;
	const errorTurn = buildCompletedAssistantTurn(event, message, currentTurn, state);
	return {
		...state,
		isStreaming: false,
		pendingApproval: null,
		statusText,
		streamBuffer: '',
		activeTurn: createEmptyActiveTurn(),
		messages: [...state.messages, errorTurn],
		events: nextEvents
	};
}

const WORKBENCH_HANDLERS = new Map([
	['WS_CONNECTED', handleWsConnected],
	[EventType.RUN_STARTED, handleRunStarted],
	[EventType.STATE_SNAPSHOT, handleStateSnapshot],
	[EventType.STATE_DELTA, handleStateDelta],
	[EventType.REASONING_MESSAGE_CONTENT, handleReasoningContent],
	[EventType.TOOL_CALL_START, handleToolCallStart],
	[EventType.TOOL_CALL_ARGS, handleToolCallArgs],
	[EventType.TOOL_CALL_RESULT, handleToolCallResult],
	[EventType.STEP_STARTED, handleStepStarted],
	[EventType.STEP_FINISHED, handleStepFinished],
	[EventType.ACTIVITY_SNAPSHOT, handleActivitySnapshot],
	[EventType.TEXT_MESSAGE_CONTENT, handleTextMessageContent],
	[EventType.RUN_FINISHED, handleRunFinished],
	[EventType.RUN_ERROR, handleRunError]
]);

/**
 * Pure reducer updating the 3-Pane Workbench state for each incoming canonical AG-UI `Event`.
 */
export function reduceAgUiEvent(state, event) {
	if (!event || !event.type) return state;
	const taggedEvent = event.direction ? event : { ...event, direction: 'server' };
	const nextEvents = [...state.events, taggedEvent];
	const handler = WORKBENCH_HANDLERS.get(event.type);
	return handler ? handler(state, event, nextEvents) : { ...state, events: nextEvents };
}

function extractCultureMediaItems(result) {
	if (!result || typeof result !== 'object') return [];
	const city = result.destination ?? 'Destination';
	const items = [];
	const heroUrl = nonBlankString(result.heroImageUrl);
	if (heroUrl) {
		items.push({
			type: 'image',
			title: `${city} Cultural & Architectural Plate`,
			subtitle: 'Wikipedia REST API v1 • Verified Landmark Photography',
			url: heroUrl
		});
	}
	const previewUrl = nonBlankString(result.audioTrack?.previewUrl);
	if (previewUrl) {
		items.push({
			type: 'audio',
			title: result.audioTrack.trackName ?? `${city} Acoustic Session`,
			subtitle: result.audioTrack.artistName ?? 'Regional Artist',
			url: previewUrl,
			durationSeconds: result.audioTrack.durationSeconds ?? 30
		});
	}
	return items;
}

function isErrorPayload(payload) {
	if (!payload || typeof payload !== 'object') return false;
	return Boolean(payload.isError || payload.status === 'ERROR');
}

function extractErrorDetail(primary, secondary) {
	return (
		primary?.error ??
		secondary?.error ??
		primary?.summary ??
		'Upstream travel service unavailable'
	);
}

const TOOL_SUMMARY_FORMATTERS = new Map([
	[
		'fetchCityWeather',
		(r) => (r.city ? `${r.city} • ${r.temperatureC}°C (${r.condition})` : '')
	],
	[
		'discoverLocalCultureAndMedia',
		(r) => (r.audioTrack ? `${r.destination} • 30s Preview: ${r.audioTrack.trackName}` : '')
	],
	[
		'updateTripItinerary',
		(r) =>
			r.stopTitle ? `Reserved ${r.stopTitle} (${r.nights} nights • $${r.allocatedUsd} USD)` : ''
	],
	[
		'TravelSafetyAndInjectionGuardrail',
		(r) => r.reason ?? 'OWASP-LLM01 Input Guardrail Verified'
	],
	[
		'CurrencyAndGroundingGuardrail',
		(r) => `Verified ${r.verifiedCity} • ${r.verifiedIsoCurrency} (${r.verifiedTemperatureC}°C)`
	],
	[
		'retrieveCulturalFieldNotes',
		(r) =>
			`${r.chunksRetrieved ?? 3} field notes retrieved (cosine similarity ${r.topSimilarity ?? 0.98})`
	],
	['sampleAcousticMoodProfile', (r) => r.sampledMood ?? 'Sampled acoustic twilight mood'],
	['sampleAcousticMood', (r) => r.sampledMood ?? 'Sampled acoustic twilight mood'],
	[
		'saveTravelerPreferences',
		(r) => (r.travelerName ? `Traveler profile updated: ${r.travelerName}` : '')
	]
]);

function summarizeToolResult(tool, result) {
	if (!result || typeof result !== 'object') return '';
	const formatter = TOOL_SUMMARY_FORMATTERS.get(tool);
	if (formatter) return formatter(result);
	return summarizeGenericToolResult(result);
}

function summarizeGenericToolResult(result) {
	if (result.architecturalHighlight) return `${result.city} — ${result.architecturalHighlight}`;
	if (result.expandedPrompt) return result.expandedPrompt;
	if (result.style) return `${result.style} • ${result.dietary ?? result.pace ?? ''}`;
	return '';
}

const HEALTH_CHECK_LABELS = {
	'MCP clients health check': 'Quarkus LangChain4j MCP Client (nomad)',
	'Quarkus LangChain4j MCP clients': 'Quarkus LangChain4j MCP Client (nomad)',
	'nomad-mcp-upstreams': 'Quarkus MCP Server (:8081/q/health/ready)',
	'nomad-a2a-specialist': 'Quarkus A2A Specialist (:8082/q/health/ready)',
	'nomad-a2a-upstreams': 'Quarkus A2A Specialist Upstreams (:8082)',
	'ollama-chat-model': 'Ollama Local LLM',
	'open-meteo': 'Open-Meteo Weather & Geocoding API',
	wikipedia: 'Wikipedia REST v1 Cultural API',
	'wikipedia-rest': 'Wikipedia REST v1 Cultural API',
	itunes: 'Apple iTunes 30s Acoustic API',
	'itunes-audio': 'Apple iTunes 30s Acoustic API',
	frankfurter: 'Frankfurter ECB FX Reference API',
	'frankfurter-fx': 'Frankfurter ECB FX Reference API',
	restcountries: 'REST Countries v5 Travel Advisory API (api.restcountries.com)',
	'restcountries-advisory': 'REST Countries v5 Travel Advisory API (api.restcountries.com)'
};

function resolveHealthCheckLabel(id, data) {
	if (id === 'ollama-chat-model' && data?.model) {
		return `Ollama Local LLM (${data.model})`;
	}
	return HEALTH_CHECK_LABELS[id] ?? id;
}

function normalizeSmallRyeChecks(checks) {
	const services = [];
	for (const check of checks) {
		if (!check || typeof check !== 'object') continue;
		services.push(...expandSingleHealthCheck(check));
	}
	return services;
}

function expandSingleHealthCheck(check) {
	const id = check.name ?? 'health-check';
	const data = check.data && typeof check.data === 'object' ? check.data : {};
	const { subServices, firstSubError } = extractHealthSubServices(id, data);
	const primaryError = data.error ?? firstSubError ?? findFallbackCheckError(check.status, data);
	return [
		{
			id,
			name: resolveHealthCheckLabel(id, data),
			status: String(check.status ?? 'DOWN').toUpperCase(),
			error: primaryError
		},
		...subServices
	];
}

function extractHealthSubServices(parentId, data) {
	const subServices = [];
	let firstSubError = null;
	for (const [key, val] of Object.entries(data)) {
		if (!key.endsWith('.status')) continue;
		const subId = key.slice(0, -'.status'.length);
		const status = String(val ?? 'DOWN').toUpperCase();
		const error = data[`${subId}.error`] ? String(data[`${subId}.error`]) : null;
		if (status !== 'UP' && !firstSubError && error) firstSubError = error;
		subServices.push({ id: subId, via: parentId, name: resolveHealthCheckLabel(subId, data), status, error });
	}
	return { subServices, firstSubError };
}

function findFallbackCheckError(status, data) {
	if (status === 'UP') return null;
	const entry = Object.entries(data).find(([k, v]) => isFailureDataEntry(k, v));
	return entry ? `${entry[0]}: ${entry[1]}` : null;
}

function isFailureDataEntry(key, val) {
	return (
		!key.endsWith('.status') &&
		!key.endsWith('.error') &&
		key !== 'endpoint' &&
		key !== 'model' &&
		val !== 'OK'
	);
}

/**
 * Summarizes MicroProfile SmallRye Health (`/q/health/ready` `{ status, checks }`) or
 * `/api/agent/health` (`{ status, services }`) payloads for the Top-Bar Health Pill & Popover.
 */
export function summarizeHealthReport(health) {
	const fromChecks = Array.isArray(health?.checks);
	const services = resolveHealthServiceList(health, fromChecks);
	if (!services) {
		return {
			status: 'UNKNOWN',
			label: 'Checking Health...',
			healthyCount: 0,
			totalCount: 0,
			downCount: 0,
			downServices: [],
			services: []
		};
	}
	const downServices = services.filter((s) => s.status !== 'UP');
	const downCount = downServices.length;
	const healthyCount = services.length - downCount;
	const status = computeOverallHealthStatus(health, fromChecks, healthyCount, downCount);
	return {
		status,
		label: formatHealthSummaryLabel(status, downCount),
		healthyCount,
		totalCount: services.length,
		downCount,
		downServices,
		services
	};
}

function resolveHealthServiceList(health, fromChecks) {
	if (fromChecks) return normalizeSmallRyeChecks(health.checks);
	return Array.isArray(health?.services) ? health.services : null;
}

function computeOverallHealthStatus(health, fromChecks, healthyCount, downCount) {
	if (!fromChecks && health?.status) return health.status;
	const reportedUp = String(health?.status ?? 'UP').toUpperCase() === 'UP';
	if (downCount === 0 && (!fromChecks || reportedUp)) return 'UP';
	return healthyCount === 0 ? 'DOWN' : 'DEGRADED';
}

function formatHealthSummaryLabel(status, downCount) {
	if (status === 'UP') return 'All Systems Operational';
	if (status === 'DOWN') return `Offline • ${downCount} Services Down`;
	return `Degraded • ${downCount} ${downCount === 1 ? 'API' : 'APIs'} Down`;
}

/**
 * Mutates local trip nights count in clusterState for bidirectional UI-to-Agent state editing.
 */
export function updateTripNightsInState(clusterState, delta) {
	if (!clusterState?.nomadTrip) return clusterState;
	const baseNights = clusterState.nomadTrip.nights ?? 0;
	const nextNights = Math.max(1, Math.min(30, baseNights + delta));
	return {
		...clusterState,
		nomadTrip: {
			...clusterState.nomadTrip,
			nights: nextNights,
			allocatedUsd:
				clusterState.nomadTrip.budgetUsd != null
					? Math.round(nextNights * 420)
					: clusterState.nomadTrip.allocatedUsd
		}
	};
}

/**
 * Creates a reactive AG-UI Workbench controller pre-configured with Nomad's initial state and reducer.
 */
export function useAgUiAgent(options = {}) {
	return createBaseAgUiAgent({
		createInitialState: createInitialWorkbenchState,
		reducer: reduceAgUiEvent,
		...options
	});
}

/**
 * Derives normalized canonical AG-UI Activity descriptors (`agui:weather`, `agui:map-places`,
 * `agui:security-advisory`, `agui:fx-budget`, `agui:culture-audio`) from the live Trip Dossier state.
 */
export function deriveDossierActivities(dossierState) {
	const trip = dossierState?.nomadTrip ?? {};
	const prefs = dossierState?.preferences ?? {};
	const sections = dossierState?.reportSections ?? {};
	const hasCity = Boolean(trip.activeCity);
	const hasSections = Boolean(sections && Object.keys(sections).length > 0);
	if (!hasCity && !hasSections) {
		return [];
	}

	const city = trip.activeCity ?? sections.weather?.city ?? sections.mapPlaces?.city ?? sections.securityAdvisory?.city ?? 'Destination';
	const country = trip.country ?? sections.weather?.country ?? sections.mapPlaces?.country ?? sections.securityAdvisory?.country ?? '';
	const list = [];

	const weatherContent =
		sections.weather ??
		(trip.temperatureC != null || trip.weatherCondition != null
			? {
					status: 'OK',
					isError: false,
					city,
					country,
					temperatureC: trip.temperatureC,
					windSpeedKph: trip.windSpeedKph ?? null,
					condition: trip.weatherCondition,
					latitude: trip.latitude ?? null,
					longitude: trip.longitude ?? null,
					source: 'api.open-meteo.com'
				}
			: null);
	if (weatherContent) {
		list.push({
			messageId: `dossier-weather-${city}`,
			activityType: AGUI_ACTIVITY_TYPES.WEATHER,
			content: weatherContent
		});
	}

	const mapContent =
		sections.mapPlaces ??
		(hasCity
			? {
					status: 'OK',
					isError: false,
					city,
					country,
					latitude: trip.latitude ?? null,
					longitude: trip.longitude ?? null,
					stopTitle: trip.stopTitle ?? null,
					focus: trip.cadenceFocus ?? 'architecture, local craft & evening acoustics',
					pace: trip.cadencePace ?? 'Unrushed curated walk',
					stops: Array.isArray(trip.places) && trip.places.length > 0
						? trip.places
						: trip.stopTitle
							? [trip.stopTitle]
							: [`${city} Historic Center & Cultural District`],
					wikiSourceUrl: trip.wikiSourceUrl ?? null
				}
			: null);
	if (mapContent) {
		list.push({
			messageId: `dossier-map-${city}`,
			activityType: AGUI_ACTIVITY_TYPES.MAP_PLACES,
			content: mapContent
		});
	}

	const securityContent = sections.securityAdvisory ?? trip.securityAdvisory ?? null;
	if (securityContent) {
		list.push({
			messageId: `dossier-security-${city}`,
			activityType: AGUI_ACTIVITY_TYPES.SECURITY_ADVISORY,
			content: securityContent
		});
	}

	const baseFx = sections.fxBudget ?? null;
	const hasTripFx =
		trip.budgetUsd != null || trip.exchangeRate != null || trip.allocatedUsd != null;
	if (baseFx || hasTripFx) {
		const liveHomeCurrency = prefs.homeCurrency ?? trip.homeCurrency ?? baseFx?.baseCurrency ?? 'USD';
		const liveBudget = trip.budgetUsd ?? baseFx?.budgetUsd ?? null;
		const liveAllocated = trip.allocatedUsd ?? baseFx?.allocatedUsd ?? null;
		const liveRate = trip.exchangeRate ?? baseFx?.exchangeRate ?? null;
		const liveConverted =
			liveBudget != null && liveRate != null
				? Math.round(liveBudget * liveRate * 100) / 100
				: (baseFx?.convertedTotal ?? null);
		list.push({
			messageId: `dossier-fx-${city}`,
			activityType: AGUI_ACTIVITY_TYPES.FX_BUDGET,
			content: {
				status: baseFx?.status ?? 'OK',
				isError: Boolean(baseFx?.isError),
				error: baseFx?.error ?? null,
				city,
				country,
				nights: trip.nights ?? baseFx?.nights ?? null,
				stopTitle: trip.stopTitle ?? baseFx?.stopTitle ?? null,
				baseCurrency: liveHomeCurrency,
				targetCurrency: trip.currency ?? baseFx?.targetCurrency ?? null,
				exchangeRate: liveRate,
				budgetUsd: liveBudget,
				allocatedUsd: liveAllocated,
				convertedTotal: liveConverted,
				hitlThresholdUsd: baseFx?.hitlThresholdUsd ?? 1500,
				source: baseFx?.source ?? 'api.frankfurter.dev'
			}
		});
	}

	const baseCulture = sections.cultureAudio ?? null;
	const hasTripCulture = Boolean(trip.heroImageUrl || trip.soundtrack || trip.culturalSummary);
	if (baseCulture || hasTripCulture) {
		list.push({
			messageId: `dossier-culture-${city}`,
			activityType: AGUI_ACTIVITY_TYPES.CULTURE_AUDIO,
			content: {
				status: baseCulture?.status ?? 'OK',
				isError: Boolean(baseCulture?.isError),
				error: baseCulture?.error ?? null,
				city,
				country,
				culturalSummary: trip.culturalSummary ?? baseCulture?.culturalSummary ?? null,
				heroImageUrl: trip.heroImageUrl ?? baseCulture?.heroImageUrl ?? null,
				wikiSourceUrl: trip.wikiSourceUrl ?? baseCulture?.wikiSourceUrl ?? null,
				acousticMood: trip.acousticMood ?? baseCulture?.acousticMood ?? null,
				listeningWindow: trip.listeningWindow ?? baseCulture?.listeningWindow ?? null,
				soundtrack: trip.soundtrack ?? baseCulture?.soundtrack ?? null
			}
		});
	}

	return list;
}

/**
 * Formats the active Trip Dossier, Traveler Profile (`traveler://preferences`), all AG-UI report sections,
 * and latest itinerary into a downloadable Markdown document (`.md`).
 */
export function formatTripDossierMarkdown(clusterState, messages = []) {
	const trip = clusterState?.nomadTrip ?? {};
	const prefs = clusterState?.preferences ?? {};
	const activities = deriveDossierActivities(clusterState);
	const lines = [
		...buildTripOverviewMarkdownLines(trip, prefs),
		...buildTravelerProfileMarkdownLines(prefs),
		...buildActivitiesMarkdownLines(activities)
	];
	const latestBriefing = findLatestAssistantContent(messages);
	if (latestBriefing) {
		lines.push(`## Curated Concierge Briefing`, ``, latestBriefing, ``);
	}
	return lines.join('\n');
}

function buildActivitiesMarkdownLines(activities) {
	if (!Array.isArray(activities) || activities.length === 0) {
		return [];
	}
	const out = [];
	for (const act of activities) {
		const c = act.content ?? {};
		if (act.activityType === AGUI_ACTIVITY_TYPES.WEATHER) {
			const coords = formatCoordinatesLabel(c.latitude, c.longitude);
			out.push(
				`## Weather & Microclimate (\`agui:weather\`)`,
				`- **Forecast**: ${formatTemperatureLabel(c.temperatureC)} — ${pickField(c.condition, null, 'Pending')}`,
				`- **Wind Speed**: ${c.windSpeedKph != null ? `${c.windSpeedKph} km/h` : '--'}`,
				`- **Coordinates**: ${coords ?? 'Not resolved'}`,
				`- **Source**: ${pickField(c.source, null, 'api.open-meteo.com')}`,
				``
			);
		} else if (act.activityType === AGUI_ACTIVITY_TYPES.MAP_PLACES) {
			out.push(`## Places & Map Links (\`agui:map-places\`)`);
			const stops = Array.isArray(c.stops) ? c.stops : [];
			for (const stop of stops) {
				const safeStopLabel = String(stop ?? '').replace(/[[\]()]/g, ' ').trim() || 'Stop';
				const links = buildPlaceMapLinks({
					name: stop,
					city: c.city,
					country: c.country,
					latitude: c.latitude,
					longitude: c.longitude,
					wikiUrl: c.wikiSourceUrl
				});
				out.push(
					`- **${safeStopLabel}** — [Google Maps](${links.googleMapsUrl}) • [Apple Maps](${links.appleMapsUrl}) • [OpenStreetMap](${links.openStreetMapUrl}) • [Wikipedia](${links.wikipediaUrl})`
				);
			}
			out.push(``);
		} else if (act.activityType === AGUI_ACTIVITY_TYPES.SECURITY_ADVISORY) {
			out.push(
				`## Security & Travel Advisory (\`agui:security-advisory\`)`,
				`- **Verification Status**: ${pickField(c.status, null, 'PENDING')} — ${pickField(c.advisoryStatus, null, '')}`,
				`- **Emergency Contacts**: ${pickField(c.emergencyNumbers, null, '112 Universal GSM')}`,
				`- **Country Metadata**: Capital ${pickField(c.capital, null, '--')} • Timezone ${pickField(c.timezone, null, '--')} • Currency ${pickField(c.currencyCode, null, '--')}`,
				`- **HITL Safeguard Threshold**: >= $${c.hitlThresholdUsd ?? 1500} USD`
			);
			if (Array.isArray(c.safetyHighlights)) {
				for (const item of c.safetyHighlights) {
					out.push(`  - ${item}`);
				}
			}
			out.push(``);
		} else if (act.activityType === AGUI_ACTIVITY_TYPES.FX_BUDGET) {
			out.push(
				`## FX & Budget Audit (\`agui:fx-budget\`)`,
				`- **Exchange Rate**: ${c.exchangeRate != null ? `1 ${c.baseCurrency ?? 'USD'} = ${c.exchangeRate} ${c.targetCurrency ?? ''}` : 'Awaiting FX audit'}`,
				`- **Converted Total**: ${c.convertedTotal != null ? `${c.convertedTotal.toLocaleString()} ${c.targetCurrency ?? ''}` : '--'}`,
				`- **Allocation**: ${formatUsdAmount(c.allocatedUsd, 'Not allocated')} / ${formatUsdAmount(c.budgetUsd, 'Not set')} (${formatNightsLabel(c.nights)})`,
				``
			);
		} else if (act.activityType === AGUI_ACTIVITY_TYPES.CULTURE_AUDIO) {
			out.push(
				`## Cultural Notes & Acoustic Preview (\`agui:culture-audio\`)`,
				`- **Cultural Summary**: ${pickField(c.culturalSummary, null, 'Awaiting Wikipedia summary')}`,
				`- **Acoustic Mood**: ${pickField(c.acousticMood, null, '--')} (${pickField(c.listeningWindow, null, '--')})`,
				`- **Soundtrack**: ${formatSoundtrackLine(c.soundtrack)}`,
				``
			);
		}
	}
	return out;
}

function buildTripOverviewMarkdownLines(trip, prefs) {
	const city = pickField(trip.activeCity, null, 'Destination Pending');
	const country = pickField(trip.country, null, 'Unspecified');
	const homeCurrency = pickField(prefs.homeCurrency, trip.homeCurrency, 'Not set');
	return [
		`# Nomad Cultural & Architectural Dossier — ${city}, ${country}`,
		``,
		`- **Sanctuary Allocation**: ${pickField(trip.stopTitle, null, 'Not reserved yet')} (${formatNightsLabel(trip.nights)})`,
		`- **Live Open-Meteo Telemetry**: ${formatTemperatureLabel(trip.temperatureC)} — ${pickField(trip.weatherCondition, null, 'Awaiting weather check')}`,
		`- **Frankfurter FX Settlement**: ${formatUsdAmount(trip.allocatedUsd, 'Not allocated')} / ${formatUsdAmount(trip.budgetUsd, 'Not set')} (${formatLocalFxSettlement(trip)})`,
		`- **Home Currency**: ${homeCurrency}`,
		`- **Soundtrack Preview**: ${formatSoundtrackLine(trip.soundtrack)}`,
		``
	];
}

function formatSoundtrackLine(soundtrack) {
	const track = pickField(soundtrack?.trackName, null, 'Not loaded');
	const artist = pickField(soundtrack?.artistName, null, '--');
	return `${track} — ${artist}`;
}

function buildTravelerProfileMarkdownLines(prefs) {
	return [
		`## Traveler Profile (\`traveler://preferences\`)`,
		`- **Architectural Style**: ${pickField(prefs.style, null, 'Not specified yet')}`,
		`- **Dietary Terroir**: ${pickField(prefs.dietary, null, 'Not specified yet')}`,
		`- **Acoustic & Music Taste**: ${pickField(prefs.musicTaste, null, 'Not specified yet')}`,
		`- **Cadence & Pace**: ${pickField(prefs.pace, null, 'Not specified yet')}`,
		``
	];
}

function formatNightsLabel(nights) {
	return nights != null ? `${nights} Nights` : 'Duration Not Set';
}

function formatTemperatureLabel(temperatureC) {
	return temperatureC != null ? `${temperatureC}°C` : '--°C';
}

function formatUsdAmount(amount, fallback) {
	return amount != null ? `$${amount}` : fallback;
}

function formatLocalFxSettlement(trip) {
	if (trip.exchangeRate == null || trip.allocatedUsd == null) {
		return 'Awaiting FX audit';
	}
	const currency = trip.currency ?? '--';
	const converted = Math.round(trip.allocatedUsd * trip.exchangeRate).toLocaleString();
	return `${converted} ${currency} @ 1 = ${trip.exchangeRate} ${currency}`;
}

function findLatestAssistantContent(messages) {
	const list = Array.isArray(messages) ? messages : [];
	for (let i = list.length - 1; i >= 0; i--) {
		if (list[i]?.role === 'assistant' && list[i]?.content) {
			return list[i].content.trim();
		}
	}
	return null;
}
