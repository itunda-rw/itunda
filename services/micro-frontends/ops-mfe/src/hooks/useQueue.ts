import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../lib/api';

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
