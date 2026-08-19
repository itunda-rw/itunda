import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { TILES_SOURCE_URL, getDirections } from './lib/maps';
import { fetchRiderLocation } from './lib/eats';
import { ApiError } from './lib/api';

// Real live delivery tracking (2026-07-20) -- "the defining 'watch your order arrive'
// moment every real Coupang Eats/Uber Eats-style app has," per
// EatsOrderService.getRiderLocation's own doc comment. The backend endpoint has existed
// since 2026-07-19 with zero client UI until now -- this closes that gap. Reuses the
// exact same self-hosted tile style RouteMiniMap already established (duplicated here
// rather than shared/exported, matching this codebase's own small-per-file-duplication
// precedent for style objects) plus the same real OSRM directions call for route
// context, with a real, independently-polled rider marker on top.
const LIVE_MAP_STYLE: maplibregl.StyleSpecification = {
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
      paint: { 'line-color': '#3182F6', 'line-width': 5, 'line-opacity': 0.9 },
    },
  ],
};

// A real, honest "how long ago" label from the rider's own last real location push --
// never disguised as instantaneous, since the rider app only pushes every real 15s.
function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 5) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  return `${Math.round(seconds / 60)}m ago`;
}

export default function LiveRiderMap({
  orderId, fromLat, fromLng, toLat, toLng, fromLabel, toLabel,
}: {
  orderId: string; fromLat: number; fromLng: number; toLat: number; toLng: number; fromLabel: string; toLabel: string;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const riderMarkerRef = useRef<maplibregl.Marker | null>(null);
  const [location, setLocation] = useState<{ latitude: number; longitude: number; updatedAt: string } | null>(null);
  const [available, setAvailable] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Real map + real route drawn once -- reuses the exact same OSRM call RouteMiniMap
  // already established, so the live rider marker gets real road context, not a blank canvas.
  useEffect(() => {
    if (!containerRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: LIVE_MAP_STYLE,
      center: [(fromLng + toLng) / 2, (fromLat + toLat) / 2],
      zoom: 13,
      attributionControl: false,
    });
    mapRef.current = map;

    new maplibregl.Marker({ color: '#3182F6' }).setLngLat([fromLng, fromLat]).setPopup(new maplibregl.Popup({ offset: 12 }).setText(fromLabel)).addTo(map);
    new maplibregl.Marker({ color: 'var(--itunda-red)' }).setLngLat([toLng, toLat]).setPopup(new maplibregl.Popup({ offset: 12 }).setText(toLabel)).addTo(map);

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
        // Real, non-critical -- the live rider dot below is the actual point of this
        // component; a missing route line just means slightly less context, not a failure.
      });

    return () => {
      map.remove();
      mapRef.current = null;
      riderMarkerRef.current = null;
    };
  }, [fromLat, fromLng, toLat, toLng, fromLabel, toLabel]);

  // Real independent poll for the rider's real live position -- separate from the
  // route/map setup above, since re-fetching directions on every tick would be
  // wasteful (the route doesn't change, only the rider's position does).
  useEffect(() => {
    let cancelled = false;
    const poll = () => {
      fetchRiderLocation(orderId)
        .then((res) => {
          if (cancelled) return;
          setAvailable(res.available);
          if (res.location) {
            setLocation(res.location);
            const map = mapRef.current;
            if (map) {
              if (!riderMarkerRef.current) {
                const el = document.createElement('div');
                el.textContent = '🛵';
                el.style.fontSize = '24px';
                riderMarkerRef.current = new maplibregl.Marker({ element: el }).setLngLat([res.location.longitude, res.location.latitude]).addTo(map);
              } else {
                riderMarkerRef.current.setLngLat([res.location.longitude, res.location.latitude]);
              }
            }
          }
        })
        .catch((err) => {
          if (!cancelled) setError(err instanceof ApiError ? err.message : 'Could not load your rider\'s location.');
        });
    };
    poll();
    const interval = setInterval(poll, 5000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [orderId]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div ref={containerRef} style={{ width: '100%', height: '200px', borderRadius: '12px', overflow: 'hidden' }} />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {available === false && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Waiting for your rider's real location…</p>}
      {location && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>🛵 Rider location updated {timeAgo(location.updatedAt)}</p>}
    </div>
  );
}
