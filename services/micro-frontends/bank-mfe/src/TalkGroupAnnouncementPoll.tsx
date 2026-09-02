import { useEffect, useState } from 'react';
import { Megaphone } from 'lucide-react';
import { IconBack } from './icons/ItundaIcons';
import { EmptyState, ErrorCard } from './EmptyState';
import { ApiError } from './lib/api';
import {
  createGroupPoll, fetchGroupAnnouncement, fetchGroupPolls, postGroupAnnouncement, voteGroupPoll,
  type GroupAnnouncement, type GroupPollWithVotes,
} from './lib/talk';
import { useDeferredLoading } from './useDeferredLoading';

// Real group 공지/투표 (announcement/poll) (itunda Talk redesign, 2026-08-28) -- see
// backend GroupPollAnnouncementService's own doc comment. Split into its own file,
// same file-size-lint-driven convention BankCardChip.tsx already established.
// Deliberately open to ANY group member (no admin/role model exists in this
// codebase's group chat -- see GroupMessagingService's own doc comment naming that
// as a separate, deliberately-deferred piece).

function AnnouncementBody({ announcement }: { announcement: GroupAnnouncement | null | undefined }) {
  const showSkeleton = useDeferredLoading(announcement === undefined);
  if (announcement === undefined) return showSkeleton ? <div className="skeleton" style={{ height: '60px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (!announcement) return <EmptyState message="No announcement yet." />;
  return (
    <div style={{ display: 'flex', gap: '10px', padding: '12px 14px', backgroundColor: 'var(--itunda-grey-50)', borderRadius: 'var(--itunda-radius-md)' }}>
      <Megaphone size={18} color="var(--itunda-indigo)" style={{ flexShrink: 0, marginTop: '2px' }} />
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)', whiteSpace: 'pre-wrap' }}>{announcement.body}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '4px' }}>{new Date(announcement.createdAt).toLocaleString()}</p>
      </div>
    </div>
  );
}

function PollsList({ polls, onVote }: { polls: GroupPollWithVotes[] | null; onVote: (pollId: string, optionId: string) => void }) {
  const showSkeleton = useDeferredLoading(polls === null);
  if (polls === null) return showSkeleton ? <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (polls.length === 0) return <EmptyState message="No polls yet." />;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      {polls.map(({ poll, options, voteCountByOptionId, myVoteOptionIds }) => {
        const totalVotes = Object.values(voteCountByOptionId).reduce((a, b) => a + b, 0);
        return (
          <div key={poll.id} style={{ padding: '12px 14px', backgroundColor: 'var(--itunda-grey-50)', borderRadius: 'var(--itunda-radius-md)' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>{poll.question}</p>
            {options.map((opt) => {
              const count = voteCountByOptionId[opt.id] ?? 0;
              const pct = totalVotes > 0 ? Math.round((count / totalVotes) * 100) : 0;
              const mine = myVoteOptionIds.includes(opt.id);
              return (
                <button
                  key={opt.id}
                  type="button"
                  onClick={() => onVote(poll.id, opt.id)}
                  style={{
                    display: 'block', width: '100%', textAlign: 'left', position: 'relative', overflow: 'hidden',
                    padding: '8px 10px', marginBottom: '6px', borderRadius: 'var(--itunda-radius-sm)',
                    border: mine ? '1px solid var(--itunda-indigo)' : '1px solid var(--itunda-grey-200)',
                    backgroundColor: 'var(--itunda-white)', cursor: 'pointer',
                  }}
                >
                  <div style={{ position: 'absolute', inset: 0, width: `${pct}%`, backgroundColor: 'var(--itunda-indigo-light)', zIndex: 0 }} />
                  <div style={{ position: 'relative', zIndex: 1, display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-900)' }}>
                    <span>{opt.text}{mine ? ' ✓' : ''}</span>
                    <span>{count} · {pct}%</span>
                  </div>
                </button>
              );
            })}
          </div>
        );
      })}
    </div>
  );
}

export function TalkGroupAnnouncementPoll({ groupId, onBack }: { groupId: string; onBack: () => void }) {
  const [announcement, setAnnouncement] = useState<GroupAnnouncement | null | undefined>(undefined);
  const [polls, setPolls] = useState<GroupPollWithVotes[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [announcementDraft, setAnnouncementDraft] = useState('');
  const [postingAnnouncement, setPostingAnnouncement] = useState(false);
  const [showNewPoll, setShowNewPoll] = useState(false);
  const [pollQuestion, setPollQuestion] = useState('');
  const [pollOptions, setPollOptions] = useState(['', '']);
  const [creatingPoll, setCreatingPoll] = useState(false);

  const load = () => {
    setError(null);
    Promise.all([fetchGroupAnnouncement(groupId), fetchGroupPolls(groupId)])
      .then(([a, p]) => { setAnnouncement(a); setPolls(p); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load.'));
  };
  useEffect(load, [groupId]);

  const submitAnnouncement = () => {
    const body = announcementDraft.trim();
    if (!body) return;
    setPostingAnnouncement(true);
    postGroupAnnouncement(groupId, body)
      .then((a) => { setAnnouncement(a); setAnnouncementDraft(''); })
      .catch(() => {})
      .finally(() => setPostingAnnouncement(false));
  };

  const submitPoll = () => {
    const question = pollQuestion.trim();
    const options = pollOptions.map((o) => o.trim()).filter(Boolean);
    if (!question || options.length < 2) return;
    setCreatingPoll(true);
    createGroupPoll(groupId, question, options)
      .then((poll) => { setPolls((prev) => [poll, ...(prev ?? [])]); setShowNewPoll(false); setPollQuestion(''); setPollOptions(['', '']); })
      .catch(() => {})
      .finally(() => setCreatingPoll(false));
  };

  const vote = (pollId: string, optionId: string) => {
    voteGroupPoll(groupId, pollId, optionId)
      .then((updated) => setPolls((prev) => (prev ?? []).map((p) => (p.poll.id === pollId ? updated : p))))
      .catch(() => {});
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)', overflowY: 'auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to group">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>공지 &amp; 투표</h3>
      </div>

      {error && <ErrorCard message={error} onRetry={load} />}

      <section style={{ marginBottom: '24px' }}>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>공지 (Announcement)</h4>
        <AnnouncementBody announcement={announcement} />
        <div style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
          <input
            value={announcementDraft}
            onChange={(e) => setAnnouncementDraft(e.target.value)}
            placeholder={announcement ? 'Post a new announcement (replaces the old one)' : 'Post an announcement'}
            style={{ flex: 1, padding: '10px 14px', borderRadius: 'var(--itunda-radius-pill)', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="button" className="itunda-btn itunda-btn-secondary" disabled={postingAnnouncement || !announcementDraft.trim()} onClick={submitAnnouncement}>
            Post
          </button>
        </div>
      </section>

      <section>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>투표 (Polls)</h4>
          <button type="button" onClick={() => setShowNewPoll((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-indigo)', background: 'none', border: 'none', cursor: 'pointer' }}>
            {showNewPoll ? 'Cancel' : '+ New poll'}
          </button>
        </div>

        {showNewPoll && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px 14px', backgroundColor: 'var(--itunda-grey-50)', borderRadius: 'var(--itunda-radius-md)', marginBottom: '12px' }}>
            <input
              value={pollQuestion}
              onChange={(e) => setPollQuestion(e.target.value)}
              placeholder="Question"
              style={{ padding: '8px 10px', borderRadius: 'var(--itunda-radius-sm)', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            {pollOptions.map((opt, i) => (
              <input
                key={i}
                value={opt}
                onChange={(e) => setPollOptions((prev) => prev.map((o, idx) => (idx === i ? e.target.value : o)))}
                placeholder={`Option ${i + 1}`}
                style={{ padding: '8px 10px', borderRadius: 'var(--itunda-radius-sm)', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
              />
            ))}
            <button type="button" onClick={() => setPollOptions((prev) => [...prev, ''])} style={{ alignSelf: 'flex-start', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', background: 'none', border: 'none', cursor: 'pointer' }}>
              + Add option
            </button>
            <button type="button" className="itunda-btn itunda-btn-primary" disabled={creatingPoll} onClick={submitPoll}>
              Create poll
            </button>
          </div>
        )}

        <PollsList polls={polls} onVote={vote} />
      </section>
    </div>
  );
}
