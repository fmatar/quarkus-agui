<script>
	import {
		ChevronRight,
		Compass,
		Cpu,
		Terminal
	} from '@lucide/svelte';
	import {
		SCENARIO_PRESETS,
		createInitialWorkbenchState,
		lockTurnInteraction,
		parseSseChunk,
		reduceAgUiEvent,
		selectTurnOption
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

	let workbench = $state(createInitialWorkbenchState('thread-nomad-1'));
	let selectedMode = $state('ollama:llama3.2:latest');
	let abortController = $state(null);
	let isInspectorOpen = $state(true);

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
		abortController = new AbortController();
		try {
			const response = await fetch('/api/agent/approve', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					threadId: workbench.threadId,
					approved,
					reason: approved
						? 'Traveler authorized sanctuary hold'
						: 'Traveler declined reservation hold',
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

	function handleSelectOption(messageId, opt) {
		const targetMsg = workbench.messages.find((m) => m.id === messageId);
		const isMulti = targetMsg?.interaction?.mode === 'multi_select';
		workbench = selectTurnOption(workbench, messageId, opt.id);
		if (!isMulti) {
			runScenarioPrompt(`Selected itinerary preference: ${opt.label}`);
		}
	}

	function handleConfirmMultiSelect(messageId, selectedIds) {
		workbench = lockTurnInteraction(workbench, messageId);
		runScenarioPrompt(`Confirmed multi-select itinerary preferences: ${selectedIds.join(', ')}`);
	}

	function clearInspectorEvents() {
		workbench = { ...workbench, events: [] };
	}

	function cancelStream() {
		abortController?.abort();
	}
</script>

<main class="workbench-grid {isInspectorOpen ? '' : 'inspector-collapsed'}">
	<!-- Editorial Precision Header -->
	<header
		class="workbench-header nomad-panel flex flex-wrap items-center justify-between gap-4 rounded-2xl px-4 py-3 sm:px-5"
	>
		<div class="flex items-center gap-3">
			<div class="flex size-9 items-center justify-center rounded-xl bg-[#141413] text-white">
				<Compass class="size-5" />
			</div>
			<div>
				<h1 class="text-sm font-bold tracking-tight text-[#141413] sm:text-base">
					Nomad — Cultural & Architectural Concierge
				</h1>
				<div class="flex items-center gap-2 text-[11px] text-[#575551]">
					<span>Live Open-Meteo Weather • Wikipedia Dossiers • iTunes 30s Audio • Frankfurter FX</span>
				</div>
			</div>
		</div>

		<div class="flex flex-wrap items-center gap-2">
			<!-- Engine Mode Selector -->
			<div class="flex items-center gap-1.5 rounded-xl border border-[#E4E0D8] bg-[#F6F5F2] px-3 py-1.5">
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

			<!-- Quick Destination & Protocol Scenarios -->
			<div class="flex flex-wrap items-center gap-1.5" role="group" aria-label="Preset Scenarios">
				{#each SCENARIO_PRESETS as preset (preset.id)}
					<button
						type="button"
						disabled={workbench.isStreaming}
						onclick={() => runScenarioPrompt(resolvePresetPrompt(preset))}
						class="group inline-flex items-center gap-1 rounded-xl border border-[#E4E0D8] bg-white px-3 py-1.5 text-xs font-semibold text-[#141413] transition-colors hover:border-[#C84B31] hover:bg-[#FBEEE9]/40 disabled:opacity-40"
					>
						<span>{preset.label}</span>
						<ChevronRight class="size-3 text-[#78756F] transition-transform group-hover:translate-x-0.5 group-hover:text-[#C84B31]" />
					</button>
				{/each}
			</div>

			<!-- Wire Telemetry Toggle -->
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
	</header>

	<!-- Pane 1: Left Trip Dossier & Protocol Rail -->
	<ClusterStatePanel
		clusterState={workbench.clusterState}
		isStreaming={workbench.isStreaming}
		onTriggerSkill={runScenarioPrompt}
	/>

	<!-- Pane 2: Center Concierge Conversation & Modular AG-UI Component Kit -->
	<section
		class="workbench-chat nomad-panel flex h-full min-h-0 flex-col justify-between overflow-hidden rounded-2xl"
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
