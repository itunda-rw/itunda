import { Fragment, useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { ChevronDown, ChevronUp, CreditCard, Minus, Plus, RefreshCw, Store, Trash2 } from 'lucide-react';
import { EmptyState } from '../components/EmptyState';
import { ApiError } from '../lib/api';
import { uploadFile } from '../lib/upload';
import { DeviceStepUpPrompt } from '../components/DeviceStepUpPrompt';
import {
  addOptionGroup,
  addProduct,
  setSurplusDeal,
  updateProductStock,
  chargeCard,
  createTimeDeal,
  endTimeDeal,
  fetchMyTimeDeals,
  fetchPriceTiers,
  generateQr,
  getOptionGroups,
  getProductCatalog,
  paymentIntentQrPayload,
  removeOptionGroup,
  removeProduct,
  setPriceTiers,
  type CardChargeResult,
  type MenuOptionGroup,
  type MerchantProduct,
  type PaymentIntent,
  type TimeDeal,
} from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';

type Mode = 'REGISTER' | 'CATALOG';

interface CartLine {
  product: MerchantProduct;
  quantity: number;
}

// Real cash-register/POS UI -- the register-software half of "Toss Place" (see
// MerchantProductService.kt's own doc comment for why the hardware bundle stays out of
// scope but this doesn't need to). Checkout reuses the already-real generateQr/
// chargeCard flows unmodified, just with a cart-derived amount/description instead of a
// single typed-in amount.
export default function PosScreen() {
  const { t } = useI18n();
  const [mode, setMode] = useState<Mode>('REGISTER');

  return (
    <div style={{ maxWidth: '900px' }}>
      <div className="itunda-card" style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', maxWidth: '300px' }}>
        {(['REGISTER', 'CATALOG'] as Mode[]).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              flex: 1,
              padding: '10px',
              borderRadius: '10px',
              fontSize: '14px',
              fontWeight: 700,
              color: mode === m ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: mode === m ? 'var(--itunda-blue)' : 'transparent',
            }}
          >
            {m === 'REGISTER' ? t('pos.modeRegister') : t('pos.modeCatalog')}
          </button>
        ))}
      </div>
      {mode === 'REGISTER' ? <RegisterView /> : <CatalogView />}
    </div>
  );
}

function RegisterView() {
  const { t } = useI18n();
  const [products, setProducts] = useState<MerchantProduct[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [checkingOut, setCheckingOut] = useState(false);

  const load = () => {
    setLoadError(null);
    getProductCatalog()
      .then(setProducts)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : t('pos.catalogLoadError')));
  };

  useEffect(load, []);

  const addToCart = (product: MerchantProduct) => {
    if (product.stockQuantity === 0) return;
    setCart((prev) => {
      const existing = prev.find((line) => line.product.id === product.id);
      if (existing) {
        if (product.stockQuantity !== null && existing.quantity >= product.stockQuantity) return prev;
        return prev.map((line) => (line.product.id === product.id ? { ...line, quantity: line.quantity + 1 } : line));
      }
      return [...prev, { product, quantity: 1 }];
    });
  };

  const changeQuantity = (productId: string, delta: number) => {
    setCart((prev) =>
      prev
        .map((line) => (line.product.id === productId ? { ...line, quantity: line.quantity + delta } : line))
        .filter((line) => line.quantity > 0),
    );
  };

  const total = cart.reduce((sum, line) => sum + line.product.price * line.quantity, 0);
  const description = cart.map((line) => `${line.quantity}x ${line.product.name}`).join(', ');

  if (checkingOut) {
    return (
      <CheckoutView
        total={total}
        description={description}
        onDone={() => {
          setCart([]);
          setCheckingOut(false);
        }}
        onCancel={() => setCheckingOut(false)}
      />
    );
  }

  if (loadError) {
    return (
      <div className="itunda-card">
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">
          {loadError}
        </p>
        <button className="itunda-btn itunda-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>
          {t('pos.retry')}
        </button>
      </div>
    );
  }

  if (products === null) {
    return <div className="itunda-card">{t('pos.loading')}</div>;
  }

  return (
    <div style={{ display: 'flex', gap: '20px' }}>
      <div style={{ flex: 2 }}>
        {products.length === 0 ? (
          <div className="itunda-card">
            <EmptyState message={t('pos.registerEmpty')} />
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '10px' }}>
            {products.map((product) => (
              <button
                key={product.id}
                onClick={() => addToCart(product)}
                disabled={product.stockQuantity === 0}
                className="itunda-card"
                style={{ padding: '16px', textAlign: 'left', cursor: 'pointer', opacity: product.stockQuantity === 0 ? 0.55 : 1 }}
              >
                {product.imageUrl && <img src={product.imageUrl} alt="" style={{ width: '100%', aspectRatio: '1.5', objectFit: 'cover', borderRadius: '8px', marginBottom: '10px' }} />}
                <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{product.name}</p>
                <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
                  {product.price.toLocaleString()} RWF
                </p>
                {product.stockQuantity !== null && <p style={{ fontSize: '12px', color: product.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)', marginTop: '4px' }}>{product.stockQuantity === 0 ? t('pos.outOfStock') : t('pos.stockAvailable', { count: product.stockQuantity })}</p>}
              </button>
            ))}
          </div>
        )}
      </div>

      <div style={{ flex: 1 }}>
        <div className="itunda-card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Store size={18} color="var(--itunda-blue)" />
            <h2 style={{ fontSize: '16px', fontWeight: 700 }}>{t('pos.cartTitle')}</h2>
          </div>
          {cart.length === 0 ? (
            <p style={{ padding: '0 20px 20px', fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
              {t('pos.cartEmpty')}
            </p>
          ) : (
            <div>
              {cart.map((line) => (
                <div
                  key={line.product.id}
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 20px', borderTop: '1px solid var(--itunda-grey-200)' }}
                >
                  <div>
                    <p style={{ fontSize: '13px', fontWeight: 600 }}>{line.product.name}</p>
                    <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
                      {(line.product.price * line.quantity).toLocaleString()} RWF
                    </p>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    {/* Real touch-target-size fix (item 244, web accessibility
                        sweep): these had no padding at all -- the clickable area
                        was just the bare 14px icon, well under WCAG 2.5.8's 24x24
                        CSS-pixel AA minimum. Real POS use (a merchant tapping fast
                        through a checkout), so this is a real usability risk, not
                        just a compliance checkbox. */}
                    <button type="button" aria-label={t('pos.decreaseQuantityAria', { name: line.product.name })} onClick={() => changeQuantity(line.product.id, -1)} style={{ color: 'var(--itunda-grey-500)', width: '28px', height: '28px', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0 }}>
                      <Minus size={14} />
                    </button>
                    <span style={{ fontSize: '13px', fontWeight: 600, minWidth: '16px', textAlign: 'center' }}>{line.quantity}</span>
                    <button type="button" aria-label={t('pos.increaseQuantityAria', { name: line.product.name })} onClick={() => changeQuantity(line.product.id, 1)} style={{ color: 'var(--itunda-grey-500)', width: '28px', height: '28px', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 0 }}>
                      <Plus size={14} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
          <div style={{ padding: '16px 20px', borderTop: '1px solid var(--itunda-grey-200)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
              <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('pos.total')}</span>
              <span style={{ fontSize: '18px', fontWeight: 700 }}>{total.toLocaleString()} RWF</span>
            </div>
            <button
              className="itunda-btn itunda-btn-primary"
              style={{ width: '100%' }}
              disabled={cart.length === 0}
              onClick={() => setCheckingOut(true)}
            >
              {t('pos.checkoutButton')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function CheckoutView({
  total, description, onDone, onCancel,
}: { total: number; description: string; onDone: () => void; onCancel: () => void }) {
  const { t } = useI18n();
  const [checkoutMode, setCheckoutMode] = useState<'QR' | 'CARD'>('QR');

  return (
    <div className="itunda-card" style={{ maxWidth: '400px' }}>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{t('pos.checkoutTitle')}</h2>
      <p style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}>{total.toLocaleString()} RWF</p>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>{description}</p>

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
        {(['QR', 'CARD'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setCheckoutMode(m)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: checkoutMode === m ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: checkoutMode === m ? 'var(--itunda-blue)' : 'transparent',
            }}
          >
            {m === 'QR' ? t('pos.checkoutModeQr') : t('pos.checkoutModeCard')}
          </button>
        ))}
      </div>

      {checkoutMode === 'QR' ? (
        <QrCheckout amount={total} description={description} onDone={onDone} />
      ) : (
        <CardCheckout amount={total} description={description} onDone={onDone} />
      )}

      <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%', marginTop: '12px' }} onClick={onCancel}>
        {t('pos.backToCart')}
      </button>
    </div>
  );
}

function QrCheckout({ amount, description, onDone }: { amount: number; description: string; onDone: () => void }) {
  const { t } = useI18n();
  const [intent, setIntent] = useState<PaymentIntent | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const generate = async () => {
    setError(null);
    setSubmitting(true);
    try {
      const newIntent = await generateQr(amount, description);
      setIntent(newIntent);
      setQrDataUrl(await QRCode.toDataURL(paymentIntentQrPayload(newIntent.id), { width: 220, margin: 1 }));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.qrGenerateError'));
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => { generate(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (intent && qrDataUrl) {
    return (
      <div style={{ textAlign: 'center' }}>
        <img src={qrDataUrl} alt="Payment QR code" width={220} height={220} style={{ borderRadius: '16px', marginBottom: '12px' }} />
        <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={onDone}>
          {t('pos.doneNewSale')}
        </button>
      </div>
    );
  }

  return (
    <div style={{ textAlign: 'center' }}>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">
          {error}
        </p>
      )}
      <button className="itunda-btn itunda-btn-secondary" onClick={generate} disabled={submitting} style={{ gap: '6px' }}>
        <RefreshCw size={14} /> {submitting ? t('pos.generating') : t('pos.retry')}
      </button>
    </div>
  );
}

function CardCheckout({ amount, description, onDone }: { amount: number; description: string; onDone: () => void }) {
  const { t } = useI18n();
  const [cardNumber, setCardNumber] = useState('');
  const [expiryMonth, setExpiryMonth] = useState('');
  const [expiryYear, setExpiryYear] = useState('');
  const [cvc, setCvc] = useState('');
  const [result, setResult] = useState<CardChargeResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real device step-up (2026-07-28 port) -- a real 403 DEVICE_NOT_VERIFIED (this
  // device hasn't been step-up-verified yet) gets its own case, not a generic error.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const charge = await chargeCard(amount, description, cardNumber.replace(/\s/g, ''), Number(expiryMonth), Number(expiryYear), cvc);
      setResult(charge);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('pos.cardChargeError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (result) {
    return (
      <div style={{ textAlign: 'center' }}>
        <CreditCard size={32} color="var(--itunda-blue)" style={{ marginBottom: '8px' }} />
        <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>{t('pos.cardChargedResult', { last4: result.cardLast4 })}</p>
        <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={onDone}>
          {t('pos.doneNewSale')}
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <input
        type="text" inputMode="numeric" value={cardNumber} onChange={(e) => setCardNumber(e.target.value)}
        placeholder="4242 4242 4242 4242" required
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }}
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <input
          type="number" min="1" max="12" value={expiryMonth} onChange={(e) => setExpiryMonth(e.target.value)}
          placeholder="MM" required
          style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number" min="2026" value={expiryYear} onChange={(e) => setExpiryYear(e.target.value)}
          placeholder="YYYY" required
          style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }}
        />
        <input
          type="text" inputMode="numeric" value={cvc} onChange={(e) => setCvc(e.target.value)}
          placeholder="CVC" required
          style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px' }}
        />
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
          {error}
        </p>
      )}
      {needsDeviceVerification ? (
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : (
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? t('pos.charging') : t('pos.chargeButton', { amount: amount.toLocaleString() })}
        </button>
      )}
    </form>
  );
}

function CatalogView() {
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
                        <div>{product.price.toLocaleString()} RWF</div>
                        {product.originalPrice && product.discountPercent && <div style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}><s>{product.originalPrice.toLocaleString()} RWF</s> · {t('pos.discountOff', { percent: product.discountPercent })}</div>}
                        <div style={{ fontSize: '12px', color: product.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)', marginTop: '2px' }}>
                          {product.stockQuantity === null ? t('pos.unlimitedStock') : product.stockQuantity === 0 ? t('pos.outOfStock') : t('pos.inStock', { count: product.stockQuantity })}
                        </div>
                      </td>
                      <td style={{ padding: '10px 20px', textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '14px' }}>
                          <button
                            onClick={() => setExpandedProductId(isExpanded ? null : product.id)}
                            style={{ color: 'var(--itunda-blue)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.optionsToggle')} {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => setExpandedPricingProductId(isPricingExpanded ? null : product.id)}
                            style={{ color: 'var(--itunda-blue)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.pricingToggle')} {isPricingExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => setExpandedTimeDealProductId(isTimeDealExpanded ? null : product.id)}
                            style={{ color: 'var(--itunda-blue)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.timeDealToggle')} {isTimeDealExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => adjustStock(product)}
                            style={{ color: 'var(--itunda-blue)', fontSize: '13px', fontWeight: 600 }}
                          >
                            {t('pos.adjustStockButton')}
                          </button>
                          <button
                            onClick={() => handleSetSurplusDeal(product)}
                            style={{ color: product.isSurplusDeal ? 'var(--itunda-red)' : 'var(--itunda-blue)', fontSize: '13px', fontWeight: 600 }}
                          >
                            {product.isSurplusDeal ? t('pos.surplusDealClearButton') : t('pos.surplusDealSetButton')}
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

interface ChoiceDraft {
  name: string;
  priceDelta: string;
}

// Real menu-item option-group management (2026-07-21) -- the merchant-facing half of
// docs/DESIGN_REFERENCES.md's Eats recommendation #3 (the buyer half, bank-mfe's own
// "Choose options" panel in MenuView, already existed). Before this, the only way to
// create an option group at all was a direct API call -- no UI anywhere. v1: required
// single-select only, matching MenuOptionGroup.kt's own real, honestly-scoped backend
// constraint (at least 2 choices per group, enforced server-side too).
function ProductOptionsPanel({ productId }: { productId: string }) {
  const { t } = useI18n();
  const [groups, setGroups] = useState<MenuOptionGroup[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [groupName, setGroupName] = useState('');
  const [choices, setChoices] = useState<ChoiceDraft[]>([{ name: '', priceDelta: '0' }, { name: '', priceDelta: '0' }]);
  const [submitting, setSubmitting] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    getOptionGroups(productId)
      .then(setGroups)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pos.optionGroupsLoadError')));
  };

  useEffect(load, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  const updateChoice = (index: number, field: keyof ChoiceDraft, value: string) => {
    setChoices((prev) => prev.map((c, i) => (i === index ? { ...c, [field]: value } : c)));
  };

  const addChoiceRow = () => setChoices((prev) => [...prev, { name: '', priceDelta: '0' }]);
  const removeChoiceRow = (index: number) => setChoices((prev) => prev.filter((_, i) => i !== index));

  const handleAddGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const validChoices = choices
        .map((c) => ({ name: c.name.trim(), priceDelta: Number(c.priceDelta || 0) }))
        .filter((c) => c.name.length > 0);
      await addOptionGroup(productId, groupName.trim(), validChoices);
      setGroupName('');
      setChoices([{ name: '', priceDelta: '0' }, { name: '', priceDelta: '0' }]);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.addOptionGroupError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemoveGroup = async (groupId: string) => {
    setRemovingId(groupId);
    try {
      await removeOptionGroup(productId, groupId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.removeOptionGroupError'));
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <div>
        <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '8px' }}>{t('pos.existingOptionGroups')}</p>
        {groups === null ? (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('pos.loading')}</p>
        ) : groups.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            {t('pos.optionGroupsEmpty')}
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {groups.map((group) => (
              <div key={group.id} className="itunda-card" style={{ padding: '12px 16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{group.name}</p>
                    <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
                      {group.choices.map((c) => `${c.name}${c.priceDelta > 0 ? ` (+${c.priceDelta.toLocaleString()} RWF)` : ''}`).join(', ')}
                    </p>
                  </div>
                  <button
                    onClick={() => handleRemoveGroup(group.id)}
                    disabled={removingId === group.id}
                    style={{ color: 'var(--itunda-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '12px' }}
                  >
                    <Trash2 size={12} /> {removingId === group.id ? t('pos.removing') : t('pos.removeButton')}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <form onSubmit={handleAddGroup} style={{ display: 'flex', flexDirection: 'column', gap: '10px', borderTop: '1px solid var(--itunda-grey-200)', paddingTop: '14px' }}>
        <p style={{ fontSize: '13px', fontWeight: 700 }}>{t('pos.addOptionGroupTitle')}</p>
        <input
          type="text" value={groupName} onChange={(e) => setGroupName(e.target.value)} placeholder={t('pos.groupNamePlaceholder')} required
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', maxWidth: '320px' }}
        />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {choices.map((choice, i) => (
            <div key={i} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="text" value={choice.name} onChange={(e) => updateChoice(i, 'name', e.target.value)}
                placeholder={t('pos.choicePlaceholder', { index: i + 1, example: i === 0 ? 'Small' : 'Large' })}
                style={{ flex: 2, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              <input
                type="number" value={choice.priceDelta} onChange={(e) => updateChoice(i, 'priceDelta', e.target.value)}
                placeholder={t('pos.choicePriceDeltaPlaceholder')} style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              {choices.length > 2 && (
                <button type="button" onClick={() => removeChoiceRow(i)} style={{ color: 'var(--itunda-grey-500)', padding: '5px' }} aria-label={t('pos.removeChoiceAria')}>
                  <Minus size={14} />
                </button>
              )}
            </div>
          ))}
          <button type="button" onClick={addChoiceRow} style={{ alignSelf: 'flex-start', color: 'var(--itunda-blue)', fontSize: '12px', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <Plus size={12} /> {t('pos.addAnotherChoice')}
          </button>
        </div>
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
        )}
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ alignSelf: 'flex-start' }}>
          {submitting ? t('pos.adding') : t('pos.addOptionGroupButton')}
        </button>
      </form>
    </div>
  );
}

interface TierDraft {
  minQuantity: string;
  unitPrice: string;
}

// Real bulk/wholesale price tiers (item 149) -- see backend ProductPriceTier's own doc
// comment. First client UI for this endpoint on ANY platform (no bank-mfe/Android/iOS
// UI exists yet to have ported this from). Real checkout money impact: OrderService
// applies the highest-qualifying tier automatically at order time, so this is real
// pricing configuration, not a cosmetic label. Replace-all on save, mirroring
// ProductOptionsPanel's own add/remove-row editing pattern above; server-side
// validation (strictly increasing minQuantity, strictly decreasing unitPrice, each
// tier below the regular price, max 10 tiers) is the real source of truth -- this form
// mirrors those same rules client-side only for a faster error round trip.
function PriceTiersPanel({ productId, regularPrice }: { productId: string; regularPrice: number }) {
  const { t } = useI18n();
  const [tiers, setTiers] = useState<TierDraft[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const load = () => {
    fetchPriceTiers(productId)
      .then((real) => setTiers(real.map((tier) => ({ minQuantity: String(tier.minQuantity), unitPrice: String(tier.unitPrice) }))))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pos.priceTiersLoadError')));
  };

  useEffect(load, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  const updateTier = (index: number, field: keyof TierDraft, value: string) => {
    setSaved(false);
    setTiers((prev) => (prev ?? []).map((t, i) => (i === index ? { ...t, [field]: value } : t)));
  };

  const addTierRow = () => {
    setSaved(false);
    setTiers((prev) => [...(prev ?? []), { minQuantity: '', unitPrice: '' }]);
  };
  const removeTierRow = (index: number) => {
    setSaved(false);
    setTiers((prev) => (prev ?? []).filter((_, i) => i !== index));
  };

  const handleSave = async () => {
    setError(null);
    const parsed = (tiers ?? [])
      .filter((t) => t.minQuantity.trim() !== '' || t.unitPrice.trim() !== '')
      .map((t) => ({ minQuantity: Number(t.minQuantity), unitPrice: Number(t.unitPrice) }));
    for (const tier of parsed) {
      if (!Number.isInteger(tier.minQuantity) || tier.minQuantity < 1) {
        setError(t('pos.tierMinQuantityError'));
        return;
      }
      if (!Number.isFinite(tier.unitPrice) || tier.unitPrice <= 0) {
        setError(t('pos.tierUnitPriceError'));
        return;
      }
      if (tier.unitPrice >= regularPrice) {
        setError(t('pos.tierBelowRegularError', { price: regularPrice.toLocaleString() }));
        return;
      }
    }
    if (parsed.length > 10) {
      setError(t('pos.tierTooManyError'));
      return;
    }
    setSaving(true);
    try {
      const real = await setPriceTiers(productId, parsed);
      setTiers(real.map((tier) => ({ minQuantity: String(tier.minQuantity), unitPrice: String(tier.unitPrice) })));
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.tierSaveError'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700 }}>{t('pos.bulkPricingTitle')}</p>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
        {t('pos.bulkPricingBody')}
      </p>
      {tiers === null ? (
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('pos.loading')}</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {tiers.map((tier, i) => (
            <div key={i} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="number" min="1" step="1" value={tier.minQuantity} onChange={(e) => updateTier(i, 'minQuantity', e.target.value)}
                placeholder={t('pos.tierMinQuantityPlaceholder')}
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              <input
                type="number" min="1" value={tier.unitPrice} onChange={(e) => updateTier(i, 'unitPrice', e.target.value)}
                placeholder={t('pos.tierUnitPricePlaceholder')}
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              <button type="button" onClick={() => removeTierRow(i)} style={{ color: 'var(--itunda-grey-500)', padding: '5px' }} aria-label={t('pos.removeTierAria')}>
                <Minus size={14} />
              </button>
            </div>
          ))}
          <button type="button" onClick={addTierRow} style={{ alignSelf: 'flex-start', color: 'var(--itunda-blue)', fontSize: '12px', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <Plus size={12} /> {t('pos.addTier')}
          </button>
        </div>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      {saved && !error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-blue)', margin: 0 }}>{t('pos.saved')}</p>
      )}
      <button type="button" className="itunda-btn itunda-btn-primary" disabled={saving || tiers === null} onClick={handleSave} style={{ alignSelf: 'flex-start' }}>
        {saving ? t('pos.saving') : t('pos.saveTiersButton')}
      </button>
    </div>
  );
}

// Real Coupang 타임특가 (Time Deal, item 226) -- see the backend TimeDeal.kt's own doc
// comment. A time-boxed, quantity-capped discount, distinct from PriceTiersPanel above
// (a permanent bulk-quantity discount, not a scheduled event). Consumer browse is
// already real on bank-mfe/Android/iOS; this is the first client for the creation half.
// GET /api/v1/time-deals/mine returns every deal across all of a merchant's products
// (no product-scoped endpoint exists), so this panel filters that list client-side to
// this one product -- an honest tradeoff for a merchant who rarely has more than a
// handful of deals running at once, not a real N+1 concern.
function TimeDealPanel({ productId, regularPrice }: { productId: string; regularPrice: number }) {
  const { t } = useI18n();
  const [deals, setDeals] = useState<TimeDeal[] | null>(null);
  const [dealPrice, setDealPrice] = useState('');
  const [totalQuantity, setTotalQuantity] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [busyDealId, setBusyDealId] = useState<string | null>(null);

  const load = () => {
    fetchMyTimeDeals()
      .then((all) => setDeals(all.filter((d) => d.productId === productId)))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pos.timeDealsLoadError')));
  };

  useEffect(load, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  const now = Date.now();
  const activeDeals = (deals ?? []).filter((d) => new Date(d.endsAt).getTime() > now && d.remainingQuantity > 0);
  const pastDeals = (deals ?? []).filter((d) => !activeDeals.includes(d));

  const handleCreate = async () => {
    setError(null);
    const price = Number(dealPrice);
    const quantity = Number(totalQuantity);
    if (!Number.isFinite(price) || price <= 0 || price >= regularPrice) {
      setError(t('pos.timeDealPriceError', { price: regularPrice.toLocaleString() }));
      return;
    }
    if (!Number.isInteger(quantity) || quantity < 1) {
      setError(t('pos.timeDealQuantityError'));
      return;
    }
    if (!startsAt || !endsAt) {
      setError(t('pos.timeDealTimesRequiredError'));
      return;
    }
    const startIso = new Date(startsAt).toISOString();
    const endIso = new Date(endsAt).toISOString();
    if (new Date(endIso).getTime() <= new Date(startIso).getTime()) {
      setError(t('pos.timeDealEndAfterStartError'));
      return;
    }
    setSubmitting(true);
    try {
      await createTimeDeal(productId, price, quantity, startIso, endIso);
      setDealPrice('');
      setTotalQuantity('');
      setStartsAt('');
      setEndsAt('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.timeDealCreateError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleEnd = async (dealId: string) => {
    setBusyDealId(dealId);
    try {
      await endTimeDeal(dealId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.timeDealEndError'));
    } finally {
      setBusyDealId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700 }}>{t('pos.timeDealTitle')}</p>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
        {t('pos.timeDealBody')}
      </p>
      {activeDeals.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {activeDeals.map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 10px', borderRadius: '8px', backgroundColor: 'var(--itunda-grey-200)' }}>
              <span style={{ fontSize: '13px' }}>
                {d.dealPrice.toLocaleString()} RWF · {d.remainingQuantity}/{d.totalQuantity} left · ends {new Date(d.endsAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
              </span>
              <button type="button" onClick={() => handleEnd(d.id)} disabled={busyDealId === d.id} style={{ color: 'var(--itunda-grey-500)', fontSize: '12px' }}>
                {busyDealId === d.id ? '…' : t('pos.timeDealEndNow')}
              </button>
            </div>
          ))}
        </div>
      ) : (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <input
            type="number" min="1" value={dealPrice} onChange={(e) => setDealPrice(e.target.value)} placeholder={t('pos.timeDealPricePlaceholder')}
            style={{ flex: 1, minWidth: '140px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
          <input
            type="number" min="1" step="1" value={totalQuantity} onChange={(e) => setTotalQuantity(e.target.value)} placeholder={t('pos.timeDealQuantityPlaceholder')}
            style={{ flex: 1, minWidth: '100px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
          <input
            type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)}
            style={{ flex: 1, minWidth: '160px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
          <input
            type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)}
            style={{ flex: 1, minWidth: '160px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
        </div>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      {activeDeals.length === 0 && (
        <button type="button" className="itunda-btn itunda-btn-primary" disabled={submitting} onClick={handleCreate} style={{ alignSelf: 'flex-start' }}>
          {submitting ? t('pos.creating') : t('pos.startTimeDeal')}
        </button>
      )}
      {pastDeals.length > 0 && (
        <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>
          {t(pastDeals.length === 1 ? 'pos.pastDealsSingular' : 'pos.pastDealsPlural', { count: pastDeals.length })}
        </p>
      )}
    </div>
  );
}
