<script>
	import { onMount } from 'svelte';
	import {
		ChevronRight,
		Compass,
		Cpu,
		HelpCircle,
		RotateCcw,
		Terminal,
		Wifi,
		X
	} from '@lucide/svelte';
	import {
		SCENARIO_PRESETS,
		createInitialWorkbenchState,
		lockTurnInteraction,
		parseSseChunk,
		recordClientOutboundEvent,
		reduceAgUiEvent,
		selectTurnOption,
		updateTripNightsInState
	} from '$lib/agui-client.js';
	import ClusterStatePanel from './ClusterStatePanel.svelte';
	import MessageHistory from './MessageHistory.svelte';
	import InputBar from './InputBar.svelte';
	import ProtocolInspector from './ProtocolInspector.svelte';

	const LLM_MODES = [
		{ value: 'ollama:llama3.2:latest', label: 'Llama 3.2 • Live Local Ollama' },
		{ value: 'ollama:ministral-3:8b', label: 'Ministral 8B • Live Local Ollama' },
		{ value: 'ollama:gemma3:latest', label: 'Gemma 3 • Live Local Ollama' },
		{ value: 'ollama:qwen2.5:3b', label: 'Qwen 2.5 3B • Live Local Ollama' },
		{ value: 'deterministic', label: 'Deterministic Concierge Engine (Instant)' }
	];

	const HEADER_PRESETS = SCENARIO_PRESETS.slice(0, 4);

	let workbench = $state(createInitialWorkbenchState('thread-nomad-1'));
	let selectedMode = $state('ollama:llama3.2:latest');
	let abortController = $state(null);
	let isInspectorOpen = $state(true);
	let isGuideOpen = $state(false);
	let wsSocket = null;

	function connectWebSocket() {
		if (typeof window === 'undefined' || typeof WebSocket === 'undefined') return;
		if (wsSocket && (wsSocket.readyState === WebSocket.OPEN || wsSocket.readyState === WebSocket.CONNECTING)) {
			return;
		}
		try {
			const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
			const socket = new WebSocket(`${proto}//${window.location.host}/api/agent/ws`);
			wsSocket = socket;

			socket.onopen = () => {
				workbench = {
					...workbench,
					wsConnected: true,
					transportMode: 'ws',
					statusText: 'WebSocket Full-Duplex Connected (/api/agent/ws)'
				};
			};

			socket.onmessage = (msgEvt) => {
				try {
					const parsed = JSON.parse(msgEvt.data);
					if (parsed && parsed.type && parsed.type !== 'pong') {
						workbench = reduceAgUiEvent(workbench, parsed);
					}
				} catch {
					// ignore malformed frame
				}
			};

			socket.onclose = () => {
				if (wsSocket === socket) {
					wsSocket = null;
				}
				workbench = {
					...workbench,
					wsConnected: false,
					transportMode: 'sse'
				};
			};

			socket.onerror = () => {
				workbench = {
					...workbench,
					wsConnected: false,
					transportMode: 'sse'
				};
			};
		} catch {
			workbench = {
				...workbench,
				wsConnected: false,
				transportMode: 'sse'
			};
		}
	}

	onMount(() => {
		connectWebSocket();
		return () => {
			wsSocket?.close();
			wsSocket = null;
		};
	});

	function sendWebSocketCommand(commandType, payload) {
		if (wsSocket && wsSocket.readyState === WebSocket.OPEN) {
			const envelope = {
				type: commandType,
				threadId: workbench.threadId,
				...payload
			};
			workbench = recordClientOutboundEvent(workbench, commandType, envelope);
			wsSocket.send(JSON.stringify(envelope));
			return true;
		}
		return false;
	}

	function resolvePresetPrompt(preset) {
		if (preset.id === 'nomad-hitl') {
			const trip = workbench.clusterState?.nomadTrip;
			const city = trip?.activeCity ?? 'Kyoto';
			const stopTitle = trip?.stopTitle ?? `${city} Architectural Sanctuary`;
			const nights = trip?.nights ?? 4;
			return `Reserve ${stopTitle} in ${city} for ${nights} nights ($1840)`;
		}
		return preset.prompt;
	}

	async function consumeSseResponse(response) {
		if (!response.ok || !response.body) {
			throw new Error(`HTTP ${response.status}`);
		}
		const reader = response.body.getReader();
		const decoder = new TextDecoder();
		let buffer = '';

		while (true) {
			const { value, done } = await reader.read();
			if (done) {
				buffer += decoder.decode();
				break;
			}
			buffer += decoder.decode(value, { stream: true });
			const parsed = parseSseChunk(buffer);
			buffer = parsed.remainder;
			for (const evt of parsed.events) {
				workbench = reduceAgUiEvent(workbench, evt);
			}
		}

		if (buffer.trim()) {
			const flushed = parseSseChunk(buffer + '\n\n');
			for (const evt of flushed.events) {
				workbench = reduceAgUiEvent(workbench, evt);
			}
		}
	}

	async function runScenarioPrompt(promptText) {
		workbench = {
			...workbench,
			isStreaming: true,
			statusText: 'Curating destination dossier...',
			streamBuffer: '',
			activeTurn: {
				reasoning: null,
				timeline: [],
				media: [],
				interaction: null,
				sources: [],
				followUps: []
			},
			messages: [
				...workbench.messages,
				{
					id: `msg-user-${Date.now()}`,
					role: 'user',
					content: promptText,
					mcpCalls: [],
					a2aSteps: []
				}
			]
		};

		if (
			sendWebSocketCommand('RUN_AGENT', {
				prompt: promptText,
				state: workbench.clusterState,
				mode: selectedMode
			})
		) {
			return;
		}

		abortController = new AbortController();
		try {
			const response = await fetch('/api/agent/run', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					threadId: workbench.threadId,
					prompt: promptText,
					state: workbench.clusterState,
					mode: selectedMode
				}),
				signal: abortController.signal
			});
			await consumeSseResponse(response);
		} catch (err) {
			workbench = {
				...workbench,
				isStreaming: false,
				statusText:
					err?.name === 'AbortError'
						? 'Stream paused by traveler'
						: `Bridge error (${err?.message ?? 'connect failed to :8080'})`
			};
		} finally {
			if (workbench.isStreaming) {
				workbench = {
					...workbench,
					isStreaming: false
				};
			}
		}
	}

	async function submitHitlDecision(approved, modifiedArgs = null) {
		const reason = approved
			? 'Traveler authorized sanctuary hold'
			: 'Traveler declined reservation hold';

		if (
			sendWebSocketCommand('APPROVE_HITL', {
				approved,
				reason,
				modifiedArgs
			})
		) {
			return;
		}

		abortController = new AbortController();
		try {
			const response = await fetch('/api/agent/approve', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					threadId: workbench.threadId,
					approved,
					reason,
					modifiedArgs
				}),
				signal: abortController.signal
			});
			await consumeSseResponse(response);
		} catch (err) {
			workbench = {
				...workbench,
				isStreaming: false,
				statusText: `Approval error (${err?.message ?? 'failed'})`
			};
		} finally {
			if (workbench.isStreaming) {
				workbench = {
					...workbench,
					isStreaming: false
				};
			}
		}
	}

	async function handleUpdateTripParams({ nights, homeCurrency }) {
		if (sendWebSocketCommand('UPDATE_TRIP_PARAMS', { nights, homeCurrency })) {
			return;
		}
		try {
			const res = await fetch('/api/agent/params', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					threadId: workbench.threadId,
					nights,
					homeCurrency
				})
			});
			if (res.ok) {
				const delta = await res.json();
				workbench = reduceAgUiEvent(workbench, {
					id: `params-${Date.now()}`,
					type: 'STATE_DELTA',
					payload: delta
				});
			}
		} catch {
			workbench = {
				...workbench,
				clusterState: updateTripNightsInState(
					workbench.clusterState,
					(nights ?? 4) - (workbench.clusterState?.nomadTrip?.nights ?? 4)
				)
			};
		}
	}

	async function handleResetMemory() {
		if (sendWebSocketCommand('RESET_MEMORY', {})) {
			return;
		}
		try {
			const res = await fetch('/api/agent/memory/reset', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ threadId: workbench.threadId })
			});
			if (res.ok) {
				const delta = await res.json();
				workbench = reduceAgUiEvent(workbench, {
					id: `reset-${Date.now()}`,
					type: 'STATE_DELTA',
					payload: delta
				});
			}
		} catch {
			workbench = createInitialWorkbenchState(workbench.threadId);
		}
	}

	function handleSelectOption(messageId, opt) {
		const targetMsg = workbench.messages.find((m) => m.id === messageId);
		const isMulti = targetMsg?.interaction?.mode === 'multi_select';
		workbench = selectTurnOption(workbench, messageId, opt.id);
		if (!isMulti) {
			if (opt.prompt) {
				runScenarioPrompt(opt.prompt);
			} else {
				const city = workbench.clusterState?.nomadTrip?.activeCity ?? 'Kyoto';
				const detail = opt.description ? ` (${opt.description})` : '';
				runScenarioPrompt(`Selected itinerary preference: ${opt.label}${detail} in ${city}`);
			}
		}
	}

	function handleConfirmMultiSelect(messageId, selectedIds) {
		const targetMsg = workbench.messages.find((m) => m.id === messageId);
		const options = targetMsg?.interaction?.options ?? [];
		const labels = selectedIds.map((id) => {
			const match = options.find((o) => o.id === id);
			return match ? match.label : id;
		});
		const city = workbench.clusterState?.nomadTrip?.activeCity ?? 'Kyoto';
		workbench = lockTurnInteraction(workbench, messageId);
		runScenarioPrompt(`Confirmed multi-select itinerary preferences: ${labels.join('; ')} in ${city}`);
	}

	function clearInspectorEvents() {
		workbench = { ...workbench, events: [] };
	}

	function cancelStream() {
		if (sendWebSocketCommand('CANCEL_RUN', {})) {
			return;
		}
		abortController?.abort();
	}

	function handleWindowKeydown(event) {
		if (event.key === 'Escape' && isGuideOpen) {
			isGuideOpen = false;
		}
	}
</script>

<svelte:window onkeydown={handleWindowKeydown} />

<main class="workbench-grid {isInspectorOpen ? '' : 'inspector-collapsed'}">
	<!-- Editorial Precision Header -->
	<header
		class="workbench-header nomad-panel relative flex flex-wrap items-center justify-between gap-3 rounded-2xl px-4 py-3 sm:px-5"
	>
		<div class="flex items-center gap-3">
			<div class="flex size-9 items-center justify-center rounded-xl bg-[#141413] text-white">
				<Compass class="size-5" />
			</div>
			<div>
				<h1 class="text-sm font-bold tracking-tight text-[#141413] sm:text-base">
					Nomad — Cultural & Architectural Concierge
				</h1>
				<div class="flex flex-wrap items-center gap-2 text-[11px] text-[#575551]">
					<span>Open-Meteo Weather • Wikipedia • iTunes 30s Audio • Frankfurter FX</span>
				</div>
			</div>
		</div>

		<div class="flex flex-wrap items-center gap-2">
			<!-- Transport & Memory Telemetry Group -->
			<div class="flex items-center gap-1.5" role="group" aria-label="Transport and Memory Controls">
				<button
					type="button"
					onclick={connectWebSocket}
					class="inline-flex items-center gap-1.5 rounded-xl border px-2.5 py-1.5 font-mono text-[11px] font-semibold transition-colors {workbench.wsConnected
						? 'border-[#1F6E50]/30 bg-[#EAF4EF] text-[#1F6E50]'
						: 'border-[#E4E0D8] bg-[#F6F5F2] text-[#575551] hover:text-[#141413]'}"
					title={workbench.wsConnected
						? 'Full-Duplex Quarkus WebSocket Next Connected (/api/agent/ws)'
						: 'Click to reconnect WebSocket (/api/agent/ws)'}
				>
					<Wifi class="size-3" />
					<span>{workbench.wsConnected ? 'WS Connected' : 'SSE Mode'}</span>
				</button>

				<div class="inline-flex items-center gap-1 rounded-xl border border-[#E4E0D8] bg-[#F6F5F2] px-2.5 py-1 font-mono text-[11px] text-[#141413]">
					<span class="font-semibold tabular-nums" title="Sliding-window conversational memory turns">
						Memory: {workbench.memoryTurns ?? 0}
					</span>
					<button
						type="button"
						disabled={workbench.isStreaming}
						onclick={handleResetMemory}
						aria-label="Reset conversational memory"
						title="Reset Conversational Memory & Traveler Profile"
						class="ml-1 rounded p-0.5 text-[#575551] transition-colors hover:bg-[#E4E0D8] hover:text-[#C84B31] disabled:opacity-40"
					>
						<RotateCcw class="size-3" />
					</button>
				</div>

				<!-- Engine Mode Selector -->
				<div class="flex items-center gap-1.5 rounded-xl border border-[#E4E0D8] bg-[#F6F5F2] px-2.5 py-1.5">
					<Cpu class="size-3.5 text-[#1F6E50]" />
					<label for="agui-llm-mode" class="sr-only">Engine Mode</label>
					<select
						id="agui-llm-mode"
						bind:value={selectedMode}
						disabled={workbench.isStreaming}
						class="cursor-pointer bg-transparent font-mono text-xs font-medium text-[#141413] focus:outline-none"
					>
						{#each LLM_MODES as m (m.value)}
							<option value={m.value} class="bg-white text-[#141413]">{m.label}</option>
						{/each}
					</select>
				</div>
			</div>

			<!-- Quick Destination & Protocol Scenarios (<= 4 items for cognitive clarity) -->
			<div class="flex flex-wrap items-center gap-1.5" role="group" aria-label="Preset Scenarios">
				{#each HEADER_PRESETS as preset (preset.id)}
					<button
						type="button"
						disabled={workbench.isStreaming}
						onclick={() => runScenarioPrompt(resolvePresetPrompt(preset))}
						class="group inline-flex items-center gap-1 rounded-xl border border-[#E4E0D8] bg-white px-2.5 py-1.5 text-xs font-semibold text-[#141413] transition-colors hover:border-[#C84B31] hover:bg-[#FBEEE9]/40 disabled:opacity-40"
					>
						<span>{preset.label}</span>
						<ChevronRight class="size-3 text-[#575551] transition-transform group-hover:translate-x-0.5 group-hover:text-[#C84B31]" />
					</button>
				{/each}
			</div>

			<!-- Inspector & Quick Guide Controls -->
			<div class="flex items-center gap-1.5" role="group" aria-label="Inspector and Help">
				<button
					type="button"
					onclick={() => (isGuideOpen = !isGuideOpen)}
					aria-expanded={isGuideOpen}
					aria-label="Open Concierge Guide and Keyboard Shortcuts"
					class="inline-flex items-center gap-1 rounded-xl border border-[#E4E0D8] bg-white px-2.5 py-1.5 text-xs font-semibold text-[#141413] transition-colors hover:bg-[#EFECE6]"
					title="Concierge Guide & Keyboard Shortcuts"
				>
					<HelpCircle class="size-3.5 text-[#575551]" />
					<span class="hidden md:inline">Guide</span>
				</button>

				<button
					type="button"
					onclick={() => (isInspectorOpen = !isInspectorOpen)}
					class="inline-flex items-center gap-1.5 rounded-xl border px-3 py-1.5 font-mono text-xs font-semibold transition-colors {isInspectorOpen
						? 'border-[#141413] bg-[#141413] text-white'
						: 'border-[#E4E0D8] bg-white text-[#141413] hover:bg-[#EFECE6]'}"
					title={isInspectorOpen ? 'Collapse Wire Telemetry' : 'Expand Wire Telemetry'}
				>
					<Terminal class="size-3.5" />
					<span class="hidden sm:inline">Wire</span>
					<span class="rounded bg-white/15 px-1.5 py-0.2 text-[10px] tabular-nums">
						{workbench.events.length}
					</span>
				</button>
			</div>
		</div>

		{#if isGuideOpen}
			<div
				class="absolute top-full right-4 z-30 mt-2 w-80 sm:w-96 rounded-2xl border border-[#CFCAC0] bg-white p-4 shadow-xl space-y-3"
				role="dialog"
				aria-label="Concierge Guide and Keyboard Shortcuts"
			>
				<div class="flex items-center justify-between border-b border-[#E4E0D8] pb-2">
					<h2 class="text-xs font-bold text-[#141413]">
						Tri-Protocol Studio Guide & Shortcuts
					</h2>
					<button
						type="button"
						onclick={() => (isGuideOpen = false)}
						aria-label="Close guide"
						class="rounded p-1 text-[#575551] hover:bg-[#EFECE6] hover:text-[#141413]"
					>
						<X class="size-3.5" />
					</button>
				</div>
				<div class="space-y-2 text-xs text-[#575551]">
					<div class="flex items-center justify-between">
						<span>Focus Concierge Prompt</span>
						<kbd class="rounded border border-[#CFCAC0] bg-[#F6F5F2] px-1.5 py-0.5 font-mono text-[10px] text-[#141413]">⌘K</kbd>
					</div>
					<div class="flex items-center justify-between">
						<span>Open Slash Commands</span>
						<kbd class="rounded border border-[#CFCAC0] bg-[#F6F5F2] px-1.5 py-0.5 font-mono text-[10px] text-[#141413]">/</kbd>
					</div>
					<div class="flex items-center justify-between">
						<span>Dismiss Popover / Guide</span>
						<kbd class="rounded border border-[#CFCAC0] bg-[#F6F5F2] px-1.5 py-0.5 font-mono text-[10px] text-[#141413]">Esc</kbd>
					</div>
				</div>
				<div class="border-t border-[#E4E0D8] pt-2 text-[11px] text-[#575551] space-y-1">
					<p><strong class="text-[#141413]">Conversational Memory:</strong> Mention dietary, architectural, or currency preferences in any turn—they persist across turns and update <code class="font-mono">traveler://preferences</code>.</p>
					<p><strong class="text-[#141413]">Interactive Choices:</strong> Ask for <em>"2 random options"</em> or <em>"4 options so I can select multiple"</em> to render on-demand choice cards.</p>
				</div>
			</div>
		{/if}
	</header>

	<!-- Pane 1: Left Trip Dossier & Protocol Rail -->
	<ClusterStatePanel
		clusterState={workbench.clusterState}
		messages={workbench.messages}
		isStreaming={workbench.isStreaming}
		onTriggerSkill={runScenarioPrompt}
		onUpdateTripParams={handleUpdateTripParams}
	/>

	<!-- Pane 2: Center Concierge Conversation & Modular AG-UI Component Kit -->
	<section
		class="workbench-chat nomad-panel flex h-full min-h-0 flex-col justify-between overflow-hidden rounded-2xl"
		aria-label="Concierge Conversation"
	>
		<MessageHistory
			messages={workbench.messages}
			isStreaming={workbench.isStreaming}
			streamBuffer={workbench.streamBuffer}
			statusText={workbench.statusText}
			activeTurn={workbench.activeTurn}
			pendingApproval={workbench.pendingApproval}
			onSelectOption={handleSelectOption}
			onConfirmMultiSelect={handleConfirmMultiSelect}
			onTriggerPrompt={runScenarioPrompt}
			onApproveHitl={(mods) => submitHitlDecision(true, mods)}
			onRejectHitl={() => submitHitlDecision(false)}
		/>

		<InputBar
			isStreaming={workbench.isStreaming}
			onSubmit={runScenarioPrompt}
			onCancel={cancelStream}
		/>
	</section>

	<!-- Pane 3: Collapsible AG-UI Wire Telemetry Inspector -->
	{#if isInspectorOpen}
		<ProtocolInspector
			events={workbench.events}
			isOpen={isInspectorOpen}
			onClose={() => (isInspectorOpen = false)}
			onClear={clearInspectorEvents}
		/>
	{/if}
</main>
