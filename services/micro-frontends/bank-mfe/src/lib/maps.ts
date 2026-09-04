import { apiFetch } from './api';

// Real self-hosted Rwanda vector-tile source (2026-07-19) -- the last open item on the
// Maps roadmap (see docs/TOSS_PARITY_MATRIX.md's Maps row). A real Rwanda-only vector
// tile archive (built via Planetiler from a real Geofabrik OSM extract) served by
// itunda's own self-hosted `pmtiles serve` process on the private cloud, not a
// Google/Kakao/Naver Maps API key.
//
// Real regression found + fixed 2026-07-26: this used to default to an absolute
// `192.168.252.x` address -- itunda's own internal-only Multipass bridge IP, reachable
// from the Mac host and its own dev server, but never from a real remote browser. The
// public nginx (`itunda.conf`) already reverse-proxies `/tiles/`/`/glyphs/` same-origin
// (see its own comment, 2026-07-21) specifically to close that gap -- this default just
// wasn't pointed at it, so every real client silently tried the unreachable private IP
// instead of the working public path. Relative paths here, same convention
// `VITE_API_BASE_URL=` (empty string) already establishes for `apiFetch`.
export const TILES_BASE_URL = import.meta.env.VITE_TILES_BASE_URL ?? '/tiles';

export const TILES_SOURCE_URL = `${TILES_BASE_URL}/rwanda/{z}/{x}/{y}.mvt`;

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

export interface RouteStep {
  instruction: string;
  distanceMeters: number;
  streetName: string | null;
}

export interface RouteResult {
  distanceKm: number;
  durationMinutes: number;
  // Real road-following geometry, [lat, lng] pairs in itunda's own coordinate
  // convention (MapsController already flips OSRM's [lng, lat] GeoJSON order back).
  geometry: [number, number][];
  // Real turn-by-turn instructions (2026-07-20), sourced from OSRM's own documented
  // maneuver vocabulary -- see OsrmRoutingClient.maneuverInstruction's own doc comment.
  steps: RouteStep[];
}

// Real walking directions (2026-07-22) -- see OsrmRoutingClient.route's own doc
// comment on the backend for the full account of the real, separately-deployed
// foot-profile OSRM instance this now reaches. Defaults to 'DRIVING', matching the
// backend's own default and every pre-existing caller's unchanged behavior.
export type TravelMode = 'DRIVING' | 'WALKING';

export const getDirections = (fromLat: number, fromLng: number, toLat: number, toLng: number, mode: TravelMode = 'DRIVING') =>
  apiFetch<{ success: boolean; route: RouteResult }>(
    `/api/v1/maps/directions?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}&mode=${mode}`,
  ).then((r) => r.route);

// Real bookmarked/favorite places (2026-07-19) -- item 7 on the Maps "100%" roadmap, the
// same star/save feature Naver/Kakao Maps offer. See MapsService's own doc comment.
//
// `folderName`/`color` added 2026-07-22 -- Naver/Kakao Maps' own real "My Places" folder
// grouping (see MapBookmark.kt's own doc comment on the backend, migration V73). Every
// bookmark belongs to exactly one named folder with its own pin color; bookmarks made
// before this existed default into a single real "Saved places" folder.
export interface MapBookmark {
  id: string;
  displayName: string;
  latitude: number;
  longitude: number;
  folderName: string;
  color: string;
  isPublic?: boolean;
  createdAt: string;
}

export const fetchMyMapBookmarks = () =>
  apiFetch<{ success: boolean; bookmarks: MapBookmark[] }>('/api/v1/maps/bookmarks').then((r) => r.bookmarks);
