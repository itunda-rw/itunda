import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowLeft, ArrowUpRight, LogOut, MessageCircle, Plus, ScanFace, Send, ShieldCheck, ShoppingBag, Wallet as WalletIcon } from 'lucide-react';
import { getStoredUser, logout, ApiError } from './lib/api';
import { fetchTransactions, fetchWallets, type Transaction, type Wallet } from './lib/wallet';
import { getMyCertificate, issueCertificate, revokeCertificate, type Certificate } from './lib/certificate';
import { collectPayment, fetchShoppingCatalog, type CollectPaymentResult, type ShoppingMerchant } from './lib/shopping';
import { fetchConversations, fetchMessages, sendMessage, startConversation, type ConversationSummary, type Message } from './lib/messaging';
import { contactSeller, createListing, fetchListings, fetchMyListings, markListingSold, removeListing, type Listing } from './lib/marketplace';

type Tab = 'HOME' | 'CERTIFICATE' | 'SHOPPING' | 'MESSAGES' | 'MARKETPLACE';

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
        A digital certificate you can use to sign agreements in Itunda. You'll need a verified identity first.
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
              Your previous certificate was {certificate.status.toLowerCase()}.
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
            Save this private key now — you won't be able to see it again.
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

function PayByCodeCard({ onPaid }: { onPaid: (result: CollectPaymentResult) => void }) {
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await collectPayment(code.trim());
      onPaid(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not complete this payment.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Pay by code</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '14px' }}>
        No scanner handy? Enter the payment code the merchant shows you to pay instantly and earn cashback.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="text"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="Payment code"
          required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Paying…' : 'Pay'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

function PaymentConfirmation({ result, onDone }: { result: CollectPaymentResult; onDone: () => void }) {
  return (
    <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
      <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
      <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>Paid {result.merchantName}</h3>
      <p style={{ fontSize: '22px', fontWeight: 700, marginBottom: '4px' }}>{result.amount.toLocaleString()} RWF</p>
      {result.cashbackEarned > 0 && (
        <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-green)', marginBottom: '16px' }}>
          +{result.cashbackEarned.toLocaleString()} RWF cashback earned
        </p>
      )}
      <button className="toss-btn toss-btn-secondary" onClick={onDone} style={{ marginTop: '8px' }}>Done</button>
    </div>
  );
}

function ShoppingView() {
  const [merchants, setMerchants] = useState<ShoppingMerchant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paymentResult, setPaymentResult] = useState<CollectPaymentResult | null>(null);

  const load = () => {
    setError(null);
    fetchShoppingCatalog()
      .then(setMerchants)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the shopping catalog.'));
  };

  useEffect(load, []);

  if (paymentResult) {
    return <PaymentConfirmation result={paymentResult} onDone={() => setPaymentResult(null)} />;
  }

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
      <PayByCodeCard onPaid={setPaymentResult} />
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px', padding: '0 4px' }}>
        Earn cashback every time you shop with Itunda merchants.
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
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Pay by QR or code to earn cashback</p>
              </div>
              <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-green)' }}>{m.cashbackRate} back</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function NewChatCard({ onStarted }: { onStarted: (conversationId: string) => void }) {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const conversation = await startConversation(phoneNumber.trim());
      setPhoneNumber('');
      onStarted(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start this chat.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>New chat</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '14px' }}>
        Enter their phone number to start a conversation.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="tel"
          value={phoneNumber}
          onChange={(e) => setPhoneNumber(e.target.value)}
          placeholder="+250788123456"
          required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Starting…' : 'Chat'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

function ConversationThread({ conversation, onBack }: { conversation: ConversationSummary; onBack: () => void }) {
  const [messages, setMessages] = useState<Message[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const load = () =>
    fetchMessages(conversation.conversationId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this conversation.'));

  useEffect(() => {
    load();
    // Real poll-based "live" delivery -- MessagingService's own doc comment names this
    // as the honest current scope (no WebSocket/push transport yet). 4s comfortably
    // reads as responsive in a real conversation without hammering the backend.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.conversationId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body) return;
    setSending(true);
    setError(null);
    try {
      const sent = await sendMessage(conversation.conversationId, body);
      setMessages((prev) => [...(prev ?? []), sent]);
      setDraft('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this message.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to conversations">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{conversation.otherUserName}</h3>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', padding: '4px' }}>
        {messages === null && <div className="toss-card skeleton" style={{ height: '120px' }} />}
        {messages !== null && messages.length === 0 && (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', textAlign: 'center', marginTop: '20px' }}>
            Say hello — no messages yet.
          </p>
        )}
        {messages?.map((m) => {
          const isMine = m.senderId === currentUser?.id;
          return (
            <div key={m.id} style={{ display: 'flex', justifyContent: isMine ? 'flex-end' : 'flex-start' }}>
              <div
                style={{
                  maxWidth: '75%',
                  padding: '10px 14px',
                  borderRadius: '16px',
                  fontSize: '14px',
                  backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
                  color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
                }}
              >
                {m.body}
              </div>
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>
      )}

      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Message"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={sending || !draft.trim()} style={{ padding: '10px 16px' }}>
          <Send size={16} />
        </button>
      </form>
    </div>
  );
}

function MessagesView({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openConversationId, setOpenConversationId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchConversations()
      .then(setConversations)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your conversations.'));
  };

  useEffect(load, []);

  // Real "jump straight into the chat" hand-off from MarketplaceView's "Message
  // seller" button -- contactSeller() returns a real conversation id (either freshly
  // created or an existing one reused), which this opens directly once it shows up in
  // the real conversation list, rather than making the buyer find it themselves.
  useEffect(() => {
    if (initialConversationId && conversations?.some((c) => c.conversationId === initialConversationId)) {
      setOpenConversationId(initialConversationId);
      onConsumedInitial?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialConversationId, conversations]);

  const openConversation = conversations?.find((c) => c.conversationId === openConversationId);
  if (openConversation) {
    return (
      <ConversationThread
        conversation={openConversation}
        onBack={() => {
          setOpenConversationId(null);
          load();
        }}
      />
    );
  }

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }

  if (conversations === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div>
      <NewChatCard onStarted={(id) => { load(); setOpenConversationId(id); }} />
      {conversations.length === 0 ? (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No conversations yet.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {conversations.map((c) => (
            <button
              key={c.conversationId}
              onClick={() => setOpenConversationId(c.conversationId)}
              className="toss-card"
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <MessageCircle size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{c.otherUserName}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {c.lastMessagePreview ?? 'No messages yet'}
                </p>
              </div>
              {c.unreadCount > 0 && (
                <span
                  style={{
                    fontSize: '11px', fontWeight: 700, color: 'var(--toss-white)', backgroundColor: 'var(--toss-blue)',
                    borderRadius: '10px', padding: '2px 8px', flexShrink: 0,
                  }}
                >
                  {c.unreadCount}
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function NewListingCard({ onCreated }: { onCreated: () => void }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [category, setCategory] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createListing(title, description, Number(price), category);
      setTitle('');
      setDescription('');
      setPrice('');
      setCategory('');
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this listing.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="toss-btn toss-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + List an item
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>List an item</h3>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What are you selling?" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Description" required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" value={price} onChange={(e) => setPrice(e.target.value)} placeholder="Price (RWF)" required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="text" value={category} onChange={(e) => setCategory(e.target.value)} placeholder="Category" required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </div>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Listing…' : 'List it'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function ListingCard({ listing, isMine, onChanged, onMessageSeller }: {
  listing: Listing;
  isMine: boolean;
  onChanged: () => void;
  onMessageSeller: (conversationId: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleMarkSold = async () => {
    setBusy(true);
    setError(null);
    try {
      await markListingSold(listing.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this listing.');
    } finally {
      setBusy(false);
    }
  };

  const handleRemove = async () => {
    setBusy(true);
    setError(null);
    try {
      await removeListing(listing.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this listing.');
    } finally {
      setBusy(false);
    }
  };

  const handleMessage = async () => {
    setBusy(true);
    setError(null);
    try {
      const conversation = await contactSeller(listing.id);
      onMessageSeller(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not message this seller.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
            {listing.title}
            {listing.status === 'SOLD' && (
              <span style={{ marginLeft: '8px', fontSize: '11px', fontWeight: 700, color: 'var(--toss-grey-500)', backgroundColor: 'var(--toss-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
                SOLD
              </span>
            )}
          </p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{listing.category}</p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{listing.price.toLocaleString()} RWF</span>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{listing.description}</p>
      <div style={{ display: 'flex', gap: '8px' }}>
        {isMine ? (
          <>
            {listing.status === 'ACTIVE' && (
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={handleMarkSold}>
                Mark sold
              </button>
            )}
            {listing.status !== 'REMOVED' && (
              <button className="toss-btn toss-btn-danger" style={{ flex: 1 }} disabled={busy} onClick={handleRemove}>
                Remove
              </button>
            )}
          </>
        ) : (
          listing.status === 'ACTIVE' && (
            <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={handleMessage}>
              {busy ? 'Starting…' : 'Message seller'}
            </button>
          )
        )}
      </div>
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

function MarketplaceView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  const [view, setView] = useState<'BROWSE' | 'MINE'>('BROWSE');
  const [listings, setListings] = useState<Listing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const currentUser = getStoredUser();

  const load = () => {
    setError(null);
    setListings(null);
    const fetcher = view === 'BROWSE' ? fetchListings() : fetchMyListings();
    fetcher
      .then(setListings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load listings.'));
  };

  useEffect(load, [view]);

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'MINE'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Browse' : 'My listings'}
          </button>
        ))}
      </div>

      {view === 'MINE' && <NewListingCard onCreated={load} />}

      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
          <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
        </div>
      )}
      {!error && listings === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
      {!error && listings !== null && listings.length === 0 && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            {view === 'BROWSE' ? 'No listings yet.' : "You haven't listed anything yet."}
          </p>
        </div>
      )}
      {!error && listings !== null && listings.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {listings.map((listing) => (
            <ListingCard
              key={listing.id}
              listing={listing}
              isMine={view === 'MINE' || listing.sellerId === currentUser?.id}
              onChanged={load}
              onMessageSeller={onMessageSeller}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function BankDashboard({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('HOME');
  const [pendingConversationId, setPendingConversationId] = useState<string | null>(null);
  const user = getStoredUser();

  const handleLogout = () => {
    logout();
    onLogout();
  };

  // Real "message seller" hand-off from MarketplaceView: switches straight to the
  // Messages tab with that real conversation already open, rather than dropping the
  // buyer on a conversation list they'd have to search through themselves.
  const handleMessageSeller = (conversationId: string) => {
    setPendingConversationId(conversationId);
    setTab('MESSAGES');
  };

  const TABS: { id: Tab; label: string }[] = [
    { id: 'HOME', label: 'Home' },
    { id: 'MESSAGES', label: 'Messages' },
    { id: 'MARKETPLACE', label: 'Marketplace' },
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
      {tab === 'MESSAGES' && (
        <MessagesView
          initialConversationId={pendingConversationId}
          onConsumedInitial={() => setPendingConversationId(null)}
        />
      )}
      {tab === 'MARKETPLACE' && <MarketplaceView onMessageSeller={handleMessageSeller} />}
      {tab === 'CERTIFICATE' && <CertificateView />}
      {tab === 'SHOPPING' && <ShoppingView />}
    </div>
  );
}
