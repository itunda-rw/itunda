import { useState } from 'react';
import { IconBack } from './icons/ItundaIcons';
import { ApiError } from './lib/api';
import { transferBetweenOwnAccounts, type Account } from './lib/account';

// Real Toss "충전하기" (top up) reference (2026-09-12, direct user-supplied Toss Pay
// screenshots) -- closes the exact gap PayHub.tsx's own onAddMoney comment already
// disclosed ("itunda has no self-service 'pull an amount from my linked account
// right now' flow"). Picks a source from the caller's OTHER real accounts (never a
// fabricated external MyData-style balance -- itunda has no such integration) and a
// real amount, then posts the same internal-transfer endpoint the reverse "옮기기"
// direction would also use. Own file, matching this screen family's established
// "genuinely distinct flow, own file" convention.
const QUICK_AMOUNTS = [1000, 5000, 10000];

export function AddMoneyScreen({ destination, sourceOptions, onBack, onDone }: { destination: Account; sourceOptions: Account[]; onBack: () => void; onDone: () => void }) {
  const [sourceId, setSourceId] = useState(sourceOptions[0]?.id ?? '');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const source = sourceOptions.find((a) => a.id === sourceId);
  const numericAmount = Number(amount);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!source || !numericAmount || numericAmount <= 0) return;
    setBusy(true);
    setError(null);
    try {
      await transferBetweenOwnAccounts(source.id, destination.id, numericAmount);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not complete this top-up.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '14px 16px' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>
        <div style={{ padding: '4px 20px 24px' }}>
          <p style={{ margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Add money</p>
        </div>

        {sourceOptions.length === 0 ? (
          <p style={{ padding: '0 20px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
            You don&apos;t have another itunda account to top up from yet.
          </p>
        ) : (
          <form onSubmit={handleSubmit} style={{ padding: '0 20px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <p style={{ margin: '0 0 8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>From</p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                {sourceOptions.map((a) => (
                  <label key={a.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)', padding: '10px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <input type="radio" name="source" checked={sourceId === a.id} onChange={() => setSourceId(a.id)} />
                      {a.nickname ?? a.accountName}
                    </span>
                    <span style={{ color: 'var(--itunda-grey-500)' }}>{a.balance.toLocaleString('en-US')} RWF</span>
                  </label>
                ))}
              </div>
            </div>

            <div>
              <p style={{ margin: '0 0 8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Amount</p>
              <input
                type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)" required autoFocus min="1"
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}
              />
              <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                {QUICK_AMOUNTS.map((q) => (
                  <button
                    key={q} type="button" className="itunda-btn itunda-btn-secondary"
                    style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    onClick={() => setAmount(String((Number(amount) || 0) + q))}
                  >
                    +{q.toLocaleString('en-US')}
                  </button>
                ))}
              </div>
            </div>

            {error && <p role="alert" style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }}>{error}</p>}

            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy || !source || !numericAmount} style={{ minHeight: '48px', borderRadius: '999px' }}>
              {busy ? 'Adding money…' : 'Add money'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
