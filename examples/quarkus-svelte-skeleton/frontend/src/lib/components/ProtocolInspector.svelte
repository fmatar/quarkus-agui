<script>
	import { ChevronDown, ChevronRight, Terminal, Trash2, X, Zap } from '@lucide/svelte';
	import { filterInspectorEvents } from '$lib/agui-client.js';
	import ProtocolCallBadge from './agui/ProtocolCallBadge.svelte';

	let { events = [], isOpen = true, onClose, onClear } = $props();
	let activeFilter = $state('ALL');
	let expandedEventId = $state(null);

	let visibleEvents = $derived(filterInspectorEvents(events, activeFilter));
	const FILTERS = ['ALL', 'AG-UI', 'MCP', 'A2A'];

	function toggleExpand(id) {
		expandedEventId = expandedEventId === id ? null : id;
	}

	function getEventBadgeStyle(type) {
		if (type?.startsWith('STEP_')) {
			return 'badge-a2a';
		}
		if (type?.startsWith('TOOL_')) {
			return 'badge-mcp';
		}
		if (type?.includes('RUN_')) {
			return 'badge-hitl';
		}
		return 'badge-local';
	}
</script>

{#if isOpen}
	<aside
		class="workbench-inspector nomad-panel flex flex-col justify-between overflow-hidden rounded-2xl p-4 h-full min-h-0"
	>
		<div class="flex flex-col h-full min-h-0">
			<div class="flex items-center justify-between border-b border-[#E4E0D8] pb-3 mb-3 shrink-0">
				<div class="flex items-center gap-2">
					<div class="flex size-7 items-center justify-center rounded-lg bg-[#EFECE6] text-[#141413]">
						<Terminal class="size-4" />
					</div>
					<h2 class="text-sm font-bold tracking-tight text-[#141413]">Wire Telemetry</h2>
				</div>

				<div class="flex items-center gap-1">
					{#if events.length > 0}
						<button
							type="button"
							aria-label="Clear protocol inspector events"
							onclick={() => onClear?.()}
							class="rounded-lg p-1.5 text-[#575551] transition-colors hover:bg-[#EFECE6] hover:text-[#C84B31]"
							title="Clear Event Log"
						>
							<Trash2 class="size-3.5" />
						</button>
					{/if}
					<button
						type="button"
						aria-label="Close protocol inspector"
						onclick={() => onClose?.()}
						class="rounded-lg p-1.5 text-[#575551] transition-colors hover:bg-[#EFECE6] hover:text-[#141413]"
						title="Close Wire Inspector"
					>
						<X class="size-3.5" />
					</button>
				</div>
			</div>

			<!-- Filter Strip -->
			<div class="mb-3 flex items-center justify-between gap-1.5 shrink-0" role="tablist" aria-label="Protocol Filter">
				<div class="flex items-center gap-1">
					{#each FILTERS as filter (filter)}
						<button
							type="button"
							role="tab"
							aria-selected={activeFilter === filter}
							onclick={() => (activeFilter = filter)}
							class="rounded-lg px-2.5 py-1 font-mono text-[11px] font-semibold transition-all {activeFilter === filter
								? 'bg-[#141413] text-white'
								: 'bg-[#F6F5F2] text-[#575551] hover:bg-[#EFECE6] hover:text-[#141413]'}"
						>
							{filter}
						</button>
					{/each}
				</div>
				<span class="rounded bg-[#F6F5F2] px-2 py-0.5 font-mono text-[10px] text-[#575551] border border-[#E4E0D8] tabular-nums">
					{visibleEvents.length} frames
				</span>
			</div>

			<!-- Event Stream List -->
			<div class="scrollable-region flex-1 min-h-0 space-y-1.5 pr-1">
				{#if visibleEvents.length === 0}
					<div class="flex h-40 flex-col items-center justify-center text-center text-[#575551] text-xs px-4">
						<Zap class="size-5 text-[#78756F] mb-2" />
						<p class="font-medium text-[#141413]">No SSE frames captured for {activeFilter}</p>
						<p class="text-[11px] text-[#575551] mt-1">
							Select a destination or submit a prompt to inspect live AG-UI, MCP, and A2A wire events.
						</p>
					</div>
				{/if}

				{#each visibleEvents as evt (evt.id)}
					<div class="overflow-hidden rounded-xl border border-[#E4E0D8] bg-[#FAF9F6]">
						<button
							type="button"
							onclick={() => toggleExpand(evt.id)}
							class="flex w-full items-center justify-between p-2.5 text-left font-mono text-xs transition-colors hover:bg-[#EFECE6]/60"
						>
							<div class="flex items-center gap-2 overflow-hidden">
								{#if expandedEventId === evt.id}
									<ChevronDown class="size-3 text-[#575551] shrink-0" />
								{:else}
									<ChevronRight class="size-3 text-[#575551] shrink-0" />
								{/if}
								<span class="rounded px-1.5 py-0.5 text-[10px] font-bold uppercase shrink-0 {getEventBadgeStyle(evt.type)}">
									{evt.type}
								</span>
								{#if evt.payload?.callType}
									<ProtocolCallBadge callType={evt.payload.callType} />
								{:else}
									<span class="truncate text-[11px] text-[#575551]">
										{evt.payload?.stepId ?? evt.payload?.tool ?? evt.payload?.phase ?? ''}
									</span>
								{/if}
							</div>
							<span class="font-mono text-[10px] text-[#78756F] shrink-0 tabular-nums">
								{evt.id?.slice(-6)}
							</span>
						</button>

						{#if expandedEventId === evt.id}
							<div class="border-t border-[#E4E0D8] bg-white p-2.5">
								<pre class="overflow-x-auto rounded bg-[#F6F5F2] p-2 text-[10px] text-[#141413] leading-relaxed font-mono"><code>{JSON.stringify(evt.payload, null, 2)}</code></pre>
							</div>
						{/if}
					</div>
				{/each}
			</div>
		</div>
	</aside>
{/if}
