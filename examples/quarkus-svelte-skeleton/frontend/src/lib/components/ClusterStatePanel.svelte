<script>
	import {
		CloudSun,
		Compass,
		Coins,
		Download,
		Minus,
		Music,
		Plus,
		Server,
		ShieldCheck,
		UserCheck
	} from '@lucide/svelte';
	import gsap from 'gsap';
	import { NOMAD_DESTINATIONS, formatTripDossierMarkdown } from '$lib/agui-client.js';

	let {
		clusterState,
		messages = [],
		isStreaming = false,
		onTriggerSkill,
		onUpdateTripParams
	} = $props();

	const HOME_CURRENCIES = ['USD', 'EUR', 'GBP'];

	let nomadTrip = $derived(
		clusterState?.nomadTrip ?? {
			activeCity: null,
			country: null,
			currency: null,
			exchangeRate: null,
			temperatureC: null,
			weatherCondition: 'Awaiting city selection',
			nights: 4,
			stopTitle: 'Select or type any city to initialize live telemetry',
			budgetUsd: 3200,
			allocatedUsd: 0,
			homeCurrency: 'USD',
			soundtrack: null
		}
	);

	let hasActiveCity = $derived(Boolean(nomadTrip?.activeCity));

	let preferences = $derived(
		clusterState?.preferences ?? {
			style: 'Architectural Minimalist & Slow Cultural Immersion',
			dietary: 'Seasonal Local Terroir',
			musicTaste: 'Analog Vinyl & Regional Acoustic',
			pace: 'Unrushed (2 curated stops per day)',
			homeCurrency: nomadTrip.homeCurrency ?? 'USD'
		}
	);

	let activeHomeCurrency = $derived(preferences.homeCurrency ?? nomadTrip.homeCurrency ?? 'USD');

	let homeCurrencySymbol = $derived(
		activeHomeCurrency === 'EUR' ? '€' : activeHomeCurrency === 'GBP' ? '£' : '$'
	);

	let homeCurrencyFactor = $derived(
		activeHomeCurrency === 'EUR' ? 0.92 : activeHomeCurrency === 'GBP' ? 0.79 : 1
	);

	let budgetPercent = $derived(
		hasActiveCity
			? Math.min(100, Math.round(((nomadTrip.allocatedUsd ?? 0) / (nomadTrip.budgetUsd ?? 3200)) * 100))
			: 0
	);

	let convertedTotal = $derived(
		hasActiveCity
			? Math.round((nomadTrip.allocatedUsd ?? 0) * (nomadTrip.exchangeRate ?? 1))
			: 0
	);

	const rawCounters = {
		temp: 0,
		fxTotal: 0,
		allocated: 0
	};

	let tweenState = $state({
		temp: 0,
		fxTotal: 0,
		allocated: 0
	});

	let dossierCardEl;

	$effect(() => {
		const targetTemp = Number(nomadTrip.temperatureC ?? 0);
		const targetFx = Number(convertedTotal ?? 0);
		const targetAllocated = Number(nomadTrip.allocatedUsd ?? 0);
		const prefersReduced =
			typeof window !== 'undefined' &&
			window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

		if (prefersReduced) {
			rawCounters.temp = targetTemp;
			rawCounters.fxTotal = targetFx;
			rawCounters.allocated = targetAllocated;
			tweenState.temp = targetTemp;
			tweenState.fxTotal = targetFx;
			tweenState.allocated = targetAllocated;
			return;
		}

		gsap.to(rawCounters, {
			temp: targetTemp,
			fxTotal: targetFx,
			allocated: targetAllocated,
			duration: 0.45,
			ease: 'power3.out',
			onUpdate: () => {
				tweenState.temp = rawCounters.temp;
				tweenState.fxTotal = rawCounters.fxTotal;
				tweenState.allocated = rawCounters.allocated;
			}
		});

		if (dossierCardEl && hasActiveCity) {
			gsap.fromTo(
				dossierCardEl,
				{ boxShadow: '0 0 0 2px rgba(200, 75, 49, 0.22)' },
				{ boxShadow: '0 0 0 0px rgba(200, 75, 49, 0)', duration: 0.55, ease: 'power2.out' }
			);
		}
	});

	function adjustNights(delta) {
		const nextNights = Math.max(1, Math.min(30, (nomadTrip.nights ?? 4) + delta));
		if (nextNights !== nomadTrip.nights) {
			onUpdateTripParams?.({ nights: nextNights, homeCurrency: activeHomeCurrency });
		}
	}

	function selectHomeCurrency(currency) {
		if (currency !== activeHomeCurrency) {
			onUpdateTripParams?.({ nights: nomadTrip.nights ?? 4, homeCurrency: currency });
		}
	}

	function formatFxAmount(val) {
		const rounded = Math.round(val);
		if (rounded >= 1_000_000) {
			return `${(rounded / 1_000_000).toFixed(1)}M`;
		}
		return rounded.toLocaleString();
	}

	function exportDossierMarkdown() {
		if (typeof window === 'undefined') return;
		const md = formatTripDossierMarkdown(clusterState, messages);
		const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		const citySlug = (nomadTrip.activeCity ?? 'kyoto').toLowerCase().replace(/\s+/g, '-');
		a.href = url;
		a.download = `nomad-${citySlug}-dossier.md`;
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
		URL.revokeObjectURL(url);
	}
</script>

<aside
	class="workbench-sidebar nomad-rail flex flex-col justify-between overflow-hidden rounded-2xl p-4"
	aria-label="Trip Dossier and Traveler Profile"
>
	<div class="scrollable-region space-y-4 pr-0.5">
		<div class="flex items-center justify-between border-b border-[#E4E0D8] pb-3">
			<div class="flex items-center gap-2">
				<div class="flex size-7 items-center justify-center rounded-lg bg-[#141413] text-white">
					<Compass class="size-4" />
				</div>
				<h2 class="text-sm font-bold tracking-tight text-[#141413]">Trip Dossier</h2>
			</div>
			<button
				type="button"
				disabled={!hasActiveCity}
				onclick={exportDossierMarkdown}
				class="inline-flex items-center gap-1 rounded-md border border-[#CFCAC0] bg-white px-2 py-1 font-mono text-[10px] font-semibold text-[#141413] transition-colors hover:border-[#C84B31] hover:text-[#C84B31] disabled:cursor-not-allowed disabled:opacity-45"
				title={hasActiveCity
					? 'Export active destination dossier as Markdown (.md)'
					: 'Select a destination first to export its dossier'}
			>
				<Download class="size-3" />
				<span>Export .md</span>
			</button>
		</div>

		<!-- Active Destination Summary Card -->
		<div bind:this={dossierCardEl} class="rounded-xl border border-[#E4E0D8] bg-white p-3.5 space-y-3">
			<div class="flex items-start justify-between gap-2">
				<div class="min-w-0">
					{#if hasActiveCity}
						<h3 class="text-base font-bold text-[#141413] truncate">
							{nomadTrip.activeCity}{nomadTrip.country ? `, ${nomadTrip.country}` : ''}
						</h3>
						<p class="text-xs text-[#575551] leading-snug" title={nomadTrip.stopTitle}>
							{nomadTrip.stopTitle}
						</p>
					{:else}
						<h3 class="text-sm font-bold text-[#141413]">
							Awaiting Destination
						</h3>
						<p class="text-xs text-[#575551] leading-snug">
							Select or type any city to initialize live telemetry
						</p>
					{/if}
				</div>

				<!-- Instant WebSocket Nights Stepper -->
				<div
					class="inline-flex items-center gap-1 rounded-lg border border-[#E4E0D8] bg-[#F6F5F2] px-1.5 py-0.5 shrink-0"
					role="group"
					aria-label="Adjust trip nights"
				>
					<button
						type="button"
						onclick={() => adjustNights(-1)}
						disabled={(nomadTrip.nights ?? 4) <= 1}
						aria-label="Decrease nights"
						class="rounded p-0.5 text-[#141413] transition-colors hover:bg-[#E4E0D8] disabled:opacity-40"
					>
						<Minus class="size-3" />
					</button>
					<span class="px-1 font-mono text-[11px] font-semibold text-[#141413] tabular-nums">
						{nomadTrip.nights ?? 4}N
					</span>
					<button
						type="button"
						onclick={() => adjustNights(1)}
						disabled={(nomadTrip.nights ?? 4) >= 30}
						aria-label="Increase nights"
						class="rounded p-0.5 text-[#141413] transition-colors hover:bg-[#E4E0D8] disabled:opacity-40"
					>
						<Plus class="size-3" />
					</button>
				</div>
			</div>

			<div class="grid grid-cols-2 gap-2 pt-1">
				<div class="rounded-lg bg-[#F6F5F2] p-2.5">
					<div class="flex items-center gap-1.5 text-[11px] font-medium text-[#575551]">
						<CloudSun class="size-3.5 text-[#C84B31]" />
						<span>Open-Meteo</span>
					</div>
					{#if hasActiveCity && nomadTrip.temperatureC != null}
						<div class="mt-1 font-mono text-sm font-bold text-[#141413] tabular-nums">
							{tweenState.temp.toFixed(1)}°C
						</div>
						<div class="truncate text-[11px] text-[#575551]" title={nomadTrip.weatherCondition}>
							{nomadTrip.weatherCondition}
						</div>
					{:else}
						<div class="mt-1 font-mono text-sm font-bold text-[#575551] tabular-nums">
							--°C
						</div>
						<div class="truncate text-[11px] text-[#575551]">
							Awaiting city selection
						</div>
					{/if}
				</div>

				<div class="rounded-lg bg-[#F6F5F2] p-2.5">
					<div class="flex items-center gap-1.5 text-[11px] font-medium text-[#575551]">
						<Coins class="size-3.5 text-[#1F6E50] shrink-0" />
						<span class="truncate">Frankfurter FX</span>
					</div>
					{#if hasActiveCity && nomadTrip.currency}
						<div class="mt-1 truncate whitespace-nowrap font-mono text-sm font-bold text-[#141413] tabular-nums">
							{formatFxAmount(tweenState.fxTotal)} {nomadTrip.currency}
						</div>
						<div class="truncate text-[11px] text-[#575551] tabular-nums">
							1 {activeHomeCurrency} = {formatFxAmount((nomadTrip.exchangeRate ?? 1) / homeCurrencyFactor)} {nomadTrip.currency}
						</div>
					{:else}
						<div class="mt-1 truncate whitespace-nowrap font-mono text-sm font-bold text-[#575551] tabular-nums">
							--
						</div>
						<div class="truncate text-[11px] text-[#575551] tabular-nums">
							1 {activeHomeCurrency} = --
						</div>
					{/if}
				</div>
			</div>

			<!-- Budget Allocation Bar + Home Currency Switcher -->
			<div class="space-y-1.5 pt-1">
				<div class="flex items-center justify-between gap-2 text-[11px] text-[#575551] tabular-nums">
					<span class="whitespace-nowrap">Sanctuary Budget</span>
					<span class="whitespace-nowrap font-mono font-semibold text-[#141413]">
						{homeCurrencySymbol}{hasActiveCity ? Math.round(tweenState.allocated * homeCurrencyFactor).toLocaleString() : '0'} / {homeCurrencySymbol}{Math.round((nomadTrip.budgetUsd ?? 3200) * homeCurrencyFactor).toLocaleString()} ({budgetPercent}%)
					</span>
				</div>
				<div class="h-1.5 w-full overflow-hidden rounded-full bg-[#EFECE6]">
					<div
						class="h-full rounded-full bg-[#1F6E50] transition-all duration-300"
						style="width: {budgetPercent}%"
					></div>
				</div>
				<div class="flex items-center justify-between pt-1 text-[11px] text-[#575551]">
					<span>Home Currency</span>
					<div class="inline-flex items-center gap-1" role="group" aria-label="Select Home Currency">
						{#each HOME_CURRENCIES as cur (cur)}
							<button
								type="button"
								onclick={() => selectHomeCurrency(cur)}
								class="rounded px-1.5 py-0.5 font-mono text-[11px] font-semibold transition-colors {activeHomeCurrency === cur
									? 'bg-[#141413] text-white'
									: 'bg-[#F6F5F2] text-[#575551] hover:bg-[#E4E0D8] hover:text-[#141413]'}"
							>
								{cur}
							</button>
						{/each}
					</div>
				</div>
			</div>

			{#if nomadTrip.soundtrack}
				<div class="flex items-center gap-2 rounded-lg border border-[#E4E0D8] bg-[#FAF9F6] px-2.5 py-2 text-[11px]">
					<Music class="size-3.5 text-[#C84B31] shrink-0" />
					<div class="min-w-0">
						<div class="truncate font-semibold text-[#141413]">{nomadTrip.soundtrack.trackName}</div>
						<div class="truncate text-[11px] text-[#575551]">{nomadTrip.soundtrack.artistName}</div>
					</div>
				</div>
			{/if}
		</div>

		<!-- Live Traveler Profile (traveler://preferences MCP Resource) -->
		<div class="rounded-xl border border-[#E4E0D8] bg-white p-3 space-y-2">
			<div class="flex items-center justify-between">
				<div class="flex items-center gap-1.5">
					<UserCheck class="size-3.5 text-[#1F6E50]" />
					<h3 class="text-xs font-bold text-[#141413]">Traveler Profile</h3>
				</div>
				<code class="font-mono text-[10px] text-[#575551]">traveler://preferences</code>
			</div>
			<div class="grid grid-cols-1 gap-1.5 text-[11px]">
				{#if preferences.travelerName}
					<div class="flex items-start justify-between gap-2 rounded-lg bg-[#F6F5F2] px-2.5 py-1.5">
						<span class="font-medium text-[#575551] shrink-0">Traveler</span>
						<span class="text-right font-semibold text-[#141413] leading-snug" title={preferences.travelerName}>
							{preferences.travelerName}
						</span>
					</div>
				{/if}
				<div class="flex items-start justify-between gap-2 rounded-lg bg-[#F6F5F2] px-2.5 py-1.5">
					<span class="font-medium text-[#575551] shrink-0">Style</span>
					<span class="text-right font-semibold text-[#141413] leading-snug" title={preferences.style}>
						{preferences.style}
					</span>
				</div>
				<div class="flex items-start justify-between gap-2 rounded-lg bg-[#F6F5F2] px-2.5 py-1.5">
					<span class="font-medium text-[#575551] shrink-0">Terroir</span>
					<span class="text-right font-semibold text-[#141413] leading-snug" title={preferences.dietary ?? 'Seasonal Local Terroir'}>
						{preferences.dietary ?? 'Seasonal Local Terroir'}
					</span>
				</div>
				<div class="flex items-start justify-between gap-2 rounded-lg bg-[#F6F5F2] px-2.5 py-1.5">
					<span class="font-medium text-[#575551] shrink-0">Sonic</span>
					<span class="text-right font-semibold text-[#141413] leading-snug" title={preferences.musicTaste ?? 'Analog Vinyl & Regional Acoustic'}>
						{preferences.musicTaste ?? 'Analog Vinyl & Regional Acoustic'}
					</span>
				</div>
			</div>
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
					disabled={isStreaming || !hasActiveCity}
					title={hasActiveCity ? `Audit FX & Trip Budget for ${nomadTrip.activeCity}` : 'Select a destination first'}
					onclick={() => onTriggerSkill?.(`Convert ${nomadTrip.activeCity} budget to ${nomadTrip.currency} and audit limits`)}
					class="flex w-full items-center justify-between rounded-xl border border-[#E4E0D8] bg-white px-3 py-2 text-left text-xs font-medium text-[#141413] transition-colors hover:border-[#1F6E50] disabled:cursor-not-allowed disabled:opacity-45"
				>
					<span class="truncate">Audit FX & Trip Budget{nomadTrip.currency ? ` (${nomadTrip.currency})` : ''}</span>
					<ShieldCheck class="size-3.5 text-[#1F6E50] shrink-0" />
				</button>

				<button
					type="button"
					disabled={isStreaming || !hasActiveCity}
					title={hasActiveCity ? `Verify travel advisory for ${nomadTrip.activeCity}` : 'Select a destination first'}
					onclick={() => onTriggerSkill?.(`Verify travel advisory and entry readiness for ${nomadTrip.activeCity}`)}
					class="flex w-full items-center justify-between rounded-xl border border-[#E4E0D8] bg-white px-3 py-2 text-left text-xs font-medium text-[#141413] transition-colors hover:border-[#1F6E50] disabled:cursor-not-allowed disabled:opacity-45"
				>
					<span class="truncate">Verify Entry & Cultural Advisory</span>
					<ShieldCheck class="size-3.5 text-[#1F6E50] shrink-0" />
				</button>

				<button
					type="button"
					disabled={isStreaming || !hasActiveCity}
					title={hasActiveCity ? `Reserve ${nomadTrip.stopTitle}` : 'Select a destination first'}
					onclick={() => onTriggerSkill?.(`Reserve ${nomadTrip.stopTitle} in ${nomadTrip.activeCity} for ${nomadTrip.nights ?? 4} nights ($1840)`)}
					class="flex w-full items-center justify-between rounded-xl border border-[#C84B31]/40 bg-[#FBEEE9]/70 px-3 py-2 text-left text-xs font-semibold text-[#C84B31] transition-colors hover:bg-[#FBEEE9] disabled:cursor-not-allowed disabled:opacity-45"
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
