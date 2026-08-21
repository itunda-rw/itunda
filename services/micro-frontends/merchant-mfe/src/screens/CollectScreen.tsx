import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { CreditCard, RefreshCw, Ticket } from 'lucide-react';
import { ApiError } from '../lib/api';
import { chargeCard, generateQr, getMyMerchant, paymentIntentQrPayload, redeemGiftVoucher, staticQrPayload, type CardChargeResult, type PaymentIntent, type RedeemedGiftVoucher } from '../lib/merchant';
import { DeviceStepUpPrompt } from '../components/DeviceStepUpPrompt';
import { useI18n } from '../i18n/I18nContext';

type Mode = 'QR' | 'STATIC' | 'CARD' | 'VOUCHER';

export default function CollectScreen() {
  const { t } = useI18n();
  const [mode, setMode] = useState<Mode>('QR');

  return (
    <div style={{ maxWidth: '400px' }}>
      <div className="itunda-card" style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px' }}>
        {(['QR', 'STATIC', 'CARD', 'VOUCHER'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              flex: 1,
              padding: '10px',
              borderRadius: '10px',
              fontSize: '13px',
              fontWeight: 700,
              color: mode === m ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: mode === m ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {m === 'QR' ? t('collect.modeQr') : m === 'STATIC' ? t('collect.modeStatic') : m === 'CARD' ? t('collect.modeCard') : t('collect.modeVoucher')}
          </button>
        ))}
      </div>
      {mode === 'QR' ? <QrCollect /> : mode === 'STATIC' ? <StaticQrCollect /> : mode === 'CARD' ? <CardCollect /> : <VoucherRedeem />}
    </div>
  );
}

// Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see lib/merchant.ts's own
// staticQrPayload doc comment. Genuinely distinct from QrCollect above: that generates a
// fresh, amount-preset code per sale; this one code is real-permanent -- print it once,
// a customer scans it and types in their own amount, no per-sale app interaction needed
// at all, matching Kakao's own real small-vendor (no POS) target use case.
function StaticQrCollect() {
  const { t } = useI18n();
  const [merchantId, setMerchantId] = useState<string | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getMyMerchant()
      .then((merchant) => {
        if (!merchant) { setError(t('collect.staticRegisterFirst')); return; }
        setMerchantId(merchant.id);
        return QRCode.toDataURL(staticQrPayload(merchant.id), { width: 240, margin: 1 }).then(setQrDataUrl);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('collect.staticLoadError')));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) {
    return (
      <div className="itunda-card">
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p>
      </div>
    );
  }

  if (!qrDataUrl || !merchantId) {
    return <div className="itunda-card skeleton" style={{ height: '320px' }} />;
  }

  return (
    <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center' }}>
      <h2 style={{ fontSize: '18px', fontWeight: 700 }}>{t('collect.staticTitle')}</h2>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
        {t('collect.staticBody')}
      </p>
      <img src={qrDataUrl} alt="Static merchant QR code" width={240} height={240} style={{ borderRadius: '16px' }} />
      <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)', fontFamily: 'monospace' }}>{merchantId}</p>
    </div>
  );
}

// Real KakaoTalk-style 기프티콘 (mobile gift voucher) merchant-side redemption
// (item 139) -- see lib/merchant.ts's own doc comment on redeemGiftVoucher: the
// customer shows the merchant their voucher (its real id, e.g. from their own itunda
// app), the merchant enters it here to redeem -- never a self-serve redeem the
// customer could fake.
function VoucherRedeem() {
  const { t } = useI18n();
  const [voucherId, setVoucherId] = useState('');
  const [result, setResult] = useState<RedeemedGiftVoucher | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    setResult(null);
    try {
      const voucher = await redeemGiftVoucher(voucherId.trim());
      setResult(voucher);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('collect.voucherRedeemError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const reset = () => {
    setResult(null);
    setVoucherId('');
  };

  if (result) {
    return (
      <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center' }}>
        <Ticket size={40} color="var(--itunda-indigo)" />
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>{t('collect.voucherRedeemedTitle')}</h2>
        <p style={{ fontSize: '18px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
          {result.productNameSnapshot ?? `${result.amount.toLocaleString()} RWF`}
        </p>
        <button className="itunda-btn itunda-btn-secondary" style={{ gap: '6px', padding: '10px 20px' }} onClick={reset}>
          <RefreshCw size={14} /> {t('collect.voucherRedeemAnother')}
        </button>
      </div>
    );
  }

  return (
    <div className="itunda-card">
      {needsDeviceVerification ? (
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : (
        <>
          <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{t('collect.voucherTitle')}</h2>
          <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
            {t('collect.voucherBody')}
          </p>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.voucherIdLabel')}</span>
              <input
                type="text"
                value={voucherId}
                onChange={(e) => setVoucherId(e.target.value)}
                placeholder="giftvoucher_..."
                required
                style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
              />
            </label>
            {error && (
              <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
                {error}
              </p>
            )}
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !voucherId.trim()}>
              {submitting ? t('collect.voucherRedeeming') : t('collect.voucherRedeemButton')}
            </button>
          </form>
        </>
      )}
    </div>
  );
}

function QrCollect() {
  const { t } = useI18n();
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [intent, setIntent] = useState<PaymentIntent | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    setQrDataUrl(null);
    try {
      const numericAmount = Number(amount);
      const newIntent = await generateQr(numericAmount, description);
      setIntent(newIntent);
      const dataUrl = await QRCode.toDataURL(paymentIntentQrPayload(newIntent.id), { width: 240, margin: 1 });
      setQrDataUrl(dataUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('collect.qrGenerateError'));
    } finally {
      setSubmitting(false);
    }
  };

  const reset = () => {
    setIntent(null);
    setQrDataUrl(null);
    setAmount('');
    setDescription('');
  };

  if (intent && qrDataUrl) {
    return (
      <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>{t('collect.qrShowTitle')}</h2>
        <img src={qrDataUrl} alt="Payment QR code" width={240} height={240} style={{ borderRadius: '16px' }} />
        <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
          {intent.amount.toLocaleString()} RWF
        </p>
        <p style={{ fontSize: '14px', color: 'var(--itunda-grey-500)' }}>{intent.description}</p>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
          {t('collect.qrExpiresPrefix')} {new Date(intent.expiresAt).toLocaleTimeString()}
        </p>
        <button className="itunda-btn itunda-btn-secondary" style={{ gap: '6px', padding: '10px 20px' }} onClick={reset}>
          <RefreshCw size={14} /> {t('collect.qrNew')}
        </button>
      </div>
    );
  }

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '16px' }}>{t('collect.qrTitle')}</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.amount')}</span>
          <input
            type="number"
            min="1"
            step="1"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="8000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.description')}</span>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="2x Coffee"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? t('collect.qrGenerating') : t('collect.qrGenerateButton')}
        </button>
      </form>
    </div>
  );
}

// Real demo card-processing UI -- backed by MerchantService.chargeCard's real Luhn
// validation + simulated authorization (see that file's own doc comment for why this
// is a genuine demo, not a real PSP integration, and why that's the honest, correct
// scope). itunda's own fixed demo test cards (same convention real PSPs like Stripe
// publish) let a real person testing this screen reliably see both outcomes.
function CardCollect() {
  const { t } = useI18n();
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
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
    setResult(null);
    try {
      const charge = await chargeCard(
        Number(amount), description, cardNumber.replace(/\s/g, ''), Number(expiryMonth), Number(expiryYear), cvc,
      );
      setResult(charge);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('collect.cardChargeError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const reset = () => {
    setResult(null);
    setAmount('');
    setDescription('');
    setCardNumber('');
    setExpiryMonth('');
    setExpiryYear('');
    setCvc('');
  };

  if (result) {
    return (
      <div className="itunda-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center' }}>
        <CreditCard size={40} color="var(--itunda-indigo)" />
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>{t('collect.cardChargedTitle')}</h2>
        <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
          {result.amount.toLocaleString()} RWF
        </p>
        <p style={{ fontSize: '14px', color: 'var(--itunda-grey-500)' }}>
          •••• {result.cardLast4} · fee {result.fee.toLocaleString()} RWF
        </p>
        <button className="itunda-btn itunda-btn-secondary" style={{ gap: '6px', padding: '10px 20px' }} onClick={reset}>
          <RefreshCw size={14} /> {t('collect.cardChargeAnother')}
        </button>
      </div>
    );
  }

  return (
    <div className="itunda-card">
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>{t('collect.cardTitle')}</h2>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        {t('collect.cardDemoNote')}
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.amount')}</span>
          <input
            type="number"
            min="1"
            step="1"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="8000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.description')}</span>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="2x Coffee"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.cardNumberLabel')}</span>
          <input
            type="text"
            inputMode="numeric"
            value={cardNumber}
            onChange={(e) => setCardNumber(e.target.value)}
            placeholder="4242 4242 4242 4242"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <div style={{ display: 'flex', gap: '12px' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.cardExpiryMonth')}</span>
            <input
              type="number" min="1" max="12" value={expiryMonth} onChange={(e) => setExpiryMonth(e.target.value)}
              placeholder="12" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.cardExpiryYear')}</span>
            <input
              type="number" min="2026" value={expiryYear} onChange={(e) => setExpiryYear(e.target.value)}
              placeholder="2030" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('collect.cardCvc')}</span>
            <input
              type="text" inputMode="numeric" value={cvc} onChange={(e) => setCvc(e.target.value)}
              placeholder="123" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
            />
          </label>
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
            {submitting ? t('collect.cardCharging') : t('collect.cardChargeButton')}
          </button>
        )}
      </form>
    </div>
  );
}
