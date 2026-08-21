import { useCallback, useEffect, useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { ApiError } from '../lib/api';
import {
  fetchAgentReconciliationReport, fetchPendingTillReconciliations, resolveTillReconciliation,
  type AgentReconciliationReport, type AgentTillReconciliation,
} from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

type ReconciliationTab = 'PENDING' | 'REPORT';

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function ReconciliationCard({ reconciliation, onResolved }: { reconciliation: AgentTillReconciliation; onResolved: () => void }) {
  const [pending, setPending] = useState(false);
  const [note, setNote] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const short = reconciliation.variance < 0;

  const resolve = async () => {
    setPending(true);
    setActionError(null);
    try {
      await resolveTillReconciliation(reconciliation.id, note);
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
          <p style={{ fontSize: '15px', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>Agent {reconciliation.agentId}</p>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            Business date {reconciliation.businessDate} · Submitted by {reconciliation.submittedByUserId}
          </p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: short ? 'var(--itunda-red)' : 'var(--itunda-green)' }}>
          {short ? '' : '+'}{reconciliation.variance.toLocaleString()} RWF
        </span>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>
        Expected {reconciliation.expectedCash.toLocaleString()} RWF · Counted {reconciliation.countedCash.toLocaleString()} RWF
      </p>

      <textarea
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Review note (required, 1-255 characters)"
        rows={2}
        maxLength={255}
        style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />

      {actionError && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
          {actionError}
        </p>
      )}

      <button
        className="itunda-btn itunda-btn-primary"
        disabled={pending || note.trim().length === 0}
        onClick={resolve}
      >
        {pending ? '…' : 'Resolve'}
      </button>
    </div>
  );
}

// Real agent till reconciliation report-by-date-range (item 133) -- see lib/queues.ts's
// own doc comment on AgentReconciliationReport for the full account. Mirrors
// ReconciliationView.tsx's own date-input pattern for the unrelated payment-rail
// reconciliation feature (same UI convention, different domain).
function ReconciliationReportView() {
  const [from, setFrom] = useState(todayIso());
  const [to, setTo] = useState(todayIso());
  const [report, setReport] = useState<AgentReconciliationReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      setReport(await fetchAgentReconciliationReport(from, to));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load the report.');
    } finally {
      setRefreshing(false);
    }
  }, [from, to]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      <QueueHeader title="Reconciliation report" count={report?.reconciliations.length ?? null} onReload={load} refreshing={refreshing}>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <input type="date" aria-label="From date" value={from} onChange={(e) => setFrom(e.target.value)} style={{ padding: '8px 10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }} />
          <span style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>to</span>
          <input type="date" aria-label="To date" value={to} onChange={(e) => setTo(e.target.value)} style={{ padding: '8px 10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }} />
        </div>
      </QueueHeader>
      {error && <QueueError message={error} onRetry={load} />}
      {!error && report === null && <QueueSkeleton />}
      {!error && report !== null && (
        <div>
          <div style={{ display: 'flex', gap: '12px', marginBottom: '16px' }}>
            <div className="itunda-card" style={{ flex: 1 }}>
              <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>Pending review</p>
              <p style={{ fontSize: '20px', fontWeight: 700 }}>{report.pendingReviewCount}</p>
            </div>
            <div className="itunda-card" style={{ flex: 1 }}>
              <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>Total variance</p>
              <p style={{ fontSize: '20px', fontWeight: 700, color: report.totalVariance < 0 ? 'var(--itunda-red)' : 'var(--itunda-green)' }}>
                {report.totalVariance.toLocaleString()} RWF
              </p>
            </div>
          </div>
          {report.reconciliations.length === 0 ? (
            <QueueEmpty label="No till reconciliations in this date range." />
          ) : (
            <div className="itunda-card" style={{ padding: 0, overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
                <thead>
                  <tr style={{ backgroundColor: 'var(--itunda-grey-100)', textAlign: 'left' }}>
                    {['Agent', 'Business date', 'Expected', 'Counted', 'Variance', 'Status'].map((h) => (
                      <th key={h} style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {report.reconciliations.map((r) => (
                    <tr key={r.id} style={{ borderTop: '1px solid var(--itunda-grey-200)' }}>
                      <td style={{ padding: '12px 16px', fontWeight: 600 }}>{r.agentId}</td>
                      <td style={{ padding: '12px 16px' }}>{r.businessDate}</td>
                      <td style={{ padding: '12px 16px' }}>{r.expectedCash.toLocaleString()}</td>
                      <td style={{ padding: '12px 16px' }}>{r.countedCash.toLocaleString()}</td>
                      <td style={{ padding: '12px 16px', color: r.variance !== 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                        {r.variance > 0 ? `+${r.variance}` : r.variance}
                      </td>
                      <td style={{ padding: '12px 16px' }}>{r.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function AgentReconciliationQueue() {
  const [tab, setTab] = useState<ReconciliationTab>('PENDING');
  const { items, error, refreshing, reload } = useQueue(fetchPendingTillReconciliations);

  return (
    <div>
      <div style={{ display: 'flex', gap: '6px', backgroundColor: 'var(--itunda-grey-100)', padding: '4px', borderRadius: '10px', marginBottom: '16px', width: 'fit-content' }}>
        {(['PENDING', 'REPORT'] as ReconciliationTab[]).map((t) => (
          <button
            key={t} onClick={() => setTab(t)}
            style={{
              padding: '6px 12px', borderRadius: '8px', border: 'none', fontSize: '13px', fontWeight: 600, cursor: 'pointer',
              backgroundColor: tab === t ? 'var(--itunda-indigo)' : 'transparent', color: tab === t ? '#fff' : 'var(--itunda-grey-700)',
            }}
          >
            {t === 'PENDING' ? 'Pending review' : 'Report by date range'}
          </button>
        ))}
      </div>

      {tab === 'REPORT' ? (
        <ReconciliationReportView />
      ) : (
        <div>
          <QueueHeader title="Agent till reconciliations" count={items?.length ?? null} onReload={reload} refreshing={refreshing} />
          {error && <QueueError message={error} onRetry={reload} />}
          {!error && items === null && <QueueSkeleton />}
          {!error && items !== null && items.length === 0 && <QueueEmpty label="No till variances awaiting review." />}
          {!error && items !== null && items.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {items.map((reconciliation) => (
                <ReconciliationCard key={reconciliation.id} reconciliation={reconciliation} onResolved={reload} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
