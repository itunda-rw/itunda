import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import type { PagedQueue } from '../lib/queues';

export function useQueue<T>(fetcher: () => Promise<T[]>) {
  const [items, setItems] = useState<T[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      const result = await fetcher();
      setItems(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load.');
    } finally {
      setRefreshing(false);
    }
  }, [fetcher]);

  useEffect(() => {
    load();
  }, [load]);

  return { items, error, refreshing, reload: load };
}

// For the four real, backend-paginated admin queues (fraud/compliance/partners/support,
// see rw.itunda.core.web.pageMeta) -- a plain `useQueue` would only ever show page 0 (20
// items) with no way to reach the rest and no honest signal more exist. `reload()`
// always resets back to page 0 (correct after a decide/resolve action removes an item
// from the queue); `loadMore()` fetches the next page and appends, matching a real
// review queue's own semantics (already-loaded items don't move once decided elsewhere).
export function usePagedQueue<T>(fetcher: (page: number) => Promise<PagedQueue<T>>) {
  const [items, setItems] = useState<T[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState<number | null>(null);
  const [hasMore, setHasMore] = useState(false);

  const load = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      const result = await fetcher(0);
      setItems(result.items);
      setPage(0);
      setTotalElements(result.totalElements);
      setHasMore(result.hasMore);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load.');
    } finally {
      setRefreshing(false);
    }
  }, [fetcher]);

  const loadMore = useCallback(async () => {
    setLoadingMore(true);
    try {
      const nextPage = page + 1;
      const result = await fetcher(nextPage);
      setItems((prev) => [...(prev ?? []), ...result.items]);
      setPage(nextPage);
      setTotalElements(result.totalElements);
      setHasMore(result.hasMore);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load more.');
    } finally {
      setLoadingMore(false);
    }
  }, [fetcher, page]);

  useEffect(() => {
    load();
  }, [load]);

  return { items, error, refreshing, reload: load, loadMore, loadingMore, totalElements, hasMore };
}
