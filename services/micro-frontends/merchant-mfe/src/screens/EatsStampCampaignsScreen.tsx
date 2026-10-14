import { useEffect, useState } from 'react';
import { ApiError } from '../lib/api';
import {
  createEatsStampCampaign,
  deactivateEatsStampCampaign,
  fetchEatsStampCampaigns,
  updateEatsStampCampaign,
  type EatsStampCampaign,
  type EatsStampCampaignInput,
} from '../lib/eats';

type Draft = {
  name: string;
  stampsRequired: string;
  rewardDescription: string;
  discountAmount: string;
  startsAt: string;
  endsAt: string;
};

const blankDraft = (): Draft => ({
  name: '',
  stampsRequired: '5',
  rewardDescription: '',
  discountAmount: '',
  startsAt: new Date(Date.now() - new Date().getTimezoneOffset() * 60_000).toISOString().slice(0, 16),
  endsAt: '',
});

function toLocalDateTime(value: string): string {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function toInput(draft: Draft): EatsStampCampaignInput {
  return {
    name: draft.name.trim(),
    stampsRequired: Number(draft.stampsRequired),
    rewardDescription: draft.rewardDescription.trim(),
    discountAmount: draft.discountAmount.trim() ? Number(draft.discountAmount) : null,
    startsAt: new Date(draft.startsAt).toISOString(),
    endsAt: draft.endsAt ? new Date(draft.endsAt).toISOString() : null,
    active: true,
  };
}

function fromCampaign(campaign: EatsStampCampaign): Draft {
  return {
    name: campaign.name,
    stampsRequired: String(campaign.stampsRequired),
    rewardDescription: campaign.rewardDescription,
    discountAmount: campaign.rewardDiscountAmount == null ? '' : String(campaign.rewardDiscountAmount),
    startsAt: toLocalDateTime(campaign.startsAt),
    endsAt: campaign.endsAt ? toLocalDateTime(campaign.endsAt) : '',
  };
}

export default function EatsStampCampaignsScreen({ restaurantId }: { restaurantId: string }) {
  const [campaigns, setCampaigns] = useState<EatsStampCampaign[] | null>(null);
  const [draft, setDraft] = useState<Draft>(blankDraft);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    setError(null);
    fetchEatsStampCampaigns(restaurantId)
      .then(setCampaigns)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load stamp campaigns. Try again.'));
  };

  useEffect(() => { load(); }, [restaurantId]);

  const change = (key: keyof Draft, value: string) => setDraft((current) => ({ ...current, [key]: value }));

  const save = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setNotice(null);
    const stamps = Number(draft.stampsRequired);
    const discount = draft.discountAmount.trim() ? Number(draft.discountAmount) : null;
    if (!draft.name.trim() || !draft.rewardDescription.trim()) {
      setError('Add a campaign name and describe the reward.');
      return;
    }
    if (!Number.isInteger(stamps) || stamps < 2 || stamps > 50) {
      setError('Choose between 2 and 50 stamps.');
      return;
    }
    if (discount !== null && (!Number.isFinite(discount) || discount <= 0)) {
      setError('The optional discount must be a positive amount in RWF.');
      return;
    }
    if (draft.endsAt && new Date(draft.endsAt).getTime() <= new Date(draft.startsAt).getTime()) {
      setError('The end date must be after the start date.');
      return;
    }
    setBusy(true);
    try {
      const payload = toInput(draft);
      if (editingId) await updateEatsStampCampaign(editingId, payload);
      else await createEatsStampCampaign(restaurantId, payload);
      setNotice(editingId ? 'Campaign updated.' : 'Campaign created.');
      setEditingId(null);
      setDraft(blankDraft());
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save the campaign. Try again.');
    } finally {
      setBusy(false);
    }
  };

  const edit = (campaign: EatsStampCampaign) => {
    setEditingId(campaign.id);
    setDraft(fromCampaign(campaign));
    setError(null);
    setNotice(null);
  };

  const deactivate = async (campaign: EatsStampCampaign) => {
    if (!window.confirm(`Pause “${campaign.name}”? Existing rewards will remain in customers’ accounts.`)) return;
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await deactivateEatsStampCampaign(campaign.id);
      setNotice('Campaign paused. Existing rewards were not removed.');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not pause the campaign. Try again.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
      <header style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <p style={{ color: 'var(--itunda-brand)', fontWeight: 700, fontSize: 12, letterSpacing: '.04em', textTransform: 'uppercase' }}>Itunda Eats</p>
        <h2 style={{ fontSize: 28, fontWeight: 750, color: 'var(--itunda-text-primary)' }}>Stamp rewards</h2>
        <p style={{ color: 'var(--itunda-text-secondary)', lineHeight: 1.5, maxWidth: 620 }}>
          Encourage repeat orders with a restaurant-funded reward. Customers earn stamps only after a qualifying order is delivered or picked up.
        </p>
        <p role="note" style={{ color: 'var(--itunda-text-secondary)', background: 'var(--itunda-surface-subtle)', padding: 12, borderRadius: 12, fontSize: 13, lineHeight: 1.5 }}>
          Reward redemption at checkout is not enabled yet. Campaigns and rewards are recorded, but a reward is not a discount until checkout redemption is implemented.
        </p>
      </header>

      {error && <p role="alert" style={{ color: 'var(--itunda-field-border-error)', fontSize: 14 }}>{error}</p>}
      {notice && <p role="status" style={{ color: 'var(--itunda-text-primary)', fontSize: 14 }}>{notice}</p>}

      <form onSubmit={save} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <h3 style={{ fontSize: 18, fontWeight: 700 }}>{editingId ? 'Edit campaign' : 'Create a campaign'}</h3>
        <label style={{ display: 'grid', gap: 6, fontSize: 13, fontWeight: 600 }}>
          Campaign name
          <input required maxLength={100} value={draft.name} onChange={(e) => change('name', e.target.value)} placeholder="e.g. Lunch regulars" style={fieldStyle} />
        </label>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
          <label style={{ display: 'grid', gap: 6, fontSize: 13, fontWeight: 600 }}>
            Stamps to unlock reward
            <input required type="number" min={2} max={50} step={1} value={draft.stampsRequired} onChange={(e) => change('stampsRequired', e.target.value)} style={fieldStyle} />
          </label>
          <label style={{ display: 'grid', gap: 6, fontSize: 13, fontWeight: 600 }}>
            Optional discount (RWF)
            <input type="number" min="1" step="1" value={draft.discountAmount} onChange={(e) => change('discountAmount', e.target.value)} placeholder="Leave blank for menu reward" style={fieldStyle} />
          </label>
        </div>
        <label style={{ display: 'grid', gap: 6, fontSize: 13, fontWeight: 600 }}>
          Reward description
          <input required maxLength={240} value={draft.rewardDescription} onChange={(e) => change('rewardDescription', e.target.value)} placeholder="e.g. Free side dish" style={fieldStyle} />
        </label>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12 }}>
          <label style={{ display: 'grid', gap: 6, fontSize: 13, fontWeight: 600 }}>
            Starts
            <input required type="datetime-local" value={draft.startsAt} onChange={(e) => change('startsAt', e.target.value)} style={fieldStyle} />
          </label>
          <label style={{ display: 'grid', gap: 6, fontSize: 13, fontWeight: 600 }}>
            Ends (optional)
            <input type="datetime-local" value={draft.endsAt} onChange={(e) => change('endsAt', e.target.value)} style={fieldStyle} />
          </label>
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? 'Saving…' : editingId ? 'Save changes' : 'Create campaign'}</button>
          {editingId && <button type="button" className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => { setEditingId(null); setDraft(blankDraft()); }}>Cancel edit</button>}
        </div>
      </form>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <h3 style={{ fontSize: 18, fontWeight: 700 }}>Your campaigns</h3>
        {campaigns === null ? <div className="itunda-card skeleton" style={{ height: 80 }} /> : campaigns.length === 0 ? (
          <p style={{ color: 'var(--itunda-text-secondary)', fontSize: 14 }}>No active campaigns yet. Create one to start building customer loyalty.</p>
        ) : campaigns.map((campaign) => (
          <article key={campaign.id} className="itunda-card" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                <strong style={{ color: 'var(--itunda-text-primary)', fontSize: 16 }}>{campaign.name}</strong>
                <span style={{ color: 'var(--itunda-text-secondary)', fontSize: 13 }}>{campaign.stampsRequired} stamps · {campaign.rewardDescription}</span>
                {campaign.rewardDiscountAmount != null && <span style={{ color: 'var(--itunda-text-secondary)', fontSize: 13 }}>{campaign.rewardDiscountAmount.toLocaleString()} RWF discount (not redeemable yet)</span>}
                <span style={{ color: 'var(--itunda-text-secondary)', fontSize: 12 }}>{new Date(campaign.startsAt).toLocaleString()}{campaign.endsAt ? ` – ${new Date(campaign.endsAt).toLocaleString()}` : ' · No end date'}</span>
              </div>
              <span style={{ borderRadius: 999, padding: '5px 9px', background: campaign.active ? 'var(--itunda-surface-brand)' : 'var(--itunda-surface-subtle)', color: campaign.active ? 'var(--itunda-brand)' : 'var(--itunda-text-secondary)', fontSize: 12, fontWeight: 700 }}>
                {campaign.active ? 'Active' : 'Paused'}
              </span>
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              <button type="button" className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => edit(campaign)}>Edit</button>
              {campaign.active && <button type="button" className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => deactivate(campaign)}>Pause campaign</button>}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

const fieldStyle: React.CSSProperties = {
  width: '100%',
  boxSizing: 'border-box',
  padding: '12px 14px',
  border: '1px solid var(--itunda-border-default)',
  borderRadius: 12,
  color: 'var(--itunda-text-primary)',
  background: 'var(--itunda-surface-default)',
  fontSize: 14,
  minHeight: 44,
};
