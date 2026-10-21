<script>
	import { Bot, User, Wrench, ShieldCheck } from '@lucide/svelte';

	let { messages = [] } = $props();
</script>

<div class="scrollable-region flex-1 space-y-4 p-4" aria-live="polite">
	{#each messages as msg (msg.id)}
		<article
			class="rounded-xl border p-4 {msg.role === 'user'
				? 'ml-8 border-indigo-500/40 bg-indigo-950/60 text-indigo-100'
				: 'mr-8 border-slate-700/80 bg-slate-900/90 text-slate-100'}"
		>
			<header class="mb-2 flex items-center gap-2 text-xs font-semibold tracking-wider uppercase opacity-80">
				{#if msg.role === 'user'}
					<User class="size-4 text-indigo-400" />
					<span>SRE Operator</span>
				{:else}
					<Bot class="size-4 text-emerald-400" />
					<span>OpsPilot Orchestrator (:8080)</span>
				{/if}
			</header>

			<p class="text-sm leading-relaxed">{msg.content}</p>

			{#if msg.mcpCalls?.length}
				<div class="mt-3 flex flex-wrap gap-2">
					{#each msg.mcpCalls as call (call.callId ?? call.tool)}
						<span
							class="inline-flex items-center gap-1.5 rounded-full border border-cyan-500/40 bg-cyan-950/70 px-2.5 py-1 text-xs font-medium text-cyan-200"
						>
							<Wrench class="size-3.5 text-cyan-400" />
							MCP: {call.tool} ({call.status})
						</span>
					{/each}
				</div>
			{/if}

			{#if msg.a2aSteps?.length}
				<div class="mt-3 space-y-2">
					{#each msg.a2aSteps as step (step.stepId ?? step.skillId)}
						<div class="rounded-lg border border-purple-500/40 bg-purple-950/40 p-2.5 text-xs text-purple-100">
							<div class="flex items-center gap-1.5 font-semibold text-purple-300">
								<ShieldCheck class="size-4 text-purple-400" />
								<span>A2A Handoff: {step.agentName} &rarr; {step.skillId}</span>
							</div>
							<p class="mt-1 opacity-90">{step.summary}</p>
						</div>
					{/each}
				</div>
			{/if}
		</article>
	{/each}
</div>
