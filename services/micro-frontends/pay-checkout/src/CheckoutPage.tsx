import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { useI18n } from './i18n/I18nContext';
import { LOCALES } from './i18n/translations';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:4001';

// Same real itunda://pay deep-link convention merchant-mfe/:merchantapp/:riderapp
// already establish (paymentIntentQrPayload in merchant-mfe's lib/merchant.ts) --
// a customer completing an external checkout scans the exact same real QR shape as
// any in-app-generated one, since it's the exact same PaymentIntent underneath.
const qrPayload = (paymentKey: string) => `itunda://pay?intentId=${paymentKey}`;

interface CheckoutInfo {
  paymentKey: string;
  merchantName: string;
  amount: number;
  description: string;
  status: 'PENDING' | 'COMPLETED' | 'EXPIRED';
  successUrl: string | null;
  failUrl: string | null;
}

// Real "Pay with itunda" hosted checkout page (2026-07-21) -- see
// PaymentsApiController.kt's own doc comment for the full account of the real Toss
// Payments feature this mirrors (Toss Payments' own hosted checkout page + real
// requestPayment() successUrl/failUrl redirect contract). Reads paymentKey from the
// URL path itself (no router library -- this is a single-page, single-route app, the
// same "don't add a dependency for one route" discipline as everywhere else in this
// codebase), fetches the real public checkout info (no API key -- a browser never
// holds the merchant's secret key), and polls for the real collect() flow completing
// via the itunda app.
export default function CheckoutPage() {
  const { t, locale, setLocale } = useI18n();
  const paymentKey = window.location.pathname.split('/checkout/')[1]?.split('/')[0];
  const [info, setInfo] = useState<CheckoutInfo | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchInfo = async () => {
    if (!paymentKey) {
      setError(t('checkout.noPaymentReference'));
      return;
    }
    try {
      const res = await fetch(`${BASE_URL}/api/v1/pay/checkout/${paymentKey}`);
      if (!res.ok) {
        setError(res.status === 404 ? t('checkout.linkInvalidOrExpired') : t('checkout.loadError'));
        return;
      }
      const body = await res.json();
      setInfo(body);
    } catch {
      setError(t('checkout.connectionError'));
    }
  };

  useEffect(() => {
    fetchInfo();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (paymentKey) {
      QRCode.toDataURL(qrPayload(paymentKey), { width: 240, margin: 1 }).then(setQrDataUrl);
    }
  }, [paymentKey]);

  // Real poll-until-complete, matching every other real QR-collection flow in this
  // codebase's own established pattern -- stops once a terminal status is reached, so
  // this doesn't keep polling forever after redirecting away.
  useEffect(() => {
    if (!info || info.status !== 'PENDING') return;
    const timer = setInterval(fetchInfo, 3000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [info?.status]);

  useEffect(() => {
    if (!info) return;
    if (info.status === 'COMPLETED' && info.successUrl) {
      const redirect = setTimeout(() => { window.location.href = info.successUrl!; }, 1200);
      return () => clearTimeout(redirect);
    }
    if (info.status === 'EXPIRED' && info.failUrl) {
      const redirect = setTimeout(() => { window.location.href = info.failUrl!; }, 1200);
      return () => clearTimeout(redirect);
    }
  }, [info]);

  if (error) {
    return (
      <div className="itunda-card" style={{ textAlign: 'center', padding: '28px' }}>
        <p style={{ fontSize: '15px', color: 'var(--itunda-red)' }}>{error}</p>
      </div>
    );
  }

  if (!info) {
    return <div className="skeleton" style={{ height: '360px', width: '100%', borderRadius: '16px' }} />;
  }

  return (
    <div className="itunda-card" style={{ textAlign: 'center', padding: '28px', width: '100%' }}>
      {/* Real first language switcher for this page (2026-08-15) -- see src/i18n's own
          doc comment: this is arguably itunda's single highest-reach real surface to
          localize, reached by any buyer via an external merchant link with no itunda
          login at all. */}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '8px' }}>
        <select
          value={locale}
          onChange={(e) => setLocale(e.target.value as 'en' | 'rw' | 'fr')}
          aria-label="Language"
          style={{ fontSize: '11px', padding: '3px 5px', borderRadius: '6px', border: '1px solid var(--itunda-grey-200)', color: 'var(--itunda-grey-700)', background: '#fff' }}
        >
          {LOCALES.map((l) => (
            <option key={l.code} value={l.code}>{l.label}</option>
          ))}
        </select>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>{t('checkout.payWith')}</p>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '2px' }}>{info.merchantName}</h2>
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>{info.description}</p>
      <p style={{ fontSize: '32px', fontWeight: 700, marginBottom: '20px' }}>{info.amount.toLocaleString('en-US')} RWF</p>

      {info.status === 'PENDING' && (
        <>
          {qrDataUrl && <img src={qrDataUrl} alt="Scan with the itunda app to pay" width={240} height={240} />}
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginTop: '16px' }}>
            {t('checkout.scanInstruction')}
          </p>
        </>
      )}

      {info.status === 'COMPLETED' && (
        <p style={{ fontSize: '15px', color: 'var(--itunda-green)', fontWeight: 700 }}>
          {t('checkout.paymentComplete')}{info.successUrl ? t('checkout.returningToMerchant') : ''}
        </p>
      )}

      {info.status === 'EXPIRED' && (
        <p style={{ fontSize: '15px', color: 'var(--itunda-red)', fontWeight: 700 }}>
          {t('checkout.linkExpired')}{info.failUrl ? t('checkout.returningToMerchant') : ''}
        </p>
      )}
    </div>
  );
}
