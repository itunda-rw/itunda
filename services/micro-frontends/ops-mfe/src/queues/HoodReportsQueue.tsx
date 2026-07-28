import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { ApiError } from '../lib/api';
import { fetchHoodReportsQueue, removeHoodReportTarget, resolveHoodReport, type HoodReport } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

const TARGET_LABEL: Record<HoodReport['targetType'], string> = {
  MARKETPLACE_LISTING: 'Marketplace listing',
  COMMUNITY_POST: 'Community post',
  JOB_POST: 'Job post',
  PROPERTY_LISTING: 'Property listing',
  DIRECT_MESSAGE: 'Direct message',
  GROUP_MESSAGE: 'Group message',
};

function HoodReportCard({ report, onDecided }: { report: HoodReport; onDecided: () => void }) {
  const [pending, setPending] = useState<'dismiss' | 'remove' | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const decide = async (action: 'dismiss' | 'remove') => {
    setPending(action);
    setActionError(null);
    try {
      if (action === 'remove') {
        await removeHoodReportTarget(report.id);
      } else {
        await resolveHoodReport(report.id);
      }
      onDecided();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Failed to act on this report.');
    } finally {
      setPending(null);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span
            style={{
              display: 'inline-block',
              fontSize: '12px',
              fontWeight: 700,
              color: 'var(--toss-blue)',
              backgroundColor: 'var(--toss-blue-light)',
              padding: '2px 8px',
              borderRadius: '6px',
              marginBottom: '6px',
            }}
          >
            {TARGET_LABEL[report.targetType]}
          </span>
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--toss-grey-900)' }}>{report.reason}</p>
        </div>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
        Reported by {report.reporterUserId} · Target {report.targetId} · {new Date(report.createdAt).toLocaleString()}
      </p>
      {actionError && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
          {actionError}
        </p>
      )}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className="toss-btn toss-btn-secondary"
          style={{ flex: 1 }}
          disabled={pending !== null}
          onClick={() => decide('dismiss')}
        >
          {pending === 'dismiss' ? '…' : 'Dismiss'}
        </button>
        <button
          className="toss-btn toss-btn-danger"
          style={{ flex: 1 }}
          disabled={pending !== null}
          onClick={() => decide('remove')}
        >
          {pending === 'remove' ? '…' : 'Remove content'}
        </button>
      </div>
    </div>
  );
}

export default function HoodReportsQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchHoodReportsQueue);

  return (
    <div>
      <QueueHeader title="Hood content reports" count={totalElements} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No open content reports." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((report) => (
            <HoodReportCard key={report.id} report={report} onDecided={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
