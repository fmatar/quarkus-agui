export const AGUI_ACTIVITY_TYPES = Object.freeze({
	WEATHER: 'agui:weather',
	MAP_PLACES: 'agui:map-places',
	SECURITY_ADVISORY: 'agui:security-advisory',
	FX_BUDGET: 'agui:fx-budget',
	CULTURE_AUDIO: 'agui:culture-audio'
});

function isValidCoordinatePair(latitude, longitude) {
	return (
		typeof latitude === 'number' &&
		Number.isFinite(latitude) &&
		typeof longitude === 'number' &&
		Number.isFinite(longitude) &&
		Math.abs(latitude) <= 90 &&
		Math.abs(longitude) <= 180
	);
}

export function formatCoordinatesLabel(latitude, longitude) {
	if (!isValidCoordinatePair(latitude, longitude)) {
		return null;
	}
	const latHemisphere = latitude >= 0 ? 'N' : 'S';
	const lonHemisphere = longitude >= 0 ? 'E' : 'W';
	return `${Math.abs(latitude).toFixed(4)}° ${latHemisphere}, ${Math.abs(longitude).toFixed(4)}° ${lonHemisphere}`;
}

export function buildPlaceMapLinks({
	name = '',
	city = '',
	country = '',
	latitude = null,
	longitude = null,
	wikiUrl = '',
	zoom = 14
} = {}) {
	const placeName = typeof name === 'string' ? name.trim() : '';
	const cityName = typeof city === 'string' ? city.trim() : '';
	const countryName = typeof country === 'string' ? country.trim() : '';
	const searchQuery =
		[placeName, cityName, countryName].filter(Boolean).join(', ') || 'Earth';
	const encodedQuery = encodeURIComponent(searchQuery);
	const hasCoords = isValidCoordinatePair(latitude, longitude);
	const safeZoom = typeof zoom === 'number' && Number.isFinite(zoom) ? Math.max(2, Math.min(18, Math.round(zoom))) : 14;

	let googleMapsUrl;
	let appleMapsUrl;
	let openStreetMapUrl;
	let openStreetMapEmbedUrl = null;

	if (hasCoords) {
		const latStr = latitude.toFixed(4);
		const lonStr = longitude.toFixed(4);
		const delta = 0.045;
		const minLon = Math.max(-180, longitude - delta).toFixed(5);
		const minLat = Math.max(-90, latitude - delta).toFixed(5);
		const maxLon = Math.min(180, longitude + delta).toFixed(5);
		const maxLat = Math.min(90, latitude + delta).toFixed(5);
		openStreetMapEmbedUrl = `https://www.openstreetmap.org/export/embed.html?bbox=${minLon}%2C${minLat}%2C${maxLon}%2C${maxLat}&layer=mapnik&marker=${latitude.toFixed(5)}%2C${longitude.toFixed(5)}`;

		if (!placeName) {
			googleMapsUrl = `https://www.google.com/maps/search/?api=1&query=${latStr},${lonStr}`;
			appleMapsUrl = `https://maps.apple.com/?ll=${latStr},${lonStr}&q=${encodedQuery}`;
			openStreetMapUrl = `https://www.openstreetmap.org/?mlat=${latStr}&mlon=${lonStr}#map=${safeZoom}/${latStr}/${lonStr}`;
		} else {
			googleMapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodedQuery}`;
			appleMapsUrl = `https://maps.apple.com/?q=${encodedQuery}`;
			openStreetMapUrl = `https://www.openstreetmap.org/search?query=${encodedQuery}`;
		}
	} else {
		googleMapsUrl = `https://www.google.com/maps/search/?api=1&query=${encodedQuery}`;
		appleMapsUrl = `https://maps.apple.com/?q=${encodedQuery}`;
		openStreetMapUrl = `https://www.openstreetmap.org/search?query=${encodedQuery}`;
	}

	let wikipediaUrl;
	if (typeof wikiUrl === 'string' && /^https?:\/\//i.test(wikiUrl.trim())) {
		wikipediaUrl = wikiUrl.trim();
	} else if (placeName) {
		wikipediaUrl = `https://en.wikipedia.org/wiki/Special:Search?search=${encodedQuery}`;
	} else {
		const wikiSlug = encodeURIComponent((cityName || 'Earth').replace(/\s+/g, '_'));
		wikipediaUrl = `https://en.wikipedia.org/wiki/${wikiSlug}`;
	}

	return {
		searchQuery,
		coordinatesLabel: formatCoordinatesLabel(latitude, longitude),
		coordinatesRaw: hasCoords ? `${latitude.toFixed(4)}, ${longitude.toFixed(4)}` : null,
		googleMapsUrl,
		appleMapsUrl,
		openStreetMapUrl,
		openStreetMapEmbedUrl,
		wikipediaUrl
	};
}

export function createActivityRegistry(initialEntries = {}) {
	const entries = new Map();
	if (initialEntries && typeof initialEntries === 'object') {
		for (const [key, value] of Object.entries(initialEntries)) {
			if (key && key.trim()) {
				entries.set(key.trim(), value);
			}
		}
	}

	return {
		register(activityType, renderer) {
			if (typeof activityType === 'string' && activityType.trim()) {
				entries.set(activityType.trim(), renderer);
			}
			return this;
		},
		has(activityType) {
			return typeof activityType === 'string' && entries.has(activityType.trim());
		},
		resolve(activityTypeOrEvent) {
			if (!activityTypeOrEvent) return null;
			const key =
				typeof activityTypeOrEvent === 'string'
					? activityTypeOrEvent.trim()
					: typeof activityTypeOrEvent.activityType === 'string'
						? activityTypeOrEvent.activityType.trim()
						: '';
			return key && entries.has(key) ? entries.get(key) : null;
		},
		listTypes() {
			return Array.from(entries.keys());
		}
	};
}

export function upsertActivityList(existingList = [], event) {
	const current = Array.isArray(existingList) ? existingList : [];
	if (!event || typeof event.activityType !== 'string' || !event.activityType.trim()) {
		return current;
	}
	const activityType = event.activityType.trim();
	const messageId =
		typeof event.messageId === 'string' && event.messageId.trim()
			? event.messageId.trim()
			: `act-${activityType}`;
	const replace = event.replace !== false;
	const entry = {
		messageId,
		activityType,
		content:
			event.content && typeof event.content === 'object'
				? structuredClone(event.content)
				: {},
		replace,
		timestamp: event.timestamp ?? null,
		rawEvent: event.rawEvent ?? null
	};

	const matchIndex = current.findIndex(
		(item) =>
			item.messageId === messageId || (replace && item.activityType === activityType)
	);
	if (matchIndex >= 0) {
		const next = [...current];
		next[matchIndex] = entry;
		return next;
	}
	return [...current, entry];
}
