import { AbstractAgent, enforceOutgoingInput, isRecognizedEvent } from '@ag-ui/client';
import { EventType } from '@ag-ui/core';
import { Observable } from 'rxjs';
import { applyJsonPatch } from './json-patch.js';

const WS_OPEN = 1;
const WS_CONNECTING = 0;

function defaultWebSocketFactory(url, protocols) {
	return protocols ? new globalThis.WebSocket(url, protocols) : new globalThis.WebSocket(url);
}

/**
 * Full-duplex WebSocket transport implementation of `@ag-ui/client`'s `AbstractAgent`.
 * Sends canonical `RunAgentInput` JSON frames and decodes inbound canonical `Event` JSON frames.
 */
export class WebSocketAgent extends AbstractAgent {
	#sharedSocket = null;
	#activeRunSocket = null;
	#activeObserver = null;

	constructor(config) {
		super(config);
		this.url = config.url;
		this.protocols = config.protocols;
		this.webSocketFactory = config.webSocketFactory ?? defaultWebSocketFactory;
		this.onControlEvent = config.onControlEvent ?? null;
		this.onSpontaneousEvent = config.onSpontaneousEvent ?? null;
		this.onConnectionChange = config.onConnectionChange ?? null;
	}

	get isConnected() {
		return Boolean(this.#sharedSocket && this.#sharedSocket.readyState === WS_OPEN);
	}

	connectSocket() {
		if (
			this.#sharedSocket &&
			(this.#sharedSocket.readyState === WS_OPEN || this.#sharedSocket.readyState === WS_CONNECTING)
		) {
			return this.#sharedSocket;
		}
		const socket = this.webSocketFactory(this.url, this.protocols);
		this.#sharedSocket = socket;
		socket.onopen = () => this.onConnectionChange?.(true);
		socket.onmessage = (evt) => this.#routeSocketMessage(socket, evt);
		socket.onerror = (err) => this.#failActiveObserver(err);
		socket.onclose = () => {
			if (this.#sharedSocket === socket) {
				this.#sharedSocket = null;
				this.onConnectionChange?.(false);
			}
			this.#failActiveObserver(new Error('WebSocket closed before run finished'));
		};
		return socket;
	}

	disconnectSocket() {
		const sock = this.#sharedSocket;
		this.#sharedSocket = null;
		if (sock) {
			sock.close(1000, 'client disconnect');
			this.onConnectionChange?.(false);
		}
	}

	sendPing() {
		if (!this.isConnected) return false;
		this.#sharedSocket.send('ping');
		return true;
	}

	sendControlFrame(type, payload = {}) {
		if (!this.isConnected) return false;
		this.#sharedSocket.send(JSON.stringify({ type, threadId: this.threadId, ...payload }));
		return true;
	}

	run(input) {
		const validated = enforceOutgoingInput(input, { maxEntries: this.maxEnforcedEntries });
		const serialized = JSON.stringify(validated);
		return this.isConnected
			? this.#runOverSharedSocket(serialized)
			: this.#runOverDedicatedSocket(serialized);
	}

	abortRun() {
		const dedicated = this.#activeRunSocket;
		this.#activeRunSocket = null;
		this.#activeObserver = null;
		if (dedicated) {
			dedicated.close(1000, 'run aborted');
		}
		super.abortRun();
	}

	clone() {
		const cloned = new WebSocketAgent({
			agentId: this.agentId,
			description: this.description,
			threadId: this.threadId,
			initialMessages: structuredClone(this.messages),
			initialState: structuredClone(this.state),
			debug: this.debug,
			maxEnforcedEntries: this.maxEnforcedEntries,
			url: this.url,
			protocols: this.protocols,
			webSocketFactory: this.webSocketFactory,
			onControlEvent: this.onControlEvent,
			onSpontaneousEvent: this.onSpontaneousEvent,
			onConnectionChange: this.onConnectionChange
		});
		if (Array.isArray(this.middlewares) && this.middlewares.length > 0) {
			cloned.use(...this.middlewares);
		}
		return cloned;
	}

	#runOverSharedSocket(serializedInput) {
		return new Observable((subscriber) => {
			this.#activeObserver = subscriber;
			this.#sharedSocket.send(serializedInput);
			return () => {
				if (this.#activeObserver === subscriber) {
					this.#activeObserver = null;
				}
			};
		});
	}

	#runOverDedicatedSocket(serializedInput) {
		return new Observable((subscriber) => {
			const socket = this.webSocketFactory(this.url, this.protocols);
			this.#activeRunSocket = socket;
			this.#activeObserver = subscriber;
			let completed = false;

			socket.onopen = () => socket.send(serializedInput);
			socket.onmessage = (evt) => {
				completed = this.#routeSocketMessage(socket, evt) || completed;
			};
			socket.onerror = (err) => {
				if (!completed) {
					completed = true;
					subscriber.error(toTransportError(err, 'WebSocket transport error'));
				}
			};
			socket.onclose = () => {
				if (!completed) {
					completed = true;
					subscriber.error(new Error('WebSocket closed before RUN_FINISHED'));
				}
			};

			return () => {
				completed = true;
				this.#activeObserver = null;
				this.#activeRunSocket = null;
				if (socket.readyState === WS_OPEN || socket.readyState === WS_CONNECTING) {
					socket.close(1000, 'subscription closed');
				}
			};
		});
	}

	#routeSocketMessage(socket, evt) {
		const parsed = parseInboundFrame(evt?.data);
		if (!parsed || parsed.type === 'pong') return false;
		if (parsed.type === 'ping') {
			socket.send('{"type":"pong"}');
			return false;
		}
		if (!isRecognizedEvent(parsed)) {
			this.onControlEvent?.(parsed);
			return false;
		}
		if (!this.#activeObserver) {
			this.#applySpontaneousState(parsed);
			return false;
		}
		const observer = this.#activeObserver;
		const isTerminal =
			parsed.type === EventType.RUN_FINISHED || parsed.type === EventType.RUN_ERROR;
		if (isTerminal) {
			this.#activeObserver = null;
		}
		observer.next(parsed);
		if (isTerminal) {
			observer.complete();
		}
		return isTerminal;
	}

	#applySpontaneousState(event) {
		if (event.type === EventType.STATE_SNAPSHOT) {
			this.setState(event.snapshot);
		} else if (event.type === EventType.STATE_DELTA) {
			this.setState(applyJsonPatch(this.state, event.delta));
		}
		this.onSpontaneousEvent?.(event);
	}

	#failActiveObserver(err) {
		const observer = this.#activeObserver;
		if (observer) {
			this.#activeObserver = null;
			observer.error(toTransportError(err, 'WebSocket transport error'));
		}
	}
}

function toTransportError(err, fallbackMessage) {
	return err instanceof Error ? err : new Error(fallbackMessage);
}

function parseInboundFrame(raw) {
	if (typeof raw !== 'string') return null;
	try {
		const parsed = JSON.parse(raw);
		return parsed && typeof parsed.type === 'string' ? parsed : null;
	} catch {
		return null;
	}
}
