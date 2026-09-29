import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { fetchPartnerAccounts, reactivatePartnerAccount, suspendPartnerAccount, type PartnerAccount } from '../lib/queues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

// Real admin moderation lever for partner accounts (Partners product-completeness
// pass, 2026-09-07) -- separate from PartnersQueue.tsx's own mini-app review queue:
// this moderates the partner developer account itself (registration/API-key holder),
// not an individual mini-app submission.
function PartnerAccountCard({ partner, onDecided }: { partner: PartnerAccount; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = async () => {
    setPending(true);
    setError(null);
    try {
      if (partner.status === 'SUSPENDED') {
        await reactivatePartnerAccount(partner.partnerId);
      } else {
        await suspendPartnerAccount(partner.partnerId);
      }
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this partner.');
    } finally {
      setPending(false);
    }
  };

  const buttonLabel = (): string => {
    if (pending) return '…';
    return partner.status === 'SUSPENDED' ? 'Reactivate' : 'Suspend';
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px' }}>
      <div style={{ flex: 1 }}>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{partner.companyName}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{partner.contactEmail}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>Registered {new Date(partner.createdAt).toLocaleString()}</p>
        {partner.status === 'SUSPENDED' && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }}>Suspended — locked out of every partner-facing API.</p>}
        {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      </div>
      <button
        className={partner.status === 'SUSPENDED' ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-danger'}
        style={{ padding: '10px 18px' }}
        disabled={pending}
        onClick={toggle}
      >
        {buttonLabel()}
      </button>
    </div>
  );
}

export default function PartnerAccountsQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchPartnerAccounts);

  return (
    <div>
      <QueueHeader title="Partner accounts" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No partners have registered yet." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((partner) => (
            <PartnerAccountCard key={partner.partnerId} partner={partner} onDecided={reload} />
          ))}
        </div>
      )}
    </div>
  );
}
