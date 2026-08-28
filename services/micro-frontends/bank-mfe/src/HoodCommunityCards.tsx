import { useEffect, useState } from 'react';
import { EmptyState } from './EmptyState';
import { showToast } from './Toast';
import { useI18n } from './i18n/I18nContext';
import { HeartFilled } from './icons/ItundaFaceHearts';
import { ApiError } from './lib/api';
import { checkIntoMeetupSession, createCommunityPost, fetchCommentNotificationsEnabled, fetchMeetupSessions, finalizeGroupBuy, removeCommunityPost, scheduleMeetupSessions, setCommentNotificationsEnabled, type CommunityCategory, type CommunityPost, type MeetupSession } from './lib/community';
import { HoodReportButton } from './BankDashboard';

export function NewCommunityPostCard({ categories, onCreated }: { categories: CommunityCategory[]; onCreated: () => void }) {
  const { t } = useI18n();
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
  // Real 당근모임/같이사요 structured fields -- see lib/community.ts's own
  // createCommunityPost doc comment for the real live bug this closes.
  const [eventDate, setEventDate] = useState('');
  const [capacity, setCapacity] = useState('');

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
      const isoEventDate = eventDate ? new Date(eventDate).toISOString() : undefined;
      const numericCapacity = capacity ? Number(capacity) : undefined;
      await createCommunityPost(category, title, body, lat, lng, isoEventDate, numericCapacity);
      setTitle('');
      setBody('');
      setShareLocation(false);
      setMyLocation(null);
      setEventDate('');
      setCapacity('');
      setOpen(false);
      showToast('Post published.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + Write a post
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Write a post</h3>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      >
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        value={body} onChange={(e) => setBody(e.target.value)} placeholder="What's going on in the neighborhood?" required rows={4}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'vertical' }}
      />
      {category === 'meetup' && (
        <input
          type="datetime-local" value={eventDate} onChange={(e) => setEventDate(e.target.value)} required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
      )}
      {(category === 'meetup' || category === 'group_buy') && (
        <input
          type="number" min={2} max={category === 'group_buy' ? 4 : undefined} value={capacity}
          onChange={(e) => setCapacity(e.target.value)}
          placeholder={category === 'group_buy' ? 'Max people (up to 4, including you)' : 'Max people (optional)'}
          required={category === 'group_buy'}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
      )}
      <button
        type="button"
        className="itunda-btn itunda-btn-secondary"
        disabled={locating}
        onClick={handleToggleShareLocation}
        style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}
      >
        {locating ? 'Finding your real location…' : shareLocation ? '📍 Real location shared -- others nearby can find this post' : '📍 Share my real location (optional)'}
      </button>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Posting…' : 'Post'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </form>
  );
}

// Real Karrot 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) -- see
// lib/community.ts's own doc comment. Found via scripts/uncalled-endpoint-sweep.py:
// fully built on the backend with zero client anywhere. Scoped to MY posts (the
// preference only affects notifications about comments on posts the caller
// authored), same real reason this renders only inside the MINE view.
export function CommentNotificationToggle() {
  const { t } = useI18n();
  const [enabled, setEnabled] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCommentNotificationsEnabled().then(setEnabled).catch(() => setEnabled(true));
  }, []);

  const handleToggle = async () => {
    if (enabled === null) return;
    setBusy(true);
    setError(null);
    const next = !enabled;
    try {
      setEnabled(await setCommentNotificationsEnabled(next));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (enabled === null) return null;

  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0', marginBottom: '12px' }}>
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Notify me about new comments</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>On your own posts, in this neighborhood</p>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
      </div>
      <button
        type="button" role="switch" aria-checked={enabled} aria-label="Notify me about new comments" disabled={busy} onClick={handleToggle}
        style={{
          width: '44px', height: '26px', borderRadius: '13px', padding: '2px', flexShrink: 0,
          background: enabled ? 'var(--itunda-indigo)' : 'var(--itunda-grey-300)', display: 'flex', justifyContent: enabled ? 'flex-end' : 'flex-start',
        }}
      >
        <span style={{ width: '22px', height: '22px', borderRadius: '11px', background: 'white', display: 'block' }} />
      </button>
    </div>
  );
}

export function CommunityPostCard({ post, categoryLabel, isMine, onOpen, onChanged, joinedCount, joining, onJoin }: {
  post: CommunityPost; categoryLabel: string; isMine: boolean; onOpen: () => void; onChanged: () => void;
  joinedCount?: number; joining?: boolean; onJoin?: () => void;
}) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRemove = async () => {
    setBusy(true);
    try {
      await removeCommunityPost(post.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div
      className="itunda-card"
      style={{ display: 'flex', flexDirection: 'column', gap: '6px', cursor: 'pointer' }}
      role="button"
      tabIndex={0}
      onClick={onOpen}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpen(); } }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{categoryLabel}</span>
        {isMine && (
          <button
            className="itunda-btn itunda-btn-secondary"
            style={{ fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 10px' }}
            disabled={busy}
            onClick={(e) => { e.stopPropagation(); void handleRemove(); }}
          >
            {busy ? 'Removing…' : 'Remove'}
          </button>
        )}
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{post.title}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as const }}>
        {post.body}
      </p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-400)', display: 'flex', alignItems: 'center', gap: '4px' }}>
        <HeartFilled size={12} /> {post.likeCount} · 💬 {post.commentCount}
      </p>
      {/* Real AI-generated 모임 summary (2026-08-28) -- see backend
          HoodAiSummaryService's own doc comment. Never shown without this visible "AI"
          disclosure badge, same convention this session's Maps AI-summary work
          established. */}
      {post.aiSummary && (
        <div style={{ display: 'flex', gap: '6px', alignItems: 'flex-start', padding: '10px', borderRadius: '10px', background: 'var(--itunda-grey-50)' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-10-size)', fontWeight: 700, color: '#fff', background: 'var(--itunda-brand)', borderRadius: '4px', padding: '2px 5px' }}>AI</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)' }}>{post.aiSummary}</span>
        </div>
      )}
      {/* Real 참여하기 (join) tap (2026-07-24) -- a real join, not just a "view"
          navigation: it adds the tapper to a real GroupConversation (see backend
          CommunityService.joinMeetup's own doc comment), shown with a real "N joined"
          count rather than a bare label. */}
      {!isMine && post.category === 'meetup' && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px', alignSelf: 'flex-start' }}
          disabled={joining}
          onClick={(e) => { e.stopPropagation(); onJoin?.(); }}
        >
          {joining ? 'Joining…' : `참여하기 · ${joinedCount ?? 0} joined`}
        </button>
      )}
      {!isMine && <HoodReportButton targetType="COMMUNITY_POST" targetId={post.id} />}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

// Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see
// lib/community.ts's own doc comment for the full sourced account. Only rendered for a
// real category === 'meetup' post; the author gets a real schedule form, any real
// joined member gets a real per-session check-in button.
export function MeetupSessionsSection({ post, currentUserId }: { post: CommunityPost; currentUserId: string | undefined }) {
  const { t } = useI18n();
  const [sessions, setSessions] = useState<MeetupSession[] | null>(null);
  const [dates, setDates] = useState<string[]>(['']);
  const [scheduling, setScheduling] = useState(false);
  const [checkingInId, setCheckingInId] = useState<string | null>(null);
  const [checkedInIds, setCheckedInIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMeetupSessions(post.id).then(setSessions).catch(() => setSessions([]));
  };

  useEffect(load, [post.id]);

  const isAuthor = currentUserId != null && currentUserId === post.authorId;

  const handleSchedule = async () => {
    const isoDates = dates.filter((d) => d).map((d) => new Date(d).toISOString());
    if (isoDates.length === 0) { setError('Add at least one session date.'); return; }
    setScheduling(true);
    setError(null);
    try {
      await scheduleMeetupSessions(post.id, isoDates);
      setDates(['']);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setScheduling(false);
    }
  };

  const handleCheckIn = async (sessionId: string) => {
    setCheckingInId(sessionId);
    setError(null);
    try {
      await checkIntoMeetupSession(sessionId);
      setCheckedInIds((prev) => new Set(prev).add(sessionId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setCheckingInId(null);
    }
  };

  return (
    <div style={{ marginTop: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Sessions</h3>
      {sessions === null && <div className="skeleton" style={{ height: '60px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {sessions !== null && sessions.length === 0 && (
        <EmptyState message="No sessions scheduled yet — start one to meet up with neighbors." />
      )}
      {sessions !== null && sessions.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', marginBottom: '12px' }}>
          {sessions.map((s) => (
            <div key={s.id} className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{new Date(s.scheduledFor).toLocaleString()}</p>
              <button
                className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px' }}
                disabled={checkingInId === s.id || checkedInIds.has(s.id)}
                onClick={() => handleCheckIn(s.id)}
              >
                {checkedInIds.has(s.id) ? '✓ Checked in' : checkingInId === s.id ? '…' : 'Check in'}
              </button>
            </div>
          ))}
        </div>
      )}
      {isAuthor && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '8px' }}>Schedule sessions (up to 6)</p>
          {dates.map((d, i) => (
            <input
              key={i} type="datetime-local" value={d}
              onChange={(e) => setDates((prev) => prev.map((v, idx) => (idx === i ? e.target.value : v)))}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginBottom: '6px' }}
            />
          ))}
          <div style={{ display: 'flex', gap: '8px' }}>
            {dates.length < 6 && (
              <button className="itunda-btn itunda-btn-secondary" onClick={() => setDates((prev) => [...prev, ''])}>+ Add date</button>
            )}
            <button className="itunda-btn itunda-btn-primary" disabled={scheduling} onClick={handleSchedule}>
              {scheduling ? 'Scheduling…' : 'Schedule'}
            </button>
          </div>
        </div>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see lib/community.ts's own
// finalizeGroupBuy doc comment. Author-only: once real participants have joined via the
// same 참여하기 flow a meetup already uses, the organizer fronts the total cost and
// splits it via the already-real SplitBill mechanic.
export function GroupBuyFinalizeSection({ post, currentUserId }: { post: CommunityPost; currentUserId: string | undefined }) {
  const { t } = useI18n();
  const [totalAmount, setTotalAmount] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (currentUserId == null || currentUserId !== post.authorId) return null;

  const handleFinalize = async () => {
    const amount = Number(totalAmount);
    if (!amount || amount <= 0 || !description.trim()) { setError('Enter a real total amount and a short description.'); return; }
    setSubmitting(true);
    setError(null);
    try {
      await finalizeGroupBuy(post.id, amount, description.trim());
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <div style={{ marginTop: '16px' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>Split request sent -- see it in your group chat's Split bill tab.</p>
      </div>
    );
  }

  return (
    <div style={{ marginTop: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Split the cost</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Enter what you paid up front -- every real member who joined will be asked for their even share.
      </p>
      <input
        type="number" value={totalAmount} onChange={(e) => setTotalAmount(e.target.value)} placeholder="Total amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
      />
      <input
        type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What was this for?"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
      />
      <button className="itunda-btn itunda-btn-primary" disabled={submitting} onClick={handleFinalize}>
        {submitting ? 'Splitting…' : 'Request even split'}
      </button>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </div>
  );
}

