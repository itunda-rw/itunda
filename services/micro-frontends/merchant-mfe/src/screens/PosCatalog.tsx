import { Fragment, useEffect, useRef, useState } from 'react';
import { ChevronDown, ChevronUp, Trash2 } from 'lucide-react';
import { EmptyState } from '../components/EmptyState';
import { ApiError } from '../lib/api';
import { uploadFile } from '../lib/upload';
import {
  addProduct, getProductCatalog, removeProduct, setSoldOut, setSurplusDeal, updateProduct, updateProductStock,
  type MerchantProduct,
} from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';
import { ProductAnalyticsPanel, ProductOptionsPanel, PriceTiersPanel, TimeDealPanel } from './PosCatalogPanels';

export function CatalogView() {
  const { t } = useI18n();
  const [products, setProducts] = useState<MerchantProduct[] | null>(null);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [originalPrice, setOriginalPrice] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [description, setDescription] = useState('');
  const [stockQuantity, setStockQuantity] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real photo upload (2026-08-13) -- see lib/upload.ts's own doc comment. Replaces
  // a "paste a URL" text field with a real device picker.
  const [uploadingImage, setUploadingImage] = useState(false);
  const imageInputRef = useRef<HTMLInputElement | null>(null);

  const handleImageSelected = async (file: File | undefined) => {
    if (!file) return;
    setUploadingImage(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      setImageUrl(url);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.uploadPhotoError'));
    } finally {
      setUploadingImage(false);
      if (imageInputRef.current) imageInputRef.current.value = '';
    }
  };
  // Real menu-item option groups management (2026-07-21) -- only one product's panel
  // expanded at a time, same "inline-card-replaces-trigger" convention bank-mfe's own
  // buyer-side option UI already established.
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  // Real bulk/wholesale price tiers (item 149, backend-only until now) -- same
  // "one panel expanded at a time" convention as the Options panel above, its own
  // separate toggle since a product can have both option groups and price tiers.
  const [expandedPricingProductId, setExpandedPricingProductId] = useState<string | null>(null);
  // Real Coupang 타임특가 (Time Deal, item 226) -- same "one panel expanded at a time"
  // convention as Options/Pricing above, its own separate toggle.
  const [expandedTimeDealProductId, setExpandedTimeDealProductId] = useState<string | null>(null);
  // Real Coupang WING 상품분석 (product analytics) -- found via
  // scripts/uncalled-endpoint-sweep.py: fully built with zero client anywhere. Same
  // "one panel expanded at a time" convention as Options/Pricing/Time Deal above.
  const [expandedAnalyticsProductId, setExpandedAnalyticsProductId] = useState<string | null>(null);
  const lowStock = products?.filter((product) => product.stockQuantity !== null && product.stockQuantity <= 5) ?? [];

  const load = () => {
    getProductCatalog()
      .then(setProducts)
      .catch(() => setProducts([]));
  };

  useEffect(load, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const currentPrice = Number(price);
    const previousPrice = originalPrice.trim() ? Number(originalPrice) : undefined;
    const stock = stockQuantity.trim() ? Number(stockQuantity) : undefined;
    if (!Number.isFinite(currentPrice) || currentPrice <= 0) {
      setError(t('pos.priceValidationError'));
      return;
    }
    if (previousPrice !== undefined && (!Number.isFinite(previousPrice) || previousPrice <= currentPrice)) {
      setError(t('pos.originalPriceValidationError'));
      return;
    }
    if (stock !== undefined && (!Number.isInteger(stock) || stock < 0)) {
      setError(t('pos.stockValidationError'));
      return;
    }
    setSubmitting(true);
    try {
      await addProduct(name, currentPrice, imageUrl.trim() || undefined, previousPrice, description.trim() || undefined, stock);
      setName('');
      setPrice('');
      setOriginalPrice('');
      setImageUrl('');
      setDescription('');
      setStockQuantity('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.addProductError'));
    } finally {
      setSubmitting(false);
    }
  };

  const adjustStock = async (product: MerchantProduct) => {
    const value = window.prompt(
      t('pos.adjustStockPrompt'),
      product.stockQuantity === null ? '' : String(product.stockQuantity),
    );
    if (value === null) return;
    const trimmed = value.trim();
    const stock = trimmed === '' ? null : Number(trimmed);
    if (stock !== null && (!Number.isInteger(stock) || stock < 0)) {
      setError(t('pos.stockValidationError'));
      return;
    }
    try {
      await updateProductStock(product.id, stock);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.updateStockError'));
    }
  };

  // Real "edit product" parity gap, found 2026-09-04 via a defined-but-uncalled-method
  // sweep: updateProduct was fully built on the backend and declared here, but never
  // called anywhere on any platform -- a merchant could adjust stock (adjustStock
  // above) or delete a product entirely, but never fix a typo in its name or adjust
  // its price without deleting and recreating it (losing its reviews/analytics/stock
  // history in the process). Reuses the same lightweight window.prompt convention
  // adjustStock above already established, rather than building a full edit form for
  // a two-field change.
  const editProduct = async (product: MerchantProduct) => {
    const name = window.prompt(t('pos.editProductNamePrompt'), product.name);
    if (name === null) return;
    const priceInput = window.prompt(t('pos.editProductPricePrompt'), String(product.price));
    if (priceInput === null) return;
    const price = Number(priceInput);
    if (!name.trim() || !Number.isFinite(price) || price <= 0) {
      setError(t('pos.priceValidationError'));
      return;
    }
    try {
      await updateProduct(product.id, product, name.trim(), price);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.editProductError'));
    }
  };

  // Real 마감할인 (closing/surplus discount) toggle -- see lib/merchant.ts's own doc
  // comment for the full sourced account.
  const handleSetSurplusDeal = async (product: MerchantProduct) => {
    if (product.isSurplusDeal) {
      try {
        await setSurplusDeal(product.id, null, null);
        load();
      } catch (err) {
        setError(err instanceof ApiError ? err.message : t('pos.surplusDealError'));
      }
      return;
    }
    const hoursValue = window.prompt(t('pos.surplusDealHoursPrompt'), '2');
    if (hoursValue === null) return;
    const hours = Number(hoursValue.trim());
    if (!Number.isFinite(hours) || hours <= 0) {
      setError(t('pos.surplusDealHoursError'));
      return;
    }
    let stock = product.stockQuantity;
    if (stock === null || stock <= 0) {
      const stockValue = window.prompt(t('pos.surplusDealStockPrompt'), '5');
      if (stockValue === null) return;
      stock = Number(stockValue.trim());
      if (!Number.isInteger(stock) || stock <= 0) {
        setError(t('pos.stockValidationError'));
        return;
      }
    }
    try {
      const expiresAt = new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();
      await setSurplusDeal(product.id, expiresAt, stock);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.surplusDealError'));
    }
  };

  // Real Baemin CEO app/DoorDash-style "86" (temporarily sold out) toggle -- see
  // lib/merchant.ts's own doc comment. A plain flip, no prompt needed -- unlike the
  // surplus-deal toggle above, there's no expiry/quantity to collect.
  const handleSetSoldOut = async (product: MerchantProduct) => {
    try {
      await setSoldOut(product.id, !product.soldOut);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.soldOutError'));
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div className="itunda-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '16px' }}>{t('pos.addProductTitle')}</h2>
        <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 2, minWidth: '160px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('pos.nameLabel')}</span>
            <input
              type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Latte" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '120px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('pos.priceLabel')}</span>
            <input
              type="number" min="1" value={price} onChange={(e) => setPrice(e.target.value)} placeholder="2500" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '150px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('pos.stockLabel')}</span>
            <input
              type="number" min="0" step="1" value={stockQuantity} onChange={(e) => setStockQuantity(e.target.value)} placeholder={t('pos.stockPlaceholderUnlimited')}
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '120px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('pos.originalPriceLabel')}</span>
            <input
              type="number" min="1" value={originalPrice} onChange={(e) => setOriginalPrice(e.target.value)} placeholder="3000"
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 2, minWidth: '220px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('pos.photoLabel')}</span>
            <input
              ref={imageInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              style={{ display: 'none' }}
              onChange={(e) => handleImageSelected(e.target.files?.[0])}
            />
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              {imageUrl && <img src={imageUrl} alt="" style={{ width: '44px', height: '44px', borderRadius: '8px', objectFit: 'cover', flexShrink: 0 }} />}
              <button
                type="button"
                onClick={() => imageInputRef.current?.click()}
                disabled={uploadingImage}
                className="itunda-btn itunda-btn-secondary"
                style={{ fontSize: '13px', padding: '10px 14px' }}
              >
                {uploadingImage ? t('pos.uploading') : imageUrl ? t('pos.changePhoto') : t('pos.addPhotoFromDevice')}
              </button>
            </div>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flexBasis: '100%' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('pos.descriptionLabel')}</span>
            <textarea
              value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} rows={2} placeholder={t('pos.descriptionPlaceholder')}
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px', resize: 'vertical' }}
            />
          </label>
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ height: '46px' }}>
            {submitting ? t('pos.adding') : t('pos.addButton')}
          </button>
        </form>
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: '12px 0 0' }} role="alert">
            {error}
          </p>
        )}
      </div>

      {lowStock.length > 0 && (
        <div className="itunda-card" role="status" style={{ borderLeft: '4px solid #F59E0B', background: '#FFFBEB' }}>
          <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
            {t(lowStock.length === 1 ? 'pos.lowStockWarningSingular' : 'pos.lowStockWarningPlural', { count: lowStock.length })}
          </p>
          <p style={{ marginTop: '4px', fontSize: '13px', color: 'var(--itunda-grey-700)' }}>
            {lowStock.map((product) => `${product.name} (${product.stockQuantity === 0 ? t('pos.lowStockOutOfStock') : t('pos.lowStockLeft', { count: product.stockQuantity ?? 0 })})`).join(', ')}
          </p>
        </div>
      )}

      {products === null ? (
        <div className="itunda-card">{t('pos.loading')}</div>
      ) : products.length === 0 ? (
        <div className="itunda-card">
          {/* Real copy fix (2026-08-15, live-verified against the actual French build):
              this list is CatalogView's own -- the item 244-era comment that used to be
              here ("points to the real Catalog tab... this REGISTER-mode checkout view")
              was simply wrong about which view this code belongs to, and the message it
              justified told a merchant already looking at the Catalog tab to "switch to
              Catalog above." Now points at the real add-product form immediately above
              this list instead. */}
          <EmptyState message={t('pos.catalogEmptyOwnForm')} />
        </div>
      ) : (
        <div className="itunda-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ backgroundColor: 'var(--itunda-grey-100)', textAlign: 'left' }}>
                {[t('pos.columnProduct'), t('pos.columnPrice'), ''].map((h, i) => (
                  <th key={i === 2 ? 'actions' : h} style={{ padding: '10px 20px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {products.map((product) => {
                const isExpanded = expandedProductId === product.id;
                const isPricingExpanded = expandedPricingProductId === product.id;
                const isTimeDealExpanded = expandedTimeDealProductId === product.id;
                const isAnalyticsExpanded = expandedAnalyticsProductId === product.id;
                return (
                  <Fragment key={product.id}>
                    <tr style={{ borderTop: '1px solid var(--itunda-grey-200)' }}>
                      <td style={{ padding: '10px 20px', fontWeight: 600 }}>
                        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                          {product.imageUrl && <img src={product.imageUrl} alt="" width={36} height={36} style={{ borderRadius: '6px', objectFit: 'cover' }} />}
                          <div><div>{product.name}</div>{product.description && <div style={{ fontWeight: 400, fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>{product.description}</div>}</div>
                        </div>
                      </td>
                      <td style={{ padding: '10px 20px' }}>
                        <div>{product.price.toLocaleString('en-US')} RWF</div>
                        {product.originalPrice && product.discountPercent && <div style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}><s>{product.originalPrice.toLocaleString('en-US')} RWF</s> · {t('pos.discountOff', { percent: product.discountPercent })}</div>}
                        <div style={{ fontSize: '12px', color: product.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)', marginTop: '2px' }}>
                          {product.stockQuantity === null ? t('pos.unlimitedStock') : product.stockQuantity === 0 ? t('pos.outOfStock') : t('pos.inStock', { count: product.stockQuantity })}
                        </div>
                      </td>
                      <td style={{ padding: '10px 20px', textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '14px' }}>
                          <button
                            onClick={() => setExpandedProductId(isExpanded ? null : product.id)}
                            style={{ color: 'var(--itunda-indigo)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.optionsToggle')} {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => setExpandedPricingProductId(isPricingExpanded ? null : product.id)}
                            style={{ color: 'var(--itunda-indigo)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.pricingToggle')} {isPricingExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => setExpandedTimeDealProductId(isTimeDealExpanded ? null : product.id)}
                            style={{ color: 'var(--itunda-indigo)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.timeDealToggle')} {isTimeDealExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => setExpandedAnalyticsProductId(isAnalyticsExpanded ? null : product.id)}
                            style={{ color: 'var(--itunda-indigo)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.analyticsToggle')} {isAnalyticsExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => editProduct(product)}
                            style={{ color: 'var(--itunda-indigo)', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.editButton')}
                          </button>
                          <button
                            onClick={() => adjustStock(product)}
                            style={{ color: 'var(--itunda-indigo)', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.adjustStockButton')}
                          </button>
                          <button
                            onClick={() => handleSetSurplusDeal(product)}
                            style={{ color: product.isSurplusDeal ? 'var(--itunda-red)' : 'var(--itunda-indigo)', fontSize: '13px', fontWeight: 600 }}
                          >
                            {product.isSurplusDeal ? t('pos.surplusDealClearButton') : t('pos.surplusDealSetButton')}
                          </button>
                          <button
                            onClick={() => handleSetSoldOut(product)}
                            style={{ color: product.soldOut ? 'var(--itunda-red)' : 'var(--itunda-indigo)', fontSize: '13px', fontWeight: 600 }}
                          >
                            {product.soldOut ? t('pos.soldOutClearButton') : t('pos.soldOutSetButton')}
                          </button>
                          <button
                            onClick={() => removeProduct(product.id).then(load)}
                            style={{ color: 'var(--itunda-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                          >
                            <Trash2 size={14} /> {t('pos.removeButton')}
                          </button>
                        </div>
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr style={{ borderTop: '1px solid var(--itunda-grey-200)', backgroundColor: 'var(--itunda-grey-100)' }}>
                        <td colSpan={3} style={{ padding: '16px 20px' }}>
                          <ProductOptionsPanel productId={product.id} />
                        </td>
                      </tr>
                    )}
                    {isPricingExpanded && (
                      <tr style={{ borderTop: '1px solid var(--itunda-grey-200)', backgroundColor: 'var(--itunda-grey-100)' }}>
                        <td colSpan={3} style={{ padding: '16px 20px' }}>
                          <PriceTiersPanel productId={product.id} regularPrice={product.price} />
                        </td>
                      </tr>
                    )}
                    {isTimeDealExpanded && (
                      <tr style={{ borderTop: '1px solid var(--itunda-grey-200)', backgroundColor: 'var(--itunda-grey-100)' }}>
                        <td colSpan={3} style={{ padding: '16px 20px' }}>
                          <TimeDealPanel productId={product.id} regularPrice={product.price} />
                        </td>
                      </tr>
                    )}
                    {isAnalyticsExpanded && (
                      <tr style={{ borderTop: '1px solid var(--itunda-grey-200)', backgroundColor: 'var(--itunda-grey-100)' }}>
                        <td colSpan={3} style={{ padding: '16px 20px' }}>
                          <ProductAnalyticsPanel productId={product.id} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
