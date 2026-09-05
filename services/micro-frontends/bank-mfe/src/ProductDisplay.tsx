import { useState, useEffect } from 'react';
import { ShoppingBag } from 'lucide-react';
import { IconBack } from './icons/ItundaIcons';
import { ShopMessageSellerButton, ShopBestSellerBadge, ShopDeliveryEtaPill } from './ShopSellerContactPicker';
import {
  fetchMyFavoriteProducts, fetchProduct, addProductFavorite, removeProductFavorite, type CommerceProduct,
} from './lib/commerce';
import type { ShoppingMerchant } from './lib/shopping';
import { cartTotalItems, type CommerceCart } from './CommerceOrders';
import { PriceTiersDisplay, ProductInquirySection } from './CommerceOrders';
import { ProductRatingBadge } from './ProductRating';
import { WishlistButton, SubscribeAndSaveButton } from './BankDashboard';

// Real product-image thumbnail (2026-07-21) -- imageUrl is a merchant-supplied external
// URL (see backend MerchantProduct.kt's own doc comment: no upload/storage layer exists
// in this backend, so this is a real "bring your own URL" v1, not a fake pipeline). A
// plain <img> with onError falling back to the same placeholder icon shown for a
// product that simply has no image set at all -- both are real, valid states.
export function ProductImageThumb({ imageUrl, size = 96 }: { imageUrl?: string | null; size?: number }) {
  const [failed, setFailed] = useState(false);
  if (!imageUrl || failed) {
    return (
      <div style={{ width: size, height: size, borderRadius: '12px', background: 'var(--itunda-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <ShoppingBag size={size * 0.4} color="var(--itunda-indigo)" />
      </div>
    );
  }
  return (
    <img
      src={imageUrl}
      alt=""
      onError={() => setFailed(true)}
      style={{ width: size, height: size, borderRadius: '12px', objectFit: 'cover', background: 'var(--itunda-grey-100)', flexShrink: 0 }}
    />
  );
}

// Real discount-price display (2026-07-21) -- Baymard Institute's own placement
// research (docs/DESIGN_REFERENCES.md Section 5): the discount % must sit immediately
// next to the struck-through original price. discountPercent is always server-computed
// (see backend doc comment), never trusted from the client -- purely a rendering of
// numbers the server already validated.
export function ProductPriceBlock({ price, originalPrice, discountPercent }: { price: number; originalPrice?: number | null; discountPercent?: number | null }) {
  if (originalPrice != null && discountPercent != null && discountPercent > 0) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-red)' }}>{discountPercent}%</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{price.toLocaleString('en-US')} RWF</span>
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', textDecoration: 'line-through' }}>{originalPrice.toLocaleString('en-US')} RWF</p>
      </div>
    );
  }
  return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{price.toLocaleString('en-US')} RWF</p>;
}

// Real dedicated product-detail screen (2026-07-21), closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #6 -- until now tapping a product
// anywhere in Commerce only ever revealed the flat catalog grid's inline qty stepper;
// there was no tap-through view showing the full-size image, the discount breakdown, a
// description, and the written reviews together. Reuses every already-proven piece
// rather than inventing new ones: ProductImageThumb (larger), ProductPriceBlock,
// ProductRatingBadge (which already lazily expands into the real written-review list),
// and the same wishlist toggle/qty-stepper/add-to-cart plumbing ProductCatalogView
// already has -- this is a real second surface for the same real data, not new business
// logic.
export function ProductDetailView({
  merchant, product, cart, onSetQty, onBack, onViewCart, onContactSeller,
}: {
  merchant: ShoppingMerchant;
  product: CommerceProduct;
  cart: CommerceCart;
  onSetQty: (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => void;
  onBack: () => void;
  onViewCart: () => void;
  onContactSeller: () => void;
}) {
  const [favorited, setFavorited] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchMyFavoriteProducts()
      .then((favorites) => setFavorited(favorites.some((f) => f.productId === product.id)))
      .catch(() => {
        // Real, non-critical -- a wishlist-status fetch failure shouldn't block viewing.
      });
  }, [product.id]);

  // Real Coupang WING 상품분석 (product analytics) view count (2026-08-16) -- fetches
  // the real, freshly server-incremented count once per detail-view mount, same
  // "non-critical, falls back to nothing on failure" discipline the favorite-status
  // fetch above already establishes. This is also the real trigger the pre-existing
  // GET /shopping/products/{id} endpoint needed to ever be called at all.
  const [freshViewCount, setFreshViewCount] = useState<number | null>(null);
  useEffect(() => {
    fetchProduct(product.id).then((p) => setFreshViewCount(p.viewCount ?? null)).catch(() => {});
  }, [product.id]);

  const toggleFavorite = async () => {
    setBusy(true);
    try {
      if (favorited) {
        await removeProductFavorite(product.id);
        setFavorited(false);
      } else {
        await addProductFavorite(product.id);
        setFavorited(true);
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block viewing.
    } finally {
      setBusy(false);
    }
  };

  const qty = cart[merchant.merchantId]?.lines[product.id]?.quantity ?? 0;
  const totalCartItems = cartTotalItems(cart);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to catalog">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, flex: 1 }}>{merchant.businessName}</h3>
        <ShopMessageSellerButton onClick={onContactSeller} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', marginBottom: totalCartItems > 0 ? '80px' : 0 }}>
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <ProductImageThumb imageUrl={product.imageUrl} size={220} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{product.name}</p>
            <ProductPriceBlock price={product.price} originalPrice={product.originalPrice} discountPercent={product.discountPercent} />
            {freshViewCount != null && (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>Views {freshViewCount.toLocaleString('en-US')}</p>
            )}
          </div>
          <WishlistButton favorited={favorited} busy={busy} onToggle={toggleFavorite} />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <ProductRatingBadge productId={product.id} />
          {product.isBestSeller && <ShopBestSellerBadge />}
          <ShopDeliveryEtaPill minutes={merchant.deliveryTimeMinutes} />
        </div>
        {product.description && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>{product.description}</p>
        )}
        <PriceTiersDisplay productId={product.id} regularPrice={product.price} />
        {/* BookingWidget/MerchantBookingInfoSection moved to itunda Place (2026-08-25)
            -- see maps-mfe's own MapsBooking.tsx doc comment. This product-detail page
            no longer reaches a bookable-service product at all (see the catalog fetch
            above's own filter), so there's nothing left to render here for booking. */}
        <ProductInquirySection productId={product.id} />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '16px', paddingTop: '4px', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <button onClick={() => onSetQty(merchant, product, Math.max(0, qty - 1))} className="itunda-btn itunda-btn-secondary" style={{ padding: '8px 16px' }}>−</button>
          <span style={{ minWidth: '24px', textAlign: 'center', fontWeight: 700, fontSize: 'var(--itunda-type-scale-16-size)' }}>{qty}</span>
          <button onClick={() => onSetQty(merchant, product, qty + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '8px 16px' }}>+</button>
        </div>
        <SubscribeAndSaveButton merchantId={merchant.merchantId} productId={product.id} />
        <button className="itunda-btn itunda-btn-primary" onClick={() => onSetQty(merchant, product, Math.max(1, qty))}>
          {qty > 0 ? 'Update cart' : 'Add to cart'}
        </button>
      </div>
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
