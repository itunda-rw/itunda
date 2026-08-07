import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import { createBillingPlan, deactivateBillingPlan, fetchMyBillingPlans, type MerchantBillingPlan } from '../lib/merchant';

// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing
// (item 144) -- see lib/merchant.ts's own doc comment. Merchant-owner-facing
// plan-management half only.
export default function BillingScreen() {
  const [plans, setPlans] = useState<MerchantBillingPlan[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyBillingPlans()
      .then(setPlans)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your billing plans.'));
  };

  useEffect(load, []);

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <CreatePlanCard onCreated={load} />

      <div className="toss-card">
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Your billing plans</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          A customer who subscribes is charged immediately, then again automatically every cycle until they cancel.
        </p>
        {error && (
          <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{error}</p>
        )}
        {plans === null ? (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
        ) : // Real copy-voice fix (item 244, round 6 of the empty-state pass): points
        // back to the real CreatePlanCard form right above.
        plans.length === 0 ? (
          <EmptyState message="No billing plans yet — use the form above to create your first one." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {plans.map((plan) => (
              <PlanRow key={plan.id} plan={plan} onChanged={load} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function CreatePlanCard({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [intervalDays, setIntervalDays] = useState('30');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createBillingPlan(name.trim(), description.trim() || undefined, Number(amount), Number(intervalDays));
      setName('');
      setDescription('');
      setAmount('');
      setIntervalDays('30');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this plan.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: '16px', fontWeight: 700 }}>Create a billing plan</h2>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Plan name</span>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Monthly coffee subscription"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Description (optional)</span>
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="One bag of beans, delivered monthly"
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
        />
      </label>
      <div style={{ display: 'flex', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Amount (RWF)</span>
          <input
            type="number"
            min="1"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="8000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Every (days)</span>
          <input
            type="number"
            min="1"
            value={intervalDays}
            onChange={(e) => setIntervalDays(e.target.value)}
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
        </label>
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
      )}
      <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create plan'}
      </button>
    </form>
  );
}

function PlanRow({ plan, onChanged }: { plan: MerchantBillingPlan; onChanged: () => void }) {
  const [deactivating, setDeactivating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDeactivate = async () => {
    setError(null);
    setDeactivating(true);
    try {
      await deactivateBillingPlan(plan.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not deactivate this plan.');
    } finally {
      setDeactivating(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{plan.name}</p>
        <span style={{ fontSize: '12px', fontWeight: 600, color: plan.active ? 'var(--toss-blue)' : 'var(--toss-grey-500)' }}>
          {plan.active ? 'Active' : 'Deactivated'}
        </span>
      </div>
      {plan.description && <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{plan.description}</p>}
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>
        {plan.amount.toLocaleString()} RWF every {plan.intervalDays} day{plan.intervalDays === 1 ? '' : 's'}
      </p>
      {error && (
        <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }} role="alert">{error}</p>
      )}
      {plan.active && (
        <button
          className="toss-btn toss-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '6px 10px', fontSize: '12px', marginTop: '4px' }}
          disabled={deactivating}
          onClick={handleDeactivate}
        >
          {deactivating ? '…' : 'Deactivate'}
        </button>
      )}
    </div>
  );
}
