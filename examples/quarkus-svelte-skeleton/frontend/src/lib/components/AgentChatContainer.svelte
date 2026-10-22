<script>
	import {
		Activity,
		Cpu,
		Layers,
		Radio,
		Server,
		ShieldAlert,
		Sparkles,
		Terminal,
		CheckCircle2,
		XCircle,
		ChevronRight,
		SlidersHorizontal,
		PanelRightClose,
		PanelRightOpen
	} from '@lucide/svelte';
	import {
		SCENARIO_PRESETS,
		createInitialWorkbenchState,
		parseSseChunk,
		reduceAgUiEvent,
		updateServiceReplicasInState
	} from '$lib/agui-client.js';
	import ClusterStatePanel from './ClusterStatePanel.svelte';
	import MessageHistory from './MessageHistory.svelte';
	import InputBar from './InputBar.svelte';
	import ProtocolInspector from './ProtocolInspector.svelte';

	const LLM_MODES = [
		{ value: 'ollama:llama3.2:latest', label: 'Llama 3.2 • Live Local Ollama' },
		{ value: 'ollama:qwen3.5:4b', label: 'Qwen 3.5 4B • Live Local Ollama' },
		{ value: 'ollama:ministral-3:8b', label: 'Ministral 8B • Live Local Ollama' },
		{ value: 'deterministic', label: 'Deterministic CI Engine (Fast Mock)' }
	];

	let workbench = $state(createInitialWorkbenchState('thread-opspilot-1'));
	let selectedMode = $state('ollama:llama3.2:latest');
	let abortController = $state(null);
	let isInspectorOpen = $state(true);

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
						? 'Stream interrupted by operator'
						: `Bridge error (${err?.message ?? 'connect failed to :8080'})`
			};
		}
	}

	async function submitHitlDecision(approved) {
		abortController = new AbortController();
		try {
			const response = await fetch('/api/agent/approve', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					threadId: workbench.threadId,
					approved,
					reason: approved
						? 'Operator authorized replica scale above threshold (>5)'
						: 'Operator rejected replica mutation'
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
		}
	}

	function handleAdjustReplicas(serviceName, delta) {
		const updated = updateServiceReplicasInState(workbench.clusterState, serviceName, delta);
		workbench = { ...workbench, clusterState: updated };
		const targetSvc = updated?.services?.find((s) => s.serviceName === serviceName);
		if (targetSvc) {
			runScenarioPrompt(`Scale deployment ${serviceName} to ${targetSvc.replicas} replicas`);
		}
	}

	function clearInspectorEvents() {
		workbench = { ...workbench, events: [] };
	}

	function cancelStream() {
		abortController?.abort();
	}
</script>

<main class="workbench-grid {isInspectorOpen ? '' : 'inspector-collapsed'}">
	<!-- High-Tech Command Header -->
	<header
		class="workbench-header cyber-card flex flex-wrap items-center justify-between gap-4 rounded-2xl px-5 py-3"
	>
		<div class="flex items-center gap-3.5">
			<div class="relative flex items-center justify-center rounded-xl bg-cyan-950/60 p-2.5 ring-1 ring-cyan-500/30">
				<Radio class="size-5 text-cyan-400" />
				<span class="absolute -top-1 -right-1 flex size-2.5">
					<span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-cyan-400 opacity-75"></span>
					<span class="relative inline-flex size-2.5 rounded-full bg-cyan-500"></span>
				</span>
			</div>
			<div>
				<div class="flex items-center gap-2">
					<span class="font-mono text-[10px] font-bold tracking-widest text-cyan-400 uppercase">
						Autonomous SRE Command
					</span>
					<span class="rounded bg-slate-800/80 px-1.5 py-0.5 font-mono text-[10px] text-slate-400">
						Tri-Protocol
					</span>
				</div>
				<h1 class="text-sm font-bold tracking-tight text-white sm:text-base">
					Quarkus AG-UI Workbench
				</h1>
			</div>
		</div>

		<!-- Protocol Status & Scenario Strip -->
		<div class="flex flex-wrap items-center gap-2.5">
			<!-- LLM Model Selector -->
			<div class="flex items-center gap-2 rounded-xl bg-slate-950/80 px-3 py-1.5 ring-1 ring-slate-800">
				<Cpu class="size-3.5 text-emerald-400" />
				<select
					id="agui-llm-mode"
					bind:value={selectedMode}
					disabled={workbench.isStreaming}
					class="bg-transparent font-mono text-xs font-medium text-emerald-300 focus:outline-none cursor-pointer"
				>
					{#each LLM_MODES as m (m.value)}
						<option value={m.value} class="bg-slate-900 text-slate-100">{m.label}</option>
					{/each}
				</select>
			</div>

			<!-- Quick Scenarios -->
			<div class="flex flex-wrap items-center gap-1.5" role="group" aria-label="Preset Scenarios">
				{#each SCENARIO_PRESETS as preset (preset.id)}
					<button
						type="button"
						disabled={workbench.isStreaming}
						onclick={() => runScenarioPrompt(preset.prompt)}
						class="group flex items-center gap-1.5 rounded-xl bg-slate-900/90 px-3 py-1.5 text-xs font-medium text-slate-300 ring-1 ring-slate-700/60 transition-all hover:bg-slate-800 hover:text-white hover:ring-cyan-500/50 disabled:opacity-40"
					>
						<span>{preset.label}</span>
						<ChevronRight class="size-3 text-slate-500 transition-transform group-hover:translate-x-0.5 group-hover:text-cyan-400" />
					</button>
				{/each}
			</div>

			<!-- Wire Inspector Toggle Button -->
			<button
				type="button"
				onclick={() => isInspectorOpen = !isInspectorOpen}
				class="flex items-center gap-1.5 rounded-xl px-3 py-1.5 font-mono text-xs font-semibold transition-all {isInspectorOpen ? 'bg-cyan-500/20 text-cyan-300 ring-1 ring-cyan-500/40' : 'bg-slate-900 text-slate-400 ring-1 ring-slate-800 hover:text-slate-200'}"
				title={isInspectorOpen ? 'Collapse Wire Inspector' : 'Expand Wire Inspector'}
			>
				<Terminal class="size-3.5" />
				<span class="hidden sm:inline">Wire</span>
				<span class="rounded bg-slate-950 px-1.5 py-0.2 text-[10px]">{workbench.events.length}</span>
			</button>
		</div>
	</header>

	<!-- Pane 1: Live Cluster Topology Rail (MCP :8081 & A2A :8082) -->
	<ClusterStatePanel
		clusterState={workbench.clusterState}
		isStreaming={workbench.isStreaming}
		onAdjustReplicas={handleAdjustReplicas}
		onTriggerSkill={runScenarioPrompt}
	/>

	<!-- Pane 2: Center AG-UI Conversational Feed (Strict Height Bounded) -->
	<section
		class="workbench-chat cyber-card flex flex-col justify-between overflow-hidden rounded-2xl h-full min-h-0"
	>
		<!-- Feed Header Sub-bar -->
		<div class="flex items-center justify-between border-b border-slate-800/80 bg-slate-950/40 px-5 py-2.5 shrink-0">
			<div class="flex items-center gap-2">
				<Terminal class="size-3.5 text-cyan-400" />
				<span class="font-mono text-xs font-semibold text-slate-300">OpsPilot Stream Feed</span>
			</div>
			<div class="flex items-center gap-2 font-mono text-[11px] text-slate-400">
				<span>Thread:</span>
				<span class="text-cyan-300">{workbench.threadId}</span>
			</div>
		</div>

		<!-- Chat Message Timeline with Auto-Scroll & In-Flight Stream Bubble -->
		<MessageHistory
			messages={workbench.messages}
			isStreaming={workbench.isStreaming}
			streamBuffer={workbench.streamBuffer}
			statusText={workbench.statusText}
		/>

		<!-- HITL Approval Barrier Notification (Pinned Floating Alert) -->
		{#if workbench.pendingApproval}
			<div
				class="mx-5 mb-3 rounded-xl border border-amber-500/40 bg-gradient-to-r from-amber-950/80 via-amber-900/60 to-slate-950/90 p-4 text-xs shadow-2xl backdrop-blur-md shrink-0"
				role="alert"
			>
				<div class="flex items-center gap-2.5 font-bold text-amber-300">
					<div class="rounded-lg bg-amber-500/20 p-1.5 ring-1 ring-amber-500/40">
						<ShieldAlert class="size-4 text-amber-400" />
					</div>
					<div>
						<div class="text-sm font-semibold tracking-wide">Human-in-the-Loop Interruption</div>
						<div class="font-mono text-[10px] font-normal text-amber-200/70">
							AG-UI Protocol • RUN_FINISHED (interrupted: true)
						</div>
					</div>
				</div>

				<p class="mt-2.5 text-xs sm:text-sm text-slate-200">
					Autonomous agent requested scaling
					<span class="font-mono font-semibold text-cyan-300">{workbench.pendingApproval.serviceName}</span>
					to
					<span class="font-mono font-semibold text-amber-300">{workbench.pendingApproval.replicas} replicas</span>.
					Safety guard threshold is &le;5 replicas.
				</p>

				<div class="mt-3 flex items-center gap-3">
					<button
						type="button"
						onclick={() => submitHitlDecision(true)}
						class="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 font-semibold text-white shadow-lg shadow-emerald-950/50 transition-all hover:bg-emerald-500 active:scale-95 text-xs"
					>
						<CheckCircle2 class="size-3.5" />
						<span>Authorize Scale ({workbench.pendingApproval.replicas} Replicas)</span>
					</button>
					<button
						type="button"
						onclick={() => submitHitlDecision(false)}
						class="inline-flex items-center gap-1.5 rounded-xl bg-slate-800 px-4 py-2 font-semibold text-slate-300 ring-1 ring-slate-700 transition-all hover:bg-rose-950/50 hover:text-rose-200 hover:ring-rose-500/50 active:scale-95 text-xs"
					>
						<XCircle class="size-3.5" />
						<span>Abort Mutation</span>
					</button>
				</div>
			</div>
		{/if}

		<!-- Fixed Sticky Input Controller -->
		<InputBar
			isStreaming={workbench.isStreaming}
			onSubmit={runScenarioPrompt}
			onCancel={cancelStream}
		/>
	</section>

	<!-- Pane 3: SSE Protocol & Wire Inspector Drawer -->
	{#if isInspectorOpen}
		<ProtocolInspector
			events={workbench.events}
			isOpen={isInspectorOpen}
			onClose={() => isInspectorOpen = false}
			onClear={clearInspectorEvents}
		/>
	{/if}
</main>
