import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const showcaseDir = path.resolve(__dirname, '../..');

test('Docker Compose topology defines mcp-server, a2a-server, backend, and frontend services', () => {
	const composePath = path.join(showcaseDir, 'docker-compose.yml');
	assert.equal(fs.existsSync(composePath), true, 'docker-compose.yml should exist in examples/quarkus-svelte-skeleton');

	const content = fs.readFileSync(composePath, 'utf8');
	assert.match(content, /mcp-server:/);
	assert.match(content, /a2a-server:/);
	assert.match(content, /backend:/);
	assert.match(content, /frontend:/);
	assert.match(content, /8081:8081/);
	assert.match(content, /8082:8082/);
	assert.match(content, /8080:8080/);
	assert.match(content, /5173:5173/);
	assert.match(content, /AGUI_A2A_BASE_URL=http:\/\/a2a-server:8082/);
	assert.match(content, /AGUI_MCP_BASE_URL=http:\/\/mcp-server:8081/);
});

test('Multi-stage Quarkus Dockerfile, Frontend Dockerfile, and run-showcase.sh exist', () => {
	const quarkusDockerfile = path.join(showcaseDir, 'Dockerfile.quarkus');
	const frontendDockerfile = path.join(showcaseDir, 'frontend', 'Dockerfile');
	const launcherScript = path.join(showcaseDir, 'run-showcase.sh');

	assert.equal(fs.existsSync(quarkusDockerfile), true, 'Dockerfile.quarkus should exist');
	assert.equal(fs.existsSync(frontendDockerfile), true, 'frontend/Dockerfile should exist');
	assert.equal(fs.existsSync(launcherScript), true, 'run-showcase.sh should exist');

	const quarkusContent = fs.readFileSync(quarkusDockerfile, 'utf8');
	assert.match(quarkusContent, /AS mcp-server/);
	assert.match(quarkusContent, /AS a2a-server/);
	assert.match(quarkusContent, /AS backend/);

	const repoRoot = path.resolve(showcaseDir, '../..');
	for (const requiredDir of ['ag-ui-core', 'quarkus-ag-ui-bom', 'quarkus-extension', 'examples']) {
		assert.equal(
			fs.existsSync(path.join(repoRoot, requiredDir)),
			true,
			`Repository root must contain ${requiredDir} for Dockerfile.quarkus`
		);
	}
});
