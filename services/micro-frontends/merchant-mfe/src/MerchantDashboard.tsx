import { useEffect, useState } from 'react';
import { Briefcase, CalendarClock, CircleDollarSign, CreditCard, HandCoins, LogOut, Megaphone, QrCode, Settings, ShoppingCart, Star, Store, Tag, Utensils, UtensilsCrossed, Users } from 'lucide-react';
import { getStoredUser, logout } from './lib/api';
import { getMyMerchant, type Merchant } from './lib/merchant';
import RegisterScreen from './RegisterScreen';
import AdsScreen from './screens/AdsScreen';
import BillingScreen from './screens/BillingScreen';
import BookingScreen from './screens/BookingScreen';
import BusinessAccountScreen from './screens/BusinessAccountScreen';
import CollectScreen from './screens/CollectScreen';
import CouponsScreen from './screens/CouponsScreen';
import DineInScreen from './screens/DineInScreen';
import EatsOrdersScreen from './screens/EatsOrdersScreen';
import PayrollScreen from './screens/PayrollScreen';
import PosScreen from './screens/PosScreen';
import ReportsScreen from './screens/ReportsScreen';
import ReviewsScreen from './screens/ReviewsScreen';
import SettingsScreen from './screens/SettingsScreen';
import VendorCashAdvanceScreen from './screens/VendorCashAdvanceScreen';
import { QueueError, QueueSkeleton } from './QueueState';

type Tab = 'collect' | 'pos' | 'eats' | 'dinein' | 'booking' | 'reports' | 'reviews' | 'billing' | 'coupons' | 'ads' | 'business' | 'advance' | 'payroll' | 'settings';

const TABS: { id: Tab; label: string; icon: typeof QrCode }[] = [
  { id: 'collect', label: 'Collect', icon: QrCode },
  { id: 'pos', label: 'POS', icon: ShoppingCart },
  // Real Coupang Eats/Baemin-style restaurant order queue (item 208) -- see
  // screens/EatsOrdersScreen.tsx's own doc comment. A merchant with no real
  // restaurant orders sees an honest empty state, not a hidden tab -- there's no
  // cheap way to know in advance whether a given merchant is a restaurant.
  { id: 'eats', label: 'Eats orders', icon: UtensilsCrossed },
  // Real 배민오더-style table/QR in-store ordering -- see lib/eats.ts's own
  // dineInTableQrPayload doc comment. Already real on Android/iOS MerchantApp since
  // 2026-07-25; found missing here via the same sweep that found Bookings below.
  { id: 'dinein', label: 'Dine-in', icon: Utensils },
  // Real local-business appointment booking, owner side -- see lib/booking.ts's own
  // doc comment. Already real on Android/iOS MerchantApp since 2026-07-25; found
  // missing here via a fresh backend-endpoint sweep.
  { id: 'booking', label: 'Bookings', icon: CalendarClock },
  { id: 'reports', label: 'Reports', icon: CircleDollarSign },
  { id: 'reviews', label: 'Reviews', icon: Star },
  { id: 'billing', label: 'Billing', icon: CreditCard },
  { id: 'coupons', label: 'Coupons', icon: Tag },
  { id: 'ads', label: 'Ads', icon: Megaphone },
  { id: 'business', label: 'Business', icon: Briefcase },
  // Real Isoko Vendor Cash Advance (item 210) -- see lib/vendorCashAdvance.ts's own
  // doc comment. A merchant with no real 14-day settlement history yet sees an honest
  // "not eligible" state, not a hidden tab.
  { id: 'advance', label: 'Cash advance', icon: HandCoins },
  { id: 'payroll', label: 'Payroll', icon: Users },
  { id: 'settings', label: 'Settings', icon: Settings },
];

export default function MerchantDashboard({ onLogout }: { onLogout: () => void }) {
  const [merchant, setMerchant] = useState<Merchant | null | undefined>(undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('collect');
  const user = getStoredUser();

  const load = () => {
    setLoadError(null);
    getMyMerchant()
      .then(setMerchant)
      .catch((err) => setLoadError(err instanceof Error ? err.message : 'Could not load your business account.'));
  };

  useEffect(load, []);

  const handleLogout = () => {
    logout();
    onLogout();
  };

  if (loadError) {
    return (
      <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ width: '400px' }}>
          <QueueError message={loadError} onRetry={load} />
        </div>
      </div>
    );
  }

  if (merchant === undefined) {
    return (
      <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ width: '400px' }}>
          <QueueSkeleton />
        </div>
      </div>
    );
  }

  if (merchant === null) {
    return <RegisterScreen onRegistered={setMerchant} />;
  }

  return (
    <div className="merchant-shell" style={{ display: 'flex', minHeight: '100svh' }}>
      <nav
        className="merchant-nav"
        style={{
          width: '220px',
          flexShrink: 0,
          backgroundColor: 'var(--itunda-white)',
          borderRight: '1px solid var(--itunda-grey-200)',
          padding: '24px 16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
        }}
      >
        <div className="merchant-nav-header" style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '0 8px', marginBottom: '20px' }}>
          <Store size={20} color="var(--itunda-blue)" />
          <h1 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Itunda Business</h1>
        </div>
        <div className="merchant-nav-items" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {TABS.map(({ id, label, icon: Icon }) => (
            <button
              key={id}
              onClick={() => setTab(id)}
              aria-label={label}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                padding: '10px 12px',
                borderRadius: '10px',
                fontSize: '14px',
                fontWeight: 600,
                textAlign: 'left',
                color: tab === id ? 'var(--itunda-blue)' : 'var(--itunda-grey-700)',
                backgroundColor: tab === id ? 'var(--itunda-blue-light)' : 'transparent',
              }}
            >
              <Icon size={18} />
              <span>{label}</span>
            </button>
          ))}
        </div>

        <div className="merchant-nav-footer" style={{ marginTop: 'auto', paddingTop: '16px', borderTop: '1px solid var(--itunda-grey-200)' }}>
          <div className="merchant-nav-footer-details">
            <p style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-900)', padding: '0 8px', marginBottom: '2px' }}>
              {merchant.businessName}
            </p>
            {user && (
              <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', padding: '0 8px', marginBottom: '8px' }}>
                {user.firstName} {user.lastName}
              </p>
            )}
          </div>
          <button
            onClick={handleLogout}
            aria-label="Sign out"
            style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 12px', fontSize: '14px', fontWeight: 600, color: 'var(--itunda-grey-500)' }}
          >
            <LogOut size={16} /> <span>Sign out</span>
          </button>
        </div>
      </nav>

      <main className="merchant-main" style={{ flex: 1, padding: '32px 40px', maxWidth: '960px' }}>
        {tab === 'collect' && <CollectScreen />}
        {tab === 'pos' && <PosScreen />}
        {tab === 'eats' && <EatsOrdersScreen />}
        {tab === 'dinein' && <DineInScreen merchant={merchant} />}
        {tab === 'booking' && <BookingScreen />}
        {tab === 'reports' && <ReportsScreen />}
        {tab === 'reviews' && <ReviewsScreen merchant={merchant} />}
        {tab === 'billing' && <BillingScreen />}
        {tab === 'coupons' && <CouponsScreen />}
        {tab === 'ads' && <AdsScreen />}
        {tab === 'business' && <BusinessAccountScreen />}
        {tab === 'advance' && <VendorCashAdvanceScreen merchant={merchant} />}
        {tab === 'payroll' && <PayrollScreen />}
        {tab === 'settings' && <SettingsScreen merchant={merchant} onUpdated={setMerchant} />}
      </main>
    </div>
  );
}
