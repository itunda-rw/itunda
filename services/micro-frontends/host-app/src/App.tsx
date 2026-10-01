import { lazy, Suspense } from 'react';

const ConsumerApp = lazy(() => import('bank_mfe/App'));

export default function App() {
  return (
    <Suspense
      fallback={
        <div
          style={{
            minHeight: '100svh',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          role="status"
          aria-live="polite"
        >
          Loading Itunda…
        </div>
      }
    >
      <ConsumerApp />
    </Suspense>
  );
}
