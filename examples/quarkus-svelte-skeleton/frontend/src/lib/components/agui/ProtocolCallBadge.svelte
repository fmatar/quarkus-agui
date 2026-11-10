<script>
	import {
		BookOpen,
		Compass,
		Cpu,
		Database,
		FileCheck2,
		FileText,
		Hammer,
		LayoutGrid,
		Network,
		ShieldAlert,
		ShieldCheck,
		Sparkles,
		Wrench
	} from '@lucide/svelte';
	import { resolveProtocolCallMeta } from '$lib/agui-client.js';

	let { callType = 'mcp:tool', fallbackProtocol = 'MCP', showCode = false } = $props();

	let meta = $derived(resolveProtocolCallMeta(callType, fallbackProtocol));
</script>

<span
	class="inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-md px-2 py-0.5 text-[11px] font-medium tracking-tight {meta.badgeClass}"
	title="{meta.family} • {meta.callType}"
>
	{#if meta.iconName === 'Hammer'}
		<Hammer class="size-3 shrink-0" />
	{:else if meta.iconName === 'Database'}
		<Database class="size-3 shrink-0" />
	{:else if meta.iconName === 'FileText'}
		<FileText class="size-3 shrink-0" />
	{:else if meta.iconName === 'Cpu'}
		<Cpu class="size-3 shrink-0" />
	{:else if meta.iconName === 'Compass'}
		<Compass class="size-3 shrink-0" />
	{:else if meta.iconName === 'Network'}
		<Network class="size-3 shrink-0" />
	{:else if meta.iconName === 'FileCheck2'}
		<FileCheck2 class="size-3 shrink-0" />
	{:else if meta.iconName === 'ShieldAlert'}
		<ShieldAlert class="size-3 shrink-0" />
	{:else if meta.iconName === 'ShieldCheck'}
		<ShieldCheck class="size-3 shrink-0" />
	{:else if meta.iconName === 'BookOpen'}
		<BookOpen class="size-3 shrink-0" />
	{:else if meta.iconName === 'Sparkles'}
		<Sparkles class="size-3 shrink-0" />
	{:else if meta.iconName === 'LayoutGrid'}
		<LayoutGrid class="size-3 shrink-0" />
	{:else}
		<Wrench class="size-3 shrink-0" />
	{/if}
	<span>{meta.label}</span>
	{#if showCode}
		<code class="font-mono text-[10px] opacity-80">{meta.callType}</code>
	{/if}
</span>
