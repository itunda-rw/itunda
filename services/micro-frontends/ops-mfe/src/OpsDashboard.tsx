import { useState } from 'react';
import { AlertTriangle, Banknote, CircleDollarSign, Flag, Gauge, HeartPulse, Home, Landmark, LifeBuoy, LogOut, MessageCircleWarning, Percent, Puzzle, Scale, ShieldCheck, Siren, Store, Users, Wrench, Webhook } from 'lucide-react';
import { getStoredUser, logout } from './lib/api';
import OverviewView from './queues/OverviewView';
import FraudQueue from './queues/FraudQueue';
import ComplianceQueue from './queues/ComplianceQueue';
import IncidentsQueue from './queues/IncidentsQueue';
import ReconciliationView from './queues/ReconciliationView';
import SupportQueue from './queues/SupportQueue';
import InsuranceClaimsQueue from './queues/InsuranceClaimsQueue';
import PartnersQueue from './queues/PartnersQueue';
import EscrowDisputesQueue from './queues/EscrowDisputesQueue';
import AgentReconciliationQueue from './queues/AgentReconciliationQueue';
import HoodReportsQueue from './queues/HoodReportsQueue';
import PropertyOwnershipQueue from './queues/PropertyOwnershipQueue';
import AgentsManagementView from './queues/AgentsManagementView';
import MerchantModerationQueue from './queues/MerchantModerationQueue';
import VehicleInspectionMechanicModerationQueue from './queues/VehicleInspectionMechanicModerationQueue';
import LoanDefaultQueue from './queues/LoanDefaultQueue';
import FeeWaiverQueue from './queues/FeeWaiverQueue';
import WebhookFailuresQueue from './queues/WebhookFailuresQueue';
import ChatReportsQueue from './queues/ChatReportsQueue';

type Tab =
  | 'overview' | 'fraud' | 'compliance' | 'incidents' | 'reconciliation' | 'support' | 'insurance' | 'partners'
  | 'escrow' | 'agents' | 'hood-reports' | 'property-verification' | 'agents-management' | 'merchants' | 'loan-default'
  | 'fee-waiver' | 'webhook-failures' | 'chat-reports' | 'vehicle-inspection-mechanics';

const TABS: { id: Tab; label: string; icon: typeof AlertTriangle }[] = [
  { id: 'overview', label: 'Overview', icon: Gauge },
  { id: 'fraud', label: 'Fraud', icon: AlertTriangle },
  { id: 'compliance', label: 'Compliance', icon: ShieldCheck },
  { id: 'incidents', label: 'Incidents', icon: Siren },
  { id: 'reconciliation', label: 'Reconciliation', icon: CircleDollarSign },
  { id: 'support', label: 'Support', icon: LifeBuoy },
  { id: 'insurance', label: 'Insurance claims', icon: HeartPulse },
  { id: 'partners', label: 'Partner mini-apps', icon: Puzzle },
  { id: 'escrow', label: 'Escrow disputes', icon: Scale },
  { id: 'agents-management', label: 'Agents', icon: Users },
  { id: 'agents', label: 'Agent till variances', icon: Banknote },
  { id: 'hood-reports', label: 'Hood content reports', icon: Flag },
  { id: 'property-verification', label: 'Property ownership', icon: Home },
  { id: 'merchants', label: 'Merchant moderation', icon: Store },
  { id: 'vehicle-inspection-mechanics', label: 'Vehicle inspection mechanics', icon: Wrench },
  { id: 'loan-default', label: 'Loan default review', icon: Landmark },
  { id: 'fee-waiver', label: 'Fee waiver revocation', icon: Percent },
  { id: 'webhook-failures', label: 'Webhook failures', icon: Webhook },
  { id: 'chat-reports', label: 'Talk message reports', icon: MessageCircleWarning },
];

export default function OpsDashboard({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('overview');
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
          backgroundColor: 'var(--itunda-white)',
          borderRight: '1px solid var(--itunda-grey-200)',
          padding: '24px 16px',
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
        }}
      >
        <h1 style={{ fontSize: '17px', fontWeight: 700, color: 'var(--itunda-grey-900)', padding: '0 8px', marginBottom: '20px' }}>
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
              color: tab === id ? 'var(--itunda-indigo)' : 'var(--itunda-grey-700)',
              backgroundColor: tab === id ? 'var(--itunda-indigo-light)' : 'transparent',
            }}
          >
            <Icon size={18} />
            {label}
          </button>
        ))}

        <div style={{ marginTop: 'auto', paddingTop: '16px', borderTop: '1px solid var(--itunda-grey-200)' }}>
          {user && (
            <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', padding: '0 8px', marginBottom: '8px' }}>
              {user.firstName} {user.lastName}
            </p>
          )}
          <button
            onClick={handleLogout}
            style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 12px', fontSize: '14px', fontWeight: 600, color: 'var(--itunda-grey-500)' }}
          >
            <LogOut size={16} /> Sign out
          </button>
        </div>
      </nav>

      <main style={{ flex: 1, padding: '32px 40px', maxWidth: '960px' }}>
        {tab === 'overview' && <OverviewView />}
        {tab === 'fraud' && <FraudQueue />}
        {tab === 'compliance' && <ComplianceQueue />}
        {tab === 'incidents' && <IncidentsQueue />}
        {tab === 'reconciliation' && <ReconciliationView />}
        {tab === 'support' && <SupportQueue />}
        {tab === 'insurance' && <InsuranceClaimsQueue />}
        {tab === 'partners' && <PartnersQueue />}
        {tab === 'escrow' && <EscrowDisputesQueue />}
        {tab === 'agents' && <AgentReconciliationQueue />}
        {tab === 'hood-reports' && <HoodReportsQueue />}
        {tab === 'property-verification' && <PropertyOwnershipQueue />}
        {tab === 'agents-management' && <AgentsManagementView />}
        {tab === 'merchants' && <MerchantModerationQueue />}
        {tab === 'vehicle-inspection-mechanics' && <VehicleInspectionMechanicModerationQueue />}
        {tab === 'loan-default' && <LoanDefaultQueue />}
        {tab === 'fee-waiver' && <FeeWaiverQueue />}
        {tab === 'webhook-failures' && <WebhookFailuresQueue />}
        {tab === 'chat-reports' && <ChatReportsQueue />}
      </main>
    </div>
  );
}
