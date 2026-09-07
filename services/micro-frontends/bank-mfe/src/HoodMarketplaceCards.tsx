import { useEffect, useState } from 'react';
import { EmptyState, ErrorCard } from './EmptyState';
import { showToast } from './Toast';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { addKeywordAlert, createListing, emptyVehicleFieldsState, fetchKeywordAlertQuietHours, fetchKeywordAlerts, fetchMyFavoriteListings, fetchMyHiddenListings, removeKeywordAlert, removeListingFavorite, setKeywordAlertQuietHours, unhideListing, vehicleFieldsToRequest, type FavoriteListing, type HiddenListing, type KeywordAlert, type KeywordAlertQuietHours, type VehicleFieldsState } from './lib/marketplace';
import { uploadFile } from './lib/upload';
import { VehicleListingFieldsForm } from './HoodVehicleFields';
import { useDeferredLoading } from './useDeferredLoading';

export function NewListingCard({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [category, setCategory] = useState('');
  const [meetingPlace, setMeetingPlace] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);
  // Real optional seller location (2026-07-18 backend support, 2026-07-19 this UI) --
  // powers real proximity search and "Directions to this seller"; a listing without it
  // simply doesn't appear in either, an honest opt-in, never assumed.
  const [shareLocation, setShareLocation] = useState(false);
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null); // [lat, lng]
  const [locating, setLocating] = useState(false);

  // Real seller-uploaded photo (2026-08-01) -- see lib/marketplace.ts's own doc
  // comment on Listing.photoUrl. Android already has this (MarketplaceScreen.kt's
  // pickPhoto flow); bank-mfe never had a photo field at all until now.
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [vehicle, setVehicle] = useState<VehicleFieldsState>(emptyVehicleFieldsState);

  const handlePhotoSelected = async (file: File | undefined) => {
    if (!file) return;
    setPhotoUrl(null);
    setUploadingPhoto(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      setPhotoUrl(url);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploadingPhoto(false);
    }
  };

  const handleToggleShareLocation = () => {
    if (shareLocation) {
      setShareLocation(false);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShareLocation(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const [lat, lng] = shareLocation && myLocation ? myLocation : [undefined, undefined];
      await createListing(title, description, Number(price), category, lat, lng, meetingPlace.trim() || undefined, photoUrl ?? undefined, vehicleFieldsToRequest(vehicle));
      setTitle('');
      setDescription('');
      setPrice('');
      setCategory('');
      setMeetingPlace('');
      setShareLocation(false);
      setMyLocation(null);
      setPhotoUrl(null);
      setVehicle(emptyVehicleFieldsState);
      setOpen(false);
      showToast('Listing posted.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + List an item
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>List an item</h3>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t('hood.marketplace.titlePlaceholder')} required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder={t('hood.marketplace.descriptionPlaceholder')} required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" value={price} onChange={(e) => setPrice(e.target.value)} placeholder={t('hood.marketplace.pricePlaceholder')} required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          type="text" value={category} onChange={(e) => setCategory(e.target.value)} placeholder={t('hood.marketplace.categoryPlaceholder')} required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
      </div>
      <input
        type="text" value={meetingPlace} maxLength={120} onChange={(e) => setMeetingPlace(e.target.value)}
        placeholder={t('hood.marketplace.meetingPlacePlaceholder')}
        aria-describedby="meeting-place-help"
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <small id="meeting-place-help" style={{ color: 'var(--itunda-grey-600)' }}>Use a public landmark, not a home address.</small>
      <VehicleListingFieldsForm state={vehicle} onChange={setVehicle} />
      <input
        type="file"
        accept="image/jpeg,image/png,image/webp"
        disabled={uploadingPhoto}
        onChange={(e) => handlePhotoSelected(e.target.files?.[0])}
        style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {uploadingPhoto && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Uploading…</p>}
      {photoUrl && !uploadingPhoto && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>✓ Photo uploaded</p>}
      <button
        type="button"
        className="itunda-btn itunda-btn-secondary"
        disabled={locating}
        onClick={handleToggleShareLocation}
        style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}
      >
        {locating ? 'Finding your real location…' : shareLocation ? '📍 Real location shared -- buyers can see distance & get directions' : '📍 Share my real location (optional)'}
      </button>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting || uploadingPhoto}>
          {submitting ? 'Listing…' : 'List it'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </form>
  );
}

export function ListingWishlistView() {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoriteListing[] | null>(null);
  const showSkeleton = useDeferredLoading(favorites === null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteListings().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleRemove = async (listingId: string) => {
    setRemovingId(listingId);
    try {
      await removeListingFavorite(listingId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (favorites.length === 0) return <EmptyState message="No saved listings yet -- tap ♡ on any listing to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div key={f.listingId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{f.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{f.category} · {f.price.toLocaleString('en-US')} RWF</p>
          </div>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={removingId === f.listingId}
            onClick={() => handleRemove(f.listingId)}
            style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {removingId === f.listingId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}

// Real "Hidden listings" view (2026-09-08 Hood product-completeness pass) -- a user
// could hide a listing on all 3 platforms (real endpoint + real client bindings) but
// never see or undo it anywhere; mirrors ListingWishlistView above field-for-field.
export function HiddenListingsView() {
  const { t } = useI18n();
  const [hidden, setHidden] = useState<HiddenListing[] | null>(null);
  const showSkeleton = useDeferredLoading(hidden === null);
  const [error, setError] = useState<string | null>(null);
  const [unhidingId, setUnhidingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyHiddenListings().then(setHidden).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleUnhide = async (listingId: string) => {
    setUnhidingId(listingId);
    try {
      await unhideListing(listingId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUnhidingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (hidden === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (hidden.length === 0) return <EmptyState message="No hidden listings -- listings you hide will show up here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {hidden.map((h) => (
        <div key={h.listingId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{h.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{h.category} · {h.price.toLocaleString('en-US')} RWF</p>
          </div>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={unhidingId === h.listingId}
            onClick={() => handleUnhide(h.listingId)}
            style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {unhidingId === h.listingId ? 'Unhiding…' : 'Unhide'}
          </button>
        </div>
      ))}
    </div>
  );
}

// Real 당근마켓-style Keyword Alert -- see lib/marketplace.ts's own doc comment. First
// client UI for this feature on any platform (item 114, found via a content-grep
// sweep confirming zero client anywhere despite a mature backend). Real, published
// Karrot 30-keyword-per-user cap enforced server-side; this view surfaces the
// backend's own real KEYWORD_ALERT_CAP_REACHED error rather than guessing the limit.
export function KeywordAlertsView() {
  const { t } = useI18n();
  const [alerts, setAlerts] = useState<KeywordAlert[] | null>(null);
  const showAlertsSkeleton = useDeferredLoading(alerts === null);
  const [keyword, setKeyword] = useState('');
  const [adding, setAdding] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [quietHours, setQuietHoursState] = useState<KeywordAlertQuietHours | null | undefined>(undefined);
  const [quietStart, setQuietStart] = useState('22:00');
  const [quietEnd, setQuietEnd] = useState('08:00');
  const [savingQuietHours, setSavingQuietHours] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchKeywordAlerts().then(setAlerts).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchKeywordAlertQuietHours()
      .then((qh) => {
        setQuietHoursState(qh);
        if (qh) { setQuietStart(qh.startTime); setQuietEnd(qh.endTime); }
      })
      .catch(() => setQuietHoursState(null));
  };
  useEffect(load, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyword.trim()) return;
    setAdding(true);
    setError(null);
    try {
      await addKeywordAlert(keyword.trim());
      setKeyword('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (alertId: string) => {
    setRemovingId(alertId);
    try {
      await removeKeywordAlert(alertId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRemovingId(null);
    }
  };

  const handleSaveQuietHours = async (enabled: boolean) => {
    setSavingQuietHours(true);
    setError(null);
    try {
      const updated = await setKeywordAlertQuietHours(quietStart, quietEnd, enabled);
      setQuietHoursState(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSavingQuietHours(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <form onSubmit={handleAdd} className="itunda-flat-section" style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text" value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder={t('hood.marketplace.keywordAlertPlaceholder')}
          style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={adding || !keyword.trim()}>{adding ? '…' : 'Add'}</button>
      </form>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}

      {alerts === null ? (
        showAlertsSkeleton ? <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : alerts.length === 0 ? (
        <EmptyState message="No keyword alerts yet -- add one to get notified when a matching listing is posted." />
      ) : (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {alerts.map((a) => (
            <div key={a.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{a.keyword}</p>
              <button
                className="itunda-btn itunda-btn-secondary" disabled={removingId === a.id} onClick={() => handleRemove(a.id)}
                style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
              >
                {removingId === a.id ? 'Removing…' : 'Remove'}
              </button>
            </div>
          ))}
        </div>
      )}

      {quietHours !== undefined && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Quiet hours</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            Don't send alert notifications during these hours.
          </p>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
            <input
              type="time" value={quietStart} onChange={(e) => setQuietStart(e.target.value)}
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
            />
            <input
              type="time" value={quietEnd} onChange={(e) => setQuietEnd(e.target.value)}
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
            />
          </div>
          <button
            className={quietHours?.enabled ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'}
            disabled={savingQuietHours}
            onClick={() => handleSaveQuietHours(!quietHours?.enabled)}
            style={{ width: '100%' }}
          >
            {savingQuietHours ? '…' : quietHours?.enabled ? 'Turn off quiet hours' : 'Turn on quiet hours'}
          </button>
        </div>
      )}
    </div>
  );
}

