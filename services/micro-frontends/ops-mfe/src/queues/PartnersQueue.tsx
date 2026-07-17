import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { decidePartnerMiniApp, fetchPartnersQueue, type PartnerMiniAppSubmission } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

// Real third-party mini-app review queue -- closes the "allow partners to build apps
// in itunda like apps in Toss" gap. See PartnerService.kt's own doc comment for the
// full account of what's real here (a real registry + review workflow + published
// catalog) vs. the honestly-scoped-out follow-up (the mobile Saronite host doesn't yet
// actually run a partner's bundle at runtime).
function PartnerMiniAppCard({ submission, onDecided }: { submission: PartnerMiniAppSubmission; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');

  const approve = async () => {
    setPending(true);
    try {
      await decidePartnerMiniApp(submission.id, true);
      onDecided();
    } finally {
      setPending(false);
    }
  };

  const reject = async () => {
    setPending(true);
    try {
      await decidePartnerMiniApp(submission.id, false, reason || undefined);
      onDecided();
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--toss-grey-900)' }}>{submission.name}</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Partner {submission.partnerId}</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)', marginTop: '4px' }}>{submission.description}</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
          Bundle: <span style={{ fontFamily: 'monospace' }}>{submission.bundleUrl}</span>
        </p>
        {submission.permissions && (
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '8px' }}>
            {submission.permissions.split(',').map((scope) => (
              <span
                key={scope}
                style={{
                  fontSize: '11px', fontWeight: 600, padding: '2px 8px', borderRadius: '8px',
                  backgroundColor: 'var(--toss-blue-light)', color: 'var(--toss-blue)',
                }}
              >
                {scope}
              </span>
            ))}
          </div>
        )}
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '8px' }}>
          Submitted {new Date(submission.createdAt).toLocaleString()}
        </p>
      </div>

      {rejecting && (
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Reason (optional)"
          rows={2}
          style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
        />
      )}

      <div style={{ display: 'flex', gap: '8px' }}>
        {!rejecting ? (
          <>
            <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={pending} onClick={() => setRejecting(true)}>
              Reject
            </button>
            <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={pending} onClick={approve}>
              Approve
            </button>
          </>
        ) : (
          <>
            <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={pending} onClick={() => setRejecting(false)}>
              Cancel
            </button>
            <button className="toss-btn toss-btn-danger" style={{ flex: 1 }} disabled={pending} onClick={reject}>
              Confirm reject
            </button>
          </>
        )}
      </div>
    </div>
  );
}

export default function PartnersQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchPartnersQueue);

  return (
    <div>
      <QueueHeader title="Partner mini-apps" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
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
        </div>
      )}
    </div>
  );
}
