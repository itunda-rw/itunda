import { useEffect, useState } from 'react';
import { EmptyState, ErrorCard } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { WishlistHeart } from './icons/ItundaFaceHearts';
import { ApiError, getStoredUser } from './lib/api';
import { addCommunityComment, fetchCommunityCategories, fetchCommunityComments, fetchCommunityPost, fetchCommunityPosts, fetchCommunityPostsMyNeighborhood, fetchCommunityTopics, fetchMyCommunityPosts, fetchUpcomingMeetups, joinCommunityMeetup, toggleCommunityLike, type CommunityCategory, type CommunityComment, type CommunityPost, type JoinedCounts } from './lib/community';
import { fetchProfile } from './lib/neighborhood';
import { NewCommunityPostCard, CommentNotificationToggle, CommunityPostCard, MeetupSessionsSection, GroupBuyFinalizeSection } from './HoodCommunityCards';
import { NeighborhoodSetupPrompt, NeighborhoodSwitcherRow } from './BankDashboard';
import { useDeferredLoading } from './useDeferredLoading';

export function CommunityPostDetailView({ postId, onBack }: { postId: string; onBack: () => void }) {
  const { t } = useI18n();
  const [post, setPost] = useState<CommunityPost | null>(null);
  const showPostSkeleton = useDeferredLoading(!post);
  const [authorName, setAuthorName] = useState('');
  const [likedByMe, setLikedByMe] = useState(false);
  const [comments, setComments] = useState<{ comment: CommunityComment; authorName: string }[] | null>(null);
  const showCommentsSkeleton = useDeferredLoading(comments === null);
  // Real pagination-discard fix (2026-09-13, see project_itunda_pagination_discard_sweep
  // memory) -- fetchCommunityComments silently capped this thread at its oldest 20
  // comments (backend sorts ascending by createdAt), so a post with 20+ comments never
  // showed any newer ones at all -- including a user's own comment right after posting it.
  const [commentsPage, setCommentsPage] = useState(0);
  const [commentsHasMore, setCommentsHasMore] = useState(false);
  const [loadingMoreComments, setLoadingMoreComments] = useState(false);
  const [commentBody, setCommentBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [liking, setLiking] = useState(false);
  const [commenting, setCommenting] = useState(false);
  const currentUser = getStoredUser();

  const load = () => {
    setError(null);
    fetchCommunityPost(postId)
      .then((r) => { setPost(r.post); setAuthorName(r.authorName); setLikedByMe(r.likedByMe); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchCommunityComments(postId, 0)
      .then((r) => { setComments(r.comments); setCommentsPage(0); setCommentsHasMore(r.page + 1 < r.totalPages); })
      .catch(() => { /* non-critical -- the post itself still renders */ });
  };

  useEffect(load, [postId]);

  const loadMoreComments = async () => {
    const nextPage = commentsPage + 1;
    setLoadingMoreComments(true);
    try {
      const r = await fetchCommunityComments(postId, nextPage);
      setComments((prev) => {
        const existingIds = new Set((prev ?? []).map((c) => c.comment.id));
        return [...(prev ?? []), ...r.comments.filter((c) => !existingIds.has(c.comment.id))];
      });
      setCommentsPage(nextPage);
      setCommentsHasMore(r.page + 1 < r.totalPages);
    } catch {
      // Non-critical -- the already-loaded comments stay visible; the user can
      // retry by tapping "Load more" again.
    } finally {
      setLoadingMoreComments(false);
    }
  };

  const handleLike = async () => {
    setLiking(true);
    try {
      const liked = await toggleCommunityLike(postId);
      setLikedByMe(liked);
      setPost((p) => (p ? { ...p, likeCount: p.likeCount + (liked ? 1 : -1) } : p));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
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
      const comment = await addCommunityComment(postId, commentBody);
      setCommentBody('');
      // Real fix: append the new comment directly rather than reloading page 0 --
      // page 0 only ever holds the OLDEST comments (ascending sort), so once a post
      // has 20+ comments, reloading page 0 would make the user's own just-posted
      // comment disappear entirely.
      const authorName = currentUser ? `${currentUser.firstName} ${currentUser.lastName}` : 'You';
      setComments((prev) => [...(prev ?? []), { comment, authorName }]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setCommenting(false);
    }
  };

  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" style={{ marginBottom: '12px' }} onClick={onBack}>← Back</button>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {!post && !error && showPostSkeleton && <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {post && (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{post.title}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>by {authorName}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-700)', whiteSpace: 'pre-wrap' }}>{post.body}</p>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={liking}
            onClick={handleLike}
            style={{ alignSelf: 'flex-start', fontSize: 'var(--itunda-type-scale-13-size)', display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <WishlistHeart favorited={likedByMe} size={16} /> {post.likeCount}
          </button>
        </div>
      )}
      {post && post.category === 'meetup' && <MeetupSessionsSection post={post} currentUserId={currentUser?.id} />}
      {post && post.category === 'group_buy' && <GroupBuyFinalizeSection post={post} currentUserId={currentUser?.id} />}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Comments</h3>
      {comments === null && showCommentsSkeleton && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {comments !== null && comments.length === 0 && (
        <EmptyState message="No comments yet -- be the first to reply." />
      )}
      {comments !== null && comments.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', marginBottom: '12px' }}>
          {comments.map(({ comment, authorName: name }) => (
            <div key={comment.id} className="itunda-flat-section">
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-700)' }}>{name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-900)' }}>{comment.body}</p>
            </div>
          ))}
          {commentsHasMore && (
            <button
              className="itunda-btn itunda-btn-secondary"
              onClick={loadMoreComments}
              disabled={loadingMoreComments}
              style={{ alignSelf: 'flex-start', marginTop: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}
            >
              {loadingMoreComments ? 'Loading…' : 'Load more comments'}
            </button>
          )}
        </div>
      )}
      <form onSubmit={handleComment} style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text" value={commentBody} onChange={(e) => setCommentBody(e.target.value)} placeholder="Add a comment"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={commenting || !commentBody.trim()}>
          {commenting ? '…' : 'Send'}
        </button>
      </form>
    </div>
  );
}

export function CommunityView({ onOpenGroupChat }: { onOpenGroupChat: (groupId: string) => void }) {
  const { t } = useI18n();
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'NEIGHBORHOOD'>('BROWSE');
  const [categories, setCategories] = useState<CommunityCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  // Real 동네생활 topic-chip filter row (2026-08-28) -- a lifestyle axis independent of
  // the functional category above, see backend CommunityService.TOPICS' own doc comment.
  const [topics, setTopics] = useState<CommunityCategory[]>([]);
  const [activeTopic, setActiveTopic] = useState<string | null>(null);
  const [posts, setPosts] = useState<CommunityPost[] | null>(null);
  const showPostsSkeleton = useDeferredLoading(posts === null);
  // Real 같이해요 (join-together) group join counts (2026-07-24) -- see TrustBadge's
  // sibling doc comments; closes docs/DESIGN_REFERENCES.md Section 4 recommendation #4.
  const [joinedCounts, setJoinedCounts] = useState<JoinedCounts>({});
  // Real fix (2026-09-04): the "🎉 Meetups" pinned slot below used to be a client-side
  // filter of whatever page of the general feed happened to be loaded (sorted by post
  // creation time, with no eventDate check at all) -- meaning an already-happened
  // meetup could appear pinned as if upcoming, and a genuinely upcoming meetup could be
  // missing entirely if it fell off the loaded page. The real backend
  // GET /api/v1/community/meetups/upcoming (built 2026-07-25) already excludes past
  // eventDates and orders by soonest first, but had zero callers on any client
  // (confirmed via repo-wide grep, matches Android's own identical dead-binding
  // finding) until now. Only used when NOT explicitly browsing the Meetups category
  // chip -- that case keeps its original full chronological (including past) browse,
  // since deliberately browsing "Meetups" is a different intent than the passive
  // homefeed highlight strip.
  const [upcomingMeetups, setUpcomingMeetups] = useState<CommunityPost[]>([]);
  const [joiningPostId, setJoiningPostId] = useState<string | null>(null);
  const [openPostId, setOpenPostId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const currentUser = getStoredUser();
  // Real pagination-discard fix (named as the systemic sibling of the Knowledge
  // gap fixed in 134758cf/30741887/a2dc88dc) -- a request never asked past page
  // 0 across all 3 views (Browse/Mine/Neighborhood), so any feed with more than
  // 20 real posts was silently unreachable beyond the first page.
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    fetchCommunityCategories().then(setCategories).catch(() => { /* chips just won't render, browse still works */ });
    fetchCommunityTopics().then(setTopics).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const loadUpcomingMeetups = () => {
    if (view === 'MINE' || activeCategory === 'meetup') {
      setUpcomingMeetups([]);
      return;
    }
    fetchUpcomingMeetups()
      .then((result) => {
        setUpcomingMeetups(result.posts);
        setJoinedCounts((prev) => ({ ...prev, ...result.joinedCounts }));
      })
      .catch(() => { /* pinned strip just won't render, main feed load below still works */ });
  };

  const load = () => {
    setError(null);
    setPosts(null);
    setPage(0);
    setHasMore(false);
    loadUpcomingMeetups();
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchCommunityPostsMyNeighborhood(activeCategory ?? undefined, 0)])
        .then(([profile, result]) => {
          setNeighborhoodName(profile.neighborhood);
          setSecondNeighborhoodName(profile.secondNeighborhood);
          setPosts(result.posts);
          setJoinedCounts(result.joinedCounts);
          setHasMore(result.page + 1 < result.totalPages);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setPosts([]);
          } else {
            setError(err instanceof ApiError ? err.message : t('common.loadError'));
          }
        });
      return;
    }
    const fetcher = view === 'BROWSE' ? fetchCommunityPosts(activeCategory ?? undefined, activeTopic ?? undefined, 0) : fetchMyCommunityPosts(0);
    fetcher
      .then((result) => {
        setPosts(result.posts);
        setJoinedCounts(result.joinedCounts);
        setHasMore(result.page + 1 < result.totalPages);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  const loadMore = () => {
    const nextPage = page + 1;
    setLoadingMore(true);
    let fetcher;
    if (view === 'NEIGHBORHOOD') {
      fetcher = fetchCommunityPostsMyNeighborhood(activeCategory ?? undefined, nextPage);
    } else if (view === 'BROWSE') {
      fetcher = fetchCommunityPosts(activeCategory ?? undefined, activeTopic ?? undefined, nextPage);
    } else {
      fetcher = fetchMyCommunityPosts(nextPage);
    }
    fetcher
      .then((result) => {
        setPosts((prev) => [...(prev ?? []), ...result.posts]);
        setJoinedCounts((prevCounts) => ({ ...prevCounts, ...result.joinedCounts }));
        setPage(nextPage);
        setHasMore(result.page + 1 < result.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMore(false));
  };

  useEffect(load, [view, activeCategory, activeTopic]);

  // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see backend
  // CommunityService.joinMeetup's own doc comment.
  const joinMeetup = async (postId: string) => {
    setJoiningPostId(postId);
    try {
      const groupId = await joinCommunityMeetup(postId);
      setJoinedCounts((prev) => ({ ...prev, [postId]: (prev[postId] ?? 0) + 1 }));
      onOpenGroupChat(groupId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setJoiningPostId(null);
    }
  };

  if (openPostId) {
    return <CommunityPostDetailView postId={openPostId} onBack={() => { setOpenPostId(null); load(); }} />;
  }

  const categoryLabel = (id: string) => categories.find((c) => c.id === id)?.label ?? id;

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
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
                whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                border: `1px solid ${activeCategory === c.id ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)'}`,
                color: activeCategory === c.id ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                backgroundColor: activeCategory === c.id ? 'var(--itunda-indigo)' : 'transparent',
              }}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}

      {view === 'BROWSE' && topics.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
          {topics.map((t) => (
            <button
              key={t.id}
              onClick={() => setActiveTopic(activeTopic === t.id ? null : t.id)}
              style={{
                whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                border: `1px solid ${activeTopic === t.id ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)'}`,
                color: activeTopic === t.id ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                backgroundColor: activeTopic === t.id ? 'var(--itunda-indigo)' : 'transparent',
              }}
            >
              {t.label}
            </button>
          ))}
        </div>
      )}

      {view === 'MINE' && <CommentNotificationToggle />}
      {view === 'MINE' && <NewCommunityPostCard categories={categories} onCreated={load} />}

      {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
        <NeighborhoodSetupPrompt onDone={() => load()} />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <NeighborhoodSwitcherRow
          secondNeighborhoodName={secondNeighborhoodName}
          onAddTapped={() => setShowSecondNeighborhoodPrompt(true)}
          onRemoved={(next) => setSecondNeighborhoodName(next)}
        />
      )}

      {view === 'NEIGHBORHOOD' && showSecondNeighborhoodPrompt && (
        <NeighborhoodSetupPrompt
          isSecond
          onDone={(name) => { setSecondNeighborhoodName(name); setShowSecondNeighborhoodPrompt(false); }}
        />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
          Your neighborhood: <strong style={{ color: 'var(--itunda-grey-900)' }}>{neighborhoodName}</strong>
        </p>
      )}

      {error && (
        <ErrorCard message={error} onRetry={load} />
      )}
      {!error && posts === null && showPostsSkeleton && <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && posts !== null && posts.length === 0 && (
        // Real copy-voice fix (item 244, round 5 of the empty-state pass, ported
        // from the same-day Android/iOS fix): say what's missing AND what fixes
        // it, per this screen's own real "+ Write a post" button above in MINE.
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          {view === 'BROWSE' ? 'No posts yet — be the first to share something with your neighbors.' : view === 'NEIGHBORHOOD' ? 'No posts in your neighborhood yet — try Browse to see posts from everywhere.' : 'You haven\'t posted anything yet — tap "+ Write a post" above to share your first one.'}
        </p>
      )}
      {!error && posts !== null && posts.length > 0 && (() => {
        // Real 같이해요 (join-together) pinned mid-feed slot (2026-07-24) -- Karrot's
        // real board gives meetup posts a dedicated slot instead of mixing them purely
        // chronologically (docs/DESIGN_REFERENCES.md Section 4 recommendation #4). "My
        // posts" stays plain chronological. Sourced from the real upcoming-only,
        // soonest-first `upcomingMeetups` fetch above (not a client-side filter of this
        // page's posts) except when explicitly browsing the Meetups category chip,
        // which keeps showing the full chronological browse including past ones.
        let meetups: CommunityPost[] = [];
        if (view !== 'MINE') {
          meetups = activeCategory === 'meetup' ? posts.filter((p) => p.category === 'meetup') : upcomingMeetups;
        }
        const regular = view !== 'MINE' ? posts.filter((p) => p.category !== 'meetup') : posts;
        const renderCard = (post: CommunityPost) => (
          <CommunityPostCard
            key={post.id}
            post={post}
            categoryLabel={categoryLabel(post.category)}
            isMine={view === 'MINE' || post.authorId === currentUser?.id}
            onOpen={() => setOpenPostId(post.id)}
            onChanged={load}
            joinedCount={joinedCounts[post.id] ?? 0}
            joining={joiningPostId === post.id}
            onJoin={() => joinMeetup(post.id)}
          />
        );
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {meetups.length > 0 && (
              <>
                <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>🎉 Meetups</p>
                {meetups.map(renderCard)}
              </>
            )}
            {regular.map(renderCard)}
            {hasMore && (
              <button className="itunda-btn itunda-btn-secondary" disabled={loadingMore} onClick={loadMore}>
                {loadingMore ? 'Loading…' : 'Load more'}
              </button>
            )}
          </div>
        );
      })()}
    </div>
  );
}

// ============================== JOBS (당근알바) ==============================

