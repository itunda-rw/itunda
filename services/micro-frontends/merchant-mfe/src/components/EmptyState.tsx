import { Inbox } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

// Real shared empty/error-state components (2026-08-04) -- same rollout as
// bank-mfe's own src/EmptyState.tsx (see that file's doc comment for the full
// cross-platform account: Android/iOS/bank-mfe all had this same gap, closed
// one at a time this pass). merchant-mfe had the identical bare
// <p style={{ color: 'var(--toss-grey-500)' }}>No X yet.</p> pattern across its
// screens/ directory.
export function EmptyState({ message, icon: Icon = Inbox }: { message: string; icon?: LucideIcon }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', padding: '32px 0', textAlign: 'center' }}>
      <div
        style={{
          width: '56px',
          height: '56px',
          borderRadius: '50%',
          backgroundColor: 'var(--toss-grey-100)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <Icon size={24} color="var(--toss-grey-500)" />
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', margin: 0, maxWidth: '260px' }}>{message}</p>
    </div>
  );
}

export function ErrorCard({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="toss-card" style={{ padding: '20px', margin: '8px 0' }}>
      <p style={{ fontSize: '14px', color: 'var(--toss-red)', margin: 0 }}>{message}</p>
      <button
        type="button"
        onClick={onRetry}
        style={{ marginTop: '10px', background: 'none', border: 'none', padding: 0, fontWeight: 600, color: 'var(--toss-blue)', cursor: 'pointer' }}
      >
        Retry
      </button>
    </div>
  );
}
