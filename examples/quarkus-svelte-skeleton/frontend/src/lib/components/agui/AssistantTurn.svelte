<script>
	import { Compass } from '@lucide/svelte';
	import ReasoningAccordion from './ReasoningAccordion.svelte';
	import ExecutionTimeline from './ExecutionTimeline.svelte';
	import StreamingMarkdownBody from './StreamingMarkdownBody.svelte';
	import MediaGallery from './MediaGallery.svelte';
	import StructuredResponseBlock from './StructuredResponseBlock.svelte';
	import HitlApprovalCard from './HitlApprovalCard.svelte';
	import TurnFooter from './TurnFooter.svelte';

	let {
		turn,
		isStreaming = false,
		statusText = '',
		pendingApproval = null,
		onSelectOption,
		onConfirmMultiSelect,
		onTriggerPrompt,
		onApproveHitl,
		onRejectHitl
	} = $props();
</script>

<article class="nomad-card turn-enter rounded-2xl p-4 sm:p-5 space-y-3.5">
	<header class="flex items-center justify-between gap-2 border-b border-[#E4E0D8] pb-2.5">
		<div class="flex items-center gap-2">
			<div class="flex size-7 items-center justify-center rounded-lg bg-[#141413] text-white">
				<Compass class="size-4" />
			</div>
			<span class="text-xs font-semibold text-[#141413]">Nomad Concierge Orchestrator</span>
			<span class="rounded bg-[#EFECE6] px-1.5 py-0.5 font-mono text-[10px] text-[#575551]">
				:8080
			</span>
		</div>

		{#if isStreaming}
			<div class="inline-flex items-center gap-1.5 font-mono text-[11px] font-medium text-[#C84B31]">
				<span class="size-1.5 rounded-full bg-[#C84B31] animate-pulse-dot"></span>
				<span>{statusText || 'Streaming AG-UI frames...'}</span>
			</div>
		{:else}
			<span class="font-mono text-[10px] text-[#78756F]">{turn.id}</span>
		{/if}
	</header>

	<ReasoningAccordion reasoning={turn.reasoning} {isStreaming} />

	<ExecutionTimeline timeline={turn.timeline} />

	<StreamingMarkdownBody content={turn.content} {isStreaming} />

	<MediaGallery media={turn.media} />

	<StructuredResponseBlock
		interaction={turn.interaction}
		disabled={isStreaming}
		onSelectOption={(opt) => onSelectOption?.(turn.id, opt)}
		onConfirmMultiSelect={(vals) => onConfirmMultiSelect?.(turn.id, vals)}
		onTriggerAction={(prompt) => onTriggerPrompt?.(prompt)}
	/>

	{#if pendingApproval}
		<HitlApprovalCard
			{pendingApproval}
			onApprove={onApproveHitl}
			onReject={onRejectHitl}
		/>
	{/if}

	{#if !isStreaming}
		<TurnFooter
			content={turn.content}
			sources={turn.sources}
			followUps={turn.followUps}
			disabled={isStreaming}
			onSelectFollowUp={(chip) => onTriggerPrompt?.(chip)}
		/>
	{/if}
</article>
