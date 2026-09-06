// Extracted from BankDashboard.tsx (2026-09-03, itunda-vs-Toss architecture comparison
// thread's own open recommendation 4 -- see project_itunda_architecture_vs_toss.md,
// ARCHITECTURE_GUIDELINES.md §2). The "start a new chat" entry points shared between
// DirectMessagesList (NewChatCard) and GroupsList (NewGroupCard/OpenChatCard) -- verified
// via real usage grep before splitting into their own file, not assumed from adjacency.
import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { LinkGlyph, GlobeGlyph, CameraGlyph } from './icons/ItundaFaceMisc';
import { ApiError } from './lib/api';
import { useI18n } from './i18n/I18nContext';
import { QrScanCamera, parseQrParam } from './QrScanCamera';
import { fetchTalkContacts, startConversation, startConversationWithUser } from './lib/messaging';
import { createGroup, createOpenGroup, joinGroupByCode } from './lib/groupMessaging';
import { buildJoinUrl, readAndClearUrlParam, shareOrCopyLink } from './BankDashboard';

export function NewChatCard({ onStarted }: { onStarted: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [phoneNumber, setPhoneNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [contacts, setContacts] = useState<{ userId: string; name: string }[] | null>(null);

  useEffect(() => { fetchTalkContacts().then(setContacts).catch(() => setContacts([])); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const conversation = await startConversation(phoneNumber.trim());
      setPhoneNumber('');
      onStarted(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>New chat</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        Start from an Itunda contact, or enter their phone number.
      </p>
      {contacts && contacts.length > 0 && (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '12px' }}>
          {contacts.map((contact) => <button key={contact.userId} type="button" className="itunda-btn itunda-btn-secondary" onClick={async () => { setSubmitting(true); try { const c = await startConversationWithUser(contact.userId); onStarted(c.id); } catch { setError('Could not start this chat.'); } finally { setSubmitting(false); } }} disabled={submitting}>{contact.name}</button>)}
        </div>
      )}
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="tel"
          value={phoneNumber}
          onChange={(e) => setPhoneNumber(e.target.value)}
          placeholder="+250788123456"
          required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? 'Starting…' : 'Chat'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

export function NewGroupCard({ onCreated }: { onCreated: (groupId: string) => void }) {
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>New group</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        Name your group and add real members by phone number, separated by commas.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Group name"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          type="text"
          value={phoneNumbers}
          onChange={(e) => setPhoneNumbers(e.target.value)}
          placeholder="+250788123456, +250788654321"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create group'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
      )}
    </div>
  );
}

// Real KakaoTalk 오픈채팅 (Open Chat)-style group -- see backend
// GroupMessagingService.createOpenGroup's own doc comment for the full sourced
// account. Distinct from NewGroupCard above: no phone numbers needed to create one,
// and anyone with the real generated code can join, not just people the creator
// explicitly invited.
export function OpenChatCard({ onCreated, onJoined }: { onCreated: (groupId: string) => void; onJoined: (groupId: string) => void }) {
  const { t } = useI18n();
  const [mode, setMode] = useState<'closed' | 'create' | 'join'>('closed');
  const [name, setName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [created, setCreated] = useState<{ id: string; joinCode: string } | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real fix (2026-08-19): joining used to require typing the raw 6-character code by
  // hand -- the exact "asking user code, instead use qr code" anti-pattern. Reuses the
  // same itunda://... QR payload convention as payments (see QrScanCamera's own doc
  // comment); the code stays as a real fallback for whoever's sharing over voice/text.
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualJoinEntry, setManualJoinEntry] = useState(false);

  useEffect(() => {
    if (created) {
      QRCode.toDataURL(`itunda://join-chat?code=${created.joinCode}`, { width: 220, margin: 1 }).then(setQrDataUrl).catch(() => setQrDataUrl(null));
    } else {
      setQrDataUrl(null);
    }
  }, [created]);

  const submitJoinCode = async (rawCode: string) => {
    const trimmed = rawCode.trim();
    if (!trimmed) return;
    setError(null);
    setSubmitting(true);
    try {
      const group = await joinGroupByCode(trimmed);
      setJoinCode('');
      setMode('closed');
      onJoined(group.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleScanJoin = (raw: string) => void submitJoinCode(parseQrParam(raw, 'code'));

  // Real remote-invite fix (2026-08-19) -- see buildJoinUrl's own doc comment: a friend
  // who taps a shared itunda link (sent via itunda talk, SMS, anywhere) lands here with
  // ?joinChatCode=... already in the URL and should join immediately, no typing or
  // scanning at all.
  useEffect(() => {
    const incoming = readAndClearUrlParam('joinChatCode');
    if (incoming) void submitJoinCode(incoming);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on mount only
  }, []);

  const [shareStatus, setShareStatus] = useState<'idle' | 'shared' | 'copied' | 'failed'>('idle');
  const handleShare = async (code: string) => {
    const url = buildJoinUrl('MESSAGES', 'joinChatCode', code);
    const result = await shareOrCopyLink(url, 'Join my open chat on itunda', `Join my open chat on itunda — tap to join instantly.`);
    setShareStatus(result);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const group = await createOpenGroup(name.trim());
      setName('');
      setCreated({ id: group.id, joinCode: group.joinCode });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleJoin = (e: React.FormEvent) => {
    e.preventDefault();
    void submitJoinCode(joinCode);
  };

  if (mode === 'closed') {
    return (
      <div style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
        <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('create')}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><GlobeGlyph size={16} /> Start an open chat</span>
        </button>
        <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('join')}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><CameraGlyph size={16} /> Join an open chat</span>
        </button>
      </div>
    );
  }

  if (created) {
    return (
      <div className="itunda-flat-section" style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', gap: '10px', alignItems: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Send friends a link — tapping it joins instantly, wherever they are</p>
        <button className="itunda-btn itunda-btn-primary" style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }} onClick={() => handleShare(created.joinCode)}>
          <LinkGlyph size={16} /> Share invite link
        </button>
        {shareStatus === 'copied' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>Link copied</p>}
        {shareStatus === 'failed' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }}>Could not copy the link — try the code below.</p>}
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '8px' }}>Or, if they're standing right next to you:</p>
        {qrDataUrl && <img src={qrDataUrl} alt={`QR code to join ${created.joinCode}`} width={140} height={140} style={{ borderRadius: '12px' }} />}
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Or read them this code:</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, letterSpacing: '4px' }}>{created.joinCode}</p>
        <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => { const id = created.id; setCreated(null); setMode('closed'); onCreated(id); }}>
          Done
        </button>
      </div>
    );
  }

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {mode === 'create' ? (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Start an open chat</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Anyone with the code can join — no phone numbers needed.</p>
          <input
            type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Open chat name" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('closed')}>Cancel</button>
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
              {submitting ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      ) : !manualJoinEntry ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Scan to join</h3>
          {!scanUnavailable && !submitting && <QrScanCamera onDetect={handleScanJoin} onUnavailable={() => setScanUnavailable(true)} />}
          {submitting && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Joining…</p>}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('closed')}>Cancel</button>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setManualJoinEntry(true)}>
              {scanUnavailable ? 'Enter code manually' : 'No camera? Enter code'}
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleJoin} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Join by code</h3>
          <input
            type="text" value={joinCode} onChange={(e) => setJoinCode(e.target.value.toUpperCase())} placeholder="6-character code" required autoFocus
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', textAlign: 'center', letterSpacing: '2px' }}
          />
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('closed')}>Cancel</button>
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
              {submitting ? 'Joining…' : 'Join'}
            </button>
          </div>
        </form>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}
