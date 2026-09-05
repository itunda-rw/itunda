import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { decideInsuranceClaim, fetchInsuranceClaimsQueue, type InsuranceClaim } from '../lib/queues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function InsuranceClaimCard({ claim, onDecided }: { claim: InsuranceClaim; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const approve = async () => {
    setPending(true);
    setError(null);
    try {
      await decideInsuranceClaim(claim.id, true);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not approve this claim.');
    } finally {
      setPending(false);
    }
  };

  const reject = async () => {
    setPending(true);
    setError(null);
    try {
      await decideInsuranceClaim(claim.id, false, reason || undefined);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reject this claim.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>
          {claim.amount.toLocaleString()} RWF
        </p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
          User {claim.userId} · Policy {claim.policyId}
        </p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>{claim.description}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          Submitted {new Date(claim.submittedAt).toLocaleString()}
        </p>
      </div>

      {rejecting && (
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Reason (optional)"
          maxLength={255}
          rows={2}
          style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
        />
      )}

      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}

      <div style={{ display: 'flex', gap: '8px' }}>
        {!rejecting ? (
          <>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={pending} onClick={() => setRejecting(true)}>
              Reject
            </button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={pending} onClick={approve}>
              Approve
            </button>
          </>
        ) : (
          <>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={pending} onClick={() => setRejecting(false)}>
              Cancel
            </button>
            <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} disabled={pending} onClick={reject}>
              Confirm reject
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export default function InsuranceClaimsQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchInsuranceClaimsQueue);

  return (
    <div>
      <QueueHeader title="Insurance claims" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No pending insurance claims." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((claim) => (
            <InsuranceClaimCard key={claim.id} claim={claim} onDecided={reload} />
          ))}
        </div>
      )}
    </div>
  );
}
