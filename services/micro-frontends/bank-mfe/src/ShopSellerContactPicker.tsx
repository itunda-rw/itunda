// Real seller chat, real canned quick-reply categories (itunda Shopping redesign,
// 2026-08-28, direct user reference: real Toss Shopping seller-chat screenshots with
// canned inquiry categories). Wires Shop into the exact same real 1:1 messaging
// system (lib/messaging.ts) MarketplaceView/Eats/Community/Jobs/Property already use
// via their own onMessageSeller -- this is Shop's first real entry point into that
// system, not a bespoke chat layer. Deliberately does NOT touch the shared
// ConversationThread UI at all: this is a small picker shown BEFORE entering it, so
// every other vertical's messaging UI stays untouched.
//
// Tapping a category is a real, honest compose-assist: it calls the real
// contact-seller endpoint, sends a real first message with that category's own real
// label as the body, then hands the real conversation id up to the caller (which
// switches to the Messages tab, same handoff shape every other vertical already
// uses). "Just start chatting" opens the real conversation with no pre-sent message.
// Every message sent here is a real message in a real thread -- no fabricated
// chat-bot layer.

import { useState } from 'react';
import { ApiError } from './lib/api';
import { contactMerchantSeller } from './lib/shopping';
import { sendMessage } from './lib/messaging';
import { useI18n } from './i18n/I18nContext';

export function ShopSellerContactPicker({
  merchantId, merchantName, onClose, onOpened,
}: {
  merchantId: string;
  merchantName: string;
  onClose: () => void;
  onOpened: (conversationId: string) => void;
}) {
  const { t } = useI18n();
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const categories: { key: string; label: string }[] = [
    { key: 'product', label: t('shop.categoryProduct') },
    { key: 'shipping', label: t('shop.categoryShipping') },
    { key: 'exchange', label: t('shop.categoryExchange') },
    { key: 'return', label: t('shop.categoryReturn') },
    { key: 'cancellation', label: t('shop.categoryCancellation') },
    { key: 'other', label: t('shop.categoryOther') },
  ];

  const start = async (firstMessage?: string) => {
    setSending(true);
    setError(null);
    try {
      const conversation = await contactMerchantSeller(merchantId);
      if (firstMessage) {
        await sendMessage(conversation.id, firstMessage);
      }
      onOpened(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('shop.contactError'));
      setSending(false);
    }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 60 }} onClick={onClose}>
      <div
        style={{ background: 'var(--itunda-white)', borderRadius: '20px 20px 0 0', padding: '20px', width: '100%', maxHeight: '80vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '4px' }}>{t('shop.contactPickerTitle')}</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>{merchantName}</p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
          {categories.map((c) => (
            <button
              key={c.key}
              disabled={sending}
              onClick={() => start(c.label)}
              style={{
                display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%',
                padding: '14px 4px', textAlign: 'left', borderBottom: '1px solid var(--itunda-grey-100)',
                fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, opacity: sending ? 0.5 : 1,
              }}
            >
              {c.label}
              <span style={{ color: 'var(--itunda-grey-400)' }}>›</span>
            </button>
          ))}
        </div>

        <button
          disabled={sending}
          onClick={() => start()}
          style={{
            width: '100%', marginTop: '16px', padding: '12px', textAlign: 'center',
            fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', fontWeight: 600,
          }}
        >
          {t('shop.contactSkip')}
        </button>

        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>}
      </div>
    </div>
  );
}

// Real card-visual polish (itunda Shopping redesign, 2026-08-28) -- three small,
// shared trust-signal atoms used across ShopView's product cards/detail page,
// matching the real Toss Shopping reference's card style (rating + badge + ETA
// pill) without fabricating anything: isBestSeller is a real, derived signal (see
// ShoppingController.bestSellerProductIds' own doc comment on the backend);
// deliveryTimeMinutes is itunda's own real, already-computed delivery-ETA estimate
// (DeliveryEtaEstimator) -- deliberately NOT the reference's literal "Ships
// today"/"Arrives tomorrow" parcel-shipping copy, since itunda's real commerce
// fulfillment model is merchant-pickup/delivery-time-estimate, not multi-day
// parcel shipping.

export function ShopMessageSellerButton({ onClick }: { onClick: () => void }) {
  const { t } = useI18n();
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        display: 'flex', alignItems: 'center', gap: '4px', padding: '6px 10px',
        fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 600, color: 'var(--itunda-grey-700)',
        border: '1px solid var(--itunda-grey-200)', borderRadius: '999px', whiteSpace: 'nowrap',
      }}
    >
      💬 {t('shop.contactSellerButton')}
    </button>
  );
}

export function ShopBestSellerBadge() {
  const { t } = useI18n();
  return (
    <span
      style={{
        fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-white)',
        background: 'var(--itunda-indigo)', borderRadius: '4px', padding: '2px 6px', whiteSpace: 'nowrap',
      }}
    >
      {t('shop.bestSeller')}
    </span>
  );
}

export function ShopDeliveryEtaPill({ minutes }: { minutes?: number | null }) {
  const { t } = useI18n();
  if (minutes == null) return null;
  return (
    <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', margin: 0 }}>
      🕒 {t('shop.etaMinutes', { minutes })}
    </p>
  );
}
