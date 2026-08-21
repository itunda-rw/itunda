import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import { EmptyState } from '../components/EmptyState';
import { createBillingPlan, deactivateBillingPlan, fetchMyBillingPlans, type MerchantBillingPlan } from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';

// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing
// (item 144) -- see lib/merchant.ts's own doc comment. Merchant-owner-facing
// plan-management half only.
export default function BillingScreen() {
  const { t } = useI18n();
  const [plans, setPlans] = useState<MerchantBillingPlan[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyBillingPlans()
      .then(setPlans)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('billing.loadError')));
  };

  useEffect(load, []);

  return (
    <div style={{ maxWidth: '480px', display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <CreatePlanCard onCreated={load} />

      <div className="itunda-card">
        <h2 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>{t('billing.plansTitle')}</h2>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          {t('billing.plansBody')}
        </p>
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>
        )}
        {plans === null ? (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('billing.loading')}</p>
        ) : // Real copy-voice fix (item 244, round 6 of the empty-state pass): points
        // back to the real CreatePlanCard form right above.
        plans.length === 0 ? (
          <EmptyState message={t('billing.plansEmpty')} />
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
  const { t } = useI18n();
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
      setError(err instanceof ApiError ? err.message : t('billing.createError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: '16px', fontWeight: 700 }}>{t('billing.createTitle')}</h2>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('billing.planNameLabel')}</span>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder={t('billing.planNamePlaceholder')}
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('billing.descriptionLabel')}</span>
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder={t('billing.descriptionPlaceholder')}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
        />
      </label>
      <div style={{ display: 'flex', gap: '10px' }}>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('billing.amountLabel')}</span>
          <input
            type="number"
            min="1"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="8000"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>{t('billing.intervalLabel')}</span>
          <input
            type="number"
            min="1"
            value={intervalDays}
            onChange={(e) => setIntervalDays(e.target.value)}
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '15px' }}
          />
        </label>
      </div>
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
        {submitting ? t('billing.creating') : t('billing.createButton')}
      </button>
    </form>
  );
}

function PlanRow({ plan, onChanged }: { plan: MerchantBillingPlan; onChanged: () => void }) {
  const { t } = useI18n();
  const [deactivating, setDeactivating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDeactivate = async () => {
    setError(null);
    setDeactivating(true);
    try {
      await deactivateBillingPlan(plan.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('billing.deactivateError'));
    } finally {
      setDeactivating(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{plan.name}</p>
        <span style={{ fontSize: '12px', fontWeight: 600, color: plan.active ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}>
          {plan.active ? t('billing.statusActive') : t('billing.statusDeactivated')}
        </span>
      </div>
      {plan.description && <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>{plan.description}</p>}
      <p style={{ fontSize: '13px', color: 'var(--itunda-grey-700)' }}>
        {t(plan.intervalDays === 1 ? 'billing.everyDaySingular' : 'billing.everyDaysPlural', { amount: plan.amount.toLocaleString(), days: plan.intervalDays })}
      </p>
      {error && (
        <p style={{ fontSize: '12px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      {plan.active && (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ alignSelf: 'flex-start', padding: '6px 10px', fontSize: '12px', marginTop: '4px' }}
          disabled={deactivating}
          onClick={handleDeactivate}
        >
          {deactivating ? '…' : t('billing.deactivateButton')}
        </button>
      )}
    </div>
  );
}
