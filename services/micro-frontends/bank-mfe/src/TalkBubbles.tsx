// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). First
// slice of the large Talk/messaging cluster (budgeted as its own dedicated pass,
// not combined with other work, per this thread's own standing note) -- the
// shared, purely-presentational message-bubble building blocks BOTH
// ConversationThread (1:1 chat) and GroupThread (group chat) render inline,
// confirmed via a real usage grep before bundling (chatMessageTime/
// shouldShowChatTimestamp/MessageReactions/EmoticonBubble all have call sites in
// both screens; OfferBubble/GiftBubble/GiftVoucherBubble are 1:1-chat-only today
// but kept alongside them as the same shape of small, low-coupling component).
// `OfferBubbleData`/`Gift`/`GiftVoucherStatus` types are re-exported/shared since
// ConversationThread (not yet extracted, still in BankDashboard.tsx) needs them
// too.

import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { SmilePlus } from 'lucide-react';
import { GiftGlyph, VoucherTicket } from './icons/ItundaFaceGifts';
import { MoneyBagGlyph } from './icons/ItundaFaceMisc';
import { ReactionGlyph } from './icons/ItundaFace';
import { ApiError } from './lib/api';
import { GIFT_THEME_LABELS, type Gift, type GiftStatus } from './lib/gift';
import { extendGiftVoucherExpiry, type GiftVoucher, type GiftVoucherStatus } from './lib/giftVouchers';
import { type ReactionGroup } from './lib/messaging';

// Real quick-react palette -- a small fixed set (matching most real chat apps' own
// "long-press to react" quick palette) rather than a full emoji picker, kept simple
// since this web client has no native emoji-keyboard integration to lean on.
const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢'];

export function chatMessageTime(sentAt: string) {
  const timestamp = new Date(sentAt);
  if (Number.isNaN(timestamp.getTime())) return '';
  return timestamp.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

// Real Kakao/Toss/iMessage-style collapsed-per-run timestamp convention
// (docs/DESIGN_REFERENCES.md Talk section, recommendation #8's "remaining polish
// gap": "each message shows its own timestamp, not grouped by consecutive-run"),
// ported from the same-day Android/iOS fix (HoodShared.kt's/TalkScreen.swift's
// shouldShowChatTimestamp). A message shows its timestamp only when it's the last
// in a consecutive run from the same sender within the same local minute. Compares
// full local date+minute, not chatMessageTime's clock-face string alone -- that
// would false-positive "same run" for two messages sent at the same clock time on
// different days, a real risk in a search-results list where adjacent entries
// aren't temporally adjacent in the real conversation.
export function shouldShowChatTimestamp<T extends { senderId: string; sentAt: string }>(messages: T[], index: number) {
  if (index === messages.length - 1) return true;
  const current = messages[index];
  const next = messages[index + 1];
  if (current.senderId !== next.senderId) return true;
  const currentDate = new Date(current.sentAt);
  const nextDate = new Date(next.sentAt);
  if (Number.isNaN(currentDate.getTime()) || Number.isNaN(nextDate.getTime())) return true;
  const minuteKey = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}-${d.getHours()}-${d.getMinutes()}`;
  return minuteKey(currentDate) !== minuteKey(nextDate);
}

// Real emoji reactions (2026-07-19) -- shared between 1:1 and group threads, which
// differ only in which toggle call they make. Tapping an existing reaction badge
// toggles the current user's own reaction for that emoji (the fast, one-tap path real
// chat apps use); the smile button opens the quick palette for a first reaction.
export function MessageReactions({
  reactions, currentUserId, onToggle, isMine,
}: {
  reactions: ReactionGroup[]; currentUserId: string | undefined; onToggle: (emoji: string) => void; isMine: boolean;
}) {
  const [pickerOpen, setPickerOpen] = useState(false);
  // Real Toss/Kakao-sourced per-glyph hover animation (index.css's itdf-anim-*,
  // see ItundaFace.tsx's ReactionGlyph doc comment) -- tracks which single picker
  // glyph is currently hovered so only that one plays its animation.
  const [hoveredReactionEmoji, setHoveredReactionEmoji] = useState<string | null>(null);
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '4px', justifyContent: isMine ? 'flex-end' : 'flex-start' }}>
      <AnimatePresence initial={false}>
        {reactions.filter((r) => r.userIds.length > 0).map((r) => {
          const mine = !!currentUserId && r.userIds.includes(currentUserId);
          return (
            <motion.button
              key={r.emoji}
              layout
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0, opacity: 0 }}
              transition={{ type: 'spring', stiffness: 500, damping: 22 }}
              whileTap={{ scale: 0.88 }}
              onClick={() => onToggle(r.emoji)}
              style={{
                display: 'flex', alignItems: 'center', gap: '4px', padding: '2px 8px', borderRadius: '12px', fontSize: 'var(--itunda-type-scale-12-size)',
                border: mine ? '1px solid var(--itunda-indigo)' : '1px solid var(--itunda-grey-200)',
                backgroundColor: mine ? 'var(--itunda-indigo-light)' : 'var(--itunda-white)',
              }}
            >
              <ReactionGlyph emoji={r.emoji} size={16} />
              <span style={{ color: 'var(--itunda-grey-700)' }}>{r.userIds.length}</span>
            </motion.button>
          );
        })}
      </AnimatePresence>
      <div style={{ position: 'relative' }}>
        <motion.button
          whileTap={{ scale: 0.88 }}
          onClick={() => setPickerOpen((v) => !v)}
          aria-label="Add reaction"
          style={{ display: 'flex', padding: '6px', borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', color: 'var(--itunda-grey-500)' }}
        >
          <SmilePlus size={14} />
        </motion.button>
        <AnimatePresence>
          {pickerOpen && (
            <motion.div
              initial={{ opacity: 0, scale: 0.85, y: 6 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.85, y: 6 }}
              transition={{ type: 'spring', stiffness: 420, damping: 28 }}
              style={{
                position: 'absolute', bottom: '32px', display: 'flex', gap: '6px', padding: '8px 10px',
                borderRadius: '14px', backgroundColor: 'var(--itunda-white)', boxShadow: '0 2px 8px rgba(0,0,0,0.15)', zIndex: 10,
                left: isMine ? undefined : 0, right: isMine ? 0 : undefined,
              }}
            >
              {QUICK_REACTIONS.map((emoji) => (
                <motion.button
                  key={emoji}
                  whileHover={{ scale: 1.18, y: -3 }}
                  whileTap={{ scale: 0.82 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 15 }}
                  onHoverStart={() => setHoveredReactionEmoji(emoji)}
                  onHoverEnd={() => setHoveredReactionEmoji((current) => (current === emoji ? null : current))}
                  onClick={() => { onToggle(emoji); setPickerOpen(false); }}
                  style={{ display: 'flex', padding: '2px' }}
                >
                  <ReactionGlyph emoji={emoji} size={32} variant="3d" animated={hoveredReactionEmoji === emoji} />
                </motion.button>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
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
export interface OfferBubbleData {
  id: string;
  amount: number;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'COUNTERED';
  proposedByUserId: string;
}

export function OfferBubble({
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
        maxWidth: '75%', padding: '12px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
        backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
        color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><MoneyBagGlyph size={16} /> {offer.amount.toLocaleString('en-US')} RWF</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.8 }}>{statusLabel[offer.status]}</p>
      {canRespond && !countering && (
        <div style={{ display: 'flex', gap: '6px' }}>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'ACCEPT')}>
            Accept
          </button>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'REJECT')}>
            Decline
          </button>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }} onClick={() => setCountering(true)}>
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
            style={{ flex: 1, padding: '6px 8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)', fontSize: 'var(--itunda-type-scale-12-size)' }}
          />
          <button
            className="itunda-btn itunda-btn-secondary"
            style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}
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

// Real KakaoTalk-style gift bubble -- renders inline wherever a message carries a real
// gift (see GiftService's own doc comment), with a real Claim button shown only to the
// recipient of a still-PENDING, not-yet-expired gift.
export function GiftBubble({
  gift, isMine, currentUserId, onClaim,
}: {
  gift: Gift; isMine: boolean; currentUserId: string | undefined; onClaim: (giftId: string) => void;
}) {
  const canClaim = gift.status === 'PENDING' && currentUserId === gift.recipientId && new Date(gift.expiresAt).getTime() > Date.now();
  const statusLabel: Record<GiftStatus, string> = {
    PENDING: isMine ? 'Waiting to be opened' : 'Tap to open',
    CLAIMED: 'Opened',
    EXPIRED: 'Expired — refunded',
  };

  return (
    <div
      style={{
        maxWidth: '75%', padding: '14px 16px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
        backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
        color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, fontSize: 'var(--itunda-type-scale-16-size)', display: 'flex', alignItems: 'center', gap: '6px' }}>
        <GiftGlyph theme={gift.theme} size={18} />
        {gift.theme ? GIFT_THEME_LABELS[gift.theme].replace(/^\S+\s*/, '') : ''} {gift.amount.toLocaleString('en-US')} RWF
      </p>
      {gift.note && <p style={{ fontStyle: 'italic', opacity: 0.9 }}>&ldquo;{gift.note}&rdquo;</p>}
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.8 }}>{statusLabel[gift.status]}</p>
      {canClaim && (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px', alignSelf: 'flex-start' }}
          onClick={() => onClaim(gift.id)}
        >
          Open gift
        </button>
      )}
    </div>
  );
}

// Real KakaoTalk-style 기프티콘 gift voucher bubble (item 134) -- see
// lib/giftVouchers.ts's own doc comment. Redemption is merchant-side only
// (GiftVoucherService.redeemVoucher's own doc comment: a customer presents the
// voucher in person for the merchant to validate, never a self-serve recipient
// redeem), so this bubble is status-only -- no claim action, unlike GiftBubble.
// Real Kakao-sourced one-time expiry extension (item 141) -- either the purchaser or
// recipient may trigger it (GiftVoucherService.extendExpiry's own doc comment: "either
// real party to the transaction"), only within EXTENSION_WINDOW (30 days) of expiry,
// and only once per voucher (`extended`). Client-side date check here is a soft UX
// convenience only -- the backend's own real validation is authoritative.
const GIFT_VOUCHER_EXTENSION_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

export function GiftVoucherBubble({
  voucher, isMine, onExtend,
}: {
  voucher: GiftVoucher; isMine: boolean; onExtend: (voucherId: string) => void;
}) {
  const [extending, setExtending] = useState(false);
  // Real gap found live (Toss-style error-handling audit, 2026-08-30): this had NO
  // catch block at all -- a real 409 GIFT_VOUCHER_ALREADY_EXTENDED (the other real
  // party to the transaction extended it first, a genuine race given either side can
  // trigger this per the backend's own doc comment) was an unhandled promise
  // rejection, reading to the customer as the page silently breaking.
  const [extendError, setExtendError] = useState<string | null>(null);
  const statusLabel: Record<GiftVoucherStatus, string> = {
    ACTIVE: 'Present this at the store to redeem',
    REDEEMED: 'Redeemed',
    EXPIRED: 'Expired',
  };
  const withinExtensionWindow = new Date(voucher.expiresAt).getTime() - Date.now() <= GIFT_VOUCHER_EXTENSION_WINDOW_MS;
  const canExtend = voucher.status === 'ACTIVE' && !voucher.extended && withinExtensionWindow;

  return (
    <div
      style={{
        maxWidth: '75%', padding: '14px 16px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
        backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
        color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, fontSize: 'var(--itunda-type-scale-15-size)', display: 'flex', alignItems: 'center', gap: '6px' }}><VoucherTicket size={18} /> {voucher.productNameSnapshot ?? `${voucher.amount.toLocaleString('en-US')} RWF voucher`}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.8 }}>{statusLabel[voucher.status]}</p>
      {voucher.status === 'ACTIVE' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.7 }}>Expires {new Date(voucher.expiresAt).toLocaleDateString()}</p>
      )}
      {extendError && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: isMine ? 'var(--itunda-white)' : 'var(--itunda-red)', opacity: isMine ? 0.9 : 1 }} role="alert">{extendError}</p>
      )}
      {canExtend && (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px', alignSelf: 'flex-start' }}
          disabled={extending}
          onClick={async () => {
            setExtending(true);
            setExtendError(null);
            try {
              await extendGiftVoucherExpiry(voucher.id);
              onExtend(voucher.id);
            } catch (err) {
              if (err instanceof ApiError && err.code === 'GIFT_VOUCHER_ALREADY_EXTENDED') {
                // The other real party to the transaction already extended it --
                // resolve forward by refreshing to show the real, already-extended
                // expiry, matching this codebase's own established pattern for an
                // "already done" conflict that isn't really a failure.
                onExtend(voucher.id);
              } else {
                setExtendError(err instanceof ApiError ? err.message : 'Could not extend this voucher.');
              }
            } finally {
              setExtending(false);
            }
          }}
        >
          {extending ? '…' : 'Extend expiry'}
        </button>
      )}
    </div>
  );
}

// Real KakaoTalk Emoticon Store (item 133) -- a real sticker message renders as just
// the image, no chat-bubble background, matching real KakaoTalk's own emoticon
// rendering (a bubble would look wrong behind a sticker that already has its own
// transparent art). See lib/emoticons.ts's own doc comment.
export function EmoticonBubble({ imageUrl }: { imageUrl: string | undefined }) {
  if (!imageUrl) {
    return <div style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', fontStyle: 'italic' }}>[emoticon]</div>;
  }
  return <img src={imageUrl} alt="emoticon" style={{ width: '96px', height: '96px', objectFit: 'contain' }} />;
}
