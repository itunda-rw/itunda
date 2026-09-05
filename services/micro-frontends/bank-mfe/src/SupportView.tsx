// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// customer support tickets (own lib/support.ts data layer, exactly one external
// call site -- `<SupportView .../>` inside the SUPPORT tab).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { createSupportTicket, fetchSupportTickets, type SupportTicket, type SupportTicketCategory } from './lib/support';
import { fetchTransactions, type Transaction } from './lib/account';
import { useDeferredLoading } from './useDeferredLoading';

// Real customer support tickets (2026-07-22) -- found fully built on the backend
// (rw.itunda.support) with zero client UI anywhere. A ticket is always tied to a
// specific transaction (see SupportTicket.kt's own doc comment for why), so this view
// has the user pick one from their real transaction history rather than filing a
// free-floating complaint.
const SUPPORT_CATEGORIES: SupportTicketCategory[] = ['GENERAL', 'PAYMENT_DISPUTE', 'ACCOUNT_TAKEOVER', 'RIDE_ISSUE'];

// Real Uber "trip issue report" hand-off (2026-08-16) -- a completed ride's own
// "Report an issue" button lands here with the trip's real transactionId/RIDE_ISSUE
// category already chosen, same pending-hand-off pattern pendingConversationId already
// established, rather than dropping the rider on a blank category picker they'd have
// to know to select the right transaction from themselves.
export function SupportView({ initialTransactionId, initialCategory, onConsumedInitial }: { initialTransactionId?: string | null; initialCategory?: SupportTicketCategory; onConsumedInitial?: () => void } = {}) {
  const { t } = useI18n();
  const [tickets, setTickets] = useState<SupportTicket[] | null>(null);
  const showSkeleton = useDeferredLoading(tickets === null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showNewForm, setShowNewForm] = useState(!!initialTransactionId);
  const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(initialTransactionId ?? null);
  const [category, setCategory] = useState<SupportTicketCategory>(initialCategory ?? 'GENERAL');
  const [description, setDescription] = useState('');

  const refresh = () => {
    setError(null);
    Promise.all([fetchSupportTickets(), fetchTransactions()])
      .then(([t, tx]) => { setTickets(t); setTransactions(tx); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);
  // Consume the pending ride-issue hand-off exactly once on mount -- clears the
  // parent's pending state so navigating back to Support later for an unrelated
  // ticket doesn't keep re-pre-filling the same stale ride transaction.
  useEffect(() => {
    if (initialTransactionId) onConsumedInitial?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTransactionId) return;
    setBusy(true);
    setError(null);
    try {
      await createSupportTicket(selectedTransactionId, category, description);
      setSelectedTransactionId(null); setDescription(''); setShowNewForm(false);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {!showNewForm ? (
        <button className="itunda-btn itunda-btn-primary" onClick={() => setShowNewForm(true)}>Report an issue with a transaction</button>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {initialTransactionId && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', fontWeight: 700 }}>
              🚗 Reporting an issue with this ride's payment
            </p>
          )}
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>Which transaction?</p>
          {transactions.slice(0, 10).map((tx) => (
            <label key={tx.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {tx.description} · {tx.currency} {tx.amount.toLocaleString('en-US')}
              <input type="radio" name="tx" checked={selectedTransactionId === tx.id} onChange={() => setSelectedTransactionId(tx.id)} />
            </label>
          ))}
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>Category</p>
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
            {SUPPORT_CATEGORIES.map((c) => (
              <button
                type="button" key={c}
                className={category === c ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                onClick={() => setCategory(c)}
              >
                {c}
              </button>
            ))}
          </div>
          <textarea
            value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the issue" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy || !selectedTransactionId || !description}>
            {busy ? 'Submitting…' : 'Submit ticket'}
          </button>
        </form>
      )}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Your tickets</h3>
      {tickets === null ? (showSkeleton ? <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} /> : null) :
        tickets.length === 0 ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>You have no support tickets.</p> :
        tickets.map((t) => (
          <div key={t.id} className="itunda-flat-section">
            <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{t.category}</h4>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t.description}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Status: {t.status}</p>
            {t.resolution && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Resolution: {t.resolution}</p>}
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Filed: {t.createdAt}</p>
          </div>
        ))}
    </div>
  );
}
