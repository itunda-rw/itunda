import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { fetchMarketplaceEscrowDisputes, resolveMarketplaceEscrowDispute, type MarketplaceEscrowDispute } from '../lib/queues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function confirmLabel(confirming: 'release' | 'refund'): string {
  return confirming === 'release' ? 'Confirm release' : 'Confirm refund';
}

function DisputeCard({ dispute, onResolved }: { dispute: MarketplaceEscrowDispute; onResolved: () => void }) {
  const [pending, setPending] = useState<'release' | 'refund' | null>(null);
  // Real fix (2026-09-05): this is real, irreversible money movement (the full escrow
  // amount to one party or the other) that shipped as a single click with zero
  // confirmation -- unlike every reject flow elsewhere in this same app (Compliance/
  // Partners/PropertyOwnership/InsuranceClaims), which already correctly gates a
  // destructive decision behind a real "are you sure" step. Matches that same
  // established pattern rather than inventing a new one.
  const [confirming, setConfirming] = useState<'release' | 'refund' | null>(null);
  const [error, setError] = useState<string | null>(null);

  const resolve = async (release: boolean) => {
    setPending(release ? 'release' : 'refund');
    setError(null);
    try {
      await resolveMarketplaceEscrowDispute(dispute.id, release);
      onResolved();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not resolve this dispute.');
      setConfirming(null);
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
      {confirming && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>
          {confirming === 'release'
            ? `Release ${dispute.amount.toLocaleString()} RWF to the seller?`
            : `Refund ${dispute.amount.toLocaleString()} RWF to the buyer?`}{' '}
          This can't be undone.
        </p>
      )}
      <div style={{ display: 'flex', gap: '8px' }}>
        {confirming === null ? (
          <>
            <button
              className="itunda-btn itunda-btn-secondary"
              style={{ flex: 1 }}
              disabled={pending !== null}
              onClick={() => setConfirming('refund')}
            >
              Refund buyer
            </button>
            <button
              className="itunda-btn itunda-btn-primary"
              style={{ flex: 1 }}
              disabled={pending !== null}
              onClick={() => setConfirming('release')}
            >
              Release to seller
            </button>
          </>
        ) : (
          <>
            <button
              className="itunda-btn itunda-btn-secondary"
              style={{ flex: 1 }}
              disabled={pending !== null}
              onClick={() => setConfirming(null)}
            >
              Cancel
            </button>
            <button
              className="itunda-btn itunda-btn-danger"
              style={{ flex: 1 }}
              disabled={pending !== null}
              onClick={() => resolve(confirming === 'release')}
            >
              {pending !== null ? '…' : confirmLabel(confirming)}
            </button>
          </>
        )}
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
