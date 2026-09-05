// Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
// can make pay for tax and moto as well"). Mirrors TransitCollectScreen.tsx's shape --
// same real CustomerPaymentCode primitive, same camera-scan-first, manual-fallback
// pattern -- but simpler: no operator picker (a Kigali moto-taxi driver is an
// individual, not a fixed-route company the way Kigali Bus Services/Royal Express
// are), and a different, real sourced fare range (400-6000 RWF vs transit's 200-500).

import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { QrScanCamera } from './QrScanCamera';
import {
  collectMotoFare, fetchMyMotoFareEarnings, fetchMyMotoFareTripsAsRider, MOTO_FARE_MAX, MOTO_FARE_MIN, MOTO_FARE_STEP,
  type MotoFareCollectResult, type MotoFareTrip,
} from './lib/motoFare';
import { EmptyState } from './EmptyState';
import { useDeferredLoading } from './useDeferredLoading';

// Real driver earnings / rider trip-history summary -- mirrors ride-hailing's own
// "This week" card (BankDashboard.tsx's DRIVE sub-tab) in shape, but moto-fare's
// /earnings and /trips endpoints both return a flat trip list, not day-bucketed
// totals, so the aggregate is summed client-side over whatever page is fetched.
// Shared between both roles (uncalled-endpoint sweep, 2026-09-02: the rider side of
// this same real component was never built, only the driver side was, since the
// driver's own uncalled-endpoint fix on 2026-08-29).
function MotoFareTripSummary({ trips, totalElements, role }: { trips: MotoFareTrip[]; totalElements: number; role: 'DRIVER' | 'RIDER' }) {
  if (trips.length === 0) return null;
  const totalFare = trips.reduce((sum, t) => sum + t.fare, 0);
  return (
    <div className="itunda-flat-section" style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>
        {role === 'DRIVER' ? 'Your fares' : 'Your trips'}
      </p>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '14px' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
            {role === 'DRIVER' ? 'Fares collected' : 'Trips paid'}
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{totalElements}</p>
        </div>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Total (last {trips.length})</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, color: role === 'DRIVER' ? 'var(--itunda-green)' : 'var(--itunda-grey-900)' }}>{totalFare.toLocaleString('en-US')} RWF</p>
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {trips.slice(0, 5).map((trip) => (
          <div key={trip.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-12-size)' }}>
            <span style={{ color: 'var(--itunda-grey-500)' }}>{new Date(trip.createdAt).toLocaleString()}</span>
            <span style={{ fontWeight: 600 }}>{trip.fare.toLocaleString('en-US')} RWF</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real rider-side trip history (uncalled-endpoint sweep, 2026-09-02) -- see
// fetchMyMotoFareTripsAsRider's own doc comment. A rider only ever pays by showing
// their existing "My payment code" to a driver (MyPaymentCodeCard), so unlike the
// driver there's no scan/collect action here -- just their own past trips.
function MotoFareRiderTripsView() {
  const [trips, setTrips] = useState<{ trips: MotoFareTrip[]; totalElements: number } | null>(null);
  const showSkeleton = useDeferredLoading(trips === null);

  useEffect(() => {
    fetchMyMotoFareTripsAsRider().then((r) => setTrips({ trips: r.trips, totalElements: r.totalElements })).catch(() => setTrips({ trips: [], totalElements: 0 }));
  }, []);

  if (trips === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (trips.trips.length === 0) {
    return <EmptyState message="No moto-taxi trips yet -- show your payment code to a driver next time you tap to pay." />;
  }
  return <MotoFareTripSummary trips={trips.trips} totalElements={trips.totalElements} role="RIDER" />;
}

function MotoFareDriverCollectView() {
  const [code, setCode] = useState<string | null>(null);
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualCode, setManualCode] = useState('');
  const [fare, setFare] = useState(MOTO_FARE_MIN);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [collected, setCollected] = useState<MotoFareCollectResult | null>(null);
  const [earnings, setEarnings] = useState<{ trips: MotoFareTrip[]; totalElements: number } | null>(null);

  const loadEarnings = () => {
    fetchMyMotoFareEarnings().then((r) => setEarnings({ trips: r.trips, totalElements: r.totalElements })).catch(() => {});
  };
  useEffect(loadEarnings, []);

  const reset = () => {
    setCode(null);
    setCollected(null);
    setError(null);
    setManualCode('');
  };

  const handleCollect = async () => {
    if (!code) return;
    setBusy(true);
    setError(null);
    try {
      const result = await collectMotoFare(code, fare);
      setCollected(result);
      loadEarnings();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not collect this fare.');
    } finally {
      setBusy(false);
    }
  };

  if (collected) {
    return (
      <div className="itunda-flat-section" style={{ textAlign: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, color: 'var(--itunda-green)' }}>Collected</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', marginTop: '4px' }}>{collected.fare.toLocaleString('en-US')} RWF</p>
        <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '16px', width: '100%' }} onClick={reset}>
          Collect next fare
        </button>
      </div>
    );
  }

  if (!code) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {earnings && <MotoFareTripSummary trips={earnings.trips} totalElements={earnings.totalElements} role="DRIVER" />}
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Scan the rider&apos;s payment code</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
          Ask the rider to open itunda and tap to show their payment code, then point your camera at it.
          On the Android app, an NFC tap does the same thing without a camera. Fares go straight into your own itunda account -- no fee.
        </p>
        {!scanUnavailable ? (
          <QrScanCamera onDetect={setCode} onUnavailable={() => setScanUnavailable(true)} />
        ) : (
          <div className="itunda-flat-section">
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
              Camera unavailable -- enter the rider&apos;s code instead.
            </p>
            <input
              placeholder="Payment code" value={manualCode} onChange={(e) => setManualCode(e.target.value)}
              style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', marginBottom: '8px' }}
            />
            <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={!manualCode.trim()} onClick={() => setCode(manualCode.trim())}>
              Use this code
            </button>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="itunda-flat-section">
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>Collect fare</p>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
        <input
          type="range" min={MOTO_FARE_MIN} max={MOTO_FARE_MAX} step={MOTO_FARE_STEP}
          value={fare} onChange={(e) => setFare(Number(e.target.value))}
          style={{ flex: 1 }}
        />
        <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, minWidth: '84px', textAlign: 'right' }}>{fare.toLocaleString('en-US')} RWF</span>
      </div>
      <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={busy} onClick={handleCollect}>
        {busy ? 'Collecting…' : `Collect ${fare.toLocaleString('en-US')} RWF`}
      </button>
      <button onClick={reset} style={{ width: '100%', marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        Scan a different code
      </button>
    </div>
  );
}

// Real rider/driver sub-tab toggle (uncalled-endpoint sweep, 2026-09-02), mirroring
// RidesView/DesignatedDriverView's own "customer side vs. provider side" pattern --
// most people opening this screen are riders checking their own trips, not drivers
// about to scan a code, so "My trips" is the default, not "Collect".
export function MotoFareCollectScreen() {
  const [subTab, setSubTab] = useState<'TRIPS' | 'COLLECT'>('TRIPS');
  return (
    <div>
      <div style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
        <button
          className={subTab === 'TRIPS' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('TRIPS')} style={{ flex: 1 }}
        >
          My trips
        </button>
        <button
          className={subTab === 'COLLECT' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('COLLECT')} style={{ flex: 1 }}
        >
          Collect
        </button>
      </div>
      {subTab === 'TRIPS' ? <MotoFareRiderTripsView /> : <MotoFareDriverCollectView />}
    </div>
  );
}
