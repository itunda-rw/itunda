import { ShoppingBag } from 'lucide-react';
import { IconStar } from './icons/ItundaIcons';
import { FlameGlyph, SoldOutGlyph } from './icons/ItundaFaceMisc';
import { HeartFilled } from './icons/ItundaFaceHearts';
import { ShopBestSellerBadge } from './ShopSellerContactPicker';
import type { ProductSearchResult, ShoppingMerchant } from './lib/shopping';
import { ProductImageThumb, ProductPriceBlock } from './ProductDisplay';

// Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a real
// product-search catalog list, matching the same flat-entity-list convention
// Android's VehicleValuationScreen/GroupAccountScreen already established
// (docs/UI_UX_GUIDELINES.md §10).
export function SearchResultsList({ searchQuery, searchResults, onOpenSearchResult }: {
  searchQuery: string;
  searchResults: ProductSearchResult[];
  onOpenSearchResult: (r: ProductSearchResult) => void;
}) {
  if (searchResults.length === 0) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>No products matched "{searchQuery}".</p>;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {searchResults.map((r) => (
        <button
          key={r.id}
          onClick={() => onOpenSearchResult(r)}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', textAlign: 'left', width: '100%', gap: '12px' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <ProductImageThumb imageUrl={r.imageUrl} size={44} />
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{r.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Sold by {r.merchantName}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: r.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                {r.stockQuantity === null || r.stockQuantity === undefined ? 'Available' : r.stockQuantity === 0 ? 'Out of stock' : `${r.stockQuantity} available`}
              </p>
              {r.isBestSeller && <ShopBestSellerBadge />}
            </div>
          </div>
          <ProductPriceBlock price={r.price} originalPrice={r.originalPrice} discountPercent={r.discountPercent} />
        </button>
      ))}
    </div>
  );
}

// Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a real
// merchant catalog list, same flat-entity-list convention as the product search
// list above.
export function MerchantList({ merchants, totalItems, onSelectMerchant }: {
  merchants: ShoppingMerchant[];
  totalItems: number;
  onSelectMerchant: (merchant: ShoppingMerchant) => void;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: totalItems > 0 ? '80px' : 0 }}>
      {merchants.map((m) => (
        <button
          key={m.merchantId}
          onClick={() => onSelectMerchant(m)}
          style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%' }}
        >
          {/* Real fix (2026-08-19): this row used to show a generic icon and the exact
              same hardcoded subtitle for every merchant, ignoring the real photoUrl/
              rating/reviewCount/distanceKm/deliveryTimeMinutes/favoriteCount fields
              ShoppingMerchant already carries -- OrderFoodView's restaurant row (same
              ShoppingMerchant type) already had this real enrichment; this was simply
              never ported over to Shop's own merchant list. */}
          {m.photoUrl ? (
            <img
              src={m.photoUrl} alt=""
              style={{ width: '44px', height: '44px', borderRadius: '12px', objectFit: 'cover', flexShrink: 0, backgroundColor: 'var(--itunda-indigo-light)' }}
              onError={(e) => { e.currentTarget.style.display = 'none'; }}
            />
          ) : (
            <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <ShoppingBag size={20} color="var(--itunda-indigo)" />
            </div>
          )}
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', display: 'flex', alignItems: 'center', gap: '6px' }}>
              {m.businessName}
              {m.isAcceptingOrders === false && (
                <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, color: 'var(--itunda-grey-500)', backgroundColor: 'var(--itunda-grey-100)', padding: '2px 8px', borderRadius: '99px' }}>
                  ⏸ Temporarily paused
                </span>
              )}
            </p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
              {m.category && <span>{m.category}</span>}
              {m.rating != null && (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }}>
                  <IconStar size={11} color="#F5A623" fill="#F5A623" /> {m.rating.toFixed(1)} ({m.reviewCount})
                </span>
              )}
              {!!m.favoriteCount && m.favoriteCount > 0 && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <HeartFilled size={11} /> {m.favoriteCount.toLocaleString()}</span>}
              {m.distanceKm != null && <span>· {m.distanceKm.toFixed(1)} km</span>}
              {m.deliveryTimeMinutes != null && <span>· ~{m.deliveryTimeMinutes} min</span>}
              {m.isBusy && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <FlameGlyph size={12} /> Busy, delivery may take longer</span>}
              {m.closedToday && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <SoldOutGlyph size={12} /> Closed today</span>}
              {m.minOrderAmount != null && <span>· Min {m.minOrderAmount.toLocaleString()} RWF</span>}
              {!m.category && m.rating == null && m.distanceKm == null && <span>Real cart checkout, real delivery tracking</span>}
            </p>
          </div>
        </button>
      ))}
    </div>
  );
}
