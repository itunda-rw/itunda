import { useEffect, useState } from 'react';
import { Landmark } from 'lucide-react';
import { declareSaccoDividend, fetchSaccoPoolStatus, type SaccoPoolStatus } from '../lib/sacco';
import { ApiError } from '../lib/api';
import { QueueError, QueueSkeleton } from '../QueueState';

export default function SaccoOversightView() {
  const [status, setStatus] = useState<SaccoPoolStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [declaring, setDeclaring] = useState(false);
  const [result, setResult] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchSaccoPoolStatus()
      .then(setStatus)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the real SACCO pool status.'));
  };

  useEffect(load, []);

  const declare = async () => {
    setDeclaring(true);
    setError(null);
    try {
      const res = await declareSaccoDividend();
      setResult(`Declared a real dividend at ${(res.distribution.dividendRate * 100).toFixed(2)}%, paying out ${res.distribution.totalDividendPaid.toLocaleString('en-US')} RWF total.`);
      setConfirming(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not declare a dividend.');
      setConfirming(false);
    } finally {
      setDeclaring(false);
    }
  };

  return (
    <div style={{ maxWidth: '520px' }}>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>SACCO oversight</h2>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        Real Umurenge SACCO-style shares -- pool solvency and dividend declaration. Declaring is a real governance
        decision (when, not whether the underwriting is safe), not an automatic schedule.
      </p>

      {result && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-green)', marginBottom: '12px' }} role="status">
          {result}
        </p>
      )}
      {error && <QueueError message={error} onRetry={load} />}
      {!error && status === null && <QueueSkeleton />}
      {!error && status && (
        <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '16px', padding: '20px' }}>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <div style={{ flex: 1, minWidth: '180px' }}>
              <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Pool account balance</p>
              <p style={{ fontSize: '20px', fontWeight: 700 }}>{status.poolAccountBalance.toLocaleString('en-US')} RWF</p>
            </div>
            <div style={{ flex: 1, minWidth: '180px' }}>
              <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Total shares outstanding</p>
              <p style={{ fontSize: '20px', fontWeight: 700 }}>{status.totalSharesOutstanding.toLocaleString('en-US')} RWF</p>
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span
              style={{
                width: '8px', height: '8px', borderRadius: '50%',
                backgroundColor: status.solvent ? 'var(--itunda-green)' : 'var(--itunda-red)',
                display: 'inline-block',
              }}
            />
            <p style={{ fontSize: '13px', fontWeight: 600, color: status.solvent ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>
              {status.solvent ? 'Solvent' : 'Insolvent -- do not declare a dividend'}
            </p>
          </div>

          {confirming && (
            <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>
              Declare a real dividend now, paying every real shareholder from itunda's own interest expense? This
              can't be undone.
            </p>
          )}
          <div style={{ display: 'flex', gap: '8px' }}>
            {confirming ? (
              <>
                <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={declaring} onClick={() => setConfirming(false)}>
                  Cancel
                </button>
                <button className="itunda-btn itunda-btn-primary" style={{ flex: 1, gap: '6px' }} disabled={declaring} onClick={declare}>
                  <Landmark size={16} />
                  {declaring ? 'Declaring…' : 'Confirm declare'}
                </button>
              </>
            ) : (
              <button
                className="itunda-btn itunda-btn-primary"
                style={{ flex: 1, gap: '6px' }}
                disabled={!status.solvent}
                onClick={() => setConfirming(true)}
              >
                <Landmark size={16} />
                Declare dividend
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
