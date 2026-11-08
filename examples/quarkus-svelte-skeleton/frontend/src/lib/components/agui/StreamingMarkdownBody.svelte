<script>
	import { renderMarkdownToHtml } from '$lib/markdown.js';

	let { content = '', isStreaming = false } = $props();
</script>

{#if content}
	<div class="nomad-markdown max-w-[72ch] text-sm leading-relaxed text-[#141413] space-y-2.5">
		{@html renderMarkdownToHtml(content)}
		{#if isStreaming}
			<span
				class="ml-1 inline-block size-2 rounded-full bg-[#C84B31] align-baseline animate-pulse-dot"
				aria-hidden="true"
			></span>
		{/if}
	</div>
{/if}
