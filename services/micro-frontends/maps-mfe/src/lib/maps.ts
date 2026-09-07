import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Deliberately duplicated from bank-mfe/src/lib/maps.ts (2026-08-19, the maps-mfe
// split) rather than shared -- bank-mfe's own inline code (RouteMiniMap/LiveRiderMap/
// search elsewhere) still calls its own copy of searchPlaces directly, and this
// codebase's real, established convention (see kyc-mfe/src/lib/api.ts's own doc
// comment) is each MFE independently re-implements what it needs rather than a shared
// network package. A real backend contract change to any /api/v1/maps/* endpoint must
// be applied to BOTH copies.

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

// Real self-hosted glyphs (font PBF) server (2026-07-19) -- closes item 5, the last item
// on the Maps "100%" roadmap. Real pre-generated Noto Sans Regular/Bold glyph PBFs
// (github.com/openmaptiles/fonts, the standard self-hosted-MapLibre glyph source),
// served statically by nginx alongside the tile server -- purely static byte-range
// serving, not a live rendering service, so it carries none of OSRM/Nominatim's RAM
// cost despite being one more persistent private-cloud service. `{fontstack}` is
// url-encoded by MapLibre itself from a layer's `text-font`.
export const GLYPHS_URL = import.meta.env.VITE_GLYPHS_BASE_URL ?? '/glyphs/{fontstack}/{range}.pbf';

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

// Resolves a dropped pin using Itunda's own Rwanda-only Nominatim instance. `null`
// means the source has no honest neighbourhood match for that coordinate.
export const reverseGeocode = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; placeName: string | null }>(`/api/v1/maps/reverse?lat=${latitude}&lng=${longitude}`).then(
    (r) => r.placeName,
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
// BIKING added 2026-08-28 (itunda Maps redesign, direct Naver Map reference + explicit
// user instruction to build it) -- see OsrmRoutingClient.kt's own doc comment on the
// backend for the full account of reversing this project's own prior "no bicycle mode"
// decision. TRANSIT is a genuinely separate real journey planner (TransitRoutingService,
// real Kigali GTFS schedule data), not another OSRM profile -- see fetchTransitDirections
// below, not getDirections.
export type TravelMode = 'DRIVING' | 'WALKING' | 'BIKING';

const TRAVEL_MODE_ICONS: Record<TravelMode, string> = { DRIVING: '🚗', WALKING: '🚶', BIKING: '🚴' };
export const travelModeIcon = (mode: TravelMode): string => TRAVEL_MODE_ICONS[mode];

// Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives' own
// doc comment on the backend for the full account of the real, live-verified case where
// itunda's own OSRM instance genuinely offers more than one route for the same trip.
// Often just a single-element list -- OSRM itself decides whether a real alternative
// exists for a given pair of points, this doesn't force a second option to exist.
export const getDirectionsAlternatives = (fromLat: number, fromLng: number, toLat: number, toLng: number, mode: TravelMode = 'DRIVING') =>
  apiFetch<{ success: boolean; routes: RouteResult[] }>(
    `/api/v1/maps/directions/alternatives?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}&mode=${mode}`,
  ).then((r) => r.routes);

// A real, ordered errand/delivery route through 2–7 Rwanda stops. This is deliberately
// POST: an ordered list belongs in a validated request body, not in an unbounded query
// string. The response retains the normal RouteResult shape so every map client can use
// the same real road-line renderer it already uses for ordinary directions.
export interface ItineraryWaypoint {
  latitude: number;
  longitude: number;
}

export const getItineraryDirections = (waypoints: ItineraryWaypoint[], mode: TravelMode = 'DRIVING') =>
  apiFetch<{ success: boolean; route: RouteResult }>('/api/v1/maps/directions/itinerary', {
    method: 'POST',
    body: JSON.stringify({ waypoints, mode }),
  }).then((r) => r.route);

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

// Real, honest fallback (Maps product-completeness pass, 2026-09-07) -- this hardcoded
// list used to be the ONLY source, requiring a manual, error-prone 3-way sync with the
// backend's own MapPlaceCategory.kt enum and Android's/iOS's own identical hardcoded
// copies. `fetchMapCategories` below is the new real source of truth; this stays only
// as the pre-fetch/offline default MapView starts from before that call resolves.
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
  { id: 'ITUNDA_AGENT', label: 'Cash agents' },
  // Real additions (2026-08-09), same pass as the backend's own MapPlaceCategory.kt enum --
  // live-verified against itunda's real self-hosted Nominatim before adding.
  { id: 'MARKET', label: 'Markets' },
  { id: 'BUS_STOP', label: 'Bus stops' },
];

// Real backend category list (Maps product-completeness pass, 2026-09-07) -- see
// NEARBY_CATEGORIES's own doc comment. ITUNDA_AGENT is re-appended since it's a real,
// itunda-only category the backend enum doesn't define (own dedicated endpoint, see
// fetchNearbyAgents).
export const fetchMapCategories = () =>
  apiFetch<{ success: boolean; categories: MapPlaceCategory[] }>('/api/v1/maps/categories').then((r) => {
    const itundaAgentCategory = NEARBY_CATEGORIES.find((c) => c.id === 'ITUNDA_AGENT');
    return itundaAgentCategory ? [...r.categories, itundaAgentCategory] : r.categories;
  });

export const searchNearbyPlaces = (category: string, lat: number, lng: number, radiusKm = 2.0) =>
  apiFetch<{ success: boolean; places: NearbyPlace[] }>(
    `/api/v1/maps/nearby?category=${encodeURIComponent(category)}&lat=${lat}&lng=${lng}&radiusKm=${radiusKm}`,
  ).then((r) => r.places);

// Real customer-facing itunda cash-agent discovery (item 157, found via a fresh full
// @RequestMapping sweep) -- AgentDiscoveryController's own real, itunda-native location
// data (distinct from the OSM-backed category search above). Android already treats this
// as its own "ITUNDA_AGENT" category on the exact same Maps category-chip row (see
// MapsScreen.kt's own searchNearbyAgents call site) -- bank-mfe never had it. Mapped into
// the same NearbyPlace shape so this map's existing marker/popup rendering needs zero
// special-casing beyond choosing which fetch to call.
export const fetchNearbyAgents = (lat: number, lng: number, radiusKm = 5.0) =>
  apiFetch<{ success: boolean; agents: { id: string; displayName: string; latitude: number; longitude: number; distanceKm: number }[] }>(
    `/api/v1/agents/nearby?latitude=${lat}&longitude=${lng}&radiusKm=${radiusKm}`,
  ).then((r): NearbyPlace[] => r.agents.map((a) => ({ displayName: a.displayName, latitude: a.latitude, longitude: a.longitude, distanceKm: a.distanceKm })));

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

// Real Naver Map-style public/private folder + share (2026-08-04 on the backend) --
// bank-mfe never got a client for this until now; only Android did. See
// MapBookmark.isPublic's own doc comment on the backend for the full sourced account.
export const setMapFolderPublic = (folderName: string, isPublic: boolean) =>
  apiFetch<{ success: boolean; updatedCount: number }>('/api/v1/maps/bookmarks/folder-visibility', {
    method: 'PATCH',
    body: JSON.stringify({ folderName, isPublic }),
  }).then((r) => r.updatedCount);

// Deliberately unauthenticated on the backend -- see MapsController.sharedFolder's own
// doc comment. Whoever opens a share link doesn't need to already be signed in.
export const fetchSharedMapFolder = (ownerId: string, folderName: string) =>
  apiFetch<{ success: boolean; bookmarks: MapBookmark[] }>(`/api/v1/maps/shared/${encodeURIComponent(ownerId)}/${encodeURIComponent(folderName)}`).then((r) => r.bookmarks);

// Real Kakao Map-style "구독" (subscribe) -- the other half of sharing a folder: not just
// viewing someone else's public list, but real-copying it into the caller's own
// bookmarks. See MapsService.subscribeToSharedFolder's own doc comment on the backend.
export const subscribeToSharedMapFolder = (ownerId: string, folderName: string) =>
  apiFetch<{ success: boolean; copiedCount: number }>(`/api/v1/maps/shared/${encodeURIComponent(ownerId)}/${encodeURIComponent(folderName)}/subscribe`, {
    method: 'POST',
  }).then((r) => r.copiedCount);

export const addMapBookmark = (displayName: string, latitude: number, longitude: number, folderName?: string, color?: string) =>
  apiFetch<{ success: boolean; bookmark: MapBookmark }>('/api/v1/maps/bookmarks', {
    method: 'POST',
    body: JSON.stringify({ displayName, latitude, longitude, folderName, color }),
  }).then((r) => r.bookmark);

// Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc comment.
export const moveMapBookmark = (latitude: number, longitude: number, folderName: string, color: string) =>
  apiFetch<{ success: boolean; bookmark: MapBookmark }>(`/api/v1/maps/bookmarks?lat=${latitude}&lng=${longitude}`, {
    method: 'PATCH',
    body: JSON.stringify({ folderName, color }),
  }).then((r) => r.bookmark);

export const removeMapBookmark = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean }>(`/api/v1/maps/bookmarks?lat=${latitude}&lng=${longitude}`, { method: 'DELETE' });

// Real Kakao Map-style "친구위치" (Friend Location) live location sharing -- a real,
// moving position shared with one specific person for a bounded window, distinct from
// the static bookmark-folder share/subscribe above. See LiveLocationShareService's own
// doc comment on the backend for the full real sourcing, including why itunda's own
// v1 is ALWAYS time-bounded (no "unlimited" option). "Live" here means
// periodically-refreshed via polling, not a push channel -- itunda has no
// WebSocket infra for this feature specifically.
export interface LiveLocationShare {
  id: string;
  sharerUserId: string;
  recipientUserId: string;
  latitude: number | null;
  longitude: number | null;
  locationUpdatedAt: string | null;
  expiresAt: string;
  revoked: boolean;
  createdAt: string;
}

// Idempotency-Key added (Maps product-completeness pass, 2026-09-07) -- creates a
// brand-new share row every call with no dedup key at all, unlike a real
// DB-unique-constraint-backed bookmark add.
export const startLocationShare = (recipientPhoneNumber: string, durationHours: number = 1) =>
  apiFetch<{ success: boolean; share: LiveLocationShare }>('/api/v1/maps/location-share', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ recipientPhoneNumber, durationHours }),
  }).then((r) => r.share);

// Real "client owns when to push a fresh reading" position update -- fans out to every
// one of the caller's currently-active shares at once, matching how a real phone only
// has one real GPS reading to push regardless of how many people are watching it.
export const updateMyLocationShare = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; updatedShareCount: number }>('/api/v1/maps/location-share/_/update-location', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.updatedShareCount);

// Idempotency-Key added (Maps product-completeness pass, 2026-09-07) -- this is
// additive (extends from the share's current expiry), so a retry without a real dedup
// key used to silently double-extend it.
export const extendLocationShare = (shareId: string, additionalHours: number = 1) =>
  apiFetch<{ success: boolean; share: LiveLocationShare }>(`/api/v1/maps/location-share/${encodeURIComponent(shareId)}/extend`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ additionalHours }),
  }).then((r) => r.share);

export const stopLocationShare = (shareId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/maps/location-share/${encodeURIComponent(shareId)}/stop`, { method: 'POST' });

export const fetchMyLocationShares = () =>
  apiFetch<{ success: boolean; shares: LiveLocationShare[] }>('/api/v1/maps/location-share/mine').then((r) => r.shares);

export const fetchLocationSharesWithMe = () =>
  apiFetch<{ success: boolean; shares: LiveLocationShare[] }>('/api/v1/maps/location-share/shared-with-me').then((r) => r.shares);

// Real recipient-side poll -- call this on a real interval (e.g. every 15s) while
// watching a share to see the sharer's latest pushed position.
export const fetchLocationShare = (shareId: string) =>
  apiFetch<{ success: boolean; share: LiveLocationShare }>(`/api/v1/maps/location-share/${encodeURIComponent(shareId)}`).then((r) => r.share);

// Real Kigali GTFS-based transit journeys (2026-08-28, itunda Maps redesign) -- see
// TransitRoutingService's own doc comment on the backend for the honest, explicitly
// scoped v1 (direct routes only, real schedule-based departure times, never live GPS).
// A real empty array means no real direct transit option was found, not an error.
export interface TransitJourney {
  originStop: { id: string; name: string; latitude: number; longitude: number };
  destinationStop: { id: string; name: string; latitude: number; longitude: number };
  route: { id: string; shortName: string | null; longName: string | null };
  departureSecondsAfterMidnight: number;
  arrivalSecondsAfterMidnight: number;
  walkToOriginStopKm: number;
  walkFromDestinationStopKm: number;
}

export const fetchTransitDirections = (fromLat: number, fromLng: number, toLat: number, toLng: number) =>
  apiFetch<{ success: boolean; journeys: TransitJourney[] }>(
    `/api/v1/maps/directions/transit?fromLat=${fromLat}&fromLng=${fromLng}&toLat=${toLat}&toLng=${toLng}`,
  ).then((r) => r.journeys);

// Real consolidated place-detail (2026-08-28, itunda Maps redesign) -- see
// MapsPlaceDetailService's own doc comment on the backend. The one real source the
// tabbed place-detail panel reads from, replacing the old per-field ad hoc lookups.
export interface MapPlaceDetail {
  merchantId: string;
  businessName: string;
  category: string | null;
  photoUrl: string | null;
  photoUrls: string[];
  openingHours: string | null;
  phoneNumber: string | null;
  aiSummary: string | null;
  rating: { average: number | null; count: number };
  goodPointCounts: Record<string, number>;
  menu: { id: string; name: string; price: number; imageUrl: string | null; active: boolean }[];
  updates: {
    id: string;
    label: 'NOTICE' | 'EVENT' | 'PROMO';
    title: string;
    body: string;
    periodStart: string | null;
    periodEnd: string | null;
    likeCount: number;
    createdAt: string;
  }[];
}

export const fetchMapPlaceDetail = (merchantId: string) =>
  apiFetch<{ success: boolean; place: MapPlaceDetail }>(`/api/v1/maps/places/${encodeURIComponent(merchantId)}`).then((r) => r.place);

// Real, free, keyless Kigali weather (2026-08-28, itunda Maps redesign) -- see
// KigaliWeatherClient's own doc comment on the backend. `weather` is null when the
// real upstream is unreachable and there's no still-fresh cache -- never fabricated.
export interface KigaliWeather {
  temperatureCelsius: number;
  condition: string;
  pm2_5: number | null;
}

export const fetchKigaliWeather = () =>
  apiFetch<{ success: boolean; weather: KigaliWeather | null }>('/api/v1/maps/weather').then((r) => r.weather);

// Real preset-tag display labels (2026-08-28, itunda Maps redesign) -- mirrors
// EatsReviewService.EATS_GOOD_POINTS' own real vocab on the backend. Display-only
// here (this view renders the real aggregate counts on MapPlaceDetail.goodPointCounts,
// it doesn't collect tags itself -- that's bank-mfe's ReviewOrderCard).
export const EATS_GOOD_POINT_LABELS: Record<string, string> = {
  GREAT_FOOD: '🍽️ Great food',
  GREAT_DESSERT: '🍰 Great dessert',
  NICE_INTERIOR: '🛋️ Nice interior',
  GREAT_DRINKS: '🥤 Great drinks',
  GOOD_FOR_CONVERSATION: '💬 Good for conversation',
};
