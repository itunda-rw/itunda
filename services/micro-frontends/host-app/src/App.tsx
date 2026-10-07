import { Component, lazy, Suspense, type ErrorInfo, type ReactNode } from 'react';

const ConsumerApp = lazy(() => import('bank_mfe/App'));
const ConsumerI18nProvider = lazy(() => import('bank_mfe/I18nProvider'));

class ConsumerAppBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('[Itunda] consumer shell failed to boot', error, info);
  }

  retry = () => {
    window.location.reload();
  };

  render() {
    if (this.state.error) {
      return (
        <main
          role="alert"
          style={{
            minHeight: '100svh',
            display: 'grid',
            placeItems: 'center',
            padding: 24,
            textAlign: 'center',
            color: 'var(--itunda-text-primary)',
            background: 'var(--itunda-surface-default)',
          }}
        >
          <div style={{ maxWidth: 360 }}>
            <h1 style={{ margin: '0 0 12px', fontSize: 24 }}>Itunda could not load</h1>
            <p style={{ margin: '0 0 20px', color: 'var(--itunda-text-secondary)', lineHeight: 1.5 }}>
              The app shell started, but the customer experience did not finish loading.
            </p>
            <button type="button" onClick={this.retry} className="itunda-btn itunda-btn-primary">
              Try again
            </button>
          </div>
        </main>
      );
    }

    return this.props.children;
  }
}

export default function App() {
  return (
    <ConsumerAppBoundary>
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
        <ConsumerI18nProvider>
          <ConsumerApp />
        </ConsumerI18nProvider>
      </Suspense>
    </ConsumerAppBoundary>
  );
}
