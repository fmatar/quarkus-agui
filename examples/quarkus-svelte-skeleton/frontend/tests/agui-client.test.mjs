import test from 'node:test';
import assert from 'node:assert/strict';
import {
	NOMAD_DESTINATIONS,
	PROTOCOL_CALL_TAXONOMY,
	SCENARIO_PRESETS,
	SLASH_COMMANDS,
	createInitialWorkbenchState,
	filterInspectorEvents,
	formatTripDossierMarkdown,
	lockTurnInteraction,
	parseSseChunk,
	recordClientOutboundEvent,
	reduceAgUiEvent,
	resolveProtocolCallMeta,
	selectTurnOption,
	updateTripNightsInState
} from '../src/lib/agui-client.js';

test('parseSseChunk splits multi-frame SSE chunks on double newline and preserves remainder', () => {
	const raw =
		'data: {"id":"1","type":"RUN_STARTED","threadId":"t1","timestamp":100,"payload":{"status":"started"}}\r\n\r\n' +
		'data:data: {"id":"2","type":"TEXT_DELTA","threadId":"t1","timestamp":101,"payload":{"content":"Hello "}}\n\n' +
		'data: {"id":"3","type":"TEXT_DELTA"';

	const { events, remainder } = parseSseChunk(raw);
	assert.equal(events.length, 2);
	assert.equal(events[0].type, 'RUN_STARTED');
	assert.equal(events[1].payload.content, 'Hello ');
	assert.equal(remainder, 'data: {"id":"3","type":"TEXT_DELTA"');
});

test('reduceAgUiEvent transitions state across RUN_STARTED, STATE_SNAPSHOT, TOOL_CALL, STEP_FINISHED, TEXT_DELTA, and RUN_FINISHED', () => {
	let state = createInitialWorkbenchState('thread-test');
	assert.equal(state.isStreaming, false);

	state = reduceAgUiEvent(state, {
		id: 'e1',
		type: 'RUN_STARTED',
		threadId: 'thread-test',
		timestamp: 1000,
		payload: { threadId: 'thread-test', status: 'started', llmMode: 'deterministic' }
	});
	assert.equal(state.isStreaming, true);
	assert.equal(state.statusText, 'Run started (deterministic)');

	state = reduceAgUiEvent(state, {
		id: 'e1-snap',
		type: 'STATE_SNAPSHOT',
		threadId: 'thread-test',
		timestamp: 1002,
		payload: {
			clusterState: {
				uri: 'itinerary://active-trip',
				nomadTrip: { activeCity: 'Kyoto', country: 'Japan', nights: 4, temperatureC: 18.4 }
			}
		}
	});
	assert.equal(state.clusterState.nomadTrip.nights, 4);

	state = reduceAgUiEvent(state, {
		id: 'e2',
		type: 'TOOL_CALL',
		threadId: 'thread-test',
		timestamp: 1005,
		payload: {
			callId: 'c1',
			tool: 'fetchCityWeather',
			protocol: 'MCP',
			server: 'nomad-mcp-concierge (:8081)',
			args: { city: 'Kyoto' }
		}
	});
	assert.equal(state.mcpCalls.length, 1);
	assert.equal(state.mcpCalls[0].tool, 'fetchCityWeather');

	state = reduceAgUiEvent(state, {
		id: 'e3',
		type: 'TOOL_RESULT',
		threadId: 'thread-test',
		timestamp: 1010,
		payload: {
			callId: 'c1',
			tool: 'fetchCityWeather',
			result: { city: 'Kyoto', temperatureC: 18.4, condition: 'Clear Cedar & Autumn Mist' }
		}
	});
	assert.equal(state.mcpCalls[0].status, 'completed');

	state = reduceAgUiEvent(state, {
		id: 'e4',
		type: 'STEP_FINISHED',
		threadId: 'thread-test',
		timestamp: 1015,
		payload: {
			stepId: 's1',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'convert-and-audit-budget',
			summary: 'Frankfurter FX verified: $3,200 USD = ¥484,480 JPY'
		}
	});
	assert.equal(state.a2aSteps.length, 1);
	assert.equal(state.a2aSteps[0].agentName, 'Nomad-A2A-Specialist');

	state = reduceAgUiEvent(state, {
		id: 'e5',
		type: 'TEXT_DELTA',
		threadId: 'thread-test',
		timestamp: 1020,
		payload: { content: 'Kyoto dossier ready.' }
	});
	assert.equal(state.streamBuffer, 'Kyoto dossier ready.');

	state = reduceAgUiEvent(state, {
		id: 'e6',
		type: 'RUN_FINISHED',
		threadId: 'thread-test',
		timestamp: 1025,
		payload: { status: 'completed' }
	});
	assert.equal(state.isStreaming, false);
	assert.equal(state.messages.at(-1).role, 'assistant');
	assert.equal(state.messages.at(-1).content, 'Kyoto dossier ready.');
});

test('SCENARIO_PRESETS exposes five tri-protocol showcase scenarios including HITL approval', () => {
	assert.equal(SCENARIO_PRESETS.length, 5);
	assert.ok(SCENARIO_PRESETS.every((s) => s.id && s.label && s.prompt));
});

test('reduceAgUiEvent handles HITL interrupted and cancelled RUN_FINISHED states', () => {
	let state = createInitialWorkbenchState('thread-hitl');
	state = reduceAgUiEvent(state, {
		id: 'h1',
		type: 'TOOL_CALL',
		payload: {
			callId: 'ch1',
			tool: 'updateTripItinerary',
			requiresApproval: true,
			args: { city: 'Kyoto', stopTitle: 'Hoshinoya Arashiyama Pavilion', nights: 5, costUsd: 2150 }
		}
	});
	assert.equal(state.mcpCalls[0].status, 'awaiting_approval');
	assert.ok(state.pendingApproval);
	assert.equal(state.pendingApproval.city, 'Kyoto');
	assert.equal(state.pendingApproval.nights, 5);
	assert.equal(state.statusText, 'HITL Gate — Awaiting Traveler Approval');

	state = reduceAgUiEvent(state, {
		id: 'h2',
		type: 'RUN_FINISHED',
		payload: {
			status: 'interrupted',
			pendingAction: {
				toolName: 'updateTripItinerary',
				city: 'Kyoto',
				stopTitle: 'Hoshinoya Arashiyama Pavilion',
				nights: 5,
				costUsd: 2150
			}
		}
	});
	assert.equal(state.isStreaming, false);
	assert.equal(state.pendingApproval.nights, 5);

	state = reduceAgUiEvent(state, {
		id: 'h3',
		type: 'RUN_FINISHED',
		payload: { status: 'cancelled' }
	});
	assert.equal(state.pendingApproval, null);
	assert.ok(state.messages.at(-1).content.includes('cancelled'));
});

test('filterInspectorEvents and updateTripNightsInState support 3-pane workbench interactions', () => {
	const sampleEvents = [
		{ id: '1', type: 'RUN_STARTED' },
		{ id: '2', type: 'TOOL_CALL' },
		{ id: '3', type: 'STEP_STARTED' },
		{ id: '4', type: 'TEXT_DELTA' }
	];
	assert.equal(filterInspectorEvents(sampleEvents, 'ALL').length, 4);
	assert.equal(filterInspectorEvents(sampleEvents, 'MCP').length, 1);
	assert.equal(filterInspectorEvents(sampleEvents, 'A2A').length, 1);
	assert.equal(filterInspectorEvents(sampleEvents, 'AG-UI').length, 2);
	assert.deepEqual(filterInspectorEvents(null, 'ALL'), []);

	const initial = createInitialWorkbenchState();
	const updated = updateTripNightsInState(initial.clusterState, 2);
	assert.equal(updated.nomadTrip.nights, 6);
	assert.equal(updateTripNightsInState(null, 1), null);
});

test('parseSseChunk and reduceAgUiEvent handle empty, malformed, STATUS, STEP_STARTED, and unknown events', () => {
	assert.deepEqual(parseSseChunk(''), { events: [], remainder: '' });
	assert.deepEqual(parseSseChunk('\n\ndata: \n\ndata: {invalid}\n\n'), {
		events: [],
		remainder: ''
	});

	let state = createInitialWorkbenchState();
	assert.equal(reduceAgUiEvent(state, null), state);

	state = reduceAgUiEvent(state, {
		id: 's1',
		type: 'STATUS',
		payload: { message: 'Executing MCP tool' }
	});
	assert.equal(state.statusText, 'Executing MCP tool');

	state = reduceAgUiEvent(state, {
		id: 's2',
		type: 'STEP_STARTED',
		payload: { agentName: 'Nomad-A2A-Specialist', skillId: 'convert-and-audit-budget' }
	});
	assert.equal(state.statusText, 'A2A Delegation: Nomad-A2A-Specialist (convert-and-audit-budget)');

	state = reduceAgUiEvent(state, {
		id: 's3',
		type: 'CUSTOM_EVENT',
		payload: {}
	});
	assert.equal(state.events.length, 3);

	state = reduceAgUiEvent(state, {
		id: 's4',
		type: 'RUN_FINISHED',
		payload: {}
	});
	assert.equal(state.isStreaming, false);
});

test('Nomad AG-UI Component Kit reducer populates timeline callTypes, reasoning, media, interaction, sources, followUps, and choice selection', () => {
	assert.ok(NOMAD_DESTINATIONS.length >= 3);
	assert.ok(SLASH_COMMANDS.length >= 8);
	assert.ok(PROTOCOL_CALL_TAXONOMY['mcp:tool']);
	assert.equal(resolveProtocolCallMeta('mcp:prompt').family, 'MCP');
	assert.equal(resolveProtocolCallMeta('a2a:artifact').family, 'A2A');
	assert.equal(resolveProtocolCallMeta(null, 'MCP').callType, 'mcp:tool');
	assert.equal(resolveProtocolCallMeta(null, 'A2A').callType, 'a2a:task');
	assert.equal(resolveProtocolCallMeta(null, 'LOCAL').callType, 'tool:function');

	let state = createInitialWorkbenchState('thread-nomad');
	assert.equal(state.clusterState.nomadTrip.activeCity, null);
	assert.equal(state.messages[0].timeline.length, 0);
	assert.equal(state.messages[0].media.length, 0);
	assert.equal(state.messages[0].sources.length, 0);
	assert.equal(state.messages[0].interaction.id, 'welcome-city-picker');
	assert.equal(state.messages[0].interaction.mode, 'single_select');
	assert.equal(state.messages[0].interaction.options.length, 4);
	assert.equal(state.messages[0].interaction.options[0].label, 'Beirut, Lebanon');
	state = reduceAgUiEvent(state, {
		id: 'n1',
		type: 'RUN_STARTED',
		payload: { status: 'started', llmMode: 'deterministic' }
	});
	state = reduceAgUiEvent(state, {
		id: 'n2',
		type: 'STATUS',
		payload: {
			phase: 'reasoning',
			durationMs: 1240,
			thought: 'Synthesizing Kyoto itinerary across MCP and A2A',
			message: 'Reasoning...'
		}
	});
	assert.equal(state.activeTurn.reasoning.durationMs, 1240);

	state = reduceAgUiEvent(state, {
		id: 'n3',
		type: 'TOOL_CALL',
		payload: {
			callId: 'cp1',
			tool: 'weekend-cultural-escape',
			protocol: 'MCP',
			callType: 'mcp:prompt',
			server: 'nomad-mcp-concierge (:8081)',
			args: { city: 'Kyoto' }
		}
	});
	state = reduceAgUiEvent(state, {
		id: 'n4',
		type: 'TOOL_RESULT',
		payload: {
			callId: 'cp1',
			tool: 'weekend-cultural-escape',
			callType: 'mcp:prompt',
			result: { expandedPrompt: 'Kyoto 3-day plan' }
		}
	});
	state = reduceAgUiEvent(state, {
		id: 'n5',
		type: 'STEP_STARTED',
		payload: {
			stepId: 'a2a-1',
			protocol: 'A2A',
			callType: 'a2a:task',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'convert-and-audit-budget'
		}
	});
	state = reduceAgUiEvent(state, {
		id: 'n6',
		type: 'STEP_FINISHED',
		payload: {
			stepId: 'a2a-1',
			protocol: 'A2A',
			callType: 'a2a:task',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'convert-and-audit-budget',
			summary: '$3,200 = ¥484,480 JPY'
		}
	});
	state = reduceAgUiEvent(state, {
		id: 'n7',
		type: 'STATE_DELTA',
		payload: {
			media: [{ type: 'audio', title: 'Haru no Umi', url: 'https://example.com/koto.m4a' }],
			interaction: {
				id: 'choice-kyoto',
				mode: 'single_select',
				prompt: 'Choose a Kyoto experience:',
				options: [
					{ id: 'arashiyama', label: 'Arashiyama Dawn Walk' },
					{ id: 'gion', label: 'Gion Koto Evening' }
				],
				actions: [{ id: 'act-1', label: 'Hold Suite', prompt: '/hitl Reserve Kyoto' }]
			},
			sources: [{ title: 'Open-Meteo', url: 'https://api.open-meteo.com', protocol: 'mcp:tool' }],
			followUps: ['/lisbon Plan Lisbon']
		}
	});
	state = reduceAgUiEvent(state, {
		id: 'n8',
		type: 'TEXT_DELTA',
		payload: { content: '### Kyoto Dossier Ready' }
	});
	state = reduceAgUiEvent(state, {
		id: 'n9',
		type: 'RUN_FINISHED',
		payload: { status: 'completed' }
	});

	const lastMsg = state.messages.at(-1);
	assert.equal(lastMsg.media.length, 1);
	assert.equal(lastMsg.sources.length, 1);
	assert.equal(lastMsg.followUps.length, 1);
	assert.equal(lastMsg.timeline.length, 2);
	assert.equal(lastMsg.timeline[0].callType, 'mcp:prompt');
	assert.equal(lastMsg.timeline[1].callType, 'a2a:task');

	// Single-select choice locks interaction
	state = selectTurnOption(state, lastMsg.id, 'arashiyama');
	const updatedMsg = state.messages.at(-1);
	assert.deepEqual(updatedMsg.interaction.selectedValues, ['arashiyama']);
	assert.equal(updatedMsg.interaction.submitted, true);

	// Multi-select choice toggles and locks via lockTurnInteraction
	state.messages[state.messages.length - 1] = {
		...updatedMsg,
		interaction: {
			...updatedMsg.interaction,
			mode: 'multi_select',
			selectedValues: [],
			submitted: false
		}
	};
	state = selectTurnOption(state, lastMsg.id, 'arashiyama');
	state = selectTurnOption(state, lastMsg.id, 'gion');
	assert.deepEqual(state.messages.at(-1).interaction.selectedValues, ['arashiyama', 'gion']);
	state = selectTurnOption(state, lastMsg.id, 'arashiyama');
	assert.deepEqual(state.messages.at(-1).interaction.selectedValues, ['gion']);
	state = lockTurnInteraction(state, lastMsg.id);
	assert.equal(state.messages.at(-1).interaction.submitted, true);

	// Duplicate stepId / callId events are deduplicated so ExecutionTimeline never throws each_key_duplicate
	let dupState = createInitialWorkbenchState('thread-dup');
	dupState = reduceAgUiEvent(dupState, { id: 'd1', type: 'RUN_STARTED', payload: {} });
	dupState = reduceAgUiEvent(dupState, {
		id: 'd2',
		type: 'STEP_STARTED',
		payload: { stepId: 'a2a-same', skillId: 'convert-and-audit-budget' }
	});
	dupState = reduceAgUiEvent(dupState, {
		id: 'd3',
		type: 'STEP_FINISHED',
		payload: { stepId: 'a2a-same', skillId: 'convert-and-audit-budget', summary: 'FX OK' }
	});
	dupState = reduceAgUiEvent(dupState, {
		id: 'd4',
		type: 'STEP_STARTED',
		payload: { stepId: 'a2a-same', skillId: 'verify-travel-advisory' }
	});
	dupState = reduceAgUiEvent(dupState, {
		id: 'd5',
		type: 'STEP_FINISHED',
		payload: { stepId: 'a2a-same', skillId: 'verify-travel-advisory', summary: 'Advisory OK' }
	});
	const timelineIds = dupState.activeTurn.timeline.map((item) => item.id);
	assert.equal(new Set(timelineIds).size, timelineIds.length, 'Timeline IDs must be strictly unique');

	// Consecutive TEXT_DELTA events coalesce in state.events while accumulating streamBuffer
	let streamState = createInitialWorkbenchState('thread-coalesce');
	streamState = reduceAgUiEvent(streamState, { id: 'c1', type: 'RUN_STARTED', payload: {} });
	streamState = reduceAgUiEvent(streamState, {
		id: 'c2',
		type: 'TEXT_DELTA',
		payload: { content: 'Hello ' }
	});
	streamState = reduceAgUiEvent(streamState, {
		id: 'c3',
		type: 'TEXT_DELTA',
		payload: { content: 'Oaxaca ' }
	});
	streamState = reduceAgUiEvent(streamState, {
		id: 'c4',
		type: 'TEXT_DELTA',
		payload: { content: 'world!' }
	});
	assert.equal(streamState.streamBuffer, 'Hello Oaxaca world!');
	assert.equal(streamState.events.length, 2);
	assert.equal(streamState.events[1].payload.content, 'Hello Oaxaca world!');
	assert.equal(streamState.events[1].payload.chunks, 3);
});

test('WebSocket transport, conversational memory, LangChain4j guardrails/RAG/specialists, and Trip Dossier Markdown export', () => {
	assert.equal(resolveProtocolCallMeta('guardrail:input').family, 'GUARDRAIL');
	assert.equal(resolveProtocolCallMeta('guardrail:output').family, 'GUARDRAIL');
	assert.equal(resolveProtocolCallMeta('rag:retrieval').family, 'RAG');
	assert.equal(resolveProtocolCallMeta('agentic:specialist').family, 'AGENTIC');
	assert.equal(resolveProtocolCallMeta(null, 'GUARDRAIL').callType, 'guardrail:input');
	assert.equal(resolveProtocolCallMeta(null, 'RAG').callType, 'rag:retrieval');
	assert.equal(resolveProtocolCallMeta(null, 'AGENTIC').callType, 'agentic:specialist');

	let state = createInitialWorkbenchState('thread-ws');
	state = reduceAgUiEvent(state, {
		id: 'ws-0',
		type: 'WS_CONNECTED',
		payload: {
			transport: 'websocket',
			endpoint: '/api/agent/ws',
			memoryTurns: 0,
			clusterState: { nomadTrip: { activeCity: 'Kyoto', nights: 4, homeCurrency: 'USD' } }
		}
	});
	assert.equal(state.clusterState.nomadTrip.activeCity, null);

	state = reduceAgUiEvent(state, {
		id: 'ws-1',
		type: 'WS_CONNECTED',
		payload: { transport: 'websocket', endpoint: '/api/agent/ws', memoryTurns: 2 }
	});
	assert.equal(state.wsConnected, true);
	assert.equal(state.transportMode, 'ws');
	assert.equal(state.memoryTurns, 2);

	state = reduceAgUiEvent(state, {
		id: 'ws-pong',
		type: 'PONG',
		payload: { timestamp: 12345 }
	});
	assert.equal(state.wsConnected, true);

	state = recordClientOutboundEvent(state, 'UPDATE_TRIP_PARAMS', { nights: 6, homeCurrency: 'EUR' });
	assert.equal(state.events.at(-1).direction, 'client');
	assert.equal(filterInspectorEvents(state.events, 'CLIENT').length, 1);

	state = reduceAgUiEvent(state, {
		id: 'sd-params',
		type: 'STATE_DELTA',
		payload: {
			status: 'params_updated',
			memoryTurns: 4,
			clusterState: {
				uri: 'itinerary://active-trip',
				nomadTrip: {
					activeCity: 'Lisbon',
					country: 'Portugal',
					nights: 6,
					homeCurrency: 'EUR',
					allocatedUsd: 2640,
					budgetUsd: 3200
				},
				preferences: {
					dietary: 'Seasonal Vegetarian & Shojin Ryori',
					style: 'Modernist Concrete & Tadao Ando Sanctuaries',
					homeCurrency: 'EUR'
				}
			}
		}
	});
	assert.equal(state.memoryTurns, 4);
	assert.equal(state.clusterState.preferences.homeCurrency, 'EUR');
	assert.equal(state.clusterState.nomadTrip.nights, 6);
	assert.ok(state.statusText.includes('WebSocket'));

	const md = formatTripDossierMarkdown(state.clusterState, state.messages);
	assert.ok(md.includes('# Nomad Cultural & Architectural Dossier — Lisbon, Portugal'));
	assert.ok(md.includes('Seasonal Vegetarian & Shojin Ryori'));

	state = reduceAgUiEvent(state, {
		id: 'sd-reset',
		type: 'STATE_DELTA',
		payload: {
			status: 'memory_reset',
			memoryTurns: 0,
			clusterState: { nomadTrip: { activeCity: 'Kyoto', nights: 4, homeCurrency: 'USD' } }
		}
	});
	assert.equal(state.memoryTurns, 0);
	assert.equal(state.clusterState.nomadTrip.activeCity, null);
	assert.equal(state.statusText, 'Conversational memory & traveler profile reset');

	// Destination-free greeting turn ("My name is Fady") preserves activeCity: null while updating travelerName
	state = reduceAgUiEvent(state, {
		id: 'run-fady-1',
		type: 'RUN_STARTED',
		payload: { status: 'started', llmMode: 'deterministic' }
	});
	state = reduceAgUiEvent(state, {
		id: 'snap-fady-1',
		type: 'STATE_SNAPSHOT',
		payload: {
			awaitingDestination: true,
			memoryTurns: 0,
			clusterState: {
				nomadTrip: { activeCity: 'Kyoto', nights: 4, homeCurrency: 'USD' },
				preferences: { travelerName: 'Fady', style: 'Architectural Minimalist' }
			}
		}
	});
	assert.equal(state.clusterState.nomadTrip.activeCity, null);
	assert.equal(state.clusterState.preferences.travelerName, 'Fady');
	state = reduceAgUiEvent(state, {
		id: 'delta-fady-1',
		type: 'STATE_DELTA',
		payload: {
			awaitingDestination: true,
			memoryTurns: 2,
			clusterState: {
				nomadTrip: { activeCity: 'Kyoto', nights: 4, homeCurrency: 'USD' },
				preferences: { travelerName: 'Fady', style: 'Architectural Minimalist' }
			}
		}
	});
	assert.equal(state.clusterState.nomadTrip.activeCity, null);
	assert.equal(state.clusterState.preferences.travelerName, 'Fady');
});
