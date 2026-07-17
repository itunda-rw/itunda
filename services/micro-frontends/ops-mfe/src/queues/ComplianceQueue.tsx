import { useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { decideCompliance, fetchComplianceQueue, type KycSubmission } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

// Real demo NIDA structural pre-check result (see lib/queues.ts's KycSubmission doc
// comment) -- a reviewer aid, never an auto-decision. Colors mirror this pre-check's own
// honesty: MATCHED is a real positive signal (green), the other three are all real
// reasons a human needs to look closer (amber), not a hard red "reject" verdict.
function AutoVerificationBadge({ submission }: { submission: KycSubmission }) {
  if (!submission.autoVerificationStatus) return null;
  const isMatch = submission.autoVerificationStatus === 'MATCHED';
  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '6px',
        padding: '4px 10px',
        borderRadius: '8px',
        backgroundColor: isMatch ? '#E3F9E5' : '#FFF4E5',
        color: isMatch ? '#1B8A3D' : '#B25E09',
        fontSize: '12px',
        fontWeight: 600,
        marginTop: '6px',
      }}
    >
      Auto-check: {submission.autoVerificationStatus.replace(/_/g, ' ')}
      {submission.autoVerificationDetail && (
        <span style={{ fontWeight: 400, opacity: 0.85 }}>· {submission.autoVerificationDetail}</span>
      )}
    </div>
  );
}

function ComplianceCard({ submission, onDecided }: { submission: KycSubmission; onDecided: () => void }) {
  const [pending, setPending] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');

  const approve = async () => {
    setPending(true);
    try {
      await decideCompliance(submission.id, true);
      onDecided();
    } finally {
      setPending(false);
    }
  };

  const reject = async () => {
    setPending(true);
    try {
      await decideCompliance(submission.id, false, reason || undefined);
      onDecided();
    } finally {
      setPending(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div>
        <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--toss-grey-900)' }}>{submission.documentType}</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          User {submission.userId} · Document {submission.documentReference || submission.documentNumber}
        </p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          Submitted {new Date(submission.submittedAt).toLocaleString()}
        </p>
        <AutoVerificationBadge submission={submission} />
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

export default function ComplianceQueue() {
  const { items, error, refreshing, reload } = useQueue(fetchComplianceQueue);

  return (
    <div>
      <QueueHeader title="Compliance (KYC)" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No pending KYC submissions." />}
      {!error && items !== null && items.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((submission) => (
            <ComplianceCard key={submission.id} submission={submission} onDecided={reload} />
          ))}
        </div>
      )}
    </div>
  );
}
