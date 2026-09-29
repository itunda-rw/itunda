import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { fetchFeeWaiverCandidates, revokeFeeWaiver, type FeeWaiverCandidate } from '../lib/merchantAdminQueues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function FeeWaiverCard({ candidate, onRevoked }: { candidate: FeeWaiverCandidate; onRevoked: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const revoke = async () => {
    setPending(true);
    setError(null);
    try {
      await revokeFeeWaiver(candidate.merchantId);
      onRevoked();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not revoke this waiver.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px' }}>
      <div style={{ flex: 1 }}>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{candidate.businessName}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
          {candidate.recentVolume.toLocaleString('en-US')} RWF in the last 30 days — above the small-merchant threshold
        </p>
        {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      </div>
      <button className="itunda-btn itunda-btn-danger" style={{ padding: '10px 18px' }} disabled={pending} onClick={revoke}>
        Revoke waiver
      </button>
    </div>
  );
}

export default function FeeWaiverQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchFeeWaiverCandidates);

  return (
    <div>
      <QueueHeader title="Fee waiver revocation" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No merchants have outgrown their fee waiver." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((candidate) => (
            <FeeWaiverCard key={candidate.merchantId} candidate={candidate} onRevoked={reload} />
          ))}
        </div>
      )}
    </div>
  );
}
