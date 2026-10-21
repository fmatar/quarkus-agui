<script>
	import { Send, Square } from '@lucide/svelte';

	let { isStreaming = false, onSubmit, onCancel } = $props();
	let promptText = $state('');

	function handleSubmit(event) {
		event.preventDefault();
		const trimmed = promptText.trim();
		if (!trimmed || isStreaming) return;
		onSubmit?.(trimmed);
		promptText = '';
	}
</script>

<form onsubmit={handleSubmit} class="flex items-center gap-2 border-t border-slate-800 p-3">
	<label for="agui-prompt-input" class="sr-only">Agent Prompt</label>
	<input
		id="agui-prompt-input"
		type="text"
		bind:value={promptText}
		disabled={isStreaming}
		placeholder="Ask OpsPilot to inspect MCP cluster health, scale replicas, or run an A2A audit..."
		class="min-w-0 flex-1 rounded-lg border border-slate-700 bg-slate-900 px-3.5 py-2 text-sm text-slate-100 placeholder-slate-400 focus:border-indigo-500 focus:outline-none disabled:opacity-50"
	/>
	{#if isStreaming}
		<button
			type="button"
			onclick={() => onCancel?.()}
			class="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-3.5 py-2 text-xs font-semibold text-white hover:bg-rose-500"
		>
			<Square class="size-3.5" />
			<span>Stop</span>
		</button>
	{:else}
		<button
			type="submit"
			class="inline-flex items-center gap-1.5 rounded-lg bg-indigo-600 px-4 py-2 text-xs font-semibold text-white hover:bg-indigo-500"
		>
			<Send class="size-3.5" />
			<span>Run AG-UI</span>
		</button>
	{/if}
</form>
