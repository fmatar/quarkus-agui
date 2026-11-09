/**
 * Pure AG-UI SSE parser, Protocol Call Taxonomy & Tri-Protocol (AG-UI + MCP + A2A) state reducer
 * powering the Nomad Cultural Concierge & AG-UI Svelte 5 Component Kit.
 */

export const NOMAD_DESTINATIONS = [
	{
		id: 'beirut',
		city: 'Beirut',
		country: 'Lebanon',
		currency: 'LBP',
		exchangeRate: 89500,
		temperatureC: 24.0,
		condition: 'Mediterranean Coastal Sun & Sea Breeze',
		tagline: 'Levantine Limestone Arcades, Sursock Modernism & Oud Acoustics',
		prompt: 'Plan a 4-night Beirut architectural, Levantine culinary & Oud music itinerary'
	},
	{
		id: 'kyoto',
		city: 'Kyoto',
		country: 'Japan',
		currency: 'JPY',
		exchangeRate: 151.4,
		temperatureC: 18.4,
		condition: 'Clear Cedar & Autumn Mist',
		tagline: 'Arashiyama Moss Gardens, Gion Machiya & Koto Acoustics',
		prompt: 'Curate a 4-night Kyoto architectural, culinary & Koto music itinerary'
	},
	{
		id: 'lisbon',
		city: 'Lisbon',
		country: 'Portugal',
		currency: 'EUR',
		exchangeRate: 0.92,
		temperatureC: 22.1,
		condition: 'Atlantic Breeze & Golden Sun',
		tagline: 'Alfama Azulejo Terraces, Miradouros & Acoustic Fado',
		prompt: 'Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary'
	},
	{
		id: 'reykjavik',
		city: 'Reykjavik',
		country: 'Iceland',
		currency: 'ISK',
		exchangeRate: 137.8,
		temperatureC: 6.2,
		condition: 'Crisp Sub-Arctic Aurora Sky',
		tagline: 'Harpa Basalt Geometry, Geothermal Lagoons & Nordic Strings',
		prompt: 'Design a 4-night Reykjavik aurora, geothermal & chamber music escape'
	}
];

export const SLASH_COMMANDS = [
	{
		command: '/beirut',
		label: 'Beirut Modernism & Oud Dossier',
		description: 'Mediterranean weather, Levantine landmarks, 30s Fairuz/Oud preview & LBP FX audit',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 4-night Beirut architectural, Levantine culinary & Oud music itinerary'
	},
	{
		command: '/kyoto',
		label: 'Kyoto Cultural & Koto Dossier',
		description: 'Open-Meteo weather, Wikipedia landmarks, 30s Koto preview & JPY FX audit',
		protocol: 'MCP + A2A',
		prompt: 'Curate a 4-night Kyoto architectural, culinary & Koto music itinerary'
	},
	{
		command: '/lisbon',
		label: 'Lisbon Fado & Azulejo Walk',
		description: 'Atlantic weather, Alfama Fado 30s audio preview & EUR budget conversion',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary'
	},
	{
		command: '/reykjavik',
		label: 'Reykjavik Aurora & Nordic Strings',
		description: 'Sub-Arctic weather, Harpa acoustics 30s preview & ISK budget audit',
		protocol: 'MCP + A2A',
		prompt: 'Design a 4-night Reykjavik aurora, geothermal & chamber music escape'
	},
	{
		command: '/media',
		label: 'Destination Photography & Audio Preview',
		description: 'Fetch Wikipedia landmark photography & 30s Apple iTunes local soundtrack',
		protocol: 'AG-UI Media',
		prompt: 'Showcase Kyoto landmark photography and 30s local Koto audio preview'
	},
	{
		command: '/choices',
		label: 'Interactive Multi-Select Choices',
		description: 'Compare and select multiple neighborhood experiences',
		protocol: 'AG-UI ChoiceGroup',
		prompt: 'Compare multi_select neighborhood experiences in Kyoto'
	},
	{
		command: '/mcp',
		label: 'Live Weather & Cultural Media',
		description: 'Query Open-Meteo forecast, Wikipedia REST API & iTunes soundtrack',
		protocol: 'MCP',
		prompt: 'Inspect Kyoto weather, active itinerary and cultural media'
	},
	{
		command: '/a2a',
		label: 'Currency & Travel Advisory Audit',
		description: 'Run Frankfurter ECB FX conversion & REST Countries advisory check',
		protocol: 'A2A',
		prompt: 'Convert Kyoto budget to JPY and verify Japan travel advisory'
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
		id: 'nomad-kyoto',
		label: 'Kyoto',
		protocol: 'MCP + A2A',
		prompt: 'Curate a 4-night Kyoto architectural, culinary & Koto music itinerary'
	},
	{
		id: 'nomad-lisbon',
		label: 'Lisbon',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary'
	},
	{
		id: 'nomad-reykjavik',
		label: 'Reykjavik',
		protocol: 'MCP + A2A',
		prompt: 'Design a 4-night Reykjavik aurora, geothermal & chamber music escape'
	},
	{
		id: 'nomad-choices',
		label: 'Tokyo',
		protocol: 'MCP + A2A',
		prompt: 'Plan a 4-night Tokyo modernist architecture, vinyl kissaten & culinary itinerary'
	},
	{
		id: 'nomad-hitl',
		label: 'Reserve Sanctuary',
		protocol: 'HITL Approval',
		prompt: 'Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)'
	}
];

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
	return {
		threadId,
		transportMode: 'ws',
		wsConnected: false,
		memoryTurns: 0,
		isStreaming: false,
		statusText: 'Ready — Nomad Concierge & Tri-Protocol Studio',
		streamBuffer: '',
		pendingApproval: null,
		activeTurn: createEmptyActiveTurn(),
		clusterState: {
			uri: 'itinerary://active-trip',
			nomadTrip: {
				uri: 'itinerary://active-trip',
				activeCity: null,
				country: null,
				currency: null,
				exchangeRate: null,
				temperatureC: null,
				weatherCondition: null,
				nights: 4,
				stopTitle: null,
				budgetUsd: 3200,
				allocatedUsd: 0,
				homeCurrency: 'USD',
				soundtrack: null
			},
			preferences: {
				uri: 'traveler://preferences',
				style: 'Architectural Minimalist & Slow Cultural Immersion',
				dietary: 'Seasonal Local Terroir',
				musicTaste: 'Analog Vinyl & Regional Acoustic',
				pace: 'Unrushed (2 curated stops per day)',
				homeCurrency: 'USD'
			}
		},
		messages: [
			{
				id: 'msg-welcome',
				role: 'assistant',
				status: 'completed',
				content:
					'### Welcome to Nomad — Cultural, Architectural & Acoustic Concierge\n\nWhich city or cultural landscape would you like to explore?\n\nSelect a starter destination from the **Destination Picker** on the right, or type **any city in the world** below (e.g., *Beirut*, *Kyoto*, *Lisbon*, *Reykjavik*, *Marrakech*, *Tokyo*, *Oaxaca*) to synthesize a live Tri-Protocol dossier combining:\n- **Live Weather & Terroir (`MCP`)** via Open-Meteo geocoding & forecast telemetry\n- **Architectural Context & 30s Regional Audio (`MCP`)** via Wikipedia REST v1 & Apple iTunes Search\n- **Currency & Travel Advisory Audit (`A2A`)** via Frankfurter ECB FX & REST Countries v3.1\n- **Cultural Field Notes RAG & Specialist Council (`LangChain4j + AG-UI`)**',
				reasoning: null,
				timeline: [],
				mcpCalls: [],
				a2aSteps: [],
				media: [],
				interaction: {
					id: 'welcome-city-picker',
					mode: 'single_select',
					prompt: 'Choose a destination to initialize your live Trip Dossier:',
					selectedValues: [],
					submitted: false,
					options: [
						{
							id: 'pick-beirut',
							label: 'Beirut, Lebanon',
							description: 'Levantine limestone arcades, Sursock modernism, coastal terroir & Oud acoustics',
							badge: 'LBP • Mediterranean',
							prompt: 'Plan a 4-night Beirut architectural, Levantine culinary & Oud music itinerary'
						},
						{
							id: 'pick-kyoto',
							label: 'Kyoto, Japan',
							description: 'Arashiyama cedar pavilions, Gion machiya joinery, kaiseki & Koto strings',
							badge: 'JPY • East Asia',
							prompt: 'Curate a 4-night Kyoto architectural, culinary & Koto music itinerary'
						},
						{
							id: 'pick-lisbon',
							label: 'Lisbon, Portugal',
							description: 'Alfama azulejo terraces, Siza Vieira concrete geometry & acoustic Fado',
							badge: 'EUR • Atlantic',
							prompt: 'Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary'
						},
						{
							id: 'pick-reykjavik',
							label: 'Reykjavik, Iceland',
							description: 'Harpa basalt facades, geothermal lagoons & Nordic chamber strings',
							badge: 'ISK • Sub-Arctic',
							prompt: 'Design a 4-night Reykjavik aurora, geothermal & chamber music escape'
						}
					]
				},
				sources: [],
				followUps: [
					'Plan a 4-night architectural, culinary and music trip in Beirut',
					'Curate a 4-night Kyoto architectural & Koto music escape',
					'Compare 4 neighborhood culinary & music experiences in Lisbon',
					'Design a 4-night Tokyo modernist architecture & vinyl kissaten itinerary'
				]
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
			const hasExistingCity = Boolean(state.clusterState?.nomadTrip?.activeCity);
			return {
				...state,
				wsConnected: true,
				transportMode: 'ws',
				memoryTurns: incomingTurns,
				clusterState:
					incomingTurns > 0 || hasExistingCity
						? (payload.clusterState ?? state.clusterState)
						: state.clusterState,
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
				statusText: `Run started (${payload.llmMode ?? payload.status ?? 'deterministic'})`,
				events: nextEvents
			};

		case 'STATE_SNAPSHOT':
		case 'STATE_DELTA': {
			if (payload.status === 'memory_reset') {
				const initial = createInitialWorkbenchState(state.threadId);
				return {
					...state,
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
			if (payload.status === 'params_updated') {
				const hasExistingCity = Boolean(state.clusterState?.nomadTrip?.activeCity);
				const incomingTurns = payload.memoryTurns ?? state.memoryTurns ?? 0;
				const incomingCluster = payload.clusterState ?? state.clusterState;
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
									allocatedUsd: 0,
									soundtrack: null
								}
							};
				return {
					...state,
					clusterState: nextClusterState,
					memoryTurns: incomingTurns,
					statusText: 'Trip parameters updated via live WebSocket (<50ms)',
					events: nextEvents
				};
			}
			const nextInteraction = payload.interaction
				? {
						...payload.interaction,
						selectedValues: payload.interaction.selectedValues ?? [],
						submitted: Boolean(payload.interaction.submitted)
					}
				: currentTurn.interaction;
			return {
				...state,
				clusterState: payload.clusterState ?? state.clusterState,
				memoryTurns: payload.memoryTurns ?? state.memoryTurns ?? 0,
				activeTurn: {
					...currentTurn,
					media: payload.media ?? currentTurn.media,
					interaction: nextInteraction,
					sources: payload.sources ?? currentTurn.sources,
					followUps: payload.followUps ?? currentTurn.followUps
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
			const resolvedCallType =
				payload.callType ?? (requiresApproval ? 'hitl:approval' : 'mcp:tool');
			const pendingApproval = requiresApproval
				? {
						callId: payload.callId,
						toolName: payload.tool,
						city: payload.args?.city ?? 'Kyoto',
						stopTitle: payload.args?.stopTitle ?? 'Hoshinoya Arashiyama Pavilion',
						nights: payload.args?.nights ?? 4,
						costUsd: payload.args?.costUsd ?? 1840
					}
				: state.pendingApproval;

			const rawCallId = payload.callId ?? `tl-${nextEvents.length}`;
			const uniqueCallId = currentTurn.timeline.some((item) => item.id === rawCallId)
				? `${rawCallId}-${nextEvents.length}`
				: rawCallId;
			const timelineEntry = {
				id: uniqueCallId,
				callType: resolvedCallType,
				title: payload.tool,
				subtitle: payload.server ?? 'nomad-mcp-concierge (:8081)',
				args: payload.args ?? {},
				status: requiresApproval ? 'awaiting_approval' : 'running',
				detail: null
			};

			return {
				...state,
				statusText: requiresApproval ? 'HITL Gate — Awaiting Traveler Approval' : `MCP Tool: ${payload.tool}`,
				pendingApproval,
				mcpCalls: [
					...state.mcpCalls,
					{
						callId: payload.callId,
						tool: payload.tool,
						callType: resolvedCallType,
						server: payload.server ?? 'nomad-mcp-concierge (:8081)',
						args: payload.args ?? {},
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
			const updatedTimeline = currentTurn.timeline.map((item) =>
				item.id === payload.callId || item.title === payload.tool
					? {
							...item,
							status: 'completed',
							result: payload.result,
							detail: summarizeToolResult(payload.tool, payload.result)
						}
					: item
			);
			return {
				...state,
				mcpCalls: state.mcpCalls.map((c) =>
					c.callId === payload.callId || c.tool === payload.tool
						? { ...c, status: 'completed', result: payload.result }
						: c
				),
				activeTurn: {
					...currentTurn,
					timeline: updatedTimeline
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
			const stepEntry = {
				id: uniqueStepId,
				callType: resolvedCallType,
				title: payload.skillId ?? 'a2a-skill',
				subtitle: `${payload.agentName ?? 'Nomad-A2A-Specialist'} (:8082)`,
				status: 'running',
				detail: null
			};
			return {
				...state,
				statusText: `A2A Delegation: ${payload.agentName} (${payload.skillId})`,
				activeTurn: {
					...currentTurn,
					timeline: [...currentTurn.timeline, stepEntry]
				},
				events: nextEvents
			};
		}

		case 'STEP_FINISHED': {
			const resolvedCallType = payload.callType ?? 'a2a:task';
			const hasExisting = currentTurn.timeline.some(
				(item) =>
					item.id === payload.stepId ||
					(item.title === payload.skillId && item.status === 'running')
			);
			const updatedTimeline = hasExisting
				? currentTurn.timeline.map((item) =>
						item.id === payload.stepId ||
						(item.title === payload.skillId && item.status === 'running')
							? {
									...item,
									callType: resolvedCallType,
									status: 'completed',
									detail: payload.summary,
									result: payload.report
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
							title: payload.skillId ?? 'a2a-skill',
							subtitle: `${payload.agentName ?? 'Nomad-A2A-Specialist'} (:8082)`,
							status: 'completed',
							detail: payload.summary,
							result: payload.report
						}
					];
			return {
				...state,
				a2aSteps: [
					...state.a2aSteps,
					{
						stepId: payload.stepId,
						callType: resolvedCallType,
						agentName: payload.agentName ?? 'Nomad-A2A-Specialist',
						skillId: payload.skillId,
						summary: payload.summary,
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
			const chunk = payload.content ?? '';
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
	if (tool === 'sampleAcousticMoodProfile') {
		return result.sampledMood ?? 'Sampled acoustic twilight mood';
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
 * Filters protocol inspector events by protocol family ('ALL', 'AG-UI', 'MCP', 'A2A').
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
 * Mutates local trip nights count in clusterState for bidirectional UI-to-Agent state editing.
 *
 * @param {any} clusterState
 * @param {number} delta
 */
export function updateTripNightsInState(clusterState, delta) {
	if (!clusterState?.nomadTrip) return clusterState;
	const nextNights = Math.max(1, Math.min(30, (clusterState.nomadTrip.nights ?? 4) + delta));
	return {
		...clusterState,
		nomadTrip: {
			...clusterState.nomadTrip,
			nights: nextNights,
			allocatedUsd: Math.round(nextNights * 420)
		}
	};
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

/**
 * Formats the active Trip Dossier, Traveler Profile (`traveler://preferences`), and latest itinerary
 * into a downloadable Markdown document (`.md`).
 */
export function formatTripDossierMarkdown(clusterState, messages = []) {
	const trip = clusterState?.nomadTrip ?? {};
	const prefs = clusterState?.preferences ?? {};
	const city = trip.activeCity ?? 'Kyoto';
	const country = trip.country ?? 'Japan';
	const currency = trip.currency ?? 'JPY';
	const rate = trip.exchangeRate ?? 151.4;
	const nights = trip.nights ?? 4;
	const allocatedUsd = trip.allocatedUsd ?? 1680;
	const budgetUsd = trip.budgetUsd ?? 3200;
	const localAllocated = Math.round(allocatedUsd * rate).toLocaleString();
	const lastAssistant = [...(messages ?? [])].reverse().find((m) => m.role === 'assistant');

	const lines = [
		`# Nomad Cultural & Architectural Dossier — ${city}, ${country}`,
		``,
		`- **Sanctuary Allocation**: ${trip.stopTitle ?? `${city} Architectural Sanctuary`} (${nights} Nights)`,
		`- **Live Open-Meteo Telemetry**: ${trip.temperatureC ?? 18.4}°C — ${trip.weatherCondition ?? 'Clear Sky'}`,
		`- **Frankfurter FX Settlement**: $${allocatedUsd} / $${budgetUsd} USD (${localAllocated} ${currency} @ 1 USD = ${rate} ${currency})`,
		`- **Home Currency**: ${prefs.homeCurrency ?? trip.homeCurrency ?? 'USD'}`,
		`- **Soundtrack Preview**: ${trip.soundtrack?.trackName ?? 'Regional Acoustic Session'} — ${trip.soundtrack?.artistName ?? 'Local Ensemble'}`,
		``,
		`## Traveler Profile (\`traveler://preferences\`)`,
		`- **Architectural Style**: ${prefs.style ?? 'Architectural Minimalist & Slow Cultural Immersion'}`,
		`- **Dietary Terroir**: ${prefs.dietary ?? 'Seasonal Local Terroir'}`,
		`- **Acoustic & Music Taste**: ${prefs.musicTaste ?? 'Analog Vinyl & Regional Acoustic'}`,
		`- **Cadence & Pace**: ${prefs.pace ?? 'Unrushed (2 curated stops per day)'}`,
		``
	];

	if (lastAssistant?.content) {
		lines.push(`## Curated Concierge Briefing`, ``, lastAssistant.content.trim(), ``);
	}

	return lines.join('\n');
}
