import { useState } from 'react';
import { IconChevronRight, IconClose } from './icons/ItundaIcons';
import { FullScreenFlow } from './FullScreenFlow';
import { getLegalDocuments, type LegalDocument } from './lib/api';

// Real itunda-branded Terms of Service/Privacy Policy/Credit Data Policy full-text
// viewer (2026-08-30, market-readiness audit) -- Android's own SettingsScreen.kt has
// carried an equivalent "Legal Documents" card since 2026-08-12 (deliberately inert
// there until this same pass -- no document screen existed to link to); bank-mfe
// never had a settings-style home for these at all, since this app's own "Sign out"
// lives directly in the dashboard header rather than a dedicated Settings page. MyView
// (the closest thing this app has to an account-settings hub) is where it lives now.
const LEGAL_DOCUMENT_ROWS: { id: string; label: string }[] = [
  { id: 'credit_data_policy', label: 'Credit data usage policy' },
  { id: 'privacy_policy', label: 'Privacy policy' },
  { id: 'terms_of_service', label: 'Terms & consent' },
];

export function LegalDocumentsCard() {
  const [openId, setOpenId] = useState<string | null>(null);
  const [documents, setDocuments] = useState<LegalDocument[] | null>(null);
  const [error, setError] = useState(false);

  const openDocument = (id: string) => {
    setOpenId(id);
    if (documents === null) {
      getLegalDocuments().then(setDocuments).catch(() => setError(true));
    }
  };

  const activeDocument = documents?.find((d) => d.id === openId) ?? null;

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Legal Documents</h3>
      {LEGAL_DOCUMENT_ROWS.map((row) => (
        <button
          key={row.id}
          type="button"
          onClick={() => openDocument(row.id)}
          style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%',
            padding: '10px 0', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-900)',
            background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left',
          }}
        >
          {row.label}
          <IconChevronRight size={16} color="var(--itunda-grey-400)" />
        </button>
      ))}

      {openId && (
        <FullScreenFlow>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
            <h2 style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, maxWidth: '260px' }}>
              {activeDocument?.title ?? LEGAL_DOCUMENT_ROWS.find((r) => r.id === openId)?.label}
            </h2>
            <button type="button" aria-label="Close" onClick={() => setOpenId(null)} style={{ background: 'none', border: 'none', display: 'flex', padding: '4px' }}>
              <IconClose size={22} color="var(--itunda-grey-500)" />
            </button>
          </div>
          {activeDocument && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
              {activeDocument.bodyMarkdown}
            </p>
          )}
          {!activeDocument && error && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              Couldn't load this document. Check your connection and try again.
            </p>
          )}
          {!activeDocument && !error && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
          )}
        </FullScreenFlow>
      )}
    </div>
  );
}
