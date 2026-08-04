import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { ErrorCard } from '../components/EmptyState';
import { advanceRestaurantOrder, completePickupOrder, fetchRestaurantOrders, type EatsOrder, type EatsOrderStatus } from '../lib/eats';

// Real Coupang Eats/Baemin-style restaurant order queue (item 208) -- see lib/eats.ts's
// own doc comment. This is merchant-mfe's first client for these real, already-live-
// verified endpoints (accept/prepare/ready, plus the Pickup-specific "mark picked up"
// terminal edge) -- previously only bank-mfe's RestaurantOrdersView had them.

const EATS_STATUS_LABEL: Record<EatsOrderStatus, string> = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted',
  PREPARING: 'Preparing',
  READY_FOR_PICKUP: 'Ready for pickup',
  RIDER_ASSIGNED: 'Rider on the way',
  PICKED_UP: 'Picked up — on the way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled — refunded',
};

const RESTAURANT_STATUS_CHAIN: EatsOrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP'];

function nextInChain(current: EatsOrderStatus): EatsOrderStatus | null {
  const idx = RESTAURANT_STATUS_CHAIN.indexOf(current);
  return idx >= 0 && idx + 1 < RESTAURANT_STATUS_CHAIN.length ? RESTAURANT_STATUS_CHAIN[idx + 1] : null;
}

export default function EatsOrdersScreen() {
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected 404 for any merchant that isn't a real restaurant (e.g.
        // shop-only) -- stays silent rather than showing an alarming error.
        if (err instanceof ApiError && err.code === 'RESTAURANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : 'Could not load your restaurant orders.');
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: EatsOrder) => {
    const next = nextInChain(order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceRestaurantOrder(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this order.');
    } finally {
      setBusyOrderId(null);
    }
  };

  const handleCompletePickup = async (order: EatsOrder) => {
    setBusyOrderId(order.id);
    setError(null);
    try {
      await completePickupOrder(order.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not complete this pickup.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return <ErrorCard message={error} onRetry={load} />;
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          No Eats orders yet. Orders placed against your restaurant will show up here.
        </p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => {
        const next = nextInChain(o.status);
        const readyForPickupHandoff = o.fulfillmentType === 'PICKUP' && o.status === 'READY_FOR_PICKUP';
        return (
          <div key={o.id} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>
                  {EATS_STATUS_LABEL[o.status]}{o.fulfillmentType === 'PICKUP' ? ' · Pickup' : ''}
                </p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{o.deliveryAddress}</p>
              </div>
              <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{o.totalAmount.toLocaleString()} RWF</span>
            </div>
            {o.deliveryNotes && (
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)', backgroundColor: 'var(--toss-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
                Note: {o.deliveryNotes}
              </p>
            )}
            {readyForPickupHandoff ? (
              <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleCompletePickup(o)}>
                {busyOrderId === o.id ? 'Updating…' : 'Mark picked up'}
              </button>
            ) : next && (
              <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                {busyOrderId === o.id ? 'Updating…' : `Mark ${EATS_STATUS_LABEL[next].toLowerCase()}`}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
