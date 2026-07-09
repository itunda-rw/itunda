import { Link } from 'react-router-dom';
import { posts } from '../posts';

export default function HomePage() {
  return (
    <div className="container">
      <section className="hero">
        <h1>Engineering notes from itunda</h1>
        <p>
          Real incidents, real fixes, and the reasoning behind them — written by the team building itunda's ledger,
          security, and mobile apps.
        </p>
      </section>

      <ul className="post-list">
        {posts.map((post) => (
          <li key={post.slug} className="post-card">
            <Link to={`/${post.slug}`}>
              <div className="post-card-meta">
                <span>{new Date(post.date).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}</span>
                <span aria-hidden="true">·</span>
                <span>{post.author}</span>
              </div>
              <h2>{post.title}</h2>
              <p>{post.excerpt}</p>
              <div className="tag-row">
                {post.tags.map((tag) => (
                  <span key={tag} className="tag">
                    {tag}
                  </span>
                ))}
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
