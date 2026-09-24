import { useEffect } from 'react';
import { useParams, Link, Navigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { getAuthorProfile, getPost, posts } from '../posts';

const date = (value: string) =>
  new Date(value).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });

const relatedPosts = (slug: string, tags: string[]) =>
  posts
    .filter((candidate) => candidate.slug !== slug)
    .map((candidate) => ({
      candidate,
      overlap: candidate.tags.filter((tag) => tags.includes(tag)).length,
    }))
    .filter(({ overlap }) => overlap > 0)
    .sort((a, b) => b.overlap - a.overlap || new Date(b.candidate.date).getTime() - new Date(a.candidate.date).getTime())
    .slice(0, 3)
    .map(({ candidate }) => candidate);

export default function PostPage() {
  const { slug } = useParams<{ slug: string }>();
  const post = slug ? getPost(slug) : undefined;

  useEffect(() => {
    if (!post) return;
    const profile = getAuthorProfile(post.author);
    document.title = `${post.title} — Itunda Tech`;

    const description = document.querySelector('meta[name="description"]') ?? document.createElement('meta');
    description.setAttribute('name', 'description');
    description.setAttribute('content', post.excerpt);
    document.head.appendChild(description);

    const setMeta = (property: string, content: string) => {
      const meta = document.querySelector(`meta[property="${property}"]`) ?? document.createElement('meta');
      meta.setAttribute('property', property);
      meta.setAttribute('content', content);
      document.head.appendChild(meta);
    };

    setMeta('og:title', post.title);
    setMeta('og:description', post.excerpt);
    setMeta('og:type', 'article');
    setMeta('article:published_time', post.date);
    setMeta('article:author', profile.name);
  }, [post]);

  if (!post) return <Navigate to="/" replace />;

  const profile = getAuthorProfile(post.author);
  const related = relatedPosts(post.slug, post.tags);

  return (
    <div>
      <div className="article-top">
        <div className="container">
          <Link to="/" className="back-link">← Back to Itunda Tech</Link>
          <article className="post">
            <header className="post-header">
              <p className="section-label">{post.tags[0] ?? 'Engineering'}</p>
              {post.image && <img className="article-image" src={post.image} alt={post.imageAlt ?? post.title} />}
              <h1>{post.title}</h1>
              <p className="article-excerpt">{post.excerpt}</p>
              <div className="post-card-meta">
                <span>{date(post.date)}</span><span>·</span><span>{profile.role}</span>
              </div>
              <div className="author-profile">
                <strong>{profile.name}</strong>
                <span>{profile.bio}</span>
              </div>
              <div className="tag-row">{post.tags.map((tag) => <span key={tag} className="tag">{tag}</span>)}</div>
            </header>
            <div className="post-body">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{post.content}</ReactMarkdown>
            </div>
          </article>
        </div>
      </div>

      {related.length > 0 && (
        <section className="related-section">
          <div className="container related-container">
            <p className="section-label">KEEP READING</p>
            <h2>More from Itunda Engineering</h2>
            <div className="related-list">
              {related.map((item) => (
                <Link key={item.slug} to={`/${item.slug}`} className="related-card">
                  <div>
                    <span className="related-meta">{date(item.date)} · {getAuthorProfile(item.author).role}</span>
                    <h3>{item.title}</h3>
                    <p>{item.excerpt}</p>
                  </div>
                  <span className="post-arrow">→</span>
                </Link>
              ))}
            </div>
          </div>
        </section>
      )}

      <div className="article-end">
        <div className="container article-end-inner">
          <Link to="/" className="next-link">Explore more engineering stories <span>→</span></Link>
        </div>
      </div>
    </div>
  );
}
