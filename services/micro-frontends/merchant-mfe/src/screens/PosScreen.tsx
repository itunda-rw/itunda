import { Fragment, useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { ChevronDown, ChevronUp, CreditCard, Minus, Plus, RefreshCw, Store, Trash2 } from 'lucide-react';
import { ApiError } from '../lib/api';
import { DeviceStepUpPrompt } from '../components/DeviceStepUpPrompt';
import {
  addOptionGroup,
  addProduct,
  updateProductStock,
  chargeCard,
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
} from '../lib/merchant';

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
  const [mode, setMode] = useState<Mode>('REGISTER');

  return (
    <div style={{ maxWidth: '900px' }}>
      <div className="toss-card" style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', maxWidth: '300px' }}>
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
              color: mode === m ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: mode === m ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {m === 'REGISTER' ? 'Register' : 'Catalog'}
          </button>
        ))}
      </div>
      {mode === 'REGISTER' ? <RegisterView /> : <CatalogView />}
    </div>
  );
}

function RegisterView() {
  const [products, setProducts] = useState<MerchantProduct[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [checkingOut, setCheckingOut] = useState(false);

  const load = () => {
    setLoadError(null);
    getProductCatalog()
      .then(setProducts)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : 'Could not load the catalog.'));
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
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">
          {loadError}
        </p>
        <button className="toss-btn toss-btn-secondary" onClick={load} style={{ marginTop: '12px' }}>
          Retry
        </button>
      </div>
    );
  }

  if (products === null) {
    return <div className="toss-card">Loading…</div>;
  }

  return (
    <div style={{ display: 'flex', gap: '20px' }}>
      <div style={{ flex: 2 }}>
        {products.length === 0 ? (
          <div className="toss-card">
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
              No products yet — add some in the Catalog tab first.
            </p>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: '10px' }}>
            {products.map((product) => (
              <button
                key={product.id}
                onClick={() => addToCart(product)}
                disabled={product.stockQuantity === 0}
                className="toss-card"
                style={{ padding: '16px', textAlign: 'left', cursor: 'pointer', opacity: product.stockQuantity === 0 ? 0.55 : 1 }}
              >
                {product.imageUrl && <img src={product.imageUrl} alt="" style={{ width: '100%', aspectRatio: '1.5', objectFit: 'cover', borderRadius: '8px', marginBottom: '10px' }} />}
                <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{product.name}</p>
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
                  {product.price.toLocaleString()} RWF
                </p>
                {product.stockQuantity !== null && <p style={{ fontSize: '12px', color: product.stockQuantity === 0 ? '#E53935' : 'var(--toss-grey-500)', marginTop: '4px' }}>{product.stockQuantity === 0 ? 'Out of stock' : `${product.stockQuantity} available`}</p>}
              </button>
            ))}
          </div>
        )}
      </div>

      <div style={{ flex: 1 }}>
        <div className="toss-card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Store size={18} color="var(--toss-blue)" />
            <h2 style={{ fontSize: '16px', fontWeight: 700 }}>Cart</h2>
          </div>
          {cart.length === 0 ? (
            <p style={{ padding: '0 20px 20px', fontSize: '13px', color: 'var(--toss-grey-500)' }}>
              Tap a product to add it.
            </p>
          ) : (
            <div>
              {cart.map((line) => (
                <div
                  key={line.product.id}
                  style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 20px', borderTop: '1px solid var(--toss-grey-200)' }}
                >
                  <div>
                    <p style={{ fontSize: '13px', fontWeight: 600 }}>{line.product.name}</p>
                    <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
                      {(line.product.price * line.quantity).toLocaleString()} RWF
                    </p>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button onClick={() => changeQuantity(line.product.id, -1)} style={{ color: 'var(--toss-grey-500)' }}>
                      <Minus size={14} />
                    </button>
                    <span style={{ fontSize: '13px', fontWeight: 600, minWidth: '16px', textAlign: 'center' }}>{line.quantity}</span>
                    <button onClick={() => changeQuantity(line.product.id, 1)} style={{ color: 'var(--toss-grey-500)' }}>
                      <Plus size={14} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
          <div style={{ padding: '16px 20px', borderTop: '1px solid var(--toss-grey-200)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
              <span style={{ fontSize: '14px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Total</span>
              <span style={{ fontSize: '18px', fontWeight: 700 }}>{total.toLocaleString()} RWF</span>
            </div>
            <button
              className="toss-btn toss-btn-primary"
              style={{ width: '100%' }}
              disabled={cart.length === 0}
              onClick={() => setCheckingOut(true)}
            >
              Checkout
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
  const [checkoutMode, setCheckoutMode] = useState<'QR' | 'CARD'>('QR');

  return (
    <div className="toss-card" style={{ maxWidth: '400px' }}>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>Checkout</h2>
      <p style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}>{total.toLocaleString()} RWF</p>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>{description}</p>

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['QR', 'CARD'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setCheckoutMode(m)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: checkoutMode === m ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: checkoutMode === m ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {m === 'QR' ? 'QR code' : 'Card'}
          </button>
        ))}
      </div>

      {checkoutMode === 'QR' ? (
        <QrCheckout amount={total} description={description} onDone={onDone} />
      ) : (
        <CardCheckout amount={total} description={description} onDone={onDone} />
      )}

      <button className="toss-btn toss-btn-secondary" style={{ width: '100%', marginTop: '12px' }} onClick={onCancel}>
        Back to cart
      </button>
    </div>
  );
}

function QrCheckout({ amount, description, onDone }: { amount: number; description: string; onDone: () => void }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not generate a QR code.');
    } finally {
      setSubmitting(false);
    }
  };

  useEffect(() => { generate(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (intent && qrDataUrl) {
    return (
      <div style={{ textAlign: 'center' }}>
        <img src={qrDataUrl} alt="Payment QR code" width={220} height={220} style={{ borderRadius: '16px', marginBottom: '12px' }} />
        <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} onClick={onDone}>
          Done — new sale
        </button>
      </div>
    );
  }

  return (
    <div style={{ textAlign: 'center' }}>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">
          {error}
        </p>
      )}
      <button className="toss-btn toss-btn-secondary" onClick={generate} disabled={submitting} style={{ gap: '6px' }}>
        <RefreshCw size={14} /> {submitting ? 'Generating…' : 'Retry'}
      </button>
    </div>
  );
}

function CardCheckout({ amount, description, onDone }: { amount: number; description: string; onDone: () => void }) {
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
        setError(err instanceof ApiError ? err.message : 'Could not charge this card.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (result) {
    return (
      <div style={{ textAlign: 'center' }}>
        <CreditCard size={32} color="var(--toss-blue)" style={{ marginBottom: '8px' }} />
        <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '12px' }}>Card charged — •••• {result.cardLast4}</p>
        <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} onClick={onDone}>
          Done — new sale
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <input
        type="text" inputMode="numeric" value={cardNumber} onChange={(e) => setCardNumber(e.target.value)}
        placeholder="4242 4242 4242 4242" required
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <input
          type="number" min="1" max="12" value={expiryMonth} onChange={(e) => setExpiryMonth(e.target.value)}
          placeholder="MM" required
          style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number" min="2026" value={expiryYear} onChange={(e) => setExpiryYear(e.target.value)}
          placeholder="YYYY" required
          style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="text" inputMode="numeric" value={cvc} onChange={(e) => setCvc(e.target.value)}
          placeholder="CVC" required
          style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
          {error}
        </p>
      )}
      {needsDeviceVerification ? (
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : (
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Charging…' : `Charge ${amount.toLocaleString()} RWF`}
        </button>
      )}
    </form>
  );
}

function CatalogView() {
  const [products, setProducts] = useState<MerchantProduct[] | null>(null);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [originalPrice, setOriginalPrice] = useState('');
  const [imageUrl, setImageUrl] = useState('');
  const [description, setDescription] = useState('');
  const [stockQuantity, setStockQuantity] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real menu-item option groups management (2026-07-21) -- only one product's panel
  // expanded at a time, same "inline-card-replaces-trigger" convention bank-mfe's own
  // buyer-side option UI already established.
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  // Real bulk/wholesale price tiers (item 149, backend-only until now) -- same
  // "one panel expanded at a time" convention as the Options panel above, its own
  // separate toggle since a product can have both option groups and price tiers.
  const [expandedPricingProductId, setExpandedPricingProductId] = useState<string | null>(null);
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
      setError('Enter a price greater than zero.');
      return;
    }
    if (previousPrice !== undefined && (!Number.isFinite(previousPrice) || previousPrice <= currentPrice)) {
      setError('The original price must be greater than the current price.');
      return;
    }
    if (stock !== undefined && (!Number.isInteger(stock) || stock < 0)) {
      setError('Stock must be a whole number of zero or more. Leave it blank for unlimited availability.');
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
      setError(err instanceof ApiError ? err.message : 'Could not add this product.');
    } finally {
      setSubmitting(false);
    }
  };

  const adjustStock = async (product: MerchantProduct) => {
    const value = window.prompt(
      'Set available units. Leave blank for unlimited availability.',
      product.stockQuantity === null ? '' : String(product.stockQuantity),
    );
    if (value === null) return;
    const trimmed = value.trim();
    const stock = trimmed === '' ? null : Number(trimmed);
    if (stock !== null && (!Number.isInteger(stock) || stock < 0)) {
      setError('Stock must be a whole number of zero or more. Leave it blank for unlimited availability.');
      return;
    }
    try {
      await updateProductStock(product.id, stock);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update stock.');
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      <div className="toss-card">
        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '16px' }}>Add a product</h2>
        <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '12px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 2, minWidth: '160px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Name</span>
            <input
              type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Latte" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '120px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Price (RWF)</span>
            <input
              type="number" min="1" value={price} onChange={(e) => setPrice(e.target.value)} placeholder="2500" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '150px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Stock (optional)</span>
            <input
              type="number" min="0" step="1" value={stockQuantity} onChange={(e) => setStockQuantity(e.target.value)} placeholder="Unlimited"
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1, minWidth: '120px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Original price (optional)</span>
            <input
              type="number" min="1" value={originalPrice} onChange={(e) => setOriginalPrice(e.target.value)} placeholder="3000"
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 2, minWidth: '220px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Image URL (optional)</span>
            <input
              type="url" value={imageUrl} onChange={(e) => setImageUrl(e.target.value)} placeholder="https://…/latte.jpg"
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flexBasis: '100%' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Description (optional)</span>
            <textarea
              value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} rows={2} placeholder="What customers should know about this item"
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px', resize: 'vertical' }}
            />
          </label>
          <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ height: '46px' }}>
            {submitting ? 'Adding…' : 'Add'}
          </button>
        </form>
        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', margin: '12px 0 0' }} role="alert">
            {error}
          </p>
        )}
      </div>

      {lowStock.length > 0 && (
        <div className="toss-card" role="status" style={{ borderLeft: '4px solid #F59E0B', background: '#FFFBEB' }}>
          <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
            {lowStock.length} product{lowStock.length === 1 ? '' : 's'} need stock attention
          </p>
          <p style={{ marginTop: '4px', fontSize: '13px', color: 'var(--toss-grey-700)' }}>
            {lowStock.map((product) => `${product.name} (${product.stockQuantity === 0 ? 'out of stock' : `${product.stockQuantity} left`})`).join(', ')}
          </p>
        </div>
      )}

      {products === null ? (
        <div className="toss-card">Loading…</div>
      ) : products.length === 0 ? (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No products yet.</p>
        </div>
      ) : (
        <div className="toss-card" style={{ padding: 0, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '14px' }}>
            <thead>
              <tr style={{ backgroundColor: 'var(--toss-grey-100)', textAlign: 'left' }}>
                {['Product', 'Price', ''].map((h) => (
                  <th key={h} style={{ padding: '10px 20px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {products.map((product) => {
                const isExpanded = expandedProductId === product.id;
                const isPricingExpanded = expandedPricingProductId === product.id;
                return (
                  <Fragment key={product.id}>
                    <tr style={{ borderTop: '1px solid var(--toss-grey-200)' }}>
                      <td style={{ padding: '10px 20px', fontWeight: 600 }}>
                        <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                          {product.imageUrl && <img src={product.imageUrl} alt="" width={36} height={36} style={{ borderRadius: '6px', objectFit: 'cover' }} />}
                          <div><div>{product.name}</div>{product.description && <div style={{ fontWeight: 400, fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '2px' }}>{product.description}</div>}</div>
                        </div>
                      </td>
                      <td style={{ padding: '10px 20px' }}>
                        <div>{product.price.toLocaleString()} RWF</div>
                        {product.originalPrice && product.discountPercent && <div style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '2px' }}><s>{product.originalPrice.toLocaleString()} RWF</s> · {product.discountPercent}% off</div>}
                        <div style={{ fontSize: '12px', color: product.stockQuantity === 0 ? '#E53935' : 'var(--toss-grey-500)', marginTop: '2px' }}>
                          {product.stockQuantity === null ? 'Unlimited stock' : product.stockQuantity === 0 ? 'Out of stock' : `${product.stockQuantity} in stock`}
                        </div>
                      </td>
                      <td style={{ padding: '10px 20px', textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '14px' }}>
                          <button
                            onClick={() => setExpandedProductId(isExpanded ? null : product.id)}
                            style={{ color: 'var(--toss-blue)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            Options {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => setExpandedPricingProductId(isPricingExpanded ? null : product.id)}
                            style={{ color: 'var(--toss-blue)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', fontWeight: 600 }}
                          >
                            Pricing {isPricingExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                          </button>
                          <button
                            onClick={() => adjustStock(product)}
                            style={{ color: 'var(--toss-blue)', fontSize: '13px', fontWeight: 600 }}
                          >
                            Adjust stock
                          </button>
                          <button
                            onClick={() => removeProduct(product.id).then(load)}
                            style={{ color: 'var(--toss-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                          >
                            <Trash2 size={14} /> Remove
                          </button>
                        </div>
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr style={{ borderTop: '1px solid var(--toss-grey-200)', backgroundColor: 'var(--toss-grey-100)' }}>
                        <td colSpan={3} style={{ padding: '16px 20px' }}>
                          <ProductOptionsPanel productId={product.id} />
                        </td>
                      </tr>
                    )}
                    {isPricingExpanded && (
                      <tr style={{ borderTop: '1px solid var(--toss-grey-200)', backgroundColor: 'var(--toss-grey-100)' }}>
                        <td colSpan={3} style={{ padding: '16px 20px' }}>
                          <PriceTiersPanel productId={product.id} regularPrice={product.price} />
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
  const [groups, setGroups] = useState<MenuOptionGroup[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [groupName, setGroupName] = useState('');
  const [choices, setChoices] = useState<ChoiceDraft[]>([{ name: '', priceDelta: '0' }, { name: '', priceDelta: '0' }]);
  const [submitting, setSubmitting] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    getOptionGroups(productId)
      .then(setGroups)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load option groups.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not add this option group.');
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
      setError(err instanceof ApiError ? err.message : 'Could not remove this option group.');
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <div>
        <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '8px' }}>Existing option groups</p>
        {groups === null ? (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
        ) : groups.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            No option groups yet -- a buyer will see a plain +/- stepper for this item until you add one (e.g. "Size" with Small/Regular/Large choices).
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {groups.map((group) => (
              <div key={group.id} className="toss-card" style={{ padding: '12px 16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{group.name}</p>
                    <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
                      {group.choices.map((c) => `${c.name}${c.priceDelta > 0 ? ` (+${c.priceDelta.toLocaleString()} RWF)` : ''}`).join(', ')}
                    </p>
                  </div>
                  <button
                    onClick={() => handleRemoveGroup(group.id)}
                    disabled={removingId === group.id}
                    style={{ color: 'var(--toss-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '12px' }}
                  >
                    <Trash2 size={12} /> {removingId === group.id ? 'Removing…' : 'Remove'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <form onSubmit={handleAddGroup} style={{ display: 'flex', flexDirection: 'column', gap: '10px', borderTop: '1px solid var(--toss-grey-200)', paddingTop: '14px' }}>
        <p style={{ fontSize: '13px', fontWeight: 700 }}>Add an option group</p>
        <input
          type="text" value={groupName} onChange={(e) => setGroupName(e.target.value)} placeholder="Group name (e.g. Size)" required
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', maxWidth: '320px' }}
        />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {choices.map((choice, i) => (
            <div key={i} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="text" value={choice.name} onChange={(e) => updateChoice(i, 'name', e.target.value)}
                placeholder={`Choice ${i + 1} (e.g. ${i === 0 ? 'Small' : 'Large'})`}
                style={{ flex: 2, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <input
                type="number" value={choice.priceDelta} onChange={(e) => updateChoice(i, 'priceDelta', e.target.value)}
                placeholder="+RWF" style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              {choices.length > 2 && (
                <button type="button" onClick={() => removeChoiceRow(i)} style={{ color: 'var(--toss-grey-500)' }} aria-label="Remove choice">
                  <Minus size={14} />
                </button>
              )}
            </div>
          ))}
          <button type="button" onClick={addChoiceRow} style={{ alignSelf: 'flex-start', color: 'var(--toss-blue)', fontSize: '12px', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <Plus size={12} /> Add another choice
          </button>
        </div>
        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
        )}
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ alignSelf: 'flex-start' }}>
          {submitting ? 'Adding…' : 'Add option group'}
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
  const [tiers, setTiers] = useState<TierDraft[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const load = () => {
    fetchPriceTiers(productId)
      .then((real) => setTiers(real.map((t) => ({ minQuantity: String(t.minQuantity), unitPrice: String(t.unitPrice) }))))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load price tiers.'));
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
    for (const t of parsed) {
      if (!Number.isInteger(t.minQuantity) || t.minQuantity < 1) {
        setError('Each minimum quantity must be a whole number of at least 1.');
        return;
      }
      if (!Number.isFinite(t.unitPrice) || t.unitPrice <= 0) {
        setError('Each unit price must be greater than zero.');
        return;
      }
      if (t.unitPrice >= regularPrice) {
        setError(`Each tier must cost less per unit than the regular price (${regularPrice.toLocaleString()} RWF).`);
        return;
      }
    }
    if (parsed.length > 10) {
      setError('Too many price tiers -- 10 is the real limit.');
      return;
    }
    setSaving(true);
    try {
      const real = await setPriceTiers(productId, parsed);
      setTiers(real.map((t) => ({ minQuantity: String(t.minQuantity), unitPrice: String(t.unitPrice) })));
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save these price tiers.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700 }}>Bulk/wholesale pricing</p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
        A buyer ordering at least the minimum quantity automatically pays the lower unit price at checkout -- real pricing, not a label. Leave empty for no bulk discount.
      </p>
      {tiers === null ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {tiers.map((tier, i) => (
            <div key={i} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="number" min="1" step="1" value={tier.minQuantity} onChange={(e) => updateTier(i, 'minQuantity', e.target.value)}
                placeholder="Min quantity (e.g. 10)"
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <input
                type="number" min="1" value={tier.unitPrice} onChange={(e) => updateTier(i, 'unitPrice', e.target.value)}
                placeholder="Unit price (RWF)"
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <button type="button" onClick={() => removeTierRow(i)} style={{ color: 'var(--toss-grey-500)' }} aria-label="Remove tier">
                <Minus size={14} />
              </button>
            </div>
          ))}
          <button type="button" onClick={addTierRow} style={{ alignSelf: 'flex-start', color: 'var(--toss-blue)', fontSize: '12px', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <Plus size={12} /> Add a tier
          </button>
        </div>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
      )}
      {saved && !error && (
        <p style={{ fontSize: '13px', color: 'var(--toss-blue)', margin: 0 }}>Saved.</p>
      )}
      <button type="button" className="toss-btn toss-btn-primary" disabled={saving || tiers === null} onClick={handleSave} style={{ alignSelf: 'flex-start' }}>
        {saving ? 'Saving…' : 'Save price tiers'}
      </button>
    </div>
  );
}
