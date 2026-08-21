import { ScanFace } from 'lucide-react';
import type { RewardTasksResult } from './lib/rewards';

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
// "our pay home screen should also look 100% like toss pay home screen") -- split
// out of PayHub (BankDashboard.tsx) to keep that file under its file-size-lint
// baseline, matching the same convention PayMoneyDetail.tsx already established for
// this file. FacePayStatusRow replaces the reference's "Facepay Earning 3%" promo
// card -- itunda's real FacePayEnrollment has no per-transaction cashback-rate
// field, so this states the real enrolled/not-enrolled fact rather than inventing a
// percentage. RewardsPreviewSection replaces the reference's "Get more rewards" list
// (Toss Prime / Google gift codes / etc, none of which itunda has a real backend
// for) with itunda's own already-real RewardsService task list, never surfaced on
// Pay before -- a real preview capped at 3 unclaimed+eligible tasks, with "View all"
// opening the full Rewards destination.

export function FacePayStatusRow({ enrolled, busy, onToggle }: { enrolled: boolean; busy: boolean; onToggle: () => void }) {
  const toggleLabel = enrolled ? 'Turn off' : 'Enroll';
  return (
    <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div style={{ width: '38px', height: '38px', borderRadius: '999px', backgroundColor: enrolled ? 'var(--itunda-blue)' : 'var(--itunda-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <ScanFace size={19} color={enrolled ? '#fff' : 'var(--itunda-grey-500)'} />
        </div>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>FacePay</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{enrolled ? 'Enrolled — pay with your face at any itunda merchant' : 'Not enrolled'}</p>
        </div>
      </div>
      <button className={enrolled ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'} disabled={busy} onClick={onToggle} style={{ padding: '8px 14px', fontSize: 'var(--itunda-type-scale-12-size)', flexShrink: 0 }}>
        {busy ? '…' : toggleLabel}
      </button>
    </div>
  );
}

export function RewardsPreviewSection({ tasks, onViewAll }: { tasks: RewardTasksResult; onViewAll: () => void }) {
  const preview = tasks.tasks.filter((t) => !t.claimed && t.eligible).slice(0, 3);
  if (preview.length === 0) return null;
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '4px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, margin: 0 }}>Get more rewards</h3>
        <button onClick={onViewAll} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>View all</button>
      </div>
      {preview.map((t) => (
        <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{t.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t.subtitle}</p>
          </div>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-blue)' }}>+{t.rewardAmount} RWF</span>
        </div>
      ))}
    </div>
  );
}
