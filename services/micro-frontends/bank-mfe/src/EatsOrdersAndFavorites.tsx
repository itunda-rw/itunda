import { useState, useEffect } from 'react';
import { Utensils } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState, ErrorCard } from './EmptyState';
import { HeartFilled } from './icons/ItundaFaceHearts';
import type { ShoppingMerchant } from './lib/shopping';
import {
  contactRestaurant, fetchMyEatsOrders, cancelEatsOrder, fetchMyFavoriteRestaurants, shareFavoritesToConversation,
  removeFavoriteRestaurant, fetchRestaurantOrders, advanceRestaurantOrder, completePickupOrder,
  type EatsOrder, type FavoriteRestaurant,
} from './lib/eats';
import { EatsOrderCard, EATS_STATUS_LABEL, RESTAURANT_STATUS_CHAIN } from './EatsOrderCard';
import { ReviewOrderCard, TipRiderPrompt, RestaurantReviewsManageView } from './EatsReviews';
import { ShareFavoritesModal, WishlistButton, nextInChain } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

export function MyEatsOrdersView({ onReorder, reorderingId, restaurants, onMessageSeller }: { onReorder: (order: EatsOrder) => void; reorderingId: string | null; restaurants: ShoppingMerchant[] | null; onMessageSeller: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const showSkeleton = useDeferredLoading(orders === null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [messagingOrderId, setMessagingOrderId] = useState<string | null>(null);
  // Real optimistic-hide for TipRiderPrompt -- same pattern reviewedTripIds/
  // tippedTripIds already established for the ride equivalent this session.
  const [tippedOrderIds, setTippedOrderIds] = useState<Set<string>>(new Set());

  const handleMessageRestaurant = async (orderId: string) => {
    setMessagingOrderId(orderId);
    setError(null);
    try {
      const conversation = await contactRestaurant(orderId);
      onMessageSeller(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setMessagingOrderId(null);
    }
  };

  const load = () => {
    setError(null);
    fetchMyEatsOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    load();
    // Real poll for order-tracking status, same 4s cadence as the Messages tab's
    // poll-based delivery -- no live push transport exists here either.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleCancel = async (orderId: string) => {
    setCancellingId(orderId);
    setError(null);
    try {
      await cancelEatsOrder(orderId);
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
  if (orders.length === 0) return <EmptyState message="No orders yet — order from a nearby restaurant and it'll show up here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <EatsOrderCard
          key={o.id}
          order={o}
          restaurant={restaurants?.find((r) => r.merchantId === o.restaurantId)}
          onMessageRestaurant={messagingOrderId === o.id ? undefined : () => handleMessageRestaurant(o.id)}
          action={
            o.status === 'PLACED' ? (
              <button className="itunda-btn itunda-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
                {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
              </button>
            ) : o.status === 'DELIVERED' ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <ReviewOrderCard order={o} onSubmitted={load} />
                {o.riderId && !o.tipAmount && !tippedOrderIds.has(o.id) && (
                  <TipRiderPrompt orderId={o.id} onTipped={() => setTippedOrderIds((prev) => new Set(prev).add(o.id))} />
                )}
                <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
                  {reorderingId === o.id ? 'Reordering…' : 'Reorder'}
                </button>
              </div>
            ) : o.status === 'CANCELLED' ? (
              <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
                {reorderingId === o.id ? 'Reordering…' : 'Reorder'}
              </button>
            ) : undefined
          }
        />
      ))}
    </div>
  );
}

// Real bookmarked/favorited restaurants (2026-07-19) -- self-contained, mirroring
// MyEatsOrdersView's own load/local-state pattern; onChanged resyncs OrderFoodView's
// favoriteIds set so the Browse tab's stars stay correct after an unfavorite here.
export function FavoriteRestaurantsView({ onOpen, onChanged }: { onOpen: (favorite: FavoriteRestaurant) => void; onChanged: () => void }) {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoriteRestaurant[] | null>(null);
  const showSkeleton = useDeferredLoading(favorites === null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [showShareModal, setShowShareModal] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);
  const [shared, setShared] = useState(false);

  const load = () => {
    setError(null);
    fetchMyFavoriteRestaurants().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  const handleShare = async (conversationId: string) => {
    setShowShareModal(false);
    setShareError(null);
    try {
      await shareFavoritesToConversation(conversationId);
      setShared(true);
      setTimeout(() => setShared(false), 3000);
    } catch (err) {
      setShareError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleRemove = async (restaurantId: string) => {
    setRemovingId(restaurantId);
    try {
      await removeFavoriteRestaurant(restaurantId);
      setFavorites((prev) => prev?.filter((f) => f.restaurantId !== restaurantId) ?? prev);
      onChanged();
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
  if (favorites === null) {
    return showSkeleton ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }
  if (favorites.length === 0) {
    return <EmptyState message="No favorite restaurants yet. Tap the heart on a restaurant to save it here." />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '10px' }}>
        {shared && <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green-600, #16a34a)' }}>Shared!</span>}
        <button className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', display: 'inline-flex', alignItems: 'center', gap: '6px' }} onClick={() => setShowShareModal(true)}>
          <HeartFilled size={14} /> Share favorites
        </button>
      </div>
      {shareError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{shareError}</p>}
      {showShareModal && <ShareFavoritesModal onShare={handleShare} onClose={() => setShowShareModal(false)} />}
      {favorites.map((f) => (
        <div
          key={f.restaurantId}
          role="button"
          tabIndex={0}
          onClick={() => onOpen(f)}
          onKeyDown={(e) => { if (e.key === 'Enter') onOpen(f); }}
          style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', textAlign: 'left', width: '100%', cursor: 'pointer' }}
        >
          <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Utensils size={20} color="var(--itunda-indigo)" />
          </div>
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{f.businessName}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{f.category ? `${f.category} · Real menu, real delivery` : 'Real menu, real delivery'}</p>
          </div>
          {/* Same shared-component fix as the restaurant list card above. */}
          <div onClick={(e) => e.stopPropagation()} style={{ flexShrink: 0 }}>
            <WishlistButton favorited busy={removingId === f.restaurantId} onToggle={() => handleRemove(f.restaurantId)} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function RestaurantOrdersView() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const showSkeleton = useDeferredLoading(orders === null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected 404 for any account that hasn't registered as a merchant --
        // this view stays silent rather than showing an alarming error for the common
        // case of a buyer-only account that has no restaurant.
        if (err instanceof ApiError && err.code === 'RESTAURANT_NOT_FOUND') {
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

  const handleAdvance = async (order: EatsOrder) => {
    const next = nextInChain(RESTAURANT_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceRestaurantOrder(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  // Real Baemin-style 포장주문 (Pickup) terminal edge (item 208) -- a PICKUP order at
  // READY_FOR_PICKUP has no `next` in RESTAURANT_STATUS_CHAIN (there's no rider to hand
  // off to), so it previously just sat there forever with no action anywhere to close
  // it out, despite EatsOrderService.completePickup being real and live-verified.
  const handleCompletePickup = async (order: EatsOrder) => {
    setBusyOrderId(order.id);
    setError(null);
    try {
      await completePickupOrder(order.id);
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
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(RESTAURANT_STATUS_CHAIN, o.status);
          const readyForPickupHandoff = o.fulfillmentType === 'PICKUP' && o.status === 'READY_FOR_PICKUP';
          return (
            <EatsOrderCard
              key={o.id}
              order={o}
              action={
                readyForPickupHandoff ? (
                  <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleCompletePickup(o)}>
                    {busyOrderId === o.id ? 'Updating…' : 'Mark picked up'}
                  </button>
                ) : next && (
                  <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                    {busyOrderId === o.id ? 'Updating…' : `Mark ${EATS_STATUS_LABEL[next].toLowerCase()}`}
                  </button>
                )
              }
            />
          );
        })}
      </div>
      <RestaurantReviewsManageView restaurantId={orders[0].restaurantId} />
    </div>
  );
}
