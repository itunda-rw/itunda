import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { ApiError } from '../lib/api';
import { fetchChatReportsQueue, removeChatReportMessage, resolveChatReport, type ChatReport } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

function ChatReportCard({ report, onDecided }: { report: ChatReport; onDecided: () => void }) {
  const [pending, setPending] = useState<'dismiss' | 'remove' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const decide = async (action: 'dismiss' | 'remove') => {
    setPending(action);
    setActionError(null);
    try {
      if (action === 'remove') {
        await removeChatReportMessage(report.id);
      } else {
        await resolveChatReport(report.id);
      }
      onDecided();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Failed to act on this report.');
    } finally {
      setPending(null);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{report.reason}</p>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
        Reported by {report.reporterUserId} · Message {report.messageId} · {new Date(report.createdAt).toLocaleString()}
      </p>
      {actionError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
          {actionError}
        </p>
      )}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ flex: 1 }}
          disabled={pending !== null}
          onClick={() => decide('dismiss')}
        >
          {pending === 'dismiss' ? '…' : 'Dismiss'}
        </button>
        <button
          className="itunda-btn itunda-btn-danger"
          style={{ flex: 1 }}
          disabled={pending !== null}
          onClick={() => decide('remove')}
        >
          {pending === 'remove' ? '…' : 'Remove message'}
        </button>
      </div>
    </div>
  );
}

export default function ChatReportsQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchChatReportsQueue);

  return (
    <div>
      <QueueHeader title="Talk message reports" count={totalElements} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No open message reports." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((report) => (
            <ChatReportCard key={report.id} report={report} onDecided={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
