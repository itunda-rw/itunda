import { useEffect, useRef, useState } from 'react';
import { Briefcase, CalendarClock, CircleDollarSign, CreditCard, HandCoins, Images, LogOut, Megaphone, Newspaper, Package, QrCode, Settings, ShoppingCart, Star, Store, Tag, Users2, Utensils, UtensilsCrossed, Users } from 'lucide-react';
import { getStoredUser, logout } from './lib/api';
import { getMyMerchant, type Merchant } from './lib/merchant';
import type { MerchantWithPhotos } from './lib/merchantPhotos';
import RegisterScreen from './RegisterScreen';
import { useI18n } from './i18n/I18nContext';
import { LOCALES, type TranslationKey } from './i18n/translations';
import AdsScreen from './screens/AdsScreen';
import BillingScreen from './screens/BillingScreen';
import BookingScreen from './screens/BookingScreen';
import BusinessAccountScreen from './screens/BusinessAccountScreen';
import CollectScreen from './screens/CollectScreen';
import CommerceOrdersScreen from './screens/CommerceOrdersScreen';
import CouponsScreen from './screens/CouponsScreen';
import DineInScreen from './screens/DineInScreen';
import EatsOrdersScreen from './screens/EatsOrdersScreen';
import PayrollScreen from './screens/PayrollScreen';
import PhotoGalleryScreen from './screens/PhotoGalleryScreen';
import PosScreen from './screens/PosScreen';
import ReportsScreen from './screens/ReportsScreen';
import ReviewsScreen from './screens/ReviewsScreen';
import SettingsScreen from './screens/SettingsScreen';
import UpdatesScreen from './screens/UpdatesScreen';
import VendorCashAdvanceScreen from './screens/VendorCashAdvanceScreen';
import VisitorAnalyticsScreen from './screens/VisitorAnalyticsScreen';
import { QueueError, QueueSkeleton } from './QueueState';

type Tab = 'collect' | 'pos' | 'eats' | 'commerce' | 'dinein' | 'booking' | 'reports' | 'reviews' | 'updates' | 'photos' | 'billing' | 'coupons' | 'visitors' | 'ads' | 'business' | 'advance' | 'payroll' | 'settings';

// Real 2nd-localization-pass (2026-08-15): labelKey replaces a literal string so the
// nav renders in the merchant's own chosen language -- see i18n/translations.ts's own
// doc comment on this file's `tabs.*` keys.
const TABS: { id: Tab; labelKey: TranslationKey; icon: typeof QrCode }[] = [
  { id: 'collect', labelKey: 'tabs.collect', icon: QrCode },
  { id: 'pos', labelKey: 'tabs.pos', icon: ShoppingCart },
  // Real Coupang Eats/Baemin-style restaurant order queue (item 208) -- see
  // screens/EatsOrdersScreen.tsx's own doc comment. A merchant with no real
  // restaurant orders sees an honest empty state, not a hidden tab -- there's no
  // cheap way to know in advance whether a given merchant is a restaurant.
  { id: 'eats', labelKey: 'tabs.eatsOrders', icon: UtensilsCrossed },
  // Real Coupang-style Shop/Commerce order queue (Shop/Commerce product-completeness
  // pass) -- see screens/CommerceOrdersScreen.tsx's own doc comment. Same real
  // "honest empty state, not a hidden tab" reasoning as Eats orders above.
  { id: 'commerce', labelKey: 'tabs.commerceOrders', icon: Package },
  // Real 배민오더-style table/QR in-store ordering -- see lib/eats.ts's own
  // dineInTableQrPayload doc comment. Already real on Android/iOS MerchantApp since
  // 2026-07-25; found missing here via the same sweep that found Bookings below.
  { id: 'dinein', labelKey: 'tabs.dineIn', icon: Utensils },
  // Real local-business appointment booking, owner side -- see lib/booking.ts's own
  // doc comment. Already real on Android/iOS MerchantApp since 2026-07-25; found
  // missing here via a fresh backend-endpoint sweep.
  { id: 'booking', labelKey: 'tabs.bookings', icon: CalendarClock },
  { id: 'reports', labelKey: 'tabs.reports', icon: CircleDollarSign },
  { id: 'reviews', labelKey: 'tabs.reviews', icon: Star },
  // Real gap found live (uncalled-endpoint sweep, 2026-08-29) -- see
  // screens/UpdatesScreen.tsx's own doc comment: the backend feed this posts to has
  // been displayed live on Android/iOS's customer-facing Maps News tab since
  // 2026-08-28, with no merchant client anywhere able to post to it until now.
  { id: 'updates', labelKey: 'tabs.updates', icon: Newspaper },
  // Real gap found live (uncalled-endpoint sweep, 2026-08-29) -- see
  // screens/PhotoGalleryScreen.tsx's own doc comment: the gallery this posts to has
  // been displayed live on Android/iOS's customer-facing Maps Photos tab since
  // 2026-08-28, with no merchant client anywhere able to populate more than the one
  // existing cover photo (Settings' own StoreSettingsCard) until now.
  { id: 'photos', labelKey: 'tabs.photos', icon: Images },
  { id: 'billing', labelKey: 'tabs.billing', icon: CreditCard },
  { id: 'coupons', labelKey: 'tabs.coupons', icon: Tag },
  { id: 'visitors', labelKey: 'tabs.visitors', icon: Users2 },
  { id: 'ads', labelKey: 'tabs.ads', icon: Megaphone },
  { id: 'business', labelKey: 'tabs.business', icon: Briefcase },
  // Real Isoko Vendor Cash Advance (item 210) -- see lib/vendorCashAdvance.ts's own
  // doc comment. A merchant with no real 14-day settlement history yet sees an honest
  // "not eligible" state, not a hidden tab.
  { id: 'advance', labelKey: 'tabs.cashAdvance', icon: HandCoins },
  { id: 'payroll', labelKey: 'tabs.payroll', icon: Users },
  { id: 'settings', labelKey: 'tabs.settings', icon: Settings },
];

export default function MerchantDashboard({ onLogout }: { onLogout: () => void }) {
  const { t, locale, setLocale } = useI18n();
  const [merchant, setMerchant] = useState<Merchant | null | undefined>(undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('collect');
  const user = getStoredUser();
  const navRefs = useRef<Partial<Record<Tab, HTMLButtonElement | null>>>({});

  const load = () => {
    setLoadError(null);
    getMyMerchant()
      .then(setMerchant)
      .catch((err) => setLoadError(err instanceof Error ? err.message : t('dashboard.loadError')));
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
          backgroundColor: 'var(--itunda-surface-default)',
          borderRight: '1px solid var(--itunda-border-default)',
          padding: '24px 16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
        }}
      >
        <div className="merchant-nav-header" style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '0 8px', marginBottom: '20px' }}>
          <Store size={20} color="var(--itunda-brand)" />
          <h1 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-text-primary)' }}>Itunda Business</h1>
        </div>
        {/* Real language switcher (2026-08-15), same placement/pattern as LoginPage's
            own -- the nav shell is visible on every session regardless of tab, so this
            is reachable without signing out first. */}
        <select
          value={locale}
          onChange={(e) => setLocale(e.target.value as 'en' | 'rw' | 'fr')}
          aria-label="Language"
          style={{ fontSize: '12px', padding: '4px 6px', borderRadius: '6px', border: '1px solid var(--itunda-border-default)', color: 'var(--itunda-text-secondary)', background: 'var(--itunda-surface-default)', marginBottom: '16px' }}
        >
          {LOCALES.map((l) => (
            <option key={l.code} value={l.code}>{l.label}</option>
          ))}
        </select>
        <div className="merchant-nav-items" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          {TABS.map(({ id, labelKey, icon: Icon }, index) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              onKeyDown={(event) => {
                if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
                event.preventDefault();
                const nextIndex = event.key === 'ArrowDown'
                  ? (index + 1) % TABS.length
                  : (index - 1 + TABS.length) % TABS.length;
                const nextTab = TABS[nextIndex].id;
                setTab(nextTab);
                navRefs.current[nextTab]?.focus();
              }}
              ref={(node) => { navRefs.current[id] = node; }}
              aria-label={t(labelKey)}
              aria-current={tab === id ? 'page' : undefined}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '10px',
                padding: '10px 12px',
                borderRadius: 'var(--itunda-control-radius, 12px)',
                fontSize: '14px',
                fontWeight: 600,
                textAlign: 'left',
                color: tab === id ? 'var(--itunda-brand)' : 'var(--itunda-text-secondary)',
                backgroundColor: tab === id ? 'var(--itunda-surface-brand)' : 'transparent',
              }}
            >
              <Icon size={18} />
              <span>{t(labelKey)}</span>
            </button>
          ))}
        </div>

        <div className="merchant-nav-footer" style={{ marginTop: 'auto', paddingTop: '16px', borderTop: '1px solid var(--itunda-border-default)' }}>
          <div className="merchant-nav-footer-details">
            <p style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-text-primary)', padding: '0 8px', marginBottom: '2px' }}>
              {merchant.businessName}
            </p>
            {user && (
              <p style={{ fontSize: '12px', color: 'var(--itunda-text-tertiary)', padding: '0 8px', marginBottom: '8px' }}>
                {user.firstName} {user.lastName}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={handleLogout}
            aria-label={t('dashboard.signOut')}
            style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 12px', fontSize: '14px', fontWeight: 600, color: 'var(--itunda-text-tertiary)' }}
          >
            <LogOut size={16} /> <span>{t('dashboard.signOut')}</span>
          </button>
        </div>
      </nav>

      <main className="merchant-main" style={{ flex: 1, padding: '32px 40px', maxWidth: '960px' }}>
        {tab === 'collect' && <CollectScreen />}
        {tab === 'pos' && <PosScreen />}
        {tab === 'eats' && <EatsOrdersScreen />}
        {tab === 'commerce' && <CommerceOrdersScreen />}
        {tab === 'dinein' && <DineInScreen merchant={merchant} />}
        {tab === 'booking' && <BookingScreen />}
        {tab === 'reports' && <ReportsScreen />}
        {tab === 'reviews' && <ReviewsScreen merchant={merchant} />}
        {tab === 'updates' && <UpdatesScreen merchant={merchant} />}
        {tab === 'photos' && <PhotoGalleryScreen merchant={merchant as MerchantWithPhotos} onUpdated={setMerchant} />}
        {tab === 'billing' && <BillingScreen />}
        {tab === 'coupons' && <CouponsScreen />}
        {tab === 'visitors' && <VisitorAnalyticsScreen />}
        {tab === 'ads' && <AdsScreen />}
        {tab === 'business' && <BusinessAccountScreen />}
        {tab === 'advance' && <VendorCashAdvanceScreen merchant={merchant} />}
        {tab === 'payroll' && <PayrollScreen />}
        {tab === 'settings' && <SettingsScreen merchant={merchant} onUpdated={setMerchant} />}
      </main>
    </div>
  );
}
