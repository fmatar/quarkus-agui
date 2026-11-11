<script>
	import { AlertTriangle, ExternalLink, Music, Radio } from '@lucide/svelte';

	let { content = {}, variant = 'turn' } = $props();

	let cityLabel = $derived(
		[content?.city, content?.country].filter(Boolean).join(', ') || 'Destination'
	);
	let soundtrack = $derived(content?.soundtrack ?? null);
</script>

<article
	class="agui-surface-card agui-culture-card"
	data-variant={variant}
	aria-label="{cityLabel} cultural notes and acoustic preview"
>
	{#if content?.heroImageUrl && !content?.isError}
		<img
			src={content.heroImageUrl}
			alt="{cityLabel} architectural landmark"
			width="640"
			height="360"
			loading="lazy"
			class="agui-culture-img"
		/>
	{/if}

	<div class="agui-culture-body">
		<header class="agui-card-header">
			<h4 class="agui-card-title">
				<Music class="size-3.5 shrink-0 text-[#C84B31]" aria-hidden="true" />
				<span>Culture & Acoustic Preview</span>
			</h4>
			{#if content?.wikiSourceUrl}
				<a
					href={content.wikiSourceUrl}
					target="_blank"
					rel="noreferrer"
					class="inline-flex items-center gap-1 font-mono text-[10px] font-semibold text-[#3B4CCA] hover:underline"
				>
					<span>Wikipedia</span>
					<ExternalLink class="size-2.5" aria-hidden="true" />
				</a>
			{/if}
		</header>

		{#if content?.isError}
			<div class="agui-degraded-note" role="status">
				<AlertTriangle class="size-3.5 shrink-0 text-[#9E331D]" aria-hidden="true" />
				<span>{content?.error ?? 'Cultural & acoustic archive unavailable'}</span>
			</div>
		{:else}
			{#if content?.culturalSummary}
				<p class="agui-culture-summary">{content.culturalSummary}</p>
			{/if}

			{#if content?.acousticMood}
				<div class="agui-mood-row">
					<Radio class="size-3 shrink-0 text-[#C84B31]" aria-hidden="true" />
					<span class="font-medium text-[#141413]">{content.acousticMood}</span>
					{#if content?.listeningWindow}
						<span class="text-[#575551]">• {content.listeningWindow}</span>
					{/if}
				</div>
			{/if}

			{#if soundtrack}
				<div class="agui-audio-box">
					<div class="min-w-0">
						<div class="truncate text-xs font-semibold text-[#141413]">
							{soundtrack.trackName ?? `${cityLabel} Acoustic Preview`}
						</div>
						<div class="truncate text-[11px] text-[#575551]">
							{soundtrack.artistName ?? 'Regional Archive'}
						</div>
					</div>
					{#if soundtrack.previewUrl}
						<audio
							controls
							preload="none"
							src={soundtrack.previewUrl}
							aria-label="Play 30-second audio preview for {soundtrack.trackName ?? cityLabel}"
							class="mt-1.5 h-8 w-full"
						></audio>
					{/if}
				</div>
			{/if}
		{/if}
	</div>
</article>

<style>
	.agui-culture-card {
		container-type: inline-size;
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: 0.65rem;
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

	.agui-culture-img {
		display: block;
		width: 100%;
		aspect-ratio: 16 / 9;
		max-height: 9.5rem;
		object-fit: cover;
		border-radius: 0.5rem;
		background: var(--bg-rail);
	}

	.agui-culture-body {
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		min-width: 0;
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

	.agui-culture-summary {
		margin: 0;
		font-size: 0.72rem;
		line-height: 1.45;
		color: var(--ink-secondary);
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.agui-mood-row {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.3rem;
		font-size: 0.6875rem;
	}

	.agui-audio-box {
		background: var(--bg-substrate);
		border-radius: 0.5rem;
		padding: 0.45rem 0.55rem;
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

	@container (min-width: 28rem) {
		.agui-culture-card:has(> img) {
			grid-template-columns: minmax(140px, 0.75fr) minmax(0, 1.25fr);
			align-items: start;
		}
	}
</style>
