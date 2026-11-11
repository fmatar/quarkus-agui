import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
	AGUI_ACTIVITY_TYPES,
	AbstractAgent,
	EventType,
	HttpAgent,
	PROTOCOL_CALL_TAXONOMY,
	WebSocketAgent,
	applyJsonPatch,
	applyJsonPatchOperation,
	buildPlaceMapLinks,
	buildResumeArray,
	createActivityRegistry,
	createAgUiStore,
	filterInspectorEvents,
	formatCoordinatesLabel,
	getRunOutcome,
	lockTurnInteraction,
	parseSseChunk,
	recordClientOutboundEvent,
	reduceCanonicalAgUiEvent,
	resolveEventProtocolMeta,
	resolveProtocolCallMeta,
	selectTurnOption,
	unescapeJsonPointerToken,
	upsertActivityList,
	useAgUiAgent
} from '../src/index.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

class FakeWebSocket {
	static CONNECTING = 0;
	static OPEN = 1;
	static CLOSING = 2;
	static CLOSED = 3;

	constructor(url, protocols) {
		this.url = url;
		this.protocols = protocols;
		this.readyState = FakeWebSocket.CONNECTING;
		this.sentFrames = [];
		this.onopen = null;
		this.onmessage = null;
		this.onerror = null;
		this.onclose = null;
	}

	openServer() {
		this.readyState = FakeWebSocket.OPEN;
		this.onopen?.({ type: 'open' });
	}

	emitServerJson(frame) {
		this.onmessage?.({ data: JSON.stringify(frame) });
	}

	emitServerRaw(raw) {
		this.onmessage?.({ data: raw });
	}

	failServer(message = 'socket failure') {
		this.onerror?.(new Error(message));
	}

	closeServer(code = 1000, reason = 'normal') {
		this.readyState = FakeWebSocket.CLOSED;
		this.onclose?.({ code, reason });
	}

	send(data) {
		if (this.readyState !== FakeWebSocket.OPEN) {
			throw new Error('Socket is not open');
		}
		this.sentFrames.push(data);
		this.onClientSend?.(data, this);
	}

	close(code = 1000, reason = 'client close') {
		this.readyState = FakeWebSocket.CLOSED;
		this.onclose?.({ code, reason });
	}
}

test('headless package contract: dual license, zero CSS dependencies, and upstream @ag-ui re-exports', () => {
	const pkg = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../package.json'), 'utf8'));
	assert.equal(pkg.license, 'Apache-2.0 OR MIT');
	assert.ok(pkg.dependencies['@ag-ui/client']);
	assert.ok(pkg.dependencies['@ag-ui/core']);
	assert.equal(pkg.dependencies['@skeletonlabs/skeleton'], undefined);
	assert.equal(pkg.dependencies['tailwindcss'], undefined);

	assert.equal(typeof AbstractAgent, 'function');
	assert.equal(typeof HttpAgent, 'function');
	assert.equal(EventType.RUN_STARTED, 'RUN_STARTED');
	assert.equal(EventType.TEXT_MESSAGE_CONTENT, 'TEXT_MESSAGE_CONTENT');
	assert.equal(EventType.TOOL_CALL_START, 'TOOL_CALL_START');
	assert.equal(EventType.STATE_DELTA, 'STATE_DELTA');
	assert.equal(typeof buildResumeArray, 'function');
	assert.equal(typeof getRunOutcome, 'function');
});

test('applyJsonPatch applies RFC 6902 add, replace, and remove immutably with RFC 6901 pointer unescaping', () => {
	assert.equal(unescapeJsonPointerToken('a~1b~0c'), 'a/b~c');
	assert.equal(unescapeJsonPointerToken('plain'), 'plain');
	assert.equal(unescapeJsonPointerToken(null), '');

	const original = {
		nomadTrip: { activeCity: 'Kyoto', nights: 4 },
		tags: ['architecture', 'koto'],
		routes: { 'a/b': { 'c~d': 10 } }
	};

	const patched = applyJsonPatch(original, [
		{ op: 'replace', path: '/nomadTrip/nights', value: 6 },
		{ op: 'add', path: '/nomadTrip/budgetUsd', value: 2800 },
		{ op: 'add', path: '/preferences/style', value: 'Machiya Joinery' },
		{ op: 'add', path: '/tags/-', value: 'kaiseki' },
		{ op: 'add', path: '/tags/1', value: 'zen-gardens' },
		{ op: 'replace', path: '/tags/0', value: 'timber' },
		{ op: 'replace', path: '/routes/a~1b/c~0d', value: 42 },
		{ op: 'remove', path: '/tags/2' },
		{ op: 'remove', path: '/nomadTrip/activeCity' },
		{ op: 'remove', path: '/missing/nested/key' }
	]);

	assert.equal(original.nomadTrip.nights, 4);
	assert.equal(original.nomadTrip.activeCity, 'Kyoto');
	assert.deepEqual(original.tags, ['architecture', 'koto']);

	assert.equal(patched.nomadTrip.nights, 6);
	assert.equal(patched.nomadTrip.budgetUsd, 2800);
	assert.equal(patched.nomadTrip.activeCity, undefined);
	assert.equal(patched.preferences.style, 'Machiya Joinery');
	assert.deepEqual(patched.tags, ['timber', 'zen-gardens', 'kaiseki']);
	assert.equal(patched.routes['a/b']['c~d'], 42);

	assert.deepEqual(applyJsonPatch(original, [{ op: 'replace', path: '', value: { reset: true } }]), {
		reset: true
	});
	assert.deepEqual(applyJsonPatch(original, [{ op: 'remove', path: '' }]), {});
	assert.equal(applyJsonPatchOperation(original, null), original);
	assert.equal(applyJsonPatchOperation(original, { op: 123, path: '/a' }), original);
	assert.equal(applyJsonPatchOperation(original, { op: 'add' }), original);
	// CWE-1321: reject prototype-chain pointer segments (__proto__, constructor, prototype)
	const cleanTarget = { safe: 1, a: { nested: true } };
	assert.equal(
		applyJsonPatch(cleanTarget, [{ op: 'add', path: '/__proto__/polluted', value: 'pwned' }]),
		cleanTarget
	);
	assert.equal(
		applyJsonPatch(cleanTarget, [{ op: 'replace', path: '/__proto__', value: null }]),
		cleanTarget
	);
	assert.equal(
		applyJsonPatch(cleanTarget, [
			{ op: 'add', path: '/constructor/prototype/polluted', value: 'pwned' }
		]),
		cleanTarget
	);
	assert.equal(
		applyJsonPatch(cleanTarget, [{ op: 'remove', path: '/a/prototype/b' }]),
		cleanTarget
	);
	assert.equal(Object.getPrototypeOf(cleanTarget), Object.prototype);
	assert.equal(({}).polluted, undefined);
	const withNullBranch = { nullBranch: null, primitiveLeaf: 7 };
	assert.deepEqual(
		applyJsonPatch(withNullBranch, [{ op: 'remove', path: '/nullBranch/deep' }]),
		withNullBranch
	);
	assert.deepEqual(
		applyJsonPatch(withNullBranch, [{ op: 'add', path: '/primitiveLeaf/sub', value: 'ok' }]),
		{ nullBranch: null, primitiveLeaf: { sub: 'ok' } }
	);
	assert.deepEqual(applyJsonPatch(null, [{ op: 'add', path: 'city', value: 'Lisbon' }]), {
		city: 'Lisbon'
	});
	assert.equal(applyJsonPatch(original, []), original);
	assert.equal(applyJsonPatch(original, null), original);
});

test('Tri-Protocol taxonomy extracts provenance from canonical rawEvent, metadata, and payload', () => {
	assert.equal(Object.keys(PROTOCOL_CALL_TAXONOMY).length, 15);
	assert.equal(resolveProtocolCallMeta('mcp:tool').family, 'MCP');
	assert.equal(resolveProtocolCallMeta('agui:activity').family, 'AG-UI');
	assert.equal(resolveProtocolCallMeta(null, 'AG-UI').callType, 'agui:activity');
	assert.equal(resolveProtocolCallMeta(null, 'A2A').callType, 'a2a:task');
	assert.equal(resolveProtocolCallMeta(null, 'GUARDRAIL').callType, 'guardrail:input');
	assert.equal(resolveProtocolCallMeta(null, 'RAG').callType, 'rag:retrieval');
	assert.equal(resolveProtocolCallMeta(null, 'AGENTIC').callType, 'agentic:specialist');
	assert.equal(resolveProtocolCallMeta(null, 'HITL').callType, 'hitl:approval');
	assert.equal(resolveProtocolCallMeta(null, 'LOCAL').callType, 'tool:function');
	assert.equal(resolveProtocolCallMeta(null, 'UI').callType, 'tool:ui');
	assert.equal(resolveProtocolCallMeta(null, 'UNKNOWN').callType, 'mcp:tool');
	assert.equal(resolveProtocolCallMeta(null, null).callType, 'mcp:tool');

	const mcpStart = {
		type: EventType.TOOL_CALL_START,
		toolCallId: 'tc-1',
		toolCallName: 'fetchCityWeather',
		rawEvent: { protocol: 'MCP', callType: 'mcp:tool', server: 'nomad-mcp (:8081)' }
	};
	const a2aActivity = {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-1',
		activityType: 'a2a.task',
		content: { status: 'COMPLETED' },
		rawEvent: { protocol: 'A2A', callType: 'a2a:artifact', agentName: 'Nomad-A2A-Specialist' }
	};
	const metadataEvent = {
		type: EventType.TOOL_CALL_START,
		toolCallId: 'tc-meta',
		toolCallName: 'uiSurface',
		metadata: { protocol: 'UI', callType: 'tool:ui' }
	};
	const legacyPayloadEvent = {
		type: 'STEP_STARTED',
		payload: { skillId: 'verify-travel-advisory' }
	};

	assert.equal(resolveEventProtocolMeta(mcpStart).server, 'nomad-mcp (:8081)');
	assert.equal(resolveProtocolCallMeta(mcpStart).callType, 'mcp:tool');
	assert.equal(resolveEventProtocolMeta(a2aActivity).callType, 'a2a:artifact');
	assert.equal(resolveEventProtocolMeta(metadataEvent).callType, 'tool:ui');
	assert.equal(resolveEventProtocolMeta(legacyPayloadEvent).family, 'A2A');
	assert.equal(resolveEventProtocolMeta({ type: 'CUSTOM', payload: 'primitive' }).family, 'MCP');
	assert.equal(resolveEventProtocolMeta(null).family, 'MCP');

	const frames = [
		{ type: EventType.RUN_STARTED, threadId: 't1', runId: 'r1' },
		mcpStart,
		{ type: EventType.TOOL_CALL_RESULT, messageId: 'm-tool', toolCallId: 'tc-1', content: '{}' },
		{ type: EventType.STEP_STARTED, stepName: 'verify-travel-advisory' },
		{ type: EventType.CUSTOM, name: 'a2a-note', rawEvent: { protocol: 'A2A' } },
		{ type: EventType.ACTIVITY_SNAPSHOT, messageId: 'act-bare', activityType: 'a2a.task', content: {} },
		{ type: 'UPDATE_TRIP_PARAMS', direction: 'client', payload: { nights: 5 } },
		{ type: EventType.TEXT_MESSAGE_CONTENT, messageId: 'm1', delta: 'Hello' }
	];

	assert.equal(filterInspectorEvents(frames, 'ALL').length, 8);
	assert.equal(filterInspectorEvents(frames, 'MCP').length, 2);
	assert.equal(filterInspectorEvents(frames, 'A2A').length, 3);
	assert.equal(filterInspectorEvents(frames, 'CLIENT').length, 1);
	assert.equal(filterInspectorEvents(frames, 'AG-UI').length, 2);
	assert.deepEqual(filterInspectorEvents(null, 'ALL'), []);
});

test('WebSocketAgent extends AbstractAgent, streams over shared & dedicated sockets, handles ping/pong & spontaneous state', async () => {
	const sockets = [];
	const controlEvents = [];
	const spontaneousEvents = [];
	const connectionChanges = [];

	const wsAgent = new WebSocketAgent({
		url: 'ws://127.0.0.1:8080/api/agent/ws',
		protocols: ['ag-ui.v1'],
		threadId: 'thread-ws-sdk',
		initialState: { nomadTrip: { nights: 3 } },
		onControlEvent: (evt) => controlEvents.push(evt),
		onSpontaneousEvent: (evt) => spontaneousEvents.push(evt),
		onConnectionChange: (connected) => connectionChanges.push(connected),
		webSocketFactory: (url, protocols) => {
			const sock = new FakeWebSocket(url, protocols);
			sockets.push(sock);
			return sock;
		}
	});

	assert.ok(wsAgent instanceof AbstractAgent);

	// Connect persistent full-duplex socket (and verify idempotent second call)
	const s1 = wsAgent.connectSocket();
	const s2 = wsAgent.connectSocket();
	assert.equal(s1, s2);
	assert.equal(sockets.length, 1);
	const activeSocket = sockets[0];

	activeSocket.onClientSend = (raw, sock) => {
		if (raw === 'ping') {
			sock.emitServerJson({ type: 'pong' });
			return;
		}
		const parsed = JSON.parse(raw);
		if (parsed.type === 'pong') {
			return;
		}
		if (parsed.type === 'UPDATE_TRIP_PARAMS') {
			sock.emitServerJson({
				type: EventType.STATE_DELTA,
				delta: [{ op: 'replace', path: '/nomadTrip/nights', value: parsed.nights }]
			});
			sock.emitServerJson({
				type: EventType.STATE_SNAPSHOT,
				snapshot: { nomadTrip: { nights: parsed.nights, homeCurrency: 'EUR' } }
			});
			return;
		}
		sock.emitServerJson({ type: 'WS_CONNECTED', payload: { sessionId: 's-1' } });
		sock.emitServerJson({ type: 'ping' });
		sock.emitServerRaw('not-json-ignored');
		sock.emitServerRaw(null);
		sock.emitServerJson({
			type: EventType.RUN_STARTED,
			threadId: parsed.threadId,
			runId: parsed.runId
		});
		sock.emitServerJson({
			type: EventType.TEXT_MESSAGE_START,
			messageId: 'msg-a1',
			role: 'assistant'
		});
		sock.emitServerJson({
			type: EventType.TEXT_MESSAGE_CONTENT,
			messageId: 'msg-a1',
			delta: 'Lisbon Fado '
		});
		sock.emitServerJson({
			type: EventType.TEXT_MESSAGE_CONTENT,
			messageId: 'msg-a1',
			delta: 'itinerary ready.'
		});
		sock.emitServerJson({
			type: EventType.TEXT_MESSAGE_END,
			messageId: 'msg-a1'
		});
		sock.emitServerJson({
			type: EventType.STATE_DELTA,
			delta: [{ op: 'replace', path: '/nomadTrip/nights', value: 5 }]
		});
		sock.emitServerJson({
			type: EventType.RUN_FINISHED,
			threadId: parsed.threadId,
			runId: parsed.runId,
			outcome: { type: 'success' }
		});
	};

	activeSocket.openServer();
	assert.equal(wsAgent.connectSocket(), activeSocket);
	assert.equal(wsAgent.isConnected, true);
	assert.deepEqual(connectionChanges, [true]);

	let mwInvocations = 0;
	const passThroughMw = (input, next) => {
		mwInvocations += 1;
		return next.run(input);
	};
	wsAgent.use(passThroughMw);

	assert.equal(wsAgent.sendPing(), true);
	assert.equal(wsAgent.sendControlFrame('UPDATE_TRIP_PARAMS', { nights: 4 }), true);
	assert.equal(spontaneousEvents.length, 2);
	assert.equal(wsAgent.state.nomadTrip.homeCurrency, 'EUR');

	wsAgent.addMessage({ id: 'u-1', role: 'user', content: 'Plan Lisbon' });
	const runResult = await wsAgent.runAgent({
		forwardedProps: { llmMode: 'ollama:llama3.2:latest' }
	});

	assert.equal(controlEvents.length, 1);
	assert.equal(controlEvents[0].type, 'WS_CONNECTED');
	assert.ok(activeSocket.sentFrames.includes('{"type":"pong"}'));
	assert.equal(wsAgent.state.nomadTrip.nights, 5);
	assert.equal(runResult.newMessages.length, 1);
	assert.equal(runResult.newMessages[0].content, 'Lisbon Fado itinerary ready.');

	// Server-initiated close updates connection state
	activeSocket.closeServer();
	assert.equal(wsAgent.isConnected, false);
	assert.deepEqual(connectionChanges, [true, false]);
	assert.equal(wsAgent.sendPing(), false);
	assert.equal(wsAgent.sendControlFrame('RESET_MEMORY', {}), false);

	// Dedicated per-run socket mode (when shared socket is disconnected)
	const cloned = wsAgent.clone();
	assert.ok(cloned instanceof WebSocketAgent);
	let clonedDedicatedSocket = null;
	cloned.webSocketFactory = (url) => {
		const sock = new FakeWebSocket(url);
		clonedDedicatedSocket = sock;
		sock.onClientSend = (raw, s) => {
			const input = JSON.parse(raw);
			s.emitServerJson({ type: EventType.RUN_STARTED, threadId: input.threadId, runId: input.runId });
			s.emitServerJson({
				type: EventType.RUN_FINISHED,
				threadId: input.threadId,
				runId: input.runId,
				outcome: { type: 'success' }
			});
		};
		queueMicrotask(() => sock.openServer());
		return sock;
	};
	const mwBeforeCloneRun = mwInvocations;
	const clonedResult = await cloned.runAgent();
	assert.equal(mwInvocations, mwBeforeCloneRun + 1);
	assert.deepEqual(clonedResult.newMessages, []);
	assert.equal(clonedDedicatedSocket.readyState, FakeWebSocket.CLOSED);
});

test('WebSocketAgent surfaces connection errors and supports abortRun / disconnectSocket', async () => {
	const errorAgent = new WebSocketAgent({
		url: 'ws://127.0.0.1:8080/api/agent/ws',
		webSocketFactory: (url) => {
			const sock = new FakeWebSocket(url);
			queueMicrotask(() => {
				sock.onerror?.({ type: 'error' });
				sock.closeServer(1006, 'abnormal');
			});
			return sock;
		}
	});
	await assert.rejects(() => errorAgent.runAgent(), /WebSocket transport error/i);

	const prematureCloseAgent = new WebSocketAgent({
		url: 'ws://127.0.0.1:8080/api/agent/ws',
		webSocketFactory: (url) => {
			const sock = new FakeWebSocket(url);
			queueMicrotask(() => {
				sock.openServer();
				sock.closeServer(1001, 'going away');
			});
			return sock;
		}
	});
	await assert.rejects(() => prematureCloseAgent.runAgent(), /closed before RUN_FINISHED/i);

	let dedicatedSocket = null;
	const abortableAgent = new WebSocketAgent({
		url: 'ws://127.0.0.1:8080/api/agent/ws',
		webSocketFactory: (url) => {
			dedicatedSocket = new FakeWebSocket(url);
			queueMicrotask(() => dedicatedSocket.openServer());
			return dedicatedSocket;
		}
	});
	const pendingPromise = abortableAgent.runAgent();
	await new Promise((r) => setTimeout(r, 10));
	abortableAgent.abortRun();
	await pendingPromise.catch(() => {});
	assert.equal(dedicatedSocket.readyState, FakeWebSocket.CLOSED);

	// Shared socket error & manual disconnectSocket
	let sharedSock = null;
	const sharedChanges = [];
	const sharedErrAgent = new WebSocketAgent({
		url: 'ws://127.0.0.1:8080/api/agent/ws',
		onConnectionChange: (val) => sharedChanges.push(val),
		webSocketFactory: (url) => {
			sharedSock = new FakeWebSocket(url);
			return sharedSock;
		}
	});
	sharedErrAgent.connectSocket();
	sharedSock.openServer();
	const sharedRunPromise = sharedErrAgent.runAgent();
	await new Promise((r) => setTimeout(r, 5));
	sharedSock.failServer('shared-boom');
	await assert.rejects(() => sharedRunPromise, /shared-boom/);
	sharedErrAgent.disconnectSocket();
	assert.equal(sharedErrAgent.isConnected, false);
	assert.deepEqual(sharedChanges, [true, false]);

	// Asynchronous W3C WebSocket.close() race: stale onclose from old shared socket must NOT kill a fresh run
	let asyncSharedSocket = null;
	let freshDedicatedSocket = null;
	const asyncRaceAgent = new WebSocketAgent({
		url: 'ws://127.0.0.1:8080/api/agent/ws',
		webSocketFactory: (url) => {
			const sock = new FakeWebSocket(url);
			if (!asyncSharedSocket) {
				sock.close = function asyncBrowserClose(code = 1000, reason = 'closing') {
					this.readyState = FakeWebSocket.CLOSING;
					setTimeout(() => {
						this.readyState = FakeWebSocket.CLOSED;
						this.onclose?.({ code, reason });
					}, 5);
				};
				asyncSharedSocket = sock;
			} else {
				sock.onClientSend = (raw, s) => {
					const input = JSON.parse(raw);
					setTimeout(() => {
						s.emitServerJson({
							type: EventType.RUN_STARTED,
							threadId: input.threadId,
							runId: input.runId
						});
						s.emitServerJson({
							type: EventType.RUN_FINISHED,
							threadId: input.threadId,
							runId: input.runId,
							outcome: { type: 'success' }
						});
					}, 15);
				};
				queueMicrotask(() => sock.openServer());
				freshDedicatedSocket = sock;
			}
			return sock;
		}
	});
	asyncRaceAgent.connectSocket();
	asyncSharedSocket.openServer();
	asyncRaceAgent.disconnectSocket();
	const raceRes = await asyncRaceAgent.runAgent();
	assert.deepEqual(raceRes.newMessages, []);
	assert.equal(freshDedicatedSocket.readyState, FakeWebSocket.CLOSED);
});

test('createAgUiStore / useAgUiAgent drives HttpAgent canonical SSE stream, InterruptOutcome, and Resume round-trip', async () => {
	const encoder = new TextEncoder();
	const capturedBodies = [];

	const httpAgent = new HttpAgent({
		url: 'http://127.0.0.1:8080/api/agent/run',
		threadId: 'thread-http-canon',
		initialState: { nomadTrip: { activeCity: null, nights: null } },
		fetch: async (_url, init) => {
			const body = JSON.parse(init.body);
			capturedBodies.push(body);

			const isResume = Array.isArray(body.resume) && body.resume.length > 0;
			const events = isResume
				? [
						{ type: EventType.RUN_STARTED, threadId: body.threadId, runId: body.runId },
						{
							type: EventType.TOOL_CALL_START,
							toolCallId: 'tc-hitl',
							toolCallName: 'updateTripItinerary',
							rawEvent: { protocol: 'MCP', callType: 'mcp:tool', server: 'nomad-mcp (:8081)' }
						},
						{
							type: EventType.TOOL_CALL_ARGS,
							toolCallId: 'tc-hitl',
							delta: '{"city":'
						},
						{
							type: EventType.TOOL_CALL_ARGS,
							toolCallId: 'tc-hitl',
							delta: `"Kyoto","nights":${body.resume[0].payload?.nights ?? 4}}`
						},
						{ type: EventType.TOOL_CALL_END, toolCallId: 'tc-hitl' },
						{
							type: EventType.TOOL_CALL_RESULT,
							messageId: 'tm-1',
							toolCallId: 'tc-hitl',
							content: 'plain-text-ack',
							role: 'tool'
						},
						{
							type: EventType.STATE_SNAPSHOT,
							snapshot: { nomadTrip: { activeCity: 'Kyoto', nights: 4 } }
						},
						{
							type: EventType.STATE_DELTA,
							delta: [
								{
									op: 'replace',
									path: '/nomadTrip/nights',
									value: body.resume[0].payload?.nights ?? 5
								}
							]
						},
						{ type: EventType.TEXT_MESSAGE_START, messageId: 'm-res', role: 'assistant' },
						{
							type: EventType.TEXT_MESSAGE_CONTENT,
							messageId: 'm-res',
							delta: 'Sanctuary confirmed.'
						},
						{ type: EventType.TEXT_MESSAGE_END, messageId: 'm-res' },
						{
							type: EventType.RUN_FINISHED,
							threadId: body.threadId,
							runId: body.runId,
							outcome: { type: 'success' }
						}
					]
				: [
						{ type: EventType.RUN_STARTED, threadId: body.threadId, runId: body.runId },
						{ type: EventType.REASONING_START, messageId: 'r-1' },
						{ type: EventType.REASONING_MESSAGE_START, messageId: 'r-1', role: 'reasoning' },
						{
							type: EventType.REASONING_MESSAGE_CONTENT,
							messageId: 'r-1',
							delta: 'Checking sanctuary threshold...'
						},
						{ type: EventType.REASONING_MESSAGE_END, messageId: 'r-1' },
						{ type: EventType.REASONING_END, messageId: 'r-1' },
						{
							type: EventType.STEP_STARTED,
							stepName: 'verify-travel-advisory',
							rawEvent: { protocol: 'A2A', callType: 'a2a:task', agentName: 'Nomad-A2A' }
						},
						{
							type: EventType.ACTIVITY_SNAPSHOT,
							messageId: 'act-a2a-1',
							activityType: 'a2a.task',
							content: { summary: 'Japan Level 1 Clear' },
							rawEvent: { protocol: 'A2A', callType: 'a2a:artifact' }
						},
						{
							type: EventType.STEP_FINISHED,
							stepName: 'verify-travel-advisory',
							rawEvent: { protocol: 'A2A', callType: 'a2a:task', agentName: 'Nomad-A2A' }
						},
						{
							type: EventType.RUN_FINISHED,
							threadId: body.threadId,
							runId: body.runId,
							outcome: {
								type: 'interrupt',
								interrupts: [
									{
										id: 'int-kyoto-1',
										reason: 'high_value_sanctuary_hold',
										message: 'Confirm $1,840 hold at Hoshinoya',
										metadata: { city: 'Kyoto', nights: 4, costUsd: 1840 }
									}
								]
							}
						}
					];

			const sseBody = events.map((e) => `data: ${JSON.stringify(e)}\n\n`).join('');
			return new Response(
				new ReadableStream({
					start(controller) {
						controller.enqueue(encoder.encode(sseBody));
						controller.close();
					}
				}),
				{
					status: 200,
					headers: { 'Content-Type': 'text/event-stream' }
				}
			);
		}
	});

	const store = createAgUiStore({ agent: httpAgent });
	assert.equal(store.agent, httpAgent);
	const noopUnsub = store.subscribe(null);
	noopUnsub();

	await store.runAgent('Reserve Hoshinoya in Kyoto ($1840)', {
		forwardedProps: { llmMode: 'ollama:llama3.2:latest' }
	});

	assert.equal(capturedBodies.length, 1);
	assert.equal(capturedBodies[0].forwardedProps.llmMode, 'ollama:llama3.2:latest');
	assert.equal(capturedBodies[0].messages.at(-1).content, 'Reserve Hoshinoya in Kyoto ($1840)');
	assert.equal(store.state.isStreaming, false);
	assert.equal(store.state.reasoningBuffer, 'Checking sanctuary threshold...');
	assert.equal(store.state.steps.length, 1);
	assert.equal(store.state.steps[0].status, 'completed');
	assert.ok(store.state.activities['act-a2a-1']);
	assert.equal(store.state.pendingInterrupts.length, 1);
	assert.equal(store.state.pendingInterrupts[0].id, 'int-kyoto-1');

	// Resume interrupt with payload
	await store.resumeInterrupt('int-kyoto-1', 'resolved', { approved: true, nights: 5 });

	assert.equal(capturedBodies.length, 2);
	assert.equal(capturedBodies[1].resume[0].interruptId, 'int-kyoto-1');
	assert.equal(capturedBodies[1].resume[0].status, 'resolved');
	assert.equal(capturedBodies[1].resume[0].payload.nights, 5);
	assert.equal(store.state.pendingInterrupts.length, 0);
	assert.equal(store.state.agentState.nomadTrip.activeCity, 'Kyoto');
	assert.equal(store.state.agentState.nomadTrip.nights, 5);
	assert.equal(store.state.toolCalls.length, 1);
	assert.deepEqual(store.state.toolCalls[0].args, { city: 'Kyoto', nights: 5 });
	assert.equal(store.state.toolCalls[0].result, 'plain-text-ack');
	assert.equal(store.state.toolCalls[0].status, 'completed');
	assert.equal(store.state.messages.at(-1).content, 'Sanctuary confirmed.');

	// Also verify cancelled interrupt resume (omits payload)
	await store.resumeInterrupt('int-kyoto-2', 'cancelled');
	assert.equal(capturedBodies.length, 3);
	assert.equal(capturedBodies[2].resume[0].status, 'cancelled');
	assert.equal(capturedBodies[2].resume[0].payload, undefined);

	// ACTIVITY_DELTA (both existing and brand-new activityId) + parallel steps + null tool args + RUN_ERROR + abortRun + reset
	let errState = reduceCanonicalAgUiEvent(store.state, null);
	errState = reduceCanonicalAgUiEvent(errState, {
		type: EventType.STEP_STARTED,
		stepName: 'parallel-step-a'
	});
	errState = reduceCanonicalAgUiEvent(errState, {
		type: EventType.STEP_STARTED,
		stepName: 'parallel-step-b'
	});
	errState = reduceCanonicalAgUiEvent(errState, {
		type: EventType.STEP_FINISHED,
		stepName: 'parallel-step-a'
	});
	assert.equal(
		errState.steps.find((s) => s.stepName === 'parallel-step-a').status,
		'completed'
	);
	assert.equal(
		errState.steps.find((s) => s.stepName === 'parallel-step-b').status,
		'running'
	);
	errState = reduceCanonicalAgUiEvent(errState, {
		type: EventType.TOOL_CALL_START,
		toolCallId: 'tc-null-args',
		toolCallName: 'noopTool'
	});
	errState = reduceCanonicalAgUiEvent(errState, {
		type: EventType.TOOL_CALL_ARGS,
		toolCallId: 'tc-null-args',
		delta: 'null'
	});
	assert.deepEqual(
		errState.toolCalls.find((tc) => tc.toolCallId === 'tc-null-args').args,
		{}
	);
	errState = reduceCanonicalAgUiEvent(errState, {
		type: EventType.ACTIVITY_DELTA,
		messageId: 'act-new',
		activityType: 'a2a.task',
		patch: [{ op: 'add', path: '/summary', value: 'Created' }]
	});
	assert.equal(errState.activities['act-new'].content.summary, 'Created');

	errState = reduceCanonicalAgUiEvent(errState, {
		type: EventType.RUN_ERROR,
		message: 'Upstream model timeout',
		code: 'TIMEOUT'
	});
	assert.equal(errState.isStreaming, false);
	assert.deepEqual(errState.lastError, { message: 'Upstream model timeout', code: 'TIMEOUT' });

	store.abortRun();
	assert.equal(store.state.isStreaming, false);
	store.reset('thread-http-canon-2');
	assert.equal(store.state.threadId, 'thread-http-canon-2');
	assert.equal(httpAgent.threadId, 'thread-http-canon-2');
});

test('backward compatibility: parseSseChunk, interaction helpers, and fetch fallback work seamlessly', async () => {
	assert.deepEqual(parseSseChunk(''), { events: [], remainder: '' });
	assert.deepEqual(parseSseChunk('\n\nevent: ping\ndata: \ndata: {malformed}\n\n'), {
		events: [],
		remainder: ''
	});
	assert.equal(selectTurnOption(null, 'm1', 'o1'), null);
	assert.equal(lockTurnInteraction(null, 'm1'), null);
	assert.equal(recordClientOutboundEvent(null, 'PING'), null);

	const snapshots = [];
	const agent = useAgUiAgent({
		threadId: 'th-pkg-1',
		fetchImpl: async () => ({ ok: false, status: 503, body: null })
	});
	const unsub = agent.subscribe((s) => snapshots.push(s));
	assert.equal(agent.state.threadId, 'th-pkg-1');
	assert.equal(agent.getState().isStreaming, false);

	await assert.rejects(() => agent.runAgent('test fallback'), /status 503/);

	const encoder = new TextEncoder();
	const fetchStreamAgent = useAgUiAgent({
		threadId: 'th-fetch-ok',
		fetchImpl: async () => ({
			ok: true,
			status: 200,
			body: new ReadableStream({
				start(controller) {
					controller.enqueue(
						encoder.encode(
							'data: {"type":"RUN_STARTED","threadId":"th-fetch-ok","runId":"r1"}\n\n' +
								'data: {"type":"TEXT_MESSAGE_CONTENT","messageId":"m1","delta":"Streamed ok"}\n\n' +
								'data: {"type":"RUN_FINISHED","threadId":"th-fetch-ok","runId":"r1"}\n\n'
						)
					);
					controller.close();
				}
			})
		})
	});
	await fetchStreamAgent.sendPrompt('Hello via sendPrompt');
	assert.equal(fetchStreamAgent.state.messages.at(-1).content, 'Streamed ok');

	const noFetchAgent = useAgUiAgent({ fetchImpl: null });
	await assert.rejects(() => noFetchAgent.sendPrompt('hi'), /fetch implementation is required/);

	const bodylessAgent = useAgUiAgent({
		fetchImpl: async () => ({ ok: true, status: 200, body: null })
	});
	await assert.rejects(() => bodylessAgent.sendPrompt('hi'), /status 200/);

	// Mid-stream transport failure resets isStreaming=false, records lastError, and releases reader lock
	let readerReleased = false;
	const midStreamFailStore = useAgUiAgent({
		fetchImpl: async () => ({
			ok: true,
			status: 200,
			body: {
				getReader() {
					let step = 0;
					return {
						async read() {
							step += 1;
							if (step === 1) {
								return {
									done: false,
									value: encoder.encode(
										'data: {"type":"RUN_STARTED","threadId":"th-mid","runId":"r-mid"}\n\n'
									)
								};
							}
							throw new Error('SSE connection reset mid-stream');
						},
						releaseLock() {
							readerReleased = true;
						}
					};
				}
			}
		})
	});
	await assert.rejects(() => midStreamFailStore.sendPrompt('hi'), /SSE connection reset mid-stream/);
	assert.equal(midStreamFailStore.state.isStreaming, false);
	assert.equal(midStreamFailStore.state.lastError.message, 'SSE connection reset mid-stream');
	assert.equal(readerReleased, true);

	const sdkMidStreamFailAgent = new HttpAgent({
		url: 'http://127.0.0.1:8080/api/agent/run',
		fetch: async () =>
			new Response(
				new ReadableStream({
					start(controller) {
						controller.enqueue(
							encoder.encode('data: {"type":"RUN_STARTED","threadId":"t","runId":"r"}\n\n')
						);
						controller.error(new Error('SDK stream broken'));
					}
				}),
				{ status: 200, headers: { 'Content-Type': 'text/event-stream' } }
			)
	});
	const sdkFailStore = createAgUiStore({ agent: sdkMidStreamFailAgent });
	await assert.rejects(() => sdkFailStore.runAgent('test'), /SDK stream broken/);
	assert.equal(sdkFailStore.state.isStreaming, false);
	assert.equal(sdkFailStore.state.lastError.message, 'SDK stream broken');

	agent.dispatch({ id: 'd0', type: 'CUSTOM', name: 'info', value: 1 });
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
				id: 'm-multi',
				role: 'assistant',
				interaction: { mode: 'multi_select', selectedValues: [], submitted: false }
			},
			{
				id: 'm-single',
				role: 'assistant',
				interaction: { mode: 'single_select', selectedValues: [], submitted: false }
			},
			{
				id: 'm-plain',
				role: 'user',
				content: 'Plain turn without interaction'
			}
		]
	};
	const customAgent = useAgUiAgent({ initialState: withInteraction });
	customAgent.selectOption('m-multi', 'opt-1');
	customAgent.selectOption('m-multi', 'opt-1');
	assert.deepEqual(customAgent.state.messages[0].interaction.selectedValues, []);
	customAgent.selectOption('m-single', 'opt-single');
	assert.equal(customAgent.state.messages[1].interaction.submitted, true);
	customAgent.selectOption('m-single', 'ignored-after-submit');
	assert.deepEqual(customAgent.state.messages[1].interaction.selectedValues, ['opt-single']);
	customAgent.lockInteraction('m-multi');
	assert.equal(customAgent.state.messages[0].interaction.submitted, true);
	customAgent.lockInteraction('m-plain');
	assert.equal(customAgent.state.messages[2].interaction, undefined);

	customAgent.reset('th-pkg-2');
	assert.equal(customAgent.state.threadId, 'th-pkg-2');
	unsub();
	assert.ok(snapshots.length >= 4);
});

test('AG-UI activity surface kit: taxonomy, registry, map deep-links, coordinate formatting, and idempotent upsert', () => {
	assert.deepEqual(AGUI_ACTIVITY_TYPES, {
		WEATHER: 'agui:weather',
		MAP_PLACES: 'agui:map-places',
		SECURITY_ADVISORY: 'agui:security-advisory',
		FX_BUDGET: 'agui:fx-budget',
		CULTURE_AUDIO: 'agui:culture-audio'
	});

	const weatherEvt = {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-weather-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.WEATHER,
		content: { city: 'Kyoto', temperatureC: 18.4 }
	};
	assert.equal(resolveEventProtocolMeta(weatherEvt).callType, 'agui:activity');
	assert.equal(resolveEventProtocolMeta(weatherEvt).family, 'AG-UI');

	const registry = createActivityRegistry({
		[AGUI_ACTIVITY_TYPES.WEATHER]: 'WeatherComponent'
	});
	registry.register(AGUI_ACTIVITY_TYPES.MAP_PLACES, 'MapComponent');
	registry.register('', 'Ignored');
	assert.equal(registry.has(AGUI_ACTIVITY_TYPES.WEATHER), true);
	assert.equal(registry.has('agui:unknown'), false);
	assert.equal(registry.resolve(AGUI_ACTIVITY_TYPES.WEATHER), 'WeatherComponent');
	assert.equal(registry.resolve(weatherEvt), 'WeatherComponent');
	assert.equal(registry.resolve(null), null);
	assert.deepEqual(registry.listTypes(), [
		AGUI_ACTIVITY_TYPES.WEATHER,
		AGUI_ACTIVITY_TYPES.MAP_PLACES
	]);

	assert.equal(formatCoordinatesLabel(35.0116, 135.7681), '35.0116° N, 135.7681° E');
	assert.equal(formatCoordinatesLabel(-33.8688, -70.6693), '33.8688° S, 70.6693° W');
	assert.equal(formatCoordinatesLabel(null, 135.76), null);
	assert.equal(formatCoordinatesLabel(Number.NaN, 10), null);

	const cityLinks = buildPlaceMapLinks({
		city: 'Kyoto',
		country: 'Japan',
		latitude: 35.0116,
		longitude: 135.7681,
		wikiUrl: 'https://en.wikipedia.org/wiki/Kyoto'
	});
	assert.equal(cityLinks.coordinatesLabel, '35.0116° N, 135.7681° E');
	assert.equal(cityLinks.coordinatesRaw, '35.0116, 135.7681');
	assert.ok(cityLinks.openStreetMapUrl.includes('mlat=35.0116'));
	assert.ok(cityLinks.openStreetMapEmbedUrl.includes('openstreetmap.org/export/embed.html'));
	assert.ok(cityLinks.googleMapsUrl.includes('35.0116,135.7681'));
	assert.ok(cityLinks.appleMapsUrl.includes('ll=35.0116,135.7681'));
	assert.equal(cityLinks.wikipediaUrl, 'https://en.wikipedia.org/wiki/Kyoto');

	const stopLinks = buildPlaceMapLinks({
		name: 'Fushimi Inari Taisha',
		city: 'Kyoto',
		country: 'Japan'
	});
	assert.equal(stopLinks.openStreetMapEmbedUrl, null);
	assert.equal(stopLinks.coordinatesLabel, null);
	assert.ok(stopLinks.googleMapsUrl.includes('Fushimi%20Inari%20Taisha'));
	assert.ok(stopLinks.appleMapsUrl.includes('Fushimi%20Inari%20Taisha'));
	assert.ok(stopLinks.openStreetMapUrl.includes('openstreetmap.org/search'));
	assert.ok(stopLinks.wikipediaUrl.includes('Special:Search'));

	const stopWithCoords = buildPlaceMapLinks({
		name: 'Kiyomizu-dera Timber Stage',
		city: 'Kyoto',
		country: 'Japan',
		latitude: 34.9949,
		longitude: 135.785
	});
	assert.ok(stopWithCoords.googleMapsUrl.includes('Kiyomizu-dera'));
	assert.ok(stopWithCoords.openStreetMapEmbedUrl.includes('34.99490'));

	const fallbackLinks = buildPlaceMapLinks();
	assert.ok(fallbackLinks.wikipediaUrl.includes('wikipedia.org/wiki/'));

	let list = upsertActivityList([], weatherEvt);
	assert.equal(list.length, 1);
	list = upsertActivityList(list, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-weather-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.WEATHER,
		content: { city: 'Kyoto', temperatureC: 21.0 }
	});
	assert.equal(list.length, 1);
	assert.equal(list[0].content.temperatureC, 21.0);

	list = upsertActivityList(list, {
		type: EventType.ACTIVITY_SNAPSHOT,
		messageId: 'act-sec-kyoto',
		activityType: AGUI_ACTIVITY_TYPES.SECURITY_ADVISORY,
		content: { status: 'VERIFIED' }
	});
	assert.equal(list.length, 2);
	assert.equal(upsertActivityList(list, null), list);
	assert.equal(upsertActivityList(list, { activityType: '   ' }), list);
});
