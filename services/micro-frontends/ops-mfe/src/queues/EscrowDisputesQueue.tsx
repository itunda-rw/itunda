import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { fetchMarketplaceEscrowDisputes, resolveMarketplaceEscrowDispute, type MarketplaceEscrowDispute } from '../lib/queues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function DisputeCard({ dispute, onResolved }: { dispute: MarketplaceEscrowDispute; onResolved: () => void }) {
  const [pending, setPending] = useState<'release' | 'refund' | null>(null);
  const [error, setError] = useState<string | null>(null);

  const resolve = async (release: boolean) => {
    setPending(release ? 'release' : 'refund');
    setError(null);
    try {
      await resolveMarketplaceEscrowDispute(dispute.id, release);
      onResolved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not resolve this dispute.');
    } finally {
      setPending(null);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>Listing {dispute.listingId}</p>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            Buyer {dispute.buyerId} · Seller {dispute.sellerId}
          </p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
          {dispute.amount.toLocaleString()} RWF
        </span>
      </div>
      {dispute.disputeReason && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)', background: 'var(--itunda-grey-100)', padding: '10px', borderRadius: '8px' }}>
          "{dispute.disputeReason}"
        </p>
      )}
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
        Disputed {new Date(dispute.updatedAt).toLocaleString()} · Escrow fee {dispute.fee.toLocaleString()} RWF
      </p>
      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ flex: 1 }}
          disabled={pending !== null}
          onClick={() => resolve(false)}
        >
          {pending === 'refund' ? '…' : 'Refund buyer'}
        </button>
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ flex: 1 }}
          disabled={pending !== null}
          onClick={() => resolve(true)}
        >
          {pending === 'release' ? '…' : 'Release to seller'}
        </button>
      </div>
    </div>
  );
}

export default function EscrowDisputesQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchMarketplaceEscrowDisputes);

  return (
    <div>
      <QueueHeader title="Marketplace escrow disputes" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No disputed escrows." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((dispute) => (
            <DisputeCard key={dispute.id} dispute={dispute} onResolved={reload} />
          ))}
        </div>
      )}
    </div>
  );
}
