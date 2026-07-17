import { useState } from 'react';
import QRCode from 'qrcode';
import { RefreshCw } from 'lucide-react';
import { ApiError } from '../lib/api';
import { generateQr, paymentIntentQrPayload, type PaymentIntent } from '../lib/merchant';

export default function CollectScreen() {
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
    <div className="toss-card" style={{ maxWidth: '400px' }}>
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
