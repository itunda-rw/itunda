import { Suspense, useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowUpRight, Wallet, ShieldCheck, ChevronRight, Plus, ScanFace } from 'lucide-react';

// Mock API Calls
const fetchBalance = async () => {
  return new Promise<{ balance: number; currency: string }>((resolve) => {
    setTimeout(() => resolve({ balance: 543000, currency: 'RWF' }), 800);
  });
};

const fetchTransactions = async () => {
  return new Promise<Array<{ id: string; title: string; amount: number; type: 'in' | 'out'; date: string; icon: string }>>((resolve) => {
    setTimeout(() => {
      resolve([
        { id: '1', title: 'MTN Mobile Money Transfer', amount: 50000, type: 'in', date: 'Today, 14:30', icon: '📱' },
        { id: '2', title: 'Irembo Services', amount: -5000, type: 'out', date: 'Yesterday', icon: '🏛️' },
        { id: '3', title: 'Coffee Shop Kigali', amount: -3500, type: 'out', date: 'Yesterday', icon: '☕' },
        { id: '4', title: 'Salary Deposit', amount: 750000, type: 'in', date: 'Jul 5', icon: '💼' },
      ]);
    }, 1000);
  });
};

// Components
function AccountBalance() {
  const [data, setData] = useState<{ balance: number; currency: string } | null>(null);

  useEffect(() => {
    fetchBalance().then(setData);
  }, []);

  if (!data) throw new Promise(() => {});

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: "easeOut" }}
      className="toss-card"
      style={{ padding: '28px', position: 'relative', overflow: 'hidden' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
        <p style={{ color: 'var(--toss-grey-700)', fontSize: '15px', fontWeight: '600' }}>Itunda Digital Savings</p>
        <ShieldCheck size={20} color="var(--toss-green)" />
      </div>
      
      <h1 style={{ color: 'var(--toss-grey-900)', fontSize: '36px', fontWeight: '700', margin: '0 0 28px 0', letterSpacing: '-0.5px', display: 'flex', alignItems: 'baseline', gap: '4px' }}>
        {data.balance.toLocaleString()} <span style={{ fontSize: '20px', color: 'var(--toss-grey-500)', fontWeight: '600' }}>{data.currency}</span>
      </h1>
      
      <div style={{ display: 'flex', gap: '12px' }}>
        <motion.button 
          whileTap={{ scale: 0.96 }}
          className="toss-btn toss-btn-primary"
          style={{ flex: 1, gap: '8px' }}
        >
          <ArrowUpRight size={18} /> Transfer
        </motion.button>
        <motion.button 
          whileTap={{ scale: 0.96 }}
          className="toss-btn toss-btn-secondary"
          style={{ flex: 1, gap: '8px' }}
        >
          <Plus size={18} /> Top up
        </motion.button>
      </div>
    </motion.div>
  );
}

function QuickActions() {
  const actions = [
    { title: 'Scan to Pay', icon: <ScanFace size={24} color="var(--toss-blue)" />, bg: 'var(--toss-blue-light)' },
    { title: 'Cards', icon: <Wallet size={24} color="#8A2BE2" />, bg: 'rgba(138, 43, 226, 0.1)' },
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

function TransactionHistory() {
  const [txs, setTxs] = useState<any[] | null>(null);

  useEffect(() => {
    fetchTransactions().then(setTxs);
  }, []);

  if (!txs) throw new Promise(() => {});

  return (
    <motion.div 
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.1, ease: "easeOut" }}
      className="toss-card" 
      style={{ padding: '24px 20px' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', padding: '0 4px' }}>
        <h3 style={{ color: 'var(--toss-grey-900)', margin: 0, fontSize: '18px', fontWeight: '700' }}>Recent Activity</h3>
        <button style={{ color: 'var(--toss-grey-500)', fontSize: '14px', fontWeight: '600', display: 'flex', alignItems: 'center' }}>
          View all <ChevronRight size={16} />
        </button>
      </div>
      
      <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
        <AnimatePresence>
          {txs.map((tx, idx) => (
            <motion.div 
              key={tx.id} 
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: idx * 0.1 }}
              style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', cursor: 'pointer', padding: '4px' }}
              whileTap={{ scale: 0.98, opacity: 0.8 }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '20px' }}>
                  {tx.icon}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                  <span style={{ color: 'var(--toss-grey-900)', fontWeight: '600', fontSize: '16px' }}>{tx.title}</span>
                  <span style={{ color: 'var(--toss-grey-500)', fontSize: '13px', fontWeight: '500' }}>{tx.date}</span>
                </div>
              </div>
              <span style={{ fontWeight: '700', fontSize: '16px', color: tx.type === 'in' ? 'var(--toss-blue)' : 'var(--toss-grey-900)' }}>
                {tx.type === 'in' ? '+' : ''}{tx.amount.toLocaleString()} RWF
              </span>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </motion.div>
  );
}

// Fallbacks
const BalanceSkeleton = () => (
  <div className="toss-card skeleton" style={{ height: '180px', marginBottom: '16px' }} />
);

const TransactionSkeleton = () => (
  <div className="toss-card skeleton" style={{ height: '300px' }} />
);

export default function BankDashboard() {
  return (
    <div style={{ padding: '20px', paddingBottom: '100px' }}>
      
      {/* Header */}
      <motion.div 
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', padding: '0 8px' }}
      >
        <h2 style={{ color: 'var(--toss-grey-900)', margin: 0, fontSize: '24px', fontWeight: '700', letterSpacing: '-0.5px' }}>Itunda</h2>
        <motion.div 
          whileTap={{ scale: 0.9 }}
          style={{ width: '40px', height: '40px', backgroundColor: 'var(--toss-grey-200)', borderRadius: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--toss-grey-700)', fontWeight: 'bold', fontSize: '18px', cursor: 'pointer' }}
        >
          K
        </motion.div>
      </motion.div>

      {/* Main Account */}
      <Suspense fallback={<BalanceSkeleton />}>
        <AccountBalance />
      </Suspense>

      {/* Quick Actions */}
      <QuickActions />

      {/* Transactions Section */}
      <Suspense fallback={<TransactionSkeleton />}>
        <TransactionHistory />
      </Suspense>
    </div>
  );
}
