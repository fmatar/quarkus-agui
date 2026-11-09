import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const showcaseDir = path.resolve(__dirname, '../..');

test('Docker Compose topology uses Jib-built images for mcp-server, a2a-server, and unified Quinoa backend on :8080', () => {
	const composePath = path.join(showcaseDir, 'docker-compose.yml');
	assert.equal(fs.existsSync(composePath), true, 'docker-compose.yml should exist in examples/quarkus-svelte-skeleton');

	const content = fs.readFileSync(composePath, 'utf8');
	assert.match(content, /mcp-server:/);
	assert.match(content, /a2a-server:/);
	assert.match(content, /backend:/);
	assert.doesNotMatch(content, /^\s*frontend:/m);
	assert.doesNotMatch(content, /5173:/);
	assert.match(content, /nomad-agui-showcase\/mcp-server:latest/);
	assert.match(content, /nomad-agui-showcase\/a2a-server:latest/);
	assert.match(content, /nomad-agui-showcase\/backend:latest/);
	assert.match(content, /8081:8081/);
	assert.match(content, /8082:8082/);
	assert.match(content, /8080:8080/);
	assert.match(content, /AGUI_A2A_BASE_URL=http:\/\/a2a-server:8082/);
	assert.match(content, /AGUI_A2A_PUBLIC_URL=http:\/\/a2a-server:8082/);
	assert.match(content, /AGUI_MCP_BASE_URL=http:\/\/mcp-server:8081/);
});

test('Quinoa SPA static adapter, Jib container extensions, -Pnative profile, and run-showcase.sh are configured while legacy Dockerfiles are removed', () => {
	const quarkusDockerfile = path.join(showcaseDir, 'Dockerfile.quarkus');
	const frontendDockerfile = path.join(showcaseDir, 'frontend', 'Dockerfile');
	const frontendDockerignore = path.join(showcaseDir, 'frontend', '.dockerignore');
	const launcherScript = path.join(showcaseDir, 'run-showcase.sh');

	assert.equal(fs.existsSync(quarkusDockerfile), false, 'Legacy Dockerfile.quarkus should be removed in favor of Jib');
	assert.equal(fs.existsSync(frontendDockerfile), false, 'Legacy frontend/Dockerfile should be removed in favor of Quinoa');
	assert.equal(fs.existsSync(frontendDockerignore), false, 'Legacy frontend/.dockerignore should be removed');
	assert.equal(fs.existsSync(launcherScript), true, 'run-showcase.sh should exist');

	const svelteConfig = fs.readFileSync(path.join(showcaseDir, 'frontend', 'svelte.config.js'), 'utf8');
	assert.match(svelteConfig, /@sveltejs\/adapter-static/);
	assert.match(svelteConfig, /fallback:\s*['"]index\.html['"]/);

	const layoutJsPath = path.join(showcaseDir, 'frontend', 'src', 'routes', '+layout.js');
	assert.equal(fs.existsSync(layoutJsPath), true, '+layout.js should exist for SPA mode');
	const layoutJs = fs.readFileSync(layoutJsPath, 'utf8');
	assert.match(layoutJs, /ssr\s*=\s*false/);

	const parentPom = fs.readFileSync(path.join(showcaseDir, 'pom.xml'), 'utf8');
	assert.match(parentPom, /quarkus-quinoa/);
	assert.match(parentPom, /quarkus-container-image-jib/);
	assert.match(parentPom, /<id>native<\/id>/);

	const backendPom = fs.readFileSync(path.join(showcaseDir, 'backend', 'pom.xml'), 'utf8');
	assert.match(backendPom, /quarkus-quinoa/);
	assert.match(backendPom, /quarkus-container-image-jib/);

	const mcpPom = fs.readFileSync(path.join(showcaseDir, 'mcp-server', 'pom.xml'), 'utf8');
	assert.match(mcpPom, /quarkus-container-image-jib/);

	const a2aPom = fs.readFileSync(path.join(showcaseDir, 'a2a-server', 'pom.xml'), 'utf8');
	assert.match(a2aPom, /quarkus-container-image-jib/);

	const backendProps = fs.readFileSync(path.join(showcaseDir, 'backend', 'src', 'main', 'resources', 'application.properties'), 'utf8');
	assert.match(backendProps, /quarkus\.quinoa\.ui-dir=\.\.\/frontend/);
	assert.match(backendProps, /quarkus\.quinoa\.package-manager=pnpm/);
	assert.match(backendProps, /quarkus\.quinoa\.enable-spa-routing=true/);
	assert.match(backendProps, /quarkus\.container-image\.builder=jib/);
	assert.match(backendProps, /quarkus\.container-image\.group=nomad-agui-showcase/);
	assert.match(backendProps, /quarkus\.container-image\.name=backend/);

	const launcherContent = fs.readFileSync(launcherScript, 'utf8');
	assert.match(launcherContent, /quarkus\.container-image\.build=true/);
	assert.match(launcherContent, /-pl :quarkus-ag-ui-deployment,:quarkus-ag-ui-bom -am/);
	assert.doesNotMatch(launcherContent, /quarkus-extension\/common/);
	assert.match(launcherContent, /--docker-native/);
	assert.match(launcherContent, /http:\/\/localhost:8080/);
});
