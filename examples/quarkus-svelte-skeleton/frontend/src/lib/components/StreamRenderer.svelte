<script>
	import { Loader2, Sparkles, Terminal } from '@lucide/svelte';

	let { isStreaming = false, statusText = 'Idle', streamBuffer = '' } = $props();
</script>

{#if isStreaming || streamBuffer}
	<div
		class="mx-5 mb-3 rounded-xl border border-cyan-500/30 bg-slate-950/80 p-3.5 shadow-lg backdrop-blur-md"
		role="status"
	>
		<!-- Status Header -->
		<div class="flex items-center justify-between font-mono text-xs">
			<div class="flex items-center gap-2">
				{#if isStreaming}
					<Loader2 class="size-3.5 animate-spin text-cyan-400" />
					<span class="font-bold text-cyan-300">STREAMING ACTIVE</span>
				{:else}
					<Sparkles class="size-3.5 text-emerald-400" />
					<span class="font-bold text-emerald-300">STREAM CONCLUDED</span>
				{/if}
			</div>
			<span class="text-[11px] text-slate-400">{statusText}</span>
		</div>

		<!-- Token Buffer Output -->
		{#if streamBuffer}
			<div class="mt-2.5 rounded-lg border border-slate-900 bg-slate-950/90 p-2.5 font-mono text-xs text-slate-200 leading-relaxed whitespace-pre-wrap">
				{streamBuffer}
			</div>
		{/if}
	</div>
{/if}
