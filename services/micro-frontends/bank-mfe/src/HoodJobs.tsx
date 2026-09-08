import { useEffect, useState } from 'react';
import { ErrorCard } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { HeartOutline } from './icons/ItundaFaceHearts';
import { ApiError, getStoredUser } from './lib/api';
import { addJobPostFavorite, contactPoster, fetchJobCategories, fetchJobPosts, fetchJobPostsMyNeighborhood, fetchMyFavoriteJobPosts, fetchMyJobPosts, fetchMyWorkedJobPosts, removeJobPostFavorite, searchJobPosts, type JobCategory, type JobPost } from './lib/jobs';
import { type TrustScores } from './lib/marketplace';
import { fetchProfile } from './lib/neighborhood';
import { NewJobPostCard, JobPostCard, MyJobApplicationsView, JobPostWishlistView } from './HoodJobsCards';
import { NeighborhoodSetupPrompt, NeighborhoodSwitcherRow } from './BankDashboard';
import { ResumeBuilderView } from './HoodResumeBuilder';
import { useDeferredLoading } from './useDeferredLoading';

export function JobsView({ onMessagePoster }: { onMessagePoster: (conversationId: string) => void }) {
  const { t } = useI18n();
  // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
  // recommendation #6, see backend JobPostRepository's own doc comment.
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'WORKED' | 'NEIGHBORHOOD' | 'WISHLIST' | 'APPLICATIONS' | 'RESUME'>('BROWSE');
  const [categories, setCategories] = useState<JobCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [posts, setPosts] = useState<JobPost[] | null>(null);
  const showPostsSkeleton = useDeferredLoading(posts === null);
  // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
  const [trustScores, setTrustScores] = useState<TrustScores>({});
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);
  const currentUser = getStoredUser();
  // Real pagination-discard fix (same systemic gap fixed for Knowledge/Community/
  // Marketplace, 2026-09-09) -- a request never asked past page 0 across
  // BROWSE/MINE/WORKED/NEIGHBORHOOD, so any feed with more than 20 real posts
  // was silently unreachable beyond the first page.
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  // Real relevance-ranked search (2026-08-14) -- see lib/jobs.ts's own doc comment: this
  // endpoint shipped Android-only and was never ported to web until now. Mirrors
  // ShoppingView's own real cross-merchant product-search state shape field-for-field.
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<{ posts: JobPost[]; trustScores: TrustScores } | null>(null);
  const [searching, setSearching] = useState(false);
  // Real pagination-discard fix (2026-09-09) -- separate from the main
  // browse/mine/worked/neighborhood page/hasMore state above since search
  // results are their own independent list.
  const [searchPage, setSearchPage] = useState(0);
  const [searchHasMore, setSearchHasMore] = useState(false);
  const [searchLoadingMore, setSearchLoadingMore] = useState(false);
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setSearching(true);
    try {
      const result = await searchJobPosts(searchQuery.trim(), 0);
      setSearchResults({ posts: result.posts, trustScores: result.trustScores });
      setSearchPage(0);
      setSearchHasMore(result.page + 1 < result.totalPages);
    } catch {
      setSearchResults({ posts: [], trustScores: {} });
      setSearchHasMore(false);
    } finally {
      setSearching(false);
    }
  };

  const loadMoreSearchResults = () => {
    const nextPage = searchPage + 1;
    setSearchLoadingMore(true);
    searchJobPosts(searchQuery.trim(), nextPage)
      .then((result) => {
        setSearchResults((prev) => ({
          posts: [...(prev?.posts ?? []), ...result.posts],
          trustScores: { ...(prev?.trustScores ?? {}), ...result.trustScores },
        }));
        setSearchPage(nextPage);
        setSearchHasMore(result.page + 1 < result.totalPages);
      })
      .catch(() => {})
      .finally(() => setSearchLoadingMore(false));
  };

  useEffect(() => {
    fetchJobCategories().then(setCategories).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const loadFavoriteIds = () => {
    fetchMyFavoriteJobPosts().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.jobPostId)))).catch(() => {
      // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
    });
  };

  const load = () => {
    setError(null);
    setPosts(null);
    setPage(0);
    setHasMore(false);
    loadFavoriteIds();
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchJobPostsMyNeighborhood(activeCategory ?? undefined, 0)])
        .then(([profile, result]) => {
          setNeighborhoodName(profile.neighborhood);
          setSecondNeighborhoodName(profile.secondNeighborhood);
          setPosts(result.posts);
          setTrustScores(result.trustScores);
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
    if (view === 'WISHLIST' || view === 'APPLICATIONS' || view === 'RESUME') return;
    let fetcher;
    if (view === 'BROWSE') {
      fetcher = fetchJobPosts(activeCategory ?? undefined, 0);
    } else if (view === 'WORKED') {
      fetcher = fetchMyWorkedJobPosts(0);
    } else {
      fetcher = fetchMyJobPosts(0);
    }
    fetcher
      .then((result) => {
        setPosts(result.posts);
        setTrustScores(result.trustScores);
        setHasMore(result.page + 1 < result.totalPages);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  const loadMore = () => {
    const nextPage = page + 1;
    setLoadingMore(true);
    let fetcher;
    if (view === 'NEIGHBORHOOD') {
      fetcher = fetchJobPostsMyNeighborhood(activeCategory ?? undefined, nextPage);
    } else if (view === 'BROWSE') {
      fetcher = fetchJobPosts(activeCategory ?? undefined, nextPage);
    } else if (view === 'WORKED') {
      fetcher = fetchMyWorkedJobPosts(nextPage);
    } else {
      fetcher = fetchMyJobPosts(nextPage);
    }
    fetcher
      .then((result) => {
        setPosts((prev) => [...(prev ?? []), ...result.posts]);
        setTrustScores((prev) => ({ ...prev, ...result.trustScores }));
        setPage(nextPage);
        setHasMore(result.page + 1 < result.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMore(false));
  };

  useEffect(load, [view, activeCategory]);

  const categoryLabel = (id: string) => categories.find((c) => c.id === id)?.label ?? id;

  const handleContact = async (jobPostId: string) => {
    try {
      const conversation = await contactPoster(jobPostId);
      onMessagePoster(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  // Real 당근알바 job-post wishlist (2026-07-22) -- mirrors MarketplaceView's own
  // toggleFavorite field-for-field.
  const toggleFavorite = async (jobPostId: string) => {
    setFavoritingId(jobPostId);
    try {
      if (favoriteIds.has(jobPostId)) {
        await removeJobPostFavorite(jobPostId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(jobPostId); return next; });
      } else {
        await addJobPostFavorite(jobPostId);
        setFavoriteIds((prev) => new Set(prev).add(jobPostId));
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block browsing.
    } finally {
      setFavoritingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE', 'WORKED', 'APPLICATIONS', 'WISHLIST', 'RESUME'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Find work' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : v === 'MINE' ? 'My posts' : v === 'WORKED' ? 'Jobs I did' : v === 'APPLICATIONS' ? 'My applications' : v === 'RESUME' ? 'My résumé' : <><HeartOutline size={12} /> Wishlist</>}
          </button>
        ))}
      </div>

      {view === 'WISHLIST' ? (
        <JobPostWishlistView />
      ) : view === 'APPLICATIONS' ? (
        <MyJobApplicationsView />
      ) : view === 'RESUME' ? (
        <ResumeBuilderView />
      ) : (
        <>
          {view === 'BROWSE' && (
            <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search jobs"
                style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
              />
              <button type="submit" className="itunda-btn itunda-btn-primary" disabled={searching || !searchQuery.trim()}>
                {searching ? '…' : 'Search'}
              </button>
              {searchResults !== null && (
                <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => { setSearchResults(null); setSearchQuery(''); }}>
                  Clear
                </button>
              )}
            </form>
          )}

          {(view === 'BROWSE' || view === 'NEIGHBORHOOD') && searchResults === null && categories.length > 0 && (
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

          {view === 'MINE' && <NewJobPostCard categories={categories} onCreated={load} />}

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

          {view === 'BROWSE' && searchResults !== null && (
            <>
              {searchResults.posts.length === 0 ? (
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>No jobs matched "{searchQuery}".</p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  {searchResults.posts.map((post) => (
                    <JobPostCard
                      key={post.id}
                      post={post}
                      categoryLabel={categoryLabel(post.category)}
                      isMine={post.posterId === currentUser?.id}
                      onChanged={() => {
                        searchJobPosts(searchQuery.trim(), 0)
                          .then((result) => {
                            setSearchResults({ posts: result.posts, trustScores: result.trustScores });
                            setSearchPage(0);
                            setSearchHasMore(result.page + 1 < result.totalPages);
                          })
                          .catch(() => {});
                      }}
                      onContact={() => handleContact(post.id)}
                      favorited={favoriteIds.has(post.id)}
                      favoriteBusy={favoritingId === post.id}
                      onToggleFavorite={() => toggleFavorite(post.id)}
                      posterTrustScore={searchResults.trustScores[post.posterId]}
                    />
                  ))}
                  {searchHasMore && (
                    <button className="itunda-btn itunda-btn-secondary" disabled={searchLoadingMore} onClick={loadMoreSearchResults}>
                      {searchLoadingMore ? 'Loading…' : 'Load more'}
                    </button>
                  )}
                </div>
              )}
            </>
          )}

          {(view !== 'BROWSE' || searchResults === null) && (
            <>
              {error && (
                <ErrorCard message={error} onRetry={load} />
              )}
              {!error && posts === null && showPostsSkeleton && <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />}
              {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && posts !== null && posts.length === 0 && (
                // Real copy-voice fix (item 244, round 5 of the empty-state pass,
                // ported from the same-day Android/iOS fix): say what's missing AND
                // what fixes it, per this screen's own real "+ Post a job" button
                // above in the MINE view.
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
                  {view === 'BROWSE' ? 'No jobs posted yet — check back soon, or post one yourself.' : view === 'NEIGHBORHOOD' ? 'No jobs in your neighborhood yet — try Browse to see jobs from everywhere.' : view === 'WORKED' ? 'No completed jobs recorded yet — jobs you complete will show up here.' : 'You haven\'t posted any jobs yet — tap "+ Post a job" above to post your first one.'}
                </p>
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
                      favorited={favoriteIds.has(post.id)}
                      favoriteBusy={favoritingId === post.id}
                      onToggleFavorite={() => toggleFavorite(post.id)}
                      posterTrustScore={trustScores[post.posterId]}
                    />
                  ))}
                  {hasMore && (
                    <button className="itunda-btn itunda-btn-secondary" disabled={loadingMore} onClick={loadMore}>
                      {loadingMore ? 'Loading…' : 'Load more'}
                    </button>
                  )}
                </div>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}

// ============================== PROPERTY (당근부동산) ==============================

