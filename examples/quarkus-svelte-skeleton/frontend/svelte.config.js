import adapter from '@sveltejs/adapter-static';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	kit: {
		alias: {
			'@agui/svelte': '../../../packages/agui-svelte/src/index.js'
		},
		adapter: adapter({
			pages: 'build',
			assets: 'build',
			fallback: 'index.html',
			precompress: false,
			strict: true
		})
	}
};

export default config;
