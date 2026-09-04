import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { partnerIdentityReferenceMarkdown } from './partnerIdentityReference';

export default function PartnerIdentityDocsPage() {
  return (
    <>
      <div className="hero">
        <h1>Verify identity with itunda</h1>
        <p>
          Confirm a real user's identity — name, phone number, itunda KYC status — signed
          and verifiable, without building or storing your own identity documents. No
          money moves in this API.
        </p>
      </div>
      <p className="sandbox-notice">
        Every API key issued today is a sandbox key — itunda has no production/live tier
        yet.
      </p>
      <div className="doc-body">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{partnerIdentityReferenceMarkdown}</ReactMarkdown>
      </div>
    </>
  );
}
