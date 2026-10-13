import { AlertCircle, Inbox } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

// Real shared empty/error-state components (2026-08-04) -- same rollout as
// bank-mfe's own src/EmptyState.tsx (see that file's doc comment for the full
// cross-platform account: Android/iOS/bank-mfe all had this same gap, closed
// one at a time this pass). merchant-mfe had the identical bare
// <p style={{ color: 'var(--itunda-grey-500)' }}>No X yet.</p> pattern across its
// screens/ directory.
export function EmptyState({ message, icon: Icon = Inbox }: { message: string; icon?: LucideIcon }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', padding: '32px 0', textAlign: 'center' }}>
      <div
        style={{
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          backgroundColor: 'var(--itunda-grey-100)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon size={24} color="var(--itunda-grey-500)" />
      </div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', margin: 0, maxWidth: '260px' }}>{message}</p>
    </div>
  );
}

// Keep merchant recovery UI aligned with bank-mfe's shared IDS error card:
// one clear error icon, readable message, and a consistent primary Retry action.
export function ErrorCard({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', padding: '24px 20px', margin: '8px 0', textAlign: 'center' }}>
      <div
        aria-hidden="true"
        style={{
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          backgroundColor: 'color-mix(in srgb, var(--itunda-red) 12%, transparent)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <AlertCircle size={24} color="var(--itunda-red)" />
      </div>
      <p role="alert" style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', margin: 0, maxWidth: '260px' }}>{message}</p>
      <button type="button" onClick={onRetry} className="itunda-btn itunda-btn-primary" style={{ padding: '10px 24px' }}>
        Retry
      </button>
    </div>
  );
}
