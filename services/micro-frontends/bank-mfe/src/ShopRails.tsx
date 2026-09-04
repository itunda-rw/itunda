import { useState, useEffect, useRef } from 'react';
import { Zap } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { IconStar } from './icons/ItundaIcons';
import { FlameGlyph } from './icons/ItundaFaceMisc';
import { Badge } from './Badge';
import { ShopBestSellerBadge } from './ShopSellerContactPicker';
import { fetchActiveTimeDeals, fetchShopBanners, type TimeDealView } from './lib/timeDeal';
import { completeShoppingMission, fetchShoppingMissionStatus, type ShoppingMission, type SpinOutcome } from './lib/shoppingMissions';
import { recentlyViewedProductsStore } from './lib/recentlyViewed';
import { fetchShopDeals, fetchSurplusDeals, fetchNearbyAds, fetchMembershipDayStatus, type ProductSearchResult, type SurplusDealResult, type NearbyMerchantAd } from './lib/shopping';
import type { CommerceProduct } from './lib/commerce';
import { ProductImageThumb, ProductPriceBlock } from './ProductDisplay';

export function formatDealCountdown(endsAt: string): string {
  const msLeft = new Date(endsAt).getTime() - Date.now();
  if (msLeft <= 0) return 'Ending soon';
  const totalMinutes = Math.floor(msLeft / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}h ${minutes}m left` : `${minutes}m left`;
}

// Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping reference screenshot
// -- "23:24:20 Limited time offer") -- same real TimeDeal.endsAt formatDealCountdown
// above already reads, ticking to the second, same real data Android/iOS's identical
// formatTimeDealCountdownHms now use.
export function formatDealCountdownHms(endsAt: string, now: number): string {
  const msLeft = new Date(endsAt).getTime() - now;
  if (msLeft <= 0) return '00:00:00';
  const totalSeconds = Math.floor(msLeft / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map((n) => String(n).padStart(2, '0')).join(':');
}

export function LiveDealCountdown({ endsAt }: { endsAt: string }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  return <>{formatDealCountdownHms(endsAt, now)}</>;
}

// Real Toss Shopping banner carousel (2026-08-12, direct user screenshot) -- see
// backend TimeDealService.getBanners's own doc comment: every banner IS a real,
// currently-active Time Deal, never fabricated promotional content. Same feature
// Android's ShopScreen.kt already ports (docs/DESIGN_REFERENCES.md Section 53) --
// this was the one real gap found via a grep for the endpoint's call sites: shipped
// Android-only that pass, never actually ported to web. Horizontal scroll-snap (no
// external carousel library) with a real "current | total" page indicator, matching
// Android's HorizontalPager reference exactly.
// Real "Membership Day" cashback-multiplier banner parity gap, found 2026-09-04 --
// Android's ShopScreen.kt already shows this (a real 🎉 banner on days
// ShoppingCashbackService.kt applies a real multiplier), fetchMembershipDayStatus was
// defined here but never rendered anywhere on bank-mfe. Own component (not a
// BannerCarousel slide) since it's a single always-or-never banner driven by a boolean
// flag, not a rotating deal carousel.
export function MembershipDayBanner() {
  const [status, setStatus] = useState<{ isMembershipDay: boolean; multiplier: number } | null>(null);
  useEffect(() => { fetchMembershipDayStatus().then(setStatus).catch(() => {}); }, []);

  if (!status?.isMembershipDay) return null;
  return (
    <div style={{ marginBottom: '16px', padding: '14px 16px', borderRadius: '14px', background: 'color-mix(in srgb, var(--itunda-indigo) 12%, transparent)' }}>
      <p style={{ fontWeight: 700, fontSize: 'var(--itunda-type-scale-15-size)', color: 'var(--itunda-grey-900)' }}>
        🎉 Membership Day · {status.multiplier}x cashback today
      </p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
        Every purchase you make today earns {status.multiplier}x the usual cashback.
      </p>
    </div>
  );
}

export function BannerCarousel() {
  const [banners, setBanners] = useState<TimeDealView[]>([]);
  useEffect(() => { fetchShopBanners().then(setBanners).catch(() => {}); }, []);
  const [bannerIndex, setBannerIndex] = useState(0);
  const bannerScrollRef = useRef<HTMLDivElement>(null);

  if (banners.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px', position: 'relative' }}>
      <div
        ref={bannerScrollRef}
        onScroll={(e) => {
          const el = e.currentTarget;
          setBannerIndex(Math.round(el.scrollLeft / el.clientWidth));
        }}
        style={{ display: 'flex', overflowX: 'auto', scrollSnapType: 'x mandatory', borderRadius: '14px', gap: '0' }}
      >
        {banners.map((v) => {
          const discountPercent = v.deal.originalPrice > 0 ? Math.round(100 - (v.deal.dealPrice / v.deal.originalPrice) * 100) : 0;
          return (
            <div
              key={v.deal.id}
              style={{
                flex: '0 0 100%', scrollSnapAlign: 'start', height: '140px', background: 'color-mix(in srgb, var(--itunda-indigo) 12%, transparent)',
                display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', boxSizing: 'border-box',
              }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                {discountPercent > 0 && <span style={{ color: 'var(--itunda-red)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{discountPercent}% off</span>}
                <span style={{ fontWeight: 700, fontSize: 'var(--itunda-type-scale-18-size)', color: 'var(--itunda-grey-900)' }}>{v.productName}</span>
                <span style={{ fontSize: 'var(--itunda-type-scale-15-size)', color: 'var(--itunda-grey-900)' }}>{v.deal.dealPrice.toLocaleString()} RWF</span>
                <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{v.businessName}</span>
              </div>
              {v.productImageUrl && (
                <img src={v.productImageUrl} alt="" style={{ width: '96px', height: '96px', borderRadius: '12px', objectFit: 'cover' }} />
              )}
            </div>
          );
        })}
      </div>
      {banners.length > 1 && (
        <span style={{ position: 'absolute', right: '10px', bottom: '10px', background: 'rgba(0,0,0,0.5)', color: '#fff', fontSize: 'var(--itunda-type-scale-11-size)', padding: '3px 8px', borderRadius: '10px' }}>
          {bannerIndex + 1} | {banners.length}
        </span>
      )}
    </div>
  );
}

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row -- see
// backend ShoppingMissionService.kt's own doc comment. Every mission credits real
// RWF to the real account; itunda has never had a separate points currency.
export function MissionsRow() {
  const { t } = useI18n();
  const [missions, setMissions] = useState<ShoppingMission[]>([]);
  const [spinOutcomes, setSpinOutcomes] = useState<SpinOutcome[]>([]);
  const [missionBusyType, setMissionBusyType] = useState<string | null>(null);
  const [missionFeedback, setMissionFeedback] = useState<string | null>(null);
  const loadMissions = () => {
    fetchShoppingMissionStatus().then((r) => { setMissions(r.missions); setSpinOutcomes(r.spinOutcomes); }).catch(() => {});
  };
  useEffect(loadMissions, []);
  const handleCompleteMission = async (type: string) => {
    setMissionBusyType(type);
    try {
      const result = await completeShoppingMission(type);
      setMissionFeedback(`+${result.amountEarned.toLocaleString()} RWF`);
      loadMissions();
    } catch (err) {
      setMissionFeedback(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setMissionBusyType(null);
    }
  };

  if (missions.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>Get points and coupons</p>
      <div style={{ display: 'flex', gap: '18px', overflowX: 'auto' }}>
        {missions.map((m) => {
          const done = m.type === 'WELCOME_BONUS' ? m.claimedEver : m.completedToday;
          const rewardText = m.type === 'SPIN' && spinOutcomes.length > 0
            ? `+${Math.min(...spinOutcomes.map((s) => s.amount)).toLocaleString()}~${Math.max(...spinOutcomes.map((s) => s.amount)).toLocaleString()}`
            : `+${m.rewardAmount.toLocaleString()}`;
          return (
            <button
              key={m.type}
              onClick={() => handleCompleteMission(m.type)}
              disabled={done || missionBusyType !== null}
              style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '64px', flexShrink: 0, background: 'none', border: 'none', cursor: done ? 'default' : 'pointer' }}
            >
              <div
                style={{
                  width: '52px', height: '52px', borderRadius: '14px', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: done ? 'var(--itunda-grey-100)' : 'color-mix(in srgb, var(--itunda-indigo) 15%, transparent)',
                }}
              >
                {missionBusyType === m.type ? (
                  <span style={{ fontSize: 'var(--itunda-type-scale-18-size)', color: 'var(--itunda-grey-500)' }}>…</span>
                ) : (
                  <Zap size={20} color={done ? 'var(--itunda-grey-400)' : 'var(--itunda-indigo)'} />
                )}
              </div>
              <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', marginTop: '4px', color: done ? 'var(--itunda-grey-400)' : 'var(--itunda-grey-900)', textAlign: 'center' }}>{m.label}</span>
              {!done && <span style={{ fontSize: '10px', fontWeight: 600, color: 'var(--itunda-indigo)' }}>{rewardText}</span>}
            </button>
          );
        })}
      </div>
      {missionFeedback && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>{missionFeedback}</p>}
    </div>
  );
}

// Real Karrot 반경 타기팅-style nearby ads (item 148) -- silent, non-blocking: a
// customer who denies/lacks location just never sees this rail, same discipline
// NeighborhoodSetupPrompt's own opt-in geolocation already establishes elsewhere.
export function NearbyAdsRail({ onOpenMerchant }: { onOpenMerchant: (args: { merchantId: string; businessName: string; category: null; cashbackRate: string }) => void }) {
  const [nearbyAds, setNearbyAds] = useState<NearbyMerchantAd[]>([]);
  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (position) => {
        fetchNearbyAds(position.coords.latitude, position.coords.longitude).then(setNearbyAds).catch(() => {});
      },
      () => {},
    );
  }, []);

  if (nearbyAds.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>📍 Near you</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {nearbyAds.map((a) => (
          <button
            key={a.ad.id}
            onClick={() => onOpenMerchant({ merchantId: a.ad.merchantId, businessName: a.businessName, category: null, cashbackRate: '1%' })}
            className="itunda-card"
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '160px', flexShrink: 0, gap: '4px' }}
          >
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{a.ad.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{a.businessName}</p>
            {a.ad.description && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-700)' }}>{a.ad.description}</p>}
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-indigo)', fontWeight: 600 }}>{a.distanceKm.toFixed(1)} km away</p>
          </button>
        ))}
      </div>
    </div>
  );
}

// Real "recently viewed products" rail -- see lib/recentlyViewed.ts's own doc
// comment. Same "merchandising above the raw list, hidden once the user starts
// filtering" discipline the Deals rail below already establishes. Reopens the
// merchant (same shortcut the Deals/Time Deals rails use), not a possibly-stale
// cached product snapshot.
export function RecentlyViewedRail({
  selectedProduct, selectedMerchantName, onOpenMerchant,
}: {
  selectedProduct: CommerceProduct | null;
  selectedMerchantName: string;
  onOpenMerchant: (args: { merchantId: string; businessName: string; category: null; cashbackRate: string }) => void;
}) {
  const [recentlyViewedProducts, setRecentlyViewedProducts] = useState(recentlyViewedProductsStore.getAll());
  useEffect(() => {
    if (!selectedProduct) return;
    setRecentlyViewedProducts(
      recentlyViewedProductsStore.add({
        id: selectedProduct.id, merchantId: selectedProduct.merchantId, businessName: selectedMerchantName,
        name: selectedProduct.name, price: selectedProduct.price, imageUrl: selectedProduct.imageUrl, discountPercent: selectedProduct.discountPercent,
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProduct?.id]);

  if (recentlyViewedProducts.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>🕒 Recently viewed</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {recentlyViewedProducts.map((rv) => (
          <button
            key={rv.id}
            onClick={() => onOpenMerchant({ merchantId: rv.merchantId, businessName: rv.businessName, category: null, cashbackRate: '1%' })}
            className="itunda-card"
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
          >
            <ProductImageThumb imageUrl={rv.imageUrl} size={96} />
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{rv.name}</p>
            <ProductPriceBlock price={rv.price} originalPrice={null} discountPercent={rv.discountPercent} />
          </button>
        ))}
      </div>
    </div>
  );
}

// Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #8. Every entry is a real merchant-set discount, never a
// fabricated promo -- see backend MerchantProductRepository.findDeals's own doc
// comment. Only shown on the unfiltered landing state, same "merchandising above the
// raw list, hidden once the user starts filtering" discipline a real Coupang/Naver
// home surface follows.
export function DealsRail({ onOpenSearchResult }: { onOpenSearchResult: (r: ProductSearchResult) => void }) {
  const [deals, setDeals] = useState<ProductSearchResult[] | null>(null);
  useEffect(() => {
    fetchShopDeals().then(setDeals).catch(() => {});
  }, []);

  if (!deals || deals.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}><FlameGlyph size={17} /> Deals</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {deals.map((d) => (
          <button
            key={d.id}
            onClick={() => onOpenSearchResult(d)}
            className="itunda-card"
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
          >
            <ProductImageThumb imageUrl={d.imageUrl} size={96} />
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{d.name}</p>
            <ProductPriceBlock price={d.price} originalPrice={d.originalPrice} discountPercent={d.discountPercent} />
            {/* rating/reviewCount added 2026-08-25 -- real batched ProductReview
                data already on this row (see lib/shopping.ts's ProductSearchResult
                comment), same IconStar treatment the merchant browse card already
                uses -- no per-item fetch needed. */}
            {d.rating != null && d.reviewCount ? (
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', display: 'flex', alignItems: 'center', gap: '2px' }}>
                <IconStar size={11} color="#F5A623" fill="#F5A623" /> {d.rating.toFixed(1)} ({d.reviewCount})
              </p>
            ) : null}
            {d.isBestSeller && <ShopBestSellerBadge />}
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: d.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
              {d.stockQuantity === null || d.stockQuantity === undefined ? 'Available' : d.stockQuantity === 0 ? 'Out of stock' : `${d.stockQuantity} available`}
            </p>
          </button>
        ))}
      </div>
    </div>
  );
}

// Real 마감할인 (closing/surplus discount) rail (2026-08-15) -- a real,
// government-partnered food-waste-reduction feature that launched 2026-06-15
// (기후부/환경부 + Baemin/Yogiyo/Coupang Eats), sourced fresh, see
// lib/shopping.ts's own doc comment. Distinct from the always-on "🔥 Deals" rail
// above: only genuinely time-boxed, still-in-stock closing sales. Shows a real
// "closes at HH:mm" time, never a fabricated urgency banner.
export function SurplusDealsRail({ onOpenSearchResult }: { onOpenSearchResult: (r: ProductSearchResult) => void }) {
  const [surplusDeals, setSurplusDeals] = useState<SurplusDealResult[] | null>(null);
  useEffect(() => {
    fetchSurplusDeals().then(setSurplusDeals).catch(() => {});
  }, []);

  if (!surplusDeals || surplusDeals.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>⏳ Closing deals</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {surplusDeals.map((d) => (
          <button
            key={d.id}
            onClick={() => onOpenSearchResult(d)}
            className="itunda-card"
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
          >
            <ProductImageThumb imageUrl={d.imageUrl} size={96} />
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{d.name}</p>
            <ProductPriceBlock price={d.price} originalPrice={d.originalPrice} discountPercent={d.discountPercent} />
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)' }}>
              Closes {new Date(d.surplusExpiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{d.stockQuantity} left</p>
          </button>
        ))}
      </div>
    </div>
  );
}

// Real Coupang 타임특가 (Time Deal, item 226) -- see lib/timeDeal.ts's own doc
// comment. Distinct from the always-on "🔥 Deals" rail above: a time-boxed,
// quantity-capped event, not a permanent discount. Re-fetched every 30s so a deal
// that just sold out or expired stops showing without a manual refresh. Clicking a
// card opens that merchant's catalog, same simplification the Deals rail above
// already uses (not a deep-link straight to the specific product).
export function TimeDealsRail({ onOpenMerchant }: { onOpenMerchant: (args: { merchantId: string; businessName: string; category: null; cashbackRate: string }) => void }) {
  const [timeDeals, setTimeDeals] = useState<TimeDealView[] | null>(null);
  useEffect(() => {
    const load = () => fetchActiveTimeDeals().then(setTimeDeals).catch(() => {});
    load();
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, []);

  if (!timeDeals || timeDeals.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>⏰ Time Deals</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {timeDeals.map((v) => (
          <button
            key={v.deal.id}
            onClick={() => onOpenMerchant({ merchantId: v.deal.merchantId, businessName: v.businessName, category: null, cashbackRate: '1%' })}
            className="itunda-card"
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
          >
            <ProductImageThumb imageUrl={v.productImageUrl} size={96} />
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{v.productName}</p>
            <ProductPriceBlock
              price={v.deal.dealPrice} originalPrice={v.deal.originalPrice}
              discountPercent={Math.round((1 - v.deal.dealPrice / v.deal.originalPrice) * 100)}
            />
            {/* Real Coupang badge system (2026-08-05) -- see Badge.tsx's own doc
                comment. Matches Android ShopScreen.kt's own identical StatusBadge
                treatment (this was plain <p> text on bank-mfe until now). */}
            <Badge text={formatDealCountdown(v.deal.endsAt)} filled={false} tint="var(--itunda-indigo)" />
            {/* Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping
                reference screenshot -- "23:24:20 Limited time offer") -- same
                real v.deal.endsAt the rounded badge above already reads. */}
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', fontWeight: 700 }}>
              ⏰ <LiveDealCountdown endsAt={v.deal.endsAt} />
            </p>
            <Badge text={`${v.deal.remainingQuantity} left`} tint="var(--itunda-red)" />
          </button>
        ))}
      </div>
    </div>
  );
}
