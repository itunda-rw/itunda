import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { CreditCard, Minus, Plus, RefreshCw, Store, Trash2 } from 'lucide-react';
import { ApiError } from '../lib/api';
import {
  addProduct,
  chargeCard,
  generateQr,
  getProductCatalog,
  paymentIntentQrPayload,
  removeProduct,
  type CardChargeResult,
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
    setCart((prev) => {
      const existing = prev.find((line) => line.product.id === product.id);
      if (existing) {
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
                className="toss-card"
                style={{ padding: '16px', textAlign: 'left', cursor: 'pointer' }}
              >
                <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{product.name}</p>
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
                  {product.price.toLocaleString()} RWF
                </p>
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const charge = await chargeCard(amount, description, cardNumber.replace(/\s/g, ''), Number(expiryMonth), Number(expiryYear), cvc);
      setResult(charge);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not charge this card.');
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
      <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
        {submitting ? 'Charging…' : `Charge ${amount.toLocaleString()} RWF`}
      </button>
    </form>
  );
}

function CatalogView() {
  const [products, setProducts] = useState<MerchantProduct[] | null>(null);
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    getProductCatalog()
      .then(setProducts)
      .catch(() => setProducts([]));
  };

  useEffect(load, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await addProduct(name, Number(price));
      setName('');
      setPrice('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add this product.');
    } finally {
      setSubmitting(false);
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
              {products.map((product) => (
                <tr key={product.id} style={{ borderTop: '1px solid var(--toss-grey-200)' }}>
                  <td style={{ padding: '10px 20px', fontWeight: 600 }}>{product.name}</td>
                  <td style={{ padding: '10px 20px' }}>{product.price.toLocaleString()} RWF</td>
                  <td style={{ padding: '10px 20px', textAlign: 'right' }}>
                    <button
                      onClick={() => removeProduct(product.id).then(load)}
                      style={{ color: 'var(--toss-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px' }}
                    >
                      <Trash2 size={14} /> Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
