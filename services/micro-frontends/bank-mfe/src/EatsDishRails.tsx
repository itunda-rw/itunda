// Real dish-discovery rails for itunda Eats' real browse home feed (itunda Eats
// redesign, 2026-08-28, direct user reference: real Coupang Eats screenshots).
// Extracted into their own file per this session's own established file-size-lint
// discipline (BankDashboard.tsx stays under its frozen baseline). Both rails wire
// up real, already-built backend capability that had zero client anywhere before
// this pass -- see lib/eats.ts's own fetchRecommendedDishes doc comment.

import { useEffect, useState } from 'react';
import type { RecommendedDish } from './lib/eats';
import { fetchRecommendedDishes } from './lib/eats';
import type { NearbyMerchantAd, ShoppingMerchant } from './lib/shopping';
import { fetchNearbyAds } from './lib/shopping';

function DishRail({ title, dishes, onOpenRestaurant }: { title: string; dishes: RecommendedDish[]; onOpenRestaurant: (merchant: ShoppingMerchant) => void }) {
  if (dishes.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>{title}</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {dishes.map((d) => (
          <button
            key={d.id}
            onClick={() => onOpenRestaurant({ merchantId: d.merchantId, businessName: d.merchantName, category: null, cashbackRate: '1%' })}
            className="itunda-card"
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
          >
            <div style={{ width: '100px', height: '100px', borderRadius: 'var(--itunda-radius-md)', overflow: 'hidden', backgroundColor: 'var(--itunda-grey-100)' }}>
              {d.imageUrl && <img src={d.imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} onError={(e) => { e.currentTarget.style.display = 'none'; }} />}
            </div>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{d.name}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{d.merchantName}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{d.price.toLocaleString()} RWF</p>
          </button>
        ))}
      </div>
    </div>
  );
}

// Real "Recommended for you" rail -- see fetchRecommendedDishes' own doc comment.
// A genuine personal-history signal (has the buyer actually ordered from this
// dish's restaurant before), never a fabricated ranking.
export function RecommendedDishesRail({ onOpenRestaurant }: { onOpenRestaurant: (merchant: ShoppingMerchant) => void }) {
  const [dishes, setDishes] = useState<RecommendedDish[]>([]);
  useEffect(() => { fetchRecommendedDishes().then(setDishes).catch(() => {}); }, []);
  return <DishRail title="🍽 Recommended for you" dishes={dishes} onOpenRestaurant={onOpenRestaurant} />;
}

// Real "Popular now" rail -- adapts the reference's own "우리 동네 인기 메뉴"
// ranked rail onto a real signal itunda actually has (see backend
// EatsDishRecommendationService.getDishes' own doc comment): a genuine, order-
// count-derived popularity ranking, platform-wide rather than fabricating a
// neighborhood-radius filter this exact query can't honestly support yet.
export function PopularDishesRail({ onOpenRestaurant }: { onOpenRestaurant: (merchant: ShoppingMerchant) => void }) {
  const [dishes, setDishes] = useState<RecommendedDish[]>([]);
  useEffect(() => { fetchRecommendedDishes('popular').then(setDishes).catch(() => {}); }, []);
  return <DishRail title="🔥 Popular now" dishes={dishes} onOpenRestaurant={onOpenRestaurant} />;
}

// Real Karrot 반경 타기팅-style nearby ads rail for Eats -- see lib/shopping.ts's
// own doc comment. Same real, already-proven-on-Shop MerchantAd feature, just
// never wired to Eats before this pass. Silent on location denial, same pattern
// Shop's own nearby-ads rail already establishes.
export function EatsNearbyAdsRail({ onOpenRestaurant }: { onOpenRestaurant: (merchant: ShoppingMerchant) => void }) {
  const [ads, setAds] = useState<NearbyMerchantAd[]>([]);
  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (position) => { fetchNearbyAds(position.coords.latitude, position.coords.longitude).then(setAds).catch(() => {}); },
      () => {},
    );
  }, []);
  if (ads.length === 0) return null;
  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>📍 Near you</p>
      <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
        {ads.map((a) => (
          <button
            key={a.ad.id}
            onClick={() => onOpenRestaurant({ merchantId: a.ad.merchantId, businessName: a.businessName, category: null, cashbackRate: '1%' })}
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
