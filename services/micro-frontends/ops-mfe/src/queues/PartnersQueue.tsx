import { useState } from 'react';
import { usePagedQueue } from '../hooks/useQueue';
import { activatePartnerMiniAppRelease, decidePartnerMiniApp, fetchPartnerMiniAppReleases, rollbackPartnerMiniAppRelease, stagePartnerMiniAppRelease, type PartnerMiniAppRelease, type PartnerMiniAppSubmission } from '../lib/queues';
import { ApiError } from '../lib/api';
import { QueueEmpty, QueueError, QueueHeader, QueueLoadMore, QueueSkeleton } from '../QueueState';

// Real third-party mini-app review queue -- closes the "allow partners to build apps
// in itunda like apps in Toss" gap. See PartnerService.kt's own doc comment for the
// full account of what's real here: registry + review workflow + published catalog,
// immutable release history, integrity verification, and the mobile Saronite runtime loader.
function PartnerMiniAppCard({ submission, onDecided }: { submission: PartnerMiniAppSubmission; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);

  const approve = async () => {
    setPending(true);
    setError(null);
    try {
      await decidePartnerMiniApp(submission.id, true);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not approve this mini-app.');
    } finally {
      setPending(false);
    }
  };

  const reject = async () => {
    setPending(true);
    setError(null);
    try {
      await decidePartnerMiniApp(submission.id, false, reason || undefined);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reject this mini-app.');
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-text-primary)' }}>{submission.name}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-text-tertiary)' }}>Partner {submission.partnerId} · {submission.category}</p>
        <p style={{ fontSize: '13px', color: 'var(--itunda-text-secondary)', marginTop: '4px' }}>{submission.description}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-text-tertiary)', marginTop: '4px' }}>
          Bundle: <span style={{ fontFamily: 'monospace' }}>{submission.bundleUrl}</span>
        </p>
        {submission.permissions && (
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '8px' }}>
            {submission.permissions.split(',').map((scope) => (
              <span
                key={scope}
                style={{
                  fontSize: '11px', fontWeight: 600, padding: '2px 8px', borderRadius: '8px',
                  backgroundColor: 'var(--itunda-surface-brand)', color: 'var(--itunda-brand)',
                }}
              >
                {scope}
              </span>
            ))}
          </div>
        )}
        <p style={{ fontSize: '12px', color: 'var(--itunda-text-tertiary)', marginTop: '8px' }}>
          Submitted {new Date(submission.createdAt).toLocaleString()}
        </p>
      </div>

      {rejecting && (
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Reason (optional)"
          maxLength={255}
          rows={2}
          style={{ padding: '10px', borderRadius: 'var(--itunda-control-radius, 12px)', border: '1px solid var(--itunda-border-default)', fontSize: '14px', resize: 'vertical' }}
        />
      )}

      {error && <p style={{ fontSize: '12px', color: 'var(--itunda-field-border-error)' }} role="alert">{error}</p>}

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
      {submission.status === 'APPROVED' && <ReleaseControls submission={submission} />}
    </div>
  );
}

function ReleaseControls({ submission }: { submission: PartnerMiniAppSubmission }) {
  const [releases, setReleases] = useState<PartnerMiniAppRelease[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const load = async () => { try { setError(null); setReleases(await fetchPartnerMiniAppReleases(submission.id)); } catch (err) { setError(err instanceof ApiError ? err.message : 'Could not load releases.'); } };
  const run = async (fn: () => Promise<unknown>) => { setBusy(true); setError(null); try { await fn(); await load(); } catch (err) { setError(err instanceof ApiError ? err.message : 'Release action failed.'); } finally { setBusy(false); } };
  return <div style={{ marginTop: '10px' }}>
    <button className='itunda-btn itunda-btn-secondary' disabled={busy} onClick={load}>Release history</button>
    {error && <p style={{ fontSize: '12px', color: 'var(--itunda-field-border-error)' }}>{error}</p>}
    {releases?.map((r) => <div key={r.releaseId} style={{ marginTop: '8px', padding: '10px', borderRadius: '12px', background: 'var(--itunda-surface-secondary)' }}>
      <strong style={{ fontSize: '12px' }}>{r.releaseId}</strong> <span style={{ fontSize: '11px' }}>{r.status}</span>
      <p style={{ fontSize: '11px', color: 'var(--itunda-text-tertiary)' }}>{r.bundleSizeBytes.toLocaleString()} bytes · SHA-256 {r.bundleSha256.slice(0, 12)}…</p>
      {r.status === 'APPROVED' || r.status === 'ROLLED_BACK' ? <button className='itunda-btn itunda-btn-secondary' disabled={busy} onClick={() => run(() => stagePartnerMiniAppRelease(r.releaseId))}>Stage</button> : null}
      {r.status === 'STAGED' ? <button className='itunda-btn itunda-btn-primary' disabled={busy} onClick={() => run(() => activatePartnerMiniAppRelease(r.releaseId))}>Activate</button> : null}
      {r.status === 'ROLLED_BACK' ? <button className='itunda-btn itunda-btn-primary' disabled={busy} onClick={() => run(() => rollbackPartnerMiniAppRelease(r.releaseId, 'Operator restore'))}>Restore</button> : null}
    </div>)}
  </div>;
}
export default function PartnersQueue() {
  const { items, error, refreshing, reload, loadMore, loadingMore, totalElements, hasMore } = usePagedQueue(fetchPartnersQueue);

  return (
    <div>
      <QueueHeader title="Partner mini-apps" count={totalElements} onReload={reload} refreshing={refreshing} />
      <p style={{ fontSize: '13px', color: 'var(--itunda-text-tertiary)', marginBottom: '12px' }}>
        Third-party mini-app submissions built on itunda's Saronite SDK, pending review before appearing in the
        real published catalog.
      </p>
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No pending partner mini-app submissions." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((submission) => (
            <PartnerMiniAppCard key={submission.id} submission={submission} onDecided={reload} />
          ))}
          {hasMore && <QueueLoadMore onLoadMore={loadMore} loading={loadingMore} />}
        </div>
      )}
    </div>
  );
}
