<script>
	import { CheckCircle2, Cpu, Radio, ShieldAlert, XCircle } from '@lucide/svelte';
	import {
		SCENARIO_PRESETS,
		createInitialWorkbenchState,
		parseSseChunk,
		reduceAgUiEvent,
		updateServiceReplicasInState
	} from '$lib/agui-client.js';
	import ClusterStatePanel from './ClusterStatePanel.svelte';
	import MessageHistory from './MessageHistory.svelte';
	import StreamRenderer from './StreamRenderer.svelte';
	import InputBar from './InputBar.svelte';
	import ProtocolInspector from './ProtocolInspector.svelte';

	const LLM_MODES = [
		{ value: 'ollama:llama3.2:latest', label: 'Ollama • llama3.2:latest (Live LLM)' },
		{ value: 'ollama:qwen3.5:4b', label: 'Ollama • qwen3.5:4b (Live LLM)' },
		{ value: 'ollama:ministral-3:8b', label: 'Ollama • ministral-3:8b (Live LLM)' },
		{ value: 'ollama:qwen3:8b', label: 'Ollama • qwen3:8b (Live LLM)' },
		{ value: 'deterministic', label: 'Deterministic (Fast Mock)' }
	];

	let workbench = $state(createInitialWorkbenchState('thread-opspilot-1'));
	let selectedMode = $state('ollama:llama3.2:latest');
	let abortController = $state(null);

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
						? 'Stream cancelled by operator'
						: `Connection error (${err?.message ?? 'check backend :8080'})`
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
					reason: approved ? 'Approved by SRE Operator in Workbench' : 'Rejected by SRE Operator'
				}),
				signal: abortController.signal
			});
			await consumeSseResponse(response);
		} catch (err) {
			workbench = {
				...workbench,
				isStreaming: false,
				statusText: `HITL error (${err?.message ?? 'failed'})`
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

<main class="workbench-grid">
	<header
		class="workbench-header flex flex-wrap items-center justify-between gap-4 rounded-xl border border-slate-800 bg-slate-900/90 px-4 py-3"
	>
		<div class="flex items-center gap-3">
			<Radio class="size-5 text-indigo-400" />
			<div>
				<h1 class="text-base font-bold text-slate-100">
					Quarkus AG-UI Tri-Protocol Workbench (AG-UI + MCP + A2A)
				</h1>
				<p class="text-xs text-slate-400">
					Java 25 &bull; Quarkus 3.40.1.1 &bull; Svelte 5 Runes &bull; Skeleton UI &bull; @lucide/svelte
				</p>
			</div>
		</div>

		<div class="flex flex-wrap items-center gap-3">
			<div class="flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1">
				<Cpu class="size-3.5 text-emerald-400" />
				<label for="agui-llm-mode" class="sr-only">LLM Mode</label>
				<select
					id="agui-llm-mode"
					bind:value={selectedMode}
					disabled={workbench.isStreaming}
					class="bg-transparent text-xs font-medium text-emerald-300 focus:outline-none"
				>
					{#each LLM_MODES as m (m.value)}
						<option value={m.value} class="bg-slate-900 text-slate-100">{m.label}</option>
					{/each}
				</select>
			</div>

			<div class="scenario-bar" role="group" aria-label="1-Click Tri-Protocol Scenarios">
				{#each SCENARIO_PRESETS as preset (preset.id)}
					<button
						type="button"
						disabled={workbench.isStreaming}
						onclick={() => runScenarioPrompt(preset.prompt)}
						class="rounded-lg border border-slate-700 bg-slate-800/90 px-3 py-1.5 text-xs font-medium text-slate-200 hover:border-indigo-500 hover:bg-slate-800 disabled:opacity-50"
					>
						{preset.label}
					</button>
				{/each}
			</div>
		</div>
	</header>

	<!-- Pane 1: MCP & A2A Topology + Live STATE_SNAPSHOT / STATE_DELTA Sidebar -->
	<ClusterStatePanel
		clusterState={workbench.clusterState}
		isStreaming={workbench.isStreaming}
		onAdjustReplicas={handleAdjustReplicas}
		onTriggerSkill={runScenarioPrompt}
	/>

	<!-- Pane 2: Center AG-UI Agent Chat -->
	<section
		class="workbench-chat workbench-pane flex flex-col rounded-xl border border-slate-800 bg-slate-900/80"
	>
		<MessageHistory messages={workbench.messages} />

		{#if workbench.pendingApproval}
			<div
				class="mx-4 mb-3 rounded-xl border border-amber-500/60 bg-amber-950/50 p-3.5 text-xs text-amber-100"
				role="alert"
			>
				<div class="flex items-center gap-2 font-bold text-amber-300">
					<ShieldAlert class="size-4" />
					<span>Human-in-the-Loop Approval Required (AG-UI RUN_FINISHED: interrupted)</span>
				</div>
				<p class="mt-1.5 text-amber-100/90">
					OpsPilot requested MCP tool <code class="font-mono text-amber-200">{workbench.pendingApproval.toolName}</code>
					on <code class="font-mono text-amber-200">{workbench.pendingApproval.serviceName}</code>
					&rarr; <strong>{workbench.pendingApproval.replicas} replicas</strong> (&gt;5 threshold).
				</p>
				<div class="mt-3 flex items-center gap-2">
					<button
						type="button"
						onclick={() => submitHitlDecision(true)}
						class="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 font-semibold text-white hover:bg-emerald-500"
					>
						<CheckCircle2 class="size-3.5" />
						<span>Approve Scale ({workbench.pendingApproval.replicas} Replicas)</span>
					</button>
					<button
						type="button"
						onclick={() => submitHitlDecision(false)}
						class="inline-flex items-center gap-1.5 rounded-lg bg-rose-700 px-3 py-1.5 font-semibold text-white hover:bg-rose-600"
					>
						<XCircle class="size-3.5" />
						<span>Reject</span>
					</button>
				</div>
			</div>
		{/if}

		<StreamRenderer
			isStreaming={workbench.isStreaming}
			statusText={workbench.statusText}
			streamBuffer={workbench.streamBuffer}
		/>
		<InputBar
			isStreaming={workbench.isStreaming}
			onSubmit={runScenarioPrompt}
			onCancel={cancelStream}
		/>
	</section>

	<!-- Pane 3: Live AG-UI SSE Protocol Inspector -->
	<ProtocolInspector events={workbench.events} onClear={clearInspectorEvents} />
</main>
