import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

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
  const paymentKey = window.location.pathname.split('/checkout/')[1]?.split('/')[0];
  const [info, setInfo] = useState<CheckoutInfo | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fetchInfo = async () => {
    if (!paymentKey) {
      setError('No payment reference in this link.');
      return;
    }
    try {
      const res = await fetch(`${BASE_URL}/api/v1/pay/checkout/${paymentKey}`);
      if (!res.ok) {
        setError(res.status === 404 ? 'This payment link is invalid or has expired.' : 'Could not load this payment.');
        return;
      }
      const body = await res.json();
      setInfo(body);
    } catch {
      setError('Could not reach itunda. Check your connection and try again.');
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
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
        <p style={{ fontSize: '15px', color: 'var(--toss-red)' }}>{error}</p>
      </div>
    );
  }

  if (!info) {
    return <div className="skeleton" style={{ height: '360px', width: '100%', borderRadius: '16px' }} />;
  }

  return (
    <div className="toss-card" style={{ textAlign: 'center', padding: '28px', width: '100%' }}>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '4px' }}>Pay with itunda</p>
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '2px' }}>{info.merchantName}</h2>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>{info.description}</p>
      <p style={{ fontSize: '32px', fontWeight: 700, marginBottom: '20px' }}>{info.amount.toLocaleString()} RWF</p>

      {info.status === 'PENDING' && (
        <>
          {qrDataUrl && <img src={qrDataUrl} alt="Scan with the itunda app to pay" width={240} height={240} />}
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginTop: '16px' }}>
            Scan this with the itunda app to complete payment
          </p>
        </>
      )}

      {info.status === 'COMPLETED' && (
        <p style={{ fontSize: '15px', color: 'var(--toss-green)', fontWeight: 700 }}>
          ✓ Payment complete{info.successUrl ? ' — returning to the merchant…' : ''}
        </p>
      )}

      {info.status === 'EXPIRED' && (
        <p style={{ fontSize: '15px', color: 'var(--toss-red)', fontWeight: 700 }}>
          This payment link has expired{info.failUrl ? ' — returning to the merchant…' : ''}
        </p>
      )}
    </div>
  );
}
