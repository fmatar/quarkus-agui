<script>
	import { Check, ChevronDown, ChevronRight, Loader2, ShieldAlert } from '@lucide/svelte';
	import gsap from 'gsap';
	import ProtocolCallBadge from './ProtocolCallBadge.svelte';

	let { timeline = [] } = $props();
	let expandedId = $state(null);
	let isSpineOpen = $state(true);
	let spineContainerEl = $state(null);
	let lastAnimatedCount = 0;

	function toggleRow(id) {
		expandedId = expandedId === id ? null : id;
	}

	$effect(() => {
		const count = timeline?.length ?? 0;
		if (!spineContainerEl || count === 0 || count === lastAnimatedCount) {
			return;
		}
		lastAnimatedCount = count;
		const prefersReduced =
			typeof window !== 'undefined' &&
			window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
		if (prefersReduced) {
			return;
		}
		const rows = spineContainerEl.querySelectorAll('[data-spine-row]');
		const latest = rows[rows.length - 1];
		if (latest) {
			gsap.fromTo(
				latest,
				{ opacity: 0.85, y: 4 },
				{ opacity: 1, y: 0, duration: 0.24, ease: 'power3.out' }
			);
		}
	});
</script>

{#if timeline?.length}
	<div bind:this={spineContainerEl} class="rounded-xl border border-[#E4E0D8] bg-[#FAF9F6] p-3">
		<div class="flex items-center justify-between text-xs text-[#575551] {isSpineOpen ? 'mb-2' : ''}">
			<button
				type="button"
				onclick={() => (isSpineOpen = !isSpineOpen)}
				aria-expanded={isSpineOpen}
				class="inline-flex items-center gap-1.5 font-semibold text-[#141413] hover:text-[#C84B31] transition-colors"
			>
				{#if isSpineOpen}
					<ChevronDown class="size-3.5 text-[#575551]" />
				{:else}
					<ChevronRight class="size-3.5 text-[#575551]" />
				{/if}
				<span>Tri-Protocol Execution Spine</span>
			</button>
			<div class="flex items-center gap-2">
				<span class="font-mono text-[11px] tabular-nums">{timeline.length} steps</span>
				<button
					type="button"
					onclick={() => (isSpineOpen = !isSpineOpen)}
					class="rounded border border-[#E4E0D8] bg-white px-2 py-0.5 font-mono text-[10px] font-medium text-[#575551] hover:bg-[#EFECE6] hover:text-[#141413]"
				>
					{isSpineOpen ? 'Compact' : 'Expand'}
				</button>
			</div>
		</div>

		{#if isSpineOpen}
			<div class="grid grid-cols-1 gap-1.5 lg:grid-cols-2">
				{#each timeline as step, idx (`${step.id ?? step.title}-${idx}`)}
					{@const rowKey = `${step.id ?? step.title}-${idx}`}
					{@const isExpanded = expandedId === rowKey}
					<div
						data-spine-row
						class="rounded-lg border border-[#E4E0D8] bg-white {isExpanded ? 'lg:col-span-2' : ''}"
					>
						<button
							type="button"
							onclick={() => toggleRow(rowKey)}
							aria-expanded={isExpanded}
							class="flex w-full flex-wrap items-center justify-between gap-2 px-3 py-2 text-left text-xs transition-colors hover:bg-[#F6F5F2]/60"
						>
							<div class="flex min-w-0 flex-1 items-center gap-2">
								<ProtocolCallBadge callType={step.callType} showCode={false} />
								<span class="truncate font-mono text-xs font-medium text-[#141413]">{step.title}</span>
								{#if step.detail}
									<span class="hidden truncate text-xs text-[#575551] xl:inline">— {step.detail}</span>
								{/if}
							</div>

							<div class="flex items-center gap-2 shrink-0">
								{#if step.status === 'running'}
									<span class="inline-flex items-center gap-1 text-[11px] font-medium text-[#9A4A06]">
										<Loader2 class="size-3 animate-spin" />
										<span>Running</span>
									</span>
								{:else if step.status === 'awaiting_approval'}
									<span class="inline-flex items-center gap-1 text-[11px] font-medium text-[#C84B31]">
										<ShieldAlert class="size-3" />
										<span>Awaiting Approval</span>
									</span>
								{:else}
									<span class="inline-flex items-center gap-1 text-[11px] font-medium text-[#1F6E50]">
										<Check class="size-3" />
										<span>Verified</span>
									</span>
								{/if}
								{#if isExpanded}
									<ChevronDown class="size-3.5 text-[#575551]" />
								{:else}
									<ChevronRight class="size-3.5 text-[#575551]" />
								{/if}
							</div>
						</button>

						{#if isExpanded}
							<div class="border-t border-[#E4E0D8] bg-[#F6F5F2] px-3 py-2.5 text-[11px] text-[#575551]">
								<div class="mb-1 flex items-center justify-between font-mono">
									<span>Endpoint: {step.subtitle ?? 'nomad-mcp (:8081)'}</span>
									<span>Type: {step.callType}</span>
								</div>
								{#if step.detail}
									<p class="mt-1 text-xs text-[#141413]">{step.detail}</p>
								{/if}
								{#if step.result}
									<pre class="mt-2 overflow-x-auto rounded border border-[#E4E0D8] bg-white p-2 font-mono text-[10px] text-[#141413]"><code>{JSON.stringify(step.result, null, 2)}</code></pre>
								{/if}
							</div>
						{/if}
					</div>
				{/each}
			</div>
		{/if}
	</div>
{/if}
