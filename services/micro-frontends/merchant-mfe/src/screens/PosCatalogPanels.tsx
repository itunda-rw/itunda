import { useEffect, useState } from 'react';
import { Minus, Plus, Trash2 } from 'lucide-react';
import { ApiError } from '../lib/api';
import {
  addOptionGroup, createTimeDeal, endTimeDeal, fetchMyTimeDeals, fetchPriceTiers,
  getOptionGroups, getProductAnalytics, removeOptionGroup, setPriceTiers,
  type MenuOptionGroup, type ProductAnalytics, type TimeDeal,
} from '../lib/merchant';
import { useI18n } from '../i18n/I18nContext';

interface ChoiceDraft {
  name: string;
  priceDelta: string;
}

// Real menu-item option-group management (2026-07-21) -- the merchant-facing half of
// docs/DESIGN_REFERENCES.md's Eats recommendation #3 (the buyer half, bank-mfe's own
// "Choose options" panel in MenuView, already existed). Before this, the only way to
// create an option group at all was a direct API call -- no UI anywhere. v1: required
// single-select only, matching MenuOptionGroup.kt's own real, honestly-scoped backend
// constraint (at least 2 choices per group, enforced server-side too).
// Real Coupang WING 상품분석 (product analytics) -- see lib/merchant.ts's own doc
// comment. Found via scripts/uncalled-endpoint-sweep.py: fully built with zero
// client anywhere.
export function ProductAnalyticsPanel({ productId }: { productId: string }) {
  const { t } = useI18n();
  const [analytics, setAnalytics] = useState<ProductAnalytics | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getProductAnalytics(productId)
      .then(setAnalytics)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pos.analyticsLoadError')));
  }, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  if (error) return <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p>;
  if (!analytics) return <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('pos.analyticsLoading')}</p>;

  return (
    <div style={{ display: 'flex', gap: '24px' }}>
      <div>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('pos.analyticsViewCount')}</p>
        <p style={{ fontSize: '20px', fontWeight: 700 }}>{analytics.viewCount.toLocaleString('en-US')}</p>
      </div>
      <div>
        <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>{t('pos.analyticsOrderCount')}</p>
        <p style={{ fontSize: '20px', fontWeight: 700 }}>{analytics.orderCount.toLocaleString('en-US')}</p>
      </div>
    </div>
  );
}

export function ProductOptionsPanel({ productId }: { productId: string }) {
  const { t } = useI18n();
  const [groups, setGroups] = useState<MenuOptionGroup[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [groupName, setGroupName] = useState('');
  const [choices, setChoices] = useState<ChoiceDraft[]>([{ name: '', priceDelta: '0' }, { name: '', priceDelta: '0' }]);
  const [submitting, setSubmitting] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    getOptionGroups(productId)
      .then(setGroups)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pos.optionGroupsLoadError')));
  };

  useEffect(load, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  const updateChoice = (index: number, field: keyof ChoiceDraft, value: string) => {
    setChoices((prev) => prev.map((c, i) => (i === index ? { ...c, [field]: value } : c)));
  };

  const addChoiceRow = () => setChoices((prev) => [...prev, { name: '', priceDelta: '0' }]);
  const removeChoiceRow = (index: number) => setChoices((prev) => prev.filter((_, i) => i !== index));

  const handleAddGroup = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const validChoices = choices
        .map((c) => ({ name: c.name.trim(), priceDelta: Number(c.priceDelta || 0) }))
        .filter((c) => c.name.length > 0);
      await addOptionGroup(productId, groupName.trim(), validChoices);
      setGroupName('');
      setChoices([{ name: '', priceDelta: '0' }, { name: '', priceDelta: '0' }]);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.addOptionGroupError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemoveGroup = async (groupId: string) => {
    setRemovingId(groupId);
    try {
      await removeOptionGroup(productId, groupId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.removeOptionGroupError'));
    } finally {
      setRemovingId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
      <div>
        <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '8px' }}>{t('pos.existingOptionGroups')}</p>
        {groups === null ? (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('pos.loading')}</p>
        ) : groups.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>
            {t('pos.optionGroupsEmpty')}
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {groups.map((group) => (
              <div key={group.id} className="itunda-card" style={{ padding: '12px 16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{group.name}</p>
                    <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
                      {group.choices.map((c) => `${c.name}${c.priceDelta > 0 ? ` (+${c.priceDelta.toLocaleString('en-US')} RWF)` : ''}`).join(', ')}
                    </p>
                  </div>
                  <button
                    onClick={() => handleRemoveGroup(group.id)}
                    disabled={removingId === group.id}
                    style={{ color: 'var(--itunda-grey-500)', display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '12px' }}
                  >
                    <Trash2 size={12} /> {removingId === group.id ? t('pos.removing') : t('pos.removeButton')}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <form onSubmit={handleAddGroup} style={{ display: 'flex', flexDirection: 'column', gap: '10px', borderTop: '1px solid var(--itunda-grey-200)', paddingTop: '14px' }}>
        <p style={{ fontSize: '13px', fontWeight: 700 }}>{t('pos.addOptionGroupTitle')}</p>
        <input
          type="text" value={groupName} onChange={(e) => setGroupName(e.target.value)} placeholder={t('pos.groupNamePlaceholder')} required
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: '14px', maxWidth: '320px' }}
        />
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {choices.map((choice, i) => (
            <div key={i} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="text" value={choice.name} onChange={(e) => updateChoice(i, 'name', e.target.value)}
                placeholder={t('pos.choicePlaceholder', { index: i + 1, example: i === 0 ? 'Small' : 'Large' })}
                style={{ flex: 2, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              <input
                type="number" value={choice.priceDelta} onChange={(e) => updateChoice(i, 'priceDelta', e.target.value)}
                placeholder={t('pos.choicePriceDeltaPlaceholder')} style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              {choices.length > 2 && (
                <button type="button" onClick={() => removeChoiceRow(i)} style={{ color: 'var(--itunda-grey-500)', padding: '5px' }} aria-label={t('pos.removeChoiceAria')}>
                  <Minus size={14} />
                </button>
              )}
            </div>
          ))}
          <button type="button" onClick={addChoiceRow} style={{ alignSelf: 'flex-start', color: 'var(--itunda-indigo)', fontSize: '12px', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <Plus size={12} /> {t('pos.addAnotherChoice')}
          </button>
        </div>
        {error && (
          <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
        )}
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ alignSelf: 'flex-start' }}>
          {submitting ? t('pos.adding') : t('pos.addOptionGroupButton')}
        </button>
      </form>
    </div>
  );
}

interface TierDraft {
  minQuantity: string;
  unitPrice: string;
}

// Real bulk/wholesale price tiers (item 149) -- see backend ProductPriceTier's own doc
// comment. First client UI for this endpoint on ANY platform (no bank-mfe/Android/iOS
// UI exists yet to have ported this from). Real checkout money impact: OrderService
// applies the highest-qualifying tier automatically at order time, so this is real
// pricing configuration, not a cosmetic label. Replace-all on save, mirroring
// ProductOptionsPanel's own add/remove-row editing pattern above; server-side
// validation (strictly increasing minQuantity, strictly decreasing unitPrice, each
// tier below the regular price, max 10 tiers) is the real source of truth -- this form
// mirrors those same rules client-side only for a faster error round trip.
export function PriceTiersPanel({ productId, regularPrice }: { productId: string; regularPrice: number }) {
  const { t } = useI18n();
  const [tiers, setTiers] = useState<TierDraft[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  const load = () => {
    fetchPriceTiers(productId)
      .then((real) => setTiers(real.map((tier) => ({ minQuantity: String(tier.minQuantity), unitPrice: String(tier.unitPrice) }))))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pos.priceTiersLoadError')));
  };

  useEffect(load, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  const updateTier = (index: number, field: keyof TierDraft, value: string) => {
    setSaved(false);
    setTiers((prev) => (prev ?? []).map((t, i) => (i === index ? { ...t, [field]: value } : t)));
  };

  const addTierRow = () => {
    setSaved(false);
    setTiers((prev) => [...(prev ?? []), { minQuantity: '', unitPrice: '' }]);
  };
  const removeTierRow = (index: number) => {
    setSaved(false);
    setTiers((prev) => (prev ?? []).filter((_, i) => i !== index));
  };

  const handleSave = async () => {
    setError(null);
    const parsed = (tiers ?? [])
      .filter((t) => t.minQuantity.trim() !== '' || t.unitPrice.trim() !== '')
      .map((t) => ({ minQuantity: Number(t.minQuantity), unitPrice: Number(t.unitPrice) }));
    for (const tier of parsed) {
      if (!Number.isInteger(tier.minQuantity) || tier.minQuantity < 1) {
        setError(t('pos.tierMinQuantityError'));
        return;
      }
      if (!Number.isFinite(tier.unitPrice) || tier.unitPrice <= 0) {
        setError(t('pos.tierUnitPriceError'));
        return;
      }
      if (tier.unitPrice >= regularPrice) {
        setError(t('pos.tierBelowRegularError', { price: regularPrice.toLocaleString('en-US') }));
        return;
      }
    }
    if (parsed.length > 10) {
      setError(t('pos.tierTooManyError'));
      return;
    }
    setSaving(true);
    try {
      const real = await setPriceTiers(productId, parsed);
      setTiers(real.map((tier) => ({ minQuantity: String(tier.minQuantity), unitPrice: String(tier.unitPrice) })));
      setSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.tierSaveError'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700 }}>{t('pos.bulkPricingTitle')}</p>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
        {t('pos.bulkPricingBody')}
      </p>
      {tiers === null ? (
        <p style={{ fontSize: '13px', color: 'var(--itunda-grey-500)' }}>{t('pos.loading')}</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {tiers.map((tier, i) => (
            <div key={i} style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
              <input
                type="number" min="1" step="1" value={tier.minQuantity} onChange={(e) => updateTier(i, 'minQuantity', e.target.value)}
                placeholder={t('pos.tierMinQuantityPlaceholder')}
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              <input
                type="number" min="1" value={tier.unitPrice} onChange={(e) => updateTier(i, 'unitPrice', e.target.value)}
                placeholder={t('pos.tierUnitPricePlaceholder')}
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
              />
              <button type="button" onClick={() => removeTierRow(i)} style={{ color: 'var(--itunda-grey-500)', padding: '5px' }} aria-label={t('pos.removeTierAria')}>
                <Minus size={14} />
              </button>
            </div>
          ))}
          <button type="button" onClick={addTierRow} style={{ alignSelf: 'flex-start', color: 'var(--itunda-indigo)', fontSize: '12px', fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
            <Plus size={12} /> {t('pos.addTier')}
          </button>
        </div>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      {saved && !error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-indigo)', margin: 0 }}>{t('pos.saved')}</p>
      )}
      <button type="button" className="itunda-btn itunda-btn-primary" disabled={saving || tiers === null} onClick={handleSave} style={{ alignSelf: 'flex-start' }}>
        {saving ? t('pos.saving') : t('pos.saveTiersButton')}
      </button>
    </div>
  );
}

// Real Coupang 타임특가 (Time Deal, item 226) -- see the backend TimeDeal.kt's own doc
// comment. A time-boxed, quantity-capped discount, distinct from PriceTiersPanel above
// (a permanent bulk-quantity discount, not a scheduled event). Consumer browse is
// already real on bank-mfe/Android/iOS; this is the first client for the creation half.
// GET /api/v1/time-deals/mine returns every deal across all of a merchant's products
// (no product-scoped endpoint exists), so this panel filters that list client-side to
// this one product -- an honest tradeoff for a merchant who rarely has more than a
// handful of deals running at once, not a real N+1 concern.
export function TimeDealPanel({ productId, regularPrice }: { productId: string; regularPrice: number }) {
  const { t } = useI18n();
  const [deals, setDeals] = useState<TimeDeal[] | null>(null);
  const [dealPrice, setDealPrice] = useState('');
  const [totalQuantity, setTotalQuantity] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [endsAt, setEndsAt] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [busyDealId, setBusyDealId] = useState<string | null>(null);

  const load = () => {
    fetchMyTimeDeals()
      .then((all) => setDeals(all.filter((d) => d.productId === productId)))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pos.timeDealsLoadError')));
  };

  useEffect(load, [productId]); // eslint-disable-line react-hooks/exhaustive-deps

  const now = Date.now();
  const activeDeals = (deals ?? []).filter((d) => new Date(d.endsAt).getTime() > now && d.remainingQuantity > 0);
  const pastDeals = (deals ?? []).filter((d) => !activeDeals.includes(d));

  const handleCreate = async () => {
    setError(null);
    const price = Number(dealPrice);
    const quantity = Number(totalQuantity);
    if (!Number.isFinite(price) || price <= 0 || price >= regularPrice) {
      setError(t('pos.timeDealPriceError', { price: regularPrice.toLocaleString('en-US') }));
      return;
    }
    if (!Number.isInteger(quantity) || quantity < 1) {
      setError(t('pos.timeDealQuantityError'));
      return;
    }
    if (!startsAt || !endsAt) {
      setError(t('pos.timeDealTimesRequiredError'));
      return;
    }
    const startIso = new Date(startsAt).toISOString();
    const endIso = new Date(endsAt).toISOString();
    if (new Date(endIso).getTime() <= new Date(startIso).getTime()) {
      setError(t('pos.timeDealEndAfterStartError'));
      return;
    }
    setSubmitting(true);
    try {
      await createTimeDeal(productId, price, quantity, startIso, endIso);
      setDealPrice('');
      setTotalQuantity('');
      setStartsAt('');
      setEndsAt('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.timeDealCreateError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleEnd = async (dealId: string) => {
    setBusyDealId(dealId);
    try {
      await endTimeDeal(dealId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('pos.timeDealEndError'));
    } finally {
      setBusyDealId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700 }}>{t('pos.timeDealTitle')}</p>
      <p style={{ fontSize: '12px', color: 'var(--itunda-grey-500)' }}>
        {t('pos.timeDealBody')}
      </p>
      {activeDeals.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {activeDeals.map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 10px', borderRadius: '8px', backgroundColor: 'var(--itunda-grey-200)' }}>
              <span style={{ fontSize: '13px' }}>
                {d.dealPrice.toLocaleString('en-US')} RWF · {d.remainingQuantity}/{d.totalQuantity} left · ends {new Date(d.endsAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
              </span>
              <button type="button" onClick={() => handleEnd(d.id)} disabled={busyDealId === d.id} style={{ color: 'var(--itunda-grey-500)', fontSize: '12px' }}>
                {busyDealId === d.id ? '…' : t('pos.timeDealEndNow')}
              </button>
            </div>
          ))}
        </div>
      ) : (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
          <input
            type="number" min="1" value={dealPrice} onChange={(e) => setDealPrice(e.target.value)} placeholder={t('pos.timeDealPricePlaceholder')}
            style={{ flex: 1, minWidth: '140px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
          <input
            type="number" min="1" step="1" value={totalQuantity} onChange={(e) => setTotalQuantity(e.target.value)} placeholder={t('pos.timeDealQuantityPlaceholder')}
            style={{ flex: 1, minWidth: '100px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
          <input
            type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)}
            style={{ flex: 1, minWidth: '160px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
          <input
            type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)}
            style={{ flex: 1, minWidth: '160px', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: '13px' }}
          />
        </div>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>
      )}
      {activeDeals.length === 0 && (
        <button type="button" className="itunda-btn itunda-btn-primary" disabled={submitting} onClick={handleCreate} style={{ alignSelf: 'flex-start' }}>
          {submitting ? t('pos.creating') : t('pos.startTimeDeal')}
        </button>
      )}
      {pastDeals.length > 0 && (
        <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>
          {t(pastDeals.length === 1 ? 'pos.pastDealsSingular' : 'pos.pastDealsPlural', { count: pastDeals.length })}
        </p>
      )}
    </div>
  );
}
