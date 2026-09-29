import { useState } from 'react';
import { ApiError } from '../lib/api';
import { photoUrlList, setMerchantPhotoUrls, type MerchantWithPhotos } from '../lib/merchantPhotos';
import { useI18n } from '../i18n/I18nContext';

// Real gap found live (uncalled-endpoint sweep, 2026-08-29) -- see
// lib/merchantPhotos.ts's own doc comment. Deliberately its own screen rather than
// added to SettingsScreen.tsx's existing StoreSettingsCard (the single-cover-photo
// field's real home) -- that file was already at its file-size-lint baseline.
export default function PhotoGalleryScreen({ merchant, onUpdated }: { merchant: MerchantWithPhotos; onUpdated: (merchant: MerchantWithPhotos) => void }) {
  const { t } = useI18n();
  const initialUrls = photoUrlList(merchant.photoUrls);
  const [urls, setUrls] = useState<string[]>(initialUrls.length ? initialUrls : ['']);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  const updateUrl = (index: number, value: string) => {
    setUrls((prev) => prev.map((u, i) => (i === index ? value : u)));
    setSaved(false);
  };

  const removeUrl = (index: number) => {
    setUrls((prev) => prev.filter((_, i) => i !== index));
    setSaved(false);
  };

  const addUrl = () => setUrls((prev) => [...prev, '']);

  const handleSave = async () => {
    setError(null);
    setSaving(true);
    try {
      const cleaned = urls.map((u) => u.trim()).filter(Boolean);
      onUpdated(await setMerchantPhotoUrls(cleaned));
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('photos.saveError'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ maxWidth: '560px' }}>
      <div className="itunda-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{t('photos.title')}</h2>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>{t('photos.subtitle')}</p>

        {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
        {saved && <p style={{ fontSize: '13px', color: 'var(--itunda-green)', marginBottom: '8px' }}>{t('photos.saved')}</p>}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          {urls.map((url, index) => (
            <div key={index} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="url"
                value={url}
                onChange={(e) => updateUrl(index, e.target.value)}
                placeholder="https://example.com/photo.jpg"
                style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }}
              />
              <button
                type="button"
                onClick={() => removeUrl(index)}
                aria-label={t('photos.removePhoto')}
                style={{ color: 'var(--itunda-red)', fontSize: '13px', padding: '6px' }}
              >
                {t('photos.removePhoto')}
              </button>
            </div>
          ))}
        </div>

        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            type="button"
            className="itunda-btn itunda-btn-secondary"
            onClick={addUrl}
            disabled={urls.length >= 20}
            style={{ fontSize: '13px', padding: '8px 14px' }}
          >
            {t('photos.addPhoto')}
          </button>
          <button
            type="button"
            className="itunda-btn itunda-btn-primary"
            onClick={handleSave}
            disabled={saving}
            style={{ flex: 1 }}
          >
            {saving ? t('photos.saving') : t('photos.save')}
          </button>
        </div>
      </div>
    </div>
  );
}
