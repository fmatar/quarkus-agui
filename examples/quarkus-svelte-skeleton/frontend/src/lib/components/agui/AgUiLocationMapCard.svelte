<script>
	import { Check, ChevronRight, Copy, ExternalLink, MapPin, Navigation } from '@lucide/svelte';
	import { buildPlaceMapLinks } from '$lib/agui-client.js';

	let { content = {}, variant = 'turn' } = $props();

	let copiedCoords = $state(false);

	let cityLabel = $derived(
		[content?.city, content?.country].filter(Boolean).join(', ') || 'Destination'
	);

	let cityLinks = $derived(
		buildPlaceMapLinks({
			city: content?.city ?? '',
			country: content?.country ?? '',
			latitude: content?.latitude ?? null,
			longitude: content?.longitude ?? null,
			wikiUrl: content?.wikiSourceUrl ?? ''
		})
	);

	let stopsWithLinks = $derived(
		(Array.isArray(content?.stops) && content.stops.length > 0
			? content.stops
			: content?.stopTitle
				? [content.stopTitle]
				: [`${content?.city ?? 'Destination'} Historic Center`]
		).map((stopName) => ({
			name: stopName,
			links: buildPlaceMapLinks({
				name: stopName,
				city: content?.city ?? '',
				country: content?.country ?? '',
				latitude: content?.latitude ?? null,
				longitude: content?.longitude ?? null
			})
		}))
	);

	async function copyCoordinates() {
		if (!cityLinks.coordinatesRaw || typeof navigator === 'undefined' || !navigator.clipboard) {
			return;
		}
		try {
			await navigator.clipboard.writeText(cityLinks.coordinatesRaw);
			copiedCoords = true;
			setTimeout(() => {
				copiedCoords = false;
			}, 1800);
		} catch {
			copiedCoords = false;
		}
	}
</script>

<article
	class="agui-surface-card"
	data-variant={variant}
	aria-label="{cityLabel} places and map links"
>
	<header class="agui-card-header">
		<h4 class="agui-card-title">
			<MapPin class="size-3.5 shrink-0 text-[#C84B31]" aria-hidden="true" />
			<span>Places & Map Links</span>
		</h4>
		<div class="inline-flex items-center gap-1.5">
			{#if cityLinks.coordinatesRaw}
				<button
					type="button"
					onclick={copyCoordinates}
					class="agui-copy-btn"
					aria-label="Copy coordinates {cityLinks.coordinatesRaw}"
				>
					{#if copiedCoords}
						<Check class="size-3 text-[#1F6E50]" aria-hidden="true" />
						<span>Copied</span>
					{:else}
						<Copy class="size-3 text-[#575551]" aria-hidden="true" />
						<span>{cityLinks.coordinatesLabel}</span>
					{/if}
				</button>
			{:else}
				<span class="agui-pill">agui:map-places</span>
			{/if}
		</div>
	</header>

	{#if content?.focus || content?.pace}
		<p class="text-[11px] text-[#575551] leading-snug">
			<Navigation class="mr-1 inline size-3 text-[#1F6E50]" aria-hidden="true" />
			<span>{content?.focus ?? 'Architectural & cultural walk'}</span>
			{#if content?.pace}
				<span> • {content.pace}</span>
			{/if}
		</p>
	{/if}

	<ul class="agui-stops-list">
		{#each stopsWithLinks as stop (stop.name)}
			<li class="agui-stop-item">
				<span class="agui-stop-name">{stop.name}</span>
				<div class="agui-link-group" role="group" aria-label="Map links for {stop.name}">
					<a
						href={stop.links.googleMapsUrl}
						target="_blank"
						rel="noreferrer"
						class="agui-map-link"
					>
						<span>Google Maps</span>
						<ExternalLink class="size-2.5" aria-hidden="true" />
					</a>
					<a
						href={stop.links.appleMapsUrl}
						target="_blank"
						rel="noreferrer"
						class="agui-map-link"
					>
						<span>Apple Maps</span>
						<ExternalLink class="size-2.5" aria-hidden="true" />
					</a>
					<a
						href={stop.links.openStreetMapUrl}
						target="_blank"
						rel="noreferrer"
						class="agui-map-link"
					>
						<span>OpenStreetMap</span>
						<ExternalLink class="size-2.5" aria-hidden="true" />
					</a>
					<a
						href={stop.links.wikipediaUrl}
						target="_blank"
						rel="noreferrer"
						class="agui-map-link"
					>
						<span>Wikipedia</span>
						<ExternalLink class="size-2.5" aria-hidden="true" />
					</a>
				</div>
			</li>
		{/each}
	</ul>

	{#if cityLinks.openStreetMapEmbedUrl}
		<details class="agui-map-disclosure">
			<summary class="agui-map-summary">
				<span class="agui-summary-chevron" aria-hidden="true">
					<ChevronRight class="size-3.5 shrink-0" aria-hidden="true" />
				</span>
				<span>OpenStreetMap Preview ({cityLabel})</span>
			</summary>
			<div class="agui-map-frame-wrap">
				<iframe
					src={cityLinks.openStreetMapEmbedUrl}
					title="{cityLabel} OpenStreetMap interactive preview"
					width="600"
					height="220"
					loading="lazy"
					referrerpolicy="no-referrer"
					class="agui-map-iframe"
				></iframe>
			</div>
		</details>
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
		flex-wrap: wrap;
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
		background: var(--accent-ochre-soft);
		color: var(--accent-ochre);
		border: 1px solid rgba(154, 74, 6, 0.2);
	}

	.agui-copy-btn {
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
		font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
		font-size: 0.625rem;
		font-weight: 600;
		color: var(--ink-primary);
		background: var(--bg-subtle);
		border: 1px solid var(--border-hairline);
		border-radius: 0.375rem;
		padding: 0.15rem 0.45rem;
		cursor: pointer;
	}

	.agui-copy-btn:hover {
		border-color: var(--border-strong);
	}

	.agui-stops-list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
	}

	.agui-stop-item {
		display: flex;
		flex-direction: column;
		gap: 0.35rem;
		padding: 0.45rem 0.55rem;
		border-radius: 0.5rem;
		background: var(--bg-substrate);
	}

	.agui-stop-name {
		font-size: 0.75rem;
		font-weight: 600;
		color: var(--ink-primary);
		line-height: 1.35;
	}

	.agui-link-group {
		display: flex;
		flex-wrap: wrap;
		gap: 0.35rem;
	}

	.agui-map-link {
		display: inline-flex;
		align-items: center;
		gap: 0.25rem;
		font-size: 0.65rem;
		font-weight: 600;
		color: var(--ink-primary);
		background: var(--bg-surface);
		border: 1px solid var(--border-hairline);
		border-radius: 0.375rem;
		padding: 0.15rem 0.425rem;
		text-decoration: none;
	}

	.agui-map-link:hover {
		border-color: var(--accent-terracotta);
		color: var(--accent-terracotta-ink);
	}

	.agui-map-disclosure {
		border-top: 1px solid var(--border-hairline);
		padding-top: 0.5rem;
	}

	.agui-map-summary {
		list-style: none;
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		font-size: 0.7rem;
		font-weight: 600;
		color: var(--ink-primary);
		cursor: pointer;
		user-select: none;
	}

	.agui-map-summary::-webkit-details-marker {
		display: none;
	}

	.agui-summary-chevron {
		display: inline-flex;
		transition: transform 0.18s ease;
		color: var(--ink-secondary);
	}

	.agui-map-disclosure[open] .agui-summary-chevron {
		transform: rotate(90deg);
	}

	.agui-map-frame-wrap {
		margin-top: 0.5rem;
		border-radius: 0.5rem;
		overflow: hidden;
		border: 1px solid var(--border-hairline);
		background: var(--bg-subtle);
		min-height: 190px;
	}

	.agui-map-iframe {
		display: block;
		width: 100%;
		height: 200px;
		border: 0;
	}

	@container (min-width: 26rem) {
		.agui-stop-item {
			flex-direction: row;
			align-items: center;
			justify-content: space-between;
		}
	}
</style>
