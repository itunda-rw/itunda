import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { fetchUncategorizedMerchants, reactivateMerchant, suspendMerchant, type UncategorizedMerchant } from '../lib/merchantAdminQueues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

function MerchantCard({ merchant, onDecided }: { merchant: UncategorizedMerchant; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const suspend = async () => {
    setPending(true);
    setError(null);
    try {
      await suspendMerchant(merchant.merchantId);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not suspend this merchant.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{merchant.businessName}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>Merchant {merchant.merchantId}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          {merchant.kybVerified ? 'KYB verified' : 'KYB not verified'} · Created {new Date(merchant.createdAt).toLocaleString()}
        </p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-orange, #FF9500)' }}>No category set — not browsable to real buyers yet.</p>
      </div>
      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <button className="itunda-btn itunda-btn-danger" disabled={pending} onClick={suspend}>
        {pending ? 'Suspending…' : 'Suspend'}
      </button>
    </div>
  );
}

// Real reactivate-by-id form -- the backend has no "list suspended merchants" endpoint
// to browse from (MerchantRepository only exposes the uncategorized-ACTIVE query above),
// so this is the only real way to reach the sibling /reactivate action, same honest
// "manual entry when there's no browsable list" discipline RequestMoneyCard's own
// pay-code field already establishes elsewhere in this codebase.
function ReactivateByIdForm() {
  const [merchantId, setMerchantId] = useState('');
  const [pending, setPending] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = merchantId.trim();
    if (!trimmed) return;
    setPending(true);
    setError(null);
    setResult(null);
    try {
      const response = await reactivateMerchant(trimmed);
      setResult(`Merchant ${trimmed} is now ${response.status}.`);
      setMerchantId('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reactivate that merchant.');
    } finally {
      setPending(false);
    }
  };

  return (
    <form onSubmit={submit} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
      <p style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>Reactivate a suspended merchant</p>
      <div style={{ display: 'flex', gap: '8px' }}>
        <input
          value={merchantId}
          onChange={(e) => setMerchantId(e.target.value)}
          placeholder="Merchant ID"
          style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={pending || !merchantId.trim()}>
          {pending ? 'Reactivating…' : 'Reactivate'}
        </button>
      </div>
      {result && <p style={{ fontSize: '12px', color: 'var(--itunda-indigo)' }}>{result}</p>}
      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </form>
  );
}

export default function MerchantModerationQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchUncategorizedMerchants);

  return (
    <div>
      <QueueHeader title="Merchant moderation" count={totalElements} onReload={reload} refreshing={refreshing} />
      <ReactivateByIdForm />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No uncategorized merchants pending review." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((merchant) => (
            <MerchantCard key={merchant.merchantId} merchant={merchant} onDecided={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
