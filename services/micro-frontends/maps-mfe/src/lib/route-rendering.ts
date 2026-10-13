import maplibregl from 'maplibre-gl';
import type { RouteResult } from './maps';

type RouteRenderResult = { ok: true } | { ok: false; error: string };

/**
 * Validate routing data before it reaches MapLibre. Routing responses can be
 * incomplete or malformed; reject them without replacing an existing route.
 */
export function renderValidatedRoute(
  map: maplibregl.Map,
  result: RouteResult,
): RouteRenderResult {
  const geometry = result?.geometry;
  const validGeometry = Array.isArray(geometry)
    && geometry.length >= 2
    && geometry.every((point) =>
      Array.isArray(point)
      && point.length >= 2
      && Number.isFinite(point[0])
      && Number.isFinite(point[1])
      && point[0] >= -90
      && point[0] <= 90
      && point[1] >= -180
      && point[1] <= 180,
    );
  const validMetrics = Number.isFinite(result?.distanceKm)
    && result.distanceKm >= 0
    && Number.isFinite(result?.durationMinutes)
    && result.durationMinutes >= 0
    && Array.isArray(result?.steps);

  if (!validGeometry || !validMetrics) {
    return { ok: false, error: 'The routing service returned an invalid route. Please try again.' };
  }

  const coordinates = geometry.map(([lat, lng]) => [lng, lat] as [number, number]);
  const bounds = coordinates.reduce(
    (b, coordinate) => b.extend(coordinate),
    new maplibregl.LngLatBounds(coordinates[0], coordinates[0]),
  );
  const source = map.getSource('route') as maplibregl.GeoJSONSource | undefined;
  if (!source) {
    return { ok: false, error: 'The map is still loading. Please try again.' };
  }

  source.setData({
    type: 'FeatureCollection',
    features: [{
      type: 'Feature',
      properties: {},
      geometry: { type: 'LineString', coordinates },
    }],
  });
  map.fitBounds(bounds, { padding: 60 });
  return { ok: true };
}
