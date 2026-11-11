<script>
	import { User } from '@lucide/svelte';
	import AssistantTurn from './agui/AssistantTurn.svelte';

	let {
		messages = [],
		isStreaming = false,
		streamBuffer = '',
		statusText = '',
		activeTurn = null,
		pendingApproval = null,
		onSelectOption,
		onConfirmMultiSelect,
		onTriggerPrompt,
		onApproveHitl,
		onRejectHitl
	} = $props();

	let scrollContainer;

	$effect(() => {
		if (
			messages.length ||
			streamBuffer ||
			activeTurn?.timeline?.length ||
			activeTurn?.activities?.length ||
			pendingApproval
		) {
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
	class="scrollable-region flex-1 min-h-0 p-4 sm:p-5"
	aria-live="polite"
>
	<div class="mx-auto w-full max-w-[1440px] space-y-4">
		{#each messages as msg, idx (`${msg.id ?? 'msg'}-${idx}`)}
			{#if msg.role === 'user'}
				<article
					class="ml-auto max-w-2xl rounded-2xl border border-[#E4E0D8] bg-[#EFECE6] px-4 py-3 text-[#141413]"
				>
					<div class="mb-1 flex items-center justify-between text-[11px] text-[#575551]">
						<span class="inline-flex items-center gap-1.5 font-semibold text-[#141413]">
							<User class="size-3.5 text-[#C84B31]" />
							<span>You</span>
						</span>
					</div>
					<div class="text-sm leading-relaxed whitespace-pre-wrap">{msg.content}</div>
				</article>
			{:else}
				<AssistantTurn
					turn={msg}
					isStreaming={false}
					onSelectOption={onSelectOption}
					onConfirmMultiSelect={onConfirmMultiSelect}
					onTriggerPrompt={onTriggerPrompt}
				/>
			{/if}
		{/each}

		{#if isStreaming || streamBuffer || pendingApproval}
			<AssistantTurn
				turn={{
					id: 'turn-streaming',
					content: streamBuffer,
					reasoning: activeTurn?.reasoning,
					timeline: activeTurn?.timeline ?? [],
					activities: activeTurn?.activities ?? [],
					media: activeTurn?.media ?? [],
					interaction: activeTurn?.interaction ?? null,
					sources: activeTurn?.sources ?? [],
					followUps: activeTurn?.followUps ?? []
				}}
				{isStreaming}
				{statusText}
				{pendingApproval}
				onSelectOption={onSelectOption}
				onConfirmMultiSelect={onConfirmMultiSelect}
				onTriggerPrompt={onTriggerPrompt}
				onApproveHitl={onApproveHitl}
				onRejectHitl={onRejectHitl}
			/>
		{/if}
	</div>
</div>
