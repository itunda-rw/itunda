import { Link, Routes, Route } from 'react-router-dom';
import HomePage from './pages/HomePage';
import PostPage from './pages/PostPage';

export default function App() {
  return (
    <div className="page">
      <header className="site-header">
        <div className="header-inner">
          <Link to="/" className="brand" aria-label="Itunda Tech home"><img src="/brand/itunda-icon.svg" alt="" className="brand-mark" /><span>itunda <span className="brand-accent">tech</span></span></Link>
          <nav className="site-nav" aria-label="Primary">
            <Link to="/" className="nav-active" aria-current="page">Articles</Link>
            <a href="/itunda/" aria-label="Open Itunda">Itunda</a>
            <a href="/itunda-business/" aria-label="Open Itunda Business">Business</a>
            <a href="/itunda-developers/" aria-label="Open Itunda Developers">Developers</a>
          </nav>
          <a className="header-cta" href="https://github.com/itunda-rw/itunda">GitHub</a>
        </div>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/:slug" element={<PostPage />} />
        </Routes>
      </main>
      <footer className="site-footer">
        <div className="footer-inner">
          <div>
            <strong className="footer-brand"><img src="/brand/itunda-icon.svg" alt="" className="footer-mark" />itunda tech</strong>
            <p>Engineering stories from the team building Rwanda's everyday digital infrastructure.</p>
          </div>
          <div className="footer-links">
            <a href="/itunda/">Itunda</a>
            <a href="/itunda-business/">Business</a>
            <a href="/itunda-developers/">Developers</a>
            <a href="https://github.com/itunda-rw/itunda">GitHub</a>
          </div>
        </div>
      </footer>
    </div>
  );
}