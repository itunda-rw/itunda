import { AlertCircle, Inbox } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

// Real shared empty/error-state components (2026-08-04) -- closes
// docs/DESIGN_REFERENCES.md Section 9's #1 recommendation on bank-mfe, which had
// no shared component at all: every "No X yet." was its own bare
// <p style={{ color: 'var(--itunda-grey-500)' }}>...</p>. Android already built the
// equivalent (EmptyState/ErrorCard in core/designsystem) and iOS just got its own
// (EmptyStateView/ErrorCardView in Core/DesignSystem) -- same icon-in-soft-circle
// + centered message shape here, using lucide-react (already a dependency, used
// elsewhere in this app) in place of Android's Material icons / iOS's SF Symbols.
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

// Real fix, found live 2026-08-05: EmptyState above got the real icon-in-circle
// treatment, but its own sibling ErrorCard -- shown right next to it in the exact
// same load-failure branches across the app -- stayed plain red text + a bare
// "Retry" text link, the identical gap Android's ErrorCard / iOS's ErrorCardView
// (same date) closed. Mirrors EmptyState's centered icon-circle layout exactly.
// tokens.css has no dedicated red-tint variable (only --itunda-blue-light exists for
// the blue role) -- rather than expand the shared token package for one call site,
// this computes the tint locally via color-mix, matching --itunda-blue-light's own
// real value (~4% blue over white) at the same ratio against --itunda-red.
export function ErrorCard({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', padding: '24px 20px', margin: '8px 0', textAlign: 'center' }}>
      <div
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
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', margin: 0, maxWidth: '260px' }}>{message}</p>
      <button type="button" onClick={onRetry} className="itunda-btn itunda-btn-primary" style={{ padding: '10px 24px' }}>
        Retry
      </button>
    </div>
  );
}
