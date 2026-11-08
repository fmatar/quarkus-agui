<script>
	import { CheckCircle2, Minus, Plus, ShieldAlert, XCircle } from '@lucide/svelte';
	import ProtocolCallBadge from './ProtocolCallBadge.svelte';

	let { pendingApproval = null, onApprove, onReject } = $props();

	let customNights = $state(4);
	let customReplicas = $state(6);

	$effect(() => {
		if (pendingApproval) {
			customNights = pendingApproval.nights ?? 4;
			customReplicas = pendingApproval.replicas ?? 6;
		}
	});

	let isItineraryHold = $derived(pendingApproval?.toolName === 'updateTripItinerary');
	let computedCostUsd = $derived(Math.round((customNights || 4) * 460));
</script>

{#if pendingApproval}
	<div
		class="rounded-xl border border-[#C84B31]/40 bg-[#FBEEE9] p-4 text-xs text-[#141413] shadow-sm"
		role="alert"
	>
		<div class="flex flex-wrap items-center justify-between gap-2">
			<div class="flex items-center gap-2">
				<ShieldAlert class="size-4 text-[#C84B31]" />
				<span class="text-sm font-semibold text-[#141413]">
					Human-in-the-Loop Authorization Gate
				</span>
			</div>
			<ProtocolCallBadge callType="hitl:approval" showCode={true} />
		</div>

		{#if isItineraryHold}
			<p class="mt-2 text-xs leading-relaxed text-[#575551]">
				Nomad Concierge prepared a high-value sanctuary hold for
				<strong class="text-[#141413]">{pendingApproval.stopTitle ?? 'Hoshinoya Arashiyama'}</strong>
				in <strong class="text-[#141413]">{pendingApproval.city ?? 'Kyoto'}</strong>. Reservations exceeding
				<span class="font-mono font-semibold text-[#141413]">$1,500 USD</span> require explicit traveler confirmation.
			</p>

			<div class="mt-3 flex flex-wrap items-center gap-4 rounded-lg border border-[#E4E0D8] bg-white px-3.5 py-2.5">
				<div class="flex items-center gap-2">
					<span class="text-xs font-medium text-[#575551]">Nights:</span>
					<button
						type="button"
						aria-label="Decrease nights"
						onclick={() => (customNights = Math.max(1, customNights - 1))}
						class="flex size-6 items-center justify-center rounded border border-[#E4E0D8] bg-[#F6F5F2] text-[#141413] hover:bg-[#EFECE6]"
					>
						<Minus class="size-3" />
					</button>
					<span class="w-5 text-center font-mono text-xs font-bold tabular-nums">{customNights}</span>
					<button
						type="button"
						aria-label="Increase nights"
						onclick={() => (customNights = Math.min(14, customNights + 1))}
						class="flex size-6 items-center justify-center rounded border border-[#E4E0D8] bg-[#F6F5F2] text-[#141413] hover:bg-[#EFECE6]"
					>
						<Plus class="size-3" />
					</button>
				</div>

				<div class="font-mono text-xs text-[#141413] tabular-nums">
					Estimated Total: <strong>${computedCostUsd.toLocaleString()} USD</strong>
				</div>
			</div>
		{:else}
			<p class="mt-2 text-xs leading-relaxed text-[#575551]">
				Agent requested scaling
				<strong class="font-mono text-[#141413]">{pendingApproval.serviceName}</strong> to
				<strong class="font-mono text-[#141413]">{customReplicas} replicas</strong> (safety policy threshold &le; 5).
			</p>
		{/if}

		<div class="mt-3.5 flex flex-wrap items-center gap-2.5">
			<button
				type="button"
				onclick={() =>
					onApprove?.(
						isItineraryHold
							? {
									city: pendingApproval.city ?? 'Kyoto',
									stopTitle: pendingApproval.stopTitle ?? 'Hoshinoya Arashiyama Pavilion',
									nights: customNights,
									costUsd: computedCostUsd
								}
							: {
									serviceName: pendingApproval.serviceName,
									replicas: customReplicas
								}
					)}
				class="inline-flex items-center gap-1.5 rounded-lg bg-[#1F6E50] px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-[#18583F]"
			>
				<CheckCircle2 class="size-3.5" />
				<span>
					{isItineraryHold
						? `Authorize Sanctuary Hold ($${computedCostUsd} USD)`
						: `Authorize Scale (${customReplicas} Replicas)`}
				</span>
			</button>

			<button
				type="button"
				onclick={() => onReject?.()}
				class="inline-flex items-center gap-1.5 rounded-lg border border-[#CFCAC0] bg-white px-3.5 py-2 text-xs font-semibold text-[#141413] transition-colors hover:bg-[#EFECE6]"
			>
				<XCircle class="size-3.5 text-[#C84B31]" />
				<span>Decline Hold</span>
			</button>
		</div>
	</div>
{/if}
