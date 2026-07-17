import { useEffect, useState } from 'react';
import { CircleDollarSign, LogOut, QrCode, Settings, ShoppingCart, Store, Users } from 'lucide-react';
import { getStoredUser, logout } from './lib/api';
import { getMyMerchant, type Merchant } from './lib/merchant';
import RegisterScreen from './RegisterScreen';
import CollectScreen from './screens/CollectScreen';
import PayrollScreen from './screens/PayrollScreen';
import PosScreen from './screens/PosScreen';
import ReportsScreen from './screens/ReportsScreen';
import SettingsScreen from './screens/SettingsScreen';
import { QueueError, QueueSkeleton } from './QueueState';

type Tab = 'collect' | 'pos' | 'reports' | 'payroll' | 'settings';

const TABS: { id: Tab; label: string; icon: typeof QrCode }[] = [
  { id: 'collect', label: 'Collect', icon: QrCode },
  { id: 'pos', label: 'POS', icon: ShoppingCart },
  { id: 'reports', label: 'Reports', icon: CircleDollarSign },
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
    <div style={{ display: 'flex', minHeight: '100svh' }}>
      <nav
        style={{
          width: '220px',
          flexShrink: 0,
          backgroundColor: 'var(--toss-white)',
          borderRight: '1px solid var(--toss-grey-200)',
          padding: '24px 16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '0 8px', marginBottom: '20px' }}>
          <Store size={20} color="var(--toss-blue)" />
          <h1 style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>Itunda Business</h1>
        </div>
        {TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              padding: '10px 12px',
              borderRadius: '10px',
              fontSize: '14px',
              fontWeight: 600,
              textAlign: 'left',
              color: tab === id ? 'var(--toss-blue)' : 'var(--toss-grey-700)',
              backgroundColor: tab === id ? 'var(--toss-blue-light)' : 'transparent',
            }}
          >
            <Icon size={18} />
            {label}
          </button>
        ))}

        <div style={{ marginTop: 'auto', paddingTop: '16px', borderTop: '1px solid var(--toss-grey-200)' }}>
          <p style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-900)', padding: '0 8px', marginBottom: '2px' }}>
            {merchant.businessName}
          </p>
          {user && (
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', padding: '0 8px', marginBottom: '8px' }}>
              {user.firstName} {user.lastName}
            </p>
          )}
          <button
            onClick={handleLogout}
            style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 12px', fontSize: '14px', fontWeight: 600, color: 'var(--toss-grey-500)' }}
          >
            <LogOut size={16} /> Sign out
          </button>
        </div>
      </nav>

      <main style={{ flex: 1, padding: '32px 40px', maxWidth: '960px' }}>
        {tab === 'collect' && <CollectScreen />}
        {tab === 'pos' && <PosScreen />}
        {tab === 'reports' && <ReportsScreen />}
        {tab === 'payroll' && <PayrollScreen />}
        {tab === 'settings' && <SettingsScreen merchant={merchant} onUpdated={setMerchant} />}
      </main>
    </div>
  );
}
