<script>
	import { AGUI_ACTIVITY_TYPES, createActivityRegistry } from '$lib/agui-client.js';
	import AgUiWeatherCard from './AgUiWeatherCard.svelte';
	import AgUiLocationMapCard from './AgUiLocationMapCard.svelte';
	import AgUiSecurityAdvisoryCard from './AgUiSecurityAdvisoryCard.svelte';
	import AgUiFxBudgetCard from './AgUiFxBudgetCard.svelte';
	import AgUiCultureAudioCard from './AgUiCultureAudioCard.svelte';

	const activityRegistry = createActivityRegistry({
		[AGUI_ACTIVITY_TYPES.WEATHER]: AgUiWeatherCard,
		[AGUI_ACTIVITY_TYPES.MAP_PLACES]: AgUiLocationMapCard,
		[AGUI_ACTIVITY_TYPES.SECURITY_ADVISORY]: AgUiSecurityAdvisoryCard,
		[AGUI_ACTIVITY_TYPES.FX_BUDGET]: AgUiFxBudgetCard,
		[AGUI_ACTIVITY_TYPES.CULTURE_AUDIO]: AgUiCultureAudioCard
	});

	let { activities = [], variant = 'turn' } = $props();

	let resolvedItems = $derived(
		(Array.isArray(activities) ? activities : [])
			.map((activity) => ({
				activity,
				Component: activityRegistry.resolve(activity)
			}))
			.filter((entry) => Boolean(entry.Component))
	);
</script>

{#if resolvedItems.length > 0}
	<div class="agui-activity-stack" data-variant={variant}>
		{#each resolvedItems as { activity, Component }, idx (`${activity.messageId ?? activity.activityType}-${idx}`)}
			<Component content={activity.content ?? {}} {variant} />
		{/each}
	</div>
{/if}

<style>
	.agui-activity-stack {
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}
</style>
