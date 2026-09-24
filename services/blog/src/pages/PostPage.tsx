import { useParams, Link, Navigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { getPost } from '../posts';

const date = (value: string) =>
  new Date(value).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });

export default function PostPage() {
  const { slug } = useParams<{ slug: string }>();
  const post = slug ? getPost(slug) : undefined;
  if (!post) return <Navigate to="/" replace />;

  return (
    <div>
      <div className="article-top">
        <div className="container">
          <Link to="/" className="back-link">← Back to Itunda Tech</Link>
          <article className="post">
            <header className="post-header">
              <p className="section-label">{post.tags[0] ?? 'Engineering'}</p>
              <h1>{post.title}</h1>
              <p className="article-excerpt">{post.excerpt}</p>
              <div className="post-card-meta">
                <span>{date(post.date)}</span><span>·</span><span>{post.author}</span>
              </div>
              <div className="tag-row">{post.tags.map((tag) => <span key={tag} className="tag">{tag}</span>)}</div>
            </header>
            <div className="post-body">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{post.content}</ReactMarkdown>
            </div>
          </article>
        </div>
      </div>
      <div className="article-end">
        <div className="container article-end-inner">
          <Link to="/" className="next-link">Explore more engineering stories <span>→</span></Link>
        </div>
      </div>
    </div>
  );
}