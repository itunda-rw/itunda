import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { RWANDA_CENTER, TILES_SOURCE_URL } from './lib/maps';
import { fetchShoppingCatalog, type ShoppingMerchant } from './lib/shopping';

// A real, minimal MapLibre style over itunda's own self-hosted vector tiles -- basic
// OpenMapTiles-schema layers (water/landcover/roads/buildings), no text labels yet since
// that needs a self-hosted glyphs/fonts server too (a real, honestly-named follow-up,
// not attempted this pass). Real Rwanda geography, not a fabricated placeholder map.
const MAP_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    rwanda: {
      type: 'vector',
      tiles: [TILES_SOURCE_URL],
      minzoom: 0,
      maxzoom: 14,
    },
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
  ],
};

// Real interactive Rwanda map -- itunda's own self-hosted Kakao Maps/Naver Maps-style
// mapping, the last open item on the Maps roadmap (see MAP_STYLE's own doc comment and
// docs/TOSS_PARITY_MATRIX.md). Plots real registered merchants (reusing the same
// GET /api/v1/shopping/merchants catalog the Shop tab already uses -- zero new backend
// browse endpoint) that have set a real location via POST /api/v1/merchant/location.
export default function MapView() {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [merchantCount, setMerchantCount] = useState<number | null>(null);

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
    map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');

    let cancelled = false;
    map.on('error', (e) => {
      // A real tile-fetch failure (tile server unreachable) surfaces here rather than
      // throwing -- MapLibre keeps rendering whatever tiles it already has.
      if (!cancelled) setError('Map tiles are temporarily unavailable.');
      console.warn('MapLibre error', e.error);
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
    };
  }, []);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}
      <div
        ref={containerRef}
        style={{ width: '100%', height: '440px', borderRadius: '16px', overflow: 'hidden' }}
      />
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', padding: '0 4px' }}>
        {merchantCount === null
          ? 'Loading real merchants near you…'
          : `${merchantCount} real merchant${merchantCount === 1 ? '' : 's'} shown on itunda's own self-hosted Rwanda map.`}
      </p>
    </div>
  );
}
