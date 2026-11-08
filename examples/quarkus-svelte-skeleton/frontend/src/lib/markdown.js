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
				let tableHtml = '<div class="overflow-x-auto my-3 rounded-xl border border-[#E4E0D8] bg-white"><table class="w-full text-left text-xs">';
				tableRows.forEach((row, idx) => {
					const isHeader = idx === 0;
					const tag = isHeader ? 'th' : 'td';
					const bg = isHeader ? 'bg-[#F6F5F2] text-[#141413] font-semibold border-b border-[#E4E0D8]' : 'border-b border-[#E4E0D8] text-[#575551] hover:bg-[#FAF9F6]';
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
				outputLines.push(`<h1 class="text-base font-bold text-[#141413] mt-4 mb-1.5 flex items-center gap-2">${text}</h1>`);
			} else if (level === 2) {
				outputLines.push(`<h2 class="text-sm font-bold text-[#141413] mt-3.5 mb-1.5 flex items-center gap-2">${text}</h2>`);
			} else if (level === 3) {
				outputLines.push(`<h3 class="text-sm font-bold text-[#141413] mt-3 mb-1">${text}</h3>`);
			} else {
				outputLines.push(`<h4 class="text-xs font-semibold text-[#141413] mt-2 mb-1">${text}</h4>`);
			}
			continue;
		}

		// Check for Blockquotes
		if (line.startsWith('> ')) {
			closeList();
			const content = parseInline(line.slice(2));
			outputLines.push(`<blockquote class="border border-[#E4E0D8] bg-[#F6F5F2] px-3.5 py-2 my-2 text-xs text-[#575551] rounded-lg">${content}</blockquote>`);
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
					? '<ul class="list-disc list-inside space-y-1 my-2 text-xs text-[#141413]">'
					: '<ol class="list-decimal list-inside space-y-1 my-2 text-xs text-[#141413]">');
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
		outputLines.push(`<p class="text-xs sm:text-sm leading-relaxed text-[#141413] my-1">${parseInline(line)}</p>`);
	}

	closeList();
	closeTable();

	let html = outputLines.join('\n');

	// Re-insert Code Blocks with editorial precision styling & 1-click copy support
	codeBlocks.forEach((block, idx) => {
		const placeholder = `%%CODE_BLOCK_${idx}%%`;
		const escapedCode = escapeHtml(block.code);
		const langBadge = block.lang ? block.lang.toUpperCase() : 'CODE';
		const codeCard = `
<div class="my-3 overflow-hidden rounded-xl border border-[#E4E0D8] bg-[#F6F5F2] text-xs">
	<div class="flex items-center justify-between border-b border-[#E4E0D8] bg-[#EFECE6] px-3.5 py-1.5 font-mono text-[11px]">
		<span class="font-bold text-[#141413] uppercase tracking-wider">${escapeHtml(langBadge)}</span>
		<button
			type="button"
			class="copy-code-btn rounded px-2 py-0.5 text-[10px] text-[#575551] hover:bg-white hover:text-[#141413] transition-colors"
			onclick="navigator.clipboard.writeText(decodeURIComponent('${encodeURIComponent(block.code)}')).then(() => { this.innerText = 'Copied!'; setTimeout(() => this.innerText = 'Copy', 1500); })"
		>
			Copy
		</button>
	</div>
	<pre class="overflow-x-auto p-3.5 font-mono text-[11px] leading-relaxed text-[#141413]"><code>${escapedCode}</code></pre>
</div>`.trim();
		html = html.replace(placeholder, codeCard);
	});

	return html;
}

function parseInline(text) {
	if (!text) return '';
	let str = escapeHtml(text);

	// Inline code: `code`
	str = str.replace(/`([^`]+)`/g, '<code class="rounded bg-[#EFECE6] px-1.5 py-0.5 font-mono text-[11px] text-[#141413] border border-[#E4E0D8]">$1</code>');

	// Bold: **text** or __text__
	str = str.replace(/\*\*([^*]+)\*\*/g, '<strong class="font-semibold text-[#141413]">$1</strong>');
	str = str.replace(/__([^_]+)__/g, '<strong class="font-semibold text-[#141413]">$1</strong>');

	// Italic: *text* or _text_
	str = str.replace(/\*([^*]+)\*/g, '<em class="italic text-[#575551]">$1</em>');
	str = str.replace(/_([^_]+)_/g, '<em class="italic text-[#575551]">$1</em>');

	// Links: [text](url)
	str = str.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer" class="text-[#C84B31] underline hover:text-[#141413]">$1</a>');

	return str;
}
