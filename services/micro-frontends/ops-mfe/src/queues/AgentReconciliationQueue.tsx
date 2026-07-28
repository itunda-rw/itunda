import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { ApiError } from '../lib/api';
import { fetchPendingTillReconciliations, resolveTillReconciliation, type AgentTillReconciliation } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

function ReconciliationCard({ reconciliation, onResolved }: { reconciliation: AgentTillReconciliation; onResolved: () => void }) {
  const [pending, setPending] = useState(false);
  const [note, setNote] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const short = reconciliation.variance < 0;

  const resolve = async () => {
    setPending(true);
    setActionError(null);
    try {
      await resolveTillReconciliation(reconciliation.id, note);
      onResolved();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Failed to resolve.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--toss-grey-900)' }}>Agent {reconciliation.agentId}</p>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            Business date {reconciliation.businessDate} · Submitted by {reconciliation.submittedByUserId}
          </p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: short ? '#E53935' : 'var(--toss-green)' }}>
          {short ? '' : '+'}{reconciliation.variance.toLocaleString()} RWF
        </span>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>
        Expected {reconciliation.expectedCash.toLocaleString()} RWF · Counted {reconciliation.countedCash.toLocaleString()} RWF
      </p>

      <textarea
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Review note (required, 1-255 characters)"
        rows={2}
        maxLength={255}
        style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />

      {actionError && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
          {actionError}
        </p>
      )}

      <button
        className="toss-btn toss-btn-primary"
        disabled={pending || note.trim().length === 0}
        onClick={resolve}
      >
        {pending ? '…' : 'Resolve'}
      </button>
    </div>
  );
}

export default function AgentReconciliationQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchPendingTillReconciliations);

  return (
    <div>
      <QueueHeader title="Agent till reconciliations" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No till variances awaiting review." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((reconciliation) => (
            <ReconciliationCard key={reconciliation.id} reconciliation={reconciliation} onResolved={reload} />
          ))}
        </div>
      )}
    </div>
  );
}
