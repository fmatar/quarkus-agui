import test from 'node:test';
import assert from 'node:assert/strict';
import {
	SCENARIO_PRESETS,
	createInitialWorkbenchState,
	filterInspectorEvents,
	parseSseChunk,
	reduceAgUiEvent,
	updateServiceReplicasInState
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
				cluster: 'quarkus-prod-us-east-1',
				services: [{ serviceName: 'payment-gateway', status: 'HEALTHY', replicas: 4, cpuPercent: 40 }]
			}
		}
	});
	assert.equal(state.clusterState.services[0].replicas, 4);

	state = reduceAgUiEvent(state, {
		id: 'e2',
		type: 'TOOL_CALL',
		threadId: 'thread-test',
		timestamp: 1005,
		payload: {
			callId: 'c1',
			tool: 'inspectClusterHealth',
			protocol: 'MCP',
			server: 'quarkus-mcp-cluster-ops (:8081)',
			args: {}
		}
	});
	assert.equal(state.mcpCalls.length, 1);
	assert.equal(state.mcpCalls[0].tool, 'inspectClusterHealth');

	state = reduceAgUiEvent(state, {
		id: 'e3',
		type: 'TOOL_RESULT',
		threadId: 'thread-test',
		timestamp: 1010,
		payload: {
			callId: 'c1',
			tool: 'inspectClusterHealth',
			result: { cluster: 'quarkus-prod-us-east-1' }
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
			agentName: 'Sentinel-A2A-Auditor',
			skillId: 'audit-clean-architecture',
			summary: 'Zero outward violations'
		}
	});
	assert.equal(state.a2aSteps.length, 1);
	assert.equal(state.a2aSteps[0].agentName, 'Sentinel-A2A-Auditor');

	state = reduceAgUiEvent(state, {
		id: 'e5',
		type: 'TEXT_DELTA',
		threadId: 'thread-test',
		timestamp: 1020,
		payload: { content: 'Cluster is healthy.' }
	});
	assert.equal(state.streamBuffer, 'Cluster is healthy.');

	state = reduceAgUiEvent(state, {
		id: 'e6',
		type: 'RUN_FINISHED',
		threadId: 'thread-test',
		timestamp: 1025,
		payload: { status: 'completed' }
	});
	assert.equal(state.isStreaming, false);
	assert.equal(state.messages.at(-1).role, 'assistant');
	assert.equal(state.messages.at(-1).content, 'Cluster is healthy.');
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
		payload: { callId: 'ch1', tool: 'scaleDeployment', requiresApproval: true }
	});
	assert.equal(state.mcpCalls[0].status, 'awaiting_approval');

	state = reduceAgUiEvent(state, {
		id: 'h2',
		type: 'RUN_FINISHED',
		payload: {
			status: 'interrupted',
			pendingAction: { toolName: 'scaleDeployment', serviceName: 'payment-gateway', replicas: 8 }
		}
	});
	assert.equal(state.isStreaming, false);
	assert.equal(state.pendingApproval.replicas, 8);

	state = reduceAgUiEvent(state, {
		id: 'h3',
		type: 'RUN_FINISHED',
		payload: { status: 'cancelled' }
	});
	assert.equal(state.pendingApproval, null);
	assert.ok(state.messages.at(-1).content.includes('cancelled'));
});

test('filterInspectorEvents and updateServiceReplicasInState support 3-pane workbench interactions', () => {
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
	const updated = updateServiceReplicasInState(initial.clusterState, 'payment-gateway', 2);
	assert.equal(updated.services[0].replicas, 5);
	assert.equal(updateServiceReplicasInState(null, 'payment-gateway', 1), null);
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
		payload: { agentName: 'Sentinel-A2A-Auditor', skillId: 'audit-clean-architecture' }
	});
	assert.equal(state.statusText, 'A2A Delegation: Sentinel-A2A-Auditor (audit-clean-architecture)');

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
