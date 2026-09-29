// Extracted from BankDashboard.tsx (2026-08-30, project_itunda_architecture_vs_toss.md
// rec 4). One external call site (DirectMessagesList). Deeply stateful (~30
// useState hooks for one cohesive 1:1 chat screen) with no safe sub-split
// available -- moved verbatim.

import { useEffect, useRef, useState } from 'react';
import { Image as ImageIcon, Link as LinkIcon, Receipt, Settings } from 'lucide-react';
import { IconBack, IconSend } from './icons/ItundaIcons';
import { CameraGlyph, PinGlyph } from './icons/ItundaFaceMisc';
import { GiftGlyph, VoucherTicket } from './icons/ItundaFaceGifts';
import { SmileySlight } from './icons/ItundaFaceSmileys';
import { EmojiPicker, renderTextWithEmoji } from './icons/ItundaFaceEmoji';
import { useI18n } from './i18n/I18nContext';
import { ApiError, getStoredUser } from './lib/api';
import { uploadFile } from './lib/upload';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { DirectSplitBillsView } from './DirectSplitBillsView';
import { chatMessageTime, shouldShowChatTimestamp, MessageReactions, OfferBubble, GiftBubble, GiftVoucherBubble, EmoticonBubble, type OfferBubbleData } from './TalkBubbles';
import { EmoticonPickerPanel, EmoticonStoreModal, GiftVoucherComposerPanel } from './TalkEmoticonStore';
import { MediaGalleryModal, ForwardPickerModal, ThreadModal } from './TalkModals';
import { TalkLinksModal } from './TalkLinksTab';
import { TalkRoomSettings } from './TalkRoomSettings';
import { extractLinks } from './lib/talk';
import { fetchEmoticonImageMap, sendEmoticon } from './lib/emoticons';
import { claimGift, fetchGiftsForConversation, sendGiftInConversation, GIFT_THEME_LABELS, type Gift, type GiftTheme } from './lib/gift';
import { fetchGiftVouchersForConversation, type GiftVoucher } from './lib/giftVouchers';
import { fetchOffersForConversation, respondToOffer, type PriceOffer } from './lib/marketplace';
import { fetchPropertyOffersForConversation, respondToPropertyOffer, type PropertyPriceOffer } from './lib/realestate';
import {
  blockConversationParticipant, connectMessagingSocket, deleteMessage, fetchConversationQuiet, fetchMessages,
  fetchPinnedConversationMessage, fetchPresence, fetchThread, forwardMessage, pinConversationMessage, reportChatMessage,
  searchConversationMessages, sendMessage, setConversationQuiet, toggleReaction, unblockConversationParticipant, unpinConversationMessage,
  type ConversationSummary, type Message, type MessagingSocketHandle,
} from './lib/messaging';
import { getRoomTheme, ROOM_THEMES } from './lib/roomSettings';
import { useDeferredLoading } from './useDeferredLoading';

function roomThemeColor(conversationId: string): string | undefined {
  const id = getRoomTheme(conversationId);
  return ROOM_THEMES.find((t) => t.id === id)?.color ?? undefined;
}

function blockButtonLabel(blocking: boolean, blocked: boolean): string {
  if (blocking && blocked) return 'Unblocking…';
  if (blocking) return 'Blocking…';
  if (blocked) return 'Unblock';
  return 'Block';
}

export function ConversationThread({ conversation, onBack }: { conversation: ConversationSummary; onBack: () => void }) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<Message[] | null>(null);
  const showSkeleton = useDeferredLoading(messages === null);
  const [offersByMessageId, setOffersByMessageId] = useState<Record<string, OfferBubbleData>>({});
  const [giftsByMessageId, setGiftsByMessageId] = useState<Record<string, Gift>>({});
  const [vouchersByMessageId, setVouchersByMessageId] = useState<Record<string, GiftVoucher>>({});
  const [voucherComposerOpen, setVoucherComposerOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [replyingTo, setReplyingTo] = useState<Message | null>(null);
  const [pinnedMessage, setPinnedMessage] = useState<Message | null>(null);
  const [updatingPin, setUpdatingPin] = useState(false);
  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const [forwardingMessage, setForwardingMessage] = useState<Message | null>(null);
  // Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
  // recommendation #3's own account: Kakao's confirmed 2025 toolkit includes a real
  // reply-expands-into-its-own-sub-conversation view, closing the last gap in that
  // recommendation (Copy/Reply/Forward/Pin/Delete/@mention were all already real).
  const [threadRootMessage, setThreadRootMessage] = useState<Message | null>(null);
  const [sending, setSending] = useState(false);
  const [giftComposerOpen, setGiftComposerOpen] = useState(false);
  const [giftAmount, setGiftAmount] = useState('');
  const [giftNote, setGiftNote] = useState('');
  const [giftTheme, setGiftTheme] = useState<GiftTheme | ''>('');
  const [sendingGift, setSendingGift] = useState(false);
  // Real KakaoTalk Emoticon Store (item 133) -- see lib/emoticons.ts's own doc comment.
  const [emoticonPickerOpen, setEmoticonPickerOpen] = useState(false);
  const [emoticonStoreOpen, setEmoticonStoreOpen] = useState(false);
  const [emoticonImageById, setEmoticonImageById] = useState<Record<string, string>>({});
  // Real itundaface emoji picker (see icons/ItundaFaceEmoji.tsx's own doc comment)
  // -- distinct from the KakaoTalk-style sticker emoticonPicker above: this inserts
  // a real Unicode character into the message draft, not a separate sticker message.
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false);
  // Real attach ("+") menu + photo send/gallery -- see GroupThread's own identical
  // doc comment.
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [showMediaGallery, setShowMediaGallery] = useState(false);
  // Real Links tab (itunda Talk redesign, 2026-08-28) -- see TalkLinksTab.tsx's own
  // doc comment, mirrors showMediaGallery's exact shape.
  const [showLinks, setShowLinks] = useState(false);
  // Real 1:1-chat split-bill (2026-08-09) -- see DirectSplitBillsView's own doc
  // comment; mirrors GroupThread's own identical showSplitBills toggle.
  const [showSplitBills, setShowSplitBills] = useState(false);
  // Real per-room settings (itunda Talk redesign, 2026-08-28) -- see
  // TalkRoomSettings.tsx's own doc comment.
  const [showRoomSettings, setShowRoomSettings] = useState(false);
  const photoInputRef = useRef<HTMLInputElement | null>(null);
  const [blocking, setBlocking] = useState(false);
  // Real unblock (item 193) -- the "Block" button had no way back: blockConversationParticipant's
  // own confirmation copy already promised "you can unblock them later from this
  // conversation," but unblockConversationParticipant was defined and never called
  // anywhere on any platform. No real "am I currently blocking them" query endpoint
  // exists (MessagingService.blockConversationParticipant/unblockConversationParticipant
  // are both idempotent fire-and-forget), so this is session-local state, same honest
  // scope the pre-existing block-only button already had.
  const [blocked, setBlocked] = useState(false);
  const [quiet, setQuiet] = useState(false);
  const [updatingQuiet, setUpdatingQuiet] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Message[] | null>(null);
  const [searching, setSearching] = useState(false);
  // Real device binding step-up (2026-07-21) -- covers Gift send/claim below.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10): two different actions (send gift / claim gift) share this
  // one flag+prompt, so re-verifying couldn't just re-call "the" handler like
  // TransferFlow's own identical fix -- it has to retry whichever one was actually
  // pending. See TransferFlow's own doc comment for the base account of why retrying
  // at all matters: re-entering a password already proves who's asking, so making the
  // user redo the original action by hand afterward is friction, not security.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);
  const [otherOnline, setOtherOnline] = useState<boolean | null>(null);
  const [otherTyping, setOtherTyping] = useState(false);
  // Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14 -- Toss's own
  // "Birth of a chatbot heard through the ears" article, toss.tech/article/38743):
  // a message pushed live over the socket only ever updated the visual message list --
  // nothing here told a screen-reader user a new message had arrived at all, since
  // nothing on this screen was an aria-live region. Announced via a visually-hidden
  // live region below, only for messages actually pushed from the OTHER participant
  // (never the current user's own sent message, which they already know they typed).
  const [liveAnnouncement, setLiveAnnouncement] = useState('');
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<MessagingSocketHandle | null>(null);
  const typingClearTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastTypingSentAt = useRef(0);

  useEffect(() => {
    fetchPresence([conversation.otherUserId]).then((p) => setOtherOnline(p[conversation.otherUserId] ?? null)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.otherUserId]);

  useEffect(() => {
    fetchConversationQuiet(conversation.conversationId).then(setQuiet).catch(() => {});
  }, [conversation.conversationId]);

  useEffect(() => {
    fetchEmoticonImageMap().then(setEmoticonImageById).catch(() => {});
  }, []);

  const loadPin = () => fetchPinnedConversationMessage(conversation.conversationId).then(setPinnedMessage).catch(() => {});

  useEffect(() => { loadPin(); }, [conversation.conversationId]);

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

  const loadGifts = () => {
    fetchGiftsForConversation(conversation.conversationId)
      .then((gifts) => setGiftsByMessageId(Object.fromEntries(gifts.map((g) => [g.messageId, g]))))
      .catch(() => {});
  };

  const loadVouchers = () => {
    fetchGiftVouchersForConversation(conversation.conversationId)
      .then((vouchers) => setVouchersByMessageId(Object.fromEntries(vouchers.map((v) => [v.messageId, v]))))
      .catch(() => {});
  };

  const load = () => {
    fetchMessages(conversation.conversationId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    loadOffers();
    loadGifts();
    loadVouchers();
  };

  const handleBlock = async () => {
    if (!window.confirm(`Block ${conversation.otherUserName}? They will no longer be able to message you.`)) return;
    setBlocking(true);
    try {
      await blockConversationParticipant(conversation.conversationId);
      setBlocked(true);
      setError(`You blocked ${conversation.otherUserName}. You can unblock them later from this conversation.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setBlocking(false); }
  };

  const handleUnblock = async () => {
    setBlocking(true);
    try {
      await unblockConversationParticipant(conversation.conversationId);
      setBlocked(false);
      setError(`You unblocked ${conversation.otherUserName}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setBlocking(false); }
  };

  const handleQuiet = async () => {
    setUpdatingQuiet(true);
    try {
      setQuiet(await setConversationQuiet(conversation.conversationId, !quiet));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingQuiet(false); }
  };

  const handlePin = async (message: Message) => {
    setUpdatingPin(true);
    try {
      await pinConversationMessage(conversation.conversationId, message.id);
      setPinnedMessage(message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleUnpin = async () => {
    setUpdatingPin(true);
    try {
      await unpinConversationMessage(conversation.conversationId);
      setPinnedMessage(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleReport = async (messageId: string) => {
    const reason = window.prompt('Why are you reporting this message? (3–180 characters)');
    if (!reason) return;
    try {
      await reportChatMessage(messageId, reason);
      setError('Thanks. Your report was sent for review.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleDelete = async (messageId: string) => {
    if (!window.confirm('Delete this message for everyone?')) return;
    try {
      await deleteMessage(conversation.conversationId, messageId);
      setMessages((prev) => prev?.map((m) => m.id === messageId ? { ...m, body: 'This message was deleted', deletedAt: new Date().toISOString(), reactions: [] } : m) ?? prev);
    } catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
  };

  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const handleForward = async (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => {
    if (!forwardingMessage) return;
    try {
      await forwardMessage(forwardingMessage.id, destinationType, destinationId);
      setForwardingMessage(null);
      setError('Message forwarded.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleCopy = (body: string) => {
    navigator.clipboard?.writeText(body).catch(() => {});
  };

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim().length < 2) return;
    setSearching(true); setError(null);
    try { setSearchResults(await searchConversationMessages(conversation.conversationId, searchQuery.trim())); }
    catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
    finally { setSearching(false); }
  };

  const handleSendGift = async (e?: React.FormEvent) => {
    e?.preventDefault();
    const amount = Number(giftAmount);
    if (!amount || amount <= 0) return;
    setSendingGift(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await sendGiftInConversation(conversation.conversationId, amount, giftNote, giftTheme || null);
      setGiftAmount('');
      setGiftNote('');
      setGiftTheme('');
      setGiftComposerOpen(false);
      load();
    } catch (err) {
      // Real device binding step-up (2026-07-21) -- Gift send was a real gap:
      // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
      // showed only a generic error, same fix already applied to Transfer/Savings.
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = () => handleSendGift();
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSendingGift(false);
    }
  };

  const handleClaimGift = async (giftId: string) => {
    setNeedsDeviceVerification(false);
    try {
      await claimGift(giftId);
      loadGifts();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = () => handleClaimGift(giftId);
        setNeedsDeviceVerification(true);
      } else if (err instanceof ApiError && err.code === 'GIFT_ALREADY_RESOLVED') {
        // Real gap found live (Toss-style error-handling audit, 2026-08-30): a
        // double-tap or an already-opened-on-another-device gift isn't really a
        // failure -- resolve forward by refreshing to show the real, already-opened
        // gift instead of a generic error.
        loadGifts();
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    }
  };

  const handleSendEmoticon = async (emoticonId: string) => {
    setError(null);
    try {
      await sendEmoticon(conversation.conversationId, emoticonId);
      setEmoticonPickerOpen(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  // Real photo message -- ports Android TalkScreen.kt's own identical addition
  // (2026-08-04) to bank-mfe.
  const handleSendPhoto = async (file: File | undefined) => {
    if (!file) return;
    setShowAttachMenu(false);
    setUploadingPhoto(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      await sendMessage(conversation.conversationId, '', replyingTo?.id, url);
      setReplyingTo(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploadingPhoto(false);
      if (photoInputRef.current) photoInputRef.current.value = '';
    }
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
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
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
      if (payload.message.senderId !== currentUser?.id) {
        setLiveAnnouncement(`New message from ${conversation.otherUserName}: ${payload.message.body || 'sent an attachment'}`);
      }
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
      const sent = await sendMessage(conversation.conversationId, body, replyingTo?.id);
      setMessages((prev) => [...(prev ?? []), sent]);
      setDraft('');
      setReplyingTo(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
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

  if (showSplitBills) {
    return (
      <DirectSplitBillsView
        otherUserId={conversation.otherUserId}
        otherUserName={conversation.otherUserName}
        currentUserId={currentUser?.id ?? null}
        onBack={() => setShowSplitBills(false)}
      />
    );
  }
  if (showRoomSettings) {
    return (
      <TalkRoomSettings
        conversationId={conversation.conversationId}
        otherUserName={conversation.otherUserName}
        messages={messages ?? []}
        onBack={() => setShowRoomSettings(false)}
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversations">
          <IconBack size={20} />
        </button>
        <div>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{conversation.otherUserName}</h3>
          {otherOnline !== null && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: otherOnline ? 'var(--itunda-green)' : 'var(--itunda-grey-500)' }}>
              {otherOnline ? 'Online' : 'Offline'}
            </p>
          )}
        </div>
        <button type="button" onClick={() => setShowMediaGallery(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', marginLeft: 'auto' }} aria-label="Shared photos">
          <ImageIcon size={20} />
        </button>
        <button type="button" onClick={() => setShowLinks(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)' }} aria-label="Shared links">
          <LinkIcon size={20} />
        </button>
        <button type="button" onClick={() => setShowSplitBills(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)' }} aria-label="Split a bill">
          <Receipt size={20} />
        </button>
        <button type="button" onClick={() => setShowRoomSettings(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)' }} aria-label="Room settings">
          <Settings size={20} />
        </button>
        <button
          type="button"
          className="itunda-btn itunda-btn-secondary"
          onClick={blocked ? handleUnblock : handleBlock}
          disabled={blocking}
          style={{ padding: '8px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }}
        >
          {blockButtonLabel(blocking, blocked)}
        </button>
        <button type="button" className="itunda-btn itunda-btn-secondary" onClick={handleQuiet} disabled={updatingQuiet} style={{ padding: '8px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }}>
          {updatingQuiet ? '…' : quiet ? 'Resume alerts' : 'Quiet room'}
        </button>
      </div>

      {showMediaGallery && (
        <MediaGalleryModal
          imageUrls={(messages ?? []).map((m) => m.imageUrl).filter((u): u is string => !!u).reverse()}
          onClose={() => setShowMediaGallery(false)}
        />
      )}
      {showLinks && (
        <TalkLinksModal links={extractLinks((messages ?? []).map((m) => m.body))} onClose={() => setShowLinks(false)} />
      )}

      <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
        <input value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="Search this conversation" minLength={2} style={{ flex: 1, padding: '9px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }} />
        <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={searching || searchQuery.trim().length < 2}>{searching ? '…' : 'Search'}</button>
        {searchResults !== null && <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => { setSearchResults(null); setSearchQuery(''); }}>Clear</button>}
      </form>
      {searchResults !== null && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>{searchResults.length} matching message{searchResults.length === 1 ? '' : 's'}</p>}

      {pinnedMessage && (
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', padding: '8px 10px', marginBottom: '8px', borderRadius: '10px', background: 'var(--itunda-grey-100)', fontSize: 'var(--itunda-type-scale-12-size)' }}>
          <span aria-hidden="true" style={{ display: 'inline-flex' }}><PinGlyph size={14} /></span><span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{pinnedMessage.body}</span>
          <button type="button" onClick={handleUnpin} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-600)', fontSize: 'var(--itunda-type-scale-12-size)' }}>Unpin</button>
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', padding: '4px', backgroundColor: roomThemeColor(conversation.conversationId), borderRadius: 'var(--itunda-radius-md)' }}>
        {messages === null && showSkeleton && <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} />}
        {messages !== null && messages.length === 0 && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', textAlign: 'center', marginTop: '20px' }}>
            Say hello — no messages yet.
          </p>
        )}
        {(searchResults ?? messages)?.map((m, index, list) => {
          const isMine = m.senderId === currentUser?.id;
          const offer = offersByMessageId[m.id];
          const gift = giftsByMessageId[m.id];
          const voucher = vouchersByMessageId[m.id];
          // Never collapsed when showing search hits -- adjacent results aren't
          // temporally adjacent in the real conversation, so each needs its own
          // explicit timestamp.
          const showTimestamp = searchResults != null || shouldShowChatTimestamp(list, index);
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {/* Real message forwarding (2026-07-25) -- a genuine provenance label,
                  only ever set on a message actually created via the forward
                  endpoint, see lib/messaging.ts's own doc comment. */}
              {m.forwardedFromMessageId && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-400)', fontStyle: 'italic', marginBottom: '2px' }}>Forwarded</span>
              )}
              {gift ? (
                <GiftBubble gift={gift} isMine={isMine} currentUserId={currentUser?.id} onClaim={handleClaimGift} />
              ) : voucher ? (
                <GiftVoucherBubble voucher={voucher} isMine={isMine} onExtend={() => loadVouchers()} />
              ) : offer ? (
                <OfferBubble offer={offer} isMine={isMine} currentUserId={currentUser?.id} onRespond={handleRespondToOffer} />
              ) : m.emoticonId ? (
                <EmoticonBubble imageUrl={emoticonImageById[m.emoticonId]} />
              ) : m.imageUrl ? (
                // Real photo message -- ports Android TalkScreen.kt's own identical
                // addition (2026-08-04) to bank-mfe.
                <img src={m.imageUrl} alt="Shared photo" style={{ maxWidth: '220px', borderRadius: '16px' }} />
              ) : (
                <div
                  style={{
                    maxWidth: '75%',
                    padding: '10px 14px',
                    borderRadius: '16px',
                    fontSize: 'var(--itunda-type-scale-14-size)',
                    backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                    color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                  }}
                >
                  {renderTextWithEmoji(m.body)}
                </div>
              )}
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
              {(showTimestamp || (isMine && !m.readAt)) && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
                  {[isMine && !m.readAt ? '1' : null, showTimestamp ? chatMessageTime(m.sentAt) : null].filter(Boolean).join(' · ')}
                </span>
              )}
              <button type="button" onClick={() => setReplyingTo(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Reply</button>
              <button type="button" onClick={() => handleCopy(m.body)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Copy</button>
              {!m.deletedAt && <button type="button" onClick={() => setForwardingMessage(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Forward</button>}
              {isMine && !m.deletedAt && <button type="button" onClick={() => handleDelete(m.id)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Delete</button>}
              <button type="button" onClick={() => handlePin(m)} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>{pinnedMessage?.id === m.id ? 'Pinned' : 'Pin'}</button>
              {!isMine && (
                <button type="button" onClick={() => handleReport(m.id)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>
                  Report message
                </button>
              )}
              {/* Real Thread support (2026-08-05) -- a message with at least one direct
                  reply gets a real "N replies" affordance opening its own sub-conversation
                  view, matching Kakao's confirmed real reply-thread pattern. */}
              {!!m.replyCount && (
                <button
                  type="button"
                  onClick={() => setThreadRootMessage(m)}
                  style={{ border: 'none', background: 'none', color: 'var(--itunda-indigo)', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, padding: '4px 0' }}
                >
                  {m.replyCount} {m.replyCount === 1 ? 'reply' : 'replies'} →
                </button>
              )}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
      {forwardingMessage && <ForwardPickerModal onForward={handleForward} onClose={() => setForwardingMessage(null)} />}
      {threadRootMessage && (
        <ThreadModal
          rootMessage={threadRootMessage}
          currentUserId={currentUser?.id}
          fetchThreadMessages={() => fetchThread(conversation.conversationId, threadRootMessage.id)}
          onSend={(body) => sendMessage(conversation.conversationId, body, threadRootMessage.id)}
          onClose={() => { setThreadRootMessage(null); load(); }}
        />
      )}

      {/* Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14) -- this
          state change was visual-only before; a screen-reader user got no signal the
          other participant started typing. */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">{liveAnnouncement}</div>
      {otherTyping && (
        <p aria-live="polite" style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {conversation.otherUserName} is typing…
        </p>
      )}

      {needsDeviceVerification ? (
        <div style={{ marginBottom: '8px' }}>
          <DeviceStepUpPrompt
            onVerified={() => { const retry = pendingDeviceRetryRef.current; pendingDeviceRetryRef.current = null; retry?.(); }}
            onCancel={() => { pendingDeviceRetryRef.current = null; setNeedsDeviceVerification(false); }}
          />
        </div>
      ) : (
        error && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>
        )
      )}

      {giftComposerOpen && (
        <form
          onSubmit={handleSendGift}
          style={{
            display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px',
            borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', marginBottom: '10px',
          }}
        >
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><GiftGlyph theme={giftTheme || null} size={16} /> Send a gift</p>
          <input
            type="number"
            value={giftAmount}
            onChange={(e) => setGiftAmount(e.target.value)}
            placeholder="Amount (RWF)"
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <input
            type="text"
            value={giftNote}
            onChange={(e) => setGiftNote(e.target.value)}
            placeholder="Add a note (optional)"
            maxLength={200}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <select
            value={giftTheme}
            onChange={(e) => setGiftTheme(e.target.value as GiftTheme | '')}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          >
            <option value="">No theme (plain gift)</option>
            {(Object.keys(GIFT_THEME_LABELS) as GiftTheme[]).map((t) => (
              <option key={t} value={t}>{GIFT_THEME_LABELS[t]}</option>
            ))}
          </select>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="submit"
              className="itunda-btn itunda-btn-primary"
              disabled={sendingGift || !giftAmount || Number(giftAmount) <= 0}
              style={{ flex: 1, padding: '10px' }}
            >
              Send gift
            </button>
            <button
              type="button"
              className="itunda-btn itunda-btn-secondary"
              style={{ padding: '10px 16px' }}
              onClick={() => setGiftComposerOpen(false)}
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {emoticonPickerOpen && (
        <EmoticonPickerPanel onSend={handleSendEmoticon} onOpenStore={() => setEmoticonStoreOpen(true)} />
      )}
      {emoticonStoreOpen && <EmoticonStoreModal onClose={() => setEmoticonStoreOpen(false)} />}
      {voucherComposerOpen && (
        <GiftVoucherComposerPanel
          onSent={() => { setVoucherComposerOpen(false); load(); }}
          onCancel={() => setVoucherComposerOpen(false)}
        />
      )}

      {replyingTo && <div style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)', padding: '8px', borderLeft: '3px solid var(--itunda-indigo)', marginBottom: '6px' }}>Replying to: {replyingTo.body.slice(0, 80)} <button type="button" aria-label="Cancel reply" onClick={() => setReplyingTo(null)}>×</button></div>}
      <input
        ref={photoInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleSendPhoto(e.target.files?.[0])}
      />
      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px', position: 'relative' }}>
        {emojiPickerOpen && <EmojiPicker onPick={(emoji) => setDraft((d) => d + emoji)} />}
        {/* Real attach ("+") menu (2026-08-04 on Android, ported to bank-mfe) --
            consolidates what used to be 3 separate always-visible icons
            (gift/emoticon/gift-voucher), plus real photo send, matching Kakao's own
            real "+"-opens-a-menu pattern. */}
        <button
          type="button"
          aria-label="Attach"
          disabled={uploadingPhoto}
          onClick={() => setShowAttachMenu((v) => !v)}
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}
        >
          {uploadingPhoto ? '…' : '+'}
        </button>
        {showAttachMenu && (
          <div style={{ position: 'absolute', bottom: '52px', left: 0, background: 'var(--itunda-white)', border: '1px solid var(--itunda-grey-200)', borderRadius: '10px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', overflow: 'hidden', zIndex: 10 }}>
            <button type="button" onClick={() => { setShowAttachMenu(false); photoInputRef.current?.click(); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}><CameraGlyph size={16} /> Photo</span>
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmojiPickerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <SmileySlight size={16} /> Emoji
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmoticonPickerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              😊 Emoticon
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setGiftComposerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <GiftGlyph theme={null} size={16} /> Gift
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setVoucherComposerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <VoucherTicket size={16} /> Gift voucher
            </button>
          </div>
        )}
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
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" aria-label="Send message" className="itunda-btn itunda-btn-primary" disabled={sending || !draft.trim()} style={{ padding: '10px 16px' }}>
          <IconSend size={16} />
        </button>
      </form>
    </div>
  );
}
