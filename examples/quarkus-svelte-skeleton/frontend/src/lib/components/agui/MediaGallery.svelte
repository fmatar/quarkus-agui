<script>
	import { ExternalLink, FileText, Film, Image as ImageIcon, Music, Radio } from '@lucide/svelte';
	import { sanitizeExternalUrl } from '$lib/agui-client.js';

	let { media = [] } = $props();
	let lightboxImage = $state(null);
	let dialogEl = $state(null);
	let safeLightboxUrl = $derived(sanitizeExternalUrl(lightboxImage?.url));

	const WAVEFORM_BARS = [
		28, 52, 74, 42, 88, 64, 36, 94, 58, 46, 82, 70, 38, 90, 62, 48, 76, 54, 84, 40, 68, 50, 34, 60
	];

	function openLightbox(item) {
		lightboxImage = item;
		requestAnimationFrame(() => {
			if (dialogEl && !dialogEl.open) {
				dialogEl.showModal();
			}
		});
	}

	function closeLightbox() {
		if (dialogEl?.open) {
			dialogEl.close();
		}
		lightboxImage = null;
	}
</script>

{#if media?.length}
	<div class="turn-media-grid">
		{#each media as item (item.title + item.type)}
			{@const safeItemUrl = sanitizeExternalUrl(item.url)}
			{@const safePosterUrl = sanitizeExternalUrl(item.posterUrl)}
			{#if item.type === 'image' && safeItemUrl}
				<div class="nomad-card overflow-hidden rounded-xl">
					<button
						type="button"
						onclick={() => openLightbox({ ...item, url: safeItemUrl })}
						class="group relative block aspect-[16/10] max-h-60 w-full overflow-hidden bg-[#EFECE6] text-left"
					>
						<img
							src={safeItemUrl}
							alt={item.title}
							loading="lazy"
							class="block h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
						/>
						<span class="pointer-events-none absolute right-2.5 bottom-2.5 inline-flex items-center gap-1 rounded-md bg-[#141413]/75 px-2 py-1 font-mono text-[10px] font-medium text-white backdrop-blur-xs transition-opacity group-hover:bg-[#141413]/90">
							<span>Expand Plate</span>
							<ExternalLink class="size-2.5" />
						</span>
					</button>
					<div class="px-3.5 py-2.5">
						<div class="flex items-center gap-1.5 text-xs font-semibold text-[#141413]">
							<ImageIcon class="size-3.5 text-[#C84B31] shrink-0" />
							<span class="truncate">{item.title}</span>
						</div>
						{#if item.subtitle}
							<p class="mt-0.5 text-[11px] text-[#575551]">{item.subtitle}</p>
						{/if}
					</div>
				</div>
			{:else if item.type === 'audio'}
				<div class="nomad-card flex flex-col justify-between rounded-xl p-3.5">
					<div>
						<div class="flex items-center justify-between gap-2">
							<div class="flex items-center gap-2 min-w-0">
								<div class="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[#FBEEE9] text-[#C84B31]">
									<Music class="size-4" />
								</div>
								<div class="min-w-0">
									<div class="truncate text-xs font-semibold text-[#141413]">{item.title}</div>
									<div class="truncate text-[11px] text-[#575551]">{item.subtitle}</div>
								</div>
							</div>
							<span class="rounded bg-[#EFECE6] px-2 py-0.5 font-mono text-[11px] font-medium text-[#575551] tabular-nums">
								{item.durationSeconds ?? 30}s
							</span>
						</div>

						<!-- Editorial Acoustic Waveform & Archive Telemetry Strip -->
						<div class="mt-3 rounded-lg border border-[#E4E0D8] bg-[#FAF9F6] px-3 py-2">
							<div class="flex items-center justify-between text-[11px] text-[#575551]">
								<span class="inline-flex items-center gap-1 font-medium text-[#141413]">
									<Radio class="size-3 text-[#C84B31]" />
									<span>Regional Acoustic Master</span>
								</span>
								<span class="font-mono text-[10px] tabular-nums">AAC 256kbps • Stereo</span>
							</div>
							<div class="mt-2 flex h-7 items-end justify-between gap-1" aria-hidden="true">
								{#each WAVEFORM_BARS as heightPct, idx (idx)}
									<span
										class="flex-1 rounded-full {idx % 3 === 0 ? 'bg-[#C84B31]/65' : 'bg-[#CFCAC0]'}"
										style="height: {heightPct}%"
									></span>
								{/each}
							</div>
						</div>
					</div>

					{#if safeItemUrl}
						<div class="mt-3">
							<audio
								controls
								preload="none"
								src={safeItemUrl}
								aria-label="Play 30 second audio preview for {item.title}"
								class="h-9 w-full"
							></audio>
						</div>
					{/if}
				</div>
			{:else if item.type === 'video' && safeItemUrl}
				<div class="nomad-card overflow-hidden rounded-xl">
					<video
						controls
						preload="metadata"
						poster={safePosterUrl ?? undefined}
						src={safeItemUrl}
						class="h-36 w-full bg-[#141413] object-cover"
					>
						<track kind="captions" />
					</video>
					<div class="px-3.5 py-2.5">
						<div class="flex items-center gap-1.5 text-xs font-semibold text-[#141413]">
							<Film class="size-3.5 text-[#3B4CCA]" />
							<span class="truncate">{item.title}</span>
						</div>
						{#if item.subtitle}
							<p class="mt-0.5 text-[11px] text-[#575551]">{item.subtitle}</p>
						{/if}
					</div>
				</div>
			{:else if item.type === 'document' && safeItemUrl}
				<a
					href={safeItemUrl}
					target="_blank"
					rel="noopener noreferrer"
					class="nomad-card flex items-center justify-between gap-3 rounded-xl p-3.5 text-left transition-colors hover:bg-[#FAF9F6]"
				>
					<div class="flex items-center gap-2.5 min-w-0">
						<div class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-[#EAF4EF] text-[#1F6E50]">
							<FileText class="size-4" />
						</div>
						<div class="min-w-0">
							<div class="truncate font-mono text-xs font-semibold text-[#141413]">{item.title}</div>
							<div class="truncate text-[11px] text-[#575551]">{item.subtitle}</div>
						</div>
					</div>
					<ExternalLink class="size-4 shrink-0 text-[#575551]" />
				</a>
			{/if}
		{/each}
	</div>
{/if}

<dialog
	bind:this={dialogEl}
	aria-label={lightboxImage?.title ?? 'Destination Image Preview'}
	onclose={() => (lightboxImage = null)}
	onclick={(e) => {
		if (e.target === e.currentTarget) {
			closeLightbox();
		}
	}}
	class="nomad-lightbox-dialog m-auto w-full max-w-5xl overflow-hidden rounded-2xl border border-[#E4E0D8] bg-white p-0 shadow-2xl"
>
	{#if lightboxImage && safeLightboxUrl}
		<div class="flex items-center justify-center bg-[#141413] p-2 sm:p-4">
			<img
				src={safeLightboxUrl}
				alt={lightboxImage.title}
				class="block max-h-[78vh] w-full rounded-lg object-contain"
			/>
		</div>
		<div class="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5">
			<div>
				<div class="text-sm font-semibold text-[#141413]">{lightboxImage.title}</div>
				<div class="text-xs text-[#575551]">{lightboxImage.subtitle ?? ''}</div>
			</div>
			<div class="flex items-center gap-2">
				<a
					href={safeLightboxUrl}
					target="_blank"
					rel="noopener noreferrer"
					class="inline-flex items-center gap-1.5 rounded-lg border border-[#E4E0D8] bg-[#FAF9F6] px-3 py-1.5 text-xs font-medium text-[#141413] hover:bg-[#EFECE6]"
				>
					<span>Open Original</span>
					<ExternalLink class="size-3 text-[#575551]" />
				</a>
				<button
					type="button"
					onclick={closeLightbox}
					class="rounded-lg bg-[#141413] px-3.5 py-1.5 text-xs font-medium text-white hover:bg-[#2A2927]"
				>
					Close Preview (Esc)
				</button>
			</div>
		</div>
	{/if}
</dialog>
