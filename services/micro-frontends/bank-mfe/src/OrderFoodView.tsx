import { useState, useEffect } from 'react';
import { Utensils } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { ErrorCard } from './EmptyState';
import { IconStar, IconShieldCheck } from './icons/ItundaIcons';
import { HeartFilled } from './icons/ItundaFaceHearts';
import { FlameGlyph, SoldOutGlyph } from './icons/ItundaFaceMisc';
import { recentlyViewedRestaurantsStore } from './lib/recentlyViewed';
import { RecommendedDishesRail, PopularDishesRail, EatsNearbyAdsRail } from './EatsDishRails';
import { ProductImageThumb } from './ProductDisplay';
import type { ShoppingMerchant } from './lib/shopping';
import {
  fetchMyFavoriteRestaurants, fetchRestaurants, fetchRestaurantCategories, removeFavoriteRestaurant, addFavoriteRestaurant,
  fetchEatsOrder, fetchMenu, type RestaurantSortMode, type EatsOrder,
} from './lib/eats';
import { MenuView } from './MenuView';
import { MyEatsOrdersView, FavoriteRestaurantsView } from './EatsOrdersAndFavorites';
import { SearchAndCategoryChips, WishlistButton } from './BankDashboard';

export function OrderFoodView({ onMessageSeller, onReportIssue }: { onMessageSeller: (conversationId: string) => void; onReportIssue: (transactionId: string) => void }) {
  const { t } = useI18n();
  const [view, setView] = useState<'BROWSE' | 'FAVORITES' | 'ORDERS'>('BROWSE');
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
  // even when that restaurant has been filtered out of the currently-browsed list.
  const [allRestaurants, setAllRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  // Real Coupang Eats-style sort picker (2026-08-19, replacing the single favorites-only
  // toggle) -- see fetchRestaurants' own RestaurantSortMode doc comment for the real
  // backend-supported modes. 'distance'/'delivery_time' need a real buyer location;
  // 'favorites'/'rating' don't. Only these 4 real modes are offered -- no fabricated
  // "Recommended"/"Newest" pill, since nothing on the backend actually sorts by either.
  const [sortMode, setSortMode] = useState<RestaurantSortMode | null>(null);
  const [buyerLocation, setBuyerLocation] = useState<{ lat: number; lng: number } | null>(null);
  // Real bug caught by live click-through (2026-08-19): an earlier version of this used a
  // plain `locatingForSort: boolean` with no record of WHICH mode triggered it, so both
  // 'distance' and 'delivery_time' pills showed "Locating..." simultaneously regardless of
  // which one was actually clicked. Tracking the specific pending mode fixes this.
  const [pendingSortMode, setPendingSortMode] = useState<RestaurantSortMode | null>(null);
  const [sortLocationError, setSortLocationError] = useState<string | null>(null);

  const selectSortMode = (mode: RestaurantSortMode | null) => {
    setSortLocationError(null);
    if (mode === null || mode === 'favorites' || mode === 'rating' || buyerLocation) {
      setSortMode(mode);
      return;
    }
    // 'distance'/'delivery_time' need a real position first -- same geolocation pattern
    // ListingCard's own handleShowDirections already uses.
    if (!navigator.geolocation) {
      setSortLocationError('This browser does not support real location access.');
      return;
    }
    setPendingSortMode(mode);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setPendingSortMode(null);
        setBuyerLocation({ lat: position.coords.latitude, lng: position.coords.longitude });
        setSortMode(mode);
      },
      () => {
        setPendingSortMode(null);
        setSortLocationError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<EatsOrder | null>(null);
  const [reorderCart, setReorderCart] = useState<Record<string, number> | null>(null);
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [reorderError, setReorderError] = useState<string | null>(null);
  // Real "recently viewed restaurants" rail (2026-08-23) -- ported from Android's
  // identical real feature (RecentlyViewedRestaurantsStore.kt), never shipped to web
  // before now -- see lib/recentlyViewed.ts's own doc comment. A plain effect on
  // `selected` (rather than wrapping every one of this view's several real
  // restaurant-opening call sites -- the browse list, favorites, dish grid, Reorder,
  // deep links) covers every real entry point uniformly.
  const [recentlyViewedRestaurants, setRecentlyViewedRestaurants] = useState(recentlyViewedRestaurantsStore.getAll());
  useEffect(() => {
    if (!selected) return;
    setRecentlyViewedRestaurants(
      recentlyViewedRestaurantsStore.add({ id: selected.merchantId, businessName: selected.businessName, category: selected.category, photoUrl: selected.photoUrl }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.merchantId]);
  // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for a
  // fast star-toggle lookup on each browse card.
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);

  const loadFavorites = () => {
    fetchMyFavoriteRestaurants().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.restaurantId)))).catch(() => {});
  };

  useEffect(() => {
    fetchRestaurants().then(setAllRestaurants).catch(() => {});
    fetchRestaurantCategories().then(setCategories).catch(() => {});
    loadFavorites();
  }, []);

  const toggleFavorite = async (restaurantId: string) => {
    setFavoritingId(restaurantId);
    try {
      if (favoriteIds.has(restaurantId)) {
        await removeFavoriteRestaurant(restaurantId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(restaurantId); return next; });
      } else {
        await addFavoriteRestaurant(restaurantId);
        setFavoriteIds((prev) => new Set(prev).add(restaurantId));
      }
    } catch {
      // Real, non-critical -- a failed toggle just leaves the star as-is; the user can retry.
    } finally {
      setFavoritingId(null);
    }
  };

  // Real category/search filter (2026-07-19), debounced so a search box doesn't
  // re-fetch on every keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const load = () => {
    setError(null);
    fetchRestaurants(selectedCategory ?? undefined, debouncedSearch || undefined, buyerLocation?.lat, buyerLocation?.lng, sortMode ?? undefined)
      .then(setRestaurants)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, [selectedCategory, debouncedSearch, sortMode, buyerLocation]);

  // Real "Reorder" (2026-07-19): re-populates a fresh cart from a real past order's
  // real items, filtered to whatever's still real and active on the restaurant's
  // current menu -- a discontinued item is silently dropped rather than added as a
  // phantom line the buyer can't actually check out with.
  const handleReorder = async (order: EatsOrder) => {
    setReorderingId(order.id);
    setReorderError(null);
    try {
      const restaurant = allRestaurants?.find((r) => r.merchantId === order.restaurantId);
      if (!restaurant) {
        setReorderError('This restaurant is no longer available.');
        return;
      }
      const [{ items }, menu] = await Promise.all([fetchEatsOrder(order.id), fetchMenu(order.restaurantId)]);
      const activeProductIds = new Set(menu.products.filter((p) => p.active).map((p) => p.id));
      const cart: Record<string, number> = {};
      items.forEach((item) => {
        if (activeProductIds.has(item.productId)) {
          cart[item.productId] = (cart[item.productId] ?? 0) + item.quantity;
        }
      });
      if (Object.keys(cart).length === 0) {
        setReorderError('None of the items from that order are on the menu anymore.');
        return;
      }
      setReorderCart(cart);
      setSelected(restaurant);
      setView('BROWSE');
    } catch (err) {
      setReorderError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setReorderingId(null);
    }
  };

  if (confirmed) {
    // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this IS
    // the whole confirmation screen's content (docs/UI_UX_GUIDELINES.md §10).
    return (
      <div style={{ textAlign: 'center', padding: '28px' }}>
        <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString('en-US')} RWF</p>
        {confirmed.promotionDiscount > 0 && (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', marginBottom: '4px' }}>
            {confirmed.promotionDiscount.toLocaleString('en-US')} RWF off, on us
          </p>
        )}
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>Delivering to {confirmed.deliveryAddress}</p>
        <button
          className="itunda-btn itunda-btn-secondary"
          onClick={() => { setConfirmed(null); setSelected(null); setView('ORDERS'); }}
        >
          Track order
        </button>
      </div>
    );
  }

  if (selected) {
    return (
      <MenuView
        restaurant={selected}
        onBack={() => { setSelected(null); setReorderCart(null); }}
        onOrderPlaced={setConfirmed}
        initialCart={reorderCart ?? undefined}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'FAVORITES', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Restaurants' : v === 'FAVORITES' ? 'Favorites' : 'My orders'}
          </button>
        ))}
      </div>

      {view === 'ORDERS' ? (
        <>
          {reorderError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{reorderError}</p>}
          <MyEatsOrdersView onReorder={handleReorder} reorderingId={reorderingId} restaurants={allRestaurants} onMessageSeller={onMessageSeller} onReportIssue={onReportIssue} />
        </>
      ) : view === 'FAVORITES' ? (
        <FavoriteRestaurantsView
          onOpen={(r) => setSelected({ merchantId: r.restaurantId, businessName: r.businessName, category: r.category, cashbackRate: '1%' })}
          onChanged={loadFavorites}
        />
      ) : (
        <>
          <SearchAndCategoryChips
            searchInput={searchInput}
            onSearchChange={setSearchInput}
            placeholder="Search restaurants"
            categories={categories}
            selectedCategory={selectedCategory}
            onSelectCategory={setSelectedCategory}
          />
          {/* Real Coupang Eats-style sort picker (2026-08-19) -- see selectSortMode's
              own doc comment. Only real backend-supported modes, no fabricated
              "Recommended"/"Newest" pill. discount/min_order added 2026-08-28 (itunda
              Eats redesign) -- adapts the real Coupang Eats reference's own quick-
              filter chip row (최대할인/최소주문낮은매장) onto itunda's own real
              per-merchant signals, see ShoppingMerchantBrowseService.browse's own doc
              comment on both real sort modes. Neither needs a real buyer location. */}
          <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '4px', paddingBottom: '2px' }}>
            {([
              { mode: null, label: 'Default' },
              { mode: 'distance' as const, label: pendingSortMode === 'distance' ? '📍 Locating…' : '📍 Nearest' },
              { mode: 'delivery_time' as const, label: pendingSortMode === 'delivery_time' ? '⏱ Locating…' : '⏱ Fastest delivery' },
              { mode: 'rating' as const, label: '⭐ Highest rated' },
              { mode: 'favorites' as const, label: '❤️ Most favorited' },
              { mode: 'discount' as const, label: '🔥 Max discount' },
              { mode: 'min_order' as const, label: '💸 Low minimum order' },
            ]).map(({ mode, label }) => (
              <button
                key={label}
                className="itunda-btn itunda-btn-secondary"
                disabled={pendingSortMode !== null}
                style={{
                  whiteSpace: 'nowrap', padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)',
                  backgroundColor: sortMode === mode ? 'var(--itunda-indigo-light)' : undefined,
                  color: sortMode === mode ? 'var(--itunda-indigo)' : undefined,
                }}
                onClick={() => selectSortMode(mode)}
              >
                {label}
              </button>
            ))}
          </div>
          {sortLocationError && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{sortLocationError}</p>
          )}
          {/* Real dish-discovery rails (itunda Eats redesign, 2026-08-28) -- see
              EatsDishRails.tsx's own doc comment. Hidden once filtering starts, same
              "merchandising above the raw list" discipline recently-viewed already
              establishes just below. */}
          {!selectedCategory && !debouncedSearch && (
            <>
              <RecommendedDishesRail onOpenRestaurant={setSelected} />
              <PopularDishesRail onOpenRestaurant={setSelected} />
              <EatsNearbyAdsRail onOpenRestaurant={setSelected} />
            </>
          )}
          {/* Real "recently viewed restaurants" rail -- see lib/recentlyViewed.ts's own
              doc comment. Hidden once the user starts filtering, same "merchandising
              above the raw list, gone once actively searching" discipline the Shop
              rails below already establish. */}
          {!selectedCategory && !debouncedSearch && recentlyViewedRestaurants.length > 0 && (
            <div style={{ marginBottom: '16px' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>🕒 Recently viewed</p>
              <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
                {recentlyViewedRestaurants.map((rv) => (
                  <button
                    key={rv.id}
                    onClick={() => setSelected({ merchantId: rv.id, businessName: rv.businessName, category: rv.category ?? null, cashbackRate: '1%' })}
                    className="itunda-card"
                    style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '130px', flexShrink: 0, gap: '4px' }}
                  >
                    <ProductImageThumb imageUrl={rv.photoUrl} size={100} />
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{rv.businessName}</p>
                    {rv.category && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{rv.category}</p>}
                  </button>
                ))}
              </div>
            </div>
          )}
          {error ? (
            <ErrorCard message={error} onRetry={load} />
          ) : restaurants === null ? (
            <div className="itunda-flat-section skeleton" style={{ height: '220px' }} />
          ) : restaurants.length === 0 ? (
            // Real copy-voice fix (item 244, round 5 of the empty-state pass,
            // ported from the same-day Android/iOS fix): "registered yet" is
            // honest about whose gap this is -- no restaurant has joined yet,
            // not something the reader is missing a step on.
            // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a
            // plain one-line empty-state message.
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              {selectedCategory || debouncedSearch ? 'No restaurants match your search — try a different category or search term.' : 'No restaurants registered yet — check back once restaurants in your area join itunda Eats.'}
            </p>
          ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {restaurants.map((r) => (
            <div
              key={r.merchantId}
              role="button"
              tabIndex={0}
              onClick={() => setSelected(r)}
              onKeyDown={(e) => { if (e.key === 'Enter') setSelected(r); }}
              className="itunda-card"
              style={{ padding: 0, overflow: 'hidden', textAlign: 'left', width: '100%', cursor: 'pointer' }}
            >
              {/* Real photo-forward card rework (2026-08-19) -- real Coupang Eats/
                  배달의민족 both use a card UI that emphasizes food/photo over a
                  text-dense row (sourced: "카드 뷰 형태의 UI를 사용해 매장보다는
                  '음식'을 강조하는 디자인"). itunda has no per-dish photo in its real
                  data model (MenuItem carries no photo field) -- honestly uses the
                  merchant's own real photoUrl as the card image rather than
                  fabricating a per-dish one, matching the real STRUCTURAL pattern
                  (large photo-topped card) without overclaiming dish-level detail
                  that doesn't exist. */}
              <div style={{ position: 'relative', width: '100%', height: '140px', backgroundColor: 'var(--itunda-indigo-light)' }}>
                {r.photoUrl ? (
                  <img
                    src={r.photoUrl} alt=""
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    onError={(e) => { e.currentTarget.style.display = 'none'; }}
                  />
                ) : (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Utensils size={32} color="var(--itunda-indigo)" />
                  </div>
                )}
                {r.isAcceptingOrders === false && (
                  <span style={{ position: 'absolute', top: '10px', left: '10px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, color: 'var(--itunda-white)', backgroundColor: 'rgba(0,0,0,0.6)', padding: '3px 9px', borderRadius: '99px' }}>
                    ⏸ Temporarily paused
                  </span>
                )}
                {/* Real Coupang 와우(WOW)-style per-restaurant membership badge
                    (itunda Eats redesign, 2026-08-28) -- see
                    ShoppingMerchant.participatesInEatsMembership's own doc comment.
                    A real, merchant-opted-in flag, never shown on every card. */}
                {r.participatesInEatsMembership && (
                  <span style={{ position: 'absolute', top: '10px', left: r.isAcceptingOrders === false ? '124px' : '10px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)', padding: '3px 9px', borderRadius: '99px' }}>
                    Member — free delivery
                  </span>
                )}
                {/* Real gap found live (2026-08-10), docs/DESIGN_REFERENCES.md Section 7's
                    own "four separate bespoke favorite implementations" note: this was a
                    hand-rolled Lucide Heart button, distinct from the shared
                    WishlistButton every other favorite/heart affordance on this screen
                    already uses. Unified onto the same shared component; overlaid on the
                    photo's corner, matching real Coupang Eats/Baemin's own card
                    convention. */}
                <div onClick={(e) => e.stopPropagation()} style={{ position: 'absolute', top: '8px', right: '8px', backgroundColor: 'rgba(0,0,0,0.35)', borderRadius: '999px' }}>
                  <WishlistButton
                    favorited={favoriteIds.has(r.merchantId)}
                    busy={favoritingId === r.merchantId}
                    onToggle={() => toggleFavorite(r.merchantId)}
                  />
                </div>
              </div>
              <div style={{ padding: '14px 16px' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
                  {r.businessName}
                </p>
                {/* Real browse-card enrichment (2026-07-21) -- rating/reviewCount/distance/
                    delivery-time estimate/min order, closing docs/DESIGN_REFERENCES.md's
                    Eats recommendations #1/#2. Every clause is conditionally rendered on
                    real data being present -- never a fabricated placeholder. */}
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                  {r.category && <span>{r.category}</span>}
                  {r.rating != null && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }}>
                      <IconStar size={11} color="#F5A623" fill="#F5A623" /> {r.rating.toFixed(1)} ({r.reviewCount})
                    </span>
                  )}
                  {/* Real Baemin 찜 (favorites) count (2026-08-16) -- see
                      ShoppingController.getEligibleMerchants's own doc comment. */}
                  {!!r.favoriteCount && r.favoriteCount > 0 && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <HeartFilled size={11} /> {r.favoriteCount.toLocaleString('en-US')}</span>}
                  {r.distanceKm != null && <span>· {r.distanceKm.toFixed(1)} km</span>}
                  {r.deliveryTimeMinutes != null && <span>· ~{r.deliveryTimeMinutes} min</span>}
                  {/* Real Uber Eats-style "busy kitchen" delay explanation (2026-08-16) --
                      see ShoppingController.getEligibleMerchants's own doc comment.
                      deliveryTimeMinutes above already includes the real delay bump when
                      this is true; this badge is why it's longer than usual, not a
                      separate/contradictory number. */}
                  {r.isBusy && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <FlameGlyph size={12} /> Busy, delivery may take longer</span>}
                  {/* Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day
                      schedule) (2026-08-16) -- see Merchant.isClosedToday's own doc
                      comment. */}
                  {r.closedToday && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <SoldOutGlyph size={12} /> Closed today</span>}
                  {r.minOrderAmount != null && <span>· Min {r.minOrderAmount.toLocaleString('en-US')} RWF</span>}
                  {!r.category && r.rating == null && r.distanceKm == null && <span>Real menu, real delivery</span>}
                </p>
                {/* Real "Discount" badge (itunda Eats redesign, 2026-08-28) -- see
                    ShoppingMerchant.maxDiscountPercent's own doc comment: the real,
                    currently-highest discount among this restaurant's own active
                    menu, never a fabricated store-wide promo. */}
                {r.maxDiscountPercent != null && r.maxDiscountPercent > 0 && (
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-red)', marginTop: '2px' }}>
                    Up to {r.maxDiscountPercent}% off
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
          )}
        </>
      )}
    </div>
  );
}
