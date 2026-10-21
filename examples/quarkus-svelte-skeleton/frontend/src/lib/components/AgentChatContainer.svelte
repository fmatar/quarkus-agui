<script>
	import { Activity, Cpu, Layers, Radio, Server } from '@lucide/svelte';
	import {
		SCENARIO_PRESETS,
		createInitialWorkbenchState,
		parseSseChunk,
		reduceAgUiEvent
	} from '$lib/agui-client.js';
	import MessageHistory from './MessageHistory.svelte';
	import StreamRenderer from './StreamRenderer.svelte';
	import InputBar from './InputBar.svelte';

	let workbench = $state(createInitialWorkbenchState('thread-opspilot-1'));
	let abortController = $state(null);

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
			const response = await fetch('http://localhost:8080/api/agent/run', {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({
					threadId: workbench.threadId,
					prompt: promptText,
					state: {},
					mode: 'deterministic'
				}),
				signal: abortController.signal
			});

			if (!response.body) return;
			const reader = response.body.getReader();
			const decoder = new TextDecoder();
			let buffer = '';

			while (true) {
				const { value, done } = await reader.read();
				if (done) break;
				buffer += decoder.decode(value, { stream: true });
				const parsed = parseSseChunk(buffer);
				buffer = parsed.remainder;
				for (const evt of parsed.events) {
					workbench = reduceAgUiEvent(workbench, evt);
				}
			}
		} catch (err) {
			workbench = {
				...workbench,
				isStreaming: false,
				statusText: err?.name === 'AbortError' ? 'Stream cancelled by operator' : 'Connection idle'
			};
		}
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
	</header>

	<!-- Pane 1: MCP & A2A Topology Sidebar -->
	<aside
		class="workbench-sidebar workbench-pane flex flex-col gap-3 rounded-xl border border-slate-800 bg-slate-900/80 p-4"
	>
		<div class="flex items-center gap-2 text-xs font-semibold tracking-wider text-cyan-400 uppercase">
			<Server class="size-4" />
			<span>MCP Server (:8081)</span>
		</div>
		<ul class="space-y-1.5 text-xs text-slate-300">
			<li class="rounded bg-slate-800/80 px-2.5 py-1.5 font-mono">inspectClusterHealth</li>
			<li class="rounded bg-slate-800/80 px-2.5 py-1.5 font-mono">queryServiceLogs</li>
			<li class="rounded bg-slate-800/80 px-2.5 py-1.5 font-mono">scaleDeployment</li>
		</ul>

		<div class="mt-3 flex items-center gap-2 text-xs font-semibold tracking-wider text-purple-400 uppercase">
			<Layers class="size-4" />
			<span>A2A Specialist (:8082)</span>
		</div>
		<ul class="space-y-1.5 text-xs text-slate-300">
			<li class="rounded bg-slate-800/80 px-2.5 py-1.5 font-mono">audit-clean-architecture</li>
			<li class="rounded bg-slate-800/80 px-2.5 py-1.5 font-mono">assess-security-posture</li>
			<li class="rounded bg-slate-800/80 px-2.5 py-1.5 font-mono">plan-incident-remediation</li>
		</ul>
	</aside>

	<!-- Pane 2: Center AG-UI Agent Chat -->
	<section
		class="workbench-chat workbench-pane flex flex-col rounded-xl border border-slate-800 bg-slate-900/80"
	>
		<MessageHistory messages={workbench.messages} />
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
	<aside
		class="workbench-inspector workbench-pane flex flex-col rounded-xl border border-slate-800 bg-slate-900/80 p-4"
	>
		<div class="mb-3 flex items-center justify-between">
			<div class="flex items-center gap-2 text-xs font-semibold tracking-wider text-emerald-400 uppercase">
				<Activity class="size-4" />
				<span>AG-UI SSE Wire Inspector</span>
			</div>
			<span class="rounded bg-slate-800 px-2 py-0.5 font-mono text-xs text-slate-300">
				{workbench.events.length} frames
			</span>
		</div>

		<div class="scrollable-region flex-1 space-y-2">
			{#each workbench.events as evt (evt.id)}
				<div class="rounded-lg border border-slate-800 bg-slate-950/90 p-2.5 font-mono text-xs">
					<div class="flex items-center justify-between text-indigo-300">
						<span class="font-bold">{evt.type}</span>
						<span class="text-slate-500">{evt.id}</span>
					</div>
					<pre class="mt-1 overflow-x-auto text-[11px] text-slate-400">{JSON.stringify(evt.payload)}</pre>
				</div>
			{/each}
		</div>
	</aside>
</main>
