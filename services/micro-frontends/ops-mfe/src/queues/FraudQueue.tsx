import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { decideFraud, fetchFraudQueue, type FraudFlag } from '../lib/queues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

const RULE_LABEL: Record<FraudFlag['rule'], string> = {
  HIGH_VALUE: 'High value',
  VELOCITY: 'Velocity',
  NEW_RECIPIENT: 'New recipient',
};

function FraudCard({ flag, onDecided }: { flag: FraudFlag; onDecided: (id: string) => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const decide = async (decision: 'CLEARED' | 'CONFIRMED') => {
    setPending(true);
    setError(null);
    try {
      await decideFraud(flag.id, decision);
      onDecided(flag.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not decide this flag.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span
            style={{
              display: 'inline-block',
              fontSize: '12px',
              fontWeight: 700,
              color: 'var(--itunda-indigo)',
              backgroundColor: 'var(--itunda-indigo-light)',
              padding: '2px 8px',
              borderRadius: '6px',
              marginBottom: '6px',
            }}
          >
            {RULE_LABEL[flag.rule]}
          </span>
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{flag.description}</p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
          {flag.amount.toLocaleString()} RWF
        </span>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
        User {flag.userId} · Transaction {flag.transactionId} · Flagged {new Date(flag.createdAt).toLocaleString()}
      </p>
      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ flex: 1 }}
          disabled={pending}
          onClick={() => decide('CLEARED')}
        >
          Clear
        </button>
        <button
          className="itunda-btn itunda-btn-danger"
          style={{ flex: 1 }}
          disabled={pending}
          onClick={() => decide('CONFIRMED')}
        >
          Confirm fraud
        </button>
      </div>
    </div>
  );
}

export default function FraudQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchFraudQueue);

  const handleDecided = (id: string) => {
    reload();
    void id;
  };

  return (
    <div>
      <QueueHeader title="Fraud review" count={totalElements} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No unreviewed fraud flags." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((flag) => (
            <FraudCard key={flag.id} flag={flag} onDecided={handleDecided} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
