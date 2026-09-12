import { useState, useEffect } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { IconBack } from './icons/ItundaIcons';
import { ShopMessageSellerButton, ShopBestSellerBadge, ShopDeliveryEtaPill } from './ShopSellerContactPicker';
import { createAffiliateLink } from './lib/affiliate';
import {
  fetchMerchantProducts, fetchMyFavoriteProducts, removeProductFavorite, addProductFavorite,
  type CommerceProduct,
} from './lib/commerce';
import {
  fetchMyFollowedMerchants, fetchMerchantBillingPlans, fetchMyBillingSubscriptions, followMerchant, unfollowMerchant,
  type ShoppingMerchant, type MerchantBillingPlan, type MerchantBillingSubscription,
} from './lib/shopping';
import { cartTotalItems, type CommerceCart } from './CommerceOrders';
import { ProductImageThumb, ProductPriceBlock } from './ProductDisplay';
import { ProductRatingBadge } from './ProductRating';
import { BillingPlanRow } from './MerchantBillingAndCart';
import { EmptyState, ErrorCard } from './EmptyState';
import { WishlistButton } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

function ProductCatalogView({
  merchant, cart, onSetQty, onBack, onViewCart, onOpenProduct, onContactSeller,
}: {
  merchant: ShoppingMerchant;
  cart: CommerceCart;
  onSetQty: (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => void;
  onBack: () => void;
  onViewCart: () => void;
  onOpenProduct: (product: CommerceProduct) => void;
  onContactSeller: () => void;
}) {
  const { t } = useI18n();
  const [catalog, setCatalog] = useState<{ businessName: string; products: CommerceProduct[] } | null>(null);
  const showSkeleton = useDeferredLoading(catalog === null);
  const [error, setError] = useState<string | null>(null);
  const [favoritedIds, setFavoritedIds] = useState<Set<string>>(new Set());
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [following, setFollowing] = useState(false);
  const [followBusy, setFollowBusy] = useState(false);
  const [billingPlans, setBillingPlans] = useState<MerchantBillingPlan[]>([]);
  const [mySubscriptions, setMySubscriptions] = useState<MerchantBillingSubscription[]>([]);

  const load = () => {
    setError(null);
    fetchMerchantProducts(merchant.merchantId)
      // Real filter (2026-08-25, direct user feedback: "booking... supposed to be in
      // itunda place not in itunda shopping") -- a product with a real durationMinutes
      // set is a real-time appointment at this merchant's physical location, not a
      // cart-able online good, so it no longer shows in Shop's own catalog at all.
      // Booking now lives in itunda Place (maps-mfe's own MapsBooking.tsx), reachable
      // from the same real merchant pinned on the map.
      .then((r) => setCatalog({ businessName: r.merchant.businessName, products: r.products.filter((p) => p.durationMinutes == null) }))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchMyFavoriteProducts()
      .then((r) => setFavoritedIds(new Set(r.favorites.map((f) => f.productId))))
      .catch(() => {
        // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
      });
    // Real Naver Smart Store-style "알림받기" follow status -- non-critical, same
    // discipline as the wishlist fetch above.
    fetchMyFollowedMerchants()
      .then((follows) => setFollowing(follows.some((f) => f.merchantId === merchant.merchantId)))
      .catch(() => {});
    // Real Kakao Pay 정기결제/Toss 빌링키-style recurring billing plans this merchant
    // itself has published -- non-critical, same discipline as follow/wishlist above.
    fetchMerchantBillingPlans(merchant.merchantId)
      .then(setBillingPlans)
      .catch(() => {});
    fetchMyBillingSubscriptions()
      .then((subs) => setMySubscriptions(subs.filter((s) => s.merchantId === merchant.merchantId)))
      .catch(() => {});
  };

  useEffect(load, [merchant.merchantId]);

  const toggleFollow = async () => {
    setFollowBusy(true);
    try {
      if (following) {
        await unfollowMerchant(merchant.merchantId);
        setFollowing(false);
      } else {
        await followMerchant(merchant.merchantId);
        setFollowing(true);
      }
    } catch {
      // Real, non-critical -- a follow-toggle failure shouldn't block browsing.
    } finally {
      setFollowBusy(false);
    }
  };

  const toggleFavorite = async (productId: string) => {
    setTogglingId(productId);
    try {
      if (favoritedIds.has(productId)) {
        await removeProductFavorite(productId);
        setFavoritedIds((prev) => { const next = new Set(prev); next.delete(productId); return next; });
      } else {
        await addProductFavorite(productId);
        setFavoritedIds((prev) => new Set(prev).add(productId));
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block browsing.
    } finally {
      setTogglingId(null);
    }
  };

  // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link generation (item 229) --
  // see lib/affiliate.ts's own doc comment. Any user can generate a real trackable
  // link for any product and earns a real 3% commission on a resulting purchase.
  const [sharingId, setSharingId] = useState<string | null>(null);
  const [shareNotice, setShareNotice] = useState<string | null>(null);
  const shareProduct = async (productId: string) => {
    setSharingId(productId);
    setShareNotice(null);
    try {
      const link = await createAffiliateLink(productId);
      const url = `${window.location.origin}${window.location.pathname}?ref=${link.code}`;
      await navigator.clipboard.writeText(url).catch(() => {});
      setShareNotice('Link copied — earn 3% on any purchase through it.');
    } catch (err) {
      setShareNotice(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSharingId(null);
      setTimeout(() => setShareNotice(null), 4000);
    }
  };

  const myLines = cart[merchant.merchantId]?.lines ?? {};
  const qtyFor = (productId: string) => myLines[productId]?.quantity ?? 0;
  const totalCartItems = cartTotalItems(cart);

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (catalog === null) {
    return showSkeleton ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to merchants">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, flex: 1 }}>{catalog.businessName}</h3>
        <ShopMessageSellerButton onClick={onContactSeller} />
        <button
          type="button"
          onClick={toggleFollow}
          disabled={followBusy}
          className={following ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'}
          style={{ padding: '6px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}
        >
          {following ? 'Following' : 'Follow'}
        </button>
      </div>
      {shareNotice && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', marginBottom: '12px' }} role="status">{shareNotice}</p>}
      {billingPlans.length > 0 && (
        <div style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Subscription plans</h4>
          {billingPlans.map((plan) => (
            <BillingPlanRow
              key={plan.id}
              plan={plan}
              subscription={mySubscriptions.find((s) => s.planId === plan.id && s.status === 'ACTIVE')}
              onChanged={load}
            />
          ))}
        </div>
      )}
      {catalog.products.length === 0 ? (
        <EmptyState message="This store hasn't added products yet — check back soon." />
      ) : (
        // Real 2-column image-led grid (2026-07-21), replacing the previous
        // single-column text-only row -- closes docs/DESIGN_REFERENCES.md Section 5
        // recommendation #5 (Chloe Youn's Coupang case study: real cards are
        // image-led, with add-to-cart/wishlist directly on the card, not buried behind
        // a detail-page visit -- recommendation #7).
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px', marginBottom: totalCartItems > 0 ? '80px' : 0 }}>
          {catalog.products.map((item) => (
            <div key={item.id} className="itunda-card" style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ position: 'absolute', top: '10px', right: '10px', zIndex: 1, display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-end' }}>
                <WishlistButton
                  favorited={favoritedIds.has(item.id)}
                  busy={togglingId === item.id}
                  onToggle={() => toggleFavorite(item.id)}
                />
                <button
                  type="button"
                  onClick={() => shareProduct(item.id)}
                  disabled={sharingId === item.id}
                  aria-label="Share this product and earn a commission"
                  title="Share & earn 3%"
                  style={{ background: 'var(--itunda-white)', borderRadius: '999px', padding: '6px', boxShadow: '0 1px 4px rgba(0,0,0,0.12)', fontSize: 'var(--itunda-type-scale-13-size)' }}
                >
                  🔗
                </button>
              </div>
              {/* Real tap-through to the new product-detail screen (2026-07-21) -- see
                  ProductDetailView's own doc comment. Wraps only the image/name/price so
                  the wishlist heart above stays independently tappable. */}
              <button
                type="button"
                onClick={() => onOpenProduct(item)}
                aria-label={`View ${item.name}`}
                style={{ display: 'flex', flexDirection: 'column', gap: '6px', textAlign: 'left', width: '100%', padding: 0 }}
              >
                <ProductImageThumb imageUrl={item.imageUrl} />
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, lineHeight: 1.3 }}>{item.name}</p>
                <ProductPriceBlock price={item.price} originalPrice={item.originalPrice} discountPercent={item.discountPercent} />
              </button>
              <p style={{ minHeight: '16px', fontSize: 'var(--itunda-type-scale-12-size)', color: item.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                {item.stockQuantity === null || item.stockQuantity === undefined ? 'Available' : item.stockQuantity === 0 ? 'Out of stock' : `${item.stockQuantity} available`}
              </p>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                <ProductRatingBadge productId={item.id} />
                {item.isBestSeller && <ShopBestSellerBadge />}
              </div>
              <ShopDeliveryEtaPill minutes={merchant.deliveryTimeMinutes} />
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px', marginTop: '4px' }}>
                <button onClick={() => onSetQty(merchant, item, qtyFor(item.id) - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{qtyFor(item.id)}</span>
                <button
                  onClick={() => onSetQty(merchant, item, qtyFor(item.id) + 1)}
                  disabled={item.stockQuantity !== null && item.stockQuantity !== undefined && qtyFor(item.id) >= item.stockQuantity}
                  className="itunda-btn itunda-btn-secondary"
                  style={{ padding: '6px 12px' }}
                >+</button>
              </div>
            </div>
          ))}
        </div>
      )}
      {totalCartItems > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={onViewCart}
        >
          View cart ({totalCartItems} item{totalCartItems === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

export { ProductCatalogView };
