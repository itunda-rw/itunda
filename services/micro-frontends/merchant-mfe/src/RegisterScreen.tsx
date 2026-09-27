import { useState } from 'react';
import { Store } from 'lucide-react';
import { ApiError } from './lib/api';
import { registerMerchant, type Merchant } from './lib/merchant';

export default function RegisterScreen({ onRegistered }: { onRegistered: (merchant: Merchant) => void }) {
  const [businessName, setBusinessName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const merchant = await registerMerchant(businessName);
      onRegistered(merchant);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not register your business. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <form
        onSubmit={handleSubmit}
        className="itunda-card"
        style={{ width: '400px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
          <Store size={24} color="var(--itunda-indigo)" />
          <h1 style={{ fontSize: '20px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Register your business</h1>
        </div>
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)', marginTop: '-8px' }}>
          This account isn't a merchant yet. Register your business to start collecting payments.
        </p>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Business name</span>
          {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11,
              rule #4), matching the identical same-day fix on bank-mfe's LoginPage.tsx. */}
          <input
            type="text"
            autoFocus
            value={businessName}
            onChange={(e) => setBusinessName(e.target.value)}
            placeholder="Amina's Boutique"
            required
            style={{
              padding: '12px 14px',
              borderRadius: '10px',
              border: '1px solid var(--itunda-grey-200)',
              fontSize: '15px',
            }}
          />
        </label>

        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert" aria-live="polite">
            {error}
          </p>
        )}

        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? 'Registering…' : 'Register'}
        </button>
      </form>
    </div>
  );
}
