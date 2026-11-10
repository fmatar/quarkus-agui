import test from 'node:test';
import assert from 'node:assert/strict';
import {
	PROTOCOL_CALL_TAXONOMY,
	filterInspectorEvents,
	lockTurnInteraction,
	parseSseChunk,
	recordClientOutboundEvent,
	resolveProtocolCallMeta,
	selectTurnOption,
	useAgUiAgent
} from '../src/index.js';

test('@agui/svelte parseSseChunk, taxonomy, inspector filter, and useAgUiAgent work end-to-end', () => {
	assert.equal(Object.keys(PROTOCOL_CALL_TAXONOMY).length, 14);
	assert.equal(resolveProtocolCallMeta('mcp:tool').family, 'MCP');
	assert.equal(resolveProtocolCallMeta(null, 'A2A').callType, 'a2a:task');
	assert.equal(resolveProtocolCallMeta(null, 'GUARDRAIL').callType, 'guardrail:input');
	assert.equal(resolveProtocolCallMeta(null, 'RAG').callType, 'rag:retrieval');
	assert.equal(resolveProtocolCallMeta(null, 'AGENTIC').callType, 'agentic:specialist');
	assert.equal(resolveProtocolCallMeta(null, 'HITL').callType, 'hitl:approval');
	assert.equal(resolveProtocolCallMeta(null, 'LOCAL').callType, 'tool:function');
	assert.equal(resolveProtocolCallMeta(null, 'UI').callType, 'tool:ui');
	assert.equal(resolveProtocolCallMeta(null, 'UNKNOWN').callType, 'mcp:tool');

	const snapshots = [];
	const agent = useAgUiAgent({ threadId: 'th-pkg-1' });
	const unsub = agent.subscribe((s) => snapshots.push(s));
	assert.equal(agent.state.threadId, 'th-pkg-1');
	assert.equal(agent.getState().isStreaming, false);

	agent.ingestSseChunk(
		'data: {"id":"1","type":"RUN_STARTED","payload":{"status":"started"}}\n\n' +
			'data: {"id":"2","type":"TEXT_DELTA","payload":{"content":"Bonjour '
	);
	assert.equal(agent.state.isStreaming, true);

	agent.ingestSseChunk(
		'Beirut"}}\n\ndata: {"id":"3","type":"RUN_FINISHED","payload":{"status":"completed"}}\n\n'
	);
	assert.equal(agent.state.isStreaming, false);
	assert.equal(agent.state.messages.length, 1);
	assert.equal(agent.state.messages[0].content, 'Bonjour Beirut');

	agent.recordOutbound('PING', { ts: 1 });
	assert.equal(filterInspectorEvents(agent.state.events, 'CLIENT').length, 1);

	const withInteraction = {
		...agent.state,
		messages: [
			{
				id: 'm-choice',
				role: 'assistant',
				interaction: { mode: 'multi_select', selectedValues: [], submitted: false }
			}
		]
	};
	const customAgent = useAgUiAgent({ initialState: withInteraction });
	customAgent.selectOption('m-choice', 'opt-1');
	assert.deepEqual(customAgent.state.messages[0].interaction.selectedValues, ['opt-1']);
	customAgent.lockInteraction('m-choice');
	assert.equal(customAgent.state.messages[0].interaction.submitted, true);

	customAgent.reset('th-pkg-2');
	assert.equal(customAgent.state.threadId, 'th-pkg-2');
	unsub();
	assert.ok(snapshots.length >= 4);
});
