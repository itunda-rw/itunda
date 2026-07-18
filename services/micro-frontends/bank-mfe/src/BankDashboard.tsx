import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowLeft, ArrowUpRight, Bike, Heart, LogOut, MessageCircle, Plus, ScanFace, Send, ShieldCheck, ShoppingBag, Star, Users, Utensils, Wallet as WalletIcon } from 'lucide-react';
import { getStoredUser, logout, ApiError } from './lib/api';
import { fetchTransactions, fetchWallets, type Transaction, type Wallet } from './lib/wallet';
import { getMyCertificate, issueCertificate, revokeCertificate, type Certificate } from './lib/certificate';
import { collectPayment, fetchShoppingCatalog, type CollectPaymentResult, type ShoppingMerchant } from './lib/shopping';
import {
  connectMessagingSocket, createGroup, fetchConversations, fetchGroupMembers, fetchGroupMessages, fetchGroups, fetchMessages,
  sendGroupMessage, sendMessage, startConversation, type ConversationSummary, type GroupMember, type GroupMessage,
  type GroupSummary, type Message,
} from './lib/messaging';
import { contactSeller, createListing, fetchListings, fetchMyListings, markListingSold, removeListing, type Listing } from './lib/marketplace';
import {
  addFavoriteRestaurant, advanceRestaurantOrder, advanceRiderOrder, cancelEatsOrder, claimDelivery, fetchAvailableDeliveries,
  fetchEatsOrder, fetchMenu, fetchMyEatsOrders, fetchMyFavoriteRestaurants, fetchMyRiderProfile, fetchRestaurantCategories,
  fetchRestaurantOrders, fetchRestaurants, fetchRestaurantRating, fetchRiderDeliveries, placeEatsOrder, registerRider,
  removeFavoriteRestaurant, searchDeliveryAddress, setRiderAvailability, submitEatsReview,
  type AddressSuggestion, type EatsOrder, type EatsOrderStatus, type FavoriteRestaurant, type MenuItem, type RatingSummary, type Rider,
} from './lib/eats';
import {
  advanceOrderStatus, cancelOrder, fetchMerchantOrders, fetchMerchantProducts, fetchMyOrders, placeOrder,
  type CommerceOrder, type CommerceOrderStatus, type CommerceProduct,
} from './lib/commerce';

type Tab = 'HOME' | 'CERTIFICATE' | 'SHOPPING' | 'SHOP' | 'MESSAGES' | 'MARKETPLACE' | 'EATS';

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

function NewGroupCard({ onCreated }: { onCreated: (groupId: string) => void }) {
  const [name, setName] = useState('');
  const [phoneNumbers, setPhoneNumbers] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const numbers = phoneNumbers.split(',').map((n) => n.trim()).filter(Boolean);
    if (numbers.length === 0) {
      setError('Enter at least one phone number, separated by commas.');
      return;
    }
    setSubmitting(true);
    try {
      const group = await createGroup(name.trim(), numbers);
      setName('');
      setPhoneNumbers('');
      onCreated(group.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this group.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>New group</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
        Name your group and add real members by phone number, separated by commas.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Group name"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="text"
          value={phoneNumbers}
          onChange={(e) => setPhoneNumbers(e.target.value)}
          placeholder="+250788123456, +250788654321"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create group'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
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
    // Real 4s poll as an always-correct fallback (kept even now that a live socket
    // exists below -- if the socket never connects, silently errors, or the server
    // restarts mid-conversation, this alone still delivers messages correctly, just
    // slower). See connectMessagingSocket's own doc comment for why it's designed as
    // a latency improvement layered on top of this, not a replacement for it.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.conversationId]);

  useEffect(() => {
    // Real WebSocket live delivery (2026-07-18) -- appends a pushed message straight
    // into state the moment it arrives, rather than waiting for the next poll tick.
    // De-duped by id since the next 4s poll will also fetch the same message.
    const disconnect = connectMessagingSocket((payload) => {
      if (payload.type !== 'message' || payload.conversationId !== conversation.conversationId) return;
      setMessages((prev) => {
        if (!prev) return prev;
        if (prev.some((m) => m.id === payload.message.id)) return prev;
        return [...prev, payload.message];
      });
    });
    return disconnect;
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

function GroupThread({ group, onBack }: { group: GroupSummary; onBack: () => void }) {
  const [messages, setMessages] = useState<GroupMessage[] | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);

  const load = () =>
    fetchGroupMessages(group.groupId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this group.'));

  useEffect(() => {
    load();
    // Real 4s poll as an always-correct fallback, same reasoning as ConversationThread's
    // own identical poll -- kept even with the live socket below.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group.groupId]);

  useEffect(() => {
    // Real member list with real resolved display names (2026-07-18), fetched once per
    // thread open -- closes the honest, named limitation this UI carried since group
    // chat first shipped (a truncated sender id instead of a real name).
    fetchGroupMembers(group.groupId).then(setMembers).catch(() => {
      // Real, non-critical -- a failed member-list fetch shouldn't block the thread;
      // bubbles just fall back to a truncated sender id below.
    });
  }, [group.groupId]);

  const nameForSender = (senderId: string) => members.find((m) => m.userId === senderId)?.name ?? senderId.slice(0, 12);

  useEffect(() => {
    // Real WebSocket live delivery for group chat (2026-07-18) -- same real push
    // GroupMessagingService.sendMessage fans out to every other real member.
    const disconnect = connectMessagingSocket((payload) => {
      if (payload.type !== 'group_message' || payload.groupConversationId !== group.groupId) return;
      setMessages((prev) => {
        if (!prev) return prev;
        if (prev.some((m) => m.id === payload.message.id)) return prev;
        return [...prev, payload.message];
      });
    });
    return disconnect;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group.groupId]);

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
      const sent = await sendGroupMessage(group.groupId, body);
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
        <div>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{group.name}</h3>
          <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{group.memberCount} members</p>
        </div>
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
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {!isMine && (
                <span style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginBottom: '2px', marginLeft: '4px' }}>
                  {nameForSender(m.senderId)}
                </span>
              )}
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

function DirectMessagesList({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
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

function GroupsList() {
  const [groups, setGroups] = useState<GroupSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openGroupId, setOpenGroupId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchGroups()
      .then(setGroups)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your groups.'));
  };

  useEffect(load, []);

  const openGroup = groups?.find((g) => g.groupId === openGroupId);
  if (openGroup) {
    return (
      <GroupThread
        group={openGroup}
        onBack={() => {
          setOpenGroupId(null);
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

  if (groups === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div>
      <NewGroupCard onCreated={(id) => { load(); setOpenGroupId(id); }} />
      {groups.length === 0 ? (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No groups yet.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {groups.map((g) => (
            <button
              key={g.groupId}
              onClick={() => setOpenGroupId(g.groupId)}
              className="toss-card"
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Users size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{g.name} · {g.memberCount}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {g.lastMessagePreview ?? 'No messages yet'}
                </p>
              </div>
              {g.unreadCount > 0 && (
                <span
                  style={{
                    fontSize: '11px', fontWeight: 700, color: 'var(--toss-white)', backgroundColor: 'var(--toss-blue)',
                    borderRadius: '10px', padding: '2px 8px', flexShrink: 0,
                  }}
                >
                  {g.unreadCount}
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// Real group chat (2026-07-18) folded in via a Direct/Groups toggle -- the single most
// defining KakaoTalk capability the original 1:1-only Messages tab didn't cover, added
// at the user's direct request. See GroupMessagingService.kt's own doc comment.
function MessagesView({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
  const [mode, setMode] = useState<'DIRECT' | 'GROUPS'>('DIRECT');

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['DIRECT', 'GROUPS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: mode === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: mode === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'DIRECT' ? 'Direct' : 'Groups'}
          </button>
        ))}
      </div>
      {mode === 'DIRECT' ? (
        <DirectMessagesList initialConversationId={initialConversationId} onConsumedInitial={onConsumedInitial} />
      ) : (
        <GroupsList />
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

const EATS_STATUS_LABEL: Record<EatsOrderStatus, string> = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted by restaurant',
  PREPARING: 'Preparing',
  READY_FOR_PICKUP: 'Ready for pickup',
  RIDER_ASSIGNED: 'Rider on the way to restaurant',
  PICKED_UP: 'Picked up — on the way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled — refunded',
};

const RESTAURANT_STATUS_CHAIN: EatsOrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP'];
const RIDER_STATUS_CHAIN: EatsOrderStatus[] = ['RIDER_ASSIGNED', 'PICKED_UP', 'DELIVERED'];

function nextInChain<T>(chain: T[], current: T): T | null {
  const idx = chain.indexOf(current);
  return idx >= 0 && idx + 1 < chain.length ? chain[idx + 1] : null;
}

function EatsOrderCard({ order, action }: { order: EatsOrder; action?: React.ReactNode }) {
  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>{EATS_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {order.deliveryNotes && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)', backgroundColor: 'var(--toss-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
          Note: {order.deliveryNotes}
        </p>
      )}
      {action}
    </div>
  );
}

function StarRatingInput({ value, onChange }: { value: number; onChange: (rating: number) => void }) {
  return (
    <div style={{ display: 'flex', gap: '4px' }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" onClick={() => onChange(n)} style={{ display: 'flex', padding: 0 }} aria-label={`${n} star${n === 1 ? '' : 's'}`}>
          <Star size={22} color={n <= value ? '#F5A623' : 'var(--toss-grey-200)'} fill={n <= value ? '#F5A623' : 'none'} />
        </button>
      ))}
    </div>
  );
}

function RestaurantRatingBadge({ restaurantId }: { restaurantId: string }) {
  const [rating, setRating] = useState<RatingSummary | null>(null);

  useEffect(() => {
    fetchRestaurantRating(restaurantId).then(setRating).catch(() => {
      // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
    });
  }, [restaurantId]);

  if (!rating || rating.count === 0) return null;
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', color: 'var(--toss-grey-700)' }}>
      <Star size={14} color="#F5A623" fill="#F5A623" />
      {rating.average?.toFixed(1)} ({rating.count})
    </span>
  );
}

function ReviewOrderCard({ order, onSubmitted }: { order: EatsOrder; onSubmitted: () => void }) {
  const [open, setOpen] = useState(false);
  const [restaurantRating, setRestaurantRating] = useState(0);
  const [restaurantComment, setRestaurantComment] = useState('');
  const [riderRating, setRiderRating] = useState(0);
  const [riderComment, setRiderComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (restaurantRating === 0 || riderRating === 0) {
      setError('Rate both the restaurant and the rider.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await submitEatsReview(order.id, restaurantRating, restaurantComment, riderRating, riderComment);
      setDone(true);
      onSubmitted();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'ORDER_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not submit this review.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Thanks for your review!</p>;
  }

  if (!open) {
    return (
      <button className="toss-btn toss-btn-secondary" onClick={() => setOpen(true)}>
        Rate this order
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '8px' }}>
      <div>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px' }}>Restaurant</p>
        <StarRatingInput value={restaurantRating} onChange={setRestaurantRating} />
        <input
          type="text"
          value={restaurantComment}
          onChange={(e) => setRestaurantComment(e.target.value)}
          placeholder="How was the food? (optional)"
          style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        />
      </div>
      <div>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px' }}>Rider</p>
        <StarRatingInput value={riderRating} onChange={setRiderRating} />
        <input
          type="text"
          value={riderComment}
          onChange={(e) => setRiderComment(e.target.value)}
          placeholder="How was the delivery? (optional)"
          style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        />
      </div>
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </form>
  );
}

function AddressAutocomplete({
  value, onChangeText, onSelectSuggestion,
}: {
  value: string;
  onChangeText: (text: string) => void;
  onSelectSuggestion: (suggestion: AddressSuggestion) => void;
}) {
  const [suggestions, setSuggestions] = useState<AddressSuggestion[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const handleChange = (text: string) => {
    onChangeText(text);
    setOpen(false);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const trimmed = text.trim();
    if (trimmed.length < 3) {
      setSuggestions([]);
      return;
    }
    // Real self-hosted Nominatim search, debounced so a full sentence of typing
    // doesn't fire a request per keystroke -- see EatsController's own doc comment.
    debounceRef.current = setTimeout(() => {
      setSearching(true);
      searchDeliveryAddress(trimmed)
        .then((results) => {
          setSuggestions(results);
          setOpen(results.length > 0);
        })
        .catch(() => {
          // Real, non-critical -- a failed suggestion fetch shouldn't block typing a
          // plain address; the order still places, just without a confirmed pin.
          setSuggestions([]);
        })
        .finally(() => setSearching(false));
    }, 400);
  };

  return (
    <div style={{ position: 'relative' }}>
      <input
        type="text" value={value} onChange={(e) => handleChange(e.target.value)}
        onFocus={() => setOpen(suggestions.length > 0)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder="Delivery address" required autoComplete="off"
        style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      {searching && (
        <span style={{ position: 'absolute', right: '12px', top: '12px', fontSize: '12px', color: 'var(--toss-grey-500)' }}>…</span>
      )}
      {open && (
        <div
          className="toss-card"
          style={{ position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '4px', padding: '6px', zIndex: 10, maxHeight: '220px', overflowY: 'auto' }}
        >
          {suggestions.map((s, i) => (
            <button
              key={i}
              type="button"
              onMouseDown={() => { onSelectSuggestion(s); setOpen(false); }}
              style={{ display: 'block', width: '100%', textAlign: 'left', padding: '8px 6px', fontSize: '13px', borderRadius: '6px' }}
            >
              {s.displayName}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function MenuView({
  restaurant, onBack, onOrderPlaced, initialCart,
}: {
  restaurant: ShoppingMerchant; onBack: () => void; onOrderPlaced: (order: EatsOrder) => void; initialCart?: Record<string, number>;
}) {
  const [menu, setMenu] = useState<{ businessName: string; products: MenuItem[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cart, setCart] = useState<Record<string, number>>(initialCart ?? {});
  const [address, setAddress] = useState('');
  const [addressCoords, setAddressCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [deliveryNotes, setDeliveryNotes] = useState('');
  const [placing, setPlacing] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);

  const load = () => {
    setError(null);
    fetchMenu(restaurant.merchantId)
      .then((r) => setMenu({ businessName: r.merchant.businessName, products: r.products }))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this menu.'));
  };

  useEffect(load, [restaurant.merchantId]);

  const cartItems = Object.entries(cart).filter(([, qty]) => qty > 0);
  const cartCount = cartItems.reduce((sum, [, qty]) => sum + qty, 0);
  const setQty = (id: string, qty: number) => setCart((c) => ({ ...c, [id]: Math.max(0, qty) }));

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!menu) return;
    setPlacing(true);
    setError(null);
    try {
      const items = cartItems.map(([menuItemId, quantity]) => ({ menuItemId, quantity }));
      const result = await placeEatsOrder(
        restaurant.merchantId, items, address.trim(), addressCoords?.latitude, addressCoords?.longitude,
        deliveryNotes.trim() || undefined,
      );
      onOrderPlaced(result.order);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not place this order.');
    } finally {
      setPlacing(false);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }

  if (menu === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  if (showCheckout) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
          <button onClick={() => setShowCheckout(false)} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to menu">
            <ArrowLeft size={20} />
          </button>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Checkout</h3>
        </div>
        <form onSubmit={handlePlaceOrder} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([id, qty]) => {
            const item = menu.products.find((p) => p.id === id);
            if (!item) return null;
            return (
              <div key={id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '14px' }}>
                <span>{item.name} x{qty}</span>
                <span>{(item.price * qty).toLocaleString()} RWF</span>
              </div>
            );
          })}
          <AddressAutocomplete
            value={address}
            onChangeText={(text) => { setAddress(text); setAddressCoords(null); }}
            onSelectSuggestion={(s) => { setAddress(s.displayName); setAddressCoords({ latitude: s.latitude, longitude: s.longitude }); }}
          />
          {addressCoords && (
            <p style={{ fontSize: '12px', color: 'var(--toss-green)' }}>Pinned -- real distance-based delivery fee applies</p>
          )}
          <textarea
            value={deliveryNotes}
            onChange={(e) => setDeliveryNotes(e.target.value.slice(0, 500))}
            placeholder="Delivery notes (optional) -- e.g. Leave at the gate, call on arrival"
            rows={2}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'none', fontFamily: 'inherit' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={placing || !address.trim()}>
            {placing ? 'Placing order…' : 'Place order'}
          </button>
          {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
        </form>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to restaurants">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{menu.businessName}</h3>
      </div>
      <div style={{ marginBottom: '12px' }}>
        <RestaurantRatingBadge restaurantId={restaurant.merchantId} />
      </div>
      {menu.products.length === 0 ? (
        <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No menu items yet.</p></div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: cartCount > 0 ? '80px' : 0 }}>
          {menu.products.map((item) => (
            <div key={item.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontSize: '15px', fontWeight: 700 }}>{item.name}</p>
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{item.price.toLocaleString()} RWF</p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <button onClick={() => setQty(item.id, (cart[item.id] ?? 0) - 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{cart[item.id] ?? 0}</span>
                <button onClick={() => setQty(item.id, (cart[item.id] ?? 0) + 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
              </div>
            </div>
          ))}
        </div>
      )}
      {cartCount > 0 && (
        <button
          className="toss-btn toss-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCheckout(true)}
        >
          Checkout ({cartCount} item{cartCount === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

function MyEatsOrdersView({ onReorder, reorderingId }: { onReorder: (order: EatsOrder) => void; reorderingId: string | null }) {
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyEatsOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your orders.'));
  };

  useEffect(() => {
    load();
    // Real poll for order-tracking status, same 4s cadence as the Messages tab's
    // poll-based delivery -- no live push transport exists here either.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleCancel = async (orderId: string) => {
    setCancellingId(orderId);
    setError(null);
    try {
      await cancelEatsOrder(orderId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this order.');
    } finally {
      setCancellingId(null);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No orders yet.</p></div>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <EatsOrderCard
          key={o.id}
          order={o}
          action={
            o.status === 'PLACED' ? (
              <button className="toss-btn toss-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
                {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
              </button>
            ) : o.status === 'DELIVERED' ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <ReviewOrderCard order={o} onSubmitted={load} />
                <button className="toss-btn toss-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
                  {reorderingId === o.id ? 'Reordering…' : 'Reorder'}
                </button>
              </div>
            ) : o.status === 'CANCELLED' ? (
              <button className="toss-btn toss-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
                {reorderingId === o.id ? 'Reordering…' : 'Reorder'}
              </button>
            ) : undefined
          }
        />
      ))}
    </div>
  );
}

function OrderFoodView() {
  const [view, setView] = useState<'BROWSE' | 'FAVORITES' | 'ORDERS'>('BROWSE');
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
  // even when that restaurant has been filtered out of the currently-browsed list.
  const [allRestaurants, setAllRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<EatsOrder | null>(null);
  const [reorderCart, setReorderCart] = useState<Record<string, number> | null>(null);
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [reorderError, setReorderError] = useState<string | null>(null);
  // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for a
  // fast star-toggle lookup on each browse card.
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);

  const loadFavorites = () => {
    fetchMyFavoriteRestaurants().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.restaurantId)))).catch(() => {});
  };

  useEffect(() => {
    fetchRestaurants().then(setAllRestaurants).catch(() => {});
    fetchRestaurantCategories().then(setCategories).catch(() => {});
    loadFavorites();
  }, []);

  const toggleFavorite = async (restaurantId: string) => {
    setFavoritingId(restaurantId);
    try {
      if (favoriteIds.has(restaurantId)) {
        await removeFavoriteRestaurant(restaurantId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(restaurantId); return next; });
      } else {
        await addFavoriteRestaurant(restaurantId);
        setFavoriteIds((prev) => new Set(prev).add(restaurantId));
      }
    } catch {
      // Real, non-critical -- a failed toggle just leaves the star as-is; the user can retry.
    } finally {
      setFavoritingId(null);
    }
  };

  // Real category/search filter (2026-07-19), debounced so a search box doesn't
  // re-fetch on every keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const load = () => {
    setError(null);
    fetchRestaurants(selectedCategory ?? undefined, debouncedSearch || undefined)
      .then(setRestaurants)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load restaurants.'));
  };

  useEffect(load, [selectedCategory, debouncedSearch]);

  // Real "Reorder" (2026-07-19): re-populates a fresh cart from a real past order's
  // real items, filtered to whatever's still real and active on the restaurant's
  // current menu -- a discontinued item is silently dropped rather than added as a
  // phantom line the buyer can't actually check out with.
  const handleReorder = async (order: EatsOrder) => {
    setReorderingId(order.id);
    setReorderError(null);
    try {
      const restaurant = allRestaurants?.find((r) => r.merchantId === order.restaurantId);
      if (!restaurant) {
        setReorderError('This restaurant is no longer available.');
        return;
      }
      const [{ items }, menu] = await Promise.all([fetchEatsOrder(order.id), fetchMenu(order.restaurantId)]);
      const activeProductIds = new Set(menu.products.filter((p) => p.active).map((p) => p.id));
      const cart: Record<string, number> = {};
      items.forEach((item) => {
        if (activeProductIds.has(item.productId)) {
          cart[item.productId] = (cart[item.productId] ?? 0) + item.quantity;
        }
      });
      if (Object.keys(cart).length === 0) {
        setReorderError('None of the items from that order are on the menu anymore.');
        return;
      }
      setReorderCart(cart);
      setSelected(restaurant);
      setView('BROWSE');
    } catch (err) {
      setReorderError(err instanceof ApiError ? err.message : 'Could not reorder.');
    } finally {
      setReorderingId(null);
    }
  };

  if (confirmed) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: '22px', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString()} RWF</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>Delivering to {confirmed.deliveryAddress}</p>
        <button
          className="toss-btn toss-btn-secondary"
          onClick={() => { setConfirmed(null); setSelected(null); setView('ORDERS'); }}
        >
          Track order
        </button>
      </div>
    );
  }

  if (selected) {
    return (
      <MenuView
        restaurant={selected}
        onBack={() => { setSelected(null); setReorderCart(null); }}
        onOrderPlaced={setConfirmed}
        initialCart={reorderCart ?? undefined}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'FAVORITES', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Restaurants' : v === 'FAVORITES' ? 'Favorites' : 'My orders'}
          </button>
        ))}
      </div>

      {view === 'ORDERS' ? (
        <>
          {reorderError && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '10px' }} role="alert">{reorderError}</p>}
          <MyEatsOrdersView onReorder={handleReorder} reorderingId={reorderingId} />
        </>
      ) : view === 'FAVORITES' ? (
        <FavoriteRestaurantsView
          onOpen={(r) => setSelected({ merchantId: r.restaurantId, businessName: r.businessName, category: r.category, cashbackRate: '1%' })}
          onChanged={loadFavorites}
        />
      ) : (
        <>
          <input
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search restaurants"
            className="toss-card"
            style={{ width: '100%', padding: '12px 16px', fontSize: '14px', marginBottom: '10px', border: 'none' }}
          />
          {categories.length > 0 && (
            <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px', marginBottom: '14px' }}>
              <button
                onClick={() => setSelectedCategory(null)}
                style={{
                  flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: '12px', fontWeight: 700,
                  color: selectedCategory === null ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                  backgroundColor: selectedCategory === null ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
                }}
              >
                All
              </button>
              {categories.map((c) => (
                <button
                  key={c}
                  onClick={() => setSelectedCategory(c === selectedCategory ? null : c)}
                  style={{
                    flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: '12px', fontWeight: 700,
                    color: selectedCategory === c ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                    backgroundColor: selectedCategory === c ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
                  }}
                >
                  {c}
                </button>
              ))}
            </div>
          )}
          {error ? (
            <div className="toss-card">
              <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
              <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
            </div>
          ) : restaurants === null ? (
            <div className="toss-card skeleton" style={{ height: '220px' }} />
          ) : restaurants.length === 0 ? (
            <div className="toss-card">
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
                {selectedCategory || debouncedSearch ? 'No restaurants match your search.' : 'No restaurants registered yet.'}
              </p>
            </div>
          ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {restaurants.map((r) => (
            <div
              key={r.merchantId}
              role="button"
              tabIndex={0}
              onClick={() => setSelected(r)}
              onKeyDown={(e) => { if (e.key === 'Enter') setSelected(r); }}
              className="toss-card"
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%', cursor: 'pointer' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Utensils size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{r.businessName}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{r.category ? `${r.category} · Real menu, real delivery` : 'Real menu, real delivery'}</p>
              </div>
              <button
                onClick={(e) => { e.stopPropagation(); toggleFavorite(r.merchantId); }}
                disabled={favoritingId === r.merchantId}
                aria-label={favoriteIds.has(r.merchantId) ? 'Remove from favorites' : 'Add to favorites'}
                style={{ padding: '6px', flexShrink: 0 }}
              >
                <Heart size={20} color={favoriteIds.has(r.merchantId) ? '#E53935' : 'var(--toss-grey-400)'} fill={favoriteIds.has(r.merchantId) ? '#E53935' : 'none'} />
              </button>
            </div>
          ))}
        </div>
          )}
        </>
      )}
    </div>
  );
}

// Real bookmarked/favorited restaurants (2026-07-19) -- self-contained, mirroring
// MyEatsOrdersView's own load/local-state pattern; onChanged resyncs OrderFoodView's
// favoriteIds set so the Browse tab's stars stay correct after an unfavorite here.
function FavoriteRestaurantsView({ onOpen, onChanged }: { onOpen: (favorite: FavoriteRestaurant) => void; onChanged: () => void }) {
  const [favorites, setFavorites] = useState<FavoriteRestaurant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteRestaurants().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load favorites.'));
  };

  useEffect(load, []);

  const handleRemove = async (restaurantId: string) => {
    setRemovingId(restaurantId);
    try {
      await removeFavoriteRestaurant(restaurantId);
      setFavorites((prev) => prev?.filter((f) => f.restaurantId !== restaurantId) ?? prev);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove favorite.');
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }
  if (favorites === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }
  if (favorites.length === 0) {
    return <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No favorite restaurants yet. Tap the heart on a restaurant to save it here.</p></div>;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div
          key={f.restaurantId}
          role="button"
          tabIndex={0}
          onClick={() => onOpen(f)}
          onKeyDown={(e) => { if (e.key === 'Enter') onOpen(f); }}
          className="toss-card"
          style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%', cursor: 'pointer' }}
        >
          <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Utensils size={20} color="var(--toss-blue)" />
          </div>
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{f.businessName}</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{f.category ? `${f.category} · Real menu, real delivery` : 'Real menu, real delivery'}</p>
          </div>
          <button
            onClick={(e) => { e.stopPropagation(); handleRemove(f.restaurantId); }}
            disabled={removingId === f.restaurantId}
            aria-label="Remove from favorites"
            style={{ padding: '6px', flexShrink: 0 }}
          >
            <Heart size={20} color="#E53935" fill="#E53935" />
          </button>
        </div>
      ))}
    </div>
  );
}

function DeliverView() {
  const [rider, setRider] = useState<Rider | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [registering, setRegistering] = useState(false);
  const [available, setAvailable] = useState<EatsOrder[] | null>(null);
  const [mine, setMine] = useState<EatsOrder[] | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const loadRider = () => {
    setError(null);
    fetchMyRiderProfile()
      .then(setRider)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RIDER_NOT_REGISTERED') {
          setRider(null);
        } else {
          setError(err instanceof ApiError ? err.message : 'Could not load your rider profile.');
        }
      });
  };

  useEffect(loadRider, []);

  const loadDeliveries = () => {
    Promise.all([fetchAvailableDeliveries(), fetchRiderDeliveries()])
      .then(([a, m]) => { setAvailable(a); setMine(m); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load deliveries.'));
  };

  useEffect(() => {
    if (!rider) return;
    loadDeliveries();
    const interval = setInterval(loadDeliveries, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rider?.id]);

  const handleRegister = async () => {
    setRegistering(true);
    setError(null);
    try {
      setRider(await registerRider());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not register as a rider.');
    } finally {
      setRegistering(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!rider) return;
    try {
      setRider(await setRiderAvailability(!rider.available));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update your availability.');
    }
  };

  const handleClaim = async (orderId: string) => {
    setBusyOrderId(orderId);
    setError(null);
    try {
      await claimDelivery(orderId);
      loadDeliveries();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not claim this delivery.');
    } finally {
      setBusyOrderId(null);
    }
  };

  const handleAdvance = async (order: EatsOrder) => {
    const next = nextInChain(RIDER_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceRiderOrder(order.id, next);
      loadDeliveries();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this delivery.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (rider === undefined) return <div className="toss-card skeleton" style={{ height: '180px' }} />;

  if (rider === null) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
        <Bike size={32} color="var(--toss-blue)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '6px' }}>Deliver with Itunda</h3>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          Earn a real delivery fee for every order you deliver, paid straight to your wallet.
        </p>
        <button className="toss-btn toss-btn-primary" onClick={handleRegister} disabled={registering}>
          {registering ? 'Registering…' : 'Become a rider'}
        </button>
        {error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '12px' }} role="alert">{error}</p>}
      </div>
    );
  }

  const activeDeliveries = (mine ?? []).filter((o) => o.status !== 'DELIVERED');
  const pastDeliveries = (mine ?? []).filter((o) => o.status === 'DELIVERED');

  return (
    <div>
      <div className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <div>
          <p style={{ fontSize: '15px', fontWeight: 700 }}>{rider.available ? "You're online" : "You're offline"}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{rider.available ? 'Visible for new deliveries' : 'Go online to see deliveries'}</p>
        </div>
        <button className={rider.available ? 'toss-btn toss-btn-danger' : 'toss-btn toss-btn-primary'} onClick={handleToggleAvailable}>
          {rider.available ? 'Go offline' : 'Go online'}
        </button>
      </div>

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{error}</p>}

      {activeDeliveries.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your active deliveries</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {activeDeliveries.map((o) => {
              const next = nextInChain(RIDER_STATUS_CHAIN, o.status);
              return (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={next && (
                    <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                      {busyOrderId === o.id ? 'Updating…' : `Mark ${EATS_STATUS_LABEL[next].toLowerCase()}`}
                    </button>
                  )}
                />
              );
            })}
          </div>
        </div>
      )}

      {rider.available && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Available deliveries</h4>
          {available === null ? (
            <div className="toss-card skeleton" style={{ height: '100px' }} />
          ) : available.length === 0 ? (
            <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No deliveries waiting right now.</p></div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {available.map((o) => (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={
                    <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleClaim(o.id)}>
                      {busyOrderId === o.id ? 'Claiming…' : 'Claim delivery'}
                    </button>
                  }
                />
              ))}
            </div>
          )}
        </div>
      )}

      {pastDeliveries.length > 0 && (
        <div>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {pastDeliveries.map((o) => <EatsOrderCard key={o.id} order={o} />)}
          </div>
        </div>
      )}
    </div>
  );
}

function RestaurantOrdersView() {
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected 404 for any account that hasn't registered as a merchant --
        // this view stays silent rather than showing an alarming error for the common
        // case of a buyer-only account that has no restaurant.
        if (err instanceof ApiError && err.code === 'RESTAURANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : 'Could not load your restaurant orders.');
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: EatsOrder) => {
    const next = nextInChain(RESTAURANT_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceRestaurantOrder(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this order.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(RESTAURANT_STATUS_CHAIN, o.status);
          return (
            <EatsOrderCard
              key={o.id}
              order={o}
              action={next && (
                <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                  {busyOrderId === o.id ? 'Updating…' : `Mark ${EATS_STATUS_LABEL[next].toLowerCase()}`}
                </button>
              )}
            />
          );
        })}
      </div>
    </div>
  );
}

function EatsView() {
  const [mode, setMode] = useState<'ORDER' | 'DELIVER'>('ORDER');

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['ORDER', 'DELIVER'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: mode === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: mode === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'ORDER' ? 'Order food' : 'Deliver'}
          </button>
        ))}
      </div>
      {mode === 'ORDER' ? (
        <div>
          <RestaurantOrdersView />
          <OrderFoodView />
        </div>
      ) : (
        <DeliverView />
      )}
    </div>
  );
}

const COMMERCE_STATUS_LABEL: Record<CommerceOrderStatus, string> = {
  PLACED: 'Placed',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled — refunded',
};

const COMMERCE_STATUS_CHAIN: CommerceOrderStatus[] = ['PLACED', 'PACKED', 'SHIPPED', 'DELIVERED'];

function CommerceOrderCard({ order, action }: { order: CommerceOrder; action?: React.ReactNode }) {
  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>{COMMERCE_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {action}
    </div>
  );
}

function ProductCatalogView({ merchant, onBack, onOrderPlaced }: { merchant: ShoppingMerchant; onBack: () => void; onOrderPlaced: (order: CommerceOrder) => void }) {
  const [catalog, setCatalog] = useState<{ businessName: string; products: CommerceProduct[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cart, setCart] = useState<Record<string, number>>({});
  const [address, setAddress] = useState('');
  const [placing, setPlacing] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);

  const load = () => {
    setError(null);
    fetchMerchantProducts(merchant.merchantId)
      .then((r) => setCatalog({ businessName: r.merchant.businessName, products: r.products }))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this catalog.'));
  };

  useEffect(load, [merchant.merchantId]);

  const cartItems = Object.entries(cart).filter(([, qty]) => qty > 0);
  const cartCount = cartItems.reduce((sum, [, qty]) => sum + qty, 0);
  const setQty = (id: string, qty: number) => setCart((c) => ({ ...c, [id]: Math.max(0, qty) }));

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!catalog) return;
    setPlacing(true);
    setError(null);
    try {
      const items = cartItems.map(([productId, quantity]) => ({ productId, quantity }));
      const result = await placeOrder(merchant.merchantId, items, address.trim());
      onOrderPlaced(result.order);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not place this order.');
    } finally {
      setPlacing(false);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }

  if (catalog === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  if (showCheckout) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
          <button onClick={() => setShowCheckout(false)} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to catalog">
            <ArrowLeft size={20} />
          </button>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Checkout</h3>
        </div>
        <form onSubmit={handlePlaceOrder} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([id, qty]) => {
            const item = catalog.products.find((p) => p.id === id);
            if (!item) return null;
            return (
              <div key={id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '14px' }}>
                <span>{item.name} x{qty}</span>
                <span>{(item.price * qty).toLocaleString()} RWF</span>
              </div>
            );
          })}
          <input
            type="text" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Delivery address" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={placing || !address.trim()}>
            {placing ? 'Placing order…' : 'Place order'}
          </button>
          {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
        </form>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to merchants">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{catalog.businessName}</h3>
      </div>
      {catalog.products.length === 0 ? (
        <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No products yet.</p></div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: cartCount > 0 ? '80px' : 0 }}>
          {catalog.products.map((item) => (
            <div key={item.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontSize: '15px', fontWeight: 700 }}>{item.name}</p>
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{item.price.toLocaleString()} RWF</p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <button onClick={() => setQty(item.id, (cart[item.id] ?? 0) - 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{cart[item.id] ?? 0}</span>
                <button onClick={() => setQty(item.id, (cart[item.id] ?? 0) + 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
              </div>
            </div>
          ))}
        </div>
      )}
      {cartCount > 0 && (
        <button
          className="toss-btn toss-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCheckout(true)}
        >
          Checkout ({cartCount} item{cartCount === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

function MyCommerceOrdersView() {
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your orders.'));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleCancel = async (orderId: string) => {
    setCancellingId(orderId);
    setError(null);
    try {
      await cancelOrder(orderId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this order.');
    } finally {
      setCancellingId(null);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No orders yet.</p></div>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <CommerceOrderCard
          key={o.id}
          order={o}
          action={o.status === 'PLACED' && (
            <button className="toss-btn toss-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
              {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
            </button>
          )}
        />
      ))}
    </div>
  );
}

function MerchantOrdersView() {
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchMerchantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected error for any account that hasn't registered as a merchant --
        // stays silent rather than alarming the common case of a buyer-only account.
        if (err instanceof ApiError && err.code === 'MERCHANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : 'Could not load your store orders.');
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: CommerceOrder) => {
    const next = nextInChain(COMMERCE_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceOrderStatus(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this order.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
      </div>
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your store</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(COMMERCE_STATUS_CHAIN, o.status);
          return (
            <CommerceOrderCard
              key={o.id}
              order={o}
              action={next && (
                <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                  {busyOrderId === o.id ? 'Updating…' : `Mark ${COMMERCE_STATUS_LABEL[next].toLowerCase()}`}
                </button>
              )}
            />
          );
        })}
      </div>
    </div>
  );
}

function ShopView() {
  const [view, setView] = useState<'BROWSE' | 'ORDERS'>('BROWSE');
  const [merchants, setMerchants] = useState<ShoppingMerchant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<CommerceOrder | null>(null);

  // Real Coupang-style commerce (rw.itunda.commerce) -- deliberately reuses the same
  // GET /api/v1/shopping/merchants catalog the Shopping tab (Toss Shopping cashback
  // browsing) already uses, matching how Android/iOS's own Shop tab reuses the same
  // merchant directory rather than inventing a second one.
  const load = () => {
    setError(null);
    fetchShoppingCatalog().then(setMerchants).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load merchants.'));
  };

  useEffect(load, []);

  if (confirmed) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: '22px', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString()} RWF</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>Delivering to {confirmed.deliveryAddress}</p>
        <button
          className="toss-btn toss-btn-secondary"
          onClick={() => { setConfirmed(null); setSelected(null); setView('ORDERS'); }}
        >
          Track order
        </button>
      </div>
    );
  }

  if (selected) {
    return <ProductCatalogView merchant={selected} onBack={() => setSelected(null)} onOrderPlaced={setConfirmed} />;
  }

  return (
    <div>
      <MerchantOrdersView />

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Merchants' : 'My orders'}
          </button>
        ))}
      </div>

      {view === 'ORDERS' ? (
        <MyCommerceOrdersView />
      ) : error ? (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
          <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
        </div>
      ) : merchants === null ? (
        <div className="toss-card skeleton" style={{ height: '220px' }} />
      ) : merchants.length === 0 ? (
        <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No merchants registered yet.</p></div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {merchants.map((m) => (
            <button
              key={m.merchantId}
              onClick={() => setSelected(m)}
              className="toss-card"
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <ShoppingBag size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{m.businessName}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Real cart checkout, real delivery tracking</p>
              </div>
            </button>
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
    { id: 'SHOP', label: 'Shop' },
    { id: 'EATS', label: 'Eats' },
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
      {tab === 'SHOP' && <ShopView />}
      {tab === 'EATS' && <EatsView />}
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
