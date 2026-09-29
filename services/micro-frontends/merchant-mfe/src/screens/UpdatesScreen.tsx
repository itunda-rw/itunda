import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import type { Merchant } from '../lib/merchant';
import { fetchMerchantUpdates, postMerchantUpdate, type MerchantUpdate, type MerchantUpdateLabel } from '../lib/merchantUpdates';
import { useI18n } from '../i18n/I18nContext';

// Real gap found live (uncalled-endpoint sweep, 2026-08-29): MerchantUpdateController
// has been fully built on the backend since the itunda Maps redesign (2026-08-28,
// Naver Map 소식-tab reference) -- already displayed live to customers on Android/
// iOS's real place-detail News tab -- but no merchant client anywhere had a way to
// actually post one. periodStart/periodEnd deliberately omitted from this first pass,
// see lib/merchant.ts's own doc comment for why.
const LABELS: MerchantUpdateLabel[] = ['NOTICE', 'EVENT', 'PROMO'];

export default function UpdatesScreen({ merchant }: { merchant: Merchant }) {
  const { t } = useI18n();
  const [updates, setUpdates] = useState<MerchantUpdate[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [label, setLabel] = useState<MerchantUpdateLabel>('NOTICE');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [posting, setPosting] = useState(false);
  const [postError, setPostError] = useState<string | null>(null);

  const load = () => {
    setLoadError(null);
    fetchMerchantUpdates(merchant.id)
      .then(setUpdates)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t('updates.loadError')));
  };

  useEffect(load, [merchant.id]);

  const labelText = (l: MerchantUpdateLabel) =>
    l === 'NOTICE' ? t('updates.labelNotice') : l === 'EVENT' ? t('updates.labelEvent') : t('updates.labelPromo');

  const handlePost = async () => {
    if (!title.trim() || !body.trim()) return;
    setPosting(true);
    setPostError(null);
    try {
      await postMerchantUpdate(label, title.trim(), body.trim());
      setTitle('');
      setBody('');
      setLabel('NOTICE');
      load();
    } catch (err) {
      setPostError(err instanceof ApiError ? err.message : t('updates.postError'));
    } finally {
      setPosting(false);
    }
  };

  return (
    <div style={{ maxWidth: '560px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div className="itunda-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{t('updates.title')}</h2>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>{t('updates.subtitle')}</p>

        {postError && <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{postError}</p>}

        <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
          {LABELS.map((l) => (
            <button
              key={l}
              className={`itunda-btn ${label === l ? 'itunda-btn-primary' : 'itunda-btn-secondary'}`}
              style={{ fontSize: '13px', padding: '6px 12px' }}
              onClick={() => setLabel(l)}
            >
              {labelText(l)}
            </button>
          ))}
        </div>
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder={t('updates.titlePlaceholder')}
          maxLength={200}
          style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', marginBottom: '8px' }}
        />
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={t('updates.bodyPlaceholder')}
          maxLength={2000}
          rows={3}
          style={{ width: '100%', padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', marginBottom: '10px', resize: 'vertical' }}
        />
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ width: '100%' }}
          disabled={posting || !title.trim() || !body.trim()}
          onClick={handlePost}
        >
          {posting ? t('updates.posting') : t('updates.postButton')}
        </button>
      </div>

      {loadError && (
        <div className="itunda-card">
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{loadError}</p>
        </div>
      )}

      {updates === null && !loadError && <div className="itunda-card skeleton" style={{ height: '80px' }} />}

      {updates !== null && updates.length === 0 && (
        <div className="itunda-card">
          <EmptyState message={t('updates.empty')} />
        </div>
      )}

      {updates?.map((update) => (
        <div key={update.id} className="itunda-card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
            <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--itunda-indigo)', textTransform: 'uppercase' }}>{labelText(update.label)}</span>
            <span style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{new Date(update.createdAt).toLocaleDateString()}</span>
          </div>
          <p style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>{update.title}</p>
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)', marginBottom: '8px', whiteSpace: 'pre-wrap' }}>{update.body}</p>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('updates.likes', { count: update.likeCount })}</p>
        </div>
      ))}
    </div>
  );
}
