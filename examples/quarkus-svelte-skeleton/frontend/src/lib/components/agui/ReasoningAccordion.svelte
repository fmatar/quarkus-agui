<script>
	import { ChevronDown, ChevronRight, Sparkles } from '@lucide/svelte';

	let { reasoning = null, isStreaming = false } = $props();
	let isOpen = $state(false);

	let formattedDuration = $derived(
		reasoning?.durationMs ? `${(reasoning.durationMs / 1000).toFixed(1)}s` : '0.9s'
	);
</script>

{#if reasoning?.thought}
	<div class="rounded-lg border border-[#E4E0D8] bg-[#F6F5F2] text-xs">
		<button
			type="button"
			onclick={() => (isOpen = !isOpen)}
			aria-expanded={isOpen}
			class="flex w-full items-center justify-between px-3 py-2 text-left text-[#575551] transition-colors hover:text-[#141413]"
		>
			<span class="inline-flex items-center gap-2 font-medium">
				<Sparkles class="size-3.5 text-[#C84B31]" />
				<span>
					{isStreaming ? 'Synthesizing protocol plan...' : `Synthesized plan in ${formattedDuration}`}
				</span>
			</span>
			{#if isOpen}
				<ChevronDown class="size-3.5 shrink-0" />
			{:else}
				<ChevronRight class="size-3.5 shrink-0" />
			{/if}
		</button>

		{#if isOpen}
			<div class="border-t border-[#E4E0D8] px-3.5 py-2.5 text-xs leading-relaxed text-[#575551]">
				{reasoning.thought}
			</div>
		{/if}
	</div>
{/if}
