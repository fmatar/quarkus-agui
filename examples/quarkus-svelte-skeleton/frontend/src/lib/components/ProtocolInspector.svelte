<script>
	import { Activity, Trash2, Filter, Code2, Zap, X, ChevronDown, ChevronRight, Terminal } from '@lucide/svelte';
	import { filterInspectorEvents } from '$lib/agui-client.js';

	let { events = [], isOpen = true, onClose, onClear } = $props();
	let activeFilter = $state('ALL');
	let expandedEventId = $state(null);

	let visibleEvents = $derived(filterInspectorEvents(events, activeFilter));
	const FILTERS = ['ALL', 'AG-UI', 'MCP', 'A2A'];

	function toggleExpand(id) {
		expandedEventId = expandedEventId === id ? null : id;
	}

	function getEventTypeBadge(type) {
		if (type?.startsWith('STEP_')) {
			return 'text-purple-300 border-purple-500/30 bg-purple-950/40';
		}
		if (type?.startsWith('TOOL_')) {
			return 'text-cyan-300 border-cyan-500/30 bg-cyan-950/40';
		}
		if (type?.startsWith('STATE_')) {
			return 'text-emerald-300 border-emerald-500/30 bg-emerald-950/40';
		}
		if (type?.includes('RUN_')) {
			return 'text-amber-300 border-amber-500/30 bg-amber-950/40';
		}
		return 'text-slate-300 border-slate-700/50 bg-slate-900/60';
	}
</script>

{#if isOpen}
	<aside
		class="workbench-inspector cyber-card flex flex-col justify-between overflow-hidden rounded-2xl p-4 sm:p-5 h-full min-h-0"
	>
		<div class="flex flex-col h-full min-h-0">
			<!-- Header -->
			<div class="flex items-center justify-between border-b border-slate-800/80 pb-3 mb-3 shrink-0">
				<div class="flex items-center gap-2">
					<div class="rounded-lg bg-emerald-950/50 p-1.5 ring-1 ring-emerald-500/30">
						<Terminal class="size-4 text-emerald-400" />
					</div>
					<div>
						<h2 class="text-xs font-bold tracking-wider text-slate-200 uppercase">SSE Protocol Console</h2>
						<div class="font-mono text-[10px] text-slate-400">Stream Event Inspector</div>
					</div>
				</div>

				<div class="flex items-center gap-1.5">
					{#if events.length > 0}
						<button
							type="button"
							aria-label="Clear protocol inspector events"
							onclick={() => onClear?.()}
							class="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-800 hover:text-rose-400"
							title="Clear Event Log"
						>
							<Trash2 class="size-3.5" />
						</button>
					{/if}
					<button
						type="button"
						aria-label="Close protocol inspector"
						onclick={() => onClose?.()}
						class="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-800 hover:text-white"
						title="Hide Inspector"
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
								? 'bg-cyan-500/20 text-cyan-300 ring-1 ring-cyan-500/50 shadow-sm'
								: 'bg-slate-950/60 text-slate-400 hover:bg-slate-900 hover:text-slate-200'}"
						>
							{filter}
						</button>
					{/each}
				</div>
				<span class="rounded bg-slate-950 px-2 py-0.5 font-mono text-[10px] text-slate-400 ring-1 ring-slate-800">
					{visibleEvents.length} frames
				</span>
			</div>

			<!-- Event Stream List (Compact Accordion Logs) -->
			<div class="scrollable-region flex-1 min-h-0 space-y-1.5 pr-1">
				{#if visibleEvents.length === 0}
					<div class="flex h-40 flex-col items-center justify-center text-center text-slate-500 font-mono text-xs">
						<Zap class="size-5 text-slate-600 mb-2" />
						<span>No SSE frames captured</span>
					</div>
				{/if}

				{#each visibleEvents as evt (evt.id)}
					<div
						class="overflow-hidden rounded-xl border border-slate-900 bg-slate-950/70 transition-all hover:border-slate-800"
					>
						<button
							type="button"
							onclick={() => toggleExpand(evt.id)}
							class="flex w-full items-center justify-between p-2.5 text-left font-mono text-xs transition-colors hover:bg-slate-900/50"
						>
							<div class="flex items-center gap-2 overflow-hidden">
								{#if expandedEventId === evt.id}
									<ChevronDown class="size-3 text-slate-400 shrink-0" />
								{:else}
									<ChevronRight class="size-3 text-slate-400 shrink-0" />
								{/if}
								<span class="rounded border px-1.5 py-0.2 text-[10px] font-bold uppercase shrink-0 {getEventTypeBadge(evt.type)}">
									{evt.type}
								</span>
								<span class="truncate text-[11px] text-slate-400">
									{evt.payload?.stepId ?? evt.payload?.tool ?? evt.payload?.threadId ?? ''}
								</span>
							</div>
							<span class="font-mono text-[10px] text-slate-600 shrink-0">{evt.id?.slice(-8)}</span>
						</button>

						{#if expandedEventId === evt.id}
							<div class="border-t border-slate-900 bg-slate-950 p-2.5">
								<pre class="overflow-x-auto rounded bg-slate-900/90 p-2 text-[10px] text-slate-300 leading-relaxed font-mono"><code>{JSON.stringify(evt.payload, null, 2)}</code></pre>
							</div>
						{/if}
					</div>
				{/each}
			</div>
		</div>
	</aside>
{/if}
