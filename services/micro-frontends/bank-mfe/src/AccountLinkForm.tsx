// Real external bank/MoMo account linking form -- extracted from OverviewAssetsView.tsx
// (itunda Pay redesign, 2026-08-28) so the new PayFundingSourcePicker's "Add account"
// row can reuse the exact same real LinkedAccountService flow instead of duplicating
// it a second time. Behavior unchanged from its original OverviewAssetsView home.

import { useState } from 'react';
import { ApiError } from './lib/api';
import { linkAccount, type LinkedAccount } from './lib/overview';
import { useI18n } from './i18n/I18nContext';
import { Button, Select, TextField } from '@itunda/design-system-web';

const LINK_PROVIDERS = ['MTN Mobile Money', 'Airtel Money', 'Bank of Kigali', 'Equity Bank Rwanda'];
// Real friction point found live via Toss Simplicity21 research (2026-08-08, session 2-1
// "신은 디테일에 있다" -- eliminating friction from a real bank-linking flow): for a MoMo
// provider, the "account number" IS the caller's own real phone number -- the same number
// they're already logged in with. Making them retype it is unnecessary friction with a
// real, already-known answer, the exact shape that session's own title names.
const MOMO_PROVIDERS = ['MTN Mobile Money', 'Airtel Money'];

export function AccountLinkForm({ myPhoneNumber, onLinked, onError }: { myPhoneNumber: string; onLinked: (linked: LinkedAccount) => void; onError: (message: string) => void }) {
  const { t } = useI18n();
  const [showForm, setShowForm] = useState(false);
  const [provider, setProvider] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [busy, setBusy] = useState(false);

  const handleLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const linked = await linkAccount(provider, accountNumber);
      const submittedProvider = provider;
      setProvider(''); setAccountNumber(''); setShowForm(false);
      onLinked(linked);
      // Real gap found via Toss Simplicity21 research (2026-08-08): a declined provider
      // verification is still a 200 response (the account is saved as VERIFICATION_FAILED
      // so it shows up in history) -- without this check the form just closed as if the
      // link had worked, and the only trace was the status text buried in the list below.
      if (linked.status === 'VERIFICATION_FAILED') {
        onError(linked.failureReason ?? t('overview.verificationFailed', { provider: submittedProvider }));
      }
    } catch (err) {
      onError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (!showForm) {
    return (
      <Button variant="primary" size="md" onClick={() => setShowForm(true)}>
        {t('overview.linkAccountPrompt')}
      </Button>
    );
  }

  return (
    <form onSubmit={handleLink} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <Select
        label="Provider"
        value={provider}
        onChange={(e) => setProvider(e.target.value)}
        options={LINK_PROVIDERS.map((p) => ({ value: p, label: p }))}
        required
      />
      <TextField
        label="Account number"
        type="text"
        value={accountNumber}
        onChange={(e) => setAccountNumber(e.target.value)}
        placeholder={t('overview.accountPhonePlaceholder')}
        required
      />
      <Button type="submit" variant="primary" size="md" disabled={busy}>{busy ? t('overview.linking') : t('overview.linkAccount')}</Button>
    </form>
  );
}
