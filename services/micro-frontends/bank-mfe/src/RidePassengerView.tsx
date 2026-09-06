// Split out of RidesView.tsx (2026-09-02, same Rides domain-split slice) purely to
// stay under the 500-line new-file cap -- the RIDE (passenger) and DRIVE (driver)
// modes are two fully independent state machines with zero shared state, a clean
// split point. See RideDriverView.tsx for the driver half and RidesView.tsx for the
// shell that switches between them.
import { useEffect, useState } from 'react';
import { Car } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { type PlaceSearchResult } from './lib/maps';
import {
  cancelRideTrip, estimateRideFare, fetchMyTrips, fetchRideTripPin, fetchTripStops, requestRideTrip, sendStatusToTrustedContacts, shareRideTripStatus,
  type RideTrip, type RideTripStop,
} from './lib/rideshare';
import { DriverRatingSection, RideReviewPrompt, RideTripCard, TipDriverPrompt, TrustedContactsSection } from './RideCards';
import RideLiveDriverMap from './RideLiveDriverMap';
import { PlaceSearchInput, ShareFavoritesModal } from './BankDashboard';

export function RidePassengerView({ onReportIssue }: { onReportIssue: (transactionId: string) => void }) {
  const { t } = useI18n();
  const [pickup, setPickup] = useState<PlaceSearchResult | null>(null);
  const [dropoff, setDropoff] = useState<PlaceSearchResult | null>(null);
  // Real Kakao T-style multi-stop rides (item 214) -- up to 3 real extra waypoints
  // between pickup and dropoff, matching Kakao T's own real cap.
  const [stops, setStops] = useState<(PlaceSearchResult | null)[]>([]);
  const [activeTripStops, setActiveTripStops] = useState<RideTripStop[] | null>(null);
  const [myTrips, setMyTrips] = useState<RideTrip[] | null>(null);
  const [requesting, setRequesting] = useState(false);
  const [rideError, setRideError] = useState<string | null>(null);
  const [busyTripId, setBusyTripId] = useState<string | null>(null);
  // Real Uber "Verify Your Ride" PIN -- see lib/rideshare.ts's own doc comment. Fetched
  // once a driver is assigned so the passenger can read it aloud before pickup.
  const [activeTripPin, setActiveTripPin] = useState<string | null>(null);
  // Real Uber "Share Trip Status" -- see lib/rideshare.ts's own doc comment.
  const [showShareTripModal, setShowShareTripModal] = useState(false);
  const [shareTripError, setShareTripError] = useState<string | null>(null);
  // Real Uber Safety "Send Status" -- see TrustedContactsSection's own doc comment.
  const [sendStatusBusy, setSendStatusBusy] = useState(false);
  const [sendStatusResult, setSendStatusResult] = useState<string | null>(null);
  // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- 'now' is unchanged
  // ASAP dispatch; 'later' holds a datetime-local value the passenger picks.
  const [rideTiming, setRideTiming] = useState<'now' | 'later'>('now');
  const [scheduledAt, setScheduledAt] = useState('');
  // Real Kakao T-style post-trip driver rating (item 213) -- tracks which completed
  // trips have already been rated this session, so a submitted/already-reviewed
  // prompt doesn't linger. See RideReviewPrompt's own doc comment.
  const [reviewedTripIds, setReviewedTripIds] = useState<Set<string>>(new Set());
  // Same real optimistic-hide pattern as reviewedTripIds above -- tipDriver's own
  // response includes the updated trip with tipAmount now set, but pastTrips itself
  // isn't refetched on every tip, so this tracks which trips were tipped THIS session.
  const [tippedTripIds, setTippedTripIds] = useState<Set<string>>(new Set());
  // Real Uber "Upfront Fare" simplification (2026-08-24) -- see lib/rideshare.ts's own
  // estimateRideFare doc comment for the full sourced account. Debounced the same
  // 350ms PlaceSearchInput's own place-search request already uses, since both pickup
  // and dropoff selecting in quick succession would otherwise fire a fetch per step.
  const [estimatedFare, setEstimatedFare] = useState<number | null>(null);
  useEffect(() => {
    if (!pickup || !dropoff) { setEstimatedFare(null); return; }
    const handle = setTimeout(() => {
      estimateRideFare(pickup.latitude, pickup.longitude, dropoff.latitude, dropoff.longitude)
        .then(setEstimatedFare)
        .catch(() => setEstimatedFare(null));
    }, 350);
    return () => clearTimeout(handle);
  }, [pickup, dropoff]);

  const loadMyTrips = () => {
    fetchMyTrips().then(setMyTrips).catch((err) => setRideError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    loadMyTrips();
    const interval = setInterval(loadMyTrips, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const activeTrip = (myTrips ?? []).find((t) => t.status === 'REQUESTED' || t.status === 'DRIVER_ASSIGNED' || t.status === 'IN_PROGRESS');
  const pastTrips = (myTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  useEffect(() => {
    if (!activeTrip) { setActiveTripStops(null); return; }
    fetchTripStops(activeTrip.id).then((s) => setActiveTripStops(s.length > 0 ? s : null)).catch(() => {});
  }, [activeTrip?.id]);

  // Real Uber "Verify Your Ride" PIN -- fetched once a driver is assigned (before that,
  // there's no driver yet to tell it to). A pre-existing REQUESTED trip that never
  // reaches DRIVER_ASSIGNED simply never shows a PIN, matching real Uber behavior.
  useEffect(() => {
    if (!activeTrip || activeTrip.status === 'REQUESTED') { setActiveTripPin(null); return; }
    fetchRideTripPin(activeTrip.id).then(setActiveTripPin).catch(() => setActiveTripPin(null));
  }, [activeTrip?.id, activeTrip?.status]);

  const handleRequestRide = async () => {
    if (!pickup || !dropoff) return;
    if (rideTiming === 'later' && !scheduledAt) return;
    const resolvedStops = stops.filter((s): s is PlaceSearchResult => s !== null);
    setRequesting(true);
    setRideError(null);
    try {
      await requestRideTrip(
        pickup.displayName, pickup.latitude, pickup.longitude, dropoff.displayName, dropoff.latitude, dropoff.longitude,
        rideTiming === 'later' ? new Date(scheduledAt).toISOString() : undefined,
        resolvedStops.length > 0 ? resolvedStops.map((s) => ({ address: s.displayName, latitude: s.latitude, longitude: s.longitude })) : undefined,
      );
      setPickup(null);
      setDropoff(null);
      setRideTiming('now');
      setScheduledAt('');
      setStops([]);
      loadMyTrips();
    } catch (err) {
      setRideError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRequesting(false);
    }
  };

  const handleCancelTrip = async (tripId: string) => {
    setBusyTripId(tripId);
    try {
      await cancelRideTrip(tripId);
      loadMyTrips();
    } catch (err) {
      setRideError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyTripId(null);
    }
  };

  const handleShareTripStatus = async (tripId: string, conversationId: string) => {
    setShareTripError(null);
    try {
      await shareRideTripStatus(tripId, conversationId);
      setShowShareTripModal(false);
    } catch (err) {
      setShareTripError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  // Real Uber Safety "Send Status" -- one tap fans this trip's live status out to every
  // real trusted contact at once, distinct from handleShareTripStatus's own per-share
  // conversation pick above.
  const handleSendStatus = async (tripId: string) => {
    setSendStatusBusy(true);
    setSendStatusResult(null);
    try {
      const sentCount = await sendStatusToTrustedContacts(tripId);
      setSendStatusResult(sentCount > 0 ? `Sent to ${sentCount} trusted contact${sentCount === 1 ? '' : 's'}.` : 'Add a trusted contact first to send your status.');
    } catch (err) {
      setSendStatusResult(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSendStatusBusy(false);
    }
  };

  return (
    <div>
      {activeTrip ? (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your ride</h4>
          <RideTripCard
            trip={activeTrip} stops={activeTripStops}
            action={(
              <>
                {activeTripPin && activeTrip.status === 'DRIVER_ASSIGNED' && (
                  <div
                    style={{
                      textAlign: 'center', padding: '12px', borderRadius: '10px',
                      backgroundColor: 'var(--itunda-indigo-light)', marginBottom: '4px',
                    }}
                  >
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Tell your driver this PIN before you get in</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 700, letterSpacing: '4px', color: 'var(--itunda-indigo)' }}>{activeTripPin}</p>
                  </div>
                )}
                {(activeTrip.status === 'DRIVER_ASSIGNED' || activeTrip.status === 'IN_PROGRESS') && (
                  <RideLiveDriverMap
                    tripId={activeTrip.id} fromLat={activeTrip.pickupLatitude} fromLng={activeTrip.pickupLongitude}
                    toLat={activeTrip.dropoffLatitude} toLng={activeTrip.dropoffLongitude}
                  />
                )}
                {activeTrip.driverId && <DriverRatingSection driverId={activeTrip.driverId} />}
                <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowShareTripModal(true)}>
                  Share trip status
                </button>
                {shareTripError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{shareTripError}</p>}
                <button className="itunda-btn itunda-btn-secondary" disabled={sendStatusBusy} onClick={() => handleSendStatus(activeTrip.id)}>
                  {sendStatusBusy ? 'Sending…' : 'Send status to trusted contacts'}
                </button>
                {sendStatusResult && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{sendStatusResult}</p>}
                {activeTrip.status !== 'IN_PROGRESS' && (
                  <button className="itunda-btn itunda-btn-danger" disabled={busyTripId === activeTrip.id} onClick={() => handleCancelTrip(activeTrip.id)}>
                    {busyTripId === activeTrip.id ? 'Cancelling…' : 'Cancel ride'}
                  </button>
                )}
              </>
            )}
          />
          {showShareTripModal && (
            <ShareFavoritesModal
              title="Share ride status to…"
              onShare={(conversationId) => handleShareTripStatus(activeTrip.id, conversationId)}
              onClose={() => setShowShareTripModal(false)}
            />
          )}
        </div>
      ) : (
        // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
        // form section shown when there's no active trip.
        <div style={{ padding: '10px 0' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <Car size={18} color="var(--itunda-indigo)" /> Request a ride
          </h3>
          <PlaceSearchInput label="Pickup" placeholder="Where from?" value={pickup} onSelect={setPickup} />
          {stops.map((stop, i) => (
            <PlaceSearchInput
              key={i} label={`Stop ${i + 1}`} placeholder="Add a stop" value={stop}
              onSelect={(place) => setStops((prev) => prev.map((s, idx) => (idx === i ? place : s)))}
            />
          ))}
          <PlaceSearchInput label="Dropoff" placeholder="Where to?" value={dropoff} onSelect={setDropoff} />
          {stops.length < 3 && (
            <button
              type="button" onClick={() => setStops((prev) => [...prev, null])}
              style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-indigo)', marginBottom: '12px' }}
            >
              + Add a stop
            </button>
          )}

          <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '12px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
            {(['now', 'later'] as const).map((v) => (
              <button
                key={v} type="button" onClick={() => setRideTiming(v)}
                style={{
                  flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
                  color: rideTiming === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                  backgroundColor: rideTiming === v ? 'var(--itunda-indigo)' : 'transparent',
                }}
              >
                {v === 'now' ? 'Ride now' : 'Schedule'}
              </button>
            ))}
          </div>
          {rideTiming === 'later' && (
            <input
              type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
            />
          )}

          {pickup && dropoff && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
              {estimatedFare != null
                ? `Estimated fare: ${estimatedFare.toLocaleString('en-US')} RWF`
                : 'Estimating fare…'}
            </p>
          )}
          <button
            className="itunda-btn itunda-btn-primary"
            disabled={!pickup || !dropoff || requesting || (rideTiming === 'later' && !scheduledAt) || stops.some((s) => s === null)}
            onClick={handleRequestRide} style={{ width: '100%' }}
          >
            {requesting ? 'Requesting…' : rideTiming === 'later' ? 'Schedule ride' : 'Request ride'}
          </button>
        </div>
      )}

      {rideError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{rideError}</p>}

      <TrustedContactsSection />

      {pastTrips.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past rides</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {pastTrips.map((t) => (
              <RideTripCard
                key={t.id} trip={t}
                action={(
                  <>
                    {t.status === 'COMPLETED' && t.driverId && !reviewedTripIds.has(t.id) && (
                      <RideReviewPrompt tripId={t.id} onSubmitted={() => setReviewedTripIds((prev) => new Set(prev).add(t.id))} />
                    )}
                    {t.status === 'COMPLETED' && t.driverId && !t.tipAmount && !tippedTripIds.has(t.id) && (
                      <TipDriverPrompt tripId={t.id} onTipped={() => setTippedTripIds((prev) => new Set(prev).add(t.id))} />
                    )}
                    {t.status === 'COMPLETED' && (
                      <button
                        className="itunda-btn itunda-btn-secondary"
                        style={{ marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                        onClick={() => onReportIssue(t.transactionId)}
                      >
                        Report an issue
                      </button>
                    )}
                  </>
                )}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
