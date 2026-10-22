<script>
	import {
		Cpu,
		Layers,
		Minus,
		Plus,
		Server,
		Activity,
		ShieldCheck,
		ExternalLink,
		Gauge
	} from '@lucide/svelte';

	let { clusterState, isStreaming = false, onAdjustReplicas, onTriggerSkill } = $props();

	const CANONICAL_SERVICE_ORDER = ['payment-gateway', 'order-processor', 'identity-provider'];
	let orderedServices = $derived(
		[...(clusterState?.services ?? [])].sort((a, b) => {
			const idxA = CANONICAL_SERVICE_ORDER.indexOf(a.serviceName);
			const idxB = CANONICAL_SERVICE_ORDER.indexOf(b.serviceName);
			if (idxA !== -1 && idxB !== -1) return idxA - idxB;
			if (idxA !== -1) return -1;
			if (idxB !== -1) return 1;
			return (a.serviceName ?? '').localeCompare(b.serviceName ?? '');
		})
	);

	function getStatusColor(status) {
		switch (status) {
			case 'HEALTHY':
				return {
					badge: 'bg-emerald-950/80 text-emerald-300 ring-emerald-500/30 border-emerald-500/20',
					dot: 'bg-emerald-400',
					text: 'text-emerald-400'
				};
			case 'DEGRADED':
				return {
					badge: 'bg-rose-950/80 text-rose-300 ring-rose-500/30 border-rose-500/20',
					dot: 'bg-rose-400',
					text: 'text-rose-400'
				};
			default:
				return {
					badge: 'bg-amber-950/80 text-amber-300 ring-amber-500/30 border-amber-500/20',
					dot: 'bg-amber-400',
					text: 'text-amber-400'
				};
		}
	}
</script>

<aside
	class="workbench-sidebar cyber-card flex flex-col justify-between overflow-hidden rounded-2xl p-4 sm:p-5"
>
	<div class="space-y-4">
		<!-- Panel Header -->
		<div class="flex items-center justify-between border-b border-slate-800/80 pb-3">
			<div class="flex items-center gap-2">
				<div class="rounded-lg bg-cyan-950/50 p-1.5 ring-1 ring-cyan-500/30">
					<Server class="size-4 text-cyan-400" />
				</div>
				<div>
					<h2 class="text-xs font-bold tracking-wider text-slate-200 uppercase">Cluster State</h2>
					<div class="font-mono text-[10px] text-slate-400">MCP Gateway (:8081)</div>
				</div>
			</div>
			<div class="flex items-center gap-1.5 rounded-full bg-slate-950 px-2.5 py-1 font-mono text-[10px] text-cyan-400 ring-1 ring-cyan-500/20">
				<span class="size-1.5 rounded-full bg-cyan-400 animate-pulse-dot"></span>
				<span>STATE_DELTA</span>
			</div>
		</div>

		<!-- Workload Cards -->
		<div class="space-y-2.5">
			{#each orderedServices as svc (svc.serviceName)}
				{@const st = getStatusColor(svc.status)}
				<div
					class="group relative overflow-hidden rounded-xl bg-slate-950/60 p-3.5 ring-1 ring-slate-800/80 transition-all hover:bg-slate-900/60 hover:ring-cyan-500/30"
				>
					<div class="flex items-center justify-between">
						<span class="font-mono text-xs font-semibold text-slate-200">{svc.serviceName}</span>
						<span
							class="inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 font-mono text-[10px] font-medium ring-1 {st.badge}"
						>
							<span class="size-1.5 rounded-full {st.dot}"></span>
							{svc.status}
						</span>
					</div>

					<!-- Telemetry Metrics & Scale Controls -->
					<div class="mt-3 flex items-center justify-between">
						<!-- Replicas -->
						<div class="flex items-center gap-2">
							<span class="font-mono text-[11px] text-slate-400">Pods:</span>
							<div class="flex items-center gap-1 rounded-lg bg-slate-900 px-1 py-0.5 ring-1 ring-slate-800">
								<button
									type="button"
									disabled={isStreaming || svc.replicas <= 1}
									aria-label="Decrease replicas for {svc.serviceName}"
									onclick={() => onAdjustReplicas?.(svc.serviceName, -1)}
									class="rounded p-1 text-slate-400 transition-colors hover:bg-slate-800 hover:text-white disabled:opacity-30"
								>
									<Minus class="size-2.5" />
								</button>
								<span class="min-w-4 text-center font-mono text-xs font-bold text-cyan-300">
									{svc.replicas}
								</span>
								<button
									type="button"
									disabled={isStreaming}
									aria-label="Increase replicas for {svc.serviceName}"
									onclick={() => onAdjustReplicas?.(svc.serviceName, 1)}
									class="rounded p-1 text-slate-400 transition-colors hover:bg-slate-800 hover:text-white disabled:opacity-30"
								>
									<Plus class="size-2.5" />
								</button>
							</div>
						</div>

						<!-- CPU Usage Gauge -->
						<div class="flex items-center gap-1.5 font-mono text-[11px] text-slate-400">
							<Gauge class="size-3 text-slate-500" />
							<span>CPU:</span>
							<span class="font-bold {svc.cpuPercent > 70 ? 'text-amber-400' : 'text-emerald-400'}">
								{svc.cpuPercent}%
							</span>
						</div>
					</div>
				</div>
			{/each}
		</div>

		<!-- A2A Autonomous Specialist Delegation Trigger Section -->
		<div class="pt-2">
			<div class="mb-2.5 flex items-center gap-2">
				<div class="rounded-lg bg-purple-950/50 p-1.5 ring-1 ring-purple-500/30">
					<Layers class="size-3.5 text-purple-400" />
				</div>
				<div>
					<h3 class="text-xs font-bold tracking-wider text-slate-200 uppercase">Sentinel A2A Agent</h3>
					<div class="font-mono text-[10px] text-slate-400">Specialist Server (:8082)</div>
				</div>
			</div>

			<div class="space-y-1.5">
				<button
					type="button"
					disabled={isStreaming}
					onclick={() => onTriggerSkill?.('Audit Clean Architecture concentric rings')}
					class="group flex w-full items-center justify-between rounded-xl bg-slate-950/60 px-3 py-2 text-left font-mono text-xs text-slate-300 ring-1 ring-slate-800/80 transition-all hover:bg-purple-950/30 hover:text-purple-200 hover:ring-purple-500/40 disabled:opacity-40"
				>
					<span class="truncate">audit-clean-architecture</span>
					<ShieldCheck class="size-3.5 text-slate-500 transition-colors group-hover:text-purple-400" />
				</button>

				<button
					type="button"
					disabled={isStreaming}
					onclick={() => onTriggerSkill?.('Assess runtime security posture and CVEs')}
					class="group flex w-full items-center justify-between rounded-xl bg-slate-950/60 px-3 py-2 text-left font-mono text-xs text-slate-300 ring-1 ring-slate-800/80 transition-all hover:bg-purple-950/30 hover:text-purple-200 hover:ring-purple-500/40 disabled:opacity-40"
				>
					<span class="truncate">assess-security-posture</span>
					<ShieldCheck class="size-3.5 text-slate-500 transition-colors group-hover:text-purple-400" />
				</button>

				<button
					type="button"
					disabled={isStreaming}
					onclick={() => onTriggerSkill?.('Plan incident remediation for degraded services')}
					class="group flex w-full items-center justify-between rounded-xl bg-slate-950/60 px-3 py-2 text-left font-mono text-xs text-slate-300 ring-1 ring-slate-800/80 transition-all hover:bg-purple-950/30 hover:text-purple-200 hover:ring-purple-500/40 disabled:opacity-40"
				>
					<span class="truncate">plan-incident-remediation</span>
					<ShieldCheck class="size-3.5 text-slate-500 transition-colors group-hover:text-purple-400" />
				</button>
			</div>
		</div>
	</div>

	<!-- Status Footer -->
	<div class="mt-4 flex items-center justify-between border-t border-slate-800/80 pt-3 font-mono text-[10px] text-slate-400">
		<span>Protocol: A2A-JSONRPC</span>
		<span class="text-emerald-400">Connected</span>
	</div>
</aside>
