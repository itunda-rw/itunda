import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { type PlaceSearchResult } from './lib/maps';
import {
  acceptDesignatedDriverTrip, cancelDesignatedDriverTrip, completeDesignatedDriverTrip, fetchAvailableDesignatedDriverTrips,
  fetchMyDesignatedDriverDriverTrips, fetchMyDesignatedDriverProfile, fetchMyDesignatedDriverTrips, registerAsDesignatedDriver,
  requestDesignatedDriverTrip, setDesignatedDriverAvailability, startDesignatedDriverTrip, updateDesignatedDriverLocation,
  type DesignatedDriver, type DesignatedDriverTrip,
} from './lib/designatedDriver';
import { PlaceSearchInput } from './BankDashboard';

const DESIGNATED_DRIVER_STATUS_LABEL: Record<DesignatedDriverTrip['status'], string> = {
  REQUESTED: 'Finding a driver…',
  ACCEPTED: 'Driver on the way',
  DRIVING: 'Driver is driving you home',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

function DesignatedDriverTripCard({ trip, action }: { trip: DesignatedDriverTrip; action?: React.ReactNode }) {
  return (
    <div style={{ padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{trip.pickupAddress}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '2px 0' }}>→ {trip.dropoffAddress}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
            {trip.vehicleMake} {trip.vehicleModel} · {trip.vehiclePlate}
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{trip.distanceKm.toFixed(1)} km · {trip.fare.toLocaleString()} RWF</p>
        </div>
        <span style={{
          fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, padding: '4px 8px', borderRadius: '6px',
          color: trip.status === 'CANCELLED' ? 'var(--itunda-red)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-500)' : 'var(--itunda-indigo)',
          backgroundColor: trip.status === 'CANCELLED' ? 'var(--itunda-red-light)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-100)' : '#E8F0FE',
        }}>
          {DESIGNATED_DRIVER_STATUS_LABEL[trip.status]}
        </span>
      </div>
      {action}
    </div>
  );
}

// Real Kakao T 대리운전 (designated driver, item 221) -- see lib/designatedDriver.ts's
// own doc comment for the full sourced account. Mirrors RidesView's own Ride/Drive
// toggle structure, but the "driver" here drives the CUSTOMER'S OWN CAR, not their own
// vehicle -- vehicleMake/vehicleModel/vehiclePlate describe that car, purely
// informational text the driver sees before arriving.
export function DesignatedDriverView() {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'REQUEST' | 'DRIVE'>('REQUEST');

  // Customer side
  const [pickup, setPickup] = useState<PlaceSearchResult | null>(null);
  const [dropoff, setDropoff] = useState<PlaceSearchResult | null>(null);
  const [vehicleMake, setVehicleMake] = useState('');
  const [vehicleModel, setVehicleModel] = useState('');
  const [vehiclePlate, setVehiclePlate] = useState('');
  const [myTrips, setMyTrips] = useState<DesignatedDriverTrip[] | null>(null);
  const [requesting, setRequesting] = useState(false);
  const [tripError, setTripError] = useState<string | null>(null);
  const [busyTripId, setBusyTripId] = useState<string | null>(null);

  const loadMyTrips = () => {
    fetchMyDesignatedDriverTrips().then(setMyTrips).catch((err) => setTripError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab !== 'REQUEST') return;
    loadMyTrips();
    const interval = setInterval(loadMyTrips, 4000);
    return () => clearInterval(interval);
  }, [subTab]);

  const activeTrip = (myTrips ?? []).find((t) => t.status === 'REQUESTED' || t.status === 'ACCEPTED' || t.status === 'DRIVING');
  const pastTrips = (myTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  const handleRequestTrip = async () => {
    if (!pickup || !dropoff || !vehicleMake.trim() || !vehicleModel.trim() || !vehiclePlate.trim()) return;
    setRequesting(true);
    setTripError(null);
    try {
      await requestDesignatedDriverTrip(
        pickup.displayName, pickup.latitude, pickup.longitude, dropoff.displayName, dropoff.latitude, dropoff.longitude,
        vehicleMake.trim(), vehicleModel.trim(), vehiclePlate.trim(),
      );
      setPickup(null);
      setDropoff(null);
      setVehicleMake('');
      setVehicleModel('');
      setVehiclePlate('');
      loadMyTrips();
    } catch (err) {
      setTripError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRequesting(false);
    }
  };

  const handleCancelTrip = async (tripId: string) => {
    setBusyTripId(tripId);
    try {
      await cancelDesignatedDriverTrip(tripId);
      loadMyTrips();
    } catch (err) {
      setTripError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyTripId(null);
    }
  };

  // Driver side
  const [driver, setDriver] = useState<DesignatedDriver | null | undefined>(undefined);
  const [licenseNumber, setLicenseNumber] = useState('');
  const [registeringDriver, setRegisteringDriver] = useState(false);
  const [availableTrips, setAvailableTrips] = useState<DesignatedDriverTrip[] | null>(null);
  const [myDriverTrips, setMyDriverTrips] = useState<DesignatedDriverTrip[] | null>(null);
  const [driverError, setDriverError] = useState<string | null>(null);
  const [busyDriverTripId, setBusyDriverTripId] = useState<string | null>(null);

  const loadDriver = () => {
    fetchMyDesignatedDriverProfile()
      .then(setDriver)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'DESIGNATED_DRIVER_NOT_REGISTERED') setDriver(null);
        else setDriverError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
  };

  useEffect(() => {
    if (subTab === 'DRIVE') loadDriver();
  }, [subTab]);

  const loadDriverTrips = () => {
    Promise.all([fetchAvailableDesignatedDriverTrips(), fetchMyDesignatedDriverDriverTrips()])
      .then(([a, m]) => { setAvailableTrips(a); setMyDriverTrips(m); })
      .catch((err) => setDriverError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab !== 'DRIVE' || !driver) return;
    loadDriverTrips();
    const interval = setInterval(loadDriverTrips, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab, driver?.id]);

  const handleRegisterDriver = async () => {
    if (!licenseNumber.trim()) return;
    setRegisteringDriver(true);
    setDriverError(null);
    try {
      setDriver(await registerAsDesignatedDriver(licenseNumber.trim()));
      setLicenseNumber('');
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): this
      // resolve-forward fix already shipped on Android/iOS but bank-mfe never got it
      // -- a double-tap or a second device registering first isn't really a failure.
      if (err instanceof ApiError && err.code === 'DESIGNATED_DRIVER_ALREADY_REGISTERED') {
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
      const updated = await setDesignatedDriverAvailability(!driver.available);
      setDriver(updated);
      if (updated.available && navigator.geolocation) {
        navigator.geolocation.getCurrentPosition((pos) => {
          updateDesignatedDriverLocation(pos.coords.latitude, pos.coords.longitude).then(setDriver).catch(() => {});
        });
      }
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleDriverTripAction = async (tripId: string, action: (id: string) => Promise<DesignatedDriverTrip>) => {
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

  const activeDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'ACCEPTED' || t.status === 'DRIVING');
  const pastDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'REQUEST' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('REQUEST')} style={{ flex: 1 }}
        >
          Get a driver
        </button>
        <button
          className={subTab === 'DRIVE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('DRIVE')} style={{ flex: 1 }}
        >
          Drive
        </button>
      </div>

      {subTab === 'REQUEST' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {tripError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{tripError}</p>}
          {activeTrip ? (
            <DesignatedDriverTripCard
              trip={activeTrip}
              action={
                activeTrip.status === 'REQUESTED' && (
                  <button
                    className="itunda-btn itunda-btn-secondary" disabled={busyTripId === activeTrip.id}
                    onClick={() => handleCancelTrip(activeTrip.id)} style={{ marginTop: '8px', width: '100%' }}
                  >
                    {busyTripId === activeTrip.id ? '…' : 'Cancel'}
                  </button>
                )
              }
            />
          ) : (
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Get a designated driver</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                A real professional driver comes to you and drives YOUR OWN CAR home.
              </p>
              <PlaceSearchInput label="Pickup" placeholder="Where are you now?" value={pickup} onSelect={setPickup} />
              <PlaceSearchInput label="Drop-off" placeholder="Where's home?" value={dropoff} onSelect={setDropoff} />
              <input
                type="text" value={vehicleMake} placeholder="Car make (e.g. Toyota)" onChange={(e) => setVehicleMake(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
              />
              <input
                type="text" value={vehicleModel} placeholder="Car model (e.g. RAV4)" onChange={(e) => setVehicleModel(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
              />
              <input
                type="text" value={vehiclePlate} placeholder="License plate" onChange={(e) => setVehiclePlate(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
              />
              <button
                className="itunda-btn itunda-btn-primary" style={{ width: '100%' }}
                disabled={requesting || !pickup || !dropoff || !vehicleMake.trim() || !vehicleModel.trim() || !vehiclePlate.trim()}
                onClick={handleRequestTrip}
              >
                {requesting ? 'Requesting…' : 'Request a driver'}
              </button>
            </div>
          )}
          {pastTrips.length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past trips</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {pastTrips.map((t) => <DesignatedDriverTripCard key={t.id} trip={t} />)}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'DRIVE' && (
        <div>
          {driver === undefined && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>}
          {driver === null && (
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Become a designated driver</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                Any itunda user can register. License number is self-declared, not verified against a real registry.
              </p>
              {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{driverError}</p>}
              <input
                type="text" value={licenseNumber} placeholder="License number" onChange={(e) => setLicenseNumber(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
              />
              <button
                className="itunda-btn itunda-btn-primary" style={{ width: '100%' }}
                disabled={registeringDriver || !licenseNumber.trim()} onClick={handleRegisterDriver}
              >
                {registeringDriver ? 'Registering…' : 'Register'}
              </button>
            </div>
          )}
          {driver && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{driver.available ? 'Online' : 'Offline'}</p>
                <button className="itunda-btn itunda-btn-secondary" onClick={handleToggleAvailable}>
                  {driver.available ? 'Go offline' : 'Go online'}
                </button>
              </div>
              {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{driverError}</p>}
              {activeDriverTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Active</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {activeDriverTrips.map((t) => (
                      <DesignatedDriverTripCard
                        key={t.id} trip={t}
                        action={
                          <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                            {t.status === 'ACCEPTED' && (
                              <button
                                className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id} style={{ flex: 1 }}
                                onClick={() => handleDriverTripAction(t.id, startDesignatedDriverTrip)}
                              >
                                {busyDriverTripId === t.id ? '…' : 'Start driving'}
                              </button>
                            )}
                            {t.status === 'DRIVING' && (
                              <button
                                className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id} style={{ flex: 1 }}
                                onClick={() => handleDriverTripAction(t.id, completeDesignatedDriverTrip)}
                              >
                                {busyDriverTripId === t.id ? '…' : 'Complete'}
                              </button>
                            )}
                          </div>
                        }
                      />
                    ))}
                  </div>
                </div>
              )}
              {availableTrips && availableTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Nearby requests</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {availableTrips.map((t) => (
                      <DesignatedDriverTripCard
                        key={t.id} trip={t}
                        action={
                          <button
                            className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id} style={{ width: '100%', marginTop: '8px' }}
                            onClick={() => handleDriverTripAction(t.id, acceptDesignatedDriverTrip)}
                          >
                            {busyDriverTripId === t.id ? '…' : 'Accept'}
                          </button>
                        }
                      />
                    ))}
                  </div>
                </div>
              )}
              {pastDriverTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {pastDriverTrips.map((t) => <DesignatedDriverTripCard key={t.id} trip={t} />)}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
