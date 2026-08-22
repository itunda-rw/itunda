import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import {
  endBikeAssetRental, fetchMyBikeAssetRentalHistory, fetchMyBikeAssets, fetchNearbyBikeAssets, registerBikeAsset, setBikeAssetAvailability,
  startBikeAssetRental, updateBikeAssetLocation, type BikeAsset, type BikeAssetRentalSession, type BikeAssetType,
} from './lib/bikeshare';
import { BikeGlyph, BikeTypeGlyph } from './icons/ItundaFaceMisc';

// Extracted from BankDashboard.tsx (2026-08-10) into its own lazy-loaded chunk --
// see InsuranceView.tsx's own doc comment for the full account of why. Self-contained:
// no shared state or helper components with any other screen.
export default function BikeShareView() {
  const [subTab, setSubTab] = useState<'RENT' | 'OWN'>('RENT');

  // Rider side
  const [nearbyBikes, setNearbyBikes] = useState<BikeAsset[] | null>(null);
  const [activeRental, setActiveRental] = useState<BikeAssetRentalSession | null>(null);
  const [rentalHistory, setRentalHistory] = useState<BikeAssetRentalSession[] | null>(null);
  const [riderError, setRiderError] = useState<string | null>(null);
  const [busyBikeId, setBusyBikeId] = useState<string | null>(null);
  const [endingRental, setEndingRental] = useState(false);
  const [justCompletedRental, setJustCompletedRental] = useState<BikeAssetRentalSession | null>(null);

  const loadRiderData = () => {
    if (!navigator.geolocation) {
      setRiderError('Location access is required to find nearby bikes.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        fetchNearbyBikeAssets(pos.coords.latitude, pos.coords.longitude)
          .then(setNearbyBikes)
          .catch((err) => setRiderError(err instanceof ApiError ? err.message : 'Could not load nearby bikes.'));
      },
      () => setRiderError('Could not access your location.'),
    );
    fetchMyBikeAssetRentalHistory().then(setRentalHistory).catch(() => {});
  };

  useEffect(() => {
    if (subTab !== 'RENT') return;
    loadRiderData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab]);

  useEffect(() => {
    const active = (rentalHistory ?? []).find((r) => r.status === 'ACTIVE');
    setActiveRental(active ?? null);
  }, [rentalHistory]);

  const handleStartRental = (bikeId: string) => {
    if (!navigator.geolocation) return;
    setBusyBikeId(bikeId);
    setRiderError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        startBikeAssetRental(bikeId, pos.coords.latitude, pos.coords.longitude)
          .then((rental) => { setActiveRental(rental); setBusyBikeId(null); })
          .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not start this rental.'); setBusyBikeId(null); });
      },
      () => { setRiderError('Could not access your location.'); setBusyBikeId(null); },
    );
  };

  const handleEndRental = () => {
    if (!activeRental || !navigator.geolocation) return;
    setEndingRental(true);
    setRiderError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        endBikeAssetRental(activeRental.id, pos.coords.latitude, pos.coords.longitude)
          .then((rental) => {
            setActiveRental(null);
            setJustCompletedRental(rental);
            setEndingRental(false);
            loadRiderData();
          })
          .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not end this rental.'); setEndingRental(false); });
      },
      () => { setRiderError('Could not access your location.'); setEndingRental(false); },
    );
  };

  // Owner side
  const [myBikes, setMyBikes] = useState<BikeAsset[] | null>(null);
  const [bikeType, setBikeType] = useState<BikeAssetType>('REGULAR');
  const [registering, setRegistering] = useState(false);
  const [ownerError, setOwnerError] = useState<string | null>(null);
  // Real fix (2026-08-15) -- updateBikeAssetLocation has existed on the backend and in
  // this file's own lib/bikeshare.ts since day one, called from NEITHER this view NOR
  // Android/iOS's equivalent screens (confirmed by grep across all 3 clients). A bike
  // owner could register a bike and toggle its availability, but never update its
  // location after moving it -- so getNearbyBikes would show a stale position forever
  // after the first registration, real bug for a real P2P bike-share pool.
  const [updatingLocationId, setUpdatingLocationId] = useState<string | null>(null);

  const loadMyBikes = () => {
    fetchMyBikeAssets().then(setMyBikes).catch((err) => setOwnerError(err instanceof ApiError ? err.message : 'Could not load your bikes.'));
  };

  useEffect(() => {
    if (subTab === 'OWN') loadMyBikes();
  }, [subTab]);

  const handleRegisterBike = () => {
    if (!navigator.geolocation) return;
    setRegistering(true);
    setOwnerError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        registerBikeAsset(bikeType, pos.coords.latitude, pos.coords.longitude)
          .then(() => { loadMyBikes(); setRegistering(false); })
          .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : 'Could not register this bike.'); setRegistering(false); });
      },
      () => { setOwnerError('Could not access your location.'); setRegistering(false); },
    );
  };

  const handleToggleBikeAvailable = (bike: BikeAsset) => {
    setBusyBikeId(bike.id);
    setOwnerError(null);
    setBikeAssetAvailability(bike.id, !bike.available)
      .then(() => { loadMyBikes(); setBusyBikeId(null); })
      .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : 'Could not update this bike.'); setBusyBikeId(null); });
  };

  const handleUpdateBikeLocation = (bikeId: string) => {
    if (!navigator.geolocation) {
      setOwnerError('Location access is required to update this bike.');
      return;
    }
    setUpdatingLocationId(bikeId);
    setOwnerError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        updateBikeAssetLocation(bikeId, pos.coords.latitude, pos.coords.longitude)
          .then(() => { loadMyBikes(); setUpdatingLocationId(null); })
          .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : "Could not update this bike's location."); setUpdatingLocationId(null); });
      },
      () => { setOwnerError('Could not access your location.'); setUpdatingLocationId(null); },
    );
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'RENT' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('RENT')} style={{ flex: 1 }}
        >
          Rent a bike
        </button>
        <button
          className={subTab === 'OWN' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('OWN')} style={{ flex: 1 }}
        >
          My bikes
        </button>
      </div>

      {subTab === 'RENT' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {riderError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{riderError}</p>}
          {justCompletedRental && (
            <div className="itunda-card" style={{ textAlign: 'center', padding: '24px' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Rental complete</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-24-size)', fontWeight: 700, margin: '8px 0' }}>{(justCompletedRental.totalFare ?? 0).toLocaleString()} RWF</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{justCompletedRental.durationMinutes} minutes</p>
              <button className="itunda-btn itunda-btn-secondary" onClick={() => setJustCompletedRental(null)} style={{ marginTop: '12px' }}>
                Done
              </button>
            </div>
          )}
          {!justCompletedRental && activeRental && (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px', display: 'flex', alignItems: 'center', gap: '6px' }}><BikeGlyph size={16} /> Riding now</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                Fare is calculated by elapsed time once you end the rental.
              </p>
              <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={endingRental} onClick={handleEndRental}>
                {endingRental ? 'Ending…' : 'End rental (park the bike here)'}
              </button>
            </div>
          )}
          {!justCompletedRental && !activeRental && (
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                Nearby bikes, within 5 km of your real location.
              </p>
              {nearbyBikes === null ? (
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
              ) : nearbyBikes.length === 0 ? (
                <EmptyState message="No bikes nearby right now — try a different area or check back soon." />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {nearbyBikes.map((bike) => (
                    <div key={bike.id} className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><BikeTypeGlyph electric={bike.type === 'ELECTRIC'} size={15} /> {bike.type === 'ELECTRIC' ? 'Electric' : 'Regular'}</p>
                        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{bike.type === 'ELECTRIC' ? '150' : '80'} RWF/minute</p>
                      </div>
                      <button
                        className="itunda-btn itunda-btn-primary" disabled={busyBikeId === bike.id}
                        onClick={() => handleStartRental(bike.id)}
                      >
                        {busyBikeId === bike.id ? '…' : 'Unlock'}
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          {rentalHistory && rentalHistory.filter((r) => r.status === 'COMPLETED').length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past rentals</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {rentalHistory.filter((r) => r.status === 'COMPLETED').map((r) => (
                  <div key={r.id} className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.durationMinutes} min</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{(r.totalFare ?? 0).toLocaleString()} RWF</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'OWN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {ownerError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{ownerError}</p>}
          <div className="itunda-card">
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Add your bike to the pool</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
              Uses your real current location as the bike's starting spot.
            </p>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
              <button
                className={bikeType === 'REGULAR' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                onClick={() => setBikeType('REGULAR')} style={{ flex: 1 }}
              >
                Regular
              </button>
              <button
                className={bikeType === 'ELECTRIC' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                onClick={() => setBikeType('ELECTRIC')} style={{ flex: 1 }}
              >
                Electric
              </button>
            </div>
            <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={registering} onClick={handleRegisterBike}>
              {registering ? 'Registering…' : 'Register bike'}
            </button>
          </div>
          {myBikes && myBikes.length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your bikes</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {myBikes.map((bike) => (
                  <div key={bike.id} className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px' }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><BikeTypeGlyph electric={bike.type === 'ELECTRIC'} size={15} /> {bike.type === 'ELECTRIC' ? 'Electric' : 'Regular'} bike</p>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        className="itunda-btn itunda-btn-secondary" disabled={updatingLocationId === bike.id}
                        onClick={() => handleUpdateBikeLocation(bike.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 10px' }}
                      >
                        {updatingLocationId === bike.id ? '…' : 'Update location'}
                      </button>
                      <button className="itunda-btn itunda-btn-secondary" disabled={busyBikeId === bike.id} onClick={() => handleToggleBikeAvailable(bike)}>
                        {busyBikeId === bike.id ? '…' : bike.available ? 'Available' : 'Unavailable'}
                      </button>
                    </div>
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
