import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { certificateReferenceMarkdown } from './certificateReference';

export default function CertificateDocsPage() {
  return (
    <>
      <div className="hero">
        <h1>Verify a signature</h1>
        <p>
          Confirm a document or message was really signed by an itunda user's real
          certificate — no account or API key needed. Not a legally-binding electronic
          signature service; see the scope note below before integrating.
        </p>
      </div>
      <div className="doc-body">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{certificateReferenceMarkdown}</ReactMarkdown>
      </div>
    </>
  );
}
