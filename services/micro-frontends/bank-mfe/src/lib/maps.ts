import { apiFetch } from './api';

// Real self-hosted Rwanda vector-tile source (2026-07-19) -- the last open item on the
// Maps roadmap (see docs/TOSS_PARITY_MATRIX.md's Maps row). A real Rwanda-only vector
// tile archive (built via Planetiler from a real Geofabrik OSM extract) served by
// itunda's own self-hosted `pmtiles serve` process on the private cloud, not a
// Google/Kakao/Naver Maps API key. Empty-string default here would break MapLibre, so
// this always resolves to a real reachable URL in every environment this app runs in.
export const TILES_BASE_URL = import.meta.env.VITE_TILES_BASE_URL ?? 'http://192.168.252.3:8090';

export const TILES_SOURCE_URL = `${TILES_BASE_URL}/rwanda/{z}/{x}/{y}.mvt`;

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
