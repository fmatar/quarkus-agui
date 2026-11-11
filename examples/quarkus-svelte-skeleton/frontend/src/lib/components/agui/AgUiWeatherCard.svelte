<script>
	import { AlertTriangle, CloudSun, ExternalLink, Wind } from '@lucide/svelte';
	import { formatCoordinatesLabel } from '$lib/agui-client.js';

	let { content = {}, variant = 'turn' } = $props();

	let cityLabel = $derived(
		[content?.city, content?.country].filter(Boolean).join(', ') || 'Destination'
	);
	let hasTemp = $derived(typeof content?.temperatureC === 'number' && Number.isFinite(content.temperatureC));
	let tempF = $derived(hasTemp ? Math.round((content.temperatureC * 9) / 5 + 32) : null);
	let coordsLabel = $derived(formatCoordinatesLabel(content?.latitude, content?.longitude));
</script>

<article
	class="agui-surface-card"
	data-variant={variant}
	aria-label="{cityLabel} weather telemetry"
>
	<header class="agui-card-header">
		<h4 class="agui-card-title">
			<CloudSun class="size-3.5 shrink-0 text-[#C84B31]" aria-hidden="true" />
			<span>Weather & Microclimate</span>
		</h4>
		<span class="agui-pill {content?.isError ? 'agui-pill-err' : 'agui-pill-ok'}">
			{content?.isError ? 'DEGRADED' : 'agui:weather'}
		</span>
	</header>

	{#if content?.isError}
		<div class="agui-degraded-note" role="status">
			<AlertTriangle class="size-3.5 shrink-0 text-[#9E331D]" aria-hidden="true" />
			<span>{content?.error ?? 'Open-Meteo weather telemetry unavailable'}</span>
		</div>
	{:else}
		<div class="agui-weather-grid">
			<div class="agui-weather-primary">
				<div class="font-mono text-lg font-bold tracking-tight text-[#141413] tabular-nums">
					{#if hasTemp}
						{content.temperatureC.toFixed(1)}°C
						<span class="text-xs font-medium text-[#575551]">({tempF}°F)</span>
					{:else}
						--°C
					{/if}
				</div>
				<p class="text-xs font-medium text-[#141413]">
					{content?.condition ?? 'Awaiting forecast telemetry'}
				</p>
				<p class="text-[11px] text-[#575551]">{cityLabel}</p>
			</div>

			<dl class="agui-weather-meta">
				<div class="agui-meta-row">
					<dt class="inline-flex items-center gap-1 text-[#575551]">
						<Wind class="size-3 text-[#575551]" aria-hidden="true" />
						<span>Wind</span>
					</dt>
					<dd class="font-mono font-semibold text-[#141413] tabular-nums">
						{content?.windSpeedKph != null ? `${content.windSpeedKph} km/h` : '--'}
					</dd>
				</div>
				{#if coordsLabel}
					<div class="agui-meta-row">
						<dt class="text-[#575551]">Coords</dt>
						<dd class="font-mono text-[10px] font-medium text-[#141413] tabular-nums">
							{coordsLabel}
						</dd>
					</div>
				{/if}
				<div class="agui-meta-row">
					<dt class="text-[#575551]">Source</dt>
					<dd>
						<a
							href="https://open-meteo.com"
							target="_blank"
							rel="noopener noreferrer"
							class="inline-flex items-center gap-1 font-mono text-[10px] font-medium text-[#3B4CCA] hover:underline"
						>
							<span>{content?.source ?? 'api.open-meteo.com'}</span>
							<ExternalLink class="size-2.5" aria-hidden="true" />
						</a>
					</dd>
				</div>
			</dl>
		</div>
	{/if}
</article>

<style>
	.agui-surface-card {
		container-type: inline-size;
		display: flex;
		flex-direction: column;
		gap: 0.625rem;
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

	.agui-pill-ok {
		background: var(--accent-ochre-soft);
		color: var(--accent-ochre);
		border: 1px solid rgba(154, 74, 6, 0.2);
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

	.agui-weather-grid {
		display: grid;
		grid-template-columns: minmax(0, 1fr);
		gap: 0.625rem;
	}

	.agui-weather-meta {
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
		font-size: 0.6875rem;
		margin: 0;
	}

	.agui-meta-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
	}

	.agui-meta-row dd {
		margin: 0;
	}

	@container (min-width: 22rem) {
		.agui-weather-grid {
			grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
			align-items: center;
		}
	}
</style>
