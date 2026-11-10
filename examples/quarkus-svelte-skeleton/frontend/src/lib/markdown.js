/**
 * Lightweight, zero-dependency, escape-safe Markdown & Code-Block renderer.
 */

const HEADING_CLASSES = {
	1: 'text-base font-bold text-[#141413] mt-4 mb-1.5 flex items-center gap-2',
	2: 'text-sm font-bold text-[#141413] mt-3.5 mb-1.5 flex items-center gap-2',
	3: 'text-sm font-bold text-[#141413] mt-3 mb-1',
	4: 'text-xs font-semibold text-[#141413] mt-2 mb-1'
};

const LIST_OPEN_TAGS = {
	ul: '<ul class="list-disc list-inside space-y-1 my-2 text-xs text-[#141413]">',
	ol: '<ol class="list-decimal list-inside space-y-1 my-2 text-xs text-[#141413]">'
};

function escapeHtml(str) {
	if (!str) return '';
	return str
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
		.replace(/'/g, '&#039;');
}

function extractFencedCodeBlocks(src) {
	const codeBlocks = [];
	const text = src.replace(/```([a-zA-Z0-9_\-.]*)\n([\s\S]*?)```/g, (_m, lang, code) => {
		const placeholder = `%%CODE_BLOCK_${codeBlocks.length}%%`;
		codeBlocks.push({ lang: lang.trim() || 'plaintext', code: code.replace(/\n$/, '') });
		return placeholder;
	});
	return { text, codeBlocks };
}

function closeActiveList(ctx) {
	if (!ctx.listType) return;
	ctx.output.push(`</${ctx.listType}>`);
	ctx.listType = null;
}

function closeActiveTable(ctx) {
	if (ctx.tableRows.length > 0) {
		ctx.output.push(renderTableHtml(ctx.tableRows));
	}
	ctx.tableRows = [];
}

function renderTableHtml(rows) {
	const renderedRows = rows.map((row, idx) => renderTableRow(row, idx === 0)).join('');
	return `<div class="overflow-x-auto my-3.5 rounded-xl border border-[#E4E0D8] bg-white shadow-2xs"><table class="w-full text-left text-xs tabular-nums">${renderedRows}</table></div>`;
}

function renderTableRow(cells, isHeader) {
	const tag = isHeader ? 'th' : 'td';
	const bg = isHeader
		? 'bg-[#F6F5F2] text-[#141413] font-semibold border-b border-[#E4E0D8]'
		: 'border-b border-[#E4E0D8] last:border-b-0 text-[#575551] hover:bg-[#FAF9F6]';
	const inner = cells.map((c, idx) => renderTableCell(tag, c.trim(), idx)).join('');
	return `<tr class="${bg}">${inner}</tr>`;
}

function renderTableCell(tag, trimmed, cellIdx) {
	const isNumeric = /^[$€¥£₹]?\s*\d|cost|budget|price|rate|total|amount/i.test(trimmed);
	const align = cellIdx > 0 && isNumeric ? 'text-right font-mono font-medium text-[#141413]' : '';
	return `<${tag} class="px-4 py-2.5 ${align}">${parseInline(trimmed)}</${tag}>`;
}

function tryConsumeTableLine(trimmed, ctx) {
	if (!trimmed.startsWith('|') || !trimmed.endsWith('|')) {
		closeActiveTable(ctx);
		return false;
	}
	closeActiveList(ctx);
	const cells = trimmed.slice(1, -1).split('|');
	if (!cells.every((c) => /^[\s\-:]+$/.test(c))) {
		ctx.tableRows.push(cells);
	}
	return true;
}

function tryConsumeHeadingOrQuote(line, ctx) {
	const heading = line.match(/^(#{1,4})\s+(.*)$/);
	if (heading) {
		closeActiveList(ctx);
		const level = heading[1].length;
		ctx.output.push(`<h${level} class="${HEADING_CLASSES[level]}">${parseInline(heading[2])}</h${level}>`);
		return true;
	}
	if (line.startsWith('> ')) {
		closeActiveList(ctx);
		ctx.output.push(
			`<blockquote class="border border-[#E4E0D8] bg-[#F6F5F2] px-3.5 py-2 my-2 text-xs text-[#575551] rounded-lg">${parseInline(line.slice(2))}</blockquote>`
		);
		return true;
	}
	return false;
}

function matchListLine(line) {
	const ul = line.match(/^(\s*)[-*+]\s+(.*)$/);
	if (ul) return { type: 'ul', content: ul[2] };
	const ol = line.match(/^(\s*)\d+\.\s+(.*)$/);
	return ol ? { type: 'ol', content: ol[2] } : null;
}

function tryConsumeListLine(line, ctx) {
	const item = matchListLine(line);
	if (!item) {
		closeActiveList(ctx);
		return false;
	}
	if (ctx.listType !== item.type) {
		closeActiveList(ctx);
		ctx.listType = item.type;
		ctx.output.push(LIST_OPEN_TAGS[item.type]);
	}
	ctx.output.push(`<li class="leading-relaxed">${parseInline(item.content)}</li>`);
	return true;
}

function consumeParagraphOrSpacer(line, trimmed, ctx) {
	if (trimmed === '') {
		ctx.output.push('<div class="h-2"></div>');
	} else if (line.startsWith('%%CODE_BLOCK_') && line.endsWith('%%')) {
		ctx.output.push(line);
	} else {
		ctx.output.push(
			`<p class="text-xs sm:text-sm leading-relaxed text-[#141413] my-1">${parseInline(line)}</p>`
		);
	}
}

function processSingleLine(line, ctx) {
	const trimmed = line.trim();
	if (tryConsumeTableLine(trimmed, ctx)) return;
	if (tryConsumeHeadingOrQuote(line, ctx)) return;
	if (tryConsumeListLine(line, ctx)) return;
	consumeParagraphOrSpacer(line, trimmed, ctx);
}

function restoreCodeBlocks(html, codeBlocks) {
	let result = html;
	codeBlocks.forEach((block, idx) => {
		const langBadge = escapeHtml(block.lang.toUpperCase());
		const escapedCode = escapeHtml(block.code);
		const card = [
			'<div class="my-3 overflow-hidden rounded-xl border border-[#E4E0D8] bg-[#F6F5F2] text-xs">',
			`\t<div class="flex items-center justify-between border-b border-[#E4E0D8] bg-[#EFECE6] px-3.5 py-1.5 font-mono text-[11px]">`,
			`\t\t<span class="font-bold text-[#141413] uppercase tracking-wider">${langBadge}</span>`,
			`\t\t<button type="button" class="copy-code-btn rounded px-2 py-0.5 text-[10px] text-[#575551] hover:bg-white hover:text-[#141413] transition-colors" onclick="navigator.clipboard.writeText(decodeURIComponent('${encodeURIComponent(block.code)}')).then(() => { this.innerText = 'Copied!'; setTimeout(() => this.innerText = 'Copy', 1500); })">Copy</button>`,
			`\t</div>`,
			`\t<pre class="overflow-x-auto p-3.5 font-mono text-[11px] leading-relaxed text-[#141413]"><code>${escapedCode}</code></pre>`,
			'</div>'
		].join('\n');
		result = result.replace(`%%CODE_BLOCK_${idx}%%`, card);
	});
	return result;
}

export function renderMarkdownToHtml(markdown) {
	if (!markdown) return '';
	const { text, codeBlocks } = extractFencedCodeBlocks(markdown.replace(/\r\n/g, '\n'));
	const ctx = { output: [], listType: null, tableRows: [] };
	for (const line of text.split('\n')) {
		processSingleLine(line, ctx);
	}
	closeActiveList(ctx);
	closeActiveTable(ctx);
	return restoreCodeBlocks(ctx.output.join('\n'), codeBlocks);
}

function parseInline(text) {
	if (!text) return '';
	return escapeHtml(text)
		.replace(
			/`([^`]+)`/g,
			'<code class="rounded bg-[#EFECE6] px-1.5 py-0.5 font-mono text-[11px] text-[#141413] border border-[#E4E0D8]">$1</code>'
		)
		.replace(/\*\*([^*]+)\*\*/g, '<strong class="font-semibold text-[#141413]">$1</strong>')
		.replace(/__([^_]+)__/g, '<strong class="font-semibold text-[#141413]">$1</strong>')
		.replace(/\*([^*]+)\*/g, '<em class="italic text-[#575551]">$1</em>')
		.replace(/_([^_]+)_/g, '<em class="italic text-[#575551]">$1</em>')
		.replace(
			/\[([^\]]+)\]\(([^)]+)\)/g,
			'<a href="$2" target="_blank" rel="noopener noreferrer" class="text-[#C84B31] underline hover:text-[#141413]">$1</a>'
		);
}
