import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import {
  endParkingSession, fetchMyParkingHistory, fetchMyParkingSpots, fetchNearbyParkingSpots, registerParkingSpot, setParkingSpotAvailability,
  startParkingSession, type ParkingSession, type ParkingSpot,
} from './lib/parking';

// Extracted from BankDashboard.tsx (2026-08-10) into its own lazy-loaded chunk --
// see InsuranceView.tsx's own doc comment for the full account of why. Self-contained:
// no shared state or helper components with any other screen.
export default function ParkingView() {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'RENT' | 'OWN'>('RENT');

  // Renter side
  const [nearbySpots, setNearbySpots] = useState<ParkingSpot[] | null>(null);
  const [activeSession, setActiveSession] = useState<ParkingSession | null>(null);
  const [rentalHistory, setRentalHistory] = useState<ParkingSession[] | null>(null);
  const [renterError, setRenterError] = useState<string | null>(null);
  const [busySpotId, setBusySpotId] = useState<string | null>(null);
  const [endingSession, setEndingSession] = useState(false);
  const [justCompletedSession, setJustCompletedSession] = useState<ParkingSession | null>(null);
  const [rentalHistoryPage, setRentalHistoryPage] = useState(0);
  const [rentalHistoryHasMore, setRentalHistoryHasMore] = useState(false);
  const [loadingMoreRentalHistory, setLoadingMoreRentalHistory] = useState(false);

  const loadRenterData = () => {
    if (!navigator.geolocation) {
      setRenterError('Location access is required to find nearby parking.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        fetchNearbyParkingSpots(pos.coords.latitude, pos.coords.longitude)
          .then(setNearbySpots)
          .catch((err) => setRenterError(err instanceof ApiError ? err.message : t('common.loadError')));
      },
      () => setRenterError('Could not access your location.'),
    );
    fetchMyParkingHistory(0)
      .then((r) => { setRentalHistory(r.sessions); setRentalHistoryHasMore(r.page + 1 < r.totalPages); })
      .catch(() => {});
  };

  const loadMoreRentalHistory = () => {
    const nextPage = rentalHistoryPage + 1;
    setLoadingMoreRentalHistory(true);
    fetchMyParkingHistory(nextPage)
      .then((r) => {
        setRentalHistory((prev) => [...(prev ?? []), ...r.sessions]);
        setRentalHistoryPage(nextPage);
        setRentalHistoryHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreRentalHistory(false));
  };

  useEffect(() => {
    if (subTab !== 'RENT') return;
    loadRenterData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab]);

  useEffect(() => {
    const active = (rentalHistory ?? []).find((r) => r.status === 'ACTIVE');
    setActiveSession(active ?? null);
  }, [rentalHistory]);

  const handleStartSession = (spotId: string) => {
    setBusySpotId(spotId);
    setRenterError(null);
    startParkingSession(spotId)
      .then((session) => { setActiveSession(session); setBusySpotId(null); })
      .catch((err) => { setRenterError(err instanceof ApiError ? err.message : t('common.actionError')); setBusySpotId(null); });
  };

  const handleEndSession = () => {
    if (!activeSession) return;
    setEndingSession(true);
    setRenterError(null);
    endParkingSession(activeSession.id)
      .then((session) => {
        setActiveSession(null);
        setJustCompletedSession(session);
        setEndingSession(false);
        loadRenterData();
      })
      .catch((err) => { setRenterError(err instanceof ApiError ? err.message : t('common.actionError')); setEndingSession(false); });
  };

  // Owner side
  const [mySpots, setMySpots] = useState<ParkingSpot[] | null>(null);
  const [spotAddress, setSpotAddress] = useState('');
  const [spotHourlyRate, setSpotHourlyRate] = useState('');
  const [registering, setRegistering] = useState(false);
  const [ownerError, setOwnerError] = useState<string | null>(null);

  const loadMySpots = () => {
    fetchMyParkingSpots().then(setMySpots).catch((err) => setOwnerError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab === 'OWN') loadMySpots();
  }, [subTab]);

  const handleRegisterSpot = () => {
    if (!navigator.geolocation || !spotAddress.trim() || !spotHourlyRate) return;
    const rate = Number(spotHourlyRate);
    if (!Number.isFinite(rate) || rate <= 0) return;
    setRegistering(true);
    setOwnerError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        registerParkingSpot(spotAddress.trim(), pos.coords.latitude, pos.coords.longitude, rate)
          .then(() => { setSpotAddress(''); setSpotHourlyRate(''); loadMySpots(); setRegistering(false); })
          .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : t('common.actionError')); setRegistering(false); });
      },
      () => { setOwnerError('Could not access your location.'); setRegistering(false); },
    );
  };

  const handleToggleSpotAvailable = (spot: ParkingSpot) => {
    setBusySpotId(spot.id);
    setOwnerError(null);
    setParkingSpotAvailability(spot.id, !spot.available)
      .then(() => { loadMySpots(); setBusySpotId(null); })
      .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : t('common.actionError')); setBusySpotId(null); });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'RENT' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('RENT')} style={{ flex: 1 }}
        >
          Find parking
        </button>
        <button
          className={subTab === 'OWN' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('OWN')} style={{ flex: 1 }}
        >
          My spots
        </button>
      </div>

      {subTab === 'RENT' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {renterError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{renterError}</p>}
          {/* Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
              dropped itunda-card -- a lone conditional section on this screen. */}
          {justCompletedSession && (
            <div style={{ textAlign: 'center', padding: '10px 0' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Parking complete</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-24-size)', fontWeight: 700, margin: '8px 0' }}>{(justCompletedSession.totalFare ?? 0).toLocaleString('en-US')} RWF</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{justCompletedSession.durationMinutes} minutes</p>
              <button className="itunda-btn itunda-btn-secondary" onClick={() => setJustCompletedSession(null)} style={{ marginTop: '12px' }}>
                Done
              </button>
            </div>
          )}
          {!justCompletedSession && activeSession && (
            <div style={{ padding: '10px 0' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>🅿️ Parked now</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                Fare is calculated by elapsed time (rounded up to the next hour) once you check out.
              </p>
              <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={endingSession} onClick={handleEndSession}>
                {endingSession ? 'Checking out…' : 'Check out'}
              </button>
            </div>
          )}
          {!justCompletedSession && !activeSession && (
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                Nearby parking, within 5 km of your real location.
              </p>
              {nearbySpots === null ? (
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
              ) : nearbySpots.length === 0 ? (
                <EmptyState message="No parking nearby right now — try a different area or check back soon." />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card --
                      entity list (spots to check into), no divider needed. */}
                  {nearbySpots.map((spot) => (
                    <div key={spot.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
                      <div>
                        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{spot.address}</p>
                        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{spot.hourlyRate.toLocaleString('en-US')} RWF/hour</p>
                      </div>
                      <button
                        className="itunda-btn itunda-btn-primary" disabled={busySpotId === spot.id}
                        onClick={() => handleStartSession(spot.id)}
                      >
                        {busySpotId === spot.id ? '…' : 'Check in'}
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          {rentalHistory && rentalHistory.filter((r) => r.status === 'COMPLETED').length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past sessions</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {/* Real fix (2026-08-24, flat-design sweep): history log of past
                    sessions -- reused .itunda-flat-section for its per-row divider
                    with :last-child auto-dropping the trailing one
                    (docs/DESIGN_REFERENCES.md §274). */}
                {rentalHistory.filter((r) => r.status === 'COMPLETED').map((r) => (
                  <div key={r.id} className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.durationMinutes} min</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{(r.totalFare ?? 0).toLocaleString('en-US')} RWF</p>
                  </div>
                ))}
                {rentalHistoryHasMore && (
                  <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreRentalHistory} onClick={loadMoreRentalHistory}>
                    {loadingMoreRentalHistory ? 'Loading…' : 'Load more'}
                  </button>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'OWN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {ownerError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{ownerError}</p>}
          {/* Real fix (2026-08-24, flat-design sweep): lone form section on this tab. */}
          <div style={{ padding: '10px 0' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>List your spot</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
              Uses your real current location as the spot's location.
            </p>
            <input
              type="text" value={spotAddress} placeholder="Address (e.g. Kigali Heights driveway)" onChange={(e) => setSpotAddress(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
            />
            <input
              type="number" value={spotHourlyRate} placeholder="Hourly rate (RWF)" onChange={(e) => setSpotHourlyRate(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
            />
            <button
              className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={registering || !spotAddress.trim() || !spotHourlyRate}
              onClick={handleRegisterSpot}
            >
              {registering ? 'Registering…' : 'Register spot'}
            </button>
          </div>
          {mySpots && mySpots.length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your spots</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {/* Real fix (2026-08-24, flat-design sweep): entity list (spots owned),
                    no divider needed. */}
                {mySpots.map((spot) => (
                  <div key={spot.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
                    <div>
                      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{spot.address}</p>
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{spot.hourlyRate.toLocaleString('en-US')} RWF/hour</p>
                    </div>
                    <button className="itunda-btn itunda-btn-secondary" disabled={busySpotId === spot.id} onClick={() => handleToggleSpotAvailable(spot)}>
                      {busySpotId === spot.id ? '…' : spot.available ? 'Available' : 'Unavailable'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
