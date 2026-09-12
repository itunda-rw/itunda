import { useEffect, useState } from 'react';
import { EmptyState, ErrorCard } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { fetchMyFavoriteJobPosts, removeJobPostFavorite, type FavoriteJobPost } from './lib/jobs';
import { useDeferredLoading } from './useDeferredLoading';

// Extracted from HoodJobsCards.tsx (2026-09-12, file-size-lint -- that file
// crossed 500 lines for the first time once this component's own pagination
// fix landed) -- a fully self-contained wishlist screen, no shared state or
// helper with any other component in that file.
//
// Real 당근알바 job-post wishlist view (2026-07-22) -- mirrors ListingWishlistView
// exactly, closing a docs/DESIGN_REFERENCES.md-named gap.
export function JobPostWishlistView() {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoriteJobPost[] | null>(null);
  const showFavoritesSkeleton = useDeferredLoading(favorites === null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  // Real pagination-discard fix (2026-09-12) -- see lib/jobs.ts's own doc
  // comment on fetchMyFavoriteJobPosts.
  const [favoritesPage, setFavoritesPage] = useState(0);
  const [favoritesHasMore, setFavoritesHasMore] = useState(false);
  const [loadingMoreFavorites, setLoadingMoreFavorites] = useState(false);

  const load = () => {
    setError(null);
    fetchMyFavoriteJobPosts(0)
      .then((r) => { setFavorites(r.favorites); setFavoritesPage(0); setFavoritesHasMore(r.page + 1 < r.totalPages); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const loadMoreFavorites = () => {
    const nextPage = favoritesPage + 1;
    setLoadingMoreFavorites(true);
    fetchMyFavoriteJobPosts(nextPage)
      .then((r) => {
        setFavorites((prev) => [...(prev ?? []), ...r.favorites]);
        setFavoritesPage(nextPage);
        setFavoritesHasMore(r.page + 1 < r.totalPages);
      })
      .catch(() => {})
      .finally(() => setLoadingMoreFavorites(false));
  };

  const handleRemove = async (jobPostId: string) => {
    setRemovingId(jobPostId);
    try {
      await removeJobPostFavorite(jobPostId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return showFavoritesSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (favorites.length === 0) return <EmptyState message="No saved jobs yet -- tap ♡ on any job post to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {favorites.map((f) => (
        <div key={f.jobPostId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{f.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{f.category} · {f.payAmount.toLocaleString('en-US')} RWF</p>
          </div>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={removingId === f.jobPostId}
            onClick={() => handleRemove(f.jobPostId)}
            style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {removingId === f.jobPostId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
      {favoritesHasMore && (
        <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreFavorites} onClick={loadMoreFavorites} style={{ marginTop: '8px' }}>
          {loadingMoreFavorites ? 'Loading…' : 'Load more'}
        </button>
      )}
    </div>
  );
}
