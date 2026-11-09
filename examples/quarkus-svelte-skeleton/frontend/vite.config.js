import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [tailwindcss(), sveltekit()],
	server: {
		host: '0.0.0.0',
		port: 5173,
		proxy: {
			'/api': {
				target: process.env.AGUI_BACKEND_PROXY_TARGET || 'http://127.0.0.1:8080',
				changeOrigin: true,
				ws: true
			},
			'/ag-ui': {
				target: process.env.AGUI_BACKEND_PROXY_TARGET || 'http://127.0.0.1:8080',
				changeOrigin: true,
				ws: true
			}
		}
	}
});
