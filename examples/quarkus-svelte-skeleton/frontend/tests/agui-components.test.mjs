import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const aguiDir = path.resolve(__dirname, '../src/lib/components/agui');

const REQUIRED_AGUI_COMPONENTS = [
	'AgUiWeatherCard.svelte',
	'AgUiLocationMapCard.svelte',
	'AgUiSecurityAdvisoryCard.svelte',
	'AgUiFxBudgetCard.svelte',
	'AgUiCultureAudioCard.svelte',
	'AgUiActivityRenderer.svelte'
];

test('Svelte 5 AG-UI surface kit components exist and honor semantic, accessibility, and craft-floor invariants', () => {
	for (const fileName of REQUIRED_AGUI_COMPONENTS) {
		const fullPath = path.join(aguiDir, fileName);
		assert.ok(fs.existsSync(fullPath), `Expected AG-UI component ${fileName} to exist`);
	}

	const mapSource = fs.readFileSync(path.join(aguiDir, 'AgUiLocationMapCard.svelte'), 'utf8');
	assert.ok(mapSource.includes('<article'), 'Map card must use semantic <article>');
	assert.ok(mapSource.includes('container-type: inline-size'), 'Map card must declare container-type: inline-size');
	assert.ok(mapSource.includes('<details'), 'Map card must use native <details> disclosure for OSM preview');
	assert.ok(mapSource.includes('<summary'), 'Map card must use native <summary> inside <details>');
	assert.ok(!/<summary[\s\S]*?<h[1-6]/i.test(mapSource), 'Must never nest headings inside <summary>');
	assert.ok(mapSource.includes('loading="lazy"'), 'Map iframe must use loading="lazy"');
	assert.ok(!/<iframe[^>]*aria-hidden="true"/i.test(mapSource), 'Focusable iframe must never carry aria-hidden="true"');
	assert.ok(mapSource.includes('Google Maps') && mapSource.includes('Apple Maps') && mapSource.includes('OpenStreetMap'));

	const securitySource = fs.readFileSync(path.join(aguiDir, 'AgUiSecurityAdvisoryCard.svelte'), 'utf8');
	assert.ok(securitySource.includes('<article'), 'Security card must use semantic <article>');
	assert.ok(securitySource.includes('container-type: inline-size'));
	assert.ok(securitySource.includes('safetyHighlights'));

	const fxSource = fs.readFileSync(path.join(aguiDir, 'AgUiFxBudgetCard.svelte'), 'utf8');
	assert.ok(fxSource.includes('--alloc-pct'), 'FX card meter must pass dynamic width via CSS custom property');

	const cultureSource = fs.readFileSync(path.join(aguiDir, 'AgUiCultureAudioCard.svelte'), 'utf8');
	assert.ok(cultureSource.includes(':has(> img)'), 'Culture card must scope side-media layout with :has(> img)');
	assert.ok(cultureSource.includes('sanitizeExternalUrl'), 'Culture card must sanitize external URLs via sanitizeExternalUrl');
	assert.ok(cultureSource.includes('rel="noopener noreferrer"'), 'External links must carry rel="noopener noreferrer"');

	const turnFooterSource = fs.readFileSync(path.join(aguiDir, 'TurnFooter.svelte'), 'utf8');
	assert.ok(turnFooterSource.includes('sanitizeExternalUrl'), 'TurnFooter must sanitize source URLs');
	assert.ok(
		turnFooterSource.includes('bg-[#FBEEE9] px-2 py-0.5 text-[11px] font-semibold text-[#9E331D]'),
		'TurnFooter error pill must use WCAG AA #9E331D contrast on #FBEEE9'
	);
	assert.ok(
		!turnFooterSource.includes('{#each sources as src (src.title)}'),
		'TurnFooter must index keyed #each blocks so duplicate source titles never throw Svelte 5 each_key_duplicate (#98)'
	);
	assert.ok(
		turnFooterSource.includes('-${idx}`)}'),
		'TurnFooter keyed #each blocks must include index disambiguation'
	);

	const dossierSource = fs.readFileSync(
		path.resolve(__dirname, '../src/lib/components/TripDossierPanel.svelte'),
		'utf8'
	);
	assert.ok(!dossierSource.includes('#8A867E'), 'TripDossierPanel must meet WCAG 2.1 AA small-text contrast');

	const workflowPath = path.resolve(__dirname, '../../../../.github/workflows/sdlc-quality-gate.yml');
	const workflowSource = fs.readFileSync(workflowPath, 'utf8');
	assert.ok(workflowSource.includes('runs-on: local-quarkus-agui'), 'CI must target local-quarkus-agui runner');
	assert.ok(workflowSource.includes('cancel-in-progress: true'), 'CI must cancel superseded runs');
	assert.ok(workflowSource.includes('pnpm install --frozen-lockfile'), 'CI must install pnpm workspace');
	assert.ok(workflowSource.includes('./mvnw -B install jacoco:report'), 'CI must run Maven reactor install + JaCoCo');
	assert.ok(workflowSource.includes('pitest-maven:mutationCoverage'), 'CI must run changed-module PITest');
	assert.ok(workflowSource.includes('github.event.before'), 'CI push events must diff before..HEAD so post-merge develop runs execute PITest');
});
