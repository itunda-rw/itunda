// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Second
// slice of the Talk/messaging cluster -- the emoticon-pack picker/store + gift-
// voucher composer, both shown BOTH from ConversationThread and GroupThread
// (still in BankDashboard.tsx, not yet extracted) but fully self-contained
// themselves (own data fetching, no shared state with either caller beyond the
// callback props they're given).

import { useEffect, useState } from 'react';
import { ShoppingBagGlyph } from './icons/ItundaFaceMisc';
import { VoucherTicket } from './icons/ItundaFaceGifts';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { FullScreenFlow } from './FullScreenFlow';
import {
  fetchEmoticonPacks, fetchOwnedEmoticonPacks, fetchPackEmoticons, giftEmoticonPack, purchaseEmoticonPack,
  type Emoticon, type EmoticonPack, type OwnedEmoticonPack,
} from './lib/emoticons';
import { purchaseGiftVoucher } from './lib/giftVouchers';
import { searchProducts, type ProductSearchResult } from './lib/shopping';

// Real emoticon picker -- shown owned packs only (each tappable emoticon sends
// immediately); a real "Get more" link opens the full store to browse/purchase.
export function EmoticonPickerPanel({
  onSend, onOpenStore,
}: {
  onSend: (emoticonId: string) => void;
  onOpenStore: () => void;
}) {
  const [ownedPacks, setOwnedPacks] = useState<OwnedEmoticonPack[] | null>(null);
  const [selectedPackId, setSelectedPackId] = useState<string | null>(null);
  const [packEmoticons, setPackEmoticons] = useState<Emoticon[] | null>(null);
  const [packTitles, setPackTitles] = useState<Record<string, string>>({});

  useEffect(() => {
    Promise.all([fetchOwnedEmoticonPacks(), fetchEmoticonPacks()])
      .then(([owned, allPacks]) => {
        setOwnedPacks(owned);
        setPackTitles(Object.fromEntries(allPacks.map((p) => [p.id, p.title])));
        if (owned.length > 0) setSelectedPackId(owned[0].packId);
      })
      .catch(() => setOwnedPacks([]));
  }, []);

  useEffect(() => {
    if (!selectedPackId) return;
    setPackEmoticons(null);
    fetchPackEmoticons(selectedPackId).then(setPackEmoticons).catch(() => setPackEmoticons([]));
  }, [selectedPackId]);

  return (
    <div style={{ padding: '10px', borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', marginBottom: '10px' }}>
      {ownedPacks === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : ownedPacks.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>You don't own any emoticon packs yet.</p>
          <button type="button" className="itunda-btn itunda-btn-primary" onClick={onOpenStore} style={{ padding: '8px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            Browse Emoticon Store
          </button>
        </div>
      ) : (
        <>
          <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '8px' }}>
            {ownedPacks.map((op) => (
              <button
                key={op.packId}
                type="button"
                onClick={() => setSelectedPackId(op.packId)}
                className={selectedPackId === op.packId ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                style={{ padding: '6px 10px', fontSize: 'var(--itunda-type-scale-12-size)', whiteSpace: 'nowrap' }}
              >
                {packTitles[op.packId] ?? op.packId}
              </button>
            ))}
            <button type="button" onClick={onOpenStore} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 10px', fontSize: 'var(--itunda-type-scale-12-size)', whiteSpace: 'nowrap' }}>
              Get more
            </button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px' }}>
            {packEmoticons === null ? (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
            ) : (
              packEmoticons.map((e) => (
                <button
                  key={e.id}
                  type="button"
                  aria-label="Send sticker"
                  onClick={() => onSend(e.id)}
                  style={{ border: 'none', background: 'none', padding: '4px', cursor: 'pointer' }}
                >
                  <img src={e.imageUrl} alt="" style={{ width: '100%', aspectRatio: '1', objectFit: 'contain' }} />
                </button>
              ))
            )}
          </div>
        </>
      )}
    </div>
  );
}

// Real Emoticon Store -- browse every real active pack, buy one (once-off purchase,
// same "buy it once, own it" model Shop/Insurance already use), or gift one to a
// friend by phone number.
export function EmoticonStoreModal({ onClose }: { onClose: () => void }) {
  const { t } = useI18n();
  const [packs, setPacks] = useState<EmoticonPack[] | null>(null);
  const [ownedPackIds, setOwnedPackIds] = useState<Set<string>>(new Set());
  const [busyPackId, setBusyPackId] = useState<string | null>(null);
  const [giftingPackId, setGiftingPackId] = useState<string | null>(null);
  const [giftPhone, setGiftPhone] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    Promise.all([fetchEmoticonPacks(), fetchOwnedEmoticonPacks()])
      .then(([allPacks, owned]) => {
        setPacks(allPacks);
        setOwnedPackIds(new Set(owned.map((o) => o.packId)));
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  const buy = async (packId: string) => {
    setBusyPackId(packId);
    setError(null);
    try {
      await purchaseEmoticonPack(packId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyPackId(null);
    }
  };

  const gift = async (packId: string) => {
    setBusyPackId(packId);
    setError(null);
    setMessage(null);
    try {
      await giftEmoticonPack(packId, giftPhone.trim());
      setMessage('Pack gifted!');
      setGiftingPackId(null);
      setGiftPhone('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyPackId(null);
    }
  };

  // Real fix (full-app audit, docs/UI_UX_GUIDELINES.md rule 1): was a classic
  // centered-card-on-dark-overlay modal, the same "old" pattern the rest of this
  // file has already moved away from in favor of FullScreenFlow (TransferFlow,
  // CreateGoalForm, Grow31/WeeklySavings, etc.). Same fix applied to
  // ForwardPickerModal below, which had the identical shape.
  return (
    <FullScreenFlow>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><ShoppingBagGlyph size={18} /> Emoticon Store</p>
          {/* Real touch-target-size fix (item 244, web accessibility sweep):
              no padding meant the clickable area was just the bare glyph,
              well under WCAG 2.5.8's 24x24 CSS-pixel AA minimum. */}
          <button type="button" aria-label="Close" onClick={onClose} style={{ border: 'none', background: 'none', fontSize: 'var(--itunda-type-scale-16-size)', padding: '8px', minWidth: '24px', minHeight: '24px' }}>×</button>
        </div>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}
        {packs === null ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
        ) : (
          packs.map((pack) => {
            const owned = ownedPackIds.has(pack.id);
            return (
              <div key={pack.id} style={{ display: 'flex', flexDirection: 'column', gap: '6px', padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)' }}>
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                  <img src={pack.thumbnailUrl} alt="" style={{ width: '48px', height: '48px', objectFit: 'contain' }} />
                  <div style={{ flex: 1 }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{pack.title}</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{pack.artistName} · {pack.price.toLocaleString('en-US')} RWF</p>
                  </div>
                  <button
                    type="button"
                    className={owned ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'}
                    disabled={owned || busyPackId === pack.id}
                    onClick={() => buy(pack.id)}
                    style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                  >
                    {owned ? 'Owned' : busyPackId === pack.id ? '…' : 'Buy'}
                  </button>
                  <button
                    type="button"
                    className="itunda-btn itunda-btn-secondary"
                    disabled={busyPackId === pack.id}
                    onClick={() => setGiftingPackId(giftingPackId === pack.id ? null : pack.id)}
                    style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                  >
                    Gift
                  </button>
                </div>
                {giftingPackId === pack.id && (
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <input
                      type="tel"
                      value={giftPhone}
                      onChange={(e) => setGiftPhone(e.target.value)}
                      placeholder="Recipient phone number"
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
                    />
                    <button
                      type="button"
                      className="itunda-btn itunda-btn-primary"
                      disabled={busyPackId === pack.id || !giftPhone.trim()}
                      onClick={() => gift(pack.id)}
                      style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    >
                      {busyPackId === pack.id ? '…' : 'Send gift'}
                    </button>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </FullScreenFlow>
  );
}

// Real gift-voucher composer -- search for a real product to gift (same real Kakao
// gifticon UX of searching for what to send, e.g. "스타벅스 아메리카노", rather than
// browsing a merchant catalog first), pick one, confirm with the recipient's phone
// number. Product-only v1 (see lib/giftVouchers.ts's own doc comment) -- the flat-
// cash-amount-at-a-merchant path is a real, deliberately deferred follow-up.
export function GiftVoucherComposerPanel({
  onSent, onCancel,
}: {
  onSent: () => void;
  onCancel: () => void;
}) {
  const { t } = useI18n();
  const [phone, setPhone] = useState('');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ProductSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<ProductSearchResult | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const search = async (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim().length < 2) return;
    setSearching(true);
    setError(null);
    try {
      setResults(await searchProducts(query.trim()));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSearching(false);
    }
  };

  const send = async () => {
    if (!selected || !phone.trim()) return;
    setSending(true);
    setError(null);
    try {
      await purchaseGiftVoucher(phone.trim(), selected.merchantId, selected.id);
      onSent();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', marginBottom: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><VoucherTicket size={16} /> Send a gift voucher</p>
      <input
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        placeholder="Recipient phone number"
        style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      {selected ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 10px', background: 'var(--itunda-grey-100)', borderRadius: '8px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{selected.name} · {selected.merchantName} · {selected.price.toLocaleString('en-US')} RWF</span>
          <button type="button" onClick={() => setSelected(null)} style={{ border: 'none', background: 'none', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>Change</button>
        </div>
      ) : (
        <>
          <form onSubmit={search} style={{ display: 'flex', gap: '8px' }}>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search a product to gift"
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={searching || query.trim().length < 2} style={{ padding: '10px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {searching ? '…' : 'Search'}
            </button>
          </form>
          {results !== null && (
            results.length === 0 ? (
              <EmptyState message="Nothing matched that search — try a different word or browse by category." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', maxHeight: '160px', overflowY: 'auto' }}>
                {results.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setSelected(p)}
                    style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', background: 'none', fontSize: 'var(--itunda-type-scale-13-size)', textAlign: 'left' }}
                  >
                    <span>{p.name} · {p.merchantName}</span>
                    <span>{p.price.toLocaleString('en-US')} RWF</span>
                  </button>
                ))}
              </div>
            )
          )}
        </>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          type="button"
          className="itunda-btn itunda-btn-primary"
          disabled={!selected || !phone.trim() || sending}
          onClick={send}
          style={{ flex: 1, padding: '10px' }}
        >
          {sending ? '…' : 'Send gift voucher'}
        </button>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ padding: '10px 16px' }} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}
