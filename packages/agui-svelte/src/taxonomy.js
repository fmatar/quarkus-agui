import { EventType } from '@ag-ui/core';

export const PROTOCOL_CALL_TAXONOMY = {
	'tool:function': {
		callType: 'tool:function',
		label: 'Local Function',
		family: 'LOCAL',
		iconName: 'Wrench',
		badgeClass: 'badge-local'
	},
	'tool:ui': {
		callType: 'tool:ui',
		label: 'AG-UI Surface',
		family: 'AG-UI',
		iconName: 'LayoutGrid',
		badgeClass: 'badge-ui'
	},
	'agui:activity': {
		callType: 'agui:activity',
		label: 'AG-UI Activity Card',
		family: 'AG-UI',
		iconName: 'LayoutDashboard',
		badgeClass: 'badge-ui'
	},
	'mcp:tool': {
		callType: 'mcp:tool',
		label: 'MCP Tool',
		family: 'MCP',
		iconName: 'Hammer',
		badgeClass: 'badge-mcp'
	},
	'mcp:resource': {
		callType: 'mcp:resource',
		label: 'MCP Resource',
		family: 'MCP',
		iconName: 'Database',
		badgeClass: 'badge-mcp'
	},
	'mcp:prompt': {
		callType: 'mcp:prompt',
		label: 'MCP Prompt',
		family: 'MCP',
		iconName: 'FileText',
		badgeClass: 'badge-mcp'
	},
	'mcp:sampling': {
		callType: 'mcp:sampling',
		label: 'MCP Sampling',
		family: 'MCP',
		iconName: 'Cpu',
		badgeClass: 'badge-mcp'
	},
	'a2a:discovery': {
		callType: 'a2a:discovery',
		label: 'A2A Discovery',
		family: 'A2A',
		iconName: 'Compass',
		badgeClass: 'badge-a2a'
	},
	'a2a:task': {
		callType: 'a2a:task',
		label: 'A2A Task',
		family: 'A2A',
		iconName: 'Network',
		badgeClass: 'badge-a2a'
	},
	'a2a:artifact': {
		callType: 'a2a:artifact',
		label: 'A2A Artifact',
		family: 'A2A',
		iconName: 'FileCheck2',
		badgeClass: 'badge-a2a'
	},
	'hitl:approval': {
		callType: 'hitl:approval',
		label: 'HITL Gate',
		family: 'HITL',
		iconName: 'ShieldAlert',
		badgeClass: 'badge-hitl'
	},
	'guardrail:input': {
		callType: 'guardrail:input',
		label: 'Input Guardrail',
		family: 'GUARDRAIL',
		iconName: 'ShieldCheck',
		badgeClass: 'badge-a2a'
	},
	'guardrail:output': {
		callType: 'guardrail:output',
		label: 'Output Guardrail',
		family: 'GUARDRAIL',
		iconName: 'ShieldCheck',
		badgeClass: 'badge-a2a'
	},
	'rag:retrieval': {
		callType: 'rag:retrieval',
		label: 'Cultural RAG',
		family: 'RAG',
		iconName: 'BookOpen',
		badgeClass: 'badge-mcp'
	},
	'agentic:specialist': {
		callType: 'agentic:specialist',
		label: 'Specialist Council',
		family: 'AGENTIC',
		iconName: 'Sparkles',
		badgeClass: 'badge-ui'
	}
};

const FAMILY_FALLBACK_RULES = [
	['GUARDRAIL', 'guardrail:input'],
	['RAG', 'rag:retrieval'],
	['AGENTIC', 'agentic:specialist'],
	['A2A', 'a2a:task'],
	['HITL', 'hitl:approval'],
	['LOCAL', 'tool:function'],
	['AG-UI', 'agui:activity'],
	['UI', 'tool:ui']
];

const MCP_EVENT_TYPES = new Set([
	EventType.TOOL_CALL_START,
	EventType.TOOL_CALL_ARGS,
	EventType.TOOL_CALL_END,
	EventType.TOOL_CALL_CHUNK,
	EventType.TOOL_CALL_RESULT,
	'TOOL_CALL',
	'TOOL_RESULT'
]);

const A2A_EVENT_TYPES = new Set([EventType.STEP_STARTED, EventType.STEP_FINISHED]);
const AGUI_ACTIVITY_EVENT_TYPES = new Set([
	EventType.ACTIVITY_SNAPSHOT,
	EventType.ACTIVITY_DELTA
]);

function extractProvenanceSource(event) {
	if (event?.rawEvent && typeof event.rawEvent === 'object') return event.rawEvent;
	if (event?.metadata && typeof event.metadata === 'object') return event.metadata;
	if (event?.payload && typeof event.payload === 'object') return event.payload;
	return {};
}

export function resolveProtocolCallMeta(callTypeOrEvent, fallbackProtocol = 'MCP') {
	if (callTypeOrEvent && typeof callTypeOrEvent === 'object') {
		const prov = extractProvenanceSource(callTypeOrEvent);
		return resolveProtocolCallMeta(prov.callType, prov.protocol ?? fallbackProtocol);
	}
	if (callTypeOrEvent && PROTOCOL_CALL_TAXONOMY[callTypeOrEvent]) {
		return PROTOCOL_CALL_TAXONOMY[callTypeOrEvent];
	}
	const upper = String(fallbackProtocol ?? 'MCP').toUpperCase();
	const matched = FAMILY_FALLBACK_RULES.find(([prefix]) => upper.includes(prefix));
	return PROTOCOL_CALL_TAXONOMY[matched ? matched[1] : 'mcp:tool'];
}

export function resolveEventProtocolMeta(event) {
	const prov = extractProvenanceSource(event);
	let defaultFamily = 'MCP';
	if (A2A_EVENT_TYPES.has(event?.type) || event?.activityType === 'a2a.task') {
		defaultFamily = 'A2A';
	} else if (AGUI_ACTIVITY_EVENT_TYPES.has(event?.type)) {
		defaultFamily = 'AG-UI';
	}
	const taxonomy = resolveProtocolCallMeta(prov.callType, prov.protocol ?? defaultFamily);
	return {
		...taxonomy,
		protocol: prov.protocol ?? taxonomy.family,
		server: prov.server ?? null,
		agentName: prov.agentName ?? null,
		skillId: prov.skillId ?? null
	};
}

function classifyInspectorFamily(event) {
	if (event?.direction === 'client') return 'CLIENT';
	const prov = extractProvenanceSource(event);
	const explicitProto = typeof prov.protocol === 'string' ? prov.protocol.toUpperCase() : '';
	if (explicitProto === 'A2A' || A2A_EVENT_TYPES.has(event?.type) || event?.activityType === 'a2a.task') {
		return 'A2A';
	}
	if (explicitProto === 'MCP' || MCP_EVENT_TYPES.has(event?.type)) {
		return 'MCP';
	}
	return 'AG-UI';
}

export function filterInspectorEvents(events, filter = 'ALL') {
	if (!Array.isArray(events)) return [];
	if (filter === 'ALL') return events;
	return events.filter((event) => classifyInspectorFamily(event) === filter);
}
