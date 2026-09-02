// Extracted from BankDashboard.tsx (2026-09-02, itunda-vs-Toss architecture
// comparison thread -- the Rides slice of the "Eats/Rides/Shop mega-region needs a
// real per-domain split" plan named in this thread's own memory). Mirrors the
// already-proven MarketplaceView/CommunityView/JobsView/PropertyView split
// (HoodMarketplace.tsx etc.): this file (plus RidePassengerView.tsx/
// RideDriverView.tsx, split out further to stay under the 500-line new-file cap --
// RIDE and DRIVE are two fully independent state machines with zero shared state)
// owns everything exclusive to Rides (confirmed via a real usage-count check --
// every lib/rideshare import had zero other callers in BankDashboard.tsx before
// this move, not assumed from adjacency), while genuinely cross-domain shared
// helpers (AddressAutocomplete/PlaceSearchInput/ShareFavoritesModal, used by
// Eats/Shop too) stay in BankDashboard.tsx, exported, and are imported back the
// same way HoodMarketplace.tsx imports NeighborhoodSetupPrompt/
// NeighborhoodSwitcherRow from BankDashboard.
import { useState } from 'react';
import { RidePassengerView } from './RidePassengerView';
import { RideDriverView } from './RideDriverView';

export function RidesView({ onReportIssue }: { onReportIssue: (transactionId: string) => void }) {
  const [subTab, setSubTab] = useState<'RIDE' | 'DRIVE'>('RIDE');

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['RIDE', 'DRIVE'] as const).map((v) => (
          <button
            key={v} onClick={() => setSubTab(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: subTab === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: subTab === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'RIDE' ? 'Get a ride' : 'Drive'}
          </button>
        ))}
      </div>

      {subTab === 'RIDE' ? <RidePassengerView onReportIssue={onReportIssue} /> : <RideDriverView />}
    </div>
  );
}
