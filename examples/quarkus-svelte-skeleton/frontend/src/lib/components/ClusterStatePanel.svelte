<script>
	import {
		CloudSun,
		Compass,
		Coins,
		Music,
		Server,
		ShieldCheck
	} from '@lucide/svelte';
	import { NOMAD_DESTINATIONS } from '$lib/agui-client.js';

	let { clusterState, isStreaming = false, onTriggerSkill } = $props();

	let nomadTrip = $derived(
		clusterState?.nomadTrip ?? {
			activeCity: 'Kyoto',
			country: 'Japan',
			currency: 'JPY',
			exchangeRate: 151.4,
			temperatureC: 18.4,
			weatherCondition: 'Clear Cedar & Autumn Mist',
			nights: 4,
			stopTitle: 'Hoshinoya Arashiyama Pavilion',
			budgetUsd: 3200,
			allocatedUsd: 1680,
			soundtrack: {
				trackName: 'Haru no Umi (Spring Sea — Koto & Shakuhachi)',
				artistName: 'Kyoto Koto Consort'
			}
		}
	);

	let budgetPercent = $derived(
		Math.min(100, Math.round(((nomadTrip.allocatedUsd ?? 1680) / (nomadTrip.budgetUsd ?? 3200)) * 100))
	);

	let convertedTotal = $derived(
		Math.round((nomadTrip.allocatedUsd ?? 1680) * (nomadTrip.exchangeRate ?? 151.4))
	);
</script>

<aside
	class="workbench-sidebar nomad-rail flex flex-col justify-between overflow-hidden rounded-2xl p-4"
>
	<div class="scrollable-region space-y-4 pr-0.5">
		<div class="flex items-center justify-between border-b border-[#E4E0D8] pb-3">
			<div class="flex items-center gap-2">
				<div class="flex size-7 items-center justify-center rounded-lg bg-[#141413] text-white">
					<Compass class="size-4" />
				</div>
				<h2 class="text-sm font-bold tracking-tight text-[#141413]">Trip Dossier</h2>
			</div>
			<span class="rounded-md bg-white px-2 py-0.5 font-mono text-[10px] font-medium text-[#1F6E50] border border-[#E4E0D8]">
				Live Itinerary
			</span>
		</div>

		<!-- Active Destination Summary Card -->
		<div class="rounded-xl border border-[#E4E0D8] bg-white p-3.5 space-y-3">
			<div class="flex items-start justify-between gap-2">
				<div>
					<h3 class="text-base font-bold text-[#141413]">
						{nomadTrip.activeCity}, {nomadTrip.country ?? 'Japan'}
					</h3>
					<p class="text-xs text-[#575551]">{nomadTrip.stopTitle}</p>
				</div>
				<span class="rounded bg-[#EFECE6] px-2 py-0.5 font-mono text-[11px] font-semibold text-[#141413] tabular-nums">
					{nomadTrip.nights ?? 4} Nights
				</span>
			</div>

			<div class="grid grid-cols-2 gap-2 pt-1">
				<div class="rounded-lg bg-[#F6F5F2] p-2.5">
					<div class="flex items-center gap-1.5 text-[11px] font-medium text-[#575551]">
						<CloudSun class="size-3.5 text-[#C84B31]" />
						<span>Open-Meteo</span>
					</div>
					<div class="mt-1 font-mono text-sm font-bold text-[#141413] tabular-nums">
						{nomadTrip.temperatureC}°C
					</div>
					<div class="truncate text-[10px] text-[#575551]">{nomadTrip.weatherCondition}</div>
				</div>

				<div class="rounded-lg bg-[#F6F5F2] p-2.5">
					<div class="flex items-center gap-1.5 text-[11px] font-medium text-[#575551]">
						<Coins class="size-3.5 text-[#1F6E50]" />
						<span>Frankfurter FX</span>
					</div>
					<div class="mt-1 font-mono text-sm font-bold text-[#141413] tabular-nums">
						{convertedTotal.toLocaleString()} {nomadTrip.currency}
					</div>
					<div class="truncate text-[10px] text-[#575551] tabular-nums">
						1 USD = {nomadTrip.exchangeRate} {nomadTrip.currency}
					</div>
				</div>
			</div>

			<!-- Budget Allocation Bar -->
			<div class="space-y-1 pt-1">
				<div class="flex items-center justify-between text-[11px] text-[#575551] tabular-nums">
					<span>Allocated Sanctuary Budget</span>
					<span class="font-mono font-semibold text-[#141413]">
						${nomadTrip.allocatedUsd} / ${nomadTrip.budgetUsd} USD ({budgetPercent}%)
					</span>
				</div>
				<div class="h-1.5 w-full overflow-hidden rounded-full bg-[#EFECE6]">
					<div
						class="h-full rounded-full bg-[#1F6E50] transition-all duration-300"
						style="width: {budgetPercent}%"
					></div>
				</div>
			</div>

			{#if nomadTrip.soundtrack}
				<div class="flex items-center gap-2 rounded-lg border border-[#E4E0D8] bg-[#FAF9F6] px-2.5 py-2 text-[11px]">
					<Music class="size-3.5 text-[#C84B31] shrink-0" />
					<div class="min-w-0">
						<div class="truncate font-semibold text-[#141413]">{nomadTrip.soundtrack.trackName}</div>
						<div class="truncate text-[10px] text-[#575551]">{nomadTrip.soundtrack.artistName}</div>
					</div>
				</div>
			{/if}
		</div>

		<!-- Curated Destination Switcher -->
		<div class="space-y-2">
			<h3 class="text-xs font-bold text-[#141413]">Switch Destination Dossier</h3>
			<div class="space-y-1.5">
				{#each NOMAD_DESTINATIONS as dest (dest.id)}
					{@const isActive = nomadTrip.activeCity?.toLowerCase() === dest.city.toLowerCase()}
					<button
						type="button"
						disabled={isStreaming}
						onclick={() => onTriggerSkill?.(dest.prompt)}
						class="flex w-full items-center justify-between rounded-xl border px-3 py-2 text-left text-xs transition-colors disabled:opacity-40 {isActive
							? 'border-[#C84B31] bg-white text-[#141413] font-semibold'
							: 'border-[#E4E0D8] bg-white/70 text-[#141413] hover:bg-white'}"
					>
						<div>
							<div>{dest.city}, {dest.country}</div>
							<div class="text-[10px] text-[#575551]">{dest.condition}</div>
						</div>
						<span class="font-mono text-[11px] font-medium text-[#575551] tabular-nums">
							{dest.temperatureC}°C • {dest.currency}
						</span>
					</button>
				{/each}
			</div>
		</div>

		<!-- Concierge Actions -->
		<div class="space-y-2 pt-1">
			<h3 class="text-xs font-bold text-[#141413]">Concierge Verifications</h3>
			<div class="space-y-1.5">
				<button
					type="button"
					disabled={isStreaming}
					onclick={() => onTriggerSkill?.(`Convert ${nomadTrip.activeCity} budget to ${nomadTrip.currency} and audit limits`)}
					class="flex w-full items-center justify-between rounded-xl border border-[#E4E0D8] bg-white px-3 py-2 text-left text-xs font-medium text-[#141413] transition-colors hover:border-[#1F6E50] disabled:opacity-40"
				>
					<span class="truncate">Audit FX & Trip Budget ({nomadTrip.currency})</span>
					<ShieldCheck class="size-3.5 text-[#1F6E50] shrink-0" />
				</button>

				<button
					type="button"
					disabled={isStreaming}
					onclick={() => onTriggerSkill?.(`Verify travel advisory and entry readiness for ${nomadTrip.activeCity}`)}
					class="flex w-full items-center justify-between rounded-xl border border-[#E4E0D8] bg-white px-3 py-2 text-left text-xs font-medium text-[#141413] transition-colors hover:border-[#1F6E50] disabled:opacity-40"
				>
					<span class="truncate">Verify Entry & Cultural Advisory</span>
					<ShieldCheck class="size-3.5 text-[#1F6E50] shrink-0" />
				</button>

				<button
					type="button"
					disabled={isStreaming}
					onclick={() => onTriggerSkill?.(`Reserve ${nomadTrip.stopTitle} in ${nomadTrip.activeCity} for 4 nights ($1840)`)}
					class="flex w-full items-center justify-between rounded-xl border border-[#C84B31]/40 bg-[#FBEEE9]/70 px-3 py-2 text-left text-xs font-semibold text-[#C84B31] transition-colors hover:bg-[#FBEEE9] disabled:opacity-40"
				>
					<span class="truncate">Hold Sanctuary Suite ($1,840)</span>
					<Server class="size-3.5 shrink-0" />
				</button>
			</div>
		</div>
	</div>

	<div class="mt-3 flex items-center justify-between border-t border-[#E4E0D8] pt-2.5 font-mono text-[10px] text-[#575551]">
		<span>Open-Meteo • Wikipedia • iTunes • ECB FX</span>
		<span class="font-semibold text-[#1F6E50]">Live</span>
	</div>
</aside>
