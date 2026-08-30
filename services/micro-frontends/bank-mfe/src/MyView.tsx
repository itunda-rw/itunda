// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Naver-style "My" personal hub, the orchestrator for the 6 profile/vehicle/
// family/subscription/affiliate cards this same slice split into their own
// files. Rendered from 2 call sites (a Pay-tab quick link + the MY tab itself).
// Every lib import here is a fresh copy of a function ALSO still imported in
// BankDashboard.tsx for an unrelated product feature (Shop/Eats/Marketplace/
// Property orders and favorites) -- this screen just reads the same real counts,
// it doesn't own that data.

import { useEffect, useState } from 'react';
import { PinSetupCard } from './PinSetupCard';
import { NotificationsCard, ProfilePhotoCard, VerificationCard } from './MyProfileCards';
import { MyVehiclesCard } from './MyVehiclesCard';
import { FamilyLinkCard } from './FamilyLinkCard';
import { MyProductSubscriptionsCard } from './MyProductSubscriptionsCard';
import { AffiliateEarningsCard } from './AffiliateEarningsCard';
import { LegalDocumentsCard } from './LegalDocumentsCard';
import { fetchMyOrders, type CommerceOrder } from './lib/commerce';
import { fetchMyEatsOrders, fetchMyFavoriteRestaurants, type EatsOrder } from './lib/eats';
import { fetchMyFavoriteListings, fetchMyListings } from './lib/marketplace';
import { fetchMyFavoritePropertyListings, fetchMyPropertyListings } from './lib/realestate';
import { fetchMyFavoriteJobPosts, fetchMyJobPosts } from './lib/jobs';
import { fetchMiniAppCatalog, type PartnerMiniApp } from './lib/partners';

// Real Naver-style "My" personal hub (2026-07-22), at the user's direct request:
// "My should be like Naver style My since we have shopping and eats and other
// products where users need to easily get track of their orders, reservation,
// favorites." bank-mfe's own nav is a flat always-visible tab bar (not a mobile
// bottom-nav-plus-hamburger-menu), so the Android/iOS "split My from a KakaoPay-style
// 전체 menu" half of this redesign doesn't map here -- every tab is already directly
// reachable. This tab is purely additive: a real cross-product activity summary,
// reusing each product tab's own existing fetch functions rather than duplicating
// their per-product order/favorite views.
export function MyView() {
  const [shopOrders, setShopOrders] = useState<CommerceOrder[]>([]);
  const [eatsOrders, setEatsOrders] = useState<EatsOrder[]>([]);
  const [favoriteListingsCount, setFavoriteListingsCount] = useState(0);
  const [favoriteJobPostsCount, setFavoriteJobPostsCount] = useState(0);
  const [favoritePropertyListingsCount, setFavoritePropertyListingsCount] = useState(0);
  const [favoriteRestaurantsCount, setFavoriteRestaurantsCount] = useState(0);
  const [myListingsCount, setMyListingsCount] = useState(0);
  const [myJobPostsCount, setMyJobPostsCount] = useState(0);
  const [myPropertyListingsCount, setMyPropertyListingsCount] = useState(0);
  const [miniApps, setMiniApps] = useState<PartnerMiniApp[]>([]);

  useEffect(() => {
    // Each fetch independent and best-effort -- one product's API hiccup must never
    // blank the rest of this real personal-activity summary.
    fetchMyOrders().then(setShopOrders).catch(() => {});
    fetchMyEatsOrders().then(setEatsOrders).catch(() => {});
    fetchMyFavoriteListings().then((r) => setFavoriteListingsCount(r.length)).catch(() => {});
    fetchMyFavoriteJobPosts().then((r) => setFavoriteJobPostsCount(r.length)).catch(() => {});
    fetchMyFavoritePropertyListings().then((r) => setFavoritePropertyListingsCount(r.length)).catch(() => {});
    fetchMyFavoriteRestaurants().then((r) => setFavoriteRestaurantsCount(r.length)).catch(() => {});
    fetchMyListings().then((r) => setMyListingsCount(r.listings.length)).catch(() => {});
    fetchMyJobPosts().then((r) => setMyJobPostsCount(r.posts.length)).catch(() => {});
    fetchMyPropertyListings().then((r) => setMyPropertyListingsCount(r.listings.length)).catch(() => {});
    fetchMiniAppCatalog().then(setMiniApps).catch(() => {});
  }, []);

  const rowStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', padding: '8px 0', fontSize: 'var(--itunda-type-scale-13-size)' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <ProfilePhotoCard />
      <PinSetupCard />
      <VerificationCard />
      <NotificationsCard />
      {/* MyBookingsCard moved to itunda Place (2026-08-25) -- see maps-mfe's own
          MapsBooking.tsx doc comment. */}
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card wrapping --
          My orders/My favorites/My listings/Mini apps are real sections in this
          screen's own stack of widgets, now flat matching itunda-flat-section's
          border-bottom divider convention. */}
      {(shopOrders.length > 0 || eatsOrders.length > 0) && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My orders</h3>
          {shopOrders.slice(0, 3).map((order) => (
            <div key={order.id} style={rowStyle}>
              <span>Shop order · {order.status}</span>
              <span>{order.totalAmount.toLocaleString()} RWF</span>
            </div>
          ))}
          {eatsOrders.slice(0, 3).map((order) => (
            <div key={order.id} style={rowStyle}>
              <span>Eats order · {order.status}</span>
              <span>{order.totalAmount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My favorites</h3>
        <div style={rowStyle}><span>Marketplace wishlist</span><span>{favoriteListingsCount}</span></div>
        <div style={rowStyle}><span>Jobs wishlist</span><span>{favoriteJobPostsCount}</span></div>
        <div style={rowStyle}><span>Property wishlist</span><span>{favoritePropertyListingsCount}</span></div>
        <div style={rowStyle}><span>Restaurant favorites</span><span>{favoriteRestaurantsCount}</span></div>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My listings</h3>
        <div style={rowStyle}><span>Marketplace</span><span>{myListingsCount}</span></div>
        <div style={rowStyle}><span>Jobs posted</span><span>{myJobPostsCount}</span></div>
        <div style={rowStyle}><span>Property listed</span><span>{myPropertyListingsCount}</span></div>
      </div>
      <MyVehiclesCard />
      <FamilyLinkCard />
      <MyProductSubscriptionsCard />
      <AffiliateEarningsCard />
      <LegalDocumentsCard />
      {miniApps.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Mini apps</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            Third-party apps reviewed and approved to run inside itunda.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {miniApps.map((app) => (
              <div key={app.id} style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
                {app.iconUrl ? (
                  <img src={app.iconUrl} alt="" style={{ width: '36px', height: '36px', borderRadius: '8px', flexShrink: 0 }} />
                ) : (
                  <div style={{ width: '36px', height: '36px', borderRadius: '8px', backgroundColor: 'var(--itunda-grey-100)', flexShrink: 0 }} />
                )}
                <div>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{app.name}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{app.description}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
