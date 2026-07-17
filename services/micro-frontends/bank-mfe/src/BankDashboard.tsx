import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowUpRight, LogOut, Plus, ScanFace, ShieldCheck, ShoppingBag, Wallet as WalletIcon } from 'lucide-react';
import { getStoredUser, logout, ApiError } from './lib/api';
import { fetchTransactions, fetchWallets, type Transaction, type Wallet } from './lib/wallet';
import { getMyCertificate, issueCertificate, revokeCertificate, type Certificate } from './lib/certificate';
import { fetchShoppingCatalog, type ShoppingMerchant } from './lib/shopping';

type Tab = 'HOME' | 'CERTIFICATE' | 'SHOPPING';

function AccountBalance({ wallet }: { wallet: Wallet | null }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: 'easeOut' }}
      className="toss-card"
      style={{ padding: '28px', position: 'relative', overflow: 'hidden' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
        <p style={{ color: 'var(--toss-grey-700)', fontSize: '15px', fontWeight: '600' }}>{wallet?.accountName ?? 'Main Account'}</p>
        <ShieldCheck size={20} color="var(--toss-green)" />
      </div>

      <h1 style={{ color: 'var(--toss-grey-900)', fontSize: '36px', fontWeight: '700', margin: '0 0 28px 0', letterSpacing: '-0.5px', display: 'flex', alignItems: 'baseline', gap: '4px' }}>
        {(wallet?.balance ?? 0).toLocaleString()} <span style={{ fontSize: '20px', color: 'var(--toss-grey-500)', fontWeight: '600' }}>{wallet?.currency ?? 'RWF'}</span>
      </h1>

      <div style={{ display: 'flex', gap: '12px' }}>
        <motion.button whileTap={{ scale: 0.96 }} className="toss-btn toss-btn-primary" style={{ flex: 1, gap: '8px' }}>
          <ArrowUpRight size={18} /> Transfer
        </motion.button>
        <motion.button whileTap={{ scale: 0.96 }} className="toss-btn toss-btn-secondary" style={{ flex: 1, gap: '8px' }}>
          <Plus size={18} /> Top up
        </motion.button>
      </div>
    </motion.div>
  );
}

function QuickActions() {
  const actions = [
    { title: 'Scan to Pay', icon: <ScanFace size={24} color="var(--toss-blue)" />, bg: 'var(--toss-blue-light)' },
    { title: 'Cards', icon: <WalletIcon size={24} color="#8A2BE2" />, bg: 'rgba(138, 43, 226, 0.1)' },
  ];

  return (
    <div style={{ display: 'flex', gap: '12px', marginBottom: '16px' }}>
      {actions.map((action, i) => (
        <motion.div
          key={i}
          whileTap={{ scale: 0.96 }}
          className="toss-card"
          style={{ flex: 1, padding: '20px', margin: 0, display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '16px', cursor: 'pointer' }}
        >
          <div style={{ width: '48px', height: '48px', borderRadius: '16px', backgroundColor: action.bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {action.icon}
          </div>
          <span style={{ fontWeight: '600', fontSize: '15px', color: 'var(--toss-grey-900)' }}>{action.title}</span>
        </motion.div>
      ))}
    </div>
  );
}

function TransactionHistory({ transactions }: { transactions: Transaction[] }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.1, ease: 'easeOut' }}
      className="toss-card"
      style={{ padding: '24px 20px' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', padding: '0 4px' }}>
        <h3 style={{ color: 'var(--toss-grey-900)', margin: 0, fontSize: '18px', fontWeight: '700' }}>Recent Activity</h3>
      </div>

      {transactions.length === 0 ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', padding: '0 4px' }}>No transactions yet.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <AnimatePresence>
            {transactions.slice(0, 10).map((tx, idx) => {
              const isCredit = tx.channel === 'CASHBACK' || tx.type === 'DEPOSIT';
              return (
                <motion.div
                  key={tx.id}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px' }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '13px', fontWeight: 700, color: 'var(--toss-grey-500)' }}>
                      {tx.channel ? tx.channel.slice(0, 2) : tx.type.slice(0, 2)}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span style={{ color: 'var(--toss-grey-900)', fontWeight: '600', fontSize: '16px' }}>{tx.description}</span>
                      <span style={{ color: 'var(--toss-grey-500)', fontSize: '13px', fontWeight: '500' }}>{new Date(tx.createdAt).toLocaleString()}</span>
                    </div>
                  </div>
                  <span style={{ fontWeight: '700', fontSize: '16px', color: isCredit ? 'var(--toss-blue)' : 'var(--toss-grey-900)' }}>
                    {isCredit ? '+' : ''}{tx.amount.toLocaleString()} RWF
                  </span>
                </motion.div>
              );
            })}
          </AnimatePresence>
        </div>
      )}
    </motion.div>
  );
}

function HomeView() {
  const [wallet, setWallet] = useState<Wallet | null | undefined>(undefined);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    Promise.all([fetchWallets(), fetchTransactions()])
      .then(([wallets, txs]) => {
        setWallet(wallets.find((w) => w.type === 'MAIN') ?? wallets[0] ?? null);
        setTransactions(txs);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your account.'));
  };

  useEffect(load, []);

  if (error) {
    return (
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }

  if (wallet === undefined) {
    return (
      <div>
        <div className="toss-card skeleton" style={{ height: '180px', marginBottom: '16px' }} />
        <div className="toss-card skeleton" style={{ height: '300px' }} />
      </div>
    );
  }

  return (
    <div>
      <AccountBalance wallet={wallet} />
      <QuickActions />
      <TransactionHistory transactions={transactions} />
    </div>
  );
}

function CertificateView() {
  const [certificate, setCertificate] = useState<Certificate | null | undefined>(undefined);
  const [issuedPrivateKey, setIssuedPrivateKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    setError(null);
    getMyCertificate()
      .then(setCertificate)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your certificate.'));
  };

  useEffect(load, []);

  const handleIssue = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await issueCertificate();
      setCertificate(result.certificate);
      setIssuedPrivateKey(result.privateKey);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not issue a certificate.');
    } finally {
      setBusy(false);
    }
  };

  const handleRevoke = async () => {
    setBusy(true);
    setError(null);
    try {
      const revoked = await revokeCertificate();
      setCertificate(revoked);
      setIssuedPrivateKey(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not revoke your certificate.');
    } finally {
      setBusy(false);
    }
  };

  if (certificate === undefined) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div className="toss-card" style={{ padding: '28px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px' }}>
        <ShieldCheck size={22} color={certificate?.status === 'ACTIVE' ? 'var(--toss-green)' : 'var(--toss-grey-500)'} />
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Itunda Certificate</h2>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '20px' }}>
        A real digital signing certificate, usable to sign real agreements inside itunda -- the same real shape as
        Toss's own certificate, requiring your identity to already be verified.
      </p>

      {certificate && certificate.status === 'ACTIVE' ? (
        <div>
          <p style={{ fontSize: '13px', color: 'var(--toss-green)', fontWeight: 700, marginBottom: '8px' }}>Active</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', fontFamily: 'monospace', marginBottom: '4px' }}>
            Serial {certificate.serialNumber}
          </p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '20px' }}>
            Expires {new Date(certificate.expiresAt).toLocaleDateString()}
          </p>
          <button className="toss-btn toss-btn-secondary" onClick={handleRevoke} disabled={busy}>
            {busy ? 'Revoking…' : 'Revoke certificate'}
          </button>
        </div>
      ) : (
        <div>
          {certificate && (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
              Previous certificate {certificate.status.toLowerCase()}.
            </p>
          )}
          <button className="toss-btn toss-btn-primary" onClick={handleIssue} disabled={busy}>
            {busy ? 'Issuing…' : 'Issue a certificate'}
          </button>
        </div>
      )}

      {issuedPrivateKey && (
        <div style={{ marginTop: '20px', padding: '16px', borderRadius: '12px', backgroundColor: '#FFF4E5' }}>
          <p style={{ fontSize: '13px', fontWeight: 700, color: '#B25E09', marginBottom: '6px' }}>
            Save this private key now -- it will never be shown again.
          </p>
          <p style={{ fontSize: '11px', fontFamily: 'monospace', wordBreak: 'break-all', color: '#B25E09' }}>{issuedPrivateKey}</p>
        </div>
      )}

      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginTop: '16px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

function ShoppingView() {
  const [merchants, setMerchants] = useState<ShoppingMerchant[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchShoppingCatalog()
      .then(setMerchants)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the shopping catalog.'));
  };

  useEffect(load, []);

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }

  if (merchants === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px', padding: '0 4px' }}>
        Real cashback on purchases at itunda's own registered merchants.
      </p>
      {merchants.length === 0 ? (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No merchants registered yet.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {merchants.map((m) => (
            <div key={m.merchantId} className="toss-card" style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px' }}>
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <ShoppingBag size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{m.businessName}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Pay by QR in-store to earn cashback</p>
              </div>
              <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-green)' }}>{m.cashbackRate} back</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default function BankDashboard({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('HOME');
  const user = getStoredUser();

  const handleLogout = () => {
    logout();
    onLogout();
  };

  const TABS: { id: Tab; label: string }[] = [
    { id: 'HOME', label: 'Home' },
    { id: 'CERTIFICATE', label: 'Certificate' },
    { id: 'SHOPPING', label: 'Shopping' },
  ];

  return (
    <div style={{ padding: '20px', paddingBottom: '100px', maxWidth: '480px', margin: '0 auto' }}>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', padding: '0 8px' }}
      >
        <h2 style={{ color: 'var(--toss-grey-900)', margin: 0, fontSize: '24px', fontWeight: '700', letterSpacing: '-0.5px' }}>Itunda</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {user && <span style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{user.firstName}</span>}
          <button onClick={handleLogout} style={{ color: 'var(--toss-grey-500)', display: 'flex' }} aria-label="Sign out">
            <LogOut size={18} />
          </button>
        </div>
      </motion.div>

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: tab === id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: tab === id ? 'var(--toss-blue)' : 'transparent',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'HOME' && <HomeView />}
      {tab === 'CERTIFICATE' && <CertificateView />}
      {tab === 'SHOPPING' && <ShoppingView />}
    </div>
  );
}
