import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { PriceDropGlyph } from './icons/ItundaFaceMisc';
import {
  fetchMyOrders, cancelOrder, fetchMerchantOrders, advanceOrderStatus, fetchMyFavoriteProducts, removeProductFavorite,
  type CommerceOrder, type FavoriteProduct,
} from './lib/commerce';
import type { ShoppingMerchant } from './lib/shopping';
import {
  COMMERCE_STATUS_LABEL, COMMERCE_STATUS_CHAIN, CommerceOrderCard, OrderItemReviews, ReturnExchangeAction, MyReturnRequestsView,
} from './CommerceOrders';
import { ProductImageThumb, ProductPriceBlock } from './ProductDisplay';
import { EmptyState, ErrorCard } from './EmptyState';
import { nextInChain } from './BankDashboard';

export function MyCommerceOrdersView({ onReorder, reorderingId }: { onReorder: (order: CommerceOrder) => void; reorderingId: string | null }) {
  const { t } = useI18n();
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleCancel = async (orderId: string) => {
    setCancellingId(orderId);
    setError(null);
    try {
      await cancelOrder(orderId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setCancellingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (orders.length === 0) return <EmptyState message="No orders yet — browse a merchant's shop and your first order will show up here." />;

  const renderAction = (o: CommerceOrder) => {
    if (o.status === 'PLACED') {
      return (
        <button className="itunda-btn itunda-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
          {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
        </button>
      );
    }
    if (o.status === 'DELIVERED') {
      return (
        <div>
          <OrderItemReviews order={o} />
          <ReturnExchangeAction orderId={o.id} />
          <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
            {reorderingId === o.id ? 'Reordering…' : 'Buy again'}
          </button>
        </div>
      );
    }
    if (o.status === 'CANCELLED') {
      return (
        <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
          {reorderingId === o.id ? 'Reordering…' : 'Buy again'}
        </button>
      );
    }
    return undefined;
  };

  return (
    <div>
      <MyReturnRequestsView />
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <CommerceOrderCard key={o.id} order={o} action={renderAction(o)} />
      ))}
      </div>
    </div>
  );
}

export function MerchantOrdersView() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchMerchantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected error for any account that hasn't registered as a merchant --
        // stays silent rather than alarming the common case of a buyer-only account.
        if (err instanceof ApiError && err.code === 'MERCHANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: CommerceOrder) => {
    const next = nextInChain(COMMERCE_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceOrderStatus(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your store</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(COMMERCE_STATUS_CHAIN, o.status);
          return (
            <CommerceOrderCard
              key={o.id}
              order={o}
              action={next && (
                <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                  {busyOrderId === o.id ? 'Updating…' : `Mark ${COMMERCE_STATUS_LABEL[next].toLowerCase()}`}
                </button>
              )}
            />
          );
        })}
      </div>
    </div>
  );
}

// Real product wishlist view (2026-07-20) -- lists every real favorited product,
// tapping one opens that merchant's real catalog (same "prove once, reuse the existing
// screen" shape as everywhere else in this file).
export function WishlistView({ onOpenMerchant }: { onOpenMerchant: (merchant: ShoppingMerchant) => void }) {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoriteProduct[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteProducts().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleRemove = async (productId: string) => {
    setRemovingId(productId);
    try {
      await removeProductFavorite(productId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (favorites.length === 0) return <EmptyState message="No saved items yet -- tap ♡ on any product to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {favorites.map((f) => (
        <div key={f.productId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', padding: '10px 0' }}>
          <button
            onClick={() => onOpenMerchant({ merchantId: f.merchantId, businessName: f.businessName, category: null, cashbackRate: '' })}
            style={{ textAlign: 'left', flex: 1, display: 'flex', alignItems: 'center', gap: '12px' }}
          >
            <ProductImageThumb imageUrl={f.imageUrl} size={44} />
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{f.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{f.businessName}</p>
              <ProductPriceBlock price={f.price} originalPrice={f.originalPrice} discountPercent={f.discountPercent} />
              {f.priceDropped && (
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-red, var(--itunda-red))', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <PriceDropGlyph size={12} /> Price dropped
                </p>
              )}
            </div>
          </button>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={removingId === f.productId}
            onClick={() => handleRemove(f.productId)}
            style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {removingId === f.productId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}
