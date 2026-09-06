// Split out of RidesView.tsx (2026-09-02, same Rides domain-split slice) purely to
// stay under the 500-line new-file cap -- see RidePassengerView.tsx's own header
// for the full account of why RIDE/DRIVE split cleanly with zero shared state.
import { useEffect, useState } from 'react';
import { Car } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';
import { EmptyState } from './EmptyState';
import { ApiError } from './lib/api';
import { type AddressSuggestion } from './lib/eats';
import {
  acceptRideTrip, arriveAtRideStop, clearDriverDestination, completeRideTrip, declineRideTrip, driverCancelRideTrip, fetchAvailableTrips, fetchDriverRating,
  fetchMyDriverProfile, fetchMyDriverTrips, fetchMyEarnings, fetchTripStops, registerAsDriver, setDriverAvailability, setDriverDestination, startRideTrip, updateDriverLocation,
  type DriverDailyEarnings, type RideDriver, type RideDriverRating, type RideTrip, type RideTripStop,
} from './lib/rideshare';
import { RideTripCard } from './RideCards';
import { AddressAutocomplete } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

export function RideDriverView() {
  const { t } = useI18n();
  const [driver, setDriver] = useState<RideDriver | null | undefined>(undefined);
  const showDriverSkeleton = useDeferredLoading(driver === undefined);
  const [registeringDriver, setRegisteringDriver] = useState(false);
  // Real gap found live (2026-08-31, market-readiness audit) -- see lib/rideshare.ts's
  // own registerAsDriver doc comment. Same real self-declared field
  // DesignatedDriverView's own licenseNumber input already establishes below.
  const [driverLicenseNumber, setDriverLicenseNumber] = useState('');
  const [availableTrips, setAvailableTrips] = useState<RideTrip[] | null>(null);
  const showTripsSkeleton = useDeferredLoading(availableTrips === null);
  const [myDriverTrips, setMyDriverTrips] = useState<RideTrip[] | null>(null);
  const [driverError, setDriverError] = useState<string | null>(null);
  const [busyDriverTripId, setBusyDriverTripId] = useState<string | null>(null);
  // Real Uber "Verify Your Ride" PIN -- what the driver has typed in for each real
  // active trip, keyed by trip id so multiple trip cards don't share one input.
  const [startPinInputs, setStartPinInputs] = useState<Record<string, string>>({});
  // Real Kakao T-style post-trip driver rating (item 213) -- the real driver's own
  // aggregate rating, computed at read time from every real submitted review.
  const [driverRating, setDriverRating] = useState<RideDriverRating | null>(null);
  // Real Kakao T-style multi-stop rides (item 214) -- keyed by trip id, so each real
  // active trip's own waypoints render independently.
  const [driverTripStops, setDriverTripStops] = useState<Record<string, RideTripStop[]>>({});
  // Real Uber "Destination Filter" + earnings report (uncalled-endpoint sweep
  // follow-up, item 245) -- both real, fully-built backend endpoints found with
  // zero client anywhere on any platform before this.
  const [destinationAddress, setDestinationAddress] = useState('');
  const [destinationBusy, setDestinationBusy] = useState(false);
  const [earnings, setEarnings] = useState<DriverDailyEarnings[] | null>(null);

  const loadDriver = () => {
    fetchMyDriverProfile()
      .then((d) => {
        setDriver(d);
        fetchDriverRating(d.id).then(setDriverRating).catch(() => {});
      })
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RIDE_DRIVER_NOT_REGISTERED') setDriver(null);
        else setDriverError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
  };

  useEffect(() => {
    loadDriver();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadDriverTrips = () => {
    Promise.all([fetchAvailableTrips(), fetchMyDriverTrips()])
      .then(([a, m]) => { setAvailableTrips(a); setMyDriverTrips(m); })
      .catch((err) => setDriverError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (!driver) return;
    loadDriverTrips();
    const interval = setInterval(loadDriverTrips, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [driver?.id]);

  // Real Uber Driver-style earnings report + Destination Filter (uncalled-endpoint
  // sweep follow-up, item 245) -- both real, fully-built backend endpoints found
  // with zero client anywhere on any platform before this.
  useEffect(() => {
    if (!driver) return;
    fetchMyEarnings().then((r) => setEarnings(r.days)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [driver?.id]);

  const handleSetDestination = async (suggestion: AddressSuggestion) => {
    setDestinationBusy(true);
    setDriverError(null);
    try {
      setDriver(await setDriverDestination(suggestion.latitude, suggestion.longitude));
      setDestinationAddress(suggestion.displayName);
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDestinationBusy(false);
    }
  };

  const handleClearDestination = async () => {
    setDestinationBusy(true);
    setDriverError(null);
    try {
      setDriver(await clearDriverDestination());
      setDestinationAddress('');
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDestinationBusy(false);
    }
  };

  const handleRegisterDriver = async () => {
    if (!driverLicenseNumber.trim()) return;
    setRegisteringDriver(true);
    setDriverError(null);
    try {
      setDriver(await registerAsDriver(driverLicenseNumber.trim()));
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): same
      // register-once shape as Eats' own RIDER_ALREADY_REGISTERED, apparently missed
      // when that one was fixed -- a double-tap or a second device registering first
      // isn't really a failure, resolve forward into the real existing profile.
      if (err instanceof ApiError && err.code === 'RIDE_DRIVER_ALREADY_REGISTERED') {
        loadDriver();
      } else {
        setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setRegisteringDriver(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!driver) return;
    try {
      const updated = await setDriverAvailability(!driver.available);
      setDriver(updated);
      if (updated.available && navigator.geolocation) {
        navigator.geolocation.getCurrentPosition((pos) => {
          updateDriverLocation(pos.coords.latitude, pos.coords.longitude).then(setDriver).catch(() => {});
        });
      }
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleDriverTripAction = async (tripId: string, action: (id: string) => Promise<RideTrip>) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await action(tripId);
      loadDriverTrips();
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyDriverTripId(null);
    }
  };

  // Real Uber "Verify Your Ride" PIN -- the driver must enter the exact code the
  // passenger just told them before the trip (and the fare clock) actually starts.
  const handleStartTrip = async (tripId: string) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await startRideTrip(tripId, startPinInputs[tripId] ?? '');
      setStartPinInputs((prev) => { const next = { ...prev }; delete next[tripId]; return next; });
      loadDriverTrips();
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyDriverTripId(null);
    }
  };

  const activeDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'DRIVER_ASSIGNED' || t.status === 'IN_PROGRESS');
  const pastDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  useEffect(() => {
    activeDriverTrips.forEach((t) => {
      fetchTripStops(t.id).then((s) => setDriverTripStops((prev) => (s.length > 0 ? { ...prev, [t.id]: s } : prev))).catch(() => {});
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeDriverTrips.map((t) => t.id).join(',')]);

  const handleArriveAtStop = async (tripId: string) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await arriveAtRideStop(tripId);
      const refreshed = await fetchTripStops(tripId);
      setDriverTripStops((prev) => ({ ...prev, [tripId]: refreshed }));
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyDriverTripId(null);
    }
  };

  if (driver === undefined) {
    return showDriverSkeleton ? <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  if (driver === null) {
    return (
      // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
      // onboarding message.
      <div style={{ textAlign: 'center', padding: '10px 0' }}>
        <Car size={32} color="var(--itunda-indigo)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '6px' }}>Drive with Itunda</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          Earn a real fare for every trip you complete, paid straight to your account.
        </p>
        <input
          type="text" value={driverLicenseNumber} placeholder="Driver's license number" onChange={(e) => setDriverLicenseNumber(e.target.value)}
          style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
        />
        <button className="itunda-btn itunda-btn-primary" onClick={handleRegisterDriver} disabled={registeringDriver || !driverLicenseNumber.trim()}>
          {registeringDriver ? 'Registering…' : 'Become a driver'}
        </button>
        {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '12px' }} role="alert">{driverError}</p>}
      </div>
    );
  }

  return (
    // Real fix (2026-08-24, flat-design sweep): 3 distinct non-exclusive sections
    // shown together -- reused .itunda-flat-section for section-boundary dividers.
    <div>
      <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{driver.available ? "You're online" : "You're offline"}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{driver.available ? 'Visible for new trip requests' : 'Go online to see trip requests'}</p>
          {driverRating && driverRating.count > 0 && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: '#FFC107', fontWeight: 700, marginTop: '4px' }}>
              ★ {driverRating.average?.toFixed(1)} <span style={{ color: 'var(--itunda-grey-500)', fontWeight: 400 }}>({driverRating.count} rating{driverRating.count === 1 ? '' : 's'})</span>
            </p>
          )}
        </div>
        <button className={driver.available ? 'itunda-btn itunda-btn-danger' : 'itunda-btn itunda-btn-primary'} onClick={handleToggleAvailable}>
          {driver.available ? 'Go offline' : 'Go online'}
        </button>
      </div>

      {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{driverError}</p>}

      {/* Real Uber "Destination Filter" -- see RideDriver.destinationLatitude's
          own doc comment. Set once, works across sessions until cleared (no
          expiry client-side; matches the real backend, which never expires it
          on its own either). */}
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Heading somewhere?</p>
        {driver.destinationLatitude != null ? (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-600)' }}>
              Only offered trips heading toward {destinationAddress || 'your destination'}.
            </p>
            <button className="itunda-btn itunda-btn-secondary" disabled={destinationBusy} onClick={handleClearDestination}>
              {destinationBusy ? '…' : 'Clear'}
            </button>
          </div>
        ) : (
          <>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
              Set a destination and you'll only be offered trips heading that direction.
            </p>
            <AddressAutocomplete
              value={destinationAddress}
              onChangeText={setDestinationAddress}
              onSelectSuggestion={handleSetDestination}
              placeholder="Where are you heading?"
            />
          </>
        )}
      </div>

      {earnings && earnings.length > 0 && (
        <div className="itunda-flat-section">
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>This week</p>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Trips</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{earnings.reduce((sum, d) => sum + d.tripCount, 0)}</p>
            </div>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Gross fare</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{earnings.reduce((sum, d) => sum + d.grossFare, 0).toLocaleString('en-US')} RWF</p>
            </div>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Net earnings</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, color: 'var(--itunda-green)' }}>{earnings.reduce((sum, d) => sum + d.netEarnings, 0).toLocaleString('en-US')} RWF</p>
            </div>
          </div>
        </div>
      )}

      {activeDriverTrips.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your active trip</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {activeDriverTrips.map((t) => {
              const tripStops = driverTripStops[t.id];
              const nextStop = tripStops?.find((s) => !s.arrivedAt);
              return (
                <RideTripCard
                  key={t.id} trip={t} stops={tripStops}
                  action={
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                      {t.status === 'IN_PROGRESS' && nextStop && (
                        <button className="itunda-btn itunda-btn-secondary" disabled={busyDriverTripId === t.id} onClick={() => handleArriveAtStop(t.id)}>
                          {busyDriverTripId === t.id ? 'Updating…' : `Arrived at ${nextStop.address}`}
                        </button>
                      )}
                      {t.status === 'DRIVER_ASSIGNED' ? (
                        <>
                          <input
                            type="text" inputMode="numeric" maxLength={4} placeholder="Ask passenger for their 4-digit PIN"
                            value={startPinInputs[t.id] ?? ''}
                            onChange={(e) => setStartPinInputs((prev) => ({ ...prev, [t.id]: e.target.value.replace(/\D/g, '') }))}
                            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', textAlign: 'center', letterSpacing: '2px' }}
                          />
                          <button
                            className="itunda-btn itunda-btn-primary"
                            disabled={busyDriverTripId === t.id || (startPinInputs[t.id] ?? '').length !== 4}
                            onClick={() => handleStartTrip(t.id)}
                          >
                            {busyDriverTripId === t.id ? 'Updating…' : 'Start trip'}
                          </button>
                          {/* Real driver-side cancel-after-acceptance (Rideshare
                              product-completeness pass, 2026-09-06) -- see backend
                              RideTripService.driverCancelTrip's own doc comment. */}
                          <button
                            className="itunda-btn itunda-btn-danger" disabled={busyDriverTripId === t.id}
                            onClick={() => handleDriverTripAction(t.id, driverCancelRideTrip)}
                          >
                            {busyDriverTripId === t.id ? 'Updating…' : 'Cancel trip'}
                          </button>
                        </>
                      ) : (
                        <button
                          className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id}
                          onClick={() => handleDriverTripAction(t.id, completeRideTrip)}
                        >
                          {busyDriverTripId === t.id ? 'Updating…' : 'Complete trip'}
                        </button>
                      )}
                    </div>
                  }
                />
              );
            })}
          </div>
        </div>
      )}

      {driver.available && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Trip requests near you</h4>
          {availableTrips === null ? (
            showTripsSkeleton ? <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
          ) : availableTrips.length === 0 ? (
            <EmptyState message="No trip requests waiting right now — stay online and you'll be notified." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {availableTrips.map((t) => (
                <RideTripCard
                  key={t.id} trip={t}
                  action={
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id}
                        onClick={() => handleDriverTripAction(t.id, acceptRideTrip)}
                      >
                        {busyDriverTripId === t.id ? 'Accepting…' : 'Accept'}
                      </button>
                      <button
                        className="itunda-btn itunda-btn-secondary" disabled={busyDriverTripId === t.id}
                        onClick={() => handleDriverTripAction(t.id, declineRideTrip)}
                      >
                        Decline
                      </button>
                    </div>
                  }
                />
              ))}
            </div>
          )}
        </div>
      )}

      {pastDriverTrips.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {pastDriverTrips.map((t) => <RideTripCard key={t.id} trip={t} />)}
          </div>
        </div>
      )}
    </div>
  );
}
