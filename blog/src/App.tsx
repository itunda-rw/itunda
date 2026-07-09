import { Routes, Route, Link } from 'react-router-dom';
import HomePage from './pages/HomePage';
import PostPage from './pages/PostPage';

export default function App() {
  return (
    <div className="page">
      <header className="site-header">
        <Link to="/" className="brand">
          itunda <span className="brand-accent">tech</span>
        </Link>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/:slug" element={<PostPage />} />
        </Routes>
      </main>
      <footer className="site-footer">
        <p>itunda Tech — engineering notes from the team building Rwanda's everyday money app.</p>
      </footer>
    </div>
  );
}
