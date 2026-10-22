<script>
	import {
		Bot,
		User,
		Wrench,
		ShieldCheck,
		ArrowRight,
		CheckCircle2,
		CornerDownRight,
		Sparkles
	} from '@lucide/svelte';
	import { renderMarkdownToHtml } from '$lib/markdown.js';

	let { messages = [], isStreaming = false, streamBuffer = '', statusText = '' } = $props();
	let scrollContainer = $state(null);

	// Auto-scroll to bottom on streaming deltas or new messages
	$effect(() => {
		if (messages.length || streamBuffer) {
			requestAnimationFrame(() => {
				if (scrollContainer) {
					scrollContainer.scrollTop = scrollContainer.scrollHeight;
				}
			});
		}
	});
</script>

<div
	bind:this={scrollContainer}
	class="scrollable-region flex-1 min-h-0 space-y-4 p-5"
	aria-live="polite"
>
	{#if messages.length === 0 && !isStreaming}
		<div class="flex h-full flex-col items-center justify-center text-center text-slate-500 py-12">
			<Sparkles class="size-8 text-cyan-500/40 mb-3" />
			<p class="text-sm font-medium text-slate-400">OpsPilot SRE Orchestrator Ready</p>
			<p class="text-xs text-slate-500 mt-1 max-w-sm">
				Choose a scenario from the header bar or submit an SRE prompt to trigger AG-UI streaming, MCP telemetry tools, and A2A specialist delegations.
			</p>
		</div>
	{/if}

	{#each messages as msg (msg.id)}
		<article
			class="relative overflow-hidden rounded-2xl border p-4.5 transition-all {msg.role === 'user'
				? 'ml-8 border-cyan-500/30 bg-gradient-to-br from-cyan-950/40 to-slate-900/90 text-cyan-50'
				: 'mr-8 border-slate-800 bg-slate-950/70 text-slate-200'}"
		>
			<!-- Role Header Badge -->
			<header class="mb-3 flex items-center justify-between">
				<div class="flex items-center gap-2">
					{#if msg.role === 'user'}
						<div class="flex items-center justify-center size-6 rounded-lg bg-cyan-500/20 text-cyan-400 ring-1 ring-cyan-500/40">
							<User class="size-3.5" />
						</div>
						<span class="font-mono text-xs font-bold text-cyan-300">SRE Operator</span>
					{:else}
						<div class="flex items-center justify-center size-6 rounded-lg bg-emerald-500/20 text-emerald-400 ring-1 ring-emerald-500/40">
							<Bot class="size-3.5" />
						</div>
						<span class="font-mono text-xs font-bold text-emerald-300">OpsPilot Orchestrator (:8080)</span>
					{/if}
				</div>
				<span class="font-mono text-[10px] text-slate-500">{msg.id}</span>
			</header>

			<!-- Content Body with Markdown Engine -->
			{#if msg.role === 'user'}
				<div class="text-sm leading-relaxed text-slate-200 whitespace-pre-wrap font-sans">
					{msg.content}
				</div>
			{:else}
				<div class="prose prose-invert max-w-none text-slate-200">
					{@html renderMarkdownToHtml(msg.content)}
				</div>
			{/if}

			<!-- Embedded MCP Tool Invocations -->
			{#if msg.mcpCalls?.length}
				<div class="mt-3.5 pt-3 border-t border-slate-800/80 flex flex-wrap gap-2">
					{#each msg.mcpCalls as call (call.callId ?? call.tool)}
						<div
							class="inline-flex items-center gap-2 rounded-xl border border-cyan-500/30 bg-cyan-950/40 px-3 py-1.5 font-mono text-xs text-cyan-200 shadow-sm"
						>
							<Wrench class="size-3.5 text-cyan-400" />
							<span class="font-semibold">{call.tool}</span>
							<span class="rounded bg-cyan-900/60 px-1.5 py-0.2 text-[10px] text-cyan-300">
								{call.status}
							</span>
						</div>
					{/each}
				</div>
			{/if}

			<!-- Embedded A2A Peer Delegations -->
			{#if msg.a2aSteps?.length}
				<div class="mt-3.5 pt-3 border-t border-slate-800/80 space-y-2.5">
					{#each msg.a2aSteps as step (step.stepId ?? step.skillId)}
						<div
							class="rounded-xl border border-purple-500/30 bg-purple-950/30 p-3 text-xs text-purple-100 shadow-sm"
						>
							<div class="flex items-center justify-between font-mono text-purple-300">
								<div class="flex items-center gap-2">
									<ShieldCheck class="size-4 text-purple-400" />
									<span class="font-bold">{step.agentName}</span>
									<ArrowRight class="size-3 text-purple-500" />
									<span class="text-purple-200">{step.skillId}</span>
								</div>
								<span class="rounded-full bg-purple-900/50 px-2 py-0.5 text-[10px] text-purple-300">
									A2A 1.4
								</span>
							</div>
							<p class="mt-2 text-xs text-slate-300 leading-relaxed font-sans">{step.summary}</p>
						</div>
					{/each}
				</div>
			{/if}
		</article>
	{/each}

	<!-- Active In-Flight Assistant Stream Message -->
	{#if isStreaming || streamBuffer}
		<article
			class="relative mr-8 overflow-hidden rounded-2xl border border-cyan-500/30 bg-slate-950/90 p-4.5 shadow-xl transition-all"
		>
			<header class="mb-3 flex items-center justify-between">
				<div class="flex items-center gap-2">
					<div class="flex items-center justify-center size-6 rounded-lg bg-cyan-500/20 text-cyan-400 ring-1 ring-cyan-500/40">
						<Bot class="size-3.5" />
					</div>
					<span class="font-mono text-xs font-bold text-cyan-300">OpsPilot (Streaming...)</span>
				</div>
				<div class="flex items-center gap-1.5 font-mono text-[10px] text-cyan-400">
					<span class="size-1.5 rounded-full bg-cyan-400 animate-ping"></span>
					<span>{statusText || 'GENERATING_TOKENS'}</span>
				</div>
			</header>

			{#if streamBuffer}
				<div class="prose prose-invert max-w-none text-slate-200">
					{@html renderMarkdownToHtml(streamBuffer)}
					<span class="inline-block size-2 rounded-full bg-cyan-400 animate-pulse ml-1 align-baseline"></span>
				</div>
			{:else}
				<div class="flex items-center gap-2 text-xs text-slate-400 font-mono italic">
					<span>Awaiting first token from model...</span>
				</div>
			{/if}
		</article>
	{/if}
</div>
