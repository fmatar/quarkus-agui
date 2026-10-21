<script>
	import { Layers, Minus, Plus, Server } from '@lucide/svelte';

	let { clusterState, isStreaming = false, onAdjustReplicas, onTriggerSkill } = $props();
</script>

<aside
	class="workbench-sidebar workbench-pane flex flex-col gap-3 rounded-xl border border-slate-800 bg-slate-900/80 p-4"
>
	<div class="flex items-center justify-between">
		<div class="flex items-center gap-2 text-xs font-semibold tracking-wider text-cyan-400 uppercase">
			<Server class="size-4" />
			<span>MCP Cluster State (:8081)</span>
		</div>
		<span class="rounded bg-cyan-950/80 px-1.5 py-0.5 font-mono text-[10px] text-cyan-300">
			STATE_DELTA
		</span>
	</div>

	<div class="space-y-2">
		{#each clusterState?.services ?? [] as svc (svc.serviceName)}
			<div class="rounded-lg border border-slate-800 bg-slate-950/70 p-2.5 text-xs">
				<div class="flex items-center justify-between font-semibold text-slate-200">
					<span>{svc.serviceName}</span>
					<span
						class="rounded px-1.5 py-0.5 text-[10px] {svc.status === 'HEALTHY'
							? 'bg-emerald-950 text-emerald-300'
							: 'bg-amber-950 text-amber-300'}"
					>
						{svc.status}
					</span>
				</div>
				<div class="mt-1.5 flex items-center justify-between font-mono text-[11px] text-slate-400">
					<div class="flex items-center gap-1.5">
						<span>Replicas: <strong class="text-cyan-300">{svc.replicas}</strong></span>
						<button
							type="button"
							disabled={isStreaming || svc.replicas <= 1}
							aria-label="Decrease replicas for {svc.serviceName}"
							onclick={() => onAdjustReplicas?.(svc.serviceName, -1)}
							class="rounded border border-slate-700 bg-slate-900 p-0.5 text-slate-300 hover:border-cyan-500 disabled:opacity-40"
						>
							<Minus class="size-3" />
						</button>
						<button
							type="button"
							disabled={isStreaming}
							aria-label="Increase replicas for {svc.serviceName}"
							onclick={() => onAdjustReplicas?.(svc.serviceName, 1)}
							class="rounded border border-slate-700 bg-slate-900 p-0.5 text-slate-300 hover:border-cyan-500 disabled:opacity-40"
						>
							<Plus class="size-3" />
						</button>
					</div>
					<span>CPU: {svc.cpuPercent}%</span>
				</div>
			</div>
		{/each}
	</div>

	<div class="mt-2 flex items-center gap-2 text-xs font-semibold tracking-wider text-purple-400 uppercase">
		<Layers class="size-4" />
		<span>A2A Specialist (:8082)</span>
	</div>
	<div class="space-y-1.5 text-xs text-slate-300">
		<button
			type="button"
			disabled={isStreaming}
			onclick={() => onTriggerSkill?.('Audit Clean Architecture concentric rings')}
			class="w-full rounded bg-slate-800/80 px-2.5 py-1.5 text-left font-mono hover:bg-purple-950/60 hover:text-purple-200 disabled:opacity-50"
		>
			audit-clean-architecture
		</button>
		<button
			type="button"
			disabled={isStreaming}
			onclick={() => onTriggerSkill?.('Assess runtime security posture and CVEs')}
			class="w-full rounded bg-slate-800/80 px-2.5 py-1.5 text-left font-mono hover:bg-purple-950/60 hover:text-purple-200 disabled:opacity-50"
		>
			assess-security-posture
		</button>
		<button
			type="button"
			disabled={isStreaming}
			onclick={() => onTriggerSkill?.('Plan incident remediation for degraded services')}
			class="w-full rounded bg-slate-800/80 px-2.5 py-1.5 text-left font-mono hover:bg-purple-950/60 hover:text-purple-200 disabled:opacity-50"
		>
			plan-incident-remediation
		</button>
	</div>
</aside>
