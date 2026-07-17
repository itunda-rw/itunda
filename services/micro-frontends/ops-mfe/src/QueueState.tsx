import { AlertTriangle, Inbox, RotateCw } from 'lucide-react';
import type { ReactNode } from 'react';

export function QueueSkeleton() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {[0, 1, 2].map((i) => (
        <div key={i} className="toss-card skeleton" style={{ height: '80px' }} />
      ))}
    </div>
  );
}

export function QueueError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      className="toss-card"
      style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px', padding: '32px', textAlign: 'center' }}
    >
      <AlertTriangle size={28} color="#E53935" />
      <p style={{ color: 'var(--toss-grey-700)', fontSize: '14px' }}>{message}</p>
      <button className="toss-btn toss-btn-secondary" onClick={onRetry} style={{ gap: '6px', padding: '8px 16px' }}>
        <RotateCw size={14} /> Retry
      </button>
    </div>
  );
}

export function QueueEmpty({ label }: { label: string }) {
  return (
    <div
      className="toss-card"
      style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', padding: '40px', color: 'var(--toss-grey-500)' }}
    >
      <Inbox size={28} />
      <p style={{ fontSize: '14px' }}>{label}</p>
    </div>
  );
}

export function QueueLoadMore({ onLoadMore, loading }: { onLoadMore: () => void; loading: boolean }) {
  return (
    <button
      className="toss-btn toss-btn-secondary"
      onClick={onLoadMore}
      disabled={loading}
      style={{ alignSelf: 'center', marginTop: '12px', padding: '10px 20px' }}
    >
      {loading ? 'Loading…' : 'Load more'}
    </button>
  );
}

export function QueueHeader({ title, count, onReload, refreshing, children }: {
  title: string;
  count: number | null;
  onReload: () => void;
  refreshing: boolean;
  children?: ReactNode;
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
        <h2 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{title}</h2>
        {count !== null && (
          <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--toss-grey-500)' }}>{count}</span>
        )}
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
        {children}
        <button
          onClick={onReload}
          disabled={refreshing}
          className="toss-btn toss-btn-secondary"
          style={{ padding: '8px 14px', gap: '6px' }}
        >
          <RotateCw size={14} style={refreshing ? { animation: 'spin 0.8s linear infinite' } : undefined} />
          Refresh
        </button>
      </div>
    </div>
  );
}
