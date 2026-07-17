import { useState } from 'react';
import { AlertTriangle, CircleDollarSign, HeartPulse, LifeBuoy, LogOut, Puzzle, ShieldCheck, Siren } from 'lucide-react';
import { getStoredUser, logout } from './lib/api';
import FraudQueue from './queues/FraudQueue';
import ComplianceQueue from './queues/ComplianceQueue';
import IncidentsQueue from './queues/IncidentsQueue';
import ReconciliationView from './queues/ReconciliationView';
import SupportQueue from './queues/SupportQueue';
import InsuranceClaimsQueue from './queues/InsuranceClaimsQueue';
import PartnersQueue from './queues/PartnersQueue';

type Tab = 'fraud' | 'compliance' | 'incidents' | 'reconciliation' | 'support' | 'insurance' | 'partners';

const TABS: { id: Tab; label: string; icon: typeof AlertTriangle }[] = [
  { id: 'fraud', label: 'Fraud', icon: AlertTriangle },
  { id: 'compliance', label: 'Compliance', icon: ShieldCheck },
  { id: 'incidents', label: 'Incidents', icon: Siren },
  { id: 'reconciliation', label: 'Reconciliation', icon: CircleDollarSign },
  { id: 'support', label: 'Support', icon: LifeBuoy },
  { id: 'insurance', label: 'Insurance claims', icon: HeartPulse },
  { id: 'partners', label: 'Partner mini-apps', icon: Puzzle },
];

export default function OpsDashboard({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('fraud');
  const user = getStoredUser();

  const handleLogout = () => {
    logout();
    onLogout();
  };

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
        <h1 style={{ fontSize: '17px', fontWeight: 700, color: 'var(--toss-grey-900)', padding: '0 8px', marginBottom: '20px' }}>
          Itunda Ops
        </h1>
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
          {user && (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', padding: '0 8px', marginBottom: '8px' }}>
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
        {tab === 'fraud' && <FraudQueue />}
        {tab === 'compliance' && <ComplianceQueue />}
        {tab === 'incidents' && <IncidentsQueue />}
        {tab === 'reconciliation' && <ReconciliationView />}
        {tab === 'support' && <SupportQueue />}
        {tab === 'insurance' && <InsuranceClaimsQueue />}
        {tab === 'partners' && <PartnersQueue />}
      </main>
    </div>
  );
}
