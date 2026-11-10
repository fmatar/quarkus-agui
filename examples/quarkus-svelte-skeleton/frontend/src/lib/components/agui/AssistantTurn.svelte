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

	let hasCompanion = $derived(
		Boolean(turn.media?.length || turn.interaction || pendingApproval)
	);
</script>

<article class="nomad-card turn-enter turn-card-container rounded-2xl p-4 sm:p-5 space-y-3.5">
	<header class="flex items-center justify-between gap-2 border-b border-[#E4E0D8] pb-2.5">
		<div class="flex items-center gap-2">
			<div class="flex size-7 items-center justify-center rounded-lg bg-[#141413] text-white">
				<Compass class="size-4" />
			</div>
			<span class="text-xs font-semibold text-[#141413]">Nomad Concierge</span>
		</div>

		{#if isStreaming}
			<div class="inline-flex items-center gap-1.5 font-mono text-[11px] font-medium text-[#C84B31]">
				<span class="size-1.5 rounded-full bg-[#C84B31] animate-pulse-dot"></span>
				<span>{statusText || 'Curating dossier...'}</span>
			</div>
		{/if}
	</header>

	<ReasoningAccordion reasoning={turn.reasoning} {isStreaming} />

	<ExecutionTimeline timeline={turn.timeline} />

	<div class="turn-editorial-split {hasCompanion ? 'has-companion' : ''}">
		<div class="min-w-0 flex-1">
			<StreamingMarkdownBody content={turn.content} {isStreaming} />
		</div>

		{#if hasCompanion}
			<aside class="turn-companion-col space-y-3.5 min-w-0" aria-label="Destination Media & Interactive Controls">
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
			</aside>
		{/if}
	</div>

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
