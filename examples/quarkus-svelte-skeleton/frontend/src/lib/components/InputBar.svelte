<script>
	import { Send, Square, Sparkles, CornerDownLeft } from '@lucide/svelte';

	let { isStreaming = false, onSubmit, onCancel } = $props();
	let promptText = $state('');

	function handleKeydown(event) {
		if (event.key === 'Enter' && !event.shiftKey) {
			event.preventDefault();
			triggerSubmit();
		}
	}

	function triggerSubmit() {
		const trimmed = promptText.trim();
		if (!trimmed || isStreaming) return;
		onSubmit?.(trimmed);
		promptText = '';
	}
</script>

<div class="border-t border-slate-800/80 bg-slate-950/90 p-3.5 backdrop-blur-md shrink-0">
	<form onsubmit={(e) => { e.preventDefault(); triggerSubmit(); }} class="flex items-end gap-2.5">
		<label for="agui-prompt-input" class="sr-only">Agent Prompt</label>
		<div class="relative flex-1">
			<textarea
				id="agui-prompt-input"
				bind:value={promptText}
				onkeydown={handleKeydown}
				disabled={isStreaming}
				rows="1"
				placeholder="Instruct OpsPilot (Enter to send, Shift+Enter for newline)..."
				class="w-full resize-none rounded-xl border border-slate-800 bg-slate-950/80 px-4 py-2.5 text-xs sm:text-sm text-slate-100 placeholder-slate-500 transition-all focus:border-cyan-500/60 focus:bg-slate-900 focus:ring-1 focus:ring-cyan-500/30 focus:outline-none disabled:opacity-40 max-h-32"
			></textarea>
		</div>

		{#if isStreaming}
			<button
				type="button"
				onclick={() => onCancel?.()}
				class="inline-flex items-center gap-1.5 rounded-xl bg-rose-600 px-4 py-2.5 text-xs font-semibold text-white shadow-lg shadow-rose-950/50 transition-all hover:bg-rose-500 active:scale-95 shrink-0"
			>
				<Square class="size-3.5 fill-current" />
				<span>Abort</span>
			</button>
		{:else}
			<button
				type="submit"
				disabled={!promptText.trim()}
				class="inline-flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-cyan-600 to-indigo-600 px-4 py-2.5 text-xs font-semibold text-white shadow-lg shadow-cyan-950/40 transition-all hover:from-cyan-500 hover:to-indigo-500 active:scale-95 disabled:opacity-40 disabled:pointer-events-none shrink-0"
			>
				<Send class="size-3.5" />
				<span>Send</span>
			</button>
		{/if}
	</form>
</div>
