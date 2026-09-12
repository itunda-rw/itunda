import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { TILES_SOURCE_URL, getDirections } from './lib/maps';
import { fetchRideDriverLocation } from './lib/rideshare';
import { ApiError } from './lib/api';
import { useI18n } from './i18n/I18nContext';

// Real live driver-location tracking during an active ride trip -- "the defining
// 'watch your ride approach' moment every real Uber/Kakao T-style app has," found
// missing on all 3 platforms during the Rideshare product-completeness pass despite
// every backend field it needs already being real (RideTripService.getDriverLocation).
// A real sibling of LiveRiderMap.tsx (same MapLibre drawn-route + polled-position
// shape, same small-per-file style-object duplication precedent that file's own doc
// comment already establishes).
const RIDE_LIVE_MAP_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    rwanda: { type: 'vector', tiles: [TILES_SOURCE_URL], minzoom: 0, maxzoom: 14 },
    route: { type: 'geojson', data: { type: 'FeatureCollection', features: [] } },
  },
  layers: [
    { id: 'background', type: 'background', paint: { 'background-color': '#f2efe9' } },
    { id: 'landcover', type: 'fill', source: 'rwanda', 'source-layer': 'landcover', paint: { 'fill-color': '#d8e8c8', 'fill-opacity': 0.6 } },
    { id: 'water', type: 'fill', source: 'rwanda', 'source-layer': 'water', paint: { 'fill-color': '#a8d0e6' } },
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
      id: 'route-line', type: 'line', source: 'route',
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': '#7472F4', 'line-width': 5, 'line-opacity': 0.9 },
    },
  ],
};

// A real, honest "how long ago" label from the driver's own last real location push.
function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 5) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  return `${Math.round(seconds / 60)}m ago`;
}

export default function RideLiveDriverMap({
  tripId, fromLat, fromLng, toLat, toLng,
}: {
  tripId: string; fromLat: number; fromLng: number; toLat: number; toLng: number;
}) {
  const { t } = useI18n();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const driverMarkerRef = useRef<maplibregl.Marker | null>(null);
  const [location, setLocation] = useState<{ latitude: number; longitude: number; updatedAt: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Real map + real route drawn once -- reuses the exact same OSRM call RouteMiniMap
  // already established, so the live driver marker gets real road context.
  useEffect(() => {
    if (!containerRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: RIDE_LIVE_MAP_STYLE,
      center: [(fromLng + toLng) / 2, (fromLat + toLat) / 2],
      zoom: 13,
      attributionControl: false,
    });
    mapRef.current = map;

    new maplibregl.Marker({ color: '#7472F4' }).setLngLat([fromLng, fromLat]).addTo(map);
    new maplibregl.Marker({ color: 'var(--itunda-red)' }).setLngLat([toLng, toLat]).addTo(map);

    getDirections(fromLat, fromLng, toLat, toLng)
      .then((result) => {
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
          new maplibregl.LngLatBounds([result.geometry[0][1], result.geometry[0][0]], [result.geometry[0][1], result.geometry[0][0]]),
        );
        map.fitBounds(bounds, { padding: 40 });
      })
      .catch(() => {
        // Real, non-critical -- the live driver dot below is the actual point of
        // this component; a missing route line just means slightly less context.
      });

    return () => {
      map.remove();
      mapRef.current = null;
      driverMarkerRef.current = null;
    };
  }, [fromLat, fromLng, toLat, toLng]);

  // Real independent poll for the driver's real live position -- separate from the
  // route/map setup above, since re-fetching directions on every tick would be
  // wasteful (the route doesn't change, only the driver's position does).
  useEffect(() => {
    let cancelled = false;
    const poll = () => {
      fetchRideDriverLocation(tripId)
        .then((loc) => {
          if (cancelled) return;
          if (loc) {
            setLocation(loc);
            const map = mapRef.current;
            if (map) {
              if (!driverMarkerRef.current) {
                const el = document.createElement('div');
                el.textContent = '🚗';
                el.style.fontSize = '24px';
                driverMarkerRef.current = new maplibregl.Marker({ element: el }).setLngLat([loc.longitude, loc.latitude]).addTo(map);
              } else {
                driverMarkerRef.current.setLngLat([loc.longitude, loc.latitude]);
              }
            }
          }
        })
        .catch((err) => {
          if (!cancelled) setError(err instanceof ApiError ? err.message : t('common.loadError'));
        });
    };
    poll();
    const interval = setInterval(poll, 5000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [tripId]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div ref={containerRef} style={{ width: '100%', height: '200px', borderRadius: '12px', overflow: 'hidden' }} />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {location && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>🚗 Driver location updated {timeAgo(location.updatedAt)}</p>}
    </div>
  );
}
