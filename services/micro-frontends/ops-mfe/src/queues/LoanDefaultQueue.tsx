import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { decideVupLoanReview, fetchVupLoanReviewQueue, type VupLoanReview } from '../lib/queues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

function VupLoanReviewCard({ loan, onDecided }: { loan: VupLoanReview; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [writingOff, setWritingOff] = useState(false);
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);

  const acknowledge = async () => {
    setPending(true);
    setError(null);
    try {
      await decideVupLoanReview(loan.id, false, note || undefined);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not record this review.');
    } finally {
      setPending(false);
    }
  };

  const writeOff = async () => {
    setPending(true);
    setError(null);
    try {
      await decideVupLoanReview(loan.id, true, note || undefined);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not write off this loan.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>
          {loan.outstandingPrincipal.toLocaleString('en-US')} RWF outstanding
        </p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>User {loan.userId}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          Principal {loan.principalAmount.toLocaleString('en-US')} RWF at {(loan.interestRate * 100).toFixed(0)}%
          {loan.dueDate && <> · due {new Date(loan.dueDate).toLocaleDateString()}</>}
        </p>
      </div>

      {writingOff && (
        <textarea
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Note (optional)"
          maxLength={255}
          rows={2}
          style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
        />
      )}

      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}

      <div style={{ display: 'flex', gap: '8px' }}>
        {!writingOff ? (
          <>
            <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} disabled={pending} onClick={() => setWritingOff(true)}>
              Write off
            </button>
            <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={pending} onClick={acknowledge}>
              Acknowledge
            </button>
          </>
        ) : (
          <>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={pending} onClick={() => setWritingOff(false)}>
              Cancel
            </button>
            <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} disabled={pending} onClick={writeOff}>
              Confirm write-off
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export default function LoanDefaultQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchVupLoanReviewQueue);

  return (
    <div>
      <QueueHeader title="Loan default review" count={totalElements} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No overdue VUP loans pending review." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((loan) => (
            <VupLoanReviewCard key={loan.id} loan={loan} onDecided={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
