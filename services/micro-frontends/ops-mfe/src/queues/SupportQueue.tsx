import { useState } from 'react';
import { AlertOctagon } from 'lucide-react';
import { usePagedQueue } from '../hooks/useQueue';
import { ApiError } from '../lib/api';
import { fetchSupportQueue, resolveSupportTicket, type SupportTicket } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

const CATEGORY_LABEL: Record<SupportTicket['category'], string> = {
  GENERAL: 'General',
  PAYMENT_DISPUTE: 'Payment dispute',
  ACCOUNT_TAKEOVER: 'Account takeover',
  // Real Uber "trip issue report" category (2026-08-16) -- see backend
  // SupportTicketCategory.RIDE_ISSUE's own doc comment.
  RIDE_ISSUE: 'Ride issue',
};

function isOverdue(dueBy: string) {
  return new Date(dueBy).getTime() < Date.now();
}

function SupportCard({ ticket, onResolved }: { ticket: SupportTicket; onResolved: () => void }) {
  const [pending, setPending] = useState(false);
  const [resolving, setResolving] = useState<'REFUNDED' | 'REJECTED' | null>(null);
  const [notes, setNotes] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const overdue = isOverdue(ticket.dueBy);

  const resolve = async (resolution: 'REFUNDED' | 'REJECTED') => {
    setPending(true);
    setActionError(null);
    try {
      await resolveSupportTicket(ticket.id, resolution, notes || undefined);
      onResolved();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Failed to resolve.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <span
              style={{
                fontSize: '12px',
                fontWeight: 700,
                color: 'var(--itunda-indigo)',
                backgroundColor: 'var(--itunda-indigo-light)',
                padding: '2px 8px',
                borderRadius: '6px',
              }}
            >
              {CATEGORY_LABEL[ticket.category]}
            </span>
            {ticket.category === 'ACCOUNT_TAKEOVER' && (
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '12px', fontWeight: 700, color: 'var(--itunda-red)' }}>
                <AlertOctagon size={13} /> Wallet frozen
              </span>
            )}
          </div>
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{ticket.description}</p>
        </div>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
        User {ticket.userId}
        {ticket.transactionId && ` · Transaction ${ticket.transactionId}`}
      </p>
      <p style={{ fontSize: '13px', fontWeight: 600, color: overdue ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
        Due {new Date(ticket.dueBy).toLocaleString()}{overdue && ' · Overdue'}
      </p>

      {resolving && (
        <textarea
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Resolution notes (optional)"
          rows={2}
          style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
        />
      )}

      {actionError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
          {actionError}
        </p>
      )}

      <div style={{ display: 'flex', gap: '8px' }}>
        {!resolving ? (
          <>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={pending} onClick={() => setResolving('REJECTED')}>
              Reject
            </button>
            <button
              className="itunda-btn itunda-btn-primary"
              style={{ flex: 1 }}
              disabled={pending || !ticket.transactionId}
              onClick={() => setResolving('REFUNDED')}
              title={!ticket.transactionId ? 'No source transaction to refund' : undefined}
            >
              Refund
            </button>
          </>
        ) : (
          <>
            <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={pending} onClick={() => setResolving(null)}>
              Cancel
            </button>
            <button
              className={resolving === 'REFUNDED' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-danger'}
              style={{ flex: 1 }}
              disabled={pending}
              onClick={() => resolve(resolving)}
            >
              Confirm {resolving === 'REFUNDED' ? 'refund' : 'reject'}
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export default function SupportQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchSupportQueue);

  return (
    <div>
      <QueueHeader title="Support" count={totalElements} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No open support tickets." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((ticket) => (
            <SupportCard key={ticket.id} ticket={ticket} onResolved={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
