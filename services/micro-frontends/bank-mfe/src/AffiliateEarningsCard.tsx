// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Coupang Partners-style affiliate earnings summary (own lib/affiliate.ts read-back
// exports, exactly one external call site -- `<AffiliateEarningsCard />` inside
// MyView). Link creation itself (`createAffiliateLink`) stays in BankDashboard.tsx
// -- a different, unrelated call site (ProductCatalogView's own inline "Share &
// earn" button).

import { useEffect, useState } from 'react';
import { fetchMyAffiliateCommissions, fetchMyAffiliateLinks, type AffiliateCommission, type AffiliateLink } from './lib/affiliate';

// Real 쿠팡파트너스 (Coupang Partners)-style affiliate earnings summary (item 229) --
// see lib/affiliate.ts's own doc comment. Link creation itself happens inline on each
// product card (ProductCatalogView's own "🔗 Share & earn" button); this card is
// purely the read-back: how many links exist, how many clicks, and real commissions
// earned so far.
export function AffiliateEarningsCard() {
  const [links, setLinks] = useState<AffiliateLink[] | null>(null);
  const [commissions, setCommissions] = useState<AffiliateCommission[] | null>(null);

  useEffect(() => {
    fetchMyAffiliateLinks().then(setLinks).catch(() => {});
    fetchMyAffiliateCommissions().then(setCommissions).catch(() => {});
  }, []);

  if (!links || links.length === 0) return null;

  const totalEarned = (commissions ?? []).reduce((sum, c) => sum + c.commissionAmount, 0);
  const totalClicks = links.reduce((sum, l) => sum + l.clickCount, 0);

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10),
  // matching Android's identical "Partner earnings" conversion (4230bba1).
  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Partner earnings</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Earn 3% on any purchase made through a product link you've shared.
      </p>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>Links shared</span>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{links.length}</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>Total clicks</span>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{totalClicks}</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>Total earned</span>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{totalEarned.toLocaleString()} RWF</span>
      </div>
    </div>
  );
}
