import { useEffect, useState } from 'react';
import { EmptyState, ErrorCard } from './EmptyState';
import { showToast } from './Toast';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { createPropertyListing, fetchMyFavoritePropertyListings, removePropertyListingFavorite, type FavoritePropertyListing, type PropertyListingType, type PropertyType } from './lib/realestate';
import { useDeferredLoading } from './useDeferredLoading';

export function NewPropertyListingCard({ propertyTypes, onCreated }: { propertyTypes: PropertyType[]; onCreated: () => void }) {
  const { t } = useI18n();
  const [listingType, setListingType] = useState<PropertyListingType>('RENT');
  const [propertyType, setPropertyType] = useState(propertyTypes[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [bedrooms, setBedrooms] = useState('');
  const [sizeSqm, setSizeSqm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createPropertyListing(
        listingType, propertyType, title, description, Number(price),
        bedrooms ? Number(bedrooms) : undefined, sizeSqm ? Number(sizeSqm) : undefined,
      );
      setTitle('');
      setDescription('');
      setPrice('');
      setBedrooms('');
      setSizeSqm('');
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
        + List a property
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>List a property</h3>
      <div style={{ display: 'flex', gap: '10px' }}>
        <select
          value={listingType} onChange={(e) => setListingType(e.target.value as PropertyListingType)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        >
          <option value="RENT">For rent</option>
          <option value="SALE">For sale</option>
        </select>
        <select
          value={propertyType} onChange={(e) => setPropertyType(e.target.value)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        >
          {propertyTypes.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
        </select>
      </div>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t('hood.property.titlePlaceholder')} required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder={t('hood.property.descriptionPlaceholder')} required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" value={price} onChange={(e) => setPrice(e.target.value)}
          placeholder={listingType === 'RENT' ? 'Rent per month (RWF)' : 'Price (RWF)'} required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          type="number" value={bedrooms} onChange={(e) => setBedrooms(e.target.value)} placeholder={t('hood.property.bedroomsPlaceholder')} min="0"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          type="number" value={sizeSqm} onChange={(e) => setSizeSqm(e.target.value)} placeholder={t('hood.property.sizePlaceholder')} min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
      </div>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Listing…' : 'List it'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </form>
  );
}

// Real 당근부동산 property-listing wishlist view (2026-07-22) -- mirrors
// ListingWishlistView exactly, closing a docs/DESIGN_REFERENCES.md-named gap.
export function PropertyListingWishlistView() {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoritePropertyListing[] | null>(null);
  const showSkeleton = useDeferredLoading(favorites === null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoritePropertyListings().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleRemove = async (propertyListingId: string) => {
    setRemovingId(propertyListingId);
    try {
      await removePropertyListingFavorite(propertyListingId);
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
  if (favorites.length === 0) return <EmptyState message="No saved properties yet -- tap ♡ on any listing to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div key={f.propertyListingId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{f.title}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{f.propertyType} · {f.price.toLocaleString('en-US')} RWF</p>
          </div>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={removingId === f.propertyListingId}
            onClick={() => handleRemove(f.propertyListingId)}
            style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {removingId === f.propertyListingId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}

