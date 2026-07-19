import { apiFetch } from './api';

// Real self-hosted Rwanda vector-tile source (2026-07-19) -- the last open item on the
// Maps roadmap (see docs/TOSS_PARITY_MATRIX.md's Maps row). A real Rwanda-only vector
// tile archive (built via Planetiler from a real Geofabrik OSM extract) served by
// itunda's own self-hosted `pmtiles serve` process on the private cloud, not a
// Google/Kakao/Naver Maps API key. Empty-string default here would break MapLibre, so
// this always resolves to a real reachable URL in every environment this app runs in.
export const TILES_BASE_URL = import.meta.env.VITE_TILES_BASE_URL ?? 'http://192.168.252.3:8090';

export const TILES_SOURCE_URL = `${TILES_BASE_URL}/rwanda/{z}/{x}/{y}.mvt`;

// Real self-hosted glyphs (font PBF) server (2026-07-19) -- closes item 5, the last item
// on the Maps "100%" roadmap. Real pre-generated Noto Sans Regular/Bold glyph PBFs
// (github.com/openmaptiles/fonts, the standard self-hosted-MapLibre glyph source),
// served statically by nginx on itunda-dc-b alongside the tile server -- purely static
// byte-range serving, not a live rendering service, so it carries none of OSRM/
// Nominatim's RAM cost despite being this host's fourth persistent private-cloud
// service. `{fontstack}` is url-encoded by MapLibre itself from a layer's `text-font`.
export const GLYPHS_URL = import.meta.env.VITE_GLYPHS_BASE_URL ?? 'http://192.168.252.3:8091/{fontstack}/{range}.pbf';

// Kigali -- the same default center EatsOrderServiceTest/MarketplaceServiceTest use for
// their real-coordinate fixtures, kept consistent across this codebase.
export const RWANDA_CENTER: [number, number] = [30.0619, -1.9441];

// Real "search this map" + "directions" (2026-07-19) -- see MapsService's own doc
// comment on the backend for why these are a new, general-purpose front door onto
// itunda's already-deployed self-hosted Nominatim/OSRM, not new infrastructure.
export interface PlaceSearchResult {
  displayName: string;
  latitude: number;
  longitude: number;
}

export const searchPlaces = (query: string) =>
  apiFetch<{ success: boolean; results: PlaceSearchResult[] }>(`/api/v1/maps/search?q=${encodeURIComponent(query)}`).then(
    (r) => r.results,
  );

export interface RouteResult {
  distanceKm: number;
  durationMinutes: number;
  // Real road-following geometry, [lat, lng] pairs in itunda's own coordinate
  // convention (MapsController already flips OSRM's [lng, lat] GeoJSON order back).
  geometry: [number, number][];
}

export const getDirections = (fromLat: number, fromLng: number, toLat: number, toLng: number) =>
  apiFetch<{ success: boolean; route: RouteResult }>(
    `/api/v1/maps/directions?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}`,
  ).then((r) => r.route);

// Real "nearby places" category search (2026-07-19) -- Naver/Kakao's own category-chip
// search (restaurants, cafes, hospitals, ...), backed by real OSM tag search over
// itunda's self-hosted Nominatim, bounded to a real radius and sorted by real distance
// (see MapsService.getNearbyPlaces's own doc comment on the backend).
export interface NearbyPlace {
  displayName: string;
  latitude: number;
  longitude: number;
  distanceKm: number;
}

export interface MapPlaceCategory {
  id: string;
  label: string;
}

export const NEARBY_CATEGORIES: MapPlaceCategory[] = [
  { id: 'RESTAURANT', label: 'Restaurants' },
  { id: 'CAFE', label: 'Cafes' },
  { id: 'HOSPITAL', label: 'Hospitals' },
  { id: 'PHARMACY', label: 'Pharmacies' },
  { id: 'BANK', label: 'Banks' },
  { id: 'ATM', label: 'ATMs' },
  { id: 'HOTEL', label: 'Hotels' },
  { id: 'SUPERMARKET', label: 'Supermarkets' },
  { id: 'GAS_STATION', label: 'Gas stations' },
  { id: 'SCHOOL', label: 'Schools' },
];

export const searchNearbyPlaces = (category: string, lat: number, lng: number, radiusKm = 2.0) =>
  apiFetch<{ success: boolean; places: NearbyPlace[] }>(
    `/api/v1/maps/nearby?category=${encodeURIComponent(category)}&lat=${lat}&lng=${lng}&radiusKm=${radiusKm}`,
  ).then((r) => r.places);

// Real bookmarked/favorite places (2026-07-19) -- item 7 on the Maps "100%" roadmap, the
// same star/save feature Naver/Kakao Maps offer. See MapsService's own doc comment.
export interface MapBookmark {
  id: string;
  displayName: string;
  latitude: number;
  longitude: number;
  createdAt: string;
}

export const fetchMyMapBookmarks = () =>
  apiFetch<{ success: boolean; bookmarks: MapBookmark[] }>('/api/v1/maps/bookmarks').then((r) => r.bookmarks);

export const addMapBookmark = (displayName: string, latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; bookmark: MapBookmark }>('/api/v1/maps/bookmarks', {
    method: 'POST',
    body: JSON.stringify({ displayName, latitude, longitude }),
  }).then((r) => r.bookmark);

export const removeMapBookmark = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean }>(`/api/v1/maps/bookmarks?lat=${latitude}&lng=${longitude}`, { method: 'DELETE' });
