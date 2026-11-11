<script>
	import { AlertTriangle, Coins } from '@lucide/svelte';

	let { content = {}, variant = 'turn' } = $props();

	let cityLabel = $derived(
		[content?.city, content?.country].filter(Boolean).join(', ') || 'Destination'
	);
	let baseCurrency = $derived(content?.baseCurrency ?? 'USD');
	let targetCurrency = $derived(content?.targetCurrency ?? '');
	let baseSymbol = $derived(
		baseCurrency === 'EUR' ? '€' : baseCurrency === 'GBP' ? '£' : '$'
	);
	let baseFactor = $derived(
		baseCurrency === 'EUR' ? 0.92 : baseCurrency === 'GBP' ? 0.79 : 1
	);

	let allocPct = $derived(
		content?.budgetUsd && content.budgetUsd > 0
			? Math.min(100, Math.round(((content?.allocatedUsd ?? 0) / content.budgetUsd) * 100))
			: 0
	);

	function formatNumber(n) {
		if (typeof n !== 'number' || !Number.isFinite(n)) return '--';
		const rounded = Math.round(n);
		if (rounded >= 1_000_000) {
			return `${(rounded / 1_000_000).toFixed(2)}M`;
		}
		return rounded.toLocaleString();
	}
</script>

<article
	class="agui-surface-card"
	data-variant={variant}
	aria-label="{cityLabel} currency exchange and budget audit"
>
	<header class="agui-card-header">
		<h4 class="agui-card-title">
			<Coins class="size-3.5 shrink-0 text-[#1F6E50]" aria-hidden="true" />
			<span>FX & Budget Audit</span>
		</h4>
		<span class="agui-pill {content?.isError ? 'agui-pill-err' : 'agui-pill-fx'}">
			{content?.isError ? 'DEGRADED' : (content?.source ?? 'api.frankfurter.dev')}
		</span>
	</header>

	{#if content?.isError}
		<div class="agui-degraded-note" role="status">
			<AlertTriangle class="size-3.5 shrink-0 text-[#9E331D]" aria-hidden="true" />
			<span>{content?.error ?? 'FX reference service unavailable'}</span>
		</div>
	{:else}
		<div class="agui-fx-grid">
			<div>
				<div class="font-mono text-base font-bold text-[#141413] tabular-nums">
					{#if content?.convertedTotal != null && targetCurrency}
						{formatNumber(content.convertedTotal)} {targetCurrency}
					{:else if content?.budgetUsd != null}
						{baseSymbol}{Math.round(content.budgetUsd * baseFactor).toLocaleString()} {baseCurrency}
					{:else}
						Awaiting budget
					{/if}
				</div>
				<div class="font-mono text-[11px] text-[#575551] tabular-nums">
					{#if content?.exchangeRate != null && targetCurrency}
						1 {baseCurrency} = {content.exchangeRate} {targetCurrency}
					{:else}
						FX rate pending
					{/if}
				</div>
			</div>

			<div class="agui-fx-allocation">
				<div class="flex items-center justify-between gap-2 text-[11px] text-[#575551] tabular-nums">
					<span>
						{content?.nights != null ? `${content.nights} nights` : 'Allocation'}
					</span>
					{#if content?.budgetUsd != null}
						<span class="font-mono font-semibold text-[#141413]">
							{baseSymbol}{Math.round((content?.allocatedUsd ?? 0) * baseFactor).toLocaleString()} / {baseSymbol}{Math.round(content.budgetUsd * baseFactor).toLocaleString()} ({allocPct}%)
						</span>
					{:else}
						<span class="font-mono">Not set</span>
					{/if}
				</div>
				<div
					class="agui-meter-track"
					style="--alloc-pct: {allocPct}%"
					role="progressbar"
					aria-valuenow={allocPct}
					aria-valuemin={0}
					aria-valuemax={100}
					aria-label="Allocated sanctuary budget percentage"
				>
					<div class="agui-meter-fill"></div>
				</div>
			</div>
		</div>
	{/if}
</article>

<style>
	.agui-surface-card {
		container-type: inline-size;
		display: flex;
		flex-direction: column;
		gap: 0.6rem;
	}

	.agui-surface-card[data-variant='dossier'] {
		background: var(--bg-surface);
		border: 1px solid var(--border-hairline);
		border-radius: 0.75rem;
		padding: 0.875rem;
	}

	.agui-surface-card[data-variant='turn'] {
		border-top: 1px solid var(--border-hairline);
		padding-top: 0.75rem;
	}

	.agui-card-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
	}

	.agui-card-title {
		display: inline-flex;
		align-items: center;
		gap: 0.375rem;
		font-size: 0.75rem;
		font-weight: 700;
		color: var(--ink-primary);
		margin: 0;
	}

	.agui-pill {
		font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
		font-size: 0.625rem;
		font-weight: 600;
		padding: 0.125rem 0.45rem;
		border-radius: 0.375rem;
		white-space: nowrap;
	}

	.agui-pill-fx {
		background: var(--accent-emerald-soft);
		color: var(--accent-emerald);
		border: 1px solid rgba(31, 110, 80, 0.22);
	}

	.agui-pill-err {
		background: var(--accent-terracotta-soft);
		color: var(--accent-terracotta-ink);
		border: 1px solid rgba(158, 51, 29, 0.25);
	}

	.agui-degraded-note {
		display: flex;
		align-items: flex-start;
		gap: 0.5rem;
		font-size: 0.75rem;
		color: var(--accent-terracotta-ink);
		background: var(--accent-terracotta-soft);
		border: 1px solid rgba(158, 51, 29, 0.22);
		border-radius: 0.5rem;
		padding: 0.5rem 0.625rem;
	}

	.agui-fx-grid {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: 0.6rem;
	}

	.agui-fx-allocation {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
	}

	.agui-meter-track {
		height: 0.375rem;
		width: 100%;
		overflow: hidden;
		border-radius: 9999px;
		background: var(--bg-rail);
	}

	.agui-meter-fill {
		height: 100%;
		width: var(--alloc-pct, 0%);
		border-radius: 9999px;
		background: var(--accent-emerald);
		transition: width 0.28s ease;
	}

	@container (min-width: 24rem) {
		.agui-fx-grid {
			grid-template-columns: minmax(0, 1fr) minmax(0, 1.15fr);
			align-items: center;
		}
	}
</style>
