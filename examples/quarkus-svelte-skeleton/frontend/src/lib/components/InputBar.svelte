<script>
	import { Command, Send, Square } from '@lucide/svelte';
	import { SLASH_COMMANDS } from '$lib/agui-client.js';

	let { isStreaming = false, onSubmit, onCancel } = $props();
	let promptText = $state('');

	let matchingCommands = $derived(
		promptText.trim().startsWith('/')
			? SLASH_COMMANDS.filter(
					(cmd) =>
						cmd.command.startsWith(promptText.trim().toLowerCase()) ||
						promptText.trim() === '/'
				)
			: []
	);

	function handleKeydown(event) {
		if (event.key === 'Enter' && !event.shiftKey) {
			event.preventDefault();
			triggerSubmit();
		}
	}

	function selectSlashCommand(cmd) {
		if (isStreaming) return;
		onSubmit?.(cmd.prompt);
		promptText = '';
	}

	function triggerSubmit() {
		const trimmed = promptText.trim();
		if (!trimmed || isStreaming) return;
		const matched = SLASH_COMMANDS.find((c) => c.command === trimmed.toLowerCase());
		onSubmit?.(matched ? matched.prompt : trimmed);
		promptText = '';
	}
</script>

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
			<div class="border-b border-[#E4E0D8] bg-[#F6F5F2] px-3.5 py-2 text-[11px] font-semibold text-[#575551]">
				AG-UI & Nomad Concierge Commands
			</div>
			<div class="max-h-56 overflow-y-auto divide-y divide-[#E4E0D8]">
				{#each matchingCommands as cmd (cmd.command)}
					<button
						type="button"
						onclick={() => selectSlashCommand(cmd)}
						class="flex w-full items-center justify-between gap-3 px-3.5 py-2.5 text-left text-xs transition-colors hover:bg-[#F6F5F2]"
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
		class="flex items-end gap-2.5"
	>
		<label for="agui-prompt-input" class="sr-only">Concierge Prompt</label>
		<div class="relative flex-1">
			<textarea
				id="agui-prompt-input"
				bind:value={promptText}
				onkeydown={handleKeydown}
				disabled={isStreaming}
				rows="1"
				placeholder="Ask Nomad Concierge or type / for protocol & destination commands (/kyoto, /lisbon, /media, /choices, /hitl)..."
				class="w-full max-h-32 resize-none rounded-xl border border-[#CFCAC0] bg-white px-3.5 py-2.5 text-xs sm:text-sm text-[#141413] placeholder-[#78756F] transition-colors focus:border-[#C84B31] focus:outline-none disabled:opacity-45"
			></textarea>
		</div>

		<button
			type="button"
			disabled={isStreaming}
			onclick={() => (promptText = promptText.startsWith('/') ? '' : '/')}
			aria-label="Toggle slash commands menu"
			title="Slash Commands (/)"
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
