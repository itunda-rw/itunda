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
import { useDeferredLoading } from './useDeferredLoading';

export function MyCommerceOrdersView({ onReorder, reorderingId }: { onReorder: (order: CommerceOrder) => void; reorderingId: string | null }) {
  const { t } = useI18n();
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const showSkeleton = useDeferredLoading(orders === null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  // Real pagination fix (2026-09-11): page 0 is polled every 4s for real-time
  // order-status accuracy, so it must always stay a live, page-0-only fetch.
  // olderOrders is a separate accumulator populated only by loadMoreOrders,
  // never touched by the poll -- safe to concatenate since both are ordered
  // by createdAt DESC and never overlap.
  const [olderOrders, setOlderOrders] = useState<CommerceOrder[]>([]);
  const [ordersPage, setOrdersPage] = useState(0);
  const [ordersHasMore, setOrdersHasMore] = useState(false);
  const [loadingMoreOrders, setLoadingMoreOrders] = useState(false);

  const load = () => {
    setError(null);
    fetchMyOrders(0)
      .then((r) => { setOrders(r.orders); setOrdersHasMore(r.page + 1 < r.totalPages); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  const loadMoreOrders = () => {
    const nextPage = ordersPage + 1;
    setLoadingMoreOrders(true);
    fetchMyOrders(nextPage)
      .then((r) => {
        setOlderOrders((prev) => [...prev, ...r.orders]);
        setOrdersPage(nextPage);
        setOrdersHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreOrders(false));
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
  if (orders === null) return showSkeleton ? <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  const allOrders = [...orders, ...olderOrders];
  if (allOrders.length === 0) return <EmptyState message="No orders yet — browse a merchant's shop and your first order will show up here." />;

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
      {allOrders.map((o) => (
        <CommerceOrderCard key={o.id} order={o} action={renderAction(o)} />
      ))}
      {ordersHasMore && (
        <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreOrders} onClick={loadMoreOrders}>
          {loadingMoreOrders ? 'Loading…' : 'Load more'}
        </button>
      )}
      </div>
    </div>
  );
}

export function MerchantOrdersView() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const showSkeleton = useDeferredLoading(orders === null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);
  // Same page-0-stays-live + separately-accumulated-older-pages design as
  // MyCommerceOrdersView above (see its own comment).
  const [olderMerchantOrders, setOlderMerchantOrders] = useState<CommerceOrder[]>([]);
  const [merchantOrdersPage, setMerchantOrdersPage] = useState(0);
  const [merchantOrdersHasMore, setMerchantOrdersHasMore] = useState(false);
  const [loadingMoreMerchantOrders, setLoadingMoreMerchantOrders] = useState(false);

  const load = () => {
    fetchMerchantOrders(0)
      .then((r) => { setOrders(r.orders); setMerchantOrdersHasMore(r.page + 1 < r.totalPages); })
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

  const loadMoreMerchantOrders = () => {
    const nextPage = merchantOrdersPage + 1;
    setLoadingMoreMerchantOrders(true);
    fetchMerchantOrders(nextPage)
      .then((r) => {
        setOlderMerchantOrders((prev) => [...prev, ...r.orders]);
        setMerchantOrdersPage(nextPage);
        setMerchantOrdersHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreMerchantOrders(false));
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
  if (orders === null) return showSkeleton ? <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  const allMerchantOrders = [...orders, ...olderMerchantOrders];
  if (allMerchantOrders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your store</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {allMerchantOrders.map((o) => {
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
        {merchantOrdersHasMore && (
          <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreMerchantOrders} onClick={loadMoreMerchantOrders}>
            {loadingMoreMerchantOrders ? 'Loading…' : 'Load more'}
          </button>
        )}
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
  const showSkeleton = useDeferredLoading(favorites === null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  // Real pagination-discard fix (2026-09-12) -- see lib/commerce.ts's own doc
  // comment on fetchMyFavoriteProducts.
  const [favoritesPage, setFavoritesPage] = useState(0);
  const [favoritesHasMore, setFavoritesHasMore] = useState(false);
  const [loadingMoreFavorites, setLoadingMoreFavorites] = useState(false);

  const load = () => {
    setError(null);
    fetchMyFavoriteProducts(0)
      .then((r) => { setFavorites(r.favorites); setFavoritesPage(0); setFavoritesHasMore(r.page + 1 < r.totalPages); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const loadMoreFavorites = () => {
    const nextPage = favoritesPage + 1;
    setLoadingMoreFavorites(true);
    fetchMyFavoriteProducts(nextPage)
      .then((r) => {
        setFavorites((prev) => [...(prev ?? []), ...r.favorites]);
        setFavoritesPage(nextPage);
        setFavoritesHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreFavorites(false));
  };

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
  if (favorites === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
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
      {favoritesHasMore && (
        <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreFavorites} onClick={loadMoreFavorites} style={{ marginTop: '8px' }}>
          {loadingMoreFavorites ? 'Loading…' : 'Load more'}
        </button>
      )}
    </div>
  );
}
