import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { ErrorCard } from '../components/EmptyState';
import { advanceRestaurantOrder, completePickupOrder, fetchEatsOrderDetail, fetchRestaurantOrders, markEatsOrderItemUnavailable, type EatsOrder, type EatsOrderItem, type EatsOrderStatus } from '../lib/eats';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';

// Real Coupang Eats/Baemin-style restaurant order queue (item 208) -- see lib/eats.ts's
// own doc comment. This is merchant-mfe's first client for these real, already-live-
// verified endpoints (accept/prepare/ready, plus the Pickup-specific "mark picked up"
// terminal edge) -- previously only bank-mfe's RestaurantOrdersView had them.

const EATS_STATUS_KEY: Record<EatsOrderStatus, TranslationKey> = {
  PLACED: 'eatsOrders.statusPlaced',
  ACCEPTED: 'eatsOrders.statusAccepted',
  PREPARING: 'eatsOrders.statusPreparing',
  READY_FOR_PICKUP: 'eatsOrders.statusReadyForPickup',
  RIDER_ASSIGNED: 'eatsOrders.statusRiderAssigned',
  PICKED_UP: 'eatsOrders.statusPickedUp',
  DELIVERED: 'eatsOrders.statusDelivered',
  CANCELLED: 'eatsOrders.statusCancelled',
};

const RESTAURANT_STATUS_CHAIN: EatsOrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP'];

function nextInChain(current: EatsOrderStatus): EatsOrderStatus | null {
  const idx = RESTAURANT_STATUS_CHAIN.indexOf(current);
  return idx >= 0 && idx + 1 < RESTAURANT_STATUS_CHAIN.length ? RESTAURANT_STATUS_CHAIN[idx + 1] : null;
}

export default function EatsOrdersScreen() {
  const { t } = useI18n();
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
          setError(err instanceof ApiError ? err.message : t('eatsOrders.loadError'));
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
      setError(err instanceof ApiError ? err.message : t('eatsOrders.updateError'));
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
      setError(err instanceof ApiError ? err.message : t('eatsOrders.completePickupError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return <ErrorCard message={error} onRetry={load} />;
  }
  if (orders === null) return <div className="itunda-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) {
    return (
      <div className="itunda-card">
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
          {t('eatsOrders.empty')}
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
          <div key={o.id} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
                  {t(EATS_STATUS_KEY[o.status])}{o.fulfillmentType === 'PICKUP' ? t('eatsOrders.pickupSuffix') : ''}
                </p>
                <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{o.deliveryAddress}</p>
              </div>
              <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{o.totalAmount.toLocaleString('en-US')} RWF</span>
            </div>
            {o.deliveryNotes && (
              <p style={{ fontSize: '12px', color: 'var(--itunda-grey-700)', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
                {t('eatsOrders.notePrefix')} {o.deliveryNotes}
              </p>
            )}
            {(o.status === 'ACCEPTED' || o.status === 'PREPARING') && (
              <ItemUnavailableSection orderId={o.id} />
            )}
            {readyForPickupHandoff ? (
              <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleCompletePickup(o)}>
                {busyOrderId === o.id ? t('eatsOrders.updating') : t('eatsOrders.markPickedUp')}
              </button>
            ) : next && (
              <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                {busyOrderId === o.id ? t('eatsOrders.updating') : `${t('eatsOrders.markPrefix')} ${t(EATS_STATUS_KEY[next]).toLowerCase()}`}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

// Real DoorDash/Uber Eats-style "Item Unavailable" flow -- see lib/eats.ts's own doc
// comment. Toggle-open, only rendered for ACCEPTED/PREPARING orders (matching the
// backend's own real status guard).
function ItemUnavailableSection({ orderId }: { orderId: string }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<EatsOrderItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyItemId, setBusyItemId] = useState<string | null>(null);

  const load = () => {
    fetchEatsOrderDetail(orderId)
      .then((r) => setItems(r.items))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('eatsOrders.itemsLoadError')));
  };

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && items === null) load();
  };

  const handleMarkUnavailable = async (itemId: string) => {
    setBusyItemId(itemId);
    setError(null);
    try {
      await markEatsOrderItemUnavailable(orderId, itemId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('eatsOrders.itemUnavailableError'));
    } finally {
      setBusyItemId(null);
    }
  };

  return (
    <div>
      <button type="button" onClick={toggle} style={{ color: 'var(--itunda-indigo)', fontSize: '13px', fontWeight: 600 }}>
        {t('eatsOrders.itemsToggle')}
      </button>
      {open && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
          {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
          {items === null ? (
            <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('eatsOrders.itemsLoading')}</p>
          ) : (
            items.map((item) => (
              <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
                <span style={{ textDecoration: item.unavailable ? 'line-through' : 'none', color: item.unavailable ? 'var(--itunda-grey-500)' : 'var(--itunda-grey-900)' }}>
                  {item.productName} x{item.quantity}
                </span>
                {!item.unavailable && (
                  <button
                    className="itunda-btn itunda-btn-secondary" disabled={busyItemId === item.id}
                    onClick={() => handleMarkUnavailable(item.id)} style={{ fontSize: '11px', padding: '4px 8px' }}
                  >
                    {busyItemId === item.id ? '…' : t('eatsOrders.markItemUnavailable')}
                  </button>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
