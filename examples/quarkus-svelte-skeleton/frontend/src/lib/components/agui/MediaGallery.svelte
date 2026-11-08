<script>
	import { ExternalLink, FileText, Film, Image as ImageIcon, Music } from '@lucide/svelte';

	let { media = [] } = $props();
	let lightboxImage = $state(null);
</script>

{#if media?.length}
	<div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
		{#each media as item (item.title + item.type)}
			{#if item.type === 'image'}
				<div class="nomad-card overflow-hidden rounded-xl">
					<button
						type="button"
						onclick={() => (lightboxImage = item)}
						class="group relative block h-40 w-full overflow-hidden bg-[#EFECE6] text-left"
					>
						<img
							src={item.url}
							alt={item.title}
							loading="lazy"
							class="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
						/>
					</button>
					<div class="px-3.5 py-2.5">
						<div class="flex items-center gap-1.5 text-xs font-semibold text-[#141413]">
							<ImageIcon class="size-3.5 text-[#C84B31]" />
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
							<span class="rounded bg-[#EFECE6] px-2 py-0.5 font-mono text-[10px] font-medium text-[#575551] tabular-nums">
								{item.durationSeconds ?? 30}s
							</span>
						</div>
					</div>

					<div class="mt-3">
						<audio
							controls
							preload="none"
							src={item.url}
							aria-label="Play 30 second audio preview for {item.title}"
							class="h-9 w-full"
						></audio>
					</div>
				</div>
			{:else if item.type === 'video'}
				<div class="nomad-card overflow-hidden rounded-xl">
					<video
						controls
						preload="metadata"
						poster={item.posterUrl}
						src={item.url}
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
			{:else if item.type === 'document'}
				<a
					href={item.url}
					target="_blank"
					rel="noreferrer"
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

{#if lightboxImage}
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-6"
		role="dialog"
		aria-modal="true"
		aria-label={lightboxImage.title}
	>
		<div class="max-w-3xl overflow-hidden rounded-2xl bg-white shadow-2xl">
			<img src={lightboxImage.url} alt={lightboxImage.title} class="max-h-[72vh] w-full object-contain bg-[#141413]" />
			<div class="flex items-center justify-between px-5 py-3.5">
				<div>
					<div class="text-sm font-semibold text-[#141413]">{lightboxImage.title}</div>
					<div class="text-xs text-[#575551]">{lightboxImage.subtitle ?? ''}</div>
				</div>
				<button
					type="button"
					onclick={() => (lightboxImage = null)}
					class="rounded-lg border border-[#E4E0D8] px-3 py-1.5 text-xs font-medium text-[#141413] hover:bg-[#F6F5F2]"
				>
					Close Preview
				</button>
			</div>
		</div>
	</div>
{/if}
