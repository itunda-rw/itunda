import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import {
  fetchBusinessTransactions,
  getBusinessAccount,
  moveToBusinessAccount,
  moveToPersonalAccount,
  openBusinessAccount,
  type BusinessLedgerEntry,
  type BusinessWallet,
} from '../lib/merchant';
import { DeviceStepUpPrompt } from '../components/DeviceStepUpPrompt';

// Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent (item 150)
// -- see lib/merchant.ts's own doc comment. Android's native merchantapp already has
// this; this is the first web (merchant-mfe) client.
export default function BusinessAccountScreen() {
  const [wallet, setWallet] = useState<BusinessWallet | null | undefined>(undefined);
  const [transactions, setTransactions] = useState<BusinessLedgerEntry[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [opening, setOpening] = useState(false);

  const load = () => {
    setError(null);
    getBusinessAccount()
      .then((w) => {
        setWallet(w);
        fetchBusinessTransactions().then(setTransactions).catch(() => setTransactions([]));
      })
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'BUSINESS_ACCOUNT_NOT_FOUND') {
          setWallet(null);
        } else {
          setError(err instanceof ApiError ? err.message : 'Could not load your business account.');
        }
      });
  };

  useEffect(load, []);

  const handleOpen = async () => {
    setError(null);
    setOpening(true);
    try {
      await openBusinessAccount();
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open a business account.');
    } finally {
      setOpening(false);
    }
  };

  if (wallet === undefined) {
    return <div className="toss-card skeleton" style={{ height: '160px' }} />;
  }

  if (wallet === null) {
    return (
      <div className="toss-card" style={{ maxWidth: '480px' }}>
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Business account</h2>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '14px' }}>
          Keep your business money separate from your personal wallet. Your real card/QR collections still settle
          to your personal wallet as before — move money into your business account whenever you're ready to set it aside.
        </p>
        {error && <p style={{ fontSize: '13px', color: 'var(--toss-red)', marginBottom: '12px' }} role="alert">{error}</p>}
        <button className="toss-btn toss-btn-primary" onClick={handleOpen} disabled={opening}>
          {opening ? 'Opening…' : 'Open business account'}
        </button>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div className="toss-card">
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Business balance</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{wallet.balance.toLocaleString()} RWF</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{wallet.accountNumber}</p>
      </div>

      <MoveMoneyCard onMoved={load} />

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Business transactions</h3>
        {// Real copy-voice fix (item 244, round 6 of the empty-state pass): matches
        // the same pattern as WalletTransactionsView's own already-shipped fix --
        // transactions are auto-recorded, not user-initiated setup.
        transactions.length === 0 ? (
          <EmptyState message="No business transactions yet — once you send or receive money, it'll show up here." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {transactions.map((entry) => (
              <div key={entry.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '10px' }}>
                <div>
                  <p style={{ fontSize: '13px' }}>{entry.memo}</p>
                  <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{new Date(entry.createdAt).toLocaleDateString()}</p>
                </div>
                <p style={{ fontSize: '13px', fontWeight: 700, whiteSpace: 'nowrap' }}>
                  {entry.direction === 'CREDIT' ? '+' : '-'}{entry.amount.toLocaleString()} RWF
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function MoveMoneyCard({ onMoved }: { onMoved: () => void }) {
  const [amount, setAmount] = useState('');
  const [moving, setMoving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const move = async (direction: 'TO_BUSINESS' | 'TO_PERSONAL') => {
    const value = Number(amount);
    if (!value || value <= 0) {
      setError('Enter a real amount.');
      return;
    }
    setError(null);
    setNeedsDeviceVerification(false);
    setMoving(true);
    try {
      await (direction === 'TO_BUSINESS' ? moveToBusinessAccount(value) : moveToPersonalAccount(value));
      setAmount('');
      onMoved();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : "Couldn't move this money. Check your balance.");
      }
    } finally {
      setMoving(false);
    }
  };

  if (needsDeviceVerification) {
    return (
      <div className="toss-card">
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      </div>
    );
  }

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Move money</h3>
      <input
        type="number"
        min="1"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        placeholder="Amount (RWF)"
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
      />
      {error && <p style={{ fontSize: '13px', color: 'var(--toss-red)', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={moving} onClick={() => move('TO_BUSINESS')}>
          To business
        </button>
        <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={moving} onClick={() => move('TO_PERSONAL')}>
          To personal
        </button>
      </div>
    </div>
  );
}
