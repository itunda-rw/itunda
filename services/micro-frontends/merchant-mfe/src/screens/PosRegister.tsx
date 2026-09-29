import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { CreditCard, Minus, Plus, RefreshCw, Store } from 'lucide-react';
import { EmptyState } from '../components/EmptyState';
import { ApiError } from '../lib/api';
import { DeviceStepUpPrompt } from '../components/DeviceStepUpPrompt';
import {
  chargeCard, generateQr, getProductCatalog, paymentIntentQrPayload,
  type CardChargeResult, type MerchantProduct, type PaymentIntent,
} from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';

export interface CartLine {
  product: MerchantProduct;
  quantity: number;
}

export function RegisterView() {
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
                  {product.price.toLocaleString('en-US')} RWF
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
            <Store size={18} color="var(--itunda-indigo)" />
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
                      {(line.product.price * line.quantity).toLocaleString('en-US')} RWF
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
              <span style={{ fontSize: '18px', fontWeight: 700 }}>{total.toLocaleString('en-US')} RWF</span>
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
      <p style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}>{total.toLocaleString('en-US')} RWF</p>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>{description}</p>

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
        {(['QR', 'CARD'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setCheckoutMode(m)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: checkoutMode === m ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: checkoutMode === m ? 'var(--itunda-indigo)' : 'transparent',
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
        <CreditCard size={32} color="var(--itunda-indigo)" style={{ marginBottom: '8px' }} />
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
          {submitting ? t('pos.charging') : t('pos.chargeButton', { amount: amount.toLocaleString('en-US') })}
        </button>
      )}
    </form>
  );
}

