<script>
	import {
		Activity,
		AlertTriangle,
		Compass,
		Download,
		Minus,
		Plus,
		Server,
		ShieldCheck,
		UserCheck
	} from '@lucide/svelte';
	import gsap from 'gsap';
	import {
		NOMAD_DESTINATIONS,
		deriveDossierActivities,
		formatTripDossierMarkdown,
		summarizeHealthReport
	} from '$lib/agui-client.js';
	import AgUiActivityRenderer from './agui/AgUiActivityRenderer.svelte';

	let {
		tripDossierState = null,
		clusterState = null,
		systemHealth = null,
		messages = [],
		isStreaming = false,
		onTriggerSkill,
		onUpdateTripParams,
		onToggleHealthPopover
	} = $props();

	const HOME_CURRENCIES = ['USD', 'EUR', 'GBP'];

	let activeDossier = $derived(tripDossierState ?? clusterState);

	let nomadTrip = $derived(
		activeDossier?.nomadTrip ?? {
			activeCity: null,
			country: null,
			currency: null,
			exchangeRate: null,
			temperatureC: null,
			weatherCondition: null,
			nights: null,
			stopTitle: null,
			budgetUsd: null,
			allocatedUsd: null,
			homeCurrency: null,
			heroImageUrl: null,
			soundtrack: null
		}
	);

	let dossierActivities = $derived(deriveDossierActivities(activeDossier));
	let hasActiveCity = $derived(Boolean(nomadTrip?.activeCity || dossierActivities.length > 0));

	let preferences = $derived(
		activeDossier?.preferences ?? {
			travelerName: null,
			style: null,
			dietary: null,
			musicTaste: null,
			pace: null,
			homeCurrency: nomadTrip.homeCurrency ?? null
		}
	);

	let activeHomeCurrency = $derived(preferences.homeCurrency ?? nomadTrip.homeCurrency ?? null);
	let healthSummary = $derived(summarizeHealthReport(systemHealth));

	let dossierHeaderEl;

	$effect(() => {
		const cityKey = nomadTrip.activeCity;
		const sectionCount = dossierActivities.length;
		const prefersReduced =
			typeof window !== 'undefined' &&
			window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
		if (!prefersReduced && dossierHeaderEl && (cityKey || sectionCount > 0)) {
			gsap.fromTo(
				dossierHeaderEl,
				{ boxShadow: '0 0 0 2px rgba(200, 75, 49, 0.22)' },
				{ boxShadow: '0 0 0 0px rgba(200, 75, 49, 0)', duration: 0.5, ease: 'power2.out' }
			);
		}
	});

	function adjustNights(delta) {
		const base = nomadTrip.nights ?? 0;
		const nextNights = Math.max(1, Math.min(30, base + delta));
		if (nextNights !== nomadTrip.nights) {
			onUpdateTripParams?.({ nights: nextNights, homeCurrency: activeHomeCurrency });
		}
	}

	function selectHomeCurrency(currency) {
		if (currency !== activeHomeCurrency) {
			onUpdateTripParams?.({ nights: nomadTrip.nights, homeCurrency: currency });
		}
	}

	function exportDossierMarkdown() {
		if (typeof window === 'undefined') return;
		const md = formatTripDossierMarkdown(activeDossier, messages);
		const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		const citySlug = (nomadTrip.activeCity ?? 'trip').toLowerCase().replace(/\s+/g, '-');
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
	<div class="scrollable-region space-y-3.5 pr-0.5">
		<div class="flex items-center justify-between border-b border-[#E4E0D8] pb-3">
			<div class="flex items-center gap-2">
				<div class="flex size-7 items-center justify-center rounded-lg bg-[#141413] text-white">
					<Compass class="size-4" aria-hidden="true" />
				</div>
				<h2 class="text-sm font-bold tracking-tight text-[#141413]">Trip Dossier</h2>
			</div>
			<button
				type="button"
				disabled={!hasActiveCity}
				onclick={exportDossierMarkdown}
				class="inline-flex items-center gap-1 rounded-md border border-[#CFCAC0] bg-white px-2 py-1 font-mono text-[10px] font-semibold text-[#141413] transition-colors hover:border-[#C84B31] hover:text-[#C84B31] disabled:cursor-not-allowed disabled:opacity-45"
				title={hasActiveCity
					? 'Export complete destination dossier report as Markdown (.md)'
					: 'Select a destination first to export its dossier'}
			>
				<Download class="size-3" aria-hidden="true" />
				<span>Export .md</span>
			</button>
		</div>

		<!-- Active Destination & Live Parameter Controls -->
		<section
			bind:this={dossierHeaderEl}
			class="rounded-xl border border-[#E4E0D8] bg-white p-3.5 space-y-2.5"
			aria-label="Destination overview and live parameters"
		>
			<div class="flex items-start justify-between gap-2">
				<div class="min-w-0">
					{#if nomadTrip.activeCity}
						<h3 class="truncate text-base font-bold text-[#141413]">
							{nomadTrip.activeCity}{nomadTrip.country ? `, ${nomadTrip.country}` : ''}
						</h3>
						<p
							class="text-xs leading-snug text-[#575551]"
							title={nomadTrip.stopTitle ?? 'Complete AG-UI Dossier Report'}
						>
							{nomadTrip.stopTitle ?? `${dossierActivities.length} of 5 AG-UI dossier sections active`}
						</p>
					{:else}
						<h3 class="text-sm font-bold text-[#141413]">Awaiting Destination</h3>
						<p class="text-xs leading-snug text-[#575551]">
							No defaults assumed — choose a city & share your trip details
						</p>
					{/if}
				</div>

				<!-- Instant WebSocket Nights Stepper -->
				<div
					class="inline-flex shrink-0 items-center gap-1 rounded-lg border border-[#E4E0D8] bg-[#F6F5F2] px-1.5 py-0.5"
					role="group"
					aria-label="Adjust trip nights"
				>
					<button
						type="button"
						onclick={() => adjustNights(-1)}
						disabled={nomadTrip.nights == null || nomadTrip.nights <= 1}
						aria-label="Decrease nights"
						class="rounded p-0.5 text-[#141413] transition-colors hover:bg-[#E4E0D8] disabled:opacity-40"
					>
						<Minus class="size-3" aria-hidden="true" />
					</button>
					<span class="px-1 font-mono text-[11px] font-semibold text-[#141413] tabular-nums">
						{nomadTrip.nights != null ? `${nomadTrip.nights}N` : '-- N'}
					</span>
					<button
						type="button"
						onclick={() => adjustNights(1)}
						disabled={(nomadTrip.nights ?? 0) >= 30}
						aria-label="Increase nights"
						class="rounded p-0.5 text-[#141413] transition-colors hover:bg-[#E4E0D8] disabled:opacity-40"
					>
						<Plus class="size-3" aria-hidden="true" />
					</button>
				</div>
			</div>

			<div class="flex items-center justify-between border-t border-[#E4E0D8] pt-2 text-[11px] text-[#575551]">
				<span>Home Currency</span>
				<div class="inline-flex items-center gap-1" role="group" aria-label="Select Home Currency">
					{#each HOME_CURRENCIES as cur (cur)}
						<button
							type="button"
							onclick={() => selectHomeCurrency(cur)}
							class="rounded px-1.5 py-0.5 font-mono text-[11px] font-semibold transition-colors {activeHomeCurrency ===
							cur
								? 'bg-[#141413] text-white'
								: 'bg-[#F6F5F2] text-[#575551] hover:bg-[#E4E0D8] hover:text-[#141413]'}"
						>
							{cur}
						</button>
					{/each}
				</div>
			</div>
		</section>

		<!-- Complete AG-UI Dossier Report Sections (Weather, Map & Places, Security, FX, Culture & Audio) -->
		{#if dossierActivities.length > 0}
			<section class="space-y-2.5" aria-label="Complete AG-UI Dossier Report">
				<div class="flex items-center justify-between">
					<h3 class="text-xs font-bold text-[#141413]">AG-UI Dossier Report</h3>
					<span class="font-mono text-[10px] font-medium text-[#575551] tabular-nums">
						{dossierActivities.length} {dossierActivities.length === 1 ? 'surface' : 'surfaces'}
					</span>
				</div>
				<AgUiActivityRenderer activities={dossierActivities} variant="dossier" />
			</section>
		{/if}

		<!-- Live Traveler Profile (traveler://preferences MCP Resource) -->
		<section class="rounded-xl border border-[#E4E0D8] bg-white p-3 space-y-2" aria-label="Traveler Profile">
			<div class="flex items-center justify-between">
				<div class="flex items-center gap-1.5">
					<UserCheck class="size-3.5 text-[#1F6E50]" aria-hidden="true" />
					<h3 class="text-xs font-bold text-[#141413]">Traveler Profile</h3>
				</div>
				<code class="font-mono text-[10px] text-[#575551]">traveler://preferences</code>
			</div>
			<dl class="grid grid-cols-1 gap-1.5 text-[11px] m-0">
				{#if preferences.travelerName}
					<div class="flex items-start justify-between gap-2 border-b border-[#E4E0D8] pb-1.5">
						<dt class="shrink-0 font-medium text-[#575551]">Traveler</dt>
						<dd class="m-0 text-right leading-snug font-semibold text-[#141413]">
							{preferences.travelerName}
						</dd>
					</div>
				{/if}
				<div class="flex items-start justify-between gap-2 border-b border-[#E4E0D8] pb-1.5">
					<dt class="shrink-0 font-medium text-[#575551]">Style</dt>
					<dd
						class="m-0 text-right leading-snug {preferences.style
							? 'font-semibold text-[#141413]'
							: 'italic text-[#575551]'}"
					>
						{preferences.style ?? 'Not specified yet'}
					</dd>
				</div>
				<div class="flex items-start justify-between gap-2 border-b border-[#E4E0D8] pb-1.5">
					<dt class="shrink-0 font-medium text-[#575551]">Dietary</dt>
					<dd
						class="m-0 text-right leading-snug {preferences.dietary
							? 'font-semibold text-[#141413]'
							: 'italic text-[#575551]'}"
					>
						{preferences.dietary ?? 'Not specified yet'}
					</dd>
				</div>
				<div class="flex items-start justify-between gap-2">
					<dt class="shrink-0 font-medium text-[#575551]">Music</dt>
					<dd
						class="m-0 text-right leading-snug {preferences.musicTaste
							? 'font-semibold text-[#141413]'
							: 'italic text-[#575551]'}"
					>
						{preferences.musicTaste ?? 'Not specified yet'}
					</dd>
				</div>
			</dl>
		</section>

		<!-- Curated Destination Switcher -->
		<div class="space-y-2">
			<h3 class="text-xs font-bold text-[#141413]">Explore Destination (Asks Trip Details)</h3>
			<div class="space-y-1.5">
				{#each NOMAD_DESTINATIONS as dest (dest.id)}
					{@const isActive = nomadTrip.activeCity?.toLowerCase() === dest.city.toLowerCase()}
					<button
						type="button"
						disabled={isStreaming}
						onclick={() => onTriggerSkill?.(dest.prompt)}
						class="flex w-full items-center justify-between gap-2 rounded-xl border px-3 py-2 text-left text-xs transition-colors disabled:opacity-40 {isActive
							? 'border-[#C84B31] bg-white font-semibold text-[#141413]'
							: 'border-[#E4E0D8] bg-white/70 text-[#141413] hover:bg-white'}"
					>
						<div class="min-w-0">
							<div>{dest.city}, {dest.country}</div>
							<div class="truncate text-[10px] text-[#575551]">{dest.tagline}</div>
						</div>
						<span class="shrink-0 font-mono text-[10px] font-medium text-[#575551] tabular-nums">
							{dest.currency} • {dest.region}
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
					disabled={isStreaming || !nomadTrip.activeCity}
					title={nomadTrip.activeCity
						? `Convert trip budget for ${nomadTrip.activeCity}`
						: 'Select a destination first'}
					onclick={() =>
						onTriggerSkill?.(
							nomadTrip.budgetUsd != null
								? `Convert $${nomadTrip.budgetUsd} ${activeHomeCurrency ?? 'USD'} budget for ${nomadTrip.activeCity}`
								: `Convert $2,000 USD budget for ${nomadTrip.activeCity}`
						)}
					class="flex w-full items-center justify-between rounded-xl border border-[#E4E0D8] bg-white px-3 py-2 text-left text-xs font-medium text-[#141413] transition-colors hover:border-[#1F6E50] disabled:cursor-not-allowed disabled:opacity-45"
				>
					<span class="truncate">
						Audit FX & Trip Budget{nomadTrip.currency ? ` (${nomadTrip.currency})` : ''}
					</span>
					<ShieldCheck class="size-3.5 shrink-0 text-[#1F6E50]" aria-hidden="true" />
				</button>

				<button
					type="button"
					disabled={isStreaming || !nomadTrip.activeCity}
					title={nomadTrip.activeCity
						? `Check travel advisory for ${nomadTrip.activeCity}`
						: 'Select a destination first'}
					onclick={() => onTriggerSkill?.(`Check the travel advisory for ${nomadTrip.activeCity}`)}
					class="flex w-full items-center justify-between rounded-xl border border-[#E4E0D8] bg-white px-3 py-2 text-left text-xs font-medium text-[#141413] transition-colors hover:border-[#C84B31] disabled:cursor-not-allowed disabled:opacity-45"
				>
					<span class="truncate">Verify Entry & Travel Advisory</span>
					<ShieldCheck class="size-3.5 shrink-0 text-[#575551]" aria-hidden="true" />
				</button>

				<button
					type="button"
					disabled={isStreaming || !nomadTrip.activeCity}
					title={nomadTrip.activeCity
						? `Reserve sanctuary stay in ${nomadTrip.activeCity}`
						: 'Select a destination first'}
					onclick={() =>
						onTriggerSkill?.(
							`Reserve ${nomadTrip.stopTitle ?? `${nomadTrip.activeCity} Architectural Sanctuary`} in ${nomadTrip.activeCity} for ${nomadTrip.nights ?? 4} nights ($1840)`
						)}
					class="flex w-full items-center justify-between rounded-xl border border-[#9E331D]/35 bg-[#FBEEE9]/70 px-3 py-2 text-left text-xs font-semibold text-[#9E331D] transition-colors hover:bg-[#FBEEE9] disabled:cursor-not-allowed disabled:opacity-45"
				>
					<span class="truncate">Hold Sanctuary Suite ($1,840)</span>
					<Server class="size-3.5 shrink-0" aria-hidden="true" />
				</button>
			</div>
		</div>
	</div>

	<div
		class="mt-3 flex items-center justify-between gap-2 border-t border-[#E4E0D8] pt-2.5 font-mono text-[10px] text-[#575551]"
	>
		<span class="truncate">MCP :8081 • A2A :8082 • Ollama</span>
		<button
			type="button"
			onclick={() => onToggleHealthPopover?.()}
			class="inline-flex shrink-0 items-center gap-1 rounded px-1.5 py-0.5 font-semibold transition-colors {healthSummary.status ===
			'UP'
				? 'bg-[#EAF4EF] text-[#1F6E50]'
				: healthSummary.status === 'UNKNOWN'
					? 'bg-[#F6F5F2] text-[#575551]'
					: 'bg-[#FBEEE9] text-[#9E331D]'}"
			title="Click to inspect live upstream & protocol healthchecks (/q/health/ready)"
		>
			{#if healthSummary.status === 'UP'}
				<Activity class="size-2.5" aria-hidden="true" />
			{:else}
				<AlertTriangle class="size-2.5" aria-hidden="true" />
			{/if}
			<span>{healthSummary.label}</span>
		</button>
	</div>
</aside>
