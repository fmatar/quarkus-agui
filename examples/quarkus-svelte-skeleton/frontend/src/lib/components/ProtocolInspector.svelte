<script>
	import { Activity, Trash2 } from '@lucide/svelte';
	import { filterInspectorEvents } from '$lib/agui-client.js';

	let { events = [], onClear } = $props();
	let activeFilter = $state('ALL');

	let visibleEvents = $derived(filterInspectorEvents(events, activeFilter));
	const FILTERS = ['ALL', 'AG-UI', 'MCP', 'A2A'];
</script>

<aside
	class="workbench-inspector workbench-pane flex flex-col rounded-xl border border-slate-800 bg-slate-900/80 p-4"
>
	<div class="mb-2 flex items-center justify-between">
		<div class="flex items-center gap-2 text-xs font-semibold tracking-wider text-emerald-400 uppercase">
			<Activity class="size-4" />
			<span>AG-UI SSE Wire Inspector</span>
		</div>
		<div class="flex items-center gap-1.5">
			<span class="rounded bg-slate-800 px-2 py-0.5 font-mono text-xs text-slate-300">
				{visibleEvents.length} frames
			</span>
			{#if events.length > 0}
				<button
					type="button"
					aria-label="Clear protocol inspector events"
					onclick={() => onClear?.()}
					class="rounded border border-slate-700 bg-slate-800 p-1 text-slate-400 hover:text-slate-100"
				>
					<Trash2 class="size-3.5" />
				</button>
			{/if}
		</div>
	</div>

	<div class="mb-3 flex items-center gap-1" role="tablist" aria-label="Protocol Event Filter">
		{#each FILTERS as filter (filter)}
			<button
				type="button"
				role="tab"
				aria-selected={activeFilter === filter}
				onclick={() => (activeFilter = filter)}
				class="rounded px-2 py-1 font-mono text-[11px] font-medium {activeFilter === filter
					? 'bg-indigo-600 text-white'
					: 'bg-slate-800/80 text-slate-400 hover:text-slate-200'}"
			>
				{filter}
			</button>
		{/each}
	</div>

	<div class="scrollable-region flex-1 space-y-2">
		{#each visibleEvents as evt (evt.id)}
			<div class="rounded-lg border border-slate-800 bg-slate-950/90 p-2.5 font-mono text-xs">
				<div class="flex items-center justify-between text-indigo-300">
					<span class="font-bold">{evt.type}</span>
					<span class="text-slate-500">{evt.id}</span>
				</div>
				<pre class="mt-1 overflow-x-auto text-[11px] text-slate-400">{JSON.stringify(evt.payload)}</pre>
			</div>
		{/each}
	</div>
</aside>
