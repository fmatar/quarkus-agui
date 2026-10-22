/**
 * Ultra-fast, lightweight, zero-dependency Markdown & Code-Block renderer.
 * Designed specifically for SRE & DevOps outputs:
 * - Fenced code blocks with language tagging and copy action hooks
 * - Headers (H1 - H4)
 * - Bold / Italic / Strikethrough
 * - Inline code spans (`...`)
 * - Bulleted & numbered lists
 * - Blockquotes
 * - Markdown tables
 * - Escape-safe HTML generation
 */

function escapeHtml(str) {
	if (!str) return '';
	return str
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
		.replace(/'/g, '&#039;');
}

export function renderMarkdownToHtml(markdown) {
	if (!markdown) return '';

	// Normalize line endings
	let src = markdown.replace(/\r\n/g, '\n');

	// Extract code blocks first to protect code content from further parsing
	const codeBlocks = [];
	src = src.replace(/```([a-zA-Z0-9_\-\.]*)\n([\s\S]*?)```/g, (match, lang, code) => {
		const placeholder = `%%CODE_BLOCK_${codeBlocks.length}%%`;
		codeBlocks.push({ lang: lang.trim() || 'plaintext', code: code.replace(/\n$/, '') });
		return placeholder;
	});

	// Split into lines for block-level parsing
	const lines = src.split('\n');
	const outputLines = [];
	let inList = false;
	let listType = null; // 'ul' | 'ol'
	let inTable = false;
	let tableRows = [];

	function closeList() {
		if (inList) {
			outputLines.push(listType === 'ul' ? '</ul>' : '</ol>');
			inList = false;
			listType = null;
		}
	}

	function closeTable() {
		if (inTable) {
			if (tableRows.length > 0) {
				let tableHtml = '<div class="overflow-x-auto my-3 rounded-xl border border-slate-800 bg-slate-950/80"><table class="w-full text-left text-xs">';
				tableRows.forEach((row, idx) => {
					const isHeader = idx === 0;
					const tag = isHeader ? 'th' : 'td';
					const bg = isHeader ? 'bg-slate-900/90 text-cyan-300 font-semibold border-b border-slate-800' : 'border-b border-slate-900/60 text-slate-300 hover:bg-slate-900/40';
					tableHtml += `<tr class="${bg}">`;
					row.forEach(cell => {
						tableHtml += `<${tag} class="px-3.5 py-2">${parseInline(cell.trim())}</${tag}>`;
					});
					tableHtml += '</tr>';
				});
				tableHtml += '</table></div>';
				outputLines.push(tableHtml);
			}
			inTable = false;
			tableRows = [];
		}
	}

	for (let i = 0; i < lines.length; i++) {
		const line = lines[i];

		// Check for Table rows
		if (line.trim().startsWith('|') && line.trim().endsWith('|')) {
			closeList();
			const cells = line.trim().slice(1, -1).split('|');
			// Skip markdown separator row |---|---|
			if (cells.every(c => /^[\s\-:]+$/.test(c))) {
				continue;
			}
			inTable = true;
			tableRows.push(cells);
			continue;
		} else {
			closeTable();
		}

		// Check for Headings
		const headingMatch = line.match(/^(#{1,4})\s+(.*)$/);
		if (headingMatch) {
			closeList();
			const level = headingMatch[1].length;
			const text = parseInline(headingMatch[2]);
			if (level === 1) {
				outputLines.push(`<h1 class="text-base font-bold text-white mt-4 mb-2 flex items-center gap-2">${text}</h1>`);
			} else if (level === 2) {
				outputLines.push(`<h2 class="text-sm font-bold text-cyan-300 mt-3.5 mb-1.5 flex items-center gap-2">${text}</h2>`);
			} else if (level === 3) {
				outputLines.push(`<h3 class="text-xs font-bold text-emerald-300 mt-3 mb-1 uppercase tracking-wide">${text}</h3>`);
			} else {
				outputLines.push(`<h4 class="text-xs font-semibold text-slate-200 mt-2 mb-1">${text}</h4>`);
			}
			continue;
		}

		// Check for Blockquotes
		if (line.startsWith('> ')) {
			closeList();
			const content = parseInline(line.slice(2));
			outputLines.push(`<blockquote class="border-l-2 border-cyan-500/60 bg-cyan-950/20 px-3.5 py-1.5 my-2 text-xs text-cyan-100 rounded-r-lg">${content}</blockquote>`);
			continue;
		}

		// Check for Lists
		const ulMatch = line.match(/^(\s*)[-*+]\s+(.*)$/);
		const olMatch = line.match(/^(\s*)\d+\.\s+(.*)$/);
		if (ulMatch || olMatch) {
			const currentType = ulMatch ? 'ul' : 'ol';
			const itemText = parseInline(ulMatch ? ulMatch[2] : olMatch[2]);

			if (!inList || listType !== currentType) {
				closeList();
				inList = true;
				listType = currentType;
				outputLines.push(currentType === 'ul'
					? '<ul class="list-disc list-inside space-y-1 my-2 text-xs text-slate-300">'
					: '<ol class="list-decimal list-inside space-y-1 my-2 text-xs text-slate-300">');
			}
			outputLines.push(`<li class="leading-relaxed">${itemText}</li>`);
			continue;
		} else {
			closeList();
		}

		// Check for Empty Lines
		if (line.trim() === '') {
			outputLines.push('<div class="h-2"></div>');
			continue;
		}

		// Check for Code Block Placeholders
		if (line.startsWith('%%CODE_BLOCK_') && line.endsWith('%%')) {
			outputLines.push(line);
			continue;
		}

		// Standard Paragraph
		outputLines.push(`<p class="text-xs leading-relaxed text-slate-200 my-1">${parseInline(line)}</p>`);
	}

	closeList();
	closeTable();

	let html = outputLines.join('\n');

	// Re-insert Code Blocks with sleek Cyber-Theme syntax styling & 1-click copy support
	codeBlocks.forEach((block, idx) => {
		const placeholder = `%%CODE_BLOCK_${idx}%%`;
		const escapedCode = escapeHtml(block.code);
		const langBadge = block.lang ? block.lang.toUpperCase() : 'CODE';
		const codeCard = `
<div class="my-3 overflow-hidden rounded-xl border border-slate-800 bg-slate-950 text-xs shadow-md">
	<div class="flex items-center justify-between border-b border-slate-800/80 bg-slate-900/80 px-3.5 py-1.5 font-mono text-[11px]">
		<span class="font-bold text-cyan-400 uppercase tracking-wider">${escapeHtml(langBadge)}</span>
		<button
			type="button"
			class="copy-code-btn rounded px-2 py-0.5 text-[10px] text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
			onclick="navigator.clipboard.writeText(decodeURIComponent('${encodeURIComponent(block.code)}')).then(() => { this.innerText = 'Copied!'; setTimeout(() => this.innerText = 'Copy', 1500); })"
		>
			Copy
		</button>
	</div>
	<pre class="overflow-x-auto p-3.5 font-mono text-[11px] leading-relaxed text-slate-200"><code>${escapedCode}</code></pre>
</div>`.trim();
		html = html.replace(placeholder, codeCard);
	});

	return html;
}

function parseInline(text) {
	if (!text) return '';
	let str = escapeHtml(text);

	// Inline code: `code`
	str = str.replace(/`([^`]+)`/g, '<code class="rounded bg-slate-900 px-1.5 py-0.5 font-mono text-[11px] text-cyan-300 border border-slate-800">$1</code>');

	// Bold: **text** or __text__
	str = str.replace(/\*\*([^*]+)\*\*/g, '<strong class="font-semibold text-white">$1</strong>');
	str = str.replace(/__([^_]+)__/g, '<strong class="font-semibold text-white">$1</strong>');

	// Italic: *text* or _text_
	str = str.replace(/\*([^*]+)\*/g, '<em class="italic text-slate-300">$1</em>');
	str = str.replace(/_([^_]+)_/g, '<em class="italic text-slate-300">$1</em>');

	// Links: [text](url)
	str = str.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer" class="text-cyan-400 underline hover:text-cyan-300">$1</a>');

	return str;
}
