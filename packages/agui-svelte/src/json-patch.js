/**
 * Immutable RFC 6902 JSON Patch (`add`, `replace`, `remove`) & RFC 6901 JSON Pointer helpers.
 */

const FORBIDDEN_POINTER_TOKENS = new Set(['__proto__', 'constructor', 'prototype']);

export function unescapeJsonPointerToken(token) {
	const raw = String(token ?? '');
	return raw.includes('~') ? raw.replace(/~1/g, '/').replace(/~0/g, '~') : raw;
}

function parsePointerSegments(pointer) {
	if (pointer === '') return [];
	const normalized = pointer.startsWith('/') ? pointer.slice(1) : pointer;
	return normalized.split('/').map(unescapeJsonPointerToken);
}

function cloneContainer(node) {
	if (Array.isArray(node)) return [...node];
	if (node && typeof node === 'object') return { ...node };
	return {};
}

function mutateLeaf(container, segment, op, value) {
	if (Array.isArray(container)) {
		if (op === 'add' && segment === '-') {
			container.push(value);
			return container;
		}
		const idx = Number.parseInt(segment, 10);
		if (op === 'add') {
			container.splice(idx, 0, value);
		} else if (op === 'replace') {
			container[idx] = value;
		} else if (op === 'remove') {
			container.splice(idx, 1);
		}
		return container;
	}
	if (op === 'remove') {
		delete container[segment];
	} else {
		container[segment] = value;
	}
	return container;
}

function applyAtSegments(node, segments, index, op, value) {
	const copy = cloneContainer(node);
	const segment = segments[index];
	if (index === segments.length - 1) {
		return mutateLeaf(copy, segment, op, value);
	}
	const nextChild = copy[segment];
	if (op === 'remove' && (nextChild === undefined || nextChild === null)) {
		return copy;
	}
	copy[segment] = applyAtSegments(nextChild, segments, index + 1, op, value);
	return copy;
}

export function applyJsonPatchOperation(state, operation) {
	if (!operation || typeof operation.op !== 'string' || typeof operation.path !== 'string') {
		return state;
	}
	const segments = parsePointerSegments(operation.path);
	if (segments.some((segment) => FORBIDDEN_POINTER_TOKENS.has(segment))) {
		return state;
	}
	if (segments.length === 0) {
		return operation.op === 'remove' ? {} : operation.value;
	}
	return applyAtSegments(state ?? {}, segments, 0, operation.op, operation.value);
}

export function applyJsonPatch(state, operations) {
	if (!Array.isArray(operations) || operations.length === 0) {
		return state;
	}
	let next = state;
	for (const op of operations) {
		next = applyJsonPatchOperation(next, op);
	}
	return next;
}
