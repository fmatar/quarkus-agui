import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
	AGUI_ACTIVITY_TYPES,
	EventType,
	HttpAgent,
	NOMAD_DESTINATIONS,
	PROTOCOL_CALL_TAXONOMY,
	SCENARIO_PRESETS,
	SLASH_COMMANDS,
	WebSocketAgent,
	applyJsonPatch,
	buildHitlResumeInput,
	buildPlaceMapLinks,
	buildRunAgentInput,
	createInitialWorkbenchState,
	deriveDossierActivities,
	filterInspectorEvents,
	formatTripDossierMarkdown,
	lockTurnInteraction,
	parseSseChunk,
	recordClientOutboundEvent,
	reduceAgUiEvent,
	resolveEventProtocolMeta,
	resolveProtocolCallMeta,
	selectTurnOption,
	summarizeHealthReport,
	toRootStateDeltaOps,
	updateTripNightsInState,
	useAgUiAgent
} from '../src/lib/agui-client.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

test('agui-client re-exports canonical @agui/svelte primitives and builds RunAgentInput + Resume envelopes', () => {
	assert.equal(typeof HttpAgent, 'function');
	assert.equal(typeof WebSocketAgent, 'function');
	assert.equal(EventType.RUN_STARTED, 'RUN_STARTED');
	assert.equal(EventType.TEXT_MESSAGE_CONTENT, 'TEXT_MESSAGE_CONTENT');
	assert.equal(typeof applyJsonPatch, 'function');
	assert.equal(typeof resolveEventProtocolMeta, 'function');

	const runInput = buildRunAgentInput({
		threadId: 'th-1',
		runId: 'run-fixed',
		prompt: 'Plan Kyoto',
		state: { uri: 'itinerary://active-trip' },
		llmMode: 'ollama:llama3.2:latest'
	});
	assert.equal(runInput.threadId, 'th-1');
	assert.equal(runInput.runId, 'run-fixed');
	assert.equal(runInput.messages.length, 1);
	assert.equal(runInput.messages[0].role, 'user');
	assert.equal(runInput.messages[0].content, 'Plan Kyoto');
	assert.deepEqual(runInput.tools, []);
	assert.deepEqual(runInput.context, []);
	assert.deepEqual(runInput.forwardedProps, { llmMode: 'ollama:llama3.2:latest' });

	const emptyPromptInput = buildRunAgentInput({});
	assert.equal(emptyPromptInput.threadId, 'thread-nomad-1');
	assert.ok(emptyPromptInput.runId.startsWith('run-'));
	assert.deepEqual(emptyPromptInput.messages, []);
	assert.deepEqual(emptyPromptInput.forwardedProps, {});

	const approvedResume = buildHitlResumeInput({
		threadId: 'th-hitl',
		runId: 'res-1',
		interruptId: 'call-hitl-1',
		approved: true,
		modifiedArgs: { nights: 5, costUsd: 2100 },
		state: { locale: 'en' },
		llmMode: 'ollama:llama3.2:latest'
	});
	assert.equal(approvedResume.threadId, 'th-hitl');
	assert.equal(approvedResume.runId, 'res-1');
	assert.equal(approvedResume.resume.length, 1);
	assert.equal(approvedResume.resume[0].interruptId, 'call-hitl-1');
	assert.equal(approvedResume.resume[0].status, 'resolved');
	assert.equal(approvedResume.resume[0].payload.nights, 5);
	assert.equal(approvedResume.resume[0].payload.costUsd, 2100);

	const declinedResume = buildHitlResumeInput({
		threadId: 'th-hitl',
		approved: false,
		reason: 'Budget changed'
	});
	assert.equal(declinedResume.resume[0].interruptId, 'hitl-approval');
	assert.equal(declinedResume.resume[0].status, 'cancelled');
	assert.equal(declinedResume.resume[0].payload.reason, 'Budget changed');

	assert.deepEqual(toRootStateDeltaOps(null), []);
	assert.deepEqual(toRootStateDeltaOps('invalid'), []);
	assert.deepEqual(toRootStateDeltaOps({ status: 'params_updated', memoryTurns: 2 }), [
		{ op: 'add', path: '/status', value: 'params_updated' },
		{ op: 'add', path: '/memoryTurns', value: 2 }
	]);
});

test('parseSseChunk splits multi-frame canonical SSE chunks on double newline and preserves remainder', () => {
	const raw =
		'data: {"type":"RUN_STARTED","threadId":"t1","runId":"r1","rawEvent":{"llmMode":"ollama:llama3.2:latest"}}\r\n\r\n' +
		'data:data: {"type":"TEXT_MESSAGE_CONTENT","messageId":"m1","delta":"Hello "}\n\n' +
		'data: {"type":"TEXT_MESSAGE_CONTENT"';

	const { events, remainder } = parseSseChunk(raw);
	assert.equal(events.length, 2);
	assert.equal(events[0].type, 'RUN_STARTED');
	assert.equal(events[1].delta, 'Hello ');
	assert.equal(remainder, 'data: {"type":"TEXT_MESSAGE_CONTENT"');
});

test('reduceAgUiEvent transitions across canonical RUN_STARTED, STATE_SNAPSHOT, TOOL_CALL_*, STEP_*, TEXT_MESSAGE_CONTENT, and RUN_FINISHED', () => {
	let state = createInitialWorkbenchState('thread-test');
	assert.equal(state.isStreaming, false);

	state = reduceAgUiEvent(state, {
		type: 'RUN_STARTED',
		threadId: 'thread-test',
		runId: 'r-1',
		rawEvent: { llmMode: 'ollama:llama3.2:latest' }
	});
	assert.equal(state.isStreaming, true);
	assert.equal(state.statusText, 'Run started (ollama:llama3.2:latest)');

	state = reduceAgUiEvent(state, {
		type: 'STATE_SNAPSHOT',
		snapshot: {
			tripDossierState: {
				uri: 'itinerary://active-trip',
				nomadTrip: { activeCity: 'Kyoto', country: 'Japan', nights: 4, temperatureC: 18.4 }
			},
			memoryTurns: 2
		}
	});
	assert.equal(state.clusterState.nomadTrip.nights, 4);
	assert.equal(state.memoryTurns, 2);

	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_START',
		toolCallId: 'c1',
		toolCallName: 'fetchCityWeather',
		rawEvent: {
			callId: 'c1',
			tool: 'fetchCityWeather',
			protocol: 'MCP',
			callType: 'mcp:tool',
			server: 'nomad-mcp-concierge (:8081)',
			args: {}
		}
	});
	assert.equal(state.mcpCalls.length, 1);
	assert.equal(state.mcpCalls[0].tool, 'fetchCityWeather');
	assert.equal(state.mcpCalls[0].status, 'running');

	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_ARGS',
		toolCallId: 'c1',
		delta: '{"city":"Ky'
	});
	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_ARGS',
		toolCallId: 'c1',
		delta: 'oto"}'
	});
	assert.deepEqual(state.mcpCalls[0].args, { city: 'Kyoto' });
	assert.deepEqual(state.activeTurn.timeline[0].args, { city: 'Kyoto' });

	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_END',
		toolCallId: 'c1'
	});

	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-1',
		toolCallId: 'c1',
		content: JSON.stringify({
			city: 'Kyoto',
			temperatureC: 18.4,
			condition: 'Clear Cedar & Autumn Mist'
		}),
		role: 'tool',
		rawEvent: {
			callId: 'c1',
			tool: 'fetchCityWeather',
			protocol: 'MCP',
			callType: 'mcp:tool',
			status: 'OK'
		}
	});
	assert.equal(state.mcpCalls[0].status, 'completed');
	assert.ok(state.activeTurn.timeline[0].detail.includes('18.4°C'));

	state = reduceAgUiEvent(state, {
		type: 'STEP_STARTED',
		stepName: 'convert-and-audit-budget',
		rawEvent: {
			stepId: 's1',
			protocol: 'A2A',
			callType: 'a2a:task',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'convert-and-audit-budget'
		}
	});
	assert.equal(state.statusText, 'A2A Delegation: Nomad-A2A-Specialist (convert-and-audit-budget)');

	state = reduceAgUiEvent(state, {
		type: 'ACTIVITY_SNAPSHOT',
		messageId: 'act-1',
		activityType: 'a2a.artifact',
		content: { rate: 151.4 },
		rawEvent: { protocol: 'A2A', callType: 'a2a:artifact', artifactName: 'fx-audit' }
	});
	assert.ok(state.activeTurn.timeline.some((item) => item.id === 'act-1'));

	state = reduceAgUiEvent(state, {
		type: 'STEP_FINISHED',
		stepName: 'convert-and-audit-budget',
		rawEvent: {
			stepId: 's1',
			protocol: 'A2A',
			callType: 'a2a:task',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'convert-and-audit-budget',
			summary: 'Frankfurter FX verified: $3,200 USD = ¥484,480 JPY'
		}
	});
	assert.equal(state.a2aSteps.length, 1);
	assert.equal(state.a2aSteps[0].agentName, 'Nomad-A2A-Specialist');

	state = reduceAgUiEvent(state, {
		type: 'TEXT_MESSAGE_CONTENT',
		messageId: 'm1',
		delta: 'Kyoto dossier ready.'
	});
	assert.equal(state.streamBuffer, 'Kyoto dossier ready.');

	state = reduceAgUiEvent(state, {
		type: 'RUN_FINISHED',
		threadId: 'thread-test',
		runId: 'r-1',
		outcome: { type: 'success' },
		result: { status: 'completed', memoryTurns: 4 }
	});
	assert.equal(state.isStreaming, false);
	assert.equal(state.memoryTurns, 4);
	assert.equal(state.messages.at(-1).role, 'assistant');
	assert.equal(state.messages.at(-1).content, 'Kyoto dossier ready.');
});

test('SCENARIO_PRESETS exposes five tri-protocol showcase scenarios including HITL approval', () => {
	assert.equal(SCENARIO_PRESETS.length, 5);
	assert.ok(SCENARIO_PRESETS.every((s) => s.id && s.label && s.prompt));
});

test('reduceAgUiEvent handles canonical HITL InterruptOutcome, cancelled RunFinished, and RUN_ERROR guardrails', () => {
	let state = createInitialWorkbenchState('thread-hitl');
	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_START',
		toolCallId: 'ch1',
		toolCallName: 'requestSanctuaryReservation',
		rawEvent: {
			callId: 'ch1',
			tool: 'requestSanctuaryReservation',
			callType: 'hitl:approval',
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
		type: 'RUN_FINISHED',
		threadId: 'thread-hitl',
		runId: 'r-hitl-1',
		outcome: {
			type: 'interrupt',
			interrupts: [
				{
					id: 'int-ch1',
					reason: 'human_approval',
					message: 'Confirm sanctuary hold',
					toolCallId: 'ch1',
					metadata: {
						callId: 'ch1',
						toolName: 'updateTripItinerary',
						city: 'Kyoto',
						stopTitle: 'Hoshinoya Arashiyama Pavilion',
						nights: 5,
						costUsd: 2150
					}
				}
			]
		},
		result: { status: 'interrupted', memoryTurns: 2 }
	});
	assert.equal(state.isStreaming, false);
	assert.equal(state.pendingApproval.interruptId, 'int-ch1');
	assert.equal(state.pendingApproval.nights, 5);
	assert.equal(state.statusText, 'HITL Gate — Awaiting Traveler Approval');

	state = reduceAgUiEvent(state, {
		type: 'RUN_FINISHED',
		threadId: 'thread-hitl',
		runId: 'r-hitl-2',
		outcome: { type: 'success' },
		result: { status: 'cancelled', memoryTurns: 2 }
	});
	assert.equal(state.pendingApproval, null);
	assert.ok(state.messages.at(-1).content.includes('cancelled'));

	// Guardrail RUN_ERROR surfaces clear status and message
	state = reduceAgUiEvent(state, {
		type: 'RUN_STARTED',
		threadId: 'thread-hitl',
		runId: 'r-guard'
	});
	state = reduceAgUiEvent(state, {
		type: 'RUN_ERROR',
		message: 'Prompt blocked by TravelSafetyInputGuardrail',
		code: 'GUARDRAIL_BLOCKED'
	});
	assert.equal(state.isStreaming, false);
	assert.equal(state.statusText, 'Blocked by Guardrail (GUARDRAIL_BLOCKED)');
	assert.ok(state.messages.at(-1).content.includes('Prompt blocked'));
});

test('filterInspectorEvents and updateTripNightsInState support 3-pane workbench interactions', () => {
	const sampleEvents = [
		{ id: '1', type: 'RUN_STARTED' },
		{ id: '2', type: 'TOOL_CALL_START', rawEvent: { protocol: 'MCP', callType: 'mcp:tool' } },
		{ id: '3', type: 'STEP_STARTED', rawEvent: { protocol: 'A2A', callType: 'a2a:task' } },
		{ id: '4', type: 'TEXT_MESSAGE_CONTENT', delta: 'Hi' }
	];
	assert.equal(filterInspectorEvents(sampleEvents, 'ALL').length, 4);
	assert.equal(filterInspectorEvents(sampleEvents, 'MCP').length, 1);
	assert.equal(filterInspectorEvents(sampleEvents, 'A2A').length, 1);
	assert.equal(filterInspectorEvents(sampleEvents, 'AG-UI').length, 2);
	assert.deepEqual(filterInspectorEvents(null, 'ALL'), []);

	const initial = createInitialWorkbenchState();
	assert.equal(initial.tripDossierState.nomadTrip.nights, null);
	assert.equal(initial.tripDossierState.nomadTrip.budgetUsd, null);
	assert.equal(initial.tripDossierState.nomadTrip.homeCurrency, null);
	const fromUnset = updateTripNightsInState(initial.tripDossierState, 2);
	assert.equal(fromUnset.nomadTrip.nights, 2);
	const clampedLow = updateTripNightsInState({ nomadTrip: { nights: 2, budgetUsd: 2000 } }, -10);
	assert.equal(clampedLow.nomadTrip.nights, 1);
	assert.equal(clampedLow.nomadTrip.allocatedUsd, 420);
	const clampedHigh = updateTripNightsInState({ nomadTrip: { nights: 28, budgetUsd: 9000 } }, 10);
	assert.equal(clampedHigh.nomadTrip.nights, 30);
	assert.equal(clampedHigh.nomadTrip.allocatedUsd, 12600);
	assert.equal(updateTripNightsInState(null, 1), null);
});

test('parseSseChunk and reduceAgUiEvent handle empty, malformed, REASONING_MESSAGE_CONTENT, and unknown events', () => {
	assert.deepEqual(parseSseChunk(''), { events: [], remainder: '' });
	assert.deepEqual(parseSseChunk('\n\ndata: \n\ndata: {invalid}\n\n'), {
		events: [],
		remainder: ''
	});

	let state = createInitialWorkbenchState();
	assert.equal(reduceAgUiEvent(state, null), state);
	assert.equal(reduceAgUiEvent(state, {}), state);

	state = reduceAgUiEvent(state, {
		type: 'REASONING_MESSAGE_CONTENT',
		messageId: 'reason-1',
		delta: 'Checking missing parameters... '
	});
	state = reduceAgUiEvent(state, {
		type: 'REASONING_MESSAGE_CONTENT',
		messageId: 'reason-1',
		delta: 'duration and budget.'
	});
	assert.equal(state.activeTurn.reasoning.thought, 'Checking missing parameters... duration and budget.');
	assert.equal(state.events.length, 1);

	state = reduceAgUiEvent(state, {
		type: 'CUSTOM',
		name: 'ping',
		value: {}
	});
	assert.equal(state.events.length, 2);
});

test('Nomad reducer populates RFC 6902 STATE_DELTA fields, media, choices, deduplicated timeline IDs, and coalesced TEXT_MESSAGE_CONTENT', () => {
	assert.ok(NOMAD_DESTINATIONS.length >= 3);
	assert.ok(SLASH_COMMANDS.length >= 8);
	assert.ok(PROTOCOL_CALL_TAXONOMY['mcp:tool']);
	assert.equal(resolveProtocolCallMeta('mcp:prompt').family, 'MCP');
	assert.equal(resolveProtocolCallMeta('a2a:artifact').family, 'A2A');

	let state = createInitialWorkbenchState('thread-nomad');
	state = reduceAgUiEvent(state, {
		type: 'RUN_STARTED',
		rawEvent: { llmMode: 'ollama:llama3.2:latest' }
	});
	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_START',
		toolCallId: 'cp1',
		toolCallName: 'weekend-cultural-escape',
		rawEvent: {
			callId: 'cp1',
			tool: 'weekend-cultural-escape',
			protocol: 'MCP',
			callType: 'mcp:prompt',
			server: 'nomad-mcp-concierge (:8081)',
			args: { city: 'Kyoto' }
		}
	});
	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-cp1',
		toolCallId: 'cp1',
		content: JSON.stringify({ expandedPrompt: 'Kyoto 3-day plan' }),
		rawEvent: {
			callId: 'cp1',
			tool: 'weekend-cultural-escape',
			callType: 'mcp:prompt'
		}
	});
	state = reduceAgUiEvent(state, {
		type: 'STEP_STARTED',
		stepName: 'convert-and-audit-budget',
		rawEvent: {
			stepId: 'a2a-1',
			protocol: 'A2A',
			callType: 'a2a:task',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'convert-and-audit-budget'
		}
	});
	state = reduceAgUiEvent(state, {
		type: 'STEP_FINISHED',
		stepName: 'convert-and-audit-budget',
		rawEvent: {
			stepId: 'a2a-1',
			protocol: 'A2A',
			callType: 'a2a:task',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'convert-and-audit-budget',
			summary: '$3,200 = ¥484,480 JPY'
		}
	});
	state = reduceAgUiEvent(state, {
		type: 'STATE_DELTA',
		delta: toRootStateDeltaOps({
			media: [{ type: 'audio', title: 'Haru no Umi', url: 'https://example.com/koto.m4a' }],
			interaction: {
				id: 'choice-kyoto',
				mode: 'single_select',
				prompt: 'Choose a Kyoto experience:',
				options: [
					{ id: 'arashiyama', label: 'Arashiyama Dawn Walk' },
					{ id: 'gion', label: 'Gion Koto Evening' }
				]
			},
			sources: [{ title: 'Open-Meteo', url: 'https://api.open-meteo.com', protocol: 'mcp:tool' }],
			followUps: ['/lisbon Plan Lisbon']
		})
	});
	state = reduceAgUiEvent(state, {
		type: 'TEXT_MESSAGE_CONTENT',
		messageId: 'm1',
		delta: '### Kyoto Dossier Ready'
	});
	state = reduceAgUiEvent(state, {
		type: 'RUN_FINISHED',
		outcome: { type: 'success' },
		result: { status: 'completed' }
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

	// Duplicate stepId / toolCallId events are deduplicated
	let dupState = createInitialWorkbenchState('thread-dup');
	dupState = reduceAgUiEvent(dupState, { type: 'RUN_STARTED' });
	dupState = reduceAgUiEvent(dupState, {
		type: 'STEP_STARTED',
		stepName: 'convert-and-audit-budget',
		rawEvent: { stepId: 'a2a-same', skillId: 'convert-and-audit-budget' }
	});
	dupState = reduceAgUiEvent(dupState, {
		type: 'STEP_FINISHED',
		stepName: 'convert-and-audit-budget',
		rawEvent: { stepId: 'a2a-same', skillId: 'convert-and-audit-budget', summary: 'FX OK' }
	});
	dupState = reduceAgUiEvent(dupState, {
		type: 'STEP_STARTED',
		stepName: 'verify-travel-advisory',
		rawEvent: { stepId: 'a2a-same', skillId: 'verify-travel-advisory' }
	});
	dupState = reduceAgUiEvent(dupState, {
		type: 'STEP_FINISHED',
		stepName: 'verify-travel-advisory',
		rawEvent: { stepId: 'a2a-same', skillId: 'verify-travel-advisory', summary: 'Advisory OK' }
	});
	const timelineIds = dupState.activeTurn.timeline.map((item) => item.id);
	assert.equal(new Set(timelineIds).size, timelineIds.length, 'Timeline IDs must be strictly unique');

	// Consecutive TEXT_MESSAGE_CONTENT events coalesce in state.events while accumulating streamBuffer
	let streamState = createInitialWorkbenchState('thread-coalesce');
	streamState = reduceAgUiEvent(streamState, { type: 'RUN_STARTED' });
	streamState = reduceAgUiEvent(streamState, { type: 'TEXT_MESSAGE_CONTENT', messageId: 'm1', delta: 'Hello ' });
	streamState = reduceAgUiEvent(streamState, { type: 'TEXT_MESSAGE_CONTENT', messageId: 'm1', delta: 'Oaxaca ' });
	streamState = reduceAgUiEvent(streamState, { type: 'TEXT_MESSAGE_CONTENT', messageId: 'm1', delta: 'world!' });
	assert.equal(streamState.streamBuffer, 'Hello Oaxaca world!');
	assert.equal(streamState.events.length, 2);
	assert.equal(streamState.events[1].delta, 'Hello Oaxaca world!');
	assert.equal(streamState.events[1].chunks, 3);

	// TOOL_CALL_RESULT for discoverLocalCultureAndMedia extracts image & audio media and updates dossier
	let mediaState = createInitialWorkbenchState('thread-media');
	mediaState = reduceAgUiEvent(mediaState, { type: 'RUN_STARTED' });
	mediaState = reduceAgUiEvent(mediaState, {
		type: 'TOOL_CALL_START',
		toolCallId: 'call-c1',
		toolCallName: 'discoverLocalCultureAndMedia',
		rawEvent: { callId: 'call-c1', tool: 'discoverLocalCultureAndMedia', callType: 'mcp:tool' }
	});
	mediaState = reduceAgUiEvent(mediaState, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-c1',
		toolCallId: 'call-c1',
		content: JSON.stringify({
			destination: 'Beirut',
			heroImageUrl: 'https://upload.wikimedia.org/beirut.jpg',
			audioTrack: {
				trackName: 'Li Beirut',
				artistName: 'Fairuz',
				previewUrl: 'https://audio-ssl.itunes.apple.com/fairuz.m4a',
				durationSeconds: 30
			}
		}),
		rawEvent: {
			callId: 'call-c1',
			tool: 'discoverLocalCultureAndMedia',
			status: 'OK'
		}
	});
	assert.equal(mediaState.activeTurn.media.length, 2);
	assert.equal(mediaState.activeTurn.media[0].type, 'image');
	assert.equal(mediaState.activeTurn.media[1].type, 'audio');
	assert.equal(mediaState.tripDossierState.nomadTrip.activeCity, 'Beirut');
	assert.equal(mediaState.tripDossierState.nomadTrip.soundtrack.trackName, 'Li Beirut');
	mediaState = reduceAgUiEvent(mediaState, {
		type: 'STATE_DELTA',
		delta: toRootStateDeltaOps({ media: [], sources: [], followUps: [] })
	});
	assert.equal(mediaState.activeTurn.media.length, 2);
});

test('WebSocket control frames, RFC 6902 STATE_DELTA params/reset, tool result summaries, and Markdown export', () => {
	let state = createInitialWorkbenchState('thread-ws');
	state = reduceAgUiEvent(state, {
		type: 'WS_CONNECTED',
		memoryTurns: 0,
		tripDossierState: { nomadTrip: { activeCity: 'Kyoto', nights: 4, homeCurrency: 'USD' } }
	});
	assert.equal(state.clusterState.nomadTrip.activeCity, null);

	state = reduceAgUiEvent(state, {
		type: 'WS_CONNECTED',
		memoryTurns: 2,
		tripDossierState: { nomadTrip: { activeCity: 'Lisbon', nights: 4, homeCurrency: 'EUR' } }
	});
	assert.equal(state.wsConnected, true);
	assert.equal(state.transportMode, 'ws');
	assert.equal(state.memoryTurns, 2);
	assert.equal(state.clusterState.nomadTrip.activeCity, 'Lisbon');

	state = recordClientOutboundEvent(state, 'UPDATE_TRIP_PARAMS', { nights: 6, homeCurrency: 'EUR' });
	assert.equal(state.events.at(-1).direction, 'client');
	assert.equal(filterInspectorEvents(state.events, 'CLIENT').length, 1);

	const updatedDossier = {
		uri: 'itinerary://active-trip',
		nomadTrip: {
			activeCity: 'Lisbon',
			country: 'Portugal',
			currency: 'EUR',
			exchangeRate: 0.92,
			temperatureC: 21.5,
			weatherCondition: 'Sunny Atlantic Breeze',
			nights: 6,
			stopTitle: 'Alfama Terrace',
			homeCurrency: 'EUR',
			allocatedUsd: 2640,
			budgetUsd: 3200,
			soundtrack: { trackName: 'Fado Song', artistName: 'Amalia' }
		},
		preferences: {
			dietary: 'Seasonal Vegetarian & Shojin Ryori',
			style: 'Modernist Concrete & Tadao Ando Sanctuaries',
			musicTaste: 'Fado & Chamber',
			pace: 'Unhurried',
			homeCurrency: 'EUR'
		}
	};

	state = reduceAgUiEvent(state, {
		type: 'STATE_DELTA',
		delta: toRootStateDeltaOps({
			status: 'params_updated',
			memoryTurns: 4,
			tripDossierState: updatedDossier,
			clusterState: updatedDossier
		}),
		rawEvent: { status: 'params_updated', memoryTurns: 4 }
	});
	assert.equal(state.memoryTurns, 4);
	assert.equal(state.clusterState.preferences.homeCurrency, 'EUR');
	assert.equal(state.clusterState.nomadTrip.nights, 6);
	assert.ok(state.statusText.includes('WebSocket'));

	const md = formatTripDossierMarkdown(state.clusterState, state.messages);
	assert.ok(md.includes('# Nomad Cultural & Architectural Dossier — Lisbon, Portugal'));
	assert.ok(md.includes('Seasonal Vegetarian & Shojin Ryori'));
	assert.ok(md.includes('21.5°C'));
	const emptyMd = formatTripDossierMarkdown(null, []);
	assert.ok(emptyMd.includes('Destination Pending'));

	// Exercise additional tool summary variants
	for (const [tool, result, expected] of [
		['updateTripItinerary', { stopTitle: 'Alfama', nights: 4, allocatedUsd: 1600 }, 'Reserved Alfama'],
		['TravelSafetyAndInjectionGuardrail', { reason: 'Input clean' }, 'Input clean'],
		['CurrencyAndGroundingGuardrail', { verifiedCity: 'Lisbon', verifiedIsoCurrency: 'EUR', verifiedTemperatureC: 21 }, 'Verified Lisbon'],
		['retrieveCulturalFieldNotes', { chunksRetrieved: 4, topSimilarity: 0.95 }, '4 field notes'],
		['sampleAcousticMood', { sampledMood: 'Oud Dusk' }, 'Oud Dusk'],
		['saveTravelerPreferences', { travelerName: 'Fady' }, 'Fady'],
		['customArchTool', { city: 'Beirut', architecturalHighlight: 'Sursock' }, 'Sursock'],
		['customStyleTool', { style: 'Minimalist', pace: 'Slow' }, 'Minimalist']
	]) {
		state = reduceAgUiEvent(state, {
			type: 'TOOL_CALL_START',
			toolCallId: `tc-${tool}`,
			toolCallName: tool,
			rawEvent: { callId: `tc-${tool}`, tool }
		});
		state = reduceAgUiEvent(state, {
			type: 'TOOL_CALL_RESULT',
			messageId: `tm-${tool}`,
			toolCallId: `tc-${tool}`,
			content: JSON.stringify(result),
			rawEvent: { callId: `tc-${tool}`, tool, result }
		});
		assert.ok(state.activeTurn.timeline.at(-1).detail.includes(expected));
	}

	state = reduceAgUiEvent(state, {
		type: 'STATE_DELTA',
		delta: toRootStateDeltaOps({ status: 'memory_reset', memoryTurns: 0 }),
		rawEvent: { status: 'memory_reset' }
	});
	assert.equal(state.memoryTurns, 0);
	assert.equal(state.clusterState.nomadTrip.activeCity, null);
	assert.equal(state.statusText, 'Conversational memory & traveler profile reset');

	// Params update when no city or turns exist yet keeps activeCity null
	state = reduceAgUiEvent(state, {
		type: 'STATE_DELTA',
		delta: toRootStateDeltaOps({
			status: 'params_updated',
			memoryTurns: 0,
			tripDossierState: { nomadTrip: { activeCity: 'Kyoto', nights: 3, homeCurrency: 'CHF' } }
		}),
		rawEvent: { status: 'params_updated', memoryTurns: 0 }
	});
	assert.equal(state.clusterState.nomadTrip.activeCity, null);
	assert.equal(state.clusterState.nomadTrip.nights, 3);

	// awaitingDestination keeps activeCity null while updating travelerName
	state = reduceAgUiEvent(state, {
		type: 'STATE_SNAPSHOT',
		snapshot: {
			awaitingDestination: true,
			memoryTurns: 2,
			tripDossierState: {
				nomadTrip: { activeCity: 'Kyoto', nights: 4, homeCurrency: 'USD' },
				preferences: { travelerName: 'Fady', style: 'Architectural Minimalist' }
			}
		}
	});
	assert.equal(state.clusterState.nomadTrip.activeCity, null);
	assert.equal(state.clusterState.preferences.travelerName, 'Fady');
});

test('Zero assumed defaults, honest upstream error surfacing, summarizeHealthReport, and canonical AgentChatContainer wiring (#85)', () => {
	const srcLibDir = path.resolve(__dirname, '../src/lib');
	const componentsDir = path.join(srcLibDir, 'components');
	assert.equal(fs.existsSync(path.join(componentsDir, 'TripDossierPanel.svelte')), true);
	assert.equal(fs.existsSync(path.join(componentsDir, 'ClusterStatePanel.svelte')), false);

	let state = createInitialWorkbenchState('thread-zero-defaults');
	for (const dest of NOMAD_DESTINATIONS) {
		assert.equal(dest.temperatureC ?? null, null);
		assert.ok(!/4-night|3-day/i.test(dest.prompt));
	}

	state = reduceAgUiEvent(state, {
		type: 'RUN_STARTED',
		rawEvent: { llmMode: 'ollama:llama3.2:latest' }
	});
	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_START',
		toolCallId: 'mcp-w1',
		toolCallName: 'fetchCityWeather',
		rawEvent: { callId: 'mcp-w1', tool: 'fetchCityWeather', callType: 'mcp:tool', args: { city: 'Paris' } }
	});
	state = reduceAgUiEvent(state, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-w1',
		toolCallId: 'mcp-w1',
		content: JSON.stringify({ status: 'ERROR', isError: true, error: 'Open-Meteo Weather API unreachable' }),
		rawEvent: {
			callId: 'mcp-w1',
			tool: 'fetchCityWeather',
			callType: 'mcp:tool',
			status: 'ERROR',
			isError: true,
			error: 'Open-Meteo Weather API unreachable'
		}
	});
	assert.equal(state.mcpCalls[0].status, 'error');
	assert.equal(state.activeTurn.timeline[0].status, 'error');
	assert.ok(state.activeTurn.timeline[0].detail.includes('Open-Meteo Weather API unreachable'));

	state = reduceAgUiEvent(state, {
		type: 'STEP_FINISHED',
		stepName: 'verify-travel-advisory',
		rawEvent: {
			stepId: 'a2a-adv-1',
			protocol: 'A2A',
			callType: 'a2a:task',
			agentName: 'Nomad-A2A-Specialist',
			skillId: 'verify-travel-advisory',
			status: 'ERROR',
			isError: true,
			error: 'Travel Advisory Service Unavailable (restcountries.com): This API version has been deprecated.'
		}
	});
	assert.equal(state.a2aSteps[0].status, 'error');
	assert.equal(state.activeTurn.timeline[1].status, 'error');
	assert.ok(state.activeTurn.timeline[1].detail.includes('deprecated'));

	assert.equal(summarizeHealthReport(null).status, 'UNKNOWN');
	const downAll = summarizeHealthReport({
		status: 'DOWN',
		checks: [{ name: 'ollama-chat-model', status: 'DOWN', data: { connection: 'refused' } }]
	});
	assert.equal(downAll.status, 'DOWN');
	assert.ok(downAll.label.includes('Offline'));

	const smallRyeUp = summarizeHealthReport({
		status: 'UP',
		checks: [
			{ name: 'MCP clients health check', status: 'UP', data: { nomad: 'OK' } },
			{
				name: 'nomad-a2a-specialist',
				status: 'UP',
				data: { 'frankfurter-fx.status': 'UP', 'restcountries-advisory.status': 'UP' }
			},
			{ name: 'ollama-chat-model', status: 'UP', data: { model: 'llama3.2:latest' } }
		]
	});
	assert.equal(smallRyeUp.status, 'UP');
	assert.equal(smallRyeUp.label, 'All Systems Operational');

	const smallRye503 = summarizeHealthReport({
		status: 'DOWN',
		checks: [
			{ name: 'MCP clients health check', status: 'UP', data: { nomad: 'OK' } },
			{
				name: 'nomad-a2a-specialist',
				status: 'DOWN',
				data: {
					'frankfurter-fx.status': 'UP',
					'restcountries-advisory.status': 'DOWN',
					'restcountries-advisory.error': 'Missing RESTCOUNTRIES_API_KEY'
				}
			}
		]
	});
	assert.equal(smallRye503.status, 'DEGRADED');
	assert.equal(smallRye503.downCount, 2);

	const clientSource = fs.readFileSync(path.join(srcLibDir, 'agui-client.js'), 'utf8');
	assert.ok(clientSource.includes("from '@agui/svelte'"));
	assert.ok(!clientSource.includes('../../../../../packages/agui-svelte'));

	const containerSource = fs.readFileSync(path.join(componentsDir, 'AgentChatContainer.svelte'), 'utf8');
	assert.ok(containerSource.includes("fetch('/q/health/ready')"));
	assert.ok(containerSource.includes('WebSocketAgent'));
	assert.ok(containerSource.includes('buildRunAgentInput'));
	assert.ok(containerSource.includes('buildHitlResumeInput'));
	assert.ok(!containerSource.includes('/api/agent/approve'));
	assert.ok(!containerSource.includes('APPROVE_HITL'));
	assert.ok(!containerSource.includes("value: 'deterministic'"));
});

test('Standalone @agui/svelte workspace integration and useAgUiAgent controller (#85)', async () => {
	const encoder = new TextEncoder();
	const ssePayload =
		'data: {"type":"RUN_STARTED","threadId":"thread-85","runId":"r-85","rawEvent":{"llmMode":"ollama:llama3.2:latest"}}\n\n' +
		'data: {"type":"TEXT_MESSAGE_CONTENT","messageId":"m-85","delta":"Marhaba from Beirut"}\n\n' +
		'data: {"type":"RUN_FINISHED","threadId":"thread-85","runId":"r-85","outcome":{"type":"success"},"result":{"status":"completed"}}\n\n';

	const controller = useAgUiAgent({
		threadId: 'thread-85',
		fetchImpl: async () => ({
			ok: true,
			status: 200,
			body: new ReadableStream({
				start(ctrl) {
					ctrl.enqueue(encoder.encode(ssePayload));
					ctrl.close();
				}
			})
		})
	});

	assert.equal(controller.state.threadId, 'thread-85');
	await controller.sendPrompt('I want to plan a trip to Beirut, Lebanon');
	assert.equal(controller.state.statusText, 'Completed (completed)');
	assert.equal(controller.state.isStreaming, false);
	assert.equal(controller.state.messages.at(-1).content, 'Marhaba from Beirut');
});

test('Boundary & mutation defense across envelope builders, reducer branches, health checks, and dossier formatting', () => {
	const initial = createInitialWorkbenchState('th-bound');
	assert.equal(initial.wsConnected, false);
	assert.equal(initial.messages[0].interaction.submitted, false);

	// Envelope builder boundaries
	const approvedNullMods = buildHitlResumeInput({ approved: true, modifiedArgs: null });
	assert.deepEqual(approvedNullMods.resume[0].payload, {
		reason: 'Traveler authorized sanctuary hold'
	});
	const approvedPrimMods = buildHitlResumeInput({ approved: true, modifiedArgs: 'not-an-object' });
	assert.deepEqual(approvedPrimMods.resume[0].payload, {
		reason: 'Traveler authorized sanctuary hold'
	});
	const declinedWithMods = buildHitlResumeInput({
		approved: false,
		modifiedArgs: { nights: 99 }
	});
	assert.equal(declinedWithMods.resume[0].payload.nights, undefined);
	assert.equal(declinedWithMods.resume[0].payload.reason, 'Traveler declined reservation hold');

	// Memory reset clears active streaming flag; params_updated preserves existing activeCity even when memoryTurns === 0
	let st = reduceAgUiEvent(initial, { type: 'RUN_STARTED' });
	assert.equal(st.isStreaming, true);
	st = reduceAgUiEvent(st, {
		type: 'STATE_DELTA',
		delta: toRootStateDeltaOps({ status: 'memory_reset' })
	});
	assert.equal(st.isStreaming, false);

	st = reduceAgUiEvent(st, {
		type: 'STATE_SNAPSHOT',
		snapshot: {
			memoryTurns: 0,
			tripDossierState: { nomadTrip: { activeCity: 'Beirut', nights: 3 } }
		}
	});
	st = reduceAgUiEvent(st, {
		type: 'STATE_DELTA',
		delta: toRootStateDeltaOps({
			status: 'params_updated',
			memoryTurns: 0,
			tripDossierState: { nomadTrip: { activeCity: 'Beirut', nights: 5 } }
		})
	});
	assert.equal(st.tripDossierState.nomadTrip.activeCity, 'Beirut');
	assert.equal(st.tripDossierState.nomadTrip.nights, 5);

	// HITL via callType alone + streaming TOOL_CALL_ARGS with multiple concurrent tool calls
	st = reduceAgUiEvent(st, {
		type: 'TOOL_CALL_START',
		toolCallId: 'bg-1',
		toolCallName: 'fetchCityWeather',
		rawEvent: { callId: 'bg-1', tool: 'fetchCityWeather', args: { city: 'Beirut' } }
	});
	st = reduceAgUiEvent(st, {
		type: 'TOOL_CALL_START',
		toolCallId: 'hitl-stream',
		toolCallName: 'requestSanctuaryReservation',
		rawEvent: { callId: 'hitl-stream', callType: 'hitl:approval' }
	});
	assert.equal(st.pendingApproval.callId, 'hitl-stream');
	st = reduceAgUiEvent(st, {
		type: 'TOOL_CALL_ARGS',
		toolCallId: 'hitl-stream',
		delta: '{"city":"Nara","stopTitle":"Cedar Inn","nights":3,"costUsd":1750}'
	});
	assert.equal(st.pendingApproval.city, 'Nara');
	assert.equal(st.pendingApproval.nights, 3);
	assert.deepEqual(st.mcpCalls[0].args, { city: 'Beirut' });

	// TOOL_CALL_RESULT without rawEvent.tool resolves name from mcpCalls & leaves other calls untouched
	st = reduceAgUiEvent(st, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-bg-1',
		toolCallId: 'bg-1',
		content: JSON.stringify({ status: 'ERROR', error: 'Timeout from JSON content' }),
		rawEvent: {}
	});
	assert.equal(st.mcpCalls[0].status, 'error');
	assert.equal(st.mcpCalls[0].error, 'Timeout from JSON content');
	assert.equal(st.mcpCalls[1].status, 'awaiting_approval');

	// Non-JSON tool content & blank media fields & errored media tool
	st = reduceAgUiEvent(st, {
		type: 'TOOL_CALL_START',
		toolCallId: 'plain-1',
		toolCallName: 'unknownPlainTool'
	});
	st = reduceAgUiEvent(st, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-plain',
		toolCallId: 'plain-1',
		content: 'raw text result'
	});
	assert.equal(st.activeTurn.timeline.find((t) => t.id === 'plain-1').detail, '');

	let mediaErrState = createInitialWorkbenchState('th-media-err');
	mediaErrState = reduceAgUiEvent(mediaErrState, {
		type: 'TOOL_CALL_START',
		toolCallId: 'mc-err',
		toolCallName: 'discoverLocalCultureAndMedia'
	});
	mediaErrState = reduceAgUiEvent(mediaErrState, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-mc-err',
		toolCallId: 'mc-err',
		content: JSON.stringify({
			isError: true,
			error: 'Wikipedia unavailable',
			destination: 'Kyoto',
			heroImageUrl: 'https://example.com/x.jpg'
		})
	});
	assert.equal(mediaErrState.activeTurn.media.length, 0);
	assert.equal(mediaErrState.tripDossierState.nomadTrip.activeCity, null);

	// Blank strings in discoverLocalCultureAndMedia do not overwrite with whitespace
	mediaErrState = reduceAgUiEvent(mediaErrState, {
		type: 'TOOL_CALL_START',
		toolCallId: 'mc-blank',
		toolCallName: 'discoverLocalCultureAndMedia'
	});
	mediaErrState = reduceAgUiEvent(mediaErrState, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-mc-blank',
		toolCallId: 'mc-blank',
		content: JSON.stringify({
			destination: '   ',
			heroImageUrl: '   ',
			audioTrack: { previewUrl: '   ' }
		})
	});
	assert.equal(mediaErrState.activeTurn.media.length, 0);
	assert.equal(mediaErrState.tripDossierState.nomadTrip.activeCity, null);

	// Non-A2A STEP_FINISHED (e.g. GUARDRAIL) does not pollute a2aSteps, while protocol=A2A custom callType is recorded
	let stepState = createInitialWorkbenchState('th-steps');
	stepState = reduceAgUiEvent(stepState, {
		type: 'STEP_FINISHED',
		stepName: 'input-guardrail',
		rawEvent: { protocol: 'GUARDRAIL', callType: 'guardrail:input', summary: 'Verified' }
	});
	assert.equal(stepState.a2aSteps.length, 0);
	stepState = reduceAgUiEvent(stepState, {
		type: 'STEP_FINISHED',
		stepName: 'custom-a2a',
		rawEvent: { protocol: 'A2A', callType: 'custom:handoff', summary: 'Delegated' }
	});
	assert.equal(stepState.a2aSteps.length, 1);

	// Non-guardrail RUN_ERROR
	stepState = reduceAgUiEvent(stepState, {
		type: 'RUN_ERROR',
		code: 'UPSTREAM_502',
		message: 'Gateway timeout'
	});
	assert.equal(stepState.statusText, 'Error: Gateway timeout');

	// Health report edge cases: null check items, non-object data, ignored metadata keys, plural APIs Down
	const edgeHealth = summarizeHealthReport({
		status: 'DOWN',
		checks: [
			null,
			{ name: 'check-with-meta', status: 'DOWN', data: { endpoint: 'http://x', model: 'llama', okKey: 'OK' } },
			{ name: 'check-prim-data', status: 'DOWN', data: 'invalid' }
		]
	});
	assert.equal(edgeHealth.status, 'DOWN');
	assert.equal(edgeHealth.checks?.[0]?.error ?? edgeHealth.services[0].error, null);

	const twoDown = summarizeHealthReport({
		services: [
			{ id: 's1', status: 'UP' },
			{ id: 's2', status: 'DOWN' },
			{ id: 's3', status: 'DOWN' }
		]
	});
	assert.equal(twoDown.status, 'DEGRADED');
	assert.equal(twoDown.label, 'Degraded • 2 APIs Down');

	// Dossier Markdown partial FX & trailing user message
	const partialFxMd = formatTripDossierMarkdown(
		{ nomadTrip: { activeCity: 'Oaxaca', allocatedUsd: 900, exchangeRate: null } },
		[
			{ role: 'assistant', content: 'First briefing' },
			{ role: 'user', content: 'Follow-up user question' }
		]
	);
	assert.ok(partialFxMd.includes('Awaiting FX audit'));
	assert.ok(partialFxMd.includes('First briefing'));
	assert.ok(!partialFxMd.includes('Follow-up user question'));

	// JSON null payloads ('null' parses to null where typeof null === 'object'), tool-name fallback matching,
	// step title matching with concurrent steps, interrupt fallback fields, and health check label/sub-error precedence
	let nullJsonState = createInitialWorkbenchState('th-null-json');
	nullJsonState = reduceAgUiEvent(nullJsonState, {
		type: 'TOOL_CALL_START',
		toolCallId: 'nj-1',
		toolCallName: 'discoverLocalCultureAndMedia',
		rawEvent: { callId: 'nj-1', tool: 'discoverLocalCultureAndMedia', args: { city: 'Beirut' } }
	});
	nullJsonState = reduceAgUiEvent(nullJsonState, {
		type: 'TOOL_CALL_START',
		toolCallId: 'nj-2',
		toolCallName: 'fetchCityWeather',
		rawEvent: { callId: 'nj-2', tool: 'fetchCityWeather', args: { city: 'Kyoto' } }
	});
	nullJsonState = reduceAgUiEvent(nullJsonState, {
		type: 'TOOL_CALL_ARGS',
		toolCallId: 'nj-1',
		delta: 'null'
	});
	assert.deepEqual(nullJsonState.mcpCalls[0].args, { city: 'Beirut' });

	nullJsonState = reduceAgUiEvent(nullJsonState, {
		type: 'TOOL_CALL_RESULT',
		messageId: 'tm-nj-1',
		toolCallId: 'unmatched-id',
		content: 'null',
		rawEvent: { tool: 'discoverLocalCultureAndMedia', result: null }
	});
	assert.equal(nullJsonState.mcpCalls[0].status, 'completed');
	assert.equal(nullJsonState.mcpCalls[1].status, 'running');
	assert.equal(nullJsonState.activeTurn.timeline[0].status, 'completed');
	assert.equal(nullJsonState.activeTurn.timeline[1].status, 'running');

	// Concurrent steps matched by title when stepId is omitted + Interrupt fallback from staged pendingApproval
	let concStep = createInitialWorkbenchState('th-conc-step');
	concStep = reduceAgUiEvent(concStep, {
		type: 'STEP_STARTED',
		stepName: 'skill-alpha',
		rawEvent: { stepId: 'step-a', skillId: 'skill-alpha' }
	});
	concStep = reduceAgUiEvent(concStep, {
		type: 'STEP_STARTED',
		stepName: 'skill-beta',
		rawEvent: { stepId: 'step-b', skillId: 'skill-beta' }
	});
	concStep = reduceAgUiEvent(concStep, {
		type: 'STEP_FINISHED',
		stepName: 'skill-alpha',
		rawEvent: { skillId: 'skill-alpha', summary: 'Alpha done' }
	});
	assert.equal(concStep.activeTurn.timeline[0].status, 'completed');
	assert.equal(concStep.activeTurn.timeline[1].status, 'running');

	concStep = reduceAgUiEvent(concStep, {
		type: 'TOOL_CALL_START',
		toolCallId: 'staged-int-1',
		toolCallName: 'requestSanctuaryReservation',
		rawEvent: {
			callId: 'staged-int-1',
			requiresApproval: true,
			args: { city: 'Nara', stopTitle: 'Cedar', nights: 2, costUsd: 1600 }
		}
	});
	concStep = reduceAgUiEvent(concStep, {
		type: 'RUN_FINISHED',
		outcome: { type: 'interrupt', interrupts: [{}] }
	});
	assert.equal(concStep.pendingApproval.interruptId, 'staged-int-1');
	assert.equal(concStep.pendingApproval.city, 'Nara');
	assert.equal(concStep.pendingApproval.nights, 2);

	const labelAndSubErrHealth = summarizeHealthReport({
		status: 'DOWN',
		checks: [
			{ name: 'MCP clients health check', status: 'UP', data: { model: 'should-not-override-label', extra: 'v1' } },
			{ name: 'ollama-chat-model', status: 'UP', data: null },
			{
				name: 'nomad-a2a-specialist',
				status: 'DOWN',
				data: {
					'frankfurter-fx.status': 'UP',
					'frankfurter-fx.error': 'stale-up-error',
					'restcountries-advisory.status': 'DOWN',
					'restcountries-advisory.error': 'First real sub error',
					'itunes-audio.status': 'DOWN',
					'itunes-audio.error': 'Second sub error'
				}
			}
		]
	});
	assert.equal(labelAndSubErrHealth.services[0].name, 'Quarkus LangChain4j MCP Client (nomad)');
	assert.equal(labelAndSubErrHealth.services[0].error, null);
	assert.equal(labelAndSubErrHealth.services[1].name, 'Ollama Local LLM');
	const parentA2a = labelAndSubErrHealth.services.find((s) => s.id === 'nomad-a2a-specialist');
	assert.equal(parentA2a.error, 'First real sub error');

	assert.deepEqual(toRootStateDeltaOps(['not', 'an', 'object']), []);
	assert.deepEqual(toRootStateDeltaOps({ 'nested/key~flag': 7 }), [
		{ op: 'add', path: '/nested~1key~0flag', value: 7 }
	]);
});

test('AG-UI activity snapshots populate turn.activities, sync reportSections, derive all 5 Dossier Report cards, and export complete Markdown', () => {
	let state = createInitialWorkbenchState('th-agui-93');
	assert.deepEqual(deriveDossierActivities(state.tripDossierState), []);

	state = reduceAgUiEvent(state, {
		type: EventType.RUN_STARTED,
		threadId: 'th-agui-93',
		runId: 'run-93'
	});

	state = reduceAgUiEvent(state, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-weather-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.WEATHER,
		content: {
			status: 'OK',
			isError: false,
			city: 'Kyoto',
			country: 'Japan',
			temperatureC: 18.4,
			windSpeedKph: 11.2,
			condition: 'Clear Sky & Crisp Light',
			latitude: 35.0116,
			longitude: 135.7681,
			source: 'api.open-meteo.com'
		},
		rawEvent: { protocol: 'AG-UI', callType: 'agui:activity' }
	});

	// Upsert same weather messageId idempotently
	state = reduceAgUiEvent(state, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-weather-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.WEATHER,
		content: {
			status: 'OK',
			isError: false,
			city: 'Kyoto',
			country: 'Japan',
			temperatureC: 19.1,
			windSpeedKph: 10.5,
			condition: 'Clear Sky & Crisp Light',
			latitude: 35.0116,
			longitude: 135.7681,
			source: 'api.open-meteo.com'
		},
		rawEvent: { protocol: 'AG-UI', callType: 'agui:activity' }
	});
	assert.equal(state.activeTurn.activities.length, 1);
	assert.equal(state.activeTurn.activities[0].content.temperatureC, 19.1);

	state = reduceAgUiEvent(state, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-map-places-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.MAP_PLACES,
		content: {
			status: 'OK',
			isError: false,
			city: 'Kyoto',
			country: 'Japan',
			latitude: 35.0116,
			longitude: 135.7681,
			focus: 'Machiya architecture & koto music',
			pace: 'Unrushed curated walk',
			stops: [
				'Fushimi Inari Taisha — Senbon Torii Ridge',
				'Arashiyama Bamboo Grove & Tenryu-ji Timber Garden'
			],
			wikiSourceUrl: 'https://en.wikipedia.org/wiki/Kyoto'
		}
	});

	state = reduceAgUiEvent(state, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-security-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.SECURITY_ADVISORY,
		content: {
			status: 'VERIFIED',
			isError: false,
			city: 'Kyoto',
			country: 'Japan',
			capital: 'Tokyo',
			region: 'Asia',
			currencyCode: 'JPY',
			timezone: 'UTC+09:00',
			advisoryLevel: 'STANDARD_PRECAUTIONS',
			advisoryStatus: 'VERIFIED — Country Metadata & Timezone Confirmed',
			emergencyNumbers: '110 Police • 119 Fire & Ambulance',
			hitlThresholdUsd: 1500,
			safetyHighlights: [
				'Verify passport validity (6+ months recommended) for Japan.',
				'Confirm local ISO 4217 currency settlement (JPY).',
				'Nomad HITL financial safeguard active >= $1,500 USD.'
			],
			source: 'api.restcountries.com'
		}
	});

	state = reduceAgUiEvent(state, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-fx-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.FX_BUDGET,
		content: {
			status: 'OK',
			isError: false,
			city: 'Kyoto',
			country: 'Japan',
			nights: 4,
			stopTitle: 'Kyoto Curated Stay',
			baseCurrency: 'USD',
			targetCurrency: 'JPY',
			exchangeRate: 153.2,
			budgetUsd: 2400,
			allocatedUsd: 1560,
			convertedTotal: 367680,
			hitlThresholdUsd: 1500,
			source: 'api.frankfurter.dev'
		}
	});

	state = reduceAgUiEvent(state, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-culture-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.CULTURE_AUDIO,
		content: {
			status: 'OK',
			isError: false,
			city: 'Kyoto',
			country: 'Japan',
			culturalSummary: 'Kyoto preserves classical machiya timber joinery and koto chamber traditions.',
			heroImageUrl: 'https://upload.wikimedia.org/kyoto.jpg',
			wikiSourceUrl: 'https://en.wikipedia.org/wiki/Kyoto',
			acousticMood: 'Arashiyama Koto Twilight',
			listeningWindow: '18:30–20:30 Local Dusk',
			soundtrack: {
				trackName: 'Rokudan no Shirabe',
				artistName: 'Kyoto Ensemble',
				previewUrl: 'https://audio-ssl.itunes.apple.com/koto.m4a',
				durationSeconds: 30
			}
		}
	});

	state = reduceAgUiEvent(state, {
		type: EventType.TEXT_MESSAGE_CONTENT,
		messageId: 'm-93',
		delta: 'Complete Kyoto Trip Dossier curated with all 5 AG-UI surfaces.'
	});
	state = reduceAgUiEvent(state, {
		type: EventType.RUN_FINISHED,
		threadId: 'th-agui-93',
		runId: 'run-93',
		outcome: { type: 'success' },
		result: { status: 'completed', memoryTurns: 2 }
	});

	const lastTurn = state.messages.at(-1);
	assert.equal(lastTurn.activities.length, 5);

	const dossierActivities = deriveDossierActivities(state.tripDossierState);
	assert.deepEqual(
		dossierActivities.map((a) => a.activityType),
		[
			AGUI_ACTIVITY_TYPES.WEATHER,
			AGUI_ACTIVITY_TYPES.MAP_PLACES,
			AGUI_ACTIVITY_TYPES.SECURITY_ADVISORY,
			AGUI_ACTIVITY_TYPES.FX_BUDGET,
			AGUI_ACTIVITY_TYPES.CULTURE_AUDIO
		]
	);

	// Verify Markdown export includes all 5 sections, map links, and security advisory highlights
	const md = formatTripDossierMarkdown(state.tripDossierState, state.messages);
	assert.ok(md.includes('## Weather & Microclimate'));
	assert.ok(md.includes('19.1°C'));
	assert.ok(md.includes('## Places & Map Links'));
	assert.ok(md.includes('Google Maps'));
	assert.ok(md.includes('Apple Maps'));
	assert.ok(md.includes('OpenStreetMap'));
	assert.ok(md.includes('## Security & Travel Advisory'));
	assert.ok(md.includes('110 Police • 119 Fire & Ambulance'));
	assert.ok(md.includes('## FX & Budget Audit'));
	assert.ok(md.includes('## Cultural Notes & Acoustic Preview'));
	assert.ok(md.includes('Rokudan no Shirabe'));

	const links = buildPlaceMapLinks({ city: 'Kyoto', country: 'Japan', latitude: 35.0116, longitude: 135.7681 });
	assert.ok(links.openStreetMapEmbedUrl.includes('openstreetmap.org'));
});
