<script>
	import { ArrowUpRight, Check, Circle, Square, CheckSquare } from '@lucide/svelte';

	let {
		interaction = null,
		disabled = false,
		onSelectOption,
		onConfirmMultiSelect,
		onTriggerAction
	} = $props();

	let isMulti = $derived(interaction?.mode === 'multi_select');
	let selected = $derived(interaction?.selectedValues ?? []);
	let isLocked = $derived(Boolean(interaction?.submitted) || disabled);
</script>

{#if interaction?.options?.length || interaction?.actions?.length}
	<div class="rounded-xl border border-[#E4E0D8] bg-[#FAF9F6] p-3.5 space-y-3">
		{#if interaction?.options?.length}
			<div>
				<div class="mb-2 flex items-center justify-between text-xs">
					<span class="font-semibold text-[#141413]">
						{interaction.prompt ?? 'Select an itinerary preference:'}
					</span>
					<span class="rounded bg-[#EFECE6] px-2 py-0.5 font-mono text-[10px] text-[#575551]">
						{isMulti ? 'Multi-Select' : 'Single-Select'}
					</span>
				</div>

				<div class="grid grid-cols-1 gap-2">
					{#each interaction.options as opt (opt.id)}
						{@const isSelected = selected.includes(opt.id)}
						<button
							type="button"
							disabled={isLocked}
							onclick={() => onSelectOption?.(opt)}
							class="flex w-full items-start justify-between gap-3 rounded-lg border p-3 text-left transition-all {isSelected
								? 'border-[#C84B31] bg-[#FBEEE9]/70 text-[#141413]'
								: 'border-[#E4E0D8] bg-white text-[#141413] hover:border-[#CFCAC0]'} disabled:opacity-75"
						>
							<div class="flex items-start gap-2.5 min-w-0">
								<span class="mt-0.5 text-[#C84B31] shrink-0">
									{#if isMulti}
										{#if isSelected}
											<CheckSquare class="size-4" />
										{:else}
											<Square class="size-4 text-[#78756F]" />
										{/if}
									{:else if isSelected}
										<Check class="size-4" />
									{:else}
										<Circle class="size-4 text-[#78756F]" />
									{/if}
								</span>
								<div class="min-w-0">
									<div class="text-xs font-semibold text-[#141413]">{opt.label}</div>
									{#if opt.description}
										<p class="mt-0.5 text-[11px] text-[#575551]">{opt.description}</p>
									{/if}
								</div>
							</div>

							{#if opt.badge}
								<span class="shrink-0 rounded bg-[#EFECE6] px-2 py-0.5 font-mono text-[10px] font-medium text-[#141413] tabular-nums">
									{opt.badge}
								</span>
							{/if}
						</button>
					{/each}
				</div>

				{#if isMulti && !interaction.submitted}
					<div class="mt-2.5 flex justify-end">
						<button
							type="button"
							disabled={selected.length === 0 || disabled}
							onclick={() => onConfirmMultiSelect?.(selected)}
							class="inline-flex items-center gap-1.5 rounded-lg bg-[#141413] px-3.5 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-[#2B2A28] disabled:opacity-40"
						>
							<Check class="size-3.5" />
							<span>Confirm Selection ({selected.length})</span>
						</button>
					</div>
				{/if}
			</div>
		{/if}

		{#if interaction?.actions?.length}
			<div class="flex flex-wrap items-center gap-2 pt-1">
				{#each interaction.actions as act (act.id)}
					<button
						type="button"
						disabled={disabled}
						onclick={() => onTriggerAction?.(act.prompt ?? act.label)}
						class="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition-all disabled:opacity-40 {act.variant === 'primary'
							? 'bg-[#C84B31] text-white hover:bg-[#B03E26]'
							: 'border border-[#E4E0D8] bg-white text-[#141413] hover:bg-[#EFECE6]'}"
					>
						<span>{act.label}</span>
						<ArrowUpRight class="size-3.5" />
					</button>
				{/each}
			</div>
		{/if}
	</div>
{/if}
