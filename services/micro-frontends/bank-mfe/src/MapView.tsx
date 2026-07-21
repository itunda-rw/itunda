import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import {
  RWANDA_CENTER,
  TILES_SOURCE_URL,
  GLYPHS_URL,
  searchPlaces,
  getDirectionsAlternatives,
  searchNearbyPlaces,
  NEARBY_CATEGORIES,
  fetchMyMapBookmarks,
  addMapBookmark,
  removeMapBookmark,
  type PlaceSearchResult,
  type NearbyPlace,
  type MapBookmark,
  type RouteStep,
  type RouteResult,
  type TravelMode,
} from './lib/maps';
import { fetchShoppingCatalog, type ShoppingMerchant } from './lib/shopping';
import { ApiError } from './lib/api';

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- basic
// OpenMapTiles-schema layers (water/landcover/roads/buildings) plus, 2026-07-19, real
// text labels (place/road/water/POI names) via itunda's own self-hosted glyphs server
// -- see lib/maps.ts's GLYPHS_URL doc comment. Real Rwanda geography and real OSM place
// names, not a fabricated placeholder map. Also declares the two real,
// empty-until-populated sources the search/directions features below write into: a
// destination marker and a real road-following route line.
const MAP_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  glyphs: GLYPHS_URL,
  sources: {
    rwanda: {
      type: 'vector',
      tiles: [TILES_SOURCE_URL],
      minzoom: 0,
      maxzoom: 14,
    },
    route: { type: 'geojson', data: { type: 'FeatureCollection', features: [] } },
  },
  layers: [
    { id: 'background', type: 'background', paint: { 'background-color': '#f2efe9' } },
    {
      id: 'landcover', type: 'fill', source: 'rwanda', 'source-layer': 'landcover',
      paint: { 'fill-color': '#d8e8c8', 'fill-opacity': 0.6 },
    },
    {
      id: 'park', type: 'fill', source: 'rwanda', 'source-layer': 'park',
      paint: { 'fill-color': '#c8e0b0', 'fill-opacity': 0.5 },
    },
    {
      id: 'water', type: 'fill', source: 'rwanda', 'source-layer': 'water',
      paint: { 'fill-color': '#a8d0e6' },
    },
    {
      id: 'landuse-residential', type: 'fill', source: 'rwanda', 'source-layer': 'landuse',
      filter: ['==', ['get', 'class'], 'residential'],
      paint: { 'fill-color': '#e6e1d8', 'fill-opacity': 0.5 },
    },
    {
      id: 'building', type: 'fill', source: 'rwanda', 'source-layer': 'building',
      minzoom: 13,
      paint: { 'fill-color': '#dcd4c6', 'fill-outline-color': '#c8bfae' },
    },
    {
      id: 'transportation-minor', type: 'line', source: 'rwanda', 'source-layer': 'transportation',
      filter: ['!', ['match', ['get', 'class'], ['motorway', 'trunk', 'primary', 'secondary'], true, false]],
      paint: { 'line-color': '#ffffff', 'line-width': ['interpolate', ['linear'], ['zoom'], 8, 0.5, 16, 3] },
    },
    {
      id: 'transportation-major', type: 'line', source: 'rwanda', 'source-layer': 'transportation',
      filter: ['match', ['get', 'class'], ['motorway', 'trunk', 'primary', 'secondary'], true, false],
      paint: { 'line-color': '#f5c96b', 'line-width': ['interpolate', ['linear'], ['zoom'], 6, 1, 16, 5] },
    },
    {
      id: 'boundary', type: 'line', source: 'rwanda', 'source-layer': 'boundary',
      filter: ['<=', ['get', 'admin_level'], 4],
      paint: { 'line-color': '#a08ccb', 'line-width': 1, 'line-dasharray': [2, 1] },
    },
    // Real drawn route (2026-07-19) -- see MapsService.getDirections' own doc comment.
    // Rendered above every base layer so it's always visible over roads/buildings.
    {
      id: 'route-line', type: 'line', source: 'route',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#3182F6', 'line-width': 5, 'line-opacity': 0.9 },
    },
    // Real text labels (2026-07-19) -- item 5, the last item on the Maps "100%"
    // roadmap. Real OSM name data already baked into the tile archive (see the
    // vector_layers this project's own `pmtiles show --metadata` inspection confirmed:
    // `place`/`transportation_name`/`water_name`/`poi` all carry a real `name` field),
    // rendered via itunda's own self-hosted glyph PBFs (GLYPHS_URL above). Ordered so
    // labels paint above every fill/line/route layer -- real map text always wins
    // legibility over the geometry beneath it.
    {
      id: 'water-label', type: 'symbol', source: 'rwanda', 'source-layer': 'water_name',
      minzoom: 7,
      layout: { 'text-field': ['get', 'name'], 'text-font': ['Noto Sans Regular'], 'text-size': 12 },
      paint: { 'text-color': '#3d6e8f', 'text-halo-color': '#ffffff', 'text-halo-width': 1 },
    },
    {
      id: 'road-label', type: 'symbol', source: 'rwanda', 'source-layer': 'transportation_name',
      minzoom: 12,
      layout: {
        'text-field': ['get', 'name'], 'text-font': ['Noto Sans Regular'], 'text-size': 12,
        'symbol-placement': 'line', 'text-letter-spacing': 0.05,
      },
      paint: { 'text-color': '#6b5a2a', 'text-halo-color': '#ffffff', 'text-halo-width': 1.2 },
    },
    {
      id: 'poi-label', type: 'symbol', source: 'rwanda', 'source-layer': 'poi',
      minzoom: 14,
      layout: { 'text-field': ['get', 'name'], 'text-font': ['Noto Sans Regular'], 'text-size': 11 },
      paint: { 'text-color': '#5a5044', 'text-halo-color': '#ffffff', 'text-halo-width': 1 },
    },
    {
      id: 'place-label-minor', type: 'symbol', source: 'rwanda', 'source-layer': 'place',
      minzoom: 10,
      filter: ['!', ['match', ['get', 'class'], ['city', 'town'], true, false]],
      layout: { 'text-field': ['get', 'name'], 'text-font': ['Noto Sans Regular'], 'text-size': 12 },
      paint: { 'text-color': '#3d3d3d', 'text-halo-color': '#ffffff', 'text-halo-width': 1.2 },
    },
    {
      id: 'place-label-major', type: 'symbol', source: 'rwanda', 'source-layer': 'place',
      filter: ['match', ['get', 'class'], ['city', 'town'], true, false],
      layout: {
        'text-field': ['get', 'name'], 'text-font': ['Noto Sans Bold'],
        'text-size': ['interpolate', ['linear'], ['zoom'], 4, 12, 10, 18],
      },
      paint: { 'text-color': '#1f1f1f', 'text-halo-color': '#ffffff', 'text-halo-width': 1.5 },
    },
  ],
};

const EMPTY_ROUTE_GEOJSON: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] };

// Real per-category glyphs for the chip row (2026-07-21) -- mirrors Android's own
// MAP_CATEGORY_ICONS lookup exactly (MapScreen.kt), same client-side-only convention:
// no icon field on the backend's NearbyPlace/category model, plain emoji over an icon
// font, never sent back to the server.
// Real recent-searches persistence key (2026-07-22) -- see the `recentSearches` state's
// own doc comment. Namespaced per-app, not just "recent_searches", since this is real
// shared browser localStorage the whole bank-mfe origin's other features also write into.
const RECENT_SEARCHES_KEY = 'itunda_map_recent_searches';

const CATEGORY_ICONS: Record<string, string> = {
  RESTAURANT: '🍽️', CAFE: '☕', HOSPITAL: '🏥', PHARMACY: '💊',
  BANK: '🏦', ATM: '🏧', HOTEL: '🏨', SUPERMARKET: '🛒',
  GAS_STATION: '⛽', SCHOOL: '🏫',
};

// Real fixed (non-theme-reactive) text colors for this component's own deliberately-
// white map chrome (search pill, chip row, results dropdown, bottom sheet) -- found as a
// real bug 2026-07-21 while verifying the redesign live in a real dark-mode browser
// session: `var(--toss-grey-900)` resolves to #ffffff in this app's dark theme (correct
// for text on the app's own dark page background), but every one of these panels uses a
// literal `background: '#fff'`, not the theme-reactive `--toss-white` token `.toss-card`
// uses -- so grey-900 text on them was rendering fully invisible (white-on-white), not
// just low-contrast. This affected the pre-existing bottom sheet ("Around you" heading,
// selected-place name, bookmark/nearby-result rows) as well as this pass's new zoom
// control, not only newly-added elements.
const MAP_CARD_TEXT = '#191F28';
const MAP_CARD_TEXT_SECONDARY = '#4E5968';
const MAP_CARD_TEXT_TERTIARY = '#8B95A1';
const MAP_CARD_DIVIDER = '#D1D6DB';

/**
 * Real interactive Rwanda map -- itunda's own self-hosted Kakao Maps/Naver Maps-style
 * mapping. Plots real registered merchants (reusing the same GET /api/v1/shopping/merchants
 * catalog the Shop tab already uses -- zero new backend browse endpoint) that have set a
 * real location via POST /api/v1/merchant/location, plus three real "feels like a real
 * maps app" capabilities added 2026-07-19 at the user's direct request ("make sure our
 * maps is fully 100% like naver maps/kakao maps for rwanda"): real place search (backed
 * by itunda's own self-hosted Nominatim, not just the Eats-checkout-scoped autocomplete
 * that existed before), a real "my location" blue dot (the browser's own real Geolocation
 * API, no backend call), and real turn-by-turn-capable directions (itunda's own
 * self-hosted OSRM, drawing the actual road-following route, not just a straight line).
 */
export default function MapView() {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const myLocationMarkerRef = useRef<maplibregl.Marker | null>(null);
  const destinationMarkerRef = useRef<maplibregl.Marker | null>(null);
  const myLocationRef = useRef<[number, number] | null>(null); // [lat, lng]
  const categoryMarkersRef = useRef<maplibregl.Marker[]>([]);

  const [error, setError] = useState<string | null>(null);
  const [merchantCount, setMerchantCount] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const [searchResults, setSearchResults] = useState<PlaceSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [selectedPlace, setSelectedPlace] = useState<PlaceSearchResult | null>(null);
  const [route, setRoute] = useState<{ distanceKm: number; durationMinutes: number; steps: RouteStep[] } | null>(null);
  // Real alternative routes (2026-07-22) -- see lib/maps.ts's getDirectionsAlternatives
  // doc comment. `routeAlternatives` holds every real route OSRM offered for this trip
  // (often just one -- OSRM itself decides whether a real alternative exists);
  // `selectedRouteIndex` is whichever one is currently drawn/reported above.
  const [routeAlternatives, setRouteAlternatives] = useState<RouteResult[] | null>(null);
  const [selectedRouteIndex, setSelectedRouteIndex] = useState(0);
  const [showSteps, setShowSteps] = useState(false);
  const [routing, setRouting] = useState(false);
  // Real driving/walking toggle (2026-07-22) -- see lib/maps.ts's TravelMode doc
  // comment for the real, separately-deployed foot-profile OSRM instance this reaches.
  const [travelMode, setTravelMode] = useState<TravelMode>('DRIVING');
  const [locating, setLocating] = useState(false);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [categoryLoading, setCategoryLoading] = useState(false);
  const [categoryResults, setCategoryResults] = useState<NearbyPlace[] | null>(null);
  const [bookmarks, setBookmarks] = useState<MapBookmark[]>([]);
  const [bookmarking, setBookmarking] = useState(false);
  // Real recent-searches list (2026-07-22) -- the other half of the same previously-
  // flagged "no autocomplete/recent-searches" gap the live-search-as-you-type pass just
  // closed the first half of. Naver/Kakao Maps' own real recent-searches list is a purely
  // client-side, per-device convenience (no account-wide sync), so this is real
  // localStorage persistence, not a fabricated backend feature -- no server-side value in
  // storing "which places did this browser search for" centrally.
  const [recentSearches, setRecentSearches] = useState<PlaceSearchResult[]>([]);
  const [searchFocused, setSearchFocused] = useState(false);

  // Real draggable peek/half/full bottom sheet (2026-07-21) -- see
  // docs/DESIGN_REFERENCES.md section 1, recommendation 1. Brings bank-mfe to parity
  // with Android's own `MapScreen.kt` restructure (commit 48ad768): this component was
  // a plain flex column stacking the search bar -> chips -> a fixed-height map div ->
  // detail/bookmark cards below it, in normal document flow. Restructured to a
  // full-bleed layout *within this component's own allotted box* (the surrounding
  // dashboard is a fixed 480px-wide mobile-style column, not a full browser viewport,
  // so "full-bleed" here means filling this component's box, the same way Android's
  // map fills whatever Scaffold padding it's given): the map fills the whole box,
  // search/chips/results float on top via the browser's native Pointer Events API
  // (works for both mouse and touch, no extra dependency), the same anchor-based
  // peek/half/full model Android implements via `AnchoredDraggableState` and iOS
  // implements via a hand-rolled `DragGesture`.
  const wrapperRef = useRef<HTMLDivElement | null>(null);
  const [wrapperHeight, setWrapperHeight] = useState(0);
  const [sheetY, setSheetY] = useState<number | null>(null);
  const [sheetDragging, setSheetDragging] = useState(false);
  const sheetSettledYRef = useRef(0);
  const dragStartRef = useRef<{ pointerY: number; startY: number } | null>(null);

  const PEEK_HEIGHT = 130;
  const FULL_TOP_GAP = 70;
  const peekAnchorY = wrapperHeight - PEEK_HEIGHT;
  const halfAnchorY = wrapperHeight * 0.55;
  const fullAnchorY = FULL_TOP_GAP;

  useEffect(() => {
    const el = wrapperRef.current;
    if (!el || typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver((entries) => setWrapperHeight(entries[0].contentRect.height));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(RECENT_SEARCHES_KEY);
      if (raw) setRecentSearches(JSON.parse(raw));
    } catch {
      // Corrupt/unavailable localStorage just means an empty recent-searches list --
      // a pure convenience feature, never worth failing the whole map view over.
    }
  }, []);

  useEffect(() => {
    if (wrapperHeight > 0 && sheetY === null) {
      setSheetY(wrapperHeight - PEEK_HEIGHT);
      sheetSettledYRef.current = wrapperHeight - PEEK_HEIGHT;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wrapperHeight]);

  // A newly-selected place should be immediately visible without a manual drag --
  // expands to Half; clearing the selection relaxes back to Peek instead of staying
  // pinned open over an empty card.
  useEffect(() => {
    if (wrapperHeight === 0) return;
    const target = selectedPlace ? wrapperHeight * 0.55 : wrapperHeight - PEEK_HEIGHT;
    setSheetY(target);
    sheetSettledYRef.current = target;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedPlace]);

  const onSheetPointerDown = (e: React.PointerEvent) => {
    (e.currentTarget as Element).setPointerCapture(e.pointerId);
    dragStartRef.current = { pointerY: e.clientY, startY: sheetSettledYRef.current };
    setSheetDragging(true);
  };
  const onSheetPointerMove = (e: React.PointerEvent) => {
    if (!dragStartRef.current) return;
    const delta = e.clientY - dragStartRef.current.pointerY;
    const proposed = dragStartRef.current.startY + delta;
    setSheetY(Math.min(peekAnchorY, Math.max(fullAnchorY, proposed)));
  };
  const onSheetPointerUp = () => {
    if (!dragStartRef.current) return;
    setSheetDragging(false);
    const current = sheetY ?? peekAnchorY;
    const candidates = [fullAnchorY, halfAnchorY, peekAnchorY];
    const nearest = candidates.reduce((a, b) => (Math.abs(b - current) < Math.abs(a - current) ? b : a));
    setSheetY(nearest);
    sheetSettledYRef.current = nearest;
    dragStartRef.current = null;
  };

  useEffect(() => {
    if (!containerRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: MAP_STYLE,
      center: RWANDA_CENTER,
      zoom: 12,
      attributionControl: false,
    });
    mapRef.current = map;
    // Real custom zoom control (2026-07-21) -- replaces MapLibre's own default
    // `NavigationControl` (a plain white square button pair, visually inconsistent
    // with the rest of this app's rounded-card/shadow language) with the same
    // itunda-styled floating control the JSX below renders, mirroring Android's own
    // MapScreen.kt zoom +/- stack exactly.

    let cancelled = false;
    map.on('error', (e) => {
      // A real tile-fetch failure (tile server unreachable) surfaces here rather than
      // throwing -- MapLibre keeps rendering whatever tiles it already has.
      if (!cancelled) setError('Map tiles are temporarily unavailable.');
      console.warn('MapLibre error', e.error);
    });

    fetchMyMapBookmarks()
      .then((real) => {
        if (!cancelled) setBookmarks(real);
      })
      .catch(() => {
        // Honest partial failure -- bookmarks are a real-nice-to-have, never block the
        // base map or the rest of the Maps feature set from loading.
      });

    fetchShoppingCatalog()
      .then((merchants: ShoppingMerchant[]) => {
        if (cancelled) return;
        const located = merchants.filter((m) => m.latitude != null && m.longitude != null);
        located.forEach((m) => {
          new maplibregl.Marker({ color: '#3182F6' })
            .setLngLat([m.longitude as number, m.latitude as number])
            .setPopup(new maplibregl.Popup({ offset: 12 }).setText(m.businessName))
            .addTo(map);
        });
        setMerchantCount(located.length);
      })
      .catch(() => {
        // Honest partial failure -- the base map still renders even if the merchant
        // overlay fails to load, never a blank screen for a real infra hiccup.
        if (!cancelled) setMerchantCount(0);
      });

    return () => {
      cancelled = true;
      map.remove();
      mapRef.current = null;
      myLocationMarkerRef.current = null;
      destinationMarkerRef.current = null;
      categoryMarkersRef.current.forEach((m) => m.remove());
      categoryMarkersRef.current = [];
    };
  }, []);

  // Real search-as-you-type autocomplete (2026-07-22) -- itunda's search was submit-then-
  // list only, unlike Naver/Kakao Maps' own real live-suggestion box; MapsService.searchPlaces
  // already anticipated this ("search-as-you-type is easy to hammer otherwise" -- its own
  // rate limit was sized for it before this UI ever called it that way). No new backend
  // work needed, just a debounced front door onto the same real Nominatim-backed endpoint
  // handleSearch below already uses. `searchRequestIdRef` discards a stale response that
  // resolves after a newer keystroke's request -- typing "Kigal" then "Kigali" fast enough
  // could otherwise let "Kigal"'s slower response overwrite "Kigali"'s newer, more relevant
  // results.
  const searchRequestIdRef = useRef(0);
  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setSearchResults(null);
      return;
    }
    const requestId = ++searchRequestIdRef.current;
    const timer = window.setTimeout(async () => {
      setSearching(true);
      setError(null);
      try {
        const results = await searchPlaces(trimmed);
        if (searchRequestIdRef.current === requestId) setSearchResults(results);
      } catch (err) {
        if (searchRequestIdRef.current === requestId) {
          setError(err instanceof ApiError ? err.message : 'Could not search for that place.');
        }
      } finally {
        if (searchRequestIdRef.current === requestId) setSearching(false);
      }
    }, 350);
    return () => window.clearTimeout(timer);
  }, [query]);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    searchRequestIdRef.current += 1;
    setSearching(true);
    setError(null);
    try {
      const results = await searchPlaces(trimmed);
      setSearchResults(results);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not search for that place.');
    } finally {
      setSearching(false);
    }
  };

  const addRecentSearch = (place: PlaceSearchResult) => {
    setRecentSearches((prev) => {
      const next = [place, ...prev.filter((p) => p.latitude !== place.latitude || p.longitude !== place.longitude)].slice(0, 8);
      try {
        window.localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(next));
      } catch {
        // Best-effort only -- a private-browsing/storage-disabled session just doesn't
        // get a persisted recent-searches list, not a broken search feature.
      }
      return next;
    });
  };

  const clearRecentSearches = () => {
    setRecentSearches([]);
    try {
      window.localStorage.removeItem(RECENT_SEARCHES_KEY);
    } catch {
      // same best-effort reasoning as addRecentSearch above
    }
  };

  const selectSearchResult = (place: PlaceSearchResult) => {
    addRecentSearch(place);
    selectPlace(place);
  };

  const selectPlace = (place: PlaceSearchResult) => {
    setSelectedPlace(place);
    setSearchResults(null);
    setRoute(null);
    setRouteAlternatives(null);
    setSelectedRouteIndex(0);
    const map = mapRef.current;
    if (!map) return;
    map.flyTo({ center: [place.longitude, place.latitude], zoom: 15 });
    destinationMarkerRef.current?.remove();
    destinationMarkerRef.current = new maplibregl.Marker({ color: '#E53935' })
      .setLngLat([place.longitude, place.latitude])
      .setPopup(new maplibregl.Popup({ offset: 12 }).setText(place.displayName))
      .addTo(map);
    // Clear any previously-drawn route -- a new destination needs a fresh "Directions" tap.
    const source = map.getSource('route') as maplibregl.GeoJSONSource | undefined;
    source?.setData(EMPTY_ROUTE_GEOJSON);
  };

  const isBookmarked = (place: PlaceSearchResult) =>
    bookmarks.some((b) => b.latitude === place.latitude && b.longitude === place.longitude);

  // Real "My Places" folder grouping (2026-07-22) -- see lib/maps.ts's MapBookmark doc
  // comment. Groups preserve `bookmarks`' own createdAt-desc order (a folder's position
  // here is simply wherever its most-recently-saved place falls), not a separate alphabetic
  // re-sort -- matches how a real recently-used folder list naturally feels most useful.
  const bookmarksByFolder = bookmarks.reduce<Array<[string, MapBookmark[]]>>((groups, b) => {
    const group = groups.find(([folder]) => folder === b.folderName);
    if (group) {
      group[1].push(b);
    } else {
      groups.push([b.folderName, [b]]);
    }
    return groups;
  }, []);

  const toggleBookmark = async (place: PlaceSearchResult) => {
    setBookmarking(true);
    setError(null);
    try {
      if (isBookmarked(place)) {
        await removeMapBookmark(place.latitude, place.longitude);
        setBookmarks((prev) => prev.filter((b) => !(b.latitude === place.latitude && b.longitude === place.longitude)));
      } else {
        const saved = await addMapBookmark(place.displayName, place.latitude, place.longitude);
        setBookmarks((prev) => [saved, ...prev]);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save this place.');
    } finally {
      setBookmarking(false);
    }
  };

  const findMyLocation = () => {
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        const lat = position.coords.latitude;
        const lng = position.coords.longitude;
        myLocationRef.current = [lat, lng];
        const map = mapRef.current;
        if (!map) return;
        myLocationMarkerRef.current?.remove();
        // A real, distinct "blue dot" marker (Naver/Kakao Maps' own real convention) --
        // a plain div styled as a filled circle, not MapLibre's default pin shape, so
        // "my location" reads visually distinct from a search-result/merchant pin.
        const el = document.createElement('div');
        el.style.width = '16px';
        el.style.height = '16px';
        el.style.borderRadius = '50%';
        el.style.backgroundColor = '#3182F6';
        el.style.border = '3px solid white';
        el.style.boxShadow = '0 0 0 2px rgba(49,130,246,0.4)';
        myLocationMarkerRef.current = new maplibregl.Marker({ element: el }).setLngLat([lng, lat]).addTo(map);
        map.flyTo({ center: [lng, lat], zoom: 14 });
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  // Real category-chip "nearby places" search (Naver/Kakao's own convention) -- searches
  // a real radius around the user's real location if known, otherwise the map's current
  // center (the same "browse this area" behavior a real maps app falls back to without
  // location permission). Tapping an already-active chip clears it, matching a real
  // toggle-filter UX rather than only ever adding more markers.
  const handleCategorySearch = async (categoryId: string) => {
    const map = mapRef.current;
    if (!map) return;
    if (activeCategory === categoryId) {
      categoryMarkersRef.current.forEach((m) => m.remove());
      categoryMarkersRef.current = [];
      setActiveCategory(null);
      setCategoryResults(null);
      return;
    }
    const center = myLocationRef.current ?? [map.getCenter().lat, map.getCenter().lng];
    setActiveCategory(categoryId);
    setCategoryLoading(true);
    setError(null);
    try {
      const places = await searchNearbyPlaces(categoryId, center[0], center[1]);
      categoryMarkersRef.current.forEach((m) => m.remove());
      categoryMarkersRef.current = places.map((place) =>
        new maplibregl.Marker({ color: '#8B5CF6' })
          .setLngLat([place.longitude, place.latitude])
          .setPopup(new maplibregl.Popup({ offset: 12 }).setText(`${place.displayName} · ${place.distanceKm.toFixed(1)}km`))
          .addTo(map),
      );
      setCategoryResults(places);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not search nearby places.');
      setActiveCategory(null);
    } finally {
      setCategoryLoading(false);
    }
  };

  // Draws one real route's geometry, fits the map to it, and updates the displayed
  // stats -- shared by the initial "Directions" fetch and by tapping a real alternative
  // route chip below it (2026-07-22, see lib/maps.ts's getDirectionsAlternatives doc
  // comment), so switching which route is selected never re-fetches from OSRM.
  const applyRoute = (result: RouteResult) => {
    const map = mapRef.current;
    if (!map) return;
    setRoute({ distanceKm: result.distanceKm, durationMinutes: result.durationMinutes, steps: result.steps });
    setShowSteps(false);
    const source = map.getSource('route') as maplibregl.GeoJSONSource | undefined;
    source?.setData({
      type: 'FeatureCollection',
      features: [{
        type: 'Feature',
        properties: {},
        geometry: { type: 'LineString', coordinates: result.geometry.map(([lat, lng]) => [lng, lat]) },
      }],
    });
    const bounds = result.geometry.reduce(
      (b, [lat, lng]) => b.extend([lng, lat]),
      new maplibregl.LngLatBounds(
        [result.geometry[0][1], result.geometry[0][0]],
        [result.geometry[0][1], result.geometry[0][0]],
      ),
    );
    map.fitBounds(bounds, { padding: 60 });
  };

  const handleGetDirections = async (mode: TravelMode = travelMode) => {
    const map = mapRef.current;
    if (!map || !selectedPlace) return;
    const origin = myLocationRef.current ?? [map.getCenter().lat, map.getCenter().lng];
    setRouting(true);
    setError(null);
    try {
      const results = await getDirectionsAlternatives(origin[0], origin[1], selectedPlace.latitude, selectedPlace.longitude, mode);
      setTravelMode(mode);
      setRouteAlternatives(results);
      setSelectedRouteIndex(0);
      applyRoute(results[0]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not find directions to this place.');
    } finally {
      setRouting(false);
    }
  };

  const selectRouteAlternative = (index: number) => {
    if (!routeAlternatives || index === selectedRouteIndex) return;
    setSelectedRouteIndex(index);
    applyRoute(routeAlternatives[index]);
  };

  const sheetTop = sheetY ?? peekAnchorY;

  return (
    <div
      ref={wrapperRef}
      style={{ position: 'relative', width: '100%', height: '70vh', minHeight: '460px', borderRadius: '16px', overflow: 'hidden' }}
    >
      {/* Real full-bleed map (2026-07-21) -- fills this component's entire box; every
          other panel below floats on top of it via absolute positioning, instead of
          the map being one fixed-height div in a document-flow column. */}
      <div ref={containerRef} style={{ position: 'absolute', inset: 0 }} />

      {/* Real floating chrome (2026-07-21 redesign, mirrors Android's MapScreen.kt) --
          previously one flat, edge-to-edge gradient band that read as a fixed toolbar.
          Now the search pill and chip row are their own individually-shadowed rounded
          surfaces with real map visible between them, matching the actual Naver
          Map/Kakao Map/Google Maps chrome convention. */}
      <div
        style={{
          position: 'absolute', top: 0, left: 0, right: 0, zIndex: 2,
          display: 'flex', flexDirection: 'column', gap: '10px', padding: '12px',
        }}
      >
        <form
          onSubmit={handleSearch}
          style={{
            display: 'flex', alignItems: 'center', gap: '6px',
            background: '#fff', borderRadius: '999px', padding: '4px 14px 4px 12px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.14)',
          }}
        >
          <span style={{ fontSize: '15px', color: searching ? 'var(--toss-grey-400)' : 'var(--toss-blue)' }}>🔍</span>
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => setSearchFocused(true)}
            onBlur={() => window.setTimeout(() => setSearchFocused(false), 150)}
            placeholder="Search a real place in Rwanda"
            style={{ flex: 1, padding: '10px 6px', border: 'none', outline: 'none', fontSize: '14px', background: 'transparent' }}
          />
          {query.trim() !== '' && (
            <button
              type="button"
              onClick={() => { setQuery(''); setSearchResults(null); }}
              aria-label="Clear search"
              style={{ fontSize: '13px', color: 'var(--toss-grey-400)', padding: '4px' }}
            >
              ✕
            </button>
          )}
        </form>

        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '2px' }}>
          {NEARBY_CATEGORIES.map((category) => {
            const active = activeCategory === category.id;
            return (
              <button
                key={category.id}
                type="button"
                onClick={() => handleCategorySearch(category.id)}
                disabled={categoryLoading && !active}
                style={{
                  flexShrink: 0,
                  display: 'flex', alignItems: 'center', gap: '4px',
                  padding: '8px 12px',
                  borderRadius: '999px',
                  fontSize: '12px',
                  fontWeight: 600,
                  border: 'none',
                  boxShadow: active ? '0 2px 6px rgba(139,92,246,0.4)' : '0 1px 4px rgba(0,0,0,0.1)',
                  backgroundColor: active ? '#8B5CF6' : '#fff',
                  color: active ? '#fff' : MAP_CARD_TEXT_SECONDARY,
                }}
              >
                <span>{CATEGORY_ICONS[category.id] ?? '📍'}</span>
                {active && categoryLoading ? '…' : category.label}
              </button>
            );
          })}
        </div>

        {(searchResults !== null || error) && (
          <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '8px', maxHeight: '160px', overflowY: 'auto', boxShadow: '0 2px 8px rgba(0,0,0,0.14)' }}>
            {searchResults !== null && (searchResults.length === 0 ? (
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', padding: '8px' }}>No real places found for that search.</p>
            ) : (
              searchResults.map((place, i) => (
                <button
                  key={`${place.latitude}-${place.longitude}-${i}`}
                  onClick={() => selectSearchResult(place)}
                  style={{ textAlign: 'left', padding: '10px 12px', borderRadius: '8px', fontSize: '13px', color: 'var(--toss-grey-900)' }}
                >
                  {place.displayName}
                </button>
              ))
            ))}
            {error && <p style={{ fontSize: '13px', color: '#E53935', padding: '8px' }} role="alert">{error}</p>}
          </div>
        )}

        {/* Real recent-searches list (2026-07-22) -- only shown while the search box is
            focused and empty (Naver/Kakao Maps' own real convention: tap the search box
            before typing anything to see what you searched for before), never competing
            with live results once a query exists. */}
        {searchFocused && query.trim() === '' && recentSearches.length > 0 && (
          <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '8px', maxHeight: '160px', overflowY: 'auto', boxShadow: '0 2px 8px rgba(0,0,0,0.14)' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '4px 8px' }}>
              <p style={{ fontSize: '12px', fontWeight: 700, color: MAP_CARD_TEXT_TERTIARY }}>Recent searches</p>
              <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={clearRecentSearches} style={{ fontSize: '11px', color: 'var(--toss-blue)', fontWeight: 700 }}>
                Clear
              </button>
            </div>
            {recentSearches.map((place, i) => (
              <button
                key={`${place.latitude}-${place.longitude}-${i}`}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => selectSearchResult(place)}
                style={{ textAlign: 'left', padding: '10px 12px', borderRadius: '8px', fontSize: '13px', color: 'var(--toss-grey-900)', display: 'flex', alignItems: 'center', gap: '8px' }}
              >
                <span style={{ color: MAP_CARD_TEXT_TERTIARY }}>🕐</span>
                {place.displayName}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Real floating right-side controls (2026-07-21) -- zoom +/- and a dedicated
          "locate me" button, matching the standard Google Maps/Naver Map/Kakao Map
          convention of a vertical control stack on the right, distinct from the search
          bar (which previously carried the locate button inline). Mirrors Android's
          own MapScreen.kt control stack exactly. Anchored above the sheet's own peek
          height so it's never covered at rest. */}
      <div
        style={{
          position: 'absolute', right: '12px', zIndex: 2,
          bottom: `${PEEK_HEIGHT + 16}px`,
          display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '10px',
        }}
      >
        <div style={{ background: '#fff', borderRadius: '14px', boxShadow: '0 2px 8px rgba(0,0,0,0.14)', overflow: 'hidden' }}>
          <button
            type="button"
            aria-label="Zoom in"
            onClick={() => mapRef.current?.zoomIn()}
            style={{ display: 'block', width: '44px', height: '44px', fontSize: '18px', color: MAP_CARD_TEXT }}
          >
            +
          </button>
          <div style={{ height: '1px', background: '#E5E8EB' }} />
          <button
            type="button"
            aria-label="Zoom out"
            onClick={() => mapRef.current?.zoomOut()}
            style={{ display: 'block', width: '44px', height: '44px', fontSize: '18px', color: MAP_CARD_TEXT }}
          >
            −
          </button>
        </div>
        <button
          type="button"
          disabled={locating}
          onClick={findMyLocation}
          aria-label="Find my real location"
          style={{
            width: '46px', height: '46px', borderRadius: '50%',
            background: '#fff', boxShadow: '0 2px 8px rgba(0,0,0,0.14)',
            fontSize: '18px', display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: locating ? 'var(--toss-grey-400)' : 'var(--toss-blue)',
          }}
        >
          {locating ? '…' : '📍'}
        </button>
      </div>

      {/* Real draggable peek/half/full bottom sheet (2026-07-21) -- a persistent,
          non-modal panel docked over the map. Pointer Events drive `sheetY` live; on
          release it snaps to whichever of the three real anchors above is closest --
          the same anchor-based model Android implements via `AnchoredDraggableState`
          and iOS implements via a hand-rolled `DragGesture`. */}
      <div
        style={{
          position: 'absolute', left: 0, right: 0, top: 0, height: '100%', zIndex: 3,
          transform: `translateY(${sheetTop}px)`,
          transition: sheetDragging ? 'none' : 'transform 0.3s cubic-bezier(0.2, 0.8, 0.2, 1)',
          background: '#fff', borderRadius: '16px 16px 0 0',
          boxShadow: '0 -4px 16px rgba(0,0,0,0.12)',
          display: 'flex', flexDirection: 'column',
        }}
      >
        <div
          onPointerDown={onSheetPointerDown}
          onPointerMove={onSheetPointerMove}
          onPointerUp={onSheetPointerUp}
          onPointerCancel={onSheetPointerUp}
          style={{ display: 'flex', justifyContent: 'center', padding: '10px 0 6px', cursor: 'grab', touchAction: 'none' }}
        >
          <div style={{ width: '36px', height: '4px', borderRadius: '2px', backgroundColor: MAP_CARD_DIVIDER }} />
        </div>

        <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 24px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {selectedPlace ? (
            <>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '8px' }}>
                <p style={{ fontSize: '14px', fontWeight: 700, color: MAP_CARD_TEXT, flex: 1 }}>{selectedPlace.displayName}</p>
                <button
                  type="button"
                  onClick={() => toggleBookmark(selectedPlace)}
                  disabled={bookmarking}
                  aria-label={isBookmarked(selectedPlace) ? 'Remove real bookmark' : 'Save this real place'}
                  style={{ fontSize: '20px', lineHeight: 1, color: isBookmarked(selectedPlace) ? '#F5A623' : MAP_CARD_DIVIDER }}
                >
                  {isBookmarked(selectedPlace) ? '★' : '☆'}
                </button>
              </div>
              {/* Real driving/walking mode toggle (2026-07-22) -- same real Naver/Kakao
                  Maps convention of picking a travel mode before/after a route is drawn.
                  Switching mode while a route is already shown re-fetches it against
                  itunda's own separately-deployed foot-profile OSRM instance rather than
                  just relabeling the existing driving route. */}
              <div style={{ display: 'flex', gap: '6px' }}>
                {(['DRIVING', 'WALKING'] as TravelMode[]).map((m) => (
                  <button
                    key={m}
                    type="button"
                    disabled={routing}
                    onClick={() => {
                      if (m === travelMode) return;
                      if (route) {
                        handleGetDirections(m);
                      } else {
                        setTravelMode(m);
                      }
                    }}
                    style={{
                      flex: 1, padding: '6px 0', borderRadius: '8px', fontSize: '12px', fontWeight: 700,
                      background: travelMode === m ? 'var(--toss-blue)' : '#F2F4F6',
                      color: travelMode === m ? '#fff' : MAP_CARD_TEXT_SECONDARY,
                    }}
                  >
                    {m === 'DRIVING' ? '🚗 Driving' : '🚶 Walking'}
                  </button>
                ))}
              </div>
              {route ? (
                <div>
                  <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_SECONDARY }}>
                    {travelMode === 'DRIVING' ? '🚗' : '🚶'} {route.distanceKm.toFixed(1)} km · {Math.round(route.durationMinutes)} min by real road, via itunda's own self-hosted OSRM
                  </p>
                  {/* Real alternative-route picker (2026-07-22) -- only rendered when
                      OSRM genuinely offered more than one real route for this trip (see
                      lib/maps.ts's getDirectionsAlternatives doc comment); a real single-
                      route trip stays exactly as it looked before this feature existed. */}
                  {routeAlternatives && routeAlternatives.length > 1 && (
                    <div style={{ display: 'flex', gap: '6px', margin: '6px 0' }}>
                      {routeAlternatives.map((alt, i) => (
                        <button
                          key={i}
                          type="button"
                          onClick={() => selectRouteAlternative(i)}
                          style={{
                            flex: 1, padding: '5px 0', borderRadius: '8px', fontSize: '11px', fontWeight: 700,
                            background: selectedRouteIndex === i ? 'var(--toss-blue)' : '#F2F4F6',
                            color: selectedRouteIndex === i ? '#fff' : MAP_CARD_TEXT_SECONDARY,
                          }}
                        >
                          Route {i + 1} · {alt.distanceKm.toFixed(1)}km · {Math.round(alt.durationMinutes)}min
                        </button>
                      ))}
                    </div>
                  )}
                  {route.steps.length > 0 && (
                    <button
                      type="button"
                      onClick={() => setShowSteps((s) => !s)}
                      style={{ fontSize: '12px', color: 'var(--toss-blue)', fontWeight: 700, marginTop: '4px' }}
                    >
                      {showSteps ? 'Hide turn-by-turn directions' : `Show turn-by-turn directions (${route.steps.length} steps)`}
                    </button>
                  )}
                  {showSteps && (
                    <ol style={{ margin: '8px 0 0', paddingLeft: '18px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      {route.steps.map((step, i) => (
                        <li key={i} style={{ fontSize: '12px', color: MAP_CARD_TEXT_SECONDARY }}>
                          {step.instruction}
                          {step.distanceMeters >= 10 && (
                            <span style={{ color: MAP_CARD_TEXT_TERTIARY }}> ({Math.round(step.distanceMeters)} m)</span>
                          )}
                        </li>
                      ))}
                    </ol>
                  )}
                </div>
              ) : (
                <button className="toss-btn toss-btn-primary" disabled={routing} onClick={() => handleGetDirections()}>
                  {routing ? 'Finding real route…' : 'Directions'}
                </button>
              )}
            </>
          ) : (
            <>
              {/* Real default "around me" state (2026-07-21) -- Naver Map's own Smart
                  Around sheet keeps a non-modal panel permanently docked with real
                  curated content even before any search, rather than only ever
                  appearing once a place is selected. itunda has no editorial "today's
                  pick" feed to curate, so this surfaces real data it already has: the
                  active category's real results, a real merchant count, and real
                  saved places -- honest functional content, not a fabricated feed. */}
              <p style={{ fontSize: '14px', fontWeight: 700, color: MAP_CARD_TEXT }}>Around you</p>
              {activeCategory && categoryResults !== null ? (
                categoryResults.length === 0 ? (
                  <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_TERTIARY }}>
                    No real matches found nearby for {NEARBY_CATEGORIES.find((c) => c.id === activeCategory)?.label.toLowerCase()}.
                  </p>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    {categoryResults.map((place, i) => (
                      <button
                        key={`${place.latitude}-${place.longitude}-${i}`}
                        onClick={() => selectPlace({ displayName: place.displayName, latitude: place.latitude, longitude: place.longitude })}
                        style={{ textAlign: 'left', padding: '6px 0', fontSize: '13px', color: MAP_CARD_TEXT }}
                      >
                        {place.displayName} · {place.distanceKm.toFixed(1)} km
                      </button>
                    ))}
                  </div>
                )
              ) : (
                <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_TERTIARY }}>
                  {merchantCount === null
                    ? 'Loading real merchants near you…'
                    : merchantCount === 0
                    ? 'Search a real place or pick a category above to explore Rwanda.'
                    : `${merchantCount} real merchant${merchantCount === 1 ? '' : 's'} on the map. Search a place or pick a category above to explore.`}
                </p>
              )}

              <p style={{ fontSize: '12px', fontWeight: 700, color: MAP_CARD_TEXT_TERTIARY, marginTop: '8px' }}>★ Your saved places</p>
              {bookmarks.length === 0 ? (
                <p style={{ fontSize: '12px', color: MAP_CARD_TEXT_TERTIARY }}>No saved places yet -- tap ☆ on a place to save it.</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {bookmarksByFolder.map(([folderName, folderBookmarks]) => (
                    <div key={folderName}>
                      {/* Only shown once there's more than one real folder -- a single
                          default "Saved places" folder stays exactly as flat as it looked
                          before this feature existed. */}
                      {bookmarksByFolder.length > 1 && (
                        <p style={{ fontSize: '11px', fontWeight: 700, color: MAP_CARD_TEXT_TERTIARY, padding: '4px 0' }}>{folderName}</p>
                      )}
                      {folderBookmarks.map((b) => (
                        <button
                          key={b.id}
                          onClick={() => selectPlace({ displayName: b.displayName, latitude: b.latitude, longitude: b.longitude })}
                          style={{ textAlign: 'left', padding: '6px 0', fontSize: '13px', color: MAP_CARD_TEXT, display: 'flex', alignItems: 'center', gap: '6px' }}
                        >
                          <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: b.color, flexShrink: 0 }} />
                          {b.displayName}
                        </button>
                      ))}
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
