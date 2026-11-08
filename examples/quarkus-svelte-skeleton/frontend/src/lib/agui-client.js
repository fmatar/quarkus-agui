/**
 * Pure AG-UI SSE parser, Protocol Call Taxonomy & Tri-Protocol (AG-UI + MCP + A2A) state reducer
 * powering the Nomad Cultural Concierge & AG-UI Svelte 5 Component Kit.
 */

export const NOMAD_DESTINATIONS = [
	{
		id: 'kyoto',
		city: 'Kyoto',
		country: 'Japan',
		currency: 'JPY',
		exchangeRate: 151.4,
		temperatureC: 18.4,
		condition: 'Clear Cedar & Autumn Mist',
		tagline: 'Arashiyama Moss Gardens, Gion Machiya & Koto Acoustics',
		prompt: '/kyoto Curate a 4-night Kyoto architectural, culinary & Koto music itinerary'
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
		prompt: '/lisbon Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary'
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
		prompt: '/reykjavik Design a 4-night Reykjavik aurora, geothermal & chamber music escape'
	}
];

export const SLASH_COMMANDS = [
	{
		command: '/kyoto',
		label: 'Kyoto Cultural & Koto Dossier',
		description: 'Open-Meteo weather, Wikipedia landmarks, 30s Koto preview & JPY FX audit',
		protocol: 'MCP + A2A',
		prompt: '/kyoto Curate a 4-night Kyoto architectural, culinary & Koto music itinerary'
	},
	{
		command: '/lisbon',
		label: 'Lisbon Fado & Azulejo Walk',
		description: 'Atlantic weather, Alfama Fado 30s audio preview & EUR budget conversion',
		protocol: 'MCP + A2A',
		prompt: '/lisbon Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary'
	},
	{
		command: '/reykjavik',
		label: 'Reykjavik Aurora & Nordic Strings',
		description: 'Sub-Arctic weather, Harpa acoustics 30s preview & ISK budget audit',
		protocol: 'MCP + A2A',
		prompt: '/reykjavik Design a 4-night Reykjavik aurora, geothermal & chamber music escape'
	},
	{
		command: '/media',
		label: 'Showcase Rich Media Gallery',
		description: 'Render destination photography, 30s iTunes audio player, aerial video & PDF folio',
		protocol: 'AG-UI Media',
		prompt: '/media Showcase Kyoto landmark photography, 30s Koto audio preview and video reel'
	},
	{
		command: '/choices',
		label: 'Interactive Multi-Select Choices',
		description: 'Render multi-select neighborhood cards and quick action triggers',
		protocol: 'AG-UI ChoiceGroup',
		prompt: '/choices Compare multi_select neighborhood experiences in Kyoto'
	},
	{
		command: '/mcp',
		label: 'MCP Prompt, Resource & Tool Trace',
		description: 'Execute MCP Prompt expansion, Resource read & live weather/culture tools (:8081)',
		protocol: 'MCP (:8081)',
		prompt: '/mcp Inspect Kyoto weather, active itinerary resource and cultural media'
	},
	{
		command: '/a2a',
		label: 'A2A Currency & Advisory Audit',
		description: 'Discover /.well-known/agent-card.json & run Frankfurter FX audit (:8082)',
		protocol: 'A2A (:8082)',
		prompt: '/a2a Convert Kyoto budget to JPY and verify Japan travel advisory'
	},
	{
		command: '/hitl',
		label: 'HITL Sanctuary Booking Gate',
		description: 'Trigger Human-in-the-Loop approval gate for a >$1,500 sanctuary hold',
		protocol: 'HITL Gate',
		prompt: '/hitl Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)'
	}
];

export const SCENARIO_PRESETS = [
	{
		id: 'nomad-kyoto',
		label: 'Kyoto • Koto & Moss',
		protocol: 'MCP + A2A',
		prompt: '/kyoto Curate a 4-night Kyoto architectural, culinary & Koto music itinerary'
	},
	{
		id: 'nomad-lisbon',
		label: 'Lisbon • Fado & Sun',
		protocol: 'MCP + A2A',
		prompt: '/lisbon Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary'
	},
	{
		id: 'nomad-reykjavik',
		label: 'Reykjavik • Aurora',
		protocol: 'MCP + A2A',
		prompt: '/reykjavik Design a 4-night Reykjavik aurora, geothermal & chamber music escape'
	},
	{
		id: 'nomad-choices',
		label: 'Multi-Select Plan',
		protocol: 'AG-UI Choice',
		prompt: '/choices Compare multi_select neighborhood experiences in Kyoto'
	},
	{
		id: 'nomad-hitl',
		label: 'Hold Sanctuary (HITL)',
		protocol: 'HITL Approval',
		prompt: '/hitl Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)'
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
	}
};

export function resolveProtocolCallMeta(callType, fallbackProtocol = 'MCP') {
	if (callType && PROTOCOL_CALL_TAXONOMY[callType]) {
		return PROTOCOL_CALL_TAXONOMY[callType];
	}
	const upper = String(fallbackProtocol ?? 'MCP').toUpperCase();
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

export function createInitialWorkbenchState(threadId = 'thread-opspilot-1') {
	return {
		threadId,
		isStreaming: false,
		statusText: 'Ready — Nomad Concierge & Tri-Protocol Studio',
		streamBuffer: '',
		pendingApproval: null,
		activeTurn: createEmptyActiveTurn(),
		clusterState: {
			cluster: 'quarkus-prod-us-east-1',
			uri: 'cluster://topology',
			nomadTrip: {
				uri: 'itinerary://active-trip',
				activeCity: 'Kyoto',
				country: 'Japan',
				currency: 'JPY',
				exchangeRate: 151.4,
				temperatureC: 18.4,
				weatherCondition: 'Clear Cedar & Autumn Mist',
				nights: 4,
				stopTitle: 'Hoshinoya Arashiyama Pavilion',
				budgetUsd: 3200,
				allocatedUsd: 1680,
				soundtrack: {
					trackName: 'Haru no Umi (Spring Sea — Koto & Shakuhachi)',
					artistName: 'Kyoto Koto Consort',
					previewUrl: 'https://www.w3schools.com/html/horse.mp3'
				}
			},
			services: [
				{
					serviceName: 'payment-gateway',
					status: 'DEGRADED',
					replicas: 3,
					cpuPercent: 78,
					memoryMb: 420,
					region: 'us-east-1'
				},
				{
					serviceName: 'order-processor',
					status: 'HEALTHY',
					replicas: 2,
					cpuPercent: 34,
					memoryMb: 256,
					region: 'us-east-1'
				},
				{
					serviceName: 'identity-provider',
					status: 'HEALTHY',
					replicas: 2,
					cpuPercent: 22,
					memoryMb: 192,
					region: 'us-west-2'
				}
			]
		},
		messages: [
			{
				id: 'msg-welcome',
				role: 'assistant',
				status: 'completed',
				content:
					'### Welcome to Nomad — Architectural & Cultural Concierge\n\nI orchestrate live **Open-Meteo weather**, **Wikipedia landmark dossiers**, and **30-second Apple iTunes local music previews** over **MCP (`:8081`)**, paired with **Frankfurter ECB currency conversion** and **REST Countries travel advisories** via **A2A (`:8082`)**.\n\nSelect a destination dossier above, sample the 30-second Kyoto Koto preview below, or type `/` in the composer to explore every AG-UI protocol capability.',
				reasoning: {
					durationMs: 840,
					thought:
						'Initialized tri-protocol concierge session: bound MCP resources (itinerary://active-trip, traveler://preferences) on :8081 and verified A2A AgentCard v1.4.0 on :8082.'
				},
				timeline: [
					{
						id: 'init-mcp-res',
						callType: 'mcp:resource',
						title: 'itinerary://active-trip',
						subtitle: 'nomad-mcp-concierge (:8081)',
						status: 'completed',
						detail: 'Loaded 4-night Kyoto sanctuary dossier ($1,680 / $3,200 USD allocated)'
					},
					{
						id: 'init-mcp-tool',
						callType: 'mcp:tool',
						title: 'fetchCityWeather',
						subtitle: 'Open-Meteo Live API (:8081)',
						status: 'completed',
						detail: 'Kyoto • 18.4°C — Clear Cedar & Autumn Mist'
					},
					{
						id: 'init-a2a-fx',
						callType: 'a2a:task',
						title: 'convert-and-audit-budget',
						subtitle: 'Sentinel-A2A-Auditor (:8082)',
						status: 'completed',
						detail: 'Frankfurter FX: $3,200 USD = ¥484,480 JPY (1 USD = 151.40 JPY)'
					}
				],
				mcpCalls: [],
				a2aSteps: [],
				media: [
					{
						type: 'image',
						title: 'Kyoto Architectural & Cultural Panorama',
						subtitle: 'Arashiyama & Higashiyama • Wikipedia REST API v1',
						url: 'https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?auto=format&fit=crop&w=1000&q=80'
					},
					{
						type: 'audio',
						title: 'Haru no Umi (Spring Sea — Koto & Shakuhachi)',
						subtitle: 'Kyoto Koto Consort • Apple iTunes 30s Preview',
						url: 'https://www.w3schools.com/html/horse.mp3',
						durationSeconds: 30
					}
				],
				interaction: {
					id: 'choice-welcome-kyoto',
					mode: 'single_select',
					prompt: 'Select an opening cultural cadence for your Kyoto dossier:',
					selectedValues: [],
					submitted: false,
					options: [
						{
							id: 'arashiyama-dawn',
							label: 'Arashiyama Bamboo & Katsura Imperial Villa',
							description: 'Early-morning moss garden architectural walk + private matcha pavilion',
							badge: 'Dawn • ¥24,000'
						},
						{
							id: 'gion-koto',
							label: 'Gion Machiya Private Koto & Kaiseki Evening',
							description: 'Seasonal Kaiseki tasting menu with live Koto & shakuhachi acoustics',
							badge: 'Evening • ¥38,000'
						},
						{
							id: 'higashiyama-ceramics',
							label: 'Higashiyama Raku Kiln & Vinyl Listening Bar',
							description: 'Private ceramic conservation studio + analog jazz kissaten',
							badge: 'Late Night • ¥19,000'
						}
					],
					actions: [
						{
							id: 'act-hitl',
							label: 'Hold Arashiyama Suite ($1,840 — HITL Gate)',
							prompt: '/hitl Reserve Hoshinoya Arashiyama Pavilion in Kyoto for 4 nights ($1840)',
							variant: 'primary'
						},
						{
							id: 'act-lisbon',
							label: 'Switch Dossier to Lisbon (/lisbon)',
							prompt: '/lisbon Plan a 4-night Lisbon Fado, azulejo atelier & coastal itinerary',
							variant: 'secondary'
						}
					]
				},
				sources: [
					{ title: 'Open-Meteo Live Weather', url: 'https://api.open-meteo.com', protocol: 'mcp:tool' },
					{ title: 'Wikipedia REST API v1', url: 'https://en.wikipedia.org/wiki/Kyoto', protocol: 'mcp:tool' },
					{ title: 'Apple iTunes 30s Audio', url: 'https://itunes.apple.com', protocol: 'mcp:tool' },
					{ title: 'Frankfurter ECB FX', url: 'https://api.frankfurter.dev', protocol: 'a2a:task' }
				],
				followUps: [
					'/lisbon Plan an evening Fado & azulejo walk in Lisbon',
					'/reykjavik Design an aurora & geothermal itinerary in Reykjavik',
					'/choices Compare multi-select neighborhood experiences in Kyoto',
					'/hitl Reserve Hoshinoya Arashiyama Pavilion ($1,840 — HITL Gate)'
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

	const nextEvents = [...state.events, event];
	const payload = event.payload ?? {};
	const currentTurn = state.activeTurn ?? createEmptyActiveTurn();

	switch (event.type) {
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
						costUsd: payload.args?.costUsd ?? 1840,
						serviceName: payload.args?.serviceName ?? 'payment-gateway',
						replicas: payload.args?.replicas ?? 6
					}
				: state.pendingApproval;

			const timelineEntry = {
				id: payload.callId ?? `tl-${nextEvents.length}`,
				callType: resolvedCallType,
				title: payload.tool,
				subtitle: payload.server ?? 'nomad-mcp-concierge (:8081)',
				args: payload.args ?? {},
				status: requiresApproval ? 'awaiting_approval' : 'running',
				detail: null
			};

			return {
				...state,
				statusText: requiresApproval ? 'HITL Gate — Awaiting Operator Approval' : `MCP Tool: ${payload.tool}`,
				pendingApproval,
				mcpCalls: [
					...state.mcpCalls,
					{
						callId: payload.callId,
						tool: payload.tool,
						callType: resolvedCallType,
						server: payload.server ?? 'quarkus-mcp-cluster-ops (:8081)',
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
			const stepEntry = {
				id: payload.stepId ?? `a2a-${nextEvents.length}`,
				callType: resolvedCallType,
				title: payload.skillId ?? 'a2a-skill',
				subtitle: `${payload.agentName ?? 'Sentinel-A2A-Auditor'} (:8082)`,
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
			const hasExisting = currentTurn.timeline.some((item) => item.id === payload.stepId);
			const updatedTimeline = hasExisting
				? currentTurn.timeline.map((item) =>
						item.id === payload.stepId
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
							id: payload.stepId ?? `a2a-${nextEvents.length}`,
							callType: resolvedCallType,
							title: payload.skillId ?? 'a2a-skill',
							subtitle: `${payload.agentName ?? 'Sentinel-A2A-Auditor'} (:8082)`,
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
						agentName: payload.agentName ?? 'Sentinel-A2A-Auditor',
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

		case 'TEXT_DELTA':
			return {
				...state,
				streamBuffer: state.streamBuffer + (payload.content ?? ''),
				events: nextEvents
			};

		case 'RUN_FINISHED': {
			if (payload.status === 'interrupted') {
				return {
					...state,
					isStreaming: false,
					pendingApproval: payload.pendingAction ?? state.pendingApproval ?? {
						toolName: 'scaleDeployment',
						serviceName: 'payment-gateway',
						replicas: 6
					},
					statusText: 'HITL Gate — Awaiting Operator Approval',
					events: nextEvents
				};
			}
			const finalReply =
				state.streamBuffer.trim() ||
				(payload.status === 'cancelled'
					? 'Operator rejected high-replica scale action. Run cancelled safely.'
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
	if (result.expandedPrompt) {
		return result.expandedPrompt;
	}
	if (result.style) {
		return `${result.style} • ${result.pace ?? ''}`;
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
 * Mutates local replica count in clusterState for bidirectional UI-to-Agent state editing.
 *
 * @param {any} clusterState
 * @param {string} serviceName
 * @param {number} delta
 */
export function updateServiceReplicasInState(clusterState, serviceName, delta) {
	if (!clusterState?.services) return clusterState;
	return {
		...clusterState,
		services: clusterState.services.map((svc) =>
			svc.serviceName === serviceName
				? { ...svc, replicas: Math.max(1, (svc.replicas ?? 1) + delta) }
				: svc
		)
	};
}
