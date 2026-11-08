<script>
	import { ArrowRight, Check, Copy, ExternalLink, RotateCcw } from '@lucide/svelte';
	import ProtocolCallBadge from './ProtocolCallBadge.svelte';

	let {
		content = '',
		sources = [],
		followUps = [],
		disabled = false,
		onSelectFollowUp,
		onRegenerate
	} = $props();

	let copied = $state(false);

	async function copyTurnMarkdown() {
		if (!content) return;
		try {
			await navigator.clipboard?.writeText(content);
			copied = true;
			setTimeout(() => (copied = false), 1600);
		} catch {
			// clipboard unavailable in headless environments
		}
	}
</script>

<footer class="mt-3 border-t border-[#E4E0D8] pt-3 space-y-2.5">
	{#if sources?.length}
		<div class="flex flex-wrap items-center gap-1.5">
			<span class="text-[11px] font-medium text-[#575551] mr-1">Verified Sources:</span>
			{#each sources as src (src.title)}
				<a
					href={src.url}
					target="_blank"
					rel="noreferrer"
					class="inline-flex items-center gap-1.5 rounded-md border border-[#E4E0D8] bg-[#F6F5F2] px-2 py-0.5 text-[11px] font-medium text-[#141413] transition-colors hover:border-[#CFCAC0] hover:bg-[#EFECE6]"
				>
					<span>{src.title}</span>
					<ExternalLink class="size-2.5 text-[#575551]" />
				</a>
			{/each}
		</div>
	{/if}

	<div class="flex flex-wrap items-center justify-between gap-2">
		<div class="flex flex-wrap items-center gap-1.5">
			{#each followUps ?? [] as chip (chip)}
				<button
					type="button"
					disabled={disabled}
					onclick={() => onSelectFollowUp?.(chip)}
					class="inline-flex items-center gap-1 rounded-full border border-[#E4E0D8] bg-white px-2.5 py-1 text-[11px] font-medium text-[#141413] transition-colors hover:border-[#C84B31] hover:bg-[#FBEEE9]/50 disabled:opacity-40"
				>
					<span class="truncate max-w-[260px]">{chip}</span>
					<ArrowRight class="size-3 text-[#C84B31] shrink-0" />
				</button>
			{/each}
		</div>

		<div class="flex items-center gap-1.5 shrink-0">
			<button
				type="button"
				onclick={copyTurnMarkdown}
				aria-label="Copy response markdown"
				class="inline-flex items-center gap-1 rounded-md border border-[#E4E0D8] bg-white px-2 py-1 text-[11px] font-medium text-[#575551] transition-colors hover:bg-[#F6F5F2] hover:text-[#141413]"
			>
				{#if copied}
					<Check class="size-3 text-[#1F6E50]" />
					<span>Copied</span>
				{:else}
					<Copy class="size-3" />
					<span>Copy</span>
				{/if}
			</button>

			{#if onRegenerate}
				<button
					type="button"
					disabled={disabled}
					onclick={() => onRegenerate?.()}
					aria-label="Re-run turn"
					class="inline-flex items-center gap-1 rounded-md border border-[#E4E0D8] bg-white px-2 py-1 text-[11px] font-medium text-[#575551] transition-colors hover:bg-[#F6F5F2] hover:text-[#141413] disabled:opacity-40"
				>
					<RotateCcw class="size-3" />
					<span>Refresh</span>
				</button>
			{/if}
		</div>
	</div>
</footer>
