import { travelModeIcon, type PlaceSearchResult, type RouteResult, type RouteStep, type TravelMode, type TransitJourney } from './lib/maps';
import type { BusTrip } from './lib/bus';

const MAP_CARD_TEXT = '#191F28';
const MAP_CARD_TEXT_SECONDARY = '#4E5968';
const MAP_CARD_TEXT_TERTIARY = '#636E7C';

// Extracted from MapView.tsx (itunda Maps redesign, 2026-08-28) -- that file grew past
// its recorded file-size-lint baseline once real BIKING/TRANSIT modes were added
// alongside the pre-existing DRIVING/WALKING/BUS ones. Real mode-toggle chips + real
// per-mode results rendering (intercity bus / Kigali GTFS transit / OSRM route with
// alternatives + turn-by-turn steps), a genuinely cohesive "directions results" unit.
export function MapDirectionsPanel(props: {
  selectedPlace: PlaceSearchResult;
  travelMode: TravelMode | 'BUS' | 'TRANSIT';
  setTravelMode: (m: TravelMode | 'BUS' | 'TRANSIT') => void;
  routing: boolean;
  busSearching: boolean;
  transitSearching: boolean;
  itineraryStops: [number, number][] | null;
  route: { distanceKm: number; durationMinutes: number; steps: RouteStep[] } | null;
  routeAlternatives: RouteResult[] | null;
  selectedRouteIndex: number;
  busTrips: BusTrip[] | null;
  transitJourneys: TransitJourney[] | null;
  showSteps: boolean;
  setShowSteps: (fn: (s: boolean) => boolean) => void;
  handleRouteItinerary: (mode: TravelMode) => void;
  handleGetDirections: (mode?: TravelMode) => void;
  searchBus: (destination: string) => void;
  searchTransit: (destination: PlaceSearchResult) => void;
  selectRouteAlternative: (index: number) => void;
}) {
  const {
    selectedPlace, travelMode, setTravelMode, routing, busSearching, transitSearching, itineraryStops,
    route, routeAlternatives, selectedRouteIndex, busTrips, transitJourneys, showSteps, setShowSteps,
    handleRouteItinerary, handleGetDirections, searchBus, searchTransit, selectRouteAlternative,
  } = props;

  return (
    <>
      {/* Real driving/walking/bike mode toggle -- same real Naver/Kakao Maps convention
          of picking a travel mode before/after a route is drawn. Switching mode while a
          route is already shown re-fetches it against the matching real OSRM instance
          rather than just relabeling the existing route. BIKING added 2026-08-28 (itunda
          Maps redesign, direct user instruction) -- see OsrmRoutingClient.kt's own doc
          comment on the backend for the full account of reversing this project's own
          prior "no bicycle mode" decision. */}
      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
        {(['DRIVING', 'WALKING', 'BIKING'] as TravelMode[]).map((m) => (
          <button
            key={m}
            type="button"
            disabled={routing}
            onClick={() => {
              if (m === travelMode) return;
              if (itineraryStops) {
                handleRouteItinerary(m);
              } else if (route) {
                handleGetDirections(m);
              } else {
                setTravelMode(m);
              }
            }}
            style={{
              flex: 1, padding: '6px 0', borderRadius: '8px', fontSize: '12px', fontWeight: 700,
              background: travelMode === m ? 'var(--itunda-indigo)' : '#F2F4F6',
              color: travelMode === m ? '#fff' : MAP_CARD_TEXT_SECONDARY,
            }}
          >
            {m === 'DRIVING' ? '🚗 Driving' : m === 'WALKING' ? '🚶 Walking' : '🚴 Bike'}
          </button>
        ))}
        {/* Real Naver Map-style intercity-bus tab -- see busTrips' own doc comment.
            Not shown for a multi-stop itinerary: a peer-posted point-to-point coach trip
            has no real concept of a custom multi-waypoint route, same deliberate scope
            boundary the Android/iOS ports already established. */}
        {!itineraryStops && (
          <button
            type="button"
            disabled={routing || busSearching}
            onClick={() => {
              if (travelMode === 'BUS') return;
              setTravelMode('BUS');
              searchBus(selectedPlace.displayName);
            }}
            style={{
              flex: 1, padding: '6px 0', borderRadius: '8px', fontSize: '12px', fontWeight: 700,
              background: travelMode === 'BUS' ? 'var(--itunda-indigo)' : '#F2F4F6',
              color: travelMode === 'BUS' ? '#fff' : MAP_CARD_TEXT_SECONDARY,
            }}
          >
            🚌 Intercity bus
          </button>
        )}
        {/* Real Kigali GTFS-based transit journeys (2026-08-28, itunda Maps redesign) --
            see transitJourneys' own doc comment. Deliberately separate from the
            intercity-bus chip above -- this is the real local city-transit journey
            planner. */}
        {!itineraryStops && (
          <button
            type="button"
            disabled={routing || transitSearching}
            onClick={() => {
              if (travelMode === 'TRANSIT') return;
              setTravelMode('TRANSIT');
              searchTransit(selectedPlace);
            }}
            style={{
              flex: 1, padding: '6px 0', borderRadius: '8px', fontSize: '12px', fontWeight: 700,
              background: travelMode === 'TRANSIT' ? 'var(--itunda-indigo)' : '#F2F4F6',
              color: travelMode === 'TRANSIT' ? '#fff' : MAP_CARD_TEXT_SECONDARY,
            }}
          >
            🚏 Transit
          </button>
        )}
      </div>
      {travelMode === 'BUS' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {busSearching ? (
            <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_SECONDARY }}>
              Searching real scheduled trips to {selectedPlace.displayName}…
            </p>
          ) : busTrips && busTrips.length > 0 ? (
            busTrips.map((trip) => (
              <div key={trip.id} style={{ background: '#F2F4F6', borderRadius: '10px', padding: '12px' }}>
                <p style={{ fontSize: '13px', fontWeight: 700, color: MAP_CARD_TEXT }}>
                  {trip.origin} → {trip.destination}
                </p>
                <p style={{ fontSize: '12px', color: MAP_CARD_TEXT_SECONDARY }}>
                  Scheduled · {trip.departureTime.slice(0, 16).replace('T', ' ')} · {trip.availableSeats} seat(s) left ·{' '}
                  {trip.farePerSeat.toLocaleString('en-US')} RWF/seat
                </p>
              </div>
            ))
          ) : (
            <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_SECONDARY }}>
              No scheduled bus trips found to {selectedPlace.displayName} right now.
            </p>
          )}
        </div>
      ) : travelMode === 'TRANSIT' ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {transitSearching ? (
            <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_SECONDARY }}>Finding real transit journeys…</p>
          ) : transitJourneys && transitJourneys.length > 0 ? (
            transitJourneys.map((journey, i) => {
              const fmt = (s: number) => `${String(Math.floor(s / 3600) % 24).padStart(2, '0')}:${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}`;
              return (
                <div key={i} style={{ background: '#F2F4F6', borderRadius: '10px', padding: '12px' }}>
                  <p style={{ fontSize: '13px', fontWeight: 700, color: MAP_CARD_TEXT }}>
                    {journey.route.shortName ?? journey.route.longName ?? 'Route'} · {journey.originStop.name} → {journey.destinationStop.name}
                  </p>
                  <p style={{ fontSize: '12px', color: MAP_CARD_TEXT_SECONDARY }}>
                    Scheduled departure {fmt(journey.departureSecondsAfterMidnight)} · arrives {fmt(journey.arrivalSecondsAfterMidnight)} ·{' '}
                    {Math.round(journey.walkToOriginStopKm * 1000)}m walk to stop
                  </p>
                </div>
              );
            })
          ) : (
            <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_SECONDARY }}>
              No real direct transit journey found to {selectedPlace.displayName} right now.
            </p>
          )}
        </div>
      ) : route ? (
        <div>
          <p style={{ fontSize: '13px', color: MAP_CARD_TEXT_SECONDARY }}>
            {travelModeIcon(travelMode as TravelMode)} {route.distanceKm.toFixed(1)} km · {Math.round(route.durationMinutes)} min by real road, via itunda's own self-hosted OSRM
          </p>
          {/* Real alternative-route picker -- only rendered when OSRM genuinely offered
              more than one real route for this trip (see lib/maps.ts's
              getDirectionsAlternatives doc comment); a real single-route trip stays
              exactly as it looked before this feature existed. */}
          {routeAlternatives && routeAlternatives.length > 1 && (
            <div style={{ display: 'flex', gap: '6px', margin: '6px 0' }}>
              {routeAlternatives.map((alt, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => selectRouteAlternative(i)}
                  style={{
                    flex: 1, padding: '5px 0', borderRadius: '8px', fontSize: '11px', fontWeight: 700,
                    background: selectedRouteIndex === i ? 'var(--itunda-indigo)' : '#F2F4F6',
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
              style={{ fontSize: '12px', color: 'var(--itunda-indigo)', fontWeight: 700, marginTop: '4px' }}
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
        <button className="itunda-btn itunda-btn-primary" disabled={routing} onClick={() => handleGetDirections()}>
          {routing ? 'Finding real route…' : 'Directions'}
        </button>
      )}
    </>
  );
}
