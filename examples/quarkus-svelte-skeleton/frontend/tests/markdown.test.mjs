import test from 'node:test';
import assert from 'node:assert/strict';
import { renderMarkdownToHtml } from '../src/lib/markdown.js';

test('renderMarkdownToHtml formats headings H1-H4, bold, italic, inline code, blockquotes, lists, links, and empty input', () => {
	assert.equal(renderMarkdownToHtml(''), '');
	assert.equal(renderMarkdownToHtml(null), '');

	const md = [
		'# Kyoto Cultural Dossier',
		'## Day 1',
		'### Morning',
		'#### Sanctuary',
		'> Quiet cedar courtyard',
		'',
		'- First *italic* stop with `code`',
		'- Second __bold__ _underscored_ [Wikipedia](https://en.wikipedia.org)',
		'1. Ordered step one',
		'2. Ordered step two',
		'Plain paragraph with <unsafe> & "quotes"'
	].join('\r\n');

	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('<h1'));
	assert.ok(html.includes('<h2'));
	assert.ok(html.includes('<h3'));
	assert.ok(html.includes('<h4'));
	assert.ok(html.includes('<blockquote'));
	assert.ok(html.includes('<ul'));
	assert.ok(html.includes('<ol'));
	assert.ok(html.includes('<code'));
	assert.ok(html.includes('<a href="https://en.wikipedia.org"'));
	assert.ok(html.includes('&lt;unsafe&gt;'));
	assert.ok(html.includes('&quot;quotes&quot;'));
});

test('renderMarkdownToHtml wraps code blocks with language badge and copy action', () => {
	const md = '```yaml\ndestination: Kyoto\nnights: 4\n```\n```\nplain block\n```';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('YAML</span>'));
	assert.ok(html.includes('PLAINTEXT</span>'));
	assert.ok(html.includes('nights: 4'));
	assert.ok(html.includes('copy-code-btn'));
});

test('renderMarkdownToHtml converts markdown tables cleanly with tabular-nums on numeric/cost cells', () => {
	const md =
		'| $100 First Col | Description | Estimated Cost |\n|---|---|---|\n| Accommodation | Cedar Suite | $150/night |\n| Total | Summary | $600 |\n## Post Table Heading';
	const html = renderMarkdownToHtml(md);
	assert.ok(html.includes('<table'));
	assert.ok(html.includes('<th class="px-4 py-2.5 ">$100 First Col</th>'));
	assert.ok(html.includes('<td class="px-4 py-2.5 ">Cedar Suite</td>'));
	assert.ok(html.includes('<td class="px-4 py-2.5 text-right font-mono font-medium text-[#141413]">$600</td>'));
	assert.ok(html.includes('<h2'));
	assert.ok(html.includes('tabular-nums'));
});
