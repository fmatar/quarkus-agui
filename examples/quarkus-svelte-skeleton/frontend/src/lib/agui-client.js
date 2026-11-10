/**
 * Pure AG-UI SSE parser, Protocol Call Taxonomy & Tri-Protocol (AG-UI + MCP + A2A) state reducer
 * powering the Nomad Cultural Concierge & AG-UI Svelte 5 Component Kit.
 * Consumes core protocol primitives from the standalone `packages/agui-svelte` (`@agui/svelte`) library.
 */

import {
	PROTOCOL_CALL_TAXONOMY,
	filterInspectorEvents,
	lockTurnInteraction,
	parseSseChunk,
	recordClientOutboundEvent,
	resolveProtocolCallMeta,
	selectTurnOption,
	useAgUiAgent as createBaseAgUiAgent
} from '../../../../../packages/agui-svelte/src/index.js';

export {
	PROTOCOL_CALL_TAXONOMY,
	filterInspectorEvents,
	lockTurnInteraction,
	parseSseChunk,
	recordClientOutboundEvent,
	resolveProtocolCallMeta,
	selectTurnOption
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

function createEmptyActiveTurn() {
	return {
		reasoning: null,
		timeline: [],
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
			activeCity: null,
			country: null,
			currency: null,
			exchangeRate: null,
			temperatureC: null,
			weatherCondition: null,
			nights: null,
			stopTitle: null,
			budgetUsd: null,
			allocatedUsd: null,
			homeCurrency: null,
			heroImageUrl: null,
			soundtrack: null
		},
		preferences: {
			uri: 'traveler://preferences',
			travelerName: null,
			style: null,
			dietary: null,
			musicTaste: null,
			pace: null,
			homeCurrency: null
		}
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
		messages: [
			{
				id: 'msg-welcome',
				role: 'assistant',
				status: 'completed',
				content:
					'### Welcome to Nomad — Conversational Travel & Local Culture Concierge\n\nTell me **where you want to travel**, ask a focused question (live weather, currency conversion, regional music, or travel advisories), or pick a destination below.\n\n- **Zero Assumed Defaults**: When planning a trip, Nomad never assumes 3 or 4 days or a preset budget—it asks for **duration**, **budget & currency**, and **travel focus** first.\n- **Quarkus LangChain4j `@McpToolBox("nomad")` (`:8081/mcp`) + Official `A2A` (`:8082`)**: Live Open-Meteo weather, Wikipedia & iTunes 30s audio, Frankfurter ECB FX, and REST Countries v5 (`api.restcountries.com`).\n- **MicroProfile SmallRye Health (`/q/health/ready`)**: Live readiness across MCP clients, A2A upstreams, and Ollama is surfaced transparently in the Top-Bar Health Pill, Execution Spine, and chat.',
				reasoning: null,
				timeline: [],
				mcpCalls: [],
				a2aSteps: [],
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
			}
		],
		mcpCalls: [],
		a2aSteps: [],
		events: []
	};
}

/**
 * Pure reducer updating the 3-Pane Workbench state for each incoming AgUiEvent.
 *
 * @param {ReturnType<typeof createInitialWorkbenchState>} state
 * @param {any} event
 */
export function reduceAgUiEvent(state, event) {
	if (!event || !event.type) return state;

	const taggedEvent = event.direction ? event : { ...event, direction: 'server' };
	const nextEvents = [...state.events, taggedEvent];
	const payload = event.payload ?? {};
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();

	switch (event.type) {
		case 'WS_CONNECTED': {
			const incomingTurns = payload.memoryTurns ?? state.memoryTurns ?? 0;
			const currentDossier = state.tripDossierState ?? state.clusterState;
			const hasExistingCity = Boolean(currentDossier?.nomadTrip?.activeCity);
			const incomingDossier = payload.tripDossierState ?? payload.clusterState ?? currentDossier;
			const resolvedDossier =
				incomingTurns > 0 || hasExistingCity ? incomingDossier : currentDossier;
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

		case 'RUN_STARTED':
			return {
				...state,
				isStreaming: true,
				streamBuffer: '',
				pendingApproval: null,
				mcpCalls: [],
				a2aSteps: [],
				activeTurn: createEmptyActiveTurn(),
				statusText: `Run started (${payload.llmMode ?? payload.status ?? 'ollama:llama3.2:latest'})`,
				events: nextEvents
			};

		case 'STATE_SNAPSHOT':
		case 'STATE_DELTA': {
			if (payload.status === 'memory_reset') {
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
			const currentDossier = state.tripDossierState ?? state.clusterState;
			const incomingCluster = payload.tripDossierState ?? payload.clusterState ?? currentDossier;
			if (payload.status === 'params_updated') {
				const hasExistingCity = Boolean(currentDossier?.nomadTrip?.activeCity);
				const incomingTurns = payload.memoryTurns ?? state.memoryTurns ?? 0;
				const nextClusterState =
					hasExistingCity || incomingTurns > 0
						? incomingCluster
						: {
								...incomingCluster,
								nomadTrip: {
									...(incomingCluster?.nomadTrip ?? {}),
									activeCity: null,
									country: null,
									currency: null,
									exchangeRate: null,
									temperatureC: null,
									weatherCondition: null,
									stopTitle: null,
									allocatedUsd: null,
									heroImageUrl: null,
									soundtrack: null
								}
							};
				return {
					...state,
					tripDossierState: nextClusterState,
					clusterState: nextClusterState,
					memoryTurns: incomingTurns,
					statusText: 'Trip parameters updated via live WebSocket (<50ms)',
					events: nextEvents
				};
			}
			const nextClusterState = payload.awaitingDestination
				? {
						...incomingCluster,
						nomadTrip: {
							...(incomingCluster?.nomadTrip ?? {}),
							activeCity: null,
							country: null,
							currency: null,
							exchangeRate: null,
							temperatureC: null,
							weatherCondition: null,
							nights: null,
							stopTitle: null,
							allocatedUsd: null,
							heroImageUrl: null,
							soundtrack: null
						}
					}
				: incomingCluster;
			const nextInteraction = payload.interaction
				? {
						...payload.interaction,
						selectedValues: payload.interaction.selectedValues ?? [],
						submitted: Boolean(payload.interaction.submitted)
					}
				: currentTurn.interaction;
			return {
				...state,
				tripDossierState: nextClusterState,
				clusterState: nextClusterState,
				memoryTurns: payload.memoryTurns ?? state.memoryTurns ?? 0,
				activeTurn: {
					...currentTurn,
					media:
						Array.isArray(payload.media) && payload.media.length > 0
							? payload.media
							: currentTurn.media,
					interaction: nextInteraction,
					sources:
						Array.isArray(payload.sources) && payload.sources.length > 0
							? payload.sources
							: currentTurn.sources,
					followUps:
						Array.isArray(payload.followUps) && payload.followUps.length > 0
							? payload.followUps
							: currentTurn.followUps
				},
				events: nextEvents
			};
		}

		case 'STATUS': {
			const nextReasoning =
				payload.phase === 'reasoning' || payload.thought
					? {
							thought: payload.thought ?? payload.message ?? 'Synthesizing multi-protocol plan...',
							durationMs: payload.durationMs ?? 950
						}
					: currentTurn.reasoning;
			return {
				...state,
				statusText: payload.message ?? payload.phase ?? 'Thinking...',
				activeTurn: {
					...currentTurn,
					reasoning: nextReasoning
				},
				events: nextEvents
			};
		}

		case 'TOOL_CALL': {
			const requiresApproval = Boolean(payload.requiresApproval);
			const resolvedTool = payload.tool ?? payload.toolName ?? 'mcp-tool';
			const resolvedCallId = payload.callId ?? payload.toolCallId;
			const resolvedArgs = payload.args ?? payload.arguments ?? {};
			const resolvedCallType =
				payload.callType ?? (requiresApproval ? 'hitl:approval' : 'mcp:tool');
			const pendingApproval = requiresApproval
				? {
						callId: resolvedCallId,
						toolName: resolvedTool,
						city: resolvedArgs?.city ?? null,
						stopTitle: resolvedArgs?.stopTitle ?? null,
						nights: resolvedArgs?.nights ?? null,
						costUsd: resolvedArgs?.costUsd ?? null
					}
				: state.pendingApproval;

			const rawCallId = resolvedCallId ?? `tl-${nextEvents.length}`;
			const uniqueCallId = currentTurn.timeline.some((item) => item.id === rawCallId)
				? `${rawCallId}-${nextEvents.length}`
				: rawCallId;
			const timelineEntry = {
				id: uniqueCallId,
				callType: resolvedCallType,
				title: resolvedTool,
				subtitle: payload.server ?? 'nomad-mcp-concierge (:8081)',
				args: resolvedArgs,
				status: requiresApproval ? 'awaiting_approval' : 'running',
				detail: null
			};

			return {
				...state,
				statusText: requiresApproval ? 'HITL Gate — Awaiting Traveler Approval' : `MCP Tool: ${resolvedTool}`,
				pendingApproval,
				mcpCalls: [
					...state.mcpCalls,
					{
						callId: resolvedCallId,
						tool: resolvedTool,
						callType: resolvedCallType,
						server: payload.server ?? 'nomad-mcp-concierge (:8081)',
						args: resolvedArgs,
						status: requiresApproval ? 'awaiting_approval' : 'running',
						result: null
					}
				],
				activeTurn: {
					...currentTurn,
					timeline: [...currentTurn.timeline, timelineEntry]
				},
				events: nextEvents
			};
		}

		case 'TOOL_RESULT': {
			const resolvedCallId = payload.callId ?? payload.toolCallId;
			const resolvedTool = payload.tool ?? payload.toolName;
			const isError = isErrorPayload(payload);
			const nextStatus = isError ? 'error' : 'completed';
			const errorText = extractErrorDetail(payload);
			const updatedTimeline = currentTurn.timeline.map((item) =>
				item.id === resolvedCallId || item.title === resolvedTool
					? {
							...item,
							status: nextStatus,
							isError,
							result: payload.result,
							detail: isError ? errorText : summarizeToolResult(resolvedTool, payload.result)
						}
					: item
			);
			const extractedMedia =
				!isError && resolvedTool === 'discoverLocalCultureAndMedia'
					? extractCultureMediaItems(payload.result)
					: [];
			const nextMedia =
				extractedMedia.length > 0 && (!currentTurn.media || currentTurn.media.length === 0)
					? extractedMedia
					: currentTurn.media;
			const currentDossier = state.tripDossierState ?? state.clusterState;
			const nextDossier =
				!isError &&
				resolvedTool === 'discoverLocalCultureAndMedia' &&
				payload.result &&
				typeof payload.result === 'object'
					? {
							...currentDossier,
							nomadTrip: {
								...(currentDossier?.nomadTrip ?? {}),
								activeCity:
									currentDossier?.nomadTrip?.activeCity ??
									(typeof payload.result.destination === 'string' &&
									payload.result.destination.trim().length > 0
										? payload.result.destination.trim()
										: null),
								heroImageUrl:
									typeof payload.result.heroImageUrl === 'string' &&
									payload.result.heroImageUrl.trim().length > 0
										? payload.result.heroImageUrl.trim()
										: (currentDossier?.nomadTrip?.heroImageUrl ?? null),
								soundtrack:
									payload.result.audioTrack &&
									typeof payload.result.audioTrack === 'object' &&
									typeof payload.result.audioTrack.previewUrl === 'string' &&
									payload.result.audioTrack.previewUrl.trim().length > 0
										? {
												trackName: payload.result.audioTrack.trackName ?? 'Regional Track',
												artistName: payload.result.audioTrack.artistName ?? 'Regional Artist',
												previewUrl: payload.result.audioTrack.previewUrl.trim(),
												durationSeconds: 30
											}
										: (currentDossier?.nomadTrip?.soundtrack ?? null)
							}
						}
					: currentDossier;
			return {
				...state,
				tripDossierState: nextDossier,
				clusterState: nextDossier,
				statusText: isError ? `Tool Error: ${errorText}` : state.statusText,
				mcpCalls: state.mcpCalls.map((c) =>
					c.callId === resolvedCallId || c.tool === resolvedTool
						? { ...c, status: nextStatus, isError, error: isError ? errorText : null, result: payload.result }
						: c
				),
				activeTurn: {
					...currentTurn,
					timeline: updatedTimeline,
					media: nextMedia
				},
				events: nextEvents
			};
		}

		case 'STEP_STARTED': {
			const resolvedCallType = payload.callType ?? 'a2a:task';
			const rawStepId = payload.stepId ?? `a2a-${nextEvents.length}`;
			const uniqueStepId = currentTurn.timeline.some((item) => item.id === rawStepId)
				? `${rawStepId}-${nextEvents.length}`
				: rawStepId;
			const title = payload.skillId ?? payload.label ?? 'a2a-skill';
			const stepEntry = {
				id: uniqueStepId,
				callType: resolvedCallType,
				title,
				subtitle: `${payload.agentName ?? 'Nomad-A2A-Specialist'} (:8082)`,
				status: 'running',
				detail: null
			};
			return {
				...state,
				statusText: `A2A Delegation: ${payload.agentName ?? 'Nomad-A2A-Specialist'} (${title})`,
				activeTurn: {
					...currentTurn,
					timeline: [...currentTurn.timeline, stepEntry]
				},
				events: nextEvents
			};
		}

		case 'STEP_FINISHED': {
			const resolvedCallType = payload.callType ?? 'a2a:task';
			const title = payload.skillId ?? payload.label ?? 'a2a-skill';
			const isError = isErrorPayload(payload);
			const nextStatus = isError ? 'error' : 'completed';
			const detailText = isError ? extractErrorDetail(payload) : payload.summary;
			const hasExisting = currentTurn.timeline.some(
				(item) =>
					item.id === payload.stepId ||
					(item.title === title && item.status === 'running')
			);
			const updatedTimeline = hasExisting
				? currentTurn.timeline.map((item) =>
						item.id === payload.stepId ||
						(item.title === title && item.status === 'running')
							? {
									...item,
									callType: resolvedCallType,
									status: nextStatus,
									isError,
									detail: detailText,
									result: payload.report ?? payload.result
								}
							: item
					)
				: [
						...currentTurn.timeline,
						{
							id: currentTurn.timeline.some((item) => item.id === payload.stepId)
								? `${payload.stepId}-${nextEvents.length}`
								: (payload.stepId ?? `a2a-${nextEvents.length}`),
							callType: resolvedCallType,
							title,
							subtitle: `${payload.agentName ?? 'Nomad-A2A-Specialist'} (:8082)`,
							status: nextStatus,
							isError,
							detail: detailText,
							result: payload.report ?? payload.result
						}
					];
			return {
				...state,
				statusText: isError ? `Service Error: ${detailText}` : state.statusText,
				a2aSteps: [
					...state.a2aSteps,
					{
						stepId: payload.stepId,
						callType: resolvedCallType,
						agentName: payload.agentName ?? 'Nomad-A2A-Specialist',
						skillId: title,
						status: nextStatus,
						isError,
						summary: detailText,
						report: payload.report ?? {}
					}
				],
				activeTurn: {
					...currentTurn,
					timeline: updatedTimeline
				},
				events: nextEvents
			};
		}

		case 'TEXT_DELTA': {
			const chunk = payload.content ?? payload.delta ?? '';
			const prevEvent = state.events.at(-1);
			const coalescedEvents =
				prevEvent && prevEvent.type === 'TEXT_DELTA'
					? [
							...state.events.slice(0, -1),
							{
								...prevEvent,
								payload: {
									...prevEvent.payload,
									content: (prevEvent.payload?.content ?? '') + chunk,
									chunks: (prevEvent.payload?.chunks ?? 1) + 1
								}
							}
						]
					: nextEvents;
			return {
				...state,
				streamBuffer: state.streamBuffer + chunk,
				events: coalescedEvents
			};
		}

		case 'RUN_FINISHED': {
			if (payload.status === 'interrupted') {
				return {
					...state,
					isStreaming: false,
					pendingApproval: payload.pendingAction ?? state.pendingApproval ?? {
						toolName: 'updateTripItinerary',
						city: 'Kyoto',
						stopTitle: 'Hoshinoya Arashiyama Pavilion',
						nights: 4,
						costUsd: 1840
					},
					statusText: 'HITL Gate — Awaiting Traveler Approval',
					events: nextEvents
				};
			}
			const finalReply =
				state.streamBuffer.trim() ||
				(payload.status === 'cancelled'
					? 'Traveler declined sanctuary reservation hold. Run cancelled safely.'
					: '');
			const assistantMsg = finalReply
				? [
						{
							id: `msg-${event.id ?? Date.now()}`,
							role: 'assistant',
							status: 'completed',
							content: finalReply,
							reasoning: currentTurn.reasoning,
							timeline: currentTurn.timeline,
							mcpCalls: state.mcpCalls,
							a2aSteps: state.a2aSteps,
							media: currentTurn.media,
							interaction: currentTurn.interaction,
							sources: currentTurn.sources,
							followUps: currentTurn.followUps
						}
					]
				: [];
			return {
				...state,
				isStreaming: false,
				pendingApproval: null,
				memoryTurns: payload.memoryTurns ?? state.memoryTurns ?? 0,
				statusText: `Completed (${payload.status ?? 'completed'})`,
				streamBuffer: '',
				activeTurn: createEmptyActiveTurn(),
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

function extractCultureMediaItems(result) {
	if (!result || typeof result !== 'object') return [];
	const city = result.destination ?? 'Destination';
	const items = [];
	if (typeof result.heroImageUrl === 'string' && result.heroImageUrl.trim()) {
		items.push({
			type: 'image',
			title: `${city} Cultural & Architectural Plate`,
			subtitle: 'Wikipedia REST API v1 • Verified Landmark Photography',
			url: result.heroImageUrl.trim()
		});
	}
	const track = result.audioTrack;
	if (track && typeof track.previewUrl === 'string' && track.previewUrl.trim()) {
		items.push({
			type: 'audio',
			title: track.trackName ?? `${city} Acoustic Session`,
			subtitle: track.artistName ?? 'Regional Artist',
			url: track.previewUrl.trim(),
			durationSeconds: track.durationSeconds ?? 30
		});
	}
	return items;
}

function isErrorPayload(payload) {
	if (!payload || typeof payload !== 'object') return false;
	return Boolean(
		payload.isError ||
			payload.status === 'ERROR' ||
			payload.result?.isError ||
			payload.result?.status === 'ERROR' ||
			payload.report?.isError ||
			payload.report?.status === 'ERROR'
	);
}

function extractErrorDetail(payload) {
	return (
		payload?.error ??
		payload?.result?.error ??
		payload?.report?.error ??
		payload?.summary ??
		'Upstream travel service unavailable'
	);
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
		const id = check.name ?? 'health-check';
		const data = check.data && typeof check.data === 'object' ? check.data : {};
		let primaryError = data.error ?? null;
		const subServices = [];

		for (const [key, val] of Object.entries(data)) {
			if (key.endsWith('.status')) {
				const subId = key.slice(0, -'.status'.length);
				const subStatus = String(val ?? 'DOWN').toUpperCase();
				const subError = data[`${subId}.error`] ? String(data[`${subId}.error`]) : null;
				if (subStatus !== 'UP' && !primaryError && subError) {
					primaryError = subError;
				}
				subServices.push({
					id: subId,
					via: id,
					name: resolveHealthCheckLabel(subId, data),
					status: subStatus,
					error: subError
				});
			}
		}

		if (check.status !== 'UP' && !primaryError) {
			const failedEntry = Object.entries(data).find(
				([k, v]) => !k.endsWith('.status') && !k.endsWith('.error') && k !== 'endpoint' && k !== 'model' && v !== 'OK'
			);
			if (failedEntry) {
				primaryError = `${failedEntry[0]}: ${failedEntry[1]}`;
			}
		}

		services.push({
			id,
			name: resolveHealthCheckLabel(id, data),
			status: String(check.status ?? 'DOWN').toUpperCase(),
			error: primaryError
		});
		services.push(...subServices);
	}
	return services;
}

/**
 * Summarizes MicroProfile SmallRye Health (`/q/health/ready` `{ status, checks }`) or legacy
 * `/api/agent/health` (`{ status, services }`) payloads for the Top-Bar Health Pill & Popover.
 */
export function summarizeHealthReport(health) {
	const fromChecks = Array.isArray(health?.checks);
	const services = fromChecks
		? normalizeSmallRyeChecks(health.checks)
		: Array.isArray(health?.services)
			? health.services
			: null;
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
	let status;
	if (fromChecks) {
		status =
			downCount === 0 && String(health.status ?? 'UP').toUpperCase() === 'UP'
				? 'UP'
				: healthyCount === 0
					? 'DOWN'
					: 'DEGRADED';
	} else {
		status = health.status ?? (downCount === 0 ? 'UP' : healthyCount === 0 ? 'DOWN' : 'DEGRADED');
	}
	const label =
		status === 'UP'
			? 'All Systems Operational'
			: status === 'DOWN'
				? `Offline • ${downCount} Services Down`
				: `Degraded • ${downCount} ${downCount === 1 ? 'API' : 'APIs'} Down`;
	return {
		status,
		label,
		healthyCount,
		totalCount: services.length,
		downCount,
		downServices,
		services
	};
}

function summarizeToolResult(tool, result) {
	if (!result || typeof result !== 'object') return '';
	if (tool === 'fetchCityWeather' && result.city) {
		return `${result.city} • ${result.temperatureC}°C (${result.condition})`;
	}
	if (tool === 'discoverLocalCultureAndMedia' && result.audioTrack) {
		return `${result.destination} • 30s Preview: ${result.audioTrack.trackName}`;
	}
	if (tool === 'updateTripItinerary' && result.stopTitle) {
		return `Reserved ${result.stopTitle} (${result.nights} nights • $${result.allocatedUsd} USD)`;
	}
	if (tool === 'TravelSafetyAndInjectionGuardrail') {
		return result.reason ?? 'OWASP-LLM01 Input Guardrail Verified';
	}
	if (tool === 'CurrencyAndGroundingGuardrail') {
		return `Verified ${result.verifiedCity} • ${result.verifiedIsoCurrency} (${result.verifiedTemperatureC}°C)`;
	}
	if (tool === 'retrieveCulturalFieldNotes') {
		return `${result.chunksRetrieved ?? 3} field notes retrieved (cosine similarity ${result.topSimilarity ?? 0.98})`;
	}
	if (tool === 'sampleAcousticMoodProfile' || tool === 'sampleAcousticMood') {
		return result.sampledMood ?? 'Sampled acoustic twilight mood';
	}
	if (tool === 'saveTravelerPreferences' && result.travelerName) {
		return `Traveler profile updated: ${result.travelerName}`;
	}
	if (result.architecturalHighlight) {
		return `${result.city} — ${result.architecturalHighlight}`;
	}
	if (result.expandedPrompt) {
		return result.expandedPrompt;
	}
	if (result.style) {
		return `${result.style} • ${result.dietary ?? result.pace ?? ''}`;
	}
	return '';
}

/**
 * Mutates local trip nights count in clusterState for bidirectional UI-to-Agent state editing.
 *
 * @param {any} clusterState
 * @param {number} delta
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
 *
 * @param {any} options
 */
export function useAgUiAgent(options = {}) {
	return createBaseAgUiAgent({
		createInitialState: createInitialWorkbenchState,
		reducer: reduceAgUiEvent,
		...options
	});
}

/**
 * Formats the active Trip Dossier, Traveler Profile (`traveler://preferences`), and latest itinerary
 * into a downloadable Markdown document (`.md`).
 */
export function formatTripDossierMarkdown(clusterState, messages = []) {
	const trip = clusterState?.nomadTrip ?? {};
	const prefs = clusterState?.preferences ?? {};
	const city = trip.activeCity ?? 'Destination Pending';
	const country = trip.country ?? 'Unspecified';
	const currency = trip.currency ?? '--';
	const rate = trip.exchangeRate ?? null;
	const nights = trip.nights != null ? `${trip.nights} Nights` : 'Duration Not Set';
	const allocatedUsd = trip.allocatedUsd != null ? `$${trip.allocatedUsd}` : 'Not allocated';
	const budgetUsd = trip.budgetUsd != null ? `$${trip.budgetUsd}` : 'Not set';
	const localAllocated =
		rate != null && trip.allocatedUsd != null
			? `${Math.round(trip.allocatedUsd * rate).toLocaleString()} ${currency} @ 1 = ${rate} ${currency}`
			: 'Awaiting FX audit';
	const lastAssistant = [...(messages ?? [])].reverse().find((m) => m.role === 'assistant');

	const lines = [
		`# Nomad Cultural & Architectural Dossier — ${city}, ${country}`,
		``,
		`- **Sanctuary Allocation**: ${trip.stopTitle ?? 'Not reserved yet'} (${nights})`,
		`- **Live Open-Meteo Telemetry**: ${trip.temperatureC != null ? `${trip.temperatureC}°C` : '--°C'} — ${trip.weatherCondition ?? 'Awaiting weather check'}`,
		`- **Frankfurter FX Settlement**: ${allocatedUsd} / ${budgetUsd} (${localAllocated})`,
		`- **Home Currency**: ${prefs.homeCurrency ?? trip.homeCurrency ?? 'Not set'}`,
		`- **Soundtrack Preview**: ${trip.soundtrack?.trackName ?? 'Not loaded'} — ${trip.soundtrack?.artistName ?? '--'}`,
		``,
		`## Traveler Profile (\`traveler://preferences\`)`,
		`- **Architectural Style**: ${prefs.style ?? 'Not specified yet'}`,
		`- **Dietary Terroir**: ${prefs.dietary ?? 'Not specified yet'}`,
		`- **Acoustic & Music Taste**: ${prefs.musicTaste ?? 'Not specified yet'}`,
		`- **Cadence & Pace**: ${prefs.pace ?? 'Not specified yet'}`,
		``
	];

	if (lastAssistant?.content) {
		lines.push(`## Curated Concierge Briefing`, ``, lastAssistant.content.trim(), ``);
	}

	return lines.join('\n');
}
