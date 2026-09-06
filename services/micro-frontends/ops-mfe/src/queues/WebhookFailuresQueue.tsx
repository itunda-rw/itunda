import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { fetchExhaustedWebhookDeliveries, replayExhaustedWebhookDelivery, type ExhaustedWebhookDelivery } from '../lib/merchantAdminQueues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

function WebhookFailureCard({ delivery, onReplayed }: { delivery: ExhaustedWebhookDelivery; onReplayed: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [replayed, setReplayed] = useState(false);

  const replay = async () => {
    setPending(true);
    setError(null);
    try {
      await replayExhaustedWebhookDelivery(delivery.id);
      setReplayed(true);
      onReplayed();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not replay this delivery.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '16px' }}>
      <div style={{ flex: 1 }}>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{delivery.eventType ?? 'Unknown event'}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>Merchant {delivery.merchantId}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          {delivery.attemptCount} attempts · {new Date(delivery.createdAt).toLocaleString()}
        </p>
        {delivery.lastError && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }}>{delivery.lastError}</p>}
        {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      </div>
      {replayed ? (
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-green)' }}>Replayed</span>
      ) : (
        <button className="itunda-btn itunda-btn-primary" style={{ padding: '10px 18px' }} disabled={pending} onClick={replay}>
          Replay
        </button>
      )}
    </div>
  );
}

export default function WebhookFailuresQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchExhaustedWebhookDeliveries);

  return (
    <div>
      <QueueHeader title="Webhook failures" count={totalElements} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No exhausted webhook deliveries." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((delivery) => (
            <WebhookFailureCard key={delivery.id} delivery={delivery} onReplayed={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
