<script>
	import { LoaderCircle, Sparkles } from '@lucide/svelte';

	let { isStreaming = false, statusText = 'Idle', streamBuffer = '' } = $props();
</script>

{#if isStreaming || streamBuffer}
	<div
		class="mx-4 mb-3 rounded-xl border border-emerald-500/40 bg-emerald-950/30 p-3 text-sm text-emerald-100"
		role="status"
	>
		<div class=" flex items-center gap-2 text-xs font-semibold text-emerald-300">
			{#if isStreaming}
				<LoaderCircle class="size-4 animate-spin text-emerald-400" />
			{:else}
				<Sparkles class="size-4 text-emerald-400" />
			{/if}
			<span>{statusText}</span>
		</div>
		{#if streamBuffer}
			<p class="mt-2 font-mono text-xs leading-relaxed text-slate-100">{streamBuffer}</p>
		{/if}
	</div>
{/if}
