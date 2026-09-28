import { useCallback, useState } from 'react';
import { useQueue } from '../hooks/useQueue';
import { fetchReconciliation, fetchTwoSidedReconciliation, type ReconciliationRow, type TwoSidedReconciliationRow } from '../lib/queues';
import { QueueEmpty, QueueError, QueueHeader, QueueSkeleton } from '../QueueState';

type Side = 'ONE_SIDED' | 'TWO_SIDED_DEMO';

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function SideToggle({ side, onChange }: { side: Side; onChange: (s: Side) => void }) {
  return (
    <div style={{ display: 'flex', gap: '6px', backgroundColor: 'var(--itunda-grey-100)', padding: '4px', borderRadius: 'var(--itunda-control-radius, 12px)' }}>
      {(['ONE_SIDED', 'TWO_SIDED_DEMO'] as Side[]).map((s) => (
        <button
          key={s}
          onClick={() => onChange(s)}
          style={{
            padding: '6px 12px',
            borderRadius: 'var(--itunda-control-radius, 12px)',
            border: 'none',
            fontSize: '13px',
            fontWeight: 600,
            cursor: 'pointer',
            backgroundColor: side === s ? 'var(--itunda-brand)' : 'transparent',
            color: side === s ? '#fff' : 'var(--itunda-text-secondary)',
          }}
        >
          {s === 'ONE_SIDED' ? 'One-sided (real)' : 'Two-sided (demo)'}
        </button>
      ))}
    </div>
  );
}

function OneSidedTable({ items, error, reload }: { items: ReconciliationRow[] | null; error: string | null; reload: () => void }) {
  return (
    <div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-text-tertiary)', marginBottom: '12px' }}>
        Reconciles itunda's own provider-attempt log against itself, per rail, for the selected day.
        There's no external settlement file to diff against yet, so this is one-sided.
      </p>
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No provider attempts recorded for this date." />}
      {!error && items !== null && items.length > 0 && (
        <div className="itunda-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ backgroundColor: 'var(--itunda-grey-100)', textAlign: 'left' }}>
                {['Rail', 'Attempts', 'Success', 'Failure', 'Success rate', 'Avg latency'].map((h) => (
                  <th key={h} style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--itunda-text-secondary)' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.railId} style={{ borderTop: '1px solid var(--itunda-border-default)' }}>
                  <td style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--itunda-text-primary)' }}>{row.displayName}</td>
                  <td style={{ padding: '12px 16px' }}>{row.totalAttempts}</td>
                  <td style={{ padding: '12px 16px', color: 'var(--itunda-green)' }}>{row.successCount}</td>
                  <td style={{ padding: '12px 16px', color: row.failureCount > 0 ? 'var(--itunda-field-border-error)' : 'var(--itunda-text-tertiary)' }}>
                    {row.failureCount}
                  </td>
                  <td style={{ padding: '12px 16px' }}>{(row.successRate * 100).toFixed(1)}%</td>
                  <td style={{ padding: '12px 16px' }}>{row.avgLatencyMs.toFixed(0)}ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function TwoSidedDemoTable({
  items,
  error,
  reload,
}: {
  items: TwoSidedReconciliationRow[] | null;
  error: string | null;
  reload: () => void;
}) {
  return (
    <div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-text-tertiary)', marginBottom: '12px' }}>
        Compares itunda's own real success count against a real, deterministic <strong>simulated</strong> external
        settlement count for the same rail/day -- there's no real settlement file feed yet, so the external side is a
        demo. Every row is marked as such.
      </p>
      {error && <QueueError message={error} onRetry={reload} />}
      {!error && items === null && <QueueSkeleton />}
      {!error && items !== null && items.length === 0 && <QueueEmpty label="No provider attempts recorded for this date." />}
      {!error && items !== null && items.length > 0 && (
        <div className="itunda-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ backgroundColor: 'var(--itunda-grey-100)', textAlign: 'left' }}>
                {['Rail', 'itunda success (real)', 'External settled (demo)', 'Discrepancy', 'Status'].map((h) => (
                  <th key={h} style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--itunda-text-secondary)' }}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.railId} style={{ borderTop: '1px solid var(--itunda-border-default)' }}>
                  <td style={{ padding: '12px 16px', fontWeight: 600, color: 'var(--itunda-text-primary)' }}>{row.displayName}</td>
                  <td style={{ padding: '12px 16px' }}>{row.itundaSuccessCount}</td>
                  <td style={{ padding: '12px 16px' }}>
                    {row.externalSettledCount}
                    {row.isExternalCountDemo && (
                      <span
                        style={{
                          marginLeft: '8px',
                          fontSize: '11px',
                          fontWeight: 600,
                          color: '#B8860B',
                          backgroundColor: '#FFF3CD',
                          padding: '2px 6px',
                          borderRadius: '6px',
                        }}
                      >
                        DEMO
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '12px 16px', color: row.discrepancy !== 0 ? 'var(--itunda-field-border-error)' : 'var(--itunda-text-tertiary)' }}>
                    {row.discrepancy > 0 ? `+${row.discrepancy}` : row.discrepancy}
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    <span style={{ color: row.matched ? 'var(--itunda-green)' : 'var(--itunda-field-border-error)', fontWeight: 600 }}>
                      {row.matched ? 'Matched' : 'Discrepancy'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default function ReconciliationView() {
  const [date, setDate] = useState(todayIso());
  const [side, setSide] = useState<Side>('ONE_SIDED');

  const oneSidedFetcher = useCallback(() => fetchReconciliation(date), [date]);
  const twoSidedFetcher = useCallback(() => fetchTwoSidedReconciliation(date), [date]);
  const oneSided = useQueue(oneSidedFetcher);
  const twoSided = useQueue(twoSidedFetcher);

  const active = side === 'ONE_SIDED' ? oneSided : twoSided;

  return (
    <div>
      <QueueHeader title="Reconciliation" count={null} onReload={active.reload} refreshing={active.refreshing}>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
          <SideToggle side={side} onChange={setSide} />
          <input
            type="date"
            aria-label="Reconciliation date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            style={{ padding: '8px 10px', borderRadius: 'var(--itunda-control-radius, 12px)', border: '1px solid var(--itunda-border-default)', fontSize: '14px' }}
          />
        </div>
      </QueueHeader>
      {side === 'ONE_SIDED' ? (
        <OneSidedTable items={oneSided.items} error={oneSided.error} reload={oneSided.reload} />
      ) : (
        <TwoSidedDemoTable items={twoSided.items} error={twoSided.error} reload={twoSided.reload} />
      )}
    </div>
  );
}
