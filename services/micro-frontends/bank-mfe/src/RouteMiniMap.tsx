import { useEffect, useRef, useState } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { TILES_SOURCE_URL, getDirections } from './lib/maps';
import { ApiError } from './lib/api';

// A real, compact, non-interactive drawn-route map -- reuses the exact same self-hosted
// OSRM directions itunda's own Maps feature already exposes (see MapsService.getDirections'
// own doc comment), so Eats/Marketplace get a real route + distance/duration instead of
// just a distance number, without duplicating any routing logic. Closes item 8 on the
// Maps "100%" roadmap ("reuse Directions inside Eats/Marketplace").
const ROUTE_STYLE: maplibregl.StyleSpecification = {
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

export default function RouteMiniMap({
  fromLat, fromLng, toLat, toLng, fromLabel, toLabel,
}: {
  fromLat: number; fromLng: number; toLat: number; toLng: number; fromLabel: string; toLabel: string;
}) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [route, setRoute] = useState<{ distanceKm: number; durationMinutes: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!containerRef.current) return;
    let cancelled = false;
    const map = new maplibregl.Map({
      container: containerRef.current,
      style: ROUTE_STYLE,
      center: [(fromLng + toLng) / 2, (fromLat + toLat) / 2],
      zoom: 12,
      interactive: false,
      attributionControl: false,
    });

    new maplibregl.Marker({ color: '#3182F6' }).setLngLat([fromLng, fromLat]).setPopup(new maplibregl.Popup({ offset: 12 }).setText(fromLabel)).addTo(map);
    new maplibregl.Marker({ color: 'var(--itunda-red)' }).setLngLat([toLng, toLat]).setPopup(new maplibregl.Popup({ offset: 12 }).setText(toLabel)).addTo(map);

    getDirections(fromLat, fromLng, toLat, toLng)
      .then((result) => {
        if (cancelled) return;
        setRoute({ distanceKm: result.distanceKm, durationMinutes: result.durationMinutes });
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
      .catch((err) => {
        if (!cancelled) setError(err instanceof ApiError ? err.message : 'Could not find a real route between these two points.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
      map.remove();
    };
  }, [fromLat, fromLng, toLat, toLng, fromLabel, toLabel]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div ref={containerRef} style={{ width: '100%', height: '160px', borderRadius: '12px', overflow: 'hidden' }} />
      {loading && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Finding the real road route…</p>}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {route && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
          🚗 {route.distanceKm.toFixed(1)} km · {Math.round(route.durationMinutes)} min by real road, via itunda's own self-hosted OSRM
        </p>
      )}
    </div>
  );
}
