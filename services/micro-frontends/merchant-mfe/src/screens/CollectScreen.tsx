import { useState } from 'react';
import QRCode from 'qrcode';
import { CreditCard, RefreshCw, Ticket } from 'lucide-react';
import { ApiError } from '../lib/api';
import { chargeCard, generateQr, paymentIntentQrPayload, redeemGiftVoucher, type CardChargeResult, type PaymentIntent, type RedeemedGiftVoucher } from '../lib/merchant';
import { DeviceStepUpPrompt } from '../components/DeviceStepUpPrompt';

type Mode = 'QR' | 'CARD' | 'VOUCHER';

export default function CollectScreen() {
  const [mode, setMode] = useState<Mode>('QR');

  return (
    <div style={{ maxWidth: '400px' }}>
      <div className="toss-card" style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px' }}>
        {(['QR', 'CARD', 'VOUCHER'] as const).map((m) => (
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
            {m === 'QR' ? 'QR code' : m === 'CARD' ? 'Card' : 'Voucher'}
          </button>
        ))}
      </div>
      {mode === 'QR' ? <QrCollect /> : mode === 'CARD' ? <CardCollect /> : <VoucherRedeem />}
    </div>
  );
}

// Real KakaoTalk-style 기프티콘 (mobile gift voucher) merchant-side redemption
// (item 139) -- see lib/merchant.ts's own doc comment on redeemGiftVoucher: the
// customer shows the merchant their voucher (its real id, e.g. from their own itunda
// app), the merchant enters it here to redeem -- never a self-serve redeem the
// customer could fake.
function VoucherRedeem() {
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
        setError(err instanceof ApiError ? err.message : 'Could not redeem this voucher.');
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
      <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center' }}>
        <Ticket size={40} color="var(--toss-blue)" />
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Voucher redeemed</h2>
        <p style={{ fontSize: '18px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
          {result.productNameSnapshot ?? `${result.amount.toLocaleString()} RWF`}
        </p>
        <button className="toss-btn toss-btn-secondary" style={{ gap: '6px', padding: '10px 20px' }} onClick={reset}>
          <RefreshCw size={14} /> Redeem another voucher
        </button>
      </div>
    );
  }

  return (
    <div className="toss-card">
      {needsDeviceVerification ? (
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : (
        <>
          <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>Redeem a gift voucher</h2>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
            Ask the customer to show you their voucher, then enter its id here.
          </p>
          <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Voucher id</span>
              <input
                type="text"
                value={voucherId}
                onChange={(e) => setVoucherId(e.target.value)}
                placeholder="giftvoucher_..."
                required
                style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
              />
            </label>
            {error && (
              <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
                {error}
              </p>
            )}
            <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting || !voucherId.trim()}>
              {submitting ? 'Redeeming…' : 'Redeem'}
            </button>
          </form>
        </>
      )}
    </div>
  );
}

function QrCollect() {
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
      setError(err instanceof ApiError ? err.message : 'Could not generate a QR code.');
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
      <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center' }}>
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Show this to your customer</h2>
        <img src={qrDataUrl} alt="Payment QR code" width={240} height={240} style={{ borderRadius: '16px' }} />
        <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
          {intent.amount.toLocaleString()} RWF
        </p>
        <p style={{ fontSize: '14px', color: 'var(--toss-grey-500)' }}>{intent.description}</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          Expires {new Date(intent.expiresAt).toLocaleTimeString()}
        </p>
        <button className="toss-btn toss-btn-secondary" style={{ gap: '6px', padding: '10px 20px' }} onClick={reset}>
          <RefreshCw size={14} /> New QR code
        </button>
      </div>
    );
  }

  return (
    <div className="toss-card">
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '16px' }}>Collect a payment</h2>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Amount (RWF)</span>
          <input
            type="number"
            min="1"
            step="1"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="8000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Description</span>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="2x Coffee"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Generating…' : 'Generate QR code'}
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
        setError(err instanceof ApiError ? err.message : 'Could not charge this card.');
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
      <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px', padding: '32px', textAlign: 'center' }}>
        <CreditCard size={40} color="var(--toss-blue)" />
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Card charged</h2>
        <p style={{ fontSize: '24px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
          {result.amount.toLocaleString()} RWF
        </p>
        <p style={{ fontSize: '14px', color: 'var(--toss-grey-500)' }}>
          •••• {result.cardLast4} · fee {result.fee.toLocaleString()} RWF
        </p>
        <button className="toss-btn toss-btn-secondary" style={{ gap: '6px', padding: '10px 20px' }} onClick={reset}>
          <RefreshCw size={14} /> Charge another card
        </button>
      </div>
    );
  }

  return (
    <div className="toss-card">
      <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '4px' }}>Charge a card</h2>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        Demo authorization only — try 4242 4242 4242 4242 (approves) or 4000 0000 0000 0002 (declines).
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Amount (RWF)</span>
          <input
            type="number"
            min="1"
            step="1"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="8000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Description</span>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="2x Coffee"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Card number</span>
          <input
            type="text"
            inputMode="numeric"
            value={cardNumber}
            onChange={(e) => setCardNumber(e.target.value)}
            placeholder="4242 4242 4242 4242"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <div style={{ display: 'flex', gap: '12px' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Expiry month</span>
            <input
              type="number" min="1" max="12" value={expiryMonth} onChange={(e) => setExpiryMonth(e.target.value)}
              placeholder="12" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Expiry year</span>
            <input
              type="number" min="2026" value={expiryYear} onChange={(e) => setExpiryYear(e.target.value)}
              placeholder="2030" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>CVC</span>
            <input
              type="text" inputMode="numeric" value={cvc} onChange={(e) => setCvc(e.target.value)}
              placeholder="123" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
            />
          </label>
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
            {submitting ? 'Charging…' : 'Charge card'}
          </button>
        )}
      </form>
    </div>
  );
}
