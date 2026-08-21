import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { decidePropertyOwnership, fetchPropertyOwnershipQueue, type PropertyOwnershipSubmission } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

function PropertyOwnershipCard({ submission, onDecided }: { submission: PropertyOwnershipSubmission; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');

  const approve = async () => {
    setPending(true);
    try {
      await decidePropertyOwnership(submission.id, true);
      onDecided();
    } finally {
      setPending(false);
    }
  };

  const reject = async () => {
    setPending(true);
    try {
      await decidePropertyOwnership(submission.id, false, reason || undefined);
      onDecided();
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>Listing {submission.listingId}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>User {submission.userId}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          Submitted {new Date(submission.submittedAt).toLocaleString()}
        </p>
        <a href={submission.documentUrl} target="_blank" rel="noreferrer" style={{ fontSize: '13px', color: 'var(--itunda-indigo)' }}>
          View ownership document
        </a>
      </div>

      {rejecting && (
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Reason (optional)"
          rows={2}
          style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
        />
      )}

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

export default function PropertyOwnershipQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchPropertyOwnershipQueue);

  return (
    <div>
      <QueueHeader title="Property ownership verification" count={totalElements} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No pending ownership submissions." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((submission) => (
            <PropertyOwnershipCard key={submission.id} submission={submission} onDecided={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
