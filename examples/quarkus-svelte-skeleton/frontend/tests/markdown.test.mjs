import test from 'node:test';
import assert from 'node:assert/strict';
import { renderMarkdownToHtml } from '../src/lib/markdown.js';

test('renderMarkdownToHtml formats headings and bold text', () => {
	const md = '# Kyoto Cultural Dossier\n**Atmosphere:** Clear Cedar & Autumn Mist.';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('<h1'));
	assert.ok(html.includes('Kyoto Cultural Dossier</h1>'));
	assert.ok(html.includes('<strong class="font-semibold text-[#141413]">Atmosphere:</strong>'));
});

test('renderMarkdownToHtml wraps code blocks with language badge and copy action', () => {
	const md = '```yaml\ndestination: Kyoto\nnights: 4\n```';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('YAML</span>'));
	assert.ok(html.includes('nights: 4'));
	assert.ok(html.includes('copy-code-btn'));
});

test('renderMarkdownToHtml converts markdown tables cleanly with tabular-nums on numeric/cost cells', () => {
	const md = '| Category | Estimated Cost |\n|---|---|\n| Accommodation | $150/night |\n| Total | $600 |';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('<table'));
	assert.ok(html.includes('<th'));
	assert.ok(html.includes('Accommodation'));
	assert.ok(html.includes('tabular-nums'));
});
