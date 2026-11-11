<script>
	import { Phone, ShieldAlert, ShieldCheck } from '@lucide/svelte';

	let { content = {}, variant = 'turn' } = $props();

	let cityLabel = $derived(
		[content?.city, content?.country].filter(Boolean).join(', ') || 'Destination'
	);
	let isDegraded = $derived(Boolean(content?.isError || content?.status === 'DEGRADED'));
	let safetyHighlights = $derived(
		Array.isArray(content?.safetyHighlights) ? content.safetyHighlights : []
	);
</script>

<article
	class="agui-surface-card"
	data-variant={variant}
	aria-label="{cityLabel} security and entry advisory"
>
	<header class="agui-card-header">
		<h4 class="agui-card-title">
			{#if isDegraded}
				<ShieldAlert class="size-3.5 shrink-0 text-[#9E331D]" aria-hidden="true" />
			{:else}
				<ShieldCheck class="size-3.5 shrink-0 text-[#1F6E50]" aria-hidden="true" />
			{/if}
			<span>Security & Entry Advisory</span>
		</h4>
		<span class="agui-pill {isDegraded ? 'agui-pill-err' : 'agui-pill-verified'}">
			{isDegraded ? 'DEGRADED' : 'VERIFIED'}
		</span>
	</header>

	<p class="text-xs font-medium {isDegraded ? 'text-[#9E331D]' : 'text-[#141413]'} leading-snug">
		{content?.advisoryStatus ?? (isDegraded ? content?.error : 'Country metadata verified')}
	</p>

	<dl class="agui-security-meta">
		<div class="agui-sec-cell">
			<dt>Emergency</dt>
			<dd class="inline-flex items-center gap-1 font-mono font-semibold text-[#141413]">
				<Phone class="size-3 shrink-0 text-[#C84B31]" aria-hidden="true" />
				<span>{content?.emergencyNumbers ?? '112 Universal GSM'}</span>
			</dd>
		</div>
		<div class="agui-sec-cell">
			<dt>Jurisdiction</dt>
			<dd class="font-mono font-semibold text-[#141413]">
				{[content?.capital, content?.timezone, content?.currencyCode].filter(Boolean).join(' • ') || cityLabel}
			</dd>
		</div>
	</dl>

	{#if safetyHighlights.length > 0}
		<ul class="agui-safety-list">
			{#each safetyHighlights as item (item)}
				<li>{item}</li>
			{/each}
		</ul>
	{/if}
</article>

<style>
	.agui-surface-card {
		container-type: inline-size;
		display: flex;
		flex-direction: column;
		gap: 0.55rem;
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

	.agui-pill-verified {
		background: var(--accent-emerald-soft);
		color: var(--accent-emerald);
		border: 1px solid rgba(31, 110, 80, 0.22);
	}

	.agui-pill-err {
		background: var(--accent-terracotta-soft);
		color: var(--accent-terracotta-ink);
		border: 1px solid rgba(158, 51, 29, 0.25);
	}

	.agui-security-meta {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: 0.4rem;
		margin: 0;
	}

	.agui-sec-cell {
		background: var(--bg-substrate);
		border-radius: 0.5rem;
		padding: 0.4rem 0.55rem;
		font-size: 0.6875rem;
	}

	.agui-sec-cell dt {
		color: var(--ink-secondary);
		font-size: 0.625rem;
		margin-bottom: 0.1rem;
	}

	.agui-sec-cell dd {
		margin: 0;
	}

	.agui-safety-list {
		margin: 0;
		padding-left: 1rem;
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
		font-size: 0.6875rem;
		color: var(--ink-secondary);
		line-height: 1.4;
	}

	@container (min-width: 24rem) {
		.agui-security-meta {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}
	}
</style>
