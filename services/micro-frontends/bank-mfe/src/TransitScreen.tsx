// Real Kigali public-transit stored-value balance (2026-08-27, direct user follow-up
// after the card-design-picker feature: "after this we will build transit features").
// See lib/transit.ts's own doc comment for the full sourced account of Kigali's real
// Tap&Go fare system (AC Group Ltd, Kigali Bus Services, Royal Express) and the honest
// boundary this simulates -- itunda has no real partnership with any of them, so this
// screen never uses their "Tap&Go" name for itunda's own product.
//
// A standalone file from the start (not inline in BankDashboard.tsx, unlike CardView),
// since BankDashboard.tsx already sits right at its file-size-lint baseline -- see
// CardExplainer.tsx's own doc comment for the same constraint.

import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { EmptyState, ErrorCard } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import {
  fetchTransitBalance,
  fetchTransitTrips,
  tapTransitFare,
  topUpTransit,
  TRANSIT_FARE_STEP,
  TRANSIT_MAX_FARE,
  TRANSIT_MIN_FARE,
  TRANSIT_OPERATORS,
  type TransitBalance,
  type TransitTrip,
} from './lib/transit';
import { useDeferredLoading } from './useDeferredLoading';

function tapButtonLabel(balance: TransitBalance | null, fare: number, busy: boolean): string {
  if (balance === null) return 'Top up first';
  if (balance.balance < fare) return 'Balance too low';
  if (busy) return 'Tapping…';
  return `Tap ${fare.toLocaleString()} RWF`;
}

export function TransitScreen({ onOpenCollect }: { onOpenCollect: () => void }) {
  const { t } = useI18n();
  const [balance, setBalance] = useState<TransitBalance | null | undefined>(undefined);
  const showSkeleton = useDeferredLoading(balance === undefined);
  const [trips, setTrips] = useState<TransitTrip[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [topUpAmount, setTopUpAmount] = useState('1000');
  const [operator, setOperator] = useState<string>(TRANSIT_OPERATORS[0]);
  const [fare, setFare] = useState(TRANSIT_MIN_FARE);
  const [tapMessage, setTapMessage] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchTransitBalance()
      .then(setBalance)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'TRANSIT_NO_ACCOUNT') {
          setBalance(null);
          return;
        }
        setError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
    fetchTransitTrips().then((r) => setTrips(r.trips)).catch(() => {});
  };
  useEffect(load, []);

  const handleTopUp = async () => {
    setBusy(true);
    setError(null);
    try {
      setBalance(await topUpTransit(Number(topUpAmount)));
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleTap = async () => {
    setBusy(true);
    setTapMessage(null);
    setError(null);
    try {
      const result = await tapTransitFare(operator, fare);
      setBalance(result.balance);
      setTapMessage(`Tapped ${result.trip.fare.toLocaleString()} RWF at ${result.trip.operator}`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (error && balance === undefined) {
    return <ErrorCard message={error} onRetry={load} />;
  }
  if (balance === undefined) {
    return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
      <div style={{ marginBottom: '12px' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>itunda Transit balance</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '4px 0 0' }}>
          {(balance?.balance ?? 0).toLocaleString()} RWF
        </p>
      </div>

      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
          Kigali&apos;s real public buses (Kigali Bus Services, Royal Express) run on a real contactless
          fare system called Tap&amp;Go, built by AC Group. itunda has no real partnership with them --
          this is itunda&apos;s own simulated transit balance: real money moves, real fares apply, it just
          isn&apos;t carried by a real bus card reader.
        </p>
        <button onClick={onOpenCollect} style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-indigo-500)' }}>
          Collecting fares for Kigali Bus Services or Royal Express? Open the collector →
        </button>
      </div>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>Top up</h3>
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="number" placeholder="Amount (RWF)" value={topUpAmount} onChange={(e) => setTopUpAmount(e.target.value)} min="1"
            style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={busy || Number(topUpAmount) <= 0} onClick={handleTopUp}>
            {busy ? 'Topping up…' : 'Top up'}
          </button>
        </div>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Tap to pay your fare</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
          Pick your real operator and fare -- Kigali&apos;s real fares run 200-500 RWF depending on
          distance; itunda has no GPS-derived distance to calculate one for you automatically.
        </p>
        {tapMessage && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', marginBottom: '8px' }}>{tapMessage}</p>}
        <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
          {TRANSIT_OPERATORS.map((op) => (
            <button
              key={op}
              onClick={() => setOperator(op)}
              className={`itunda-btn ${operator === op ? 'itunda-btn-primary' : 'itunda-btn-secondary'}`}
              style={{ flex: 1, fontSize: 'var(--itunda-type-scale-12-size)' }}
            >
              {op}
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '14px' }}>
          <input
            type="range" min={TRANSIT_MIN_FARE} max={TRANSIT_MAX_FARE} step={TRANSIT_FARE_STEP}
            value={fare} onChange={(e) => setFare(Number(e.target.value))}
            style={{ flex: 1 }}
          />
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, minWidth: '84px', textAlign: 'right' }}>{fare.toLocaleString()} RWF</span>
        </div>
        <button
          className="itunda-btn itunda-btn-primary" style={{ width: '100%' }}
          disabled={busy || balance === null || (balance?.balance ?? 0) < fare}
          onClick={handleTap}
        >
          {tapButtonLabel(balance, fare, busy)}
        </button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>Ride history</h3>
        {trips.length === 0 ? (
          <EmptyState message="No transit taps yet — once you tap to pay a fare, they'll show up here." />
        ) : (
          trips.map((trip) => (
            <div key={trip.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{trip.operator}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{new Date(trip.createdAt).toLocaleString()}</p>
              </div>
              <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{trip.fare.toLocaleString()} RWF</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
