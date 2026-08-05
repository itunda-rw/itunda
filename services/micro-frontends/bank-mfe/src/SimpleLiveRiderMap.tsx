import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { TILES_SOURCE_URL } from './lib/maps';
import { ApiError } from './lib/api';

// Real live rider-location tracking for Commerce orders (2026-08-05) -- see
// lib/commerce.ts's own fetchOrderRiderLocation doc comment for the full sourced
// account. A deliberately simpler sibling of LiveRiderMap.tsx (Eats): no route line,
// no fixed "from"/"to" endpoints, since Commerce orders carry no delivery coordinates
// to draw a route toward -- just the rider's own live position, re-centered as it
// updates. Reuses the exact same self-hosted tile style LiveRiderMap/RouteMiniMap
// already established.
const SIMPLE_MAP_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    rwanda: { type: 'vector', tiles: [TILES_SOURCE_URL], minzoom: 0, maxzoom: 14 },
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
  ],
};

function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 5) return 'just now';
  if (seconds < 60) return `${seconds}s ago`;
  return `${Math.round(seconds / 60)}m ago`;
}

export default function SimpleLiveRiderMap({
  orderId, fetchLocation,
}: {
  orderId: string;
  fetchLocation: (orderId: string) => Promise<{ success: boolean; available: boolean; location: { latitude: number; longitude: number; updatedAt: string } | null }>;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const riderMarkerRef = useRef<maplibregl.Marker | null>(null);
  const [location, setLocation] = useState<{ latitude: number; longitude: number; updatedAt: string } | null>(null);
  const [available, setAvailable] = useState<boolean | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: SIMPLE_MAP_STYLE,
      center: [30.0619, -1.9441], // Kigali -- re-centered onto the rider the instant a real position arrives below
      zoom: 12,
      attributionControl: false,
    });
    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
      riderMarkerRef.current = null;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    const poll = () => {
      fetchLocation(orderId)
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
                map.jumpTo({ center: [res.location.longitude, res.location.latitude], zoom: 14 });
              } else {
                riderMarkerRef.current.setLngLat([res.location.longitude, res.location.latitude]);
                map.panTo([res.location.longitude, res.location.latitude]);
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
  }, [orderId, fetchLocation]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div ref={containerRef} style={{ width: '100%', height: '200px', borderRadius: '12px', overflow: 'hidden' }} />
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      {available === false && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Waiting for your rider's real location…</p>}
      {location && <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>🛵 Rider location updated {timeAgo(location.updatedAt)}</p>}
    </div>
  );
}
