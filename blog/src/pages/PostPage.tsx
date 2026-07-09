import { useParams, Link, Navigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { getPost } from '../posts';

export default function PostPage() {
  const { slug } = useParams<{ slug: string }>();
  const post = slug ? getPost(slug) : undefined;

  if (!post) return <Navigate to="/" replace />;

  return (
    <div className="container">
      <Link to="/" className="back-link">
        ← All posts
      </Link>
      <article className="post">
        <header className="post-header">
          <h1>{post.title}</h1>
          <div className="post-card-meta">
            <span>{new Date(post.date).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })}</span>
            <span aria-hidden="true">·</span>
            <span>{post.author}</span>
          </div>
          <div className="tag-row">
            {post.tags.map((tag) => (
              <span key={tag} className="tag">
                {tag}
              </span>
            ))}
          </div>
        </header>
        <div className="post-body">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{post.content}</ReactMarkdown>
        </div>
      </article>
    </div>
  );
}
