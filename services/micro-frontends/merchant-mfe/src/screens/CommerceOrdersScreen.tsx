import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { ErrorCard } from '../components/EmptyState';
import { advanceCommerceOrder, fetchCommerceMerchantOrders, type CommerceOrder, type CommerceOrderStatus } from '../lib/commerce';
import { useI18n } from '../i18n/I18nContext';
import type { TranslationKey } from '../i18n/translations';

// Real Coupang-style Shop/Commerce order queue (Shop/Commerce product-completeness
// pass) -- see lib/commerce.ts's own doc comment. This is merchant-mfe's first client
// for these real, already-live-verified endpoints (pack/ship, DELIVERED is
// rider-driven) -- previously only bank-mfe's ShopOrdersAndWishlist.tsx had them, the
// same real gap already closed for Eats orders via EatsOrdersScreen.tsx.

const COMMERCE_STATUS_KEY: Record<CommerceOrderStatus, TranslationKey> = {
  PLACED: 'commerceOrders.statusPlaced',
  PACKED: 'commerceOrders.statusPacked',
  SHIPPED: 'commerceOrders.statusShipped',
  DELIVERED: 'commerceOrders.statusDelivered',
  CANCELLED: 'commerceOrders.statusCancelled',
};

// Real PLACED -> PACKED -> SHIPPED merchant-advanceable prefix -- DELIVERED is
// rider-driven (itunda's own internal rider fleet claims SHIPPED and completes the
// hand-off), matching EatsOrdersScreen.tsx's own restaurant-chain-stops-before-
// terminal-state convention exactly.
const MERCHANT_STATUS_CHAIN: CommerceOrderStatus[] = ['PLACED', 'PACKED', 'SHIPPED'];

function nextInChain(current: CommerceOrderStatus): CommerceOrderStatus | null {
  const idx = MERCHANT_STATUS_CHAIN.indexOf(current);
  return idx >= 0 && idx + 1 < MERCHANT_STATUS_CHAIN.length ? MERCHANT_STATUS_CHAIN[idx + 1] : null;
}

export default function CommerceOrdersScreen() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchCommerceMerchantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected 404 for any merchant that has never listed a Shop
        // product -- stays silent rather than showing an alarming error.
        if (err instanceof ApiError && err.code === 'MERCHANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('commerceOrders.loadError'));
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: CommerceOrder) => {
    const next = nextInChain(order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceCommerceOrder(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('commerceOrders.updateError'));
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
          {t('commerceOrders.empty')}
        </p>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => {
        const next = nextInChain(o.status);
        return (
          <div key={o.id} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
                  {t(COMMERCE_STATUS_KEY[o.status])}
                </p>
                <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{o.deliveryAddress}</p>
              </div>
              <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{o.totalAmount.toLocaleString('en-US')} RWF</span>
            </div>
            {next && (
              <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                {busyOrderId === o.id ? t('commerceOrders.updating') : `${t('commerceOrders.markPrefix')} ${t(COMMERCE_STATUS_KEY[next]).toLowerCase()}`}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
