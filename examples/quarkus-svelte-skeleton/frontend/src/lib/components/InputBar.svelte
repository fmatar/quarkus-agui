<script>
	import { Command, Send, Square } from '@lucide/svelte';
	import { SLASH_COMMANDS } from '$lib/agui-client.js';

	let { isStreaming = false, onSubmit, onCancel } = $props();
	let promptText = $state('');
	let textareaEl = $state(null);
	let activeCommandIdx = $state(0);

	let matchingCommands = $derived(
		promptText.trim().startsWith('/')
			? SLASH_COMMANDS.filter(
					(cmd) =>
						cmd.command.startsWith(promptText.trim().toLowerCase()) ||
						promptText.trim() === '/'
				)
			: []
	);

	function handleGlobalKeydown(event) {
		if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
			event.preventDefault();
			textareaEl?.focus();
			return;
		}
		if (
			event.key === '/' &&
			document.activeElement !== textareaEl &&
			!['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName ?? '')
		) {
			event.preventDefault();
			promptText = '/';
			activeCommandIdx = 0;
			textareaEl?.focus();
			return;
		}
		if (event.key === 'Escape' && promptText.startsWith('/')) {
			promptText = '';
			activeCommandIdx = 0;
		}
	}

	function handleKeydown(event) {
		if (matchingCommands.length > 0 && !isStreaming) {
			if (event.key === 'ArrowDown') {
				event.preventDefault();
				activeCommandIdx = (activeCommandIdx + 1) % matchingCommands.length;
				return;
			}
			if (event.key === 'ArrowUp') {
				event.preventDefault();
				activeCommandIdx =
					(activeCommandIdx - 1 + matchingCommands.length) % matchingCommands.length;
				return;
			}
			if (event.key === 'Enter' && !event.shiftKey) {
				event.preventDefault();
				const chosen = matchingCommands[activeCommandIdx] ?? matchingCommands[0];
				if (chosen) {
					selectSlashCommand(chosen);
					return;
				}
			}
		}
		if (event.key === 'Escape' && promptText.startsWith('/')) {
			event.preventDefault();
			promptText = '';
			activeCommandIdx = 0;
			return;
		}
		if (event.key === 'Enter' && !event.shiftKey) {
			event.preventDefault();
			triggerSubmit();
		}
	}

	function selectSlashCommand(cmd) {
		if (isStreaming) return;
		onSubmit?.(cmd.prompt);
		promptText = '';
		activeCommandIdx = 0;
	}

	function triggerSubmit() {
		const trimmed = promptText.trim();
		if (!trimmed || isStreaming) return;
		const matched = SLASH_COMMANDS.find((c) => c.command === trimmed.toLowerCase());
		onSubmit?.(matched ? matched.prompt : trimmed);
		promptText = '';
	}
</script>

<svelte:window onkeydown={handleGlobalKeydown} />

<div class="relative border-t border-[#E4E0D8] bg-[#FAF9F6] p-3.5 shrink-0">
	{#if matchingCommands.length > 0 && !isStreaming}
		<div
			class="
				absolute right-3.5 bottom-full left-3.5 z-20 mb-2 overflow-hidden
				rounded-xl border border-[#CFCAC0] bg-white shadow-xl
			"
			role="listbox"
			aria-label="Slash Commands"
		>
			<div class="flex items-center justify-between border-b border-[#E4E0D8] bg-[#F6F5F2] px-3.5 py-2 text-[11px] font-semibold text-[#575551]">
				<span>AG-UI & Nomad Concierge Commands</span>
				<span class="font-mono text-[10px]">↑↓ Navigate • Enter Select • Esc Close</span>
			</div>
			<div class="max-h-56 overflow-y-auto divide-y divide-[#E4E0D8]">
				{#each matchingCommands as cmd, idx (cmd.command)}
					<button
						type="button"
						role="option"
						aria-selected={idx === activeCommandIdx}
						onclick={() => selectSlashCommand(cmd)}
						onmouseenter={() => (activeCommandIdx = idx)}
						class="flex w-full items-center justify-between gap-3 px-3.5 py-2.5 text-left text-xs transition-colors {idx === activeCommandIdx
							? 'bg-[#FBEEE9]/60'
							: 'hover:bg-[#F6F5F2]'}"
					>
						<div class="min-w-0">
							<div class="flex items-center gap-2">
								<code class="rounded bg-[#EFECE6] px-1.5 py-0.5 font-mono text-xs font-bold text-[#C84B31]">
									{cmd.command}
								</code>
								<span class="font-semibold text-[#141413]">{cmd.label}</span>
							</div>
							<p class="mt-0.5 truncate text-[11px] text-[#575551]">{cmd.description}</p>
						</div>
						<span class="shrink-0 rounded bg-[#EFECE6] px-2 py-0.5 font-mono text-[10px] font-medium text-[#575551]">
							{cmd.protocol}
						</span>
					</button>
				{/each}
			</div>
		</div>
	{/if}

	<form
		onsubmit={(e) => {
			e.preventDefault();
			triggerSubmit();
		}}
		class="mx-auto flex w-full max-w-[1440px] items-end gap-2.5"
	>
		<label for="agui-prompt-input" class="sr-only">Concierge Prompt</label>
		<div class="relative flex-1">
			<textarea
				id="agui-prompt-input"
				bind:this={textareaEl}
				bind:value={promptText}
				onkeydown={handleKeydown}
				disabled={isStreaming}
				rows="1"
				placeholder="Ask about any city, weather, local music, architecture, or press ⌘K / type / for commands..."
				class="w-full max-h-32 resize-none rounded-xl border border-[#CFCAC0] bg-white px-3.5 py-2.5 text-xs sm:text-sm text-[#141413] placeholder-[#575551] transition-colors focus:border-[#C84B31] focus:outline-none disabled:opacity-45"
			></textarea>
		</div>

		<button
			type="button"
			disabled={isStreaming}
			onclick={() => (promptText = promptText.startsWith('/') ? '' : '/')}
			aria-label="Toggle slash commands menu"
			title="Slash Commands (/ or ⌘K)"
			class="inline-flex items-center justify-center rounded-xl border border-[#CFCAC0] bg-white p-2.5 text-[#575551] transition-colors hover:bg-[#EFECE6] hover:text-[#141413] disabled:opacity-40 shrink-0"
		>
			<Command class="size-4" />
		</button>

		{#if isStreaming}
			<button
				type="button"
				onclick={() => onCancel?.()}
				class="inline-flex items-center gap-1.5 rounded-xl bg-[#C84B31] px-4 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#B03E26] shrink-0"
			>
				<Square class="size-3.5 fill-current" />
				<span>Stop</span>
			</button>
		{:else}
			<button
				type="submit"
				disabled={!promptText.trim()}
				class="inline-flex items-center gap-1.5 rounded-xl bg-[#141413] px-4 py-2.5 text-xs font-semibold text-white transition-colors hover:bg-[#2B2A28] disabled:opacity-40 disabled:pointer-events-none shrink-0"
			>
				<Send class="size-3.5" />
				<span>Send</span>
			</button>
		{/if}
	</form>
</div>
