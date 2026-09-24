import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getAuthorProfile, posts } from '../posts';

const date = (value: string) =>
  new Date(value).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });

const disciplines = ['All', 'Engineering', 'Android', 'iOS', 'Backend', 'Security', 'Infrastructure', 'AI', 'Design'];

export default function HomePage() {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('All');

  const categories = useMemo(
    () => ['All', ...Array.from(new Set([...disciplines.slice(1), ...posts.flatMap((post) => post.tags)]))],
    [],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return posts.filter((post) => {
      const matchesCategory = category === 'All' || post.tags.some((tag) => tag.toLowerCase() === category.toLowerCase());
      const haystack = [post.title, post.excerpt, post.author, ...post.tags].join(' ').toLowerCase();
      return matchesCategory && (!q || haystack.includes(q));
    });
  }, [category, query]);

  const featured = posts[0];
  const popular = posts.slice(0, 3);
  const series = [
    { name: 'Building Reliable Money Movement', description: 'Lessons from payments, idempotency, security, and failure recovery.', posts: posts.filter((post) => ['ledger', 'reliability', 'payments', 'security'].some((tag) => post.tags.includes(tag))).slice(0, 4) },
    { name: 'Systems That Fail in Production', description: 'The small assumptions that become real incidents.', posts: posts.filter((post) => ['infrastructure', 'reliability', 'operations'].some((tag) => post.tags.includes(tag))).slice(0, 4) },
  ];

  return (
    <div>
      <section className="tech-hero">
        <div className="wide-container">
          <p className="eyebrow">ITUNDA ENGINEERING</p>
          <h1>We build for<br /><span>everyday life.</span></h1>
          <p className="hero-copy">
            The engineering decisions behind Itunda — from payments and identity to mobile, infrastructure,
            security, and the systems that connect them.
          </p>
        </div>
      </section>

      <div className="wide-container">
        <nav className="category-nav" aria-label="Article categories">
          {categories.map((item) => (
            <button key={item} className={category === item ? 'category active' : 'category'} onClick={() => setCategory(item)}>
              {item}
            </button>
          ))}
        </nav>

        <div className="search-row">
          <label className="search-box">
            <span aria-hidden="true">⌕</span>
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search Itunda Tech" aria-label="Search articles" />
          </label>
          <span className="result-count">{filtered.length} articles</span>
        </div>

        {!query && category === 'All' && (
          <>
            <section className="featured">
              <div className="featured-copy">
                <p className="section-label">FEATURED</p>
                <Link to={`/${featured.slug}`}>
                  {featured.image && <img className="featured-image" src={featured.image} alt={featured.imageAlt ?? featured.title} loading="eager" />}
                  <h2>{featured.title}</h2>
                  <p>{featured.excerpt}</p>
                  <span className="read-link">Read article →</span>
                </Link>
              </div>
              <div className="featured-meta"><span>{date(featured.date)}</span><span>{featured.author}</span></div>
            </section>

            <section className="popular-section">
              <div className="section-heading">
                <p className="section-label">POPULAR</p>
                <h2>Worth reading</h2>
              </div>
              <div className="popular-grid">
                {popular.map((post, index) => (
                  <Link className="popular-card" key={post.slug} to={`/${post.slug}`}>
                    <span className="popular-number">0{index + 1}</span>
                    {post.image && <img className="popular-image" src={post.image} alt={post.imageAlt ?? post.title} loading="lazy" />}
                    <h3>{post.title}</h3>
                    <p>{getAuthorProfile(post.author).role}</p>
                  </Link>
                ))}
              </div>
            </section>

            <section className="series-section">
              <div className="section-heading">
                <p className="section-label">ARTICLE SERIES</p>
                <h2>Follow a thread</h2>
              </div>
              <div className="series-list">
                {series.filter((item) => item.posts.length).map((item) => (
                  <div className="series-card" key={item.name}>
                    <div className="series-card-head"><div><h3>{item.name}</h3><p>{item.description}</p></div><span>{item.posts.length} stories</span></div>
                    <div className="series-posts">
                      {item.posts.map((post) => <Link key={post.slug} to={'/' + post.slug}><span>{post.title}</span><span>→</span></Link>)}
                    </div>
                  </div>
                ))}
              </div>
            </section>
            <section className="discipline-section">
              <div>
                <p className="section-label">EXPLORE</p>
                <h2>Engineering at Itunda</h2>
                <p>Explore the disciplines behind the platform.</p>
              </div>
              <div className="discipline-grid">
                {disciplines.slice(1).map((item) => (
                  <button key={item} onClick={() => setCategory(item)}>{item}<span>→</span></button>
                ))}
              </div>
            </section>
          </>
        )}

        <section className="latest">
          <div className="section-heading">
            <p className="section-label">LATEST</p>
            <h2>{query || category !== 'All' ? 'Search results' : 'What we’re building'}</h2>
          </div>
          <div className="post-list">
            {filtered.map((post) => (
              <article key={post.slug} className="post-card">
                <Link to={`/${post.slug}`}>
                  <div className="post-card-main">
                    {post.image && <img className="post-card-image" src={post.image} alt={post.imageAlt ?? post.title} />}
                    <div className="post-card-meta"><span>{date(post.date)}</span><span>·</span><span>{getAuthorProfile(post.author).role}</span></div>
                    <h3>{post.title}</h3>
                    <p>{post.excerpt}</p>
                    <div className="tag-row">{post.tags.map((tag) => <span key={tag} className="tag">{tag}</span>)}</div>
                  </div>
                  <span className="post-arrow" aria-hidden="true">→</span>
                </Link>
              </article>
            ))}
            {filtered.length === 0 && <div className="empty-state">No articles match that search.</div>}
          </div>
        </section>
      </div>
    </div>
  );
}
