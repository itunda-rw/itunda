import { useQueue } from '../hooks/useQueue';
import { getReport } from '../lib/merchant';
import { QueueError, QueueSkeleton } from '../QueueState';

export default function ReportsScreen() {
  const { items, error, refreshing, reload } = useQueue(async () => (await getReport()).days);

  if (error) return <QueueError message={error} onRetry={reload} />;
  if (items === null) return <QueueSkeleton />;

  const totalGross = items.reduce((sum, d) => sum + d.grossAmount, 0);
  const totalFees = items.reduce((sum, d) => sum + d.fees, 0);
  const totalNet = items.reduce((sum, d) => sum + d.netAmount, 0);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <h2 style={{ fontSize: '20px', fontWeight: 700 }}>Reports (last 7 days)</h2>
        <button className="toss-btn toss-btn-secondary" style={{ padding: '8px 14px' }} disabled={refreshing} onClick={reload}>
          Refresh
        </button>
      </div>

      <div className="toss-card" style={{ display: 'flex', gap: '32px' }}>
        <div>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Gross</p>
          <p style={{ fontSize: '20px', fontWeight: 700 }}>{totalGross.toLocaleString()} RWF</p>
        </div>
        <div>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Fees</p>
          <p style={{ fontSize: '20px', fontWeight: 700 }}>{totalFees.toLocaleString()} RWF</p>
        </div>
        <div>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Net</p>
          <p style={{ fontSize: '20px', fontWeight: 700, color: 'var(--toss-blue)' }}>{totalNet.toLocaleString()} RWF</p>
        </div>
      </div>

      <div className="toss-card" style={{ padding: 0, overflow: 'hidden' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
          <thead>
            <tr style={{ backgroundColor: 'var(--toss-grey-100)' }}>
              <th style={{ textAlign: 'left', padding: '12px 16px' }}>Date</th>
              <th style={{ textAlign: 'right', padding: '12px 16px' }}>Collections</th>
              <th style={{ textAlign: 'right', padding: '12px 16px' }}>Gross</th>
              <th style={{ textAlign: 'right', padding: '12px 16px' }}>Fees</th>
              <th style={{ textAlign: 'right', padding: '12px 16px' }}>Net</th>
            </tr>
          </thead>
          <tbody>
            {items.map((day) => (
              <tr key={day.date} style={{ borderTop: '1px solid var(--toss-grey-200)' }}>
                <td style={{ padding: '12px 16px' }}>{day.date}</td>
                <td style={{ textAlign: 'right', padding: '12px 16px' }}>{day.collectionCount}</td>
                <td style={{ textAlign: 'right', padding: '12px 16px' }}>{day.grossAmount.toLocaleString()}</td>
                <td style={{ textAlign: 'right', padding: '12px 16px' }}>{day.fees.toLocaleString()}</td>
                <td style={{ textAlign: 'right', padding: '12px 16px', fontWeight: 600 }}>{day.netAmount.toLocaleString()}</td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td colSpan={5} style={{ padding: '32px 16px', textAlign: 'center', color: 'var(--toss-grey-500)' }}>
                  No collections in this range.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
