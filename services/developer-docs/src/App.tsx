import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { apiReferenceMarkdown } from './apiReference';

export default function App() {
  return (
    <div className="page">
      <header className="site-header">
        <span className="brand">
          itunda <span className="brand-accent">developers</span>
        </span>
      </header>
      <main>
        <div className="container">
          <div className="hero">
            <h1>Pay with itunda</h1>
            <p>
              Accept real itunda payments from your own backend — API-key auth, a hosted
              checkout page, webhooks, and real cancel/refund. No itunda account login on
              your site at any point.
            </p>
          </div>
          <p className="sandbox-notice">
            Every API key issued today is a sandbox key — itunda has no production/live tier
            yet. Nothing described here moves real regulated money.
          </p>
          <div className="doc-body">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{apiReferenceMarkdown}</ReactMarkdown>
          </div>
        </div>
      </main>
      <footer className="site-footer">
        <p>itunda Developers — building on itunda's own payments infrastructure.</p>
      </footer>
    </div>
  );
}
