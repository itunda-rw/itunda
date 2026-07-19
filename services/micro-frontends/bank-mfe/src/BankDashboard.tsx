import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { ArrowLeft, ArrowUpRight, Bike, Heart, LogOut, MessageCircle, Plus, ScanFace, Send, ShieldCheck, ShoppingBag, SmilePlus, Star, TrendingDown, TrendingUp, Users, Utensils, Wallet as WalletIcon } from 'lucide-react';
import { getStoredUser, logout, ApiError } from './lib/api';
import { fetchTransactions, fetchWallets, type Transaction, type Wallet } from './lib/wallet';
import { sendDirect } from './lib/p2p';
import { getMyCertificate, issueCertificate, revokeCertificate, type Certificate } from './lib/certificate';
import { collectPayment, fetchShoppingCatalog, type CollectPaymentResult, type ShoppingMerchant } from './lib/shopping';
import {
  buyStock, fetchPortfolio, fetchPortfolioHistory, fetchStockHistory, fetchStocks, fetchWatchlist,
  sellStock, unwatchStock, watchStock,
  type Portfolio, type PortfolioValuePoint, type PricePoint, type Stock,
} from './lib/stocks';
import {
  connectMessagingSocket, createGroup, fetchConversations, fetchGroupMembers, fetchGroupMessages, fetchGroups, fetchMessages,
  fetchPresence, sendGroupMessage, sendMessage, startConversation, toggleGroupReaction, toggleReaction,
  type ConversationSummary, type GroupMember, type GroupMessage,
  type GroupSummary, type Message, type MessagingSocketHandle, type ReactionGroup,
} from './lib/messaging';
import {
  contactSeller, createListing, fetchListings, fetchListingsMyNeighborhood, fetchMyListings, fetchOffersForConversation,
  makeOffer, markListingSold, removeListing, respondToOffer, type Listing, type PriceOffer,
} from './lib/marketplace';
import { fetchProfile, setNeighborhood } from './lib/neighborhood';
import {
  addCommunityComment, createCommunityPost, fetchCommunityCategories, fetchCommunityComments, fetchCommunityPost,
  fetchCommunityPosts, fetchCommunityPostsMyNeighborhood, fetchMyCommunityPosts, removeCommunityPost, toggleCommunityLike,
  type CommunityCategory, type CommunityComment, type CommunityPost,
} from './lib/community';
import {
  contactPoster, createJobPost, fetchJobCategories, fetchJobPosts, fetchJobPostsMyNeighborhood, fetchMyJobPosts,
  markJobPostFilled, removeJobPost, type JobCategory, type JobPayType, type JobPost,
} from './lib/jobs';
import {
  contactLister, createPropertyListing, fetchMyPropertyListings, fetchPropertyListings, fetchPropertyListingsMyNeighborhood,
  fetchPropertyOffersForConversation, fetchPropertyTypes, makePropertyOffer, markPropertyListingTaken, removePropertyListing,
  respondToPropertyOffer, type PropertyListing, type PropertyListingType, type PropertyPriceOffer, type PropertyType,
} from './lib/realestate';
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
import MapView from './MapView';
import RouteMiniMap from './RouteMiniMap';

type Tab = 'HOME' | 'CERTIFICATE' | 'SHOPPING' | 'SHOP' | 'STOCKS' | 'MESSAGES' | 'MARKETPLACE' | 'COMMUNITY' | 'JOBS' | 'PROPERTY' | 'EATS' | 'MAP';

function AccountBalance({ wallet, onTransferClick }: { wallet: Wallet | null; onTransferClick: () => void }) {
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
        <motion.button whileTap={{ scale: 0.96 }} className="toss-btn toss-btn-primary" style={{ flex: 1, gap: '8px' }} onClick={onTransferClick}>
          <ArrowUpRight size={18} /> Transfer
        </motion.button>
        <motion.button whileTap={{ scale: 0.96 }} className="toss-btn toss-btn-secondary" style={{ flex: 1, gap: '8px' }} disabled title="Real mobile-money top-up needs a live MTN/Airtel/bank provider relationship this backend doesn't have yet -- see docs/TOSS_PARITY_MATRIX.md's Transfer row">
          <Plus size={18} /> Top up
        </motion.button>
      </div>
    </motion.div>
  );
}

// Real direct itunda-to-itunda push-transfer (2026-07-20) -- closes a real gap found
// live while first wiring this exact button: WalletController's quote/confirm transfer
// (used by Android/iOS's sendTransfer) always routes through a simulated external rail
// and never actually credits another itunda user's wallet, even when the recipient is a
// real itunda account (confirmed via direct MySQL query: recipientId stayed "external").
// This now calls the new real rw.itunda.p2p.sendDirect instead -- a real recipient
// resolved by phone number or account number, credited immediately, no fee (nothing
// external to settle). No network "quote" step needed (unlike the external-rail flow,
// there's no rail decision to quote) -- the review screen below is a client-side
// confirmation only, same inline-card-replaces-trigger convention every other flow in
// this file already uses, not a modal overlay.
function TransferFlow({ onClose, onSuccess }: { onClose: () => void; onSuccess: () => void }) {
  const [recipient, setRecipient] = useState('');
  const [amount, setAmount] = useState('');
  const [reviewing, setReviewing] = useState(false);
  const [result, setResult] = useState<{ message: string; newBalance: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleReview = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setReviewing(true);
  };

  const handleConfirm = async () => {
    setError(null);
    setBusy(true);
    try {
      const res = await sendDirect(recipient.trim(), Number(amount), '');
      setResult({ message: res.message, newBalance: res.newBalance });
    } catch (err) {
      // A real, honest error surfaces here as-is -- e.g. a recipient that doesn't match
      // any real itunda account real-404s rather than silently doing nothing.
      setError(err instanceof ApiError ? err.message : 'Could not complete this transfer.');
      setReviewing(false);
    } finally {
      setBusy(false);
    }
  };

  if (result) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px', marginBottom: '16px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>{result.message}</h3>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          New balance: {result.newBalance.toLocaleString()} RWF
        </p>
        <button className="toss-btn toss-btn-secondary" onClick={onSuccess}>Done</button>
      </div>
    );
  }

  if (reviewing) {
    return (
      <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Confirm transfer</h3>
        <div style={{ fontSize: '13px', color: 'var(--toss-grey-700)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span>To {recipient}</span>
          <span style={{ fontWeight: 700 }}>Amount: {Number(amount).toLocaleString()} RWF</span>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={onClose} disabled={busy}>Cancel</button>
          <button type="button" className="toss-btn toss-btn-primary" style={{ flex: 1 }} onClick={handleConfirm} disabled={busy}>
            {busy ? 'Sending…' : 'Confirm'}
          </button>
        </div>
        {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      </div>
    );
  }

  return (
    <form onSubmit={handleReview} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Transfer</h3>
      <input
        type="text" value={recipient} onChange={(e) => setRecipient(e.target.value)} placeholder="Recipient phone or account number" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <input
        type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)" required min="1"
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={onClose}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }}>Continue</button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
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
  const [showTransfer, setShowTransfer] = useState(false);

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
      <AccountBalance wallet={wallet} onTransferClick={() => setShowTransfer(true)} />
      {showTransfer && (
        <TransferFlow
          onClose={() => setShowTransfer(false)}
          onSuccess={() => {
            setShowTransfer(false);
            load();
          }}
        />
      )}
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

// Lightweight dependency-free bar sparkline -- no charting library exists anywhere in
// this app yet, and pulling one in just for this would be disproportionate to a real
// MVP chart. Real values, real relative scaling, just rendered as flexbox bars instead
// of an SVG line chart.
function Sparkline({ values, positive }: { values: number[]; positive: boolean }) {
  if (values.length === 0) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: '2px', height: '48px' }}>
      {values.map((v, i) => (
        <div
          key={i}
          style={{
            flex: 1,
            height: `${Math.max(8, ((v - min) / range) * 100)}%`,
            backgroundColor: positive ? 'var(--toss-green)' : '#E53935',
            borderRadius: '2px',
            opacity: 0.3 + (0.7 * i) / values.length,
          }}
        />
      ))}
    </div>
  );
}

function StockDetailSheet({ stock, isWatched, onClose, onTraded, onWatchToggled }: {
  stock: Stock;
  isWatched: boolean;
  onClose: () => void;
  onTraded: () => void;
  onWatchToggled: () => void;
}) {
  const [history, setHistory] = useState<PricePoint[] | null>(null);
  const [shares, setShares] = useState('');
  const [mode, setMode] = useState<'BUY' | 'SELL'>('BUY');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [watching, setWatching] = useState(isWatched);
  const [watchBusy, setWatchBusy] = useState(false);

  useEffect(() => {
    fetchStockHistory(stock.id, 14).then(setHistory).catch(() => setHistory([]));
  }, [stock.id]);

  const handleTrade = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const shareCount = Number(shares);
    if (!shareCount || shareCount <= 0) {
      setError('Enter a real number of shares.');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'BUY') await buyStock(stock.id, shareCount);
      else await sellStock(stock.id, shareCount);
      setShares('');
      onTraded();
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : `Could not ${mode === 'BUY' ? 'buy' : 'sell'} this stock.`);
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleWatch = async () => {
    setWatchBusy(true);
    try {
      if (watching) {
        await unwatchStock(stock.id);
        setWatching(false);
      } else {
        await watchStock(stock.id);
        setWatching(true);
      }
      onWatchToggled();
    } catch {
      // Non-critical -- the star just doesn't flip, no error surfaced for a real
      // watch/unwatch toggle failure.
    } finally {
      setWatchBusy(false);
    }
  };

  const positive = stock.changePercent >= 0;

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '4px' }}>
        <button onClick={onClose} style={{ color: 'var(--toss-grey-500)', display: 'flex' }} aria-label="Back">
          <ArrowLeft size={18} />
        </button>
        <button onClick={handleToggleWatch} disabled={watchBusy} style={{ color: watching ? '#FFC107' : 'var(--toss-grey-300)', display: 'flex' }} aria-label="Toggle watch">
          <Star size={20} fill={watching ? '#FFC107' : 'none'} />
        </button>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', fontWeight: 600 }}>{stock.symbol} · {stock.marketCap}</p>
      <h3 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '6px' }}>{stock.name}</h3>
      <p style={{ fontSize: '26px', fontWeight: 700, marginBottom: '4px' }}>{stock.price.toLocaleString()} RWF</p>
      <p style={{ fontSize: '14px', fontWeight: 700, color: positive ? 'var(--toss-green)' : '#E53935', display: 'flex', alignItems: 'center', gap: '4px', marginBottom: '16px' }}>
        {positive ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
        {positive ? '+' : ''}{stock.change.toLocaleString()} ({positive ? '+' : ''}{stock.changePercent.toFixed(2)}%) today
      </p>

      {history === null ? (
        <div className="skeleton" style={{ height: '48px', borderRadius: '8px', marginBottom: '16px' }} />
      ) : history.length > 0 ? (
        <div style={{ marginBottom: '16px' }}>
          <Sparkline values={history.map((h) => h.price)} positive={positive} />
          <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>Last 14 days -- real deterministic simulation, not live RSE data</p>
        </div>
      ) : null}

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '12px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BUY', 'SELL'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: mode === m ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: mode === m ? (m === 'BUY' ? 'var(--toss-blue)' : '#E53935') : 'transparent',
            }}
          >
            {m === 'BUY' ? 'Buy' : 'Sell'}
          </button>
        ))}
      </div>
      <form onSubmit={handleTrade} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" min="0.0001" step="any" value={shares} onChange={(e) => setShares(e.target.value)}
          placeholder="Shares" required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className={mode === 'BUY' ? 'toss-btn toss-btn-primary' : 'toss-btn'} style={mode === 'SELL' ? { backgroundColor: '#E53935', color: 'white' } : undefined} disabled={submitting}>
          {submitting ? 'Working…' : mode === 'BUY' ? 'Buy' : 'Sell'}
        </button>
      </form>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>}
    </div>
  );
}

function StocksView() {
  const [subTab, setSubTab] = useState<'MARKET' | 'PORTFOLIO' | 'WATCHLIST'>('MARKET');
  const [stocks, setStocks] = useState<Stock[] | null>(null);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [portfolioHistory, setPortfolioHistory] = useState<PortfolioValuePoint[] | null>(null);
  const [watchlist, setWatchlist] = useState<Stock[] | null>(null);
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadMarket = () => {
    setError(null);
    fetchStocks().then(setStocks).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the real market.'));
  };
  const loadPortfolio = () => {
    setError(null);
    Promise.all([fetchPortfolio(), fetchPortfolioHistory(30)])
      .then(([p, h]) => { setPortfolio(p); setPortfolioHistory(h); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your real portfolio.'));
  };
  const loadWatchlist = () => {
    setError(null);
    fetchWatchlist().then(setWatchlist).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your real watchlist.'));
  };

  useEffect(() => {
    if (subTab === 'MARKET') loadMarket();
    else if (subTab === 'PORTFOLIO') loadPortfolio();
    else loadWatchlist();
    setSelectedStock(null);
  }, [subTab]);

  const watchedIds = new Set((watchlist ?? []).map((s) => s.id));

  const renderStockRow = (stock: Stock) => {
    const positive = stock.changePercent >= 0;
    return (
      <div
        key={stock.id}
        onClick={() => setSelectedStock(stock)}
        className="toss-card"
        style={{ display: 'flex', alignItems: 'center', gap: '14px', padding: '16px 18px', cursor: 'pointer' }}
      >
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{stock.symbol}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{stock.name}</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p style={{ fontSize: '15px', fontWeight: 700 }}>{stock.price.toLocaleString()} RWF</p>
          <p style={{ fontSize: '12px', fontWeight: 700, color: positive ? 'var(--toss-green)' : '#E53935', display: 'flex', alignItems: 'center', gap: '2px', justifyContent: 'flex-end' }}>
            {positive ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
            {positive ? '+' : ''}{stock.changePercent.toFixed(2)}%
          </p>
        </div>
      </div>
    );
  };

  if (selectedStock) {
    return (
      <StockDetailSheet
        stock={selectedStock}
        isWatched={watchedIds.has(selectedStock.id)}
        onClose={() => setSelectedStock(null)}
        onTraded={() => { loadPortfolio(); if (subTab === 'MARKET') loadMarket(); }}
        onWatchToggled={loadWatchlist}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {([{ id: 'MARKET', label: 'Market' }, { id: 'PORTFOLIO', label: 'Portfolio' }, { id: 'WATCHLIST', label: 'Watchlist' }] as const).map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setSubTab(id)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: subTab === id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: subTab === id ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {error && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
          <button className="toss-btn toss-btn-secondary" onClick={subTab === 'MARKET' ? loadMarket : subTab === 'PORTFOLIO' ? loadPortfolio : loadWatchlist} style={{ marginTop: '12px' }}>Retry</button>
        </div>
      )}

      {subTab === 'MARKET' && (
        stocks === null ? <div className="toss-card skeleton" style={{ height: '220px' }} /> : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {stocks.map(renderStockRow)}
          </div>
        )
      )}

      {subTab === 'PORTFOLIO' && (
        portfolio === null ? <div className="toss-card skeleton" style={{ height: '220px' }} /> : (
          <div>
            <div className="toss-card" style={{ marginBottom: '16px' }}>
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', fontWeight: 600 }}>Total value</p>
              <p style={{ fontSize: '26px', fontWeight: 700, marginBottom: '4px' }}>{portfolio.totalValue.toLocaleString()} RWF</p>
              <p style={{ fontSize: '14px', fontWeight: 700, color: portfolio.totalReturn >= 0 ? 'var(--toss-green)' : '#E53935', marginBottom: '12px' }}>
                {portfolio.totalReturn >= 0 ? '+' : ''}{portfolio.totalReturn.toLocaleString()} RWF ({portfolio.totalReturn >= 0 ? '+' : ''}{portfolio.totalReturnPercent.toFixed(2)}%)
              </p>
              {portfolioHistory && portfolioHistory.length > 0 && (
                <div>
                  <Sparkline values={portfolioHistory.map((h) => h.value)} positive={portfolio.totalReturn >= 0} />
                  <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
                    Last 30 days -- based on your current holdings applied to real historical prices, not a full historical reconstruction
                  </p>
                </div>
              )}
            </div>
            {portfolio.holdings.length === 0 ? (
              <div className="toss-card">
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>You don't hold any real shares yet. Browse the Market tab to buy some.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {portfolio.holdings.map((h) => (
                  <div key={h.stockId} className="toss-card" style={{ padding: '16px 18px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <p style={{ fontSize: '15px', fontWeight: 700 }}>{h.symbol}</p>
                      <p style={{ fontSize: '15px', fontWeight: 700 }}>{h.value.toLocaleString()} RWF</p>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{h.shares} shares @ {h.avgPrice.toLocaleString()} avg</p>
                      <p style={{ fontSize: '12px', fontWeight: 700, color: h.return >= 0 ? 'var(--toss-green)' : '#E53935' }}>
                        {h.return >= 0 ? '+' : ''}{h.return.toFixed(2)}%
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      )}

      {subTab === 'WATCHLIST' && (
        watchlist === null ? <div className="toss-card skeleton" style={{ height: '220px' }} /> : watchlist.length === 0 ? (
          <div className="toss-card">
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No stocks watched yet. Tap the star on any stock in the Market tab to follow it.</p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {watchlist.map(renderStockRow)}
          </div>
        )
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

// Real quick-react palette -- a small fixed set (matching most real chat apps' own
// "long-press to react" quick palette) rather than a full emoji picker, kept simple
// since this web client has no native emoji-keyboard integration to lean on.
const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢'];

// Real emoji reactions (2026-07-19) -- shared between 1:1 and group threads, which
// differ only in which toggle call they make. Tapping an existing reaction badge
// toggles the current user's own reaction for that emoji (the fast, one-tap path real
// chat apps use); the smile button opens the quick palette for a first reaction.
function MessageReactions({
  reactions, currentUserId, onToggle, isMine,
}: {
  reactions: ReactionGroup[]; currentUserId: string | undefined; onToggle: (emoji: string) => void; isMine: boolean;
}) {
  const [pickerOpen, setPickerOpen] = useState(false);
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '4px', justifyContent: isMine ? 'flex-end' : 'flex-start' }}>
      {reactions.filter((r) => r.userIds.length > 0).map((r) => {
        const mine = !!currentUserId && r.userIds.includes(currentUserId);
        return (
          <button
            key={r.emoji}
            onClick={() => onToggle(r.emoji)}
            style={{
              display: 'flex', alignItems: 'center', gap: '4px', padding: '2px 8px', borderRadius: '12px', fontSize: '12px',
              border: mine ? '1px solid var(--toss-blue)' : '1px solid var(--toss-grey-200)',
              backgroundColor: mine ? 'var(--toss-blue-light)' : 'var(--toss-white)',
            }}
          >
            <span>{r.emoji}</span>
            <span style={{ color: 'var(--toss-grey-700)' }}>{r.userIds.length}</span>
          </button>
        );
      })}
      <div style={{ position: 'relative' }}>
        <button
          onClick={() => setPickerOpen((v) => !v)}
          aria-label="Add reaction"
          style={{ display: 'flex', padding: '2px 6px', borderRadius: '12px', border: '1px solid var(--toss-grey-200)', color: 'var(--toss-grey-500)' }}
        >
          <SmilePlus size={14} />
        </button>
        {pickerOpen && (
          <div
            style={{
              position: 'absolute', bottom: '28px', display: 'flex', gap: '4px', padding: '6px 8px',
              borderRadius: '12px', backgroundColor: 'var(--toss-white)', boxShadow: '0 2px 8px rgba(0,0,0,0.15)', zIndex: 10,
              left: isMine ? undefined : 0, right: isMine ? 0 : undefined,
            }}
          >
            {QUICK_REACTIONS.map((emoji) => (
              <button
                key={emoji}
                onClick={() => { onToggle(emoji); setPickerOpen(false); }}
                style={{ fontSize: '18px', padding: '2px' }}
              >
                {emoji}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Real 당근-style price-offer bubble -- see PriceOfferService's own doc comment.
// Renders inline wherever a message carries a real offer, replacing the plain-text
// bubble with amount + status + real Accept/Reject/Counter actions (only shown to
// whichever participant did NOT propose the current pending amount). Prop type
// deliberately narrowed to just the fields this component actually reads (not the full
// `PriceOffer` shape) so it structurally accepts both Marketplace's `PriceOffer` and
// Real Estate's `PropertyPriceOffer` (2026-07-19) without duplicating this component --
// the two types have different field names for listing/buyer/seller (irrelevant here),
// but identical id/amount/status/proposedByUserId shapes.
interface OfferBubbleData {
  id: string;
  amount: number;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'COUNTERED';
  proposedByUserId: string;
}

function OfferBubble({
  offer, isMine, currentUserId, onRespond,
}: {
  offer: OfferBubbleData; isMine: boolean; currentUserId: string | undefined; onRespond: (offerId: string, action: 'ACCEPT' | 'REJECT' | 'COUNTER', counterAmount?: number) => void;
}) {
  const [countering, setCountering] = useState(false);
  const [counterAmount, setCounterAmount] = useState('');
  const canRespond = offer.status === 'PENDING' && currentUserId && currentUserId !== offer.proposedByUserId;
  const statusLabel: Record<OfferBubbleData['status'], string> = {
    PENDING: 'Pending', ACCEPTED: 'Accepted', REJECTED: 'Declined', COUNTERED: 'Countered',
  };

  return (
    <div
      style={{
        maxWidth: '75%', padding: '12px 14px', borderRadius: '16px', fontSize: '14px',
        backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
        color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700 }}>💰 {offer.amount.toLocaleString()} RWF</p>
      <p style={{ fontSize: '12px', opacity: 0.8 }}>{statusLabel[offer.status]}</p>
      {canRespond && !countering && (
        <div style={{ display: 'flex', gap: '6px' }}>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'ACCEPT')}>
            Accept
          </button>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'REJECT')}>
            Decline
          </button>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 10px' }} onClick={() => setCountering(true)}>
            Counter
          </button>
        </div>
      )}
      {canRespond && countering && (
        <div style={{ display: 'flex', gap: '6px' }}>
          <input
            type="number"
            value={counterAmount}
            onChange={(e) => setCounterAmount(e.target.value)}
            placeholder="Counter (RWF)"
            style={{ flex: 1, padding: '6px 8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)', fontSize: '12px' }}
          />
          <button
            className="toss-btn toss-btn-secondary"
            style={{ fontSize: '12px', padding: '6px 10px' }}
            disabled={!counterAmount || Number(counterAmount) <= 0}
            onClick={() => {
              onRespond(offer.id, 'COUNTER', Number(counterAmount));
              setCountering(false);
              setCounterAmount('');
            }}
          >
            Send
          </button>
        </div>
      )}
    </div>
  );
}

function ConversationThread({ conversation, onBack }: { conversation: ConversationSummary; onBack: () => void }) {
  const [messages, setMessages] = useState<Message[] | null>(null);
  const [offersByMessageId, setOffersByMessageId] = useState<Record<string, OfferBubbleData>>({});
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [otherOnline, setOtherOnline] = useState<boolean | null>(null);
  const [otherTyping, setOtherTyping] = useState(false);
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<MessagingSocketHandle | null>(null);
  const typingClearTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastTypingSentAt = useRef(0);

  useEffect(() => {
    fetchPresence([conversation.otherUserId]).then((p) => setOtherOnline(p[conversation.otherUserId] ?? null)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.otherUserId]);

  // Real-fetches both Marketplace and Real Estate offer history for this conversation --
  // a given real conversation only ever carries one type in practice (a listing/property
  // negotiation thread), but fetching both is cheap and correct rather than guessing
  // which one applies; each failure is independently non-critical.
  const loadOffers = () => {
    Promise.all([
      fetchOffersForConversation(conversation.conversationId).catch(() => [] as PriceOffer[]),
      fetchPropertyOffersForConversation(conversation.conversationId).catch(() => [] as PropertyPriceOffer[]),
    ]).then(([marketplaceOffers, propertyOffers]) => {
      setOffersByMessageId(
        Object.fromEntries([...marketplaceOffers, ...propertyOffers].map((o) => [o.messageId, o])),
      );
    });
  };

  const load = () => {
    fetchMessages(conversation.conversationId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this conversation.'));
    loadOffers();
  };

  const handleRespondToOffer = async (offerId: string, action: 'ACCEPT' | 'REJECT' | 'COUNTER', counterAmount?: number) => {
    try {
      // Real offer ids are stably prefixed by their real owning service
      // ("price_offer_"/"property_offer_"), a reliable dispatch key -- avoids needing
      // the thread to already know which listing type this conversation is about.
      if (offerId.startsWith('property_offer_')) {
        await respondToPropertyOffer(offerId, action, counterAmount);
      } else {
        await respondToOffer(offerId, action, counterAmount);
      }
      loadOffers();
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not respond to this offer.');
    }
  };

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
    const socket = connectMessagingSocket((payload) => {
      if (payload.type === 'presence') {
        if (payload.userId === conversation.otherUserId) setOtherOnline(payload.online);
        return;
      }
      if (payload.type === 'typing') {
        if (payload.conversationId !== conversation.conversationId || payload.userId !== conversation.otherUserId) return;
        setOtherTyping(true);
        if (typingClearTimer.current) clearTimeout(typingClearTimer.current);
        // Real, client-side "stopped typing" inference (2026-07-19) -- there's no
        // explicit "stopped typing" event, same convention every real chat app uses:
        // clear the indicator if no new typing ping arrives within a few seconds.
        typingClearTimer.current = setTimeout(() => setOtherTyping(false), 3000);
        return;
      }
      if (payload.type === 'reaction') {
        if (payload.conversationId !== conversation.conversationId) return;
        setMessages((prev) => prev?.map((m) => (m.id === payload.messageId ? { ...m, reactions: payload.reactions } : m)) ?? prev);
        return;
      }
      if (payload.type !== 'message' || payload.conversationId !== conversation.conversationId) return;
      setOtherTyping(false);
      setMessages((prev) => {
        if (!prev) return prev;
        if (prev.some((m) => m.id === payload.message.id)) return prev;
        return [...prev, payload.message];
      });
      // A pushed message might be a real offer/counter/accept/reject -- refresh the
      // offer history so it renders as an offer bubble immediately rather than waiting
      // for the next 4s poll.
      loadOffers();
    });
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
      if (typingClearTimer.current) clearTimeout(typingClearTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.conversationId, conversation.otherUserId]);

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

  const handleToggleReaction = async (messageId: string, emoji: string) => {
    try {
      const reactions = await toggleReaction(messageId, emoji);
      setMessages((prev) => prev?.map((m) => (m.id === messageId ? { ...m, reactions } : m)) ?? prev);
    } catch {
      // Best-effort -- a failed reaction toggle just leaves the badge as it was, never
      // blocks the thread.
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to conversations">
          <ArrowLeft size={20} />
        </button>
        <div>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{conversation.otherUserName}</h3>
          {otherOnline !== null && (
            <p style={{ fontSize: '12px', color: otherOnline ? 'var(--toss-green)' : 'var(--toss-grey-500)' }}>
              {otherOnline ? 'Online' : 'Offline'}
            </p>
          )}
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
          const offer = offersByMessageId[m.id];
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {offer ? (
                <OfferBubble offer={offer} isMine={isMine} currentUserId={currentUser?.id} onRespond={handleRespondToOffer} />
              ) : (
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
              )}
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {otherTyping && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {conversation.otherUserName} is typing…
        </p>
      )}

      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>
      )}

      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="text"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            // Real typing indicator send (2026-07-19), client-throttled to match the
            // server's own 1-per-2s rate limit so every keystroke isn't a wasted send.
            const now = Date.now();
            if (now - lastTypingSentAt.current > 2000) {
              lastTypingSentAt.current = now;
              socketRef.current?.sendTyping({ conversationId: conversation.conversationId });
            }
          }}
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
  const [typingUserIds, setTypingUserIds] = useState<Record<string, boolean>>({});
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<MessagingSocketHandle | null>(null);
  const typingClearTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const lastTypingSentAt = useRef(0);

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
    const socket = connectMessagingSocket((payload) => {
      if (payload.type === 'typing') {
        if (payload.groupConversationId !== group.groupId) return;
        const userId = payload.userId;
        setTypingUserIds((prev) => ({ ...prev, [userId]: true }));
        if (typingClearTimers.current[userId]) clearTimeout(typingClearTimers.current[userId]);
        typingClearTimers.current[userId] = setTimeout(() => {
          setTypingUserIds((prev) => {
            const next = { ...prev };
            delete next[userId];
            return next;
          });
        }, 3000);
        return;
      }
      if (payload.type === 'reaction') {
        if (payload.groupConversationId !== group.groupId) return;
        setMessages((prev) => prev?.map((m) => (m.id === payload.messageId ? { ...m, reactions: payload.reactions } : m)) ?? prev);
        return;
      }
      if (payload.type !== 'group_message' || payload.groupConversationId !== group.groupId) return;
      setTypingUserIds((prev) => {
        if (!(payload.message.senderId in prev)) return prev;
        const next = { ...prev };
        delete next[payload.message.senderId];
        return next;
      });
      setMessages((prev) => {
        if (!prev) return prev;
        if (prev.some((m) => m.id === payload.message.id)) return prev;
        return [...prev, payload.message];
      });
    });
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
      Object.values(typingClearTimers.current).forEach(clearTimeout);
    };
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

  const handleToggleReaction = async (groupMessageId: string, emoji: string) => {
    try {
      const reactions = await toggleGroupReaction(groupMessageId, emoji);
      setMessages((prev) => prev?.map((m) => (m.id === groupMessageId ? { ...m, reactions } : m)) ?? prev);
    } catch {
      // Best-effort -- a failed reaction toggle just leaves the badge as it was, never
      // blocks the thread.
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
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>

      {Object.keys(typingUserIds).length > 0 && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {Object.keys(typingUserIds).map(nameForSender).join(', ')} {Object.keys(typingUserIds).length === 1 ? 'is' : 'are'} typing…
        </p>
      )}

      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>
      )}

      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="text"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            const now = Date.now();
            if (now - lastTypingSentAt.current > 2000) {
              lastTypingSentAt.current = now;
              socketRef.current?.sendTyping({ groupConversationId: group.groupId });
            }
          }}
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
  const [presence, setPresence] = useState<Record<string, boolean>>({});

  const load = () => {
    setError(null);
    fetchConversations()
      .then(setConversations)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your conversations.'));
  };

  useEffect(load, []);

  // Real online/offline presence for the list view (2026-07-19) -- a bulk on-demand
  // check for every listed contact, refreshed on a 10s cadence (a real, coarser-grained
  // signal than the 4s message poll -- presence doesn't need to be as fresh as message
  // delivery). No live WebSocket connection is opened just for this list view; the
  // per-thread real-time push happens in ConversationThread once a thread is open.
  useEffect(() => {
    if (!conversations || conversations.length === 0) return;
    const otherIds = conversations.map((c) => c.otherUserId);
    const refresh = () => fetchPresence(otherIds).then(setPresence).catch(() => {});
    refresh();
    const interval = setInterval(refresh, 10000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversations?.map((c) => c.otherUserId).join(',')]);

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
              <div style={{ position: 'relative', width: '44px', height: '44px', flexShrink: 0 }}>
                <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <MessageCircle size={20} color="var(--toss-blue)" />
                </div>
                {presence[c.otherUserId] && (
                  <span
                    style={{
                      position: 'absolute', bottom: 0, right: 0, width: '12px', height: '12px', borderRadius: '6px',
                      backgroundColor: 'var(--toss-green)', border: '2px solid var(--toss-white)',
                    }}
                  />
                )}
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
  // Real optional seller location (2026-07-18 backend support, 2026-07-19 this UI) --
  // powers real proximity search and "Directions to this seller"; a listing without it
  // simply doesn't appear in either, an honest opt-in, never assumed.
  const [shareLocation, setShareLocation] = useState(false);
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null); // [lat, lng]
  const [locating, setLocating] = useState(false);

  const handleToggleShareLocation = () => {
    if (shareLocation) {
      setShareLocation(false);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShareLocation(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const [lat, lng] = shareLocation && myLocation ? myLocation : [undefined, undefined];
      await createListing(title, description, Number(price), category, lat, lng);
      setTitle('');
      setDescription('');
      setPrice('');
      setCategory('');
      setShareLocation(false);
      setMyLocation(null);
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
      <button
        type="button"
        className="toss-btn toss-btn-secondary"
        disabled={locating}
        onClick={handleToggleShareLocation}
        style={{ fontSize: '13px' }}
      >
        {locating ? 'Finding your real location…' : shareLocation ? '📍 Real location shared -- buyers can see distance & get directions' : '📍 Share my real location (optional)'}
      </button>
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
  const [offering, setOffering] = useState(false);
  const [offerAmount, setOfferAmount] = useState('');
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null); // [lat, lng]
  const [showRoute, setShowRoute] = useState(false);
  const [locating, setLocating] = useState(false);

  const handleShowDirections = () => {
    if (showRoute) {
      setShowRoute(false);
      return;
    }
    if (myLocation) {
      setShowRoute(true);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShowRoute(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

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

  const handleMakeOffer = async () => {
    const amount = Number(offerAmount);
    if (!amount || amount <= 0) return;
    setBusy(true);
    setError(null);
    try {
      const offer = await makeOffer(listing.id, amount);
      setOffering(false);
      setOfferAmount('');
      onMessageSeller(offer.conversationId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this offer.');
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
      {offering && (
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="number"
            value={offerAmount}
            onChange={(e) => setOfferAmount(e.target.value)}
            placeholder="Your offer (RWF)"
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button className="toss-btn toss-btn-primary" disabled={busy || !offerAmount} onClick={handleMakeOffer}>
            Send
          </button>
        </div>
      )}
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
          listing.status === 'ACTIVE' && !offering && (
            <>
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={handleMessage}>
                {busy ? 'Starting…' : 'Message seller'}
              </button>
              <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => setOffering(true)}>
                Make an offer
              </button>
            </>
          )
        )}
      </div>
      {!isMine && listing.status === 'ACTIVE' && listing.latitude != null && listing.longitude != null && (
        <button className="toss-btn toss-btn-secondary" disabled={locating} onClick={handleShowDirections}>
          {locating ? 'Finding your real location…' : showRoute ? 'Hide directions' : '🚗 Directions to this seller'}
        </button>
      )}
      {showRoute && myLocation && listing.latitude != null && listing.longitude != null && (
        <RouteMiniMap
          fromLat={myLocation[0]}
          fromLng={myLocation[1]}
          toLat={listing.latitude}
          toLng={listing.longitude}
          fromLabel="You"
          toLabel={listing.title}
        />
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

// Real hyperlocal neighborhood setup (2026-07-20) -- shared across every Hood-tab
// module (Marketplace/Community/Jobs/Property), same "one small component, four real
// call sites" shape this project already uses for offer bubbles etc. See
// lib/neighborhood.ts's own doc comment for the full backend account.
function NeighborhoodSetupPrompt({ onDone }: { onDone: (neighborhood: string) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleShare = () => {
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setBusy(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setNeighborhood(position.coords.latitude, position.coords.longitude)
          .then((user) => {
            setBusy(false);
            if (user.neighborhood) onDone(user.neighborhood);
          })
          .catch((err) => {
            setBusy(false);
            setError(err instanceof ApiError ? err.message : 'Could not determine your neighborhood.');
          });
      },
      () => {
        setBusy(false);
        setError('Could not get your real location. Check your browser permissions.');
      },
    );
  };

  return (
    <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
      <p style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>Set your neighborhood</p>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        Share your real location once to see what's happening near you.
      </p>
      <button className="toss-btn toss-btn-primary" onClick={handleShare} disabled={busy}>
        {busy ? 'Finding your neighborhood…' : '📍 Share my location'}
      </button>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '12px' }} role="alert">{error}</p>}
    </div>
  );
}

function MarketplaceView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'NEIGHBORHOOD'>('BROWSE');
  const [listings, setListings] = useState<Listing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const currentUser = getStoredUser();

  const load = () => {
    setError(null);
    setListings(null);
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchListingsMyNeighborhood()])
        .then(([profile, items]) => {
          setNeighborhoodName(profile.neighborhood);
          setListings(items);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setListings([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    const fetcher = view === 'BROWSE' ? fetchListings() : fetchMyListings();
    fetcher
      .then(setListings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load listings.'));
  };

  useEffect(load, [view]);

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Browse' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : 'My listings'}
          </button>
        ))}
      </div>

      {view === 'MINE' && <NewListingCard onCreated={load} />}

      {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
        <NeighborhoodSetupPrompt onDone={() => load()} />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
          Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
        </p>
      )}

      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
          <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
        </div>
      )}
      {!error && listings === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
      {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && listings !== null && listings.length === 0 && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            {view === 'BROWSE' ? 'No listings yet.' : view === 'NEIGHBORHOOD' ? 'No listings in your neighborhood yet.' : "You haven't listed anything yet."}
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

// ============================== COMMUNITY (동네생활) ==============================

function NewCommunityPostCard({ categories, onCreated }: { categories: CommunityCategory[]; onCreated: () => void }) {
  const [category, setCategory] = useState(categories[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);
  // Real optional post location (opt-in, same pattern NewListingCard already
  // established) -- powers a real "near me" browse.
  const [shareLocation, setShareLocation] = useState(false);
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null);
  const [locating, setLocating] = useState(false);

  const handleToggleShareLocation = () => {
    if (shareLocation) {
      setShareLocation(false);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShareLocation(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const [lat, lng] = shareLocation && myLocation ? myLocation : [undefined, undefined];
      await createCommunityPost(category, title, body, lat, lng);
      setTitle('');
      setBody('');
      setShareLocation(false);
      setMyLocation(null);
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this post.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="toss-btn toss-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + Write a post
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Write a post</h3>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      >
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={body} onChange={(e) => setBody(e.target.value)} placeholder="What's going on in the neighborhood?" required rows={4}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      <button
        type="button"
        className="toss-btn toss-btn-secondary"
        disabled={locating}
        onClick={handleToggleShareLocation}
        style={{ fontSize: '13px' }}
      >
        {locating ? 'Finding your real location…' : shareLocation ? '📍 Real location shared -- others nearby can find this post' : '📍 Share my real location (optional)'}
      </button>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Posting…' : 'Post'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function CommunityPostCard({ post, categoryLabel, isMine, onOpen, onChanged }: {
  post: CommunityPost; categoryLabel: string; isMine: boolean; onOpen: () => void; onChanged: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRemove = async () => {
    setBusy(true);
    try {
      await removeCommunityPost(post.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this post.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px', cursor: 'pointer' }} onClick={onOpen}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>{categoryLabel}</span>
        {isMine && (
          <button
            className="toss-btn toss-btn-secondary"
            style={{ fontSize: '11px', padding: '4px 10px' }}
            disabled={busy}
            onClick={(e) => { e.stopPropagation(); void handleRemove(); }}
          >
            {busy ? 'Removing…' : 'Remove'}
          </button>
        )}
      </div>
      <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{post.title}</p>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as const }}>
        {post.body}
      </p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-400)' }}>
        ❤️ {post.likeCount} · 💬 {post.commentCount}
      </p>
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

function CommunityPostDetailView({ postId, onBack }: { postId: string; onBack: () => void }) {
  const [post, setPost] = useState<CommunityPost | null>(null);
  const [authorName, setAuthorName] = useState('');
  const [likedByMe, setLikedByMe] = useState(false);
  const [comments, setComments] = useState<{ comment: CommunityComment; authorName: string }[] | null>(null);
  const [commentBody, setCommentBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [liking, setLiking] = useState(false);
  const [commenting, setCommenting] = useState(false);

  const load = () => {
    setError(null);
    fetchCommunityPost(postId)
      .then((r) => { setPost(r.post); setAuthorName(r.authorName); setLikedByMe(r.likedByMe); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this post.'));
    fetchCommunityComments(postId)
      .then(setComments)
      .catch(() => { /* non-critical -- the post itself still renders */ });
  };

  useEffect(load, [postId]);

  const handleLike = async () => {
    setLiking(true);
    try {
      const liked = await toggleCommunityLike(postId);
      setLikedByMe(liked);
      setPost((p) => (p ? { ...p, likeCount: p.likeCount + (liked ? 1 : -1) } : p));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update your like.');
    } finally {
      setLiking(false);
    }
  };

  const handleComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentBody.trim()) return;
    setCommenting(true);
    setError(null);
    try {
      await addCommunityComment(postId, commentBody);
      setCommentBody('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post your comment.');
    } finally {
      setCommenting(false);
    }
  };

  return (
    <div>
      <button className="toss-btn toss-btn-secondary" style={{ marginBottom: '12px' }} onClick={onBack}>← Back</button>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {!post && !error && <div className="toss-card skeleton" style={{ height: '160px' }} />}
      {post && (
        <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
          <p style={{ fontSize: '17px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{post.title}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>by {authorName}</p>
          <p style={{ fontSize: '14px', color: 'var(--toss-grey-700)', whiteSpace: 'pre-wrap' }}>{post.body}</p>
          <button
            className="toss-btn toss-btn-secondary"
            disabled={liking}
            onClick={handleLike}
            style={{ alignSelf: 'flex-start', fontSize: '13px' }}
          >
            {likedByMe ? '❤️' : '🤍'} {post.likeCount}
          </button>
        </div>
      )}
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Comments</h3>
      {comments === null && <div className="toss-card skeleton" style={{ height: '80px' }} />}
      {comments !== null && comments.length === 0 && (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>No comments yet -- be the first to reply.</p>
      )}
      {comments !== null && comments.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          {comments.map(({ comment, authorName: name }) => (
            <div key={comment.id} className="toss-card" style={{ padding: '10px 14px' }}>
              <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-700)' }}>{name}</p>
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-900)' }}>{comment.body}</p>
            </div>
          ))}
        </div>
      )}
      <form onSubmit={handleComment} style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text" value={commentBody} onChange={(e) => setCommentBody(e.target.value)} placeholder="Add a comment"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={commenting || !commentBody.trim()}>
          {commenting ? '…' : 'Send'}
        </button>
      </form>
    </div>
  );
}

function CommunityView() {
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'NEIGHBORHOOD'>('BROWSE');
  const [categories, setCategories] = useState<CommunityCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [posts, setPosts] = useState<CommunityPost[] | null>(null);
  const [openPostId, setOpenPostId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const currentUser = getStoredUser();

  useEffect(() => {
    fetchCommunityCategories().then(setCategories).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const load = () => {
    setError(null);
    setPosts(null);
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchCommunityPostsMyNeighborhood(activeCategory ?? undefined)])
        .then(([profile, items]) => {
          setNeighborhoodName(profile.neighborhood);
          setPosts(items);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setPosts([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    const fetcher = view === 'BROWSE' ? fetchCommunityPosts(activeCategory ?? undefined) : fetchMyCommunityPosts();
    fetcher
      .then(setPosts)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load posts.'));
  };

  useEffect(load, [view, activeCategory]);

  if (openPostId) {
    return <CommunityPostDetailView postId={openPostId} onBack={() => { setOpenPostId(null); load(); }} />;
  }

  const categoryLabel = (id: string) => categories.find((c) => c.id === id)?.label ?? id;

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Feed' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : 'My posts'}
          </button>
        ))}
      </div>

      {(view === 'BROWSE' || view === 'NEIGHBORHOOD') && categories.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
          {categories.map((c) => (
            <button
              key={c.id}
              onClick={() => setActiveCategory(activeCategory === c.id ? null : c.id)}
              style={{
                whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                border: `1px solid ${activeCategory === c.id ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                color: activeCategory === c.id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: activeCategory === c.id ? 'var(--toss-blue)' : 'transparent',
              }}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}

      {view === 'MINE' && <NewCommunityPostCard categories={categories} onCreated={load} />}

      {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
        <NeighborhoodSetupPrompt onDone={() => load()} />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
          Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
        </p>
      )}

      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
          <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
        </div>
      )}
      {!error && posts === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
      {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && posts !== null && posts.length === 0 && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            {view === 'BROWSE' ? 'No posts yet.' : view === 'NEIGHBORHOOD' ? 'No posts in your neighborhood yet.' : "You haven't posted anything yet."}
          </p>
        </div>
      )}
      {!error && posts !== null && posts.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {posts.map((post) => (
            <CommunityPostCard
              key={post.id}
              post={post}
              categoryLabel={categoryLabel(post.category)}
              isMine={view === 'MINE' || post.authorId === currentUser?.id}
              onOpen={() => setOpenPostId(post.id)}
              onChanged={load}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ============================== JOBS (당근알바) ==============================

function NewJobPostCard({ categories, onCreated }: { categories: JobCategory[]; onCreated: () => void }) {
  const [category, setCategory] = useState(categories[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [payType, setPayType] = useState<JobPayType>('HOURLY');
  const [payAmount, setPayAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createJobPost(category, title, description, payType, Number(payAmount));
      setTitle('');
      setDescription('');
      setPayAmount('');
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post this job.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="toss-btn toss-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + Post a job
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Post a job</h3>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      >
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What do you need done?" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the work" required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <select
          value={payType} onChange={(e) => setPayType(e.target.value as JobPayType)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        >
          <option value="HOURLY">Per hour</option>
          <option value="FIXED">Fixed price</option>
        </select>
        <input
          type="number" value={payAmount} onChange={(e) => setPayAmount(e.target.value)} placeholder="Pay (RWF)" required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </div>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Posting…' : 'Post job'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function JobPostCard({ post, categoryLabel, isMine, onChanged, onContact }: {
  post: JobPost; categoryLabel: string; isMine: boolean; onChanged: () => void; onContact: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const payLabel = `${post.payAmount.toLocaleString()} RWF${post.payType === 'HOURLY' ? '/hr' : ''}`;

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>{categoryLabel}</span>
          {post.status === 'FILLED' && (
            <span style={{ marginLeft: '8px', fontSize: '10px', fontWeight: 700, color: 'var(--toss-grey-500)', backgroundColor: 'var(--toss-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
              FILLED
            </span>
          )}
        </div>
        <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{payLabel}</span>
      </div>
      <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{post.title}</p>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{post.description}</p>
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        {isMine ? (
          <>
            {post.status === 'OPEN' && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await markJobPostFilled(post.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : 'Could not update this job.'); }
                  finally { setBusy(false); }
                }}
              >
                Mark filled
              </button>
            )}
            {post.status !== 'REMOVED' && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await removeJobPost(post.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : 'Could not remove this job.'); }
                  finally { setBusy(false); }
                }}
              >
                Remove
              </button>
            )}
          </>
        ) : (
          post.status === 'OPEN' && (
            <button className="toss-btn toss-btn-primary" disabled={busy} onClick={onContact}>
              Message poster
            </button>
          )
        )}
      </div>
    </div>
  );
}

function JobsView({ onMessagePoster }: { onMessagePoster: (conversationId: string) => void }) {
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'NEIGHBORHOOD'>('BROWSE');
  const [categories, setCategories] = useState<JobCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [posts, setPosts] = useState<JobPost[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const currentUser = getStoredUser();

  useEffect(() => {
    fetchJobCategories().then(setCategories).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const load = () => {
    setError(null);
    setPosts(null);
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchJobPostsMyNeighborhood(activeCategory ?? undefined)])
        .then(([profile, items]) => {
          setNeighborhoodName(profile.neighborhood);
          setPosts(items);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setPosts([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    const fetcher = view === 'BROWSE' ? fetchJobPosts(activeCategory ?? undefined) : fetchMyJobPosts();
    fetcher
      .then(setPosts)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load jobs.'));
  };

  useEffect(load, [view, activeCategory]);

  const categoryLabel = (id: string) => categories.find((c) => c.id === id)?.label ?? id;

  const handleContact = async (jobPostId: string) => {
    try {
      const conversation = await contactPoster(jobPostId);
      onMessagePoster(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not message this poster.');
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Find work' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : 'My posts'}
          </button>
        ))}
      </div>

      {(view === 'BROWSE' || view === 'NEIGHBORHOOD') && categories.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
          {categories.map((c) => (
            <button
              key={c.id}
              onClick={() => setActiveCategory(activeCategory === c.id ? null : c.id)}
              style={{
                whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                border: `1px solid ${activeCategory === c.id ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                color: activeCategory === c.id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: activeCategory === c.id ? 'var(--toss-blue)' : 'transparent',
              }}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}

      {view === 'MINE' && <NewJobPostCard categories={categories} onCreated={load} />}

      {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
        <NeighborhoodSetupPrompt onDone={() => load()} />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
          Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
        </p>
      )}

      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
          <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
        </div>
      )}
      {!error && posts === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
      {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && posts !== null && posts.length === 0 && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            {view === 'BROWSE' ? 'No jobs posted yet.' : view === 'NEIGHBORHOOD' ? 'No jobs in your neighborhood yet.' : "You haven't posted any jobs yet."}
          </p>
        </div>
      )}
      {!error && posts !== null && posts.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {posts.map((post) => (
            <JobPostCard
              key={post.id}
              post={post}
              categoryLabel={categoryLabel(post.category)}
              isMine={view === 'MINE' || post.posterId === currentUser?.id}
              onChanged={load}
              onContact={() => handleContact(post.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ============================== PROPERTY (당근부동산) ==============================

function NewPropertyListingCard({ propertyTypes, onCreated }: { propertyTypes: PropertyType[]; onCreated: () => void }) {
  const [listingType, setListingType] = useState<PropertyListingType>('RENT');
  const [propertyType, setPropertyType] = useState(propertyTypes[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [bedrooms, setBedrooms] = useState('');
  const [sizeSqm, setSizeSqm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createPropertyListing(
        listingType, propertyType, title, description, Number(price),
        bedrooms ? Number(bedrooms) : undefined, sizeSqm ? Number(sizeSqm) : undefined,
      );
      setTitle('');
      setDescription('');
      setPrice('');
      setBedrooms('');
      setSizeSqm('');
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
        + List a property
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>List a property</h3>
      <div style={{ display: 'flex', gap: '10px' }}>
        <select
          value={listingType} onChange={(e) => setListingType(e.target.value as PropertyListingType)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        >
          <option value="RENT">For rent</option>
          <option value="SALE">For sale</option>
        </select>
        <select
          value={propertyType} onChange={(e) => setPropertyType(e.target.value)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        >
          {propertyTypes.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
        </select>
      </div>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. 2-bedroom apartment in Kacyiru" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the property" required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" value={price} onChange={(e) => setPrice(e.target.value)}
          placeholder={listingType === 'RENT' ? 'Rent per month (RWF)' : 'Price (RWF)'} required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number" value={bedrooms} onChange={(e) => setBedrooms(e.target.value)} placeholder="Bedrooms" min="0"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number" value={sizeSqm} onChange={(e) => setSizeSqm(e.target.value)} placeholder="Size (m²)" min="1"
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

function PropertyListingCard({ listing, propertyTypeLabel, isMine, onChanged, onContact, onMessageLister }: {
  listing: PropertyListing; propertyTypeLabel: string; isMine: boolean; onChanged: () => void; onContact: () => void;
  onMessageLister: (conversationId: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService's
  // own doc comment; mirrors ListingCard's own offering state exactly.
  const [offering, setOffering] = useState(false);
  const [offerAmount, setOfferAmount] = useState('');

  const priceLabel = `${listing.price.toLocaleString()} RWF${listing.listingType === 'RENT' ? '/mo' : ''}`;
  const detailsLabel = [
    listing.bedrooms != null ? `${listing.bedrooms} bd` : null,
    listing.sizeSqm != null ? `${listing.sizeSqm} m²` : null,
  ].filter(Boolean).join(' · ');

  const handleMakeOffer = async () => {
    const amount = Number(offerAmount);
    if (!amount || amount <= 0) return;
    setBusy(true);
    setError(null);
    try {
      const offer = await makePropertyOffer(listing.id, amount);
      setOffering(false);
      setOfferAmount('');
      onMessageLister(offer.conversationId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this offer.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>
            {listing.listingType === 'RENT' ? 'For rent' : 'For sale'} · {propertyTypeLabel}
          </span>
          {listing.status === 'TAKEN' && (
            <span style={{ marginLeft: '8px', fontSize: '10px', fontWeight: 700, color: 'var(--toss-grey-500)', backgroundColor: 'var(--toss-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
              TAKEN
            </span>
          )}
        </div>
        <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{priceLabel}</span>
      </div>
      <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{listing.title}</p>
      {detailsLabel && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{detailsLabel}</p>}
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{listing.description}</p>
      {offering && (
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="number"
            value={offerAmount}
            onChange={(e) => setOfferAmount(e.target.value)}
            placeholder="Your offer (RWF)"
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button className="toss-btn toss-btn-primary" disabled={busy || !offerAmount} onClick={handleMakeOffer}>
            Send
          </button>
        </div>
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        {isMine ? (
          <>
            {listing.status === 'AVAILABLE' && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await markPropertyListingTaken(listing.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : 'Could not update this listing.'); }
                  finally { setBusy(false); }
                }}
              >
                Mark taken
              </button>
            )}
            {listing.status !== 'REMOVED' && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await removePropertyListing(listing.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : 'Could not remove this listing.'); }
                  finally { setBusy(false); }
                }}
              >
                Remove
              </button>
            )}
          </>
        ) : (
          listing.status === 'AVAILABLE' && !offering && (
            <>
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={onContact}>
                Message lister
              </button>
              <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => setOffering(true)}>
                Make an offer
              </button>
            </>
          )
        )}
      </div>
    </div>
  );
}

function PropertyView({ onMessageLister }: { onMessageLister: (conversationId: string) => void }) {
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'NEIGHBORHOOD'>('BROWSE');
  const [propertyTypes, setPropertyTypes] = useState<PropertyType[]>([]);
  const [listingTypeFilter, setListingTypeFilter] = useState<PropertyListingType | null>(null);
  const [propertyTypeFilter, setPropertyTypeFilter] = useState<string | null>(null);
  const [listings, setListings] = useState<PropertyListing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const currentUser = getStoredUser();

  useEffect(() => {
    fetchPropertyTypes().then(setPropertyTypes).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const load = () => {
    setError(null);
    setListings(null);
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchPropertyListingsMyNeighborhood()])
        .then(([profile, items]) => {
          setNeighborhoodName(profile.neighborhood);
          setListings(items);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setListings([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    const fetcher = view === 'BROWSE'
      ? fetchPropertyListings(listingTypeFilter ?? undefined, propertyTypeFilter ?? undefined)
      : fetchMyPropertyListings();
    fetcher
      .then(setListings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load listings.'));
  };

  useEffect(load, [view, listingTypeFilter, propertyTypeFilter]);

  const propertyTypeLabel = (id: string) => propertyTypes.find((t) => t.id === id)?.label ?? id;

  const handleContact = async (propertyListingId: string) => {
    try {
      const conversation = await contactLister(propertyListingId);
      onMessageLister(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not message this lister.');
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Browse' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : 'My listings'}
          </button>
        ))}
      </div>

      {view === 'BROWSE' && (
        <div style={{ display: 'flex', gap: '6px', marginBottom: '10px' }}>
          {(['RENT', 'SALE'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setListingTypeFilter(listingTypeFilter === t ? null : t)}
              style={{
                padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                border: `1px solid ${listingTypeFilter === t ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                color: listingTypeFilter === t ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: listingTypeFilter === t ? 'var(--toss-blue)' : 'transparent',
              }}
            >
              {t === 'RENT' ? 'For rent' : 'For sale'}
            </button>
          ))}
        </div>
      )}

      {view === 'BROWSE' && propertyTypes.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
          {propertyTypes.map((t) => (
            <button
              key={t.id}
              onClick={() => setPropertyTypeFilter(propertyTypeFilter === t.id ? null : t.id)}
              style={{
                whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                border: `1px solid ${propertyTypeFilter === t.id ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                color: propertyTypeFilter === t.id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: propertyTypeFilter === t.id ? 'var(--toss-blue)' : 'transparent',
              }}
            >
              {t.label}
            </button>
          ))}
        </div>
      )}

      {view === 'MINE' && <NewPropertyListingCard propertyTypes={propertyTypes} onCreated={load} />}

      {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
        <NeighborhoodSetupPrompt onDone={() => load()} />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
          Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
        </p>
      )}

      {error && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
          <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>Retry</button>
        </div>
      )}
      {!error && listings === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
      {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && listings !== null && listings.length === 0 && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            {view === 'BROWSE' ? 'No properties listed yet.' : view === 'NEIGHBORHOOD' ? 'No properties in your neighborhood yet.' : "You haven't listed any properties yet."}
          </p>
        </div>
      )}
      {!error && listings !== null && listings.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {listings.map((listing) => (
            <PropertyListingCard
              key={listing.id}
              listing={listing}
              propertyTypeLabel={propertyTypeLabel(listing.propertyType)}
              isMine={view === 'MINE' || listing.listerId === currentUser?.id}
              onChanged={load}
              onContact={() => handleContact(listing.id)}
              onMessageLister={onMessageLister}
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

function EatsOrderCard({ order, restaurant, action }: { order: EatsOrder; restaurant?: ShoppingMerchant; action?: React.ReactNode }) {
  const [showRoute, setShowRoute] = useState(false);
  const canShowRoute = restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null;

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
      {canShowRoute && (
        <button className="toss-btn toss-btn-secondary" onClick={() => setShowRoute((v) => !v)}>
          {showRoute ? 'Hide route' : '🚗 View real delivery route'}
        </button>
      )}
      {showRoute && restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null && (
        <RouteMiniMap
          fromLat={restaurant.latitude}
          fromLng={restaurant.longitude}
          toLat={order.deliveryLatitude}
          toLng={order.deliveryLongitude}
          fromLabel={restaurant.businessName}
          toLabel="Delivery address"
        />
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

function MyEatsOrdersView({ onReorder, reorderingId, restaurants }: { onReorder: (order: EatsOrder) => void; reorderingId: string | null; restaurants: ShoppingMerchant[] | null }) {
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
          restaurant={restaurants?.find((r) => r.merchantId === o.restaurantId)}
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
          <MyEatsOrdersView onReorder={handleReorder} reorderingId={reorderingId} restaurants={allRestaurants} />
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

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification this
// row's own text named. Keyed by merchantId so a buyer can browse merchant A, add
// items, go back, browse merchant B, add items there too, and check out everything
// in one pass -- each merchant's line items get a real, separate placeOrder() call
// (the backend already only ever accepted one merchantId per order; no backend
// change needed at all, this is purely a client-side cart-architecture change).
interface CommerceCartGroup {
  businessName: string;
  lines: Record<string, { product: CommerceProduct; quantity: number }>;
}
type CommerceCart = Record<string, CommerceCartGroup>;

function cartTotalItems(cart: CommerceCart): number {
  return Object.values(cart).reduce((sum, group) => sum + Object.values(group.lines).reduce((s, l) => s + l.quantity, 0), 0);
}

function ProductCatalogView({
  merchant, cart, onSetQty, onBack, onViewCart,
}: {
  merchant: ShoppingMerchant;
  cart: CommerceCart;
  onSetQty: (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => void;
  onBack: () => void;
  onViewCart: () => void;
}) {
  const [catalog, setCatalog] = useState<{ businessName: string; products: CommerceProduct[] } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMerchantProducts(merchant.merchantId)
      .then((r) => setCatalog({ businessName: r.merchant.businessName, products: r.products }))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this catalog.'));
  };

  useEffect(load, [merchant.merchantId]);

  const myLines = cart[merchant.merchantId]?.lines ?? {};
  const qtyFor = (productId: string) => myLines[productId]?.quantity ?? 0;
  const totalCartItems = cartTotalItems(cart);

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
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: totalCartItems > 0 ? '80px' : 0 }}>
          {catalog.products.map((item) => (
            <div key={item.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontSize: '15px', fontWeight: 700 }}>{item.name}</p>
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{item.price.toLocaleString()} RWF</p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <button onClick={() => onSetQty(merchant, item, qtyFor(item.id) - 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{qtyFor(item.id)}</span>
                <button onClick={() => onSetQty(merchant, item, qtyFor(item.id) + 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
              </div>
            </div>
          ))}
        </div>
      )}
      {totalCartItems > 0 && (
        <button
          className="toss-btn toss-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={onViewCart}
        >
          View cart ({totalCartItems} item{totalCartItems === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

interface CommerceCheckoutResult {
  merchantId: string;
  businessName: string;
  success: boolean;
  order?: CommerceOrder;
  error?: string;
}

function MultiCartView({
  cart, onBack, onSetQty, onCheckedOut,
}: {
  cart: CommerceCart;
  onBack: () => void;
  onSetQty: (merchantId: string, productId: string, quantity: number) => void;
  onCheckedOut: (results: CommerceCheckoutResult[]) => void;
}) {
  const [address, setAddress] = useState('');
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const groups = Object.entries(cart).filter(([, g]) => Object.values(g.lines).some((l) => l.quantity > 0));
  const grandTotal = groups.reduce(
    (sum, [, g]) => sum + Object.values(g.lines).reduce((s, l) => s + l.product.price * l.quantity, 0),
    0,
  );

  // Real per-seller order splitting -- each merchant group becomes its own real,
  // independent placeOrder() call (its own Idempotency-Key, its own wallet-to-wallet
  // ledger transaction). Sequential, not Promise.all: these are real money-moving
  // calls against the same buyer wallet, and a clear one-at-a-time result list is
  // more honest than a swallowed Promise.allSettled. A failure on one merchant's
  // order does not block or roll back any other -- exactly how a real multi-seller
  // checkout behaves (each seller is charged/fulfilled independently in real life).
  const handlePlaceOrders = async (e: React.FormEvent) => {
    e.preventDefault();
    setPlacing(true);
    setError(null);
    const results: CommerceCheckoutResult[] = [];
    for (const [merchantId, group] of groups) {
      const items = Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => ({ productId, quantity: l.quantity }));
      try {
        const result = await placeOrder(merchantId, items, address.trim());
        results.push({ merchantId, businessName: group.businessName, success: true, order: result.order });
      } catch (err) {
        results.push({ merchantId, businessName: group.businessName, success: false, error: err instanceof ApiError ? err.message : 'Could not place this order.' });
      }
    }
    setPlacing(false);
    onCheckedOut(results);
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to shop">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Your cart</h3>
      </div>
      {groups.length === 0 ? (
        <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Your cart is empty.</p></div>
      ) : (
        <form onSubmit={handlePlaceOrders} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {groups.map(([merchantId, group]) => (
            <div key={merchantId} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <p style={{ fontSize: '14px', fontWeight: 700 }}>{group.businessName}</p>
              {Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => (
                <div key={productId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
                  <span>{l.product.name} x{l.quantity}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span>{(l.product.price * l.quantity).toLocaleString()} RWF</span>
                    <button type="button" onClick={() => onSetQty(merchantId, productId, 0)} style={{ color: 'var(--toss-grey-500)', fontSize: '12px' }}>Remove</button>
                  </div>
                </div>
              ))}
            </div>
          ))}
          <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '15px', fontWeight: 700 }}>
              <span>Total ({groups.length} order{groups.length === 1 ? '' : 's'})</span>
              <span>{grandTotal.toLocaleString()} RWF</span>
            </div>
            <input
              type="text" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Delivery address" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
            />
            {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
            <button type="submit" className="toss-btn toss-btn-primary" disabled={placing || !address.trim()}>
              {placing ? 'Placing orders…' : `Place ${groups.length} order${groups.length === 1 ? '' : 's'}`}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

function MultiCartResultsView({ results, onDone }: { results: CommerceCheckoutResult[]; onDone: () => void }) {
  const successCount = results.filter((r) => r.success).length;
  return (
    <div className="toss-card" style={{ padding: '28px' }}>
      <div style={{ textAlign: 'center', marginBottom: '20px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700 }}>
          {successCount} of {results.length} order{results.length === 1 ? '' : 's'} placed
        </h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
        {results.map((r) => (
          <div key={r.merchantId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
            <span style={{ fontWeight: 600 }}>{r.businessName}</span>
            {r.success ? (
              <span style={{ color: 'var(--toss-green)' }}>{r.order!.totalAmount.toLocaleString()} RWF — placed</span>
            ) : (
              <span style={{ color: '#E53935' }}>{r.error}</span>
            )}
          </div>
        ))}
      </div>
      <button className="toss-btn toss-btn-secondary" style={{ width: '100%' }} onClick={onDone}>
        {results.some((r) => !r.success) ? 'Back to cart' : 'Done'}
      </button>
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
  const [cart, setCart] = useState<CommerceCart>({});
  const [showCart, setShowCart] = useState(false);
  const [results, setResults] = useState<CommerceCheckoutResult[] | null>(null);

  // Real Coupang-style commerce (rw.itunda.commerce) -- deliberately reuses the same
  // GET /api/v1/shopping/merchants catalog the Shopping tab (Toss Shopping cashback
  // browsing) already uses, matching how Android/iOS's own Shop tab reuses the same
  // merchant directory rather than inventing a second one.
  const load = () => {
    setError(null);
    fetchShoppingCatalog().then(setMerchants).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load merchants.'));
  };

  useEffect(load, []);

  const setQtyByMerchant = (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => {
    setCart((prev) => {
      const next = { ...prev };
      const existing = next[merchant.merchantId] ?? { businessName: merchant.businessName, lines: {} };
      const lines = { ...existing.lines };
      if (quantity <= 0) delete lines[product.id];
      else lines[product.id] = { product, quantity };
      if (Object.keys(lines).length === 0) delete next[merchant.merchantId];
      else next[merchant.merchantId] = { ...existing, lines };
      return next;
    });
  };

  const setQtyByIds = (merchantId: string, productId: string, quantity: number) => {
    setCart((prev) => {
      const existing = prev[merchantId];
      if (!existing) return prev;
      const next = { ...prev };
      const lines = { ...existing.lines };
      if (quantity <= 0) delete lines[productId];
      else if (lines[productId]) lines[productId] = { ...lines[productId], quantity };
      if (Object.keys(lines).length === 0) delete next[merchantId];
      else next[merchantId] = { ...existing, lines };
      return next;
    });
  };

  const handleCheckedOut = (checkoutResults: CommerceCheckoutResult[]) => {
    // Only clear the merchants that actually succeeded -- a failed group's items
    // stay in the cart so the buyer doesn't lose their selection and can retry
    // (e.g. after fixing the delivery address or topping up their wallet).
    setCart((prev) => {
      const next = { ...prev };
      checkoutResults.filter((r) => r.success).forEach((r) => delete next[r.merchantId]);
      return next;
    });
    setResults(checkoutResults);
    setShowCart(false);
  };

  if (results) {
    return (
      <MultiCartResultsView
        results={results}
        onDone={() => { setResults(null); setSelected(null); setView('ORDERS'); }}
      />
    );
  }

  if (showCart) {
    return <MultiCartView cart={cart} onBack={() => setShowCart(false)} onSetQty={setQtyByIds} onCheckedOut={handleCheckedOut} />;
  }

  if (selected) {
    return (
      <ProductCatalogView
        merchant={selected}
        cart={cart}
        onSetQty={setQtyByMerchant}
        onBack={() => setSelected(null)}
        onViewCart={() => setShowCart(true)}
      />
    );
  }

  const totalItems = cartTotalItems(cart);

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
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: totalItems > 0 ? '80px' : 0 }}>
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
      {view === 'BROWSE' && totalItems > 0 && (
        <button
          className="toss-btn toss-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCart(true)}
        >
          View cart ({totalItems} item{totalItems === 1 ? '' : 's'})
        </button>
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
    { id: 'STOCKS', label: 'Invest' },
    { id: 'MESSAGES', label: 'Messages' },
    { id: 'MARKETPLACE', label: 'Marketplace' },
    { id: 'COMMUNITY', label: 'Community' },
    { id: 'JOBS', label: 'Jobs' },
    { id: 'PROPERTY', label: 'Property' },
    { id: 'MAP', label: 'Map' },
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
      {tab === 'STOCKS' && <StocksView />}
      {tab === 'MESSAGES' && (
        <MessagesView
          initialConversationId={pendingConversationId}
          onConsumedInitial={() => setPendingConversationId(null)}
        />
      )}
      {tab === 'MARKETPLACE' && <MarketplaceView onMessageSeller={handleMessageSeller} />}
      {tab === 'COMMUNITY' && <CommunityView />}
      {tab === 'JOBS' && <JobsView onMessagePoster={handleMessageSeller} />}
      {tab === 'PROPERTY' && <PropertyView onMessageLister={handleMessageSeller} />}
      {tab === 'MAP' && <MapView />}
      {tab === 'CERTIFICATE' && <CertificateView />}
      {tab === 'SHOPPING' && <ShoppingView />}
    </div>
  );
}
