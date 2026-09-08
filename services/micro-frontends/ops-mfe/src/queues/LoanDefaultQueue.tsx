import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import {
  decideHarvestAdvanceReview, decideVupLoanReview, fetchHarvestAdvanceReviewQueue, fetchVupLoanReviewQueue,
  type HarvestAdvanceReview, type VupLoanReview,
} from '../lib/loansQueues';
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

// Real overdue detection + admin review queue (Bank product-completeness pass,
// cycle 2, 2026-09-08) -- mirrors VupLoanReviewCard exactly. HarvestAdvance has
// no direct userId/farmer-name field (only membershipId), an honest v1 --
// see HarvestAdvanceReview's own doc comment in lib/queues.ts.
function HarvestAdvanceReviewCard({ advance, onDecided }: { advance: HarvestAdvanceReview; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [writingOff, setWritingOff] = useState(false);
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);

  const acknowledge = async () => {
    setPending(true);
    setError(null);
    try {
      await decideHarvestAdvanceReview(advance.id, false, note || undefined);
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
      await decideHarvestAdvanceReview(advance.id, true, note || undefined);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not write off this advance.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>
          {advance.principalAmount.toLocaleString('en-US')} RWF outstanding
        </p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>Cooperative membership {advance.membershipId}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          Due {new Date(advance.repaymentDueDate).toLocaleDateString()}
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
  const vup = usePagedQueue(fetchVupLoanReviewQueue);
  const harvest = usePagedQueue(fetchHarvestAdvanceReviewQueue);

  return (
    <div>
      <QueueHeader title="Loan default review" count={(vup.totalElements ?? 0) + (harvest.totalElements ?? 0)} onReload={() => { vup.reload(); harvest.reload(); }} refreshing={vup.refreshing || harvest.refreshing} />

      <h4 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-500)', margin: '4px 0 8px' }}>VUP loans</h4>
      {vup.error && <QueueError message={vup.error} onRetry={vup.reload} />}
      {!vup.error && vup.items === null && <QueueSkeleton />}
      {!vup.error && vup.items !== null && vup.items.length === 0 && <QueueEmpty label="No overdue VUP loans pending review." />}
      {!vup.error && vup.items !== null && vup.items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginBottom: '16px' }}>
          {vup.items.map((loan) => (
            <VupLoanReviewCard key={loan.id} loan={loan} onDecided={vup.reload} />
          ))}
          {vup.hasMore && <QueueLoadMore onLoadMore={vup.loadMore} loading={vup.loadingMore} />}
        </div>
      )}

      <h4 style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-500)', margin: '16px 0 8px' }}>Harvest advances</h4>
      {harvest.error && <QueueError message={harvest.error} onRetry={harvest.reload} />}
      {!harvest.error && harvest.items === null && <QueueSkeleton />}
      {!harvest.error && harvest.items !== null && harvest.items.length === 0 && <QueueEmpty label="No overdue harvest advances pending review." />}
      {!harvest.error && harvest.items !== null && harvest.items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {harvest.items.map((advance) => (
            <HarvestAdvanceReviewCard key={advance.id} advance={advance} onDecided={harvest.reload} />
          ))}
          {harvest.hasMore && <QueueLoadMore onLoadMore={harvest.loadMore} loading={harvest.loadingMore} />}
        </div>
      )}
    </div>
  );
}
