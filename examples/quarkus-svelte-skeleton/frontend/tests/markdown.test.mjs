import test from 'node:test';
import assert from 'node:assert/strict';
import { renderMarkdownToHtml } from '../src/lib/markdown.js';

test('renderMarkdownToHtml formats headings and bold text', () => {
	const md = '# SRE Audit Report\n**Status:** All pods healthy.';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('<h1'));
	assert.ok(html.includes('SRE Audit Report</h1>'));
	assert.ok(html.includes('<strong class="font-semibold text-white">Status:</strong>'));
});

test('renderMarkdownToHtml wraps code blocks with language badge and copy action', () => {
	const md = '```yaml\nreplicas: 4\nservice: payment-gateway\n```';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('YAML</span>'));
	assert.ok(html.includes('replicas: 4'));
	assert.ok(html.includes('copy-code-btn'));
});

test('renderMarkdownToHtml converts markdown tables cleanly', () => {
	const md = '| Service | Replicas | Status |\n|---|---|---|\n| api-gateway | 3 | HEALTHY |';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('<table'));
	assert.ok(html.includes('<th'));
	assert.ok(html.includes('api-gateway'));
});
