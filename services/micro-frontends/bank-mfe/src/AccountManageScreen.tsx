import { useEffect, useState, type ReactNode } from 'react';
import { IconBack, IconChevronRight } from './icons/ItundaIcons';
import { setAccountNickname, type Account } from './lib/account';
import { ApiError, setPin } from './lib/api';
import { fetchProfile } from './lib/neighborhood';
import { fetchMyDevices, getOrCreateDeviceId } from './lib/device';
import { fetchTransferLimit, type TransferLimit } from './lib/p2p';
import { PinPad } from './PinPad';
import { useI18n } from './i18n/I18nContext';

// Real Toss Bank reference (5 more screenshots, 2026-08-23, direct user instruction:
// "this is what users should [see] when they click on manage in itunda bank"): the
// real Toss "관리" (Manage) screen is a comprehensive ~35-row account-settings hub
// (Profile/Security/Transfer/Documents/Taxes/Child/View/Notifications/Support/close
// account). Most of those rows are genuinely Korea-specific banking infrastructure or
// regulation itunda has no equivalent for -- Open Banking/firm banking (a Korean
// interbank data-sharing standard), tax-free limit (a Korean tax concept), telecom
// fraud information sharing (a Korean carrier-integration), ATM limit (itunda has no
// real ATM network), Credit Information Usage Policy (a specific Korean regulatory
// disclosure) -- honestly not reproduced here, not silently dropped: see the
// disclosure note at the bottom of this screen.
//
// What IS real and included: every row below routes to an already-built, real itunda
// screen or feature (never a fabricated destination) -- Card (CardView, just
// redesigned the same session), Devices (DevicesView, real device list + revoke,
// itunda's own real equivalent of "Device Change History"), Auto transfer
// (AutoTransfersCard on SavingsView), Delayed transfers (DelayedTransfersCard on
// PayHub, itunda's real 지연이체 anti-phishing hold-and-cancel feature), Exchange
// rates (the FOREIGN_CURRENCY tab's real rate cards), Bills (the real BILLS tab),
// Support (the real SUPPORT tab).
//
// Added 2026-09-01 (same real Toss reference, re-audited against what itunda already
// has): "View interest earned" (routes to the real Interest Jar bucket screen, never
// linked from here before), "Verification method" (real phoneVerified + real
// per-device publicKey state, not an invented method list), "Transfer limit" (the
// real, already-enforced P2pTransferLimitService caps, previously surfaced only
// reactively as a decline error).
//
// Real, named, deliberately NOT built this pass (unlike the Korea-specific rows
// above, these genuinely fit itunda's own account model but don't exist yet --
// flagged as honest gaps, not fabricated): closing your account (the only existing
// mechanism, SupportService's Account.isActive flip, is a fraud-response freeze
// triggered by support staff, not real self-service closing -- too large/risky for
// this pass) and a primary/default-account designation (isPrimary/isDefault don't
// exist anywhere; touches which account shows as default across potentially many
// screens). Also real and disclosed: MAIN account has no interest rate to display
// (only the separate Savings/Interest Jar accrues), and Contract documents/Get
// documents has no real PDF/statement generation backing it anywhere in this
// codebase yet.
//
// Added 2026-09-12 (12 more real Toss Manage-screen screenshots, re-audit against
// a fuller batch): "Account nickname" (a real, user-editable label, see
// AccountService.setNickname's own doc comment -- distinct from the fixed,
// system-assigned accountName) and "Change password" (PUT /api/v1/auth/pin
// already exists and already covers exactly this case -- re-proving the current
// credential to set a new one -- this was previously deferred only because
// building the UI flow felt like a rushed security-sensitive addition, not
// because the backend was missing; reuses PinSetupCard.tsx's exact
// current-credential/new/confirm shape, minus its one-time-only upgrade gate).
export function AccountManageScreen({ account, onBack, onNavigateToTab, onNicknameChanged }: { account: Account; onBack: () => void; onNavigateToTab: (tab: 'CARD' | 'SAVINGS' | 'PAY' | 'BILLS' | 'FOREIGN_CURRENCY' | 'DEVICES' | 'SUPPORT') => void; onNicknameChanged?: (nickname: string | null) => void }) {
  const go = (tab: 'CARD' | 'SAVINGS' | 'PAY' | 'BILLS' | 'FOREIGN_CURRENCY' | 'DEVICES' | 'SUPPORT') => {
    onBack();
    onNavigateToTab(tab);
  };
  const [showVerification, setShowVerification] = useState(false);
  const [showTransferLimit, setShowTransferLimit] = useState(false);
  const [showNickname, setShowNickname] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);

  if (showVerification) return <VerificationMethodScreen onBack={() => setShowVerification(false)} />;
  if (showTransferLimit) return <TransferLimitScreen onBack={() => setShowTransferLimit(false)} />;
  if (showNickname) {
    return (
      <AccountNicknameScreen
        account={account}
        onBack={() => setShowNickname(false)}
        onSaved={(nickname) => { onNicknameChanged?.(nickname); setShowNickname(false); }}
      />
    );
  }
  if (showChangePassword) return <ChangePasswordScreen onBack={() => setShowChangePassword(false)} />;

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1100, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 20px 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '14px 0' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px', marginLeft: '-4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>

        {/* Real gap found live (2026-08-31, direct user correction: "it's not itunda
            account number it's itunda bank account number"): this is always the
            primary itunda Bank account (never Pay/Youth/etc, see this screen's own
            caller), so the caption should say so, matching real Toss's own "토스뱅크
            1000-XXXX-XXXX" pattern. */}
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', margin: '4px 0 24px' }}>
          itunda Bank {account.accountNumber.match(/.{1,4}/g)?.join('-') ?? account.accountNumber}
        </p>

        <ManageSectionHeader title="Account" />
        <ManageRow label="Debit card" onClick={() => go('CARD')} />
        {/* Real "View interest earned" row (2026-09-01, direct user-supplied Toss
            Bank Manage-screen screenshot) -- the real Interest Jar bucket screen
            already exists (ItundaBankAssetsScreen), just never linked from here.
            Routes to SAVINGS, the same real home InterestJarCard already has. */}
        <ManageRow label="View interest earned" onClick={() => go('SAVINGS')} />
        <ManageRow label="Account nickname" onClick={() => setShowNickname(true)} />

        <ManageSectionHeader title="Security" />
        <ManageRow label="Manage devices" onClick={() => go('DEVICES')} />
        <ManageRow label="Verification method" onClick={() => setShowVerification(true)} />
        <ManageRow label="Change password" onClick={() => setShowChangePassword(true)} />

        <ManageSectionHeader title="Transfer" />
        <ManageRow label="Auto transfer" onClick={() => go('SAVINGS')} />
        <ManageRow label="Delayed transfers" onClick={() => go('PAY')} />
        <ManageRow label="Transfer limit" onClick={() => setShowTransferLimit(true)} />

        <ManageSectionHeader title="Foreign currency" />
        <ManageRow label="Exchange rates" onClick={() => go('FOREIGN_CURRENCY')} />

        <ManageSectionHeader title="Taxes & bills" />
        <ManageRow label="Pay taxes & bills" onClick={() => go('BILLS')} />

        <ManageSectionHeader title="Support" />
        <ManageRow label="Get help" onClick={() => go('SUPPORT')} />

        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '28px', lineHeight: 1.5 }}>
          Some Toss Bank account-management features (Open Banking, tax-free limits,
          ATM networks, closing your account) don&apos;t have a real itunda
          equivalent yet, and aren&apos;t shown here rather than being faked.
        </p>
      </div>
    </div>
  );
}

function ManageSectionHeader({ title }: { title: string }) {
  return (
    <h2 style={{ margin: '20px 0 6px', fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{title}</h2>
  );
}

function ManageRow({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button onClick={onClick} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left' }}>
      <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>{label}</span>
      <IconChevronRight size={18} color="var(--itunda-grey-400)" />
    </button>
  );
}

function ManageSubScreen({ title, onBack, children }: { title: string; onBack: () => void; children: ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1200, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 20px 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '14px 0' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px', marginLeft: '-4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>
        <h1 style={{ margin: '4px 0 20px', fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{title}</h1>
        {children}
      </div>
    </div>
  );
}

// Real "Verification method" screen (2026-09-01, direct user-supplied Toss Bank
// Manage-screen screenshot) -- shows exactly two real, already-tracked facts rather
// than a fabricated 2FA-method label: whether the phone on this account is verified
// (User.phoneVerified) and whether THIS device has completed real biometric/
// passwordless device-key registration (TrustedDevice.publicKey != null for the
// entry matching this browser's own real getOrCreateDeviceId()) -- itunda's real
// equivalent of "what verifies you," not an invented list of methods it doesn't have.
function VerificationMethodScreen({ onBack }: { onBack: () => void }) {
  const [phoneVerified, setPhoneVerified] = useState<boolean | null>(null);
  const [deviceVerified, setDeviceVerified] = useState<boolean | null>(null);

  useEffect(() => {
    fetchProfile().then((p) => setPhoneVerified(p.phoneVerified)).catch(() => setPhoneVerified(null));
    const deviceId = getOrCreateDeviceId();
    fetchMyDevices()
      .then((devices) => setDeviceVerified(devices.some((d) => d.deviceId === deviceId && d.publicKey != null)))
      .catch(() => setDeviceVerified(null));
  }, []);

  const row = (label: string, value: boolean | null) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
      <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>{label}</span>
      <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: value ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}>
        {value === null ? '…' : value ? 'Verified' : 'Not verified'}
      </span>
    </div>
  );

  return (
    <ManageSubScreen title="Verification method" onBack={onBack}>
      {row('Phone number', phoneVerified)}
      {row('This device', deviceVerified)}
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '16px', lineHeight: 1.5 }}>
        "This device" reflects real biometric/passwordless verification for the device you're using right now — set it up from Manage devices.
      </p>
    </ManageSubScreen>
  );
}

// Real "Transfer limit" screen (2026-09-01, direct user-supplied Toss Bank
// Manage-screen screenshot) -- the real, enforced P2pTransferLimitService caps,
// previously surfaced only reactively as a decline error on an oversized transfer.
function TransferLimitScreen({ onBack }: { onBack: () => void }) {
  const [limit, setLimit] = useState<TransferLimit | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    fetchTransferLimit().then(setLimit).catch(() => setError(true));
  }, []);

  return (
    <ManageSubScreen title="Transfer limit" onBack={onBack}>
      {error ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }}>Could not load your transfer limit.</p>
      ) : !limit ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>Per transfer</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{limit.perTransferLimit.toLocaleString('en-US')} RWF</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>Daily</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{limit.dailyLimit.toLocaleString('en-US')} RWF</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', padding: '12px 0' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>Remaining today</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: 'var(--itunda-indigo)' }}>{limit.remainingToday.toLocaleString('en-US')} RWF</span>
          </div>
        </>
      )}
    </ManageSubScreen>
  );
}

// Real "Account nickname" screen (2026-09-12, "계좌 별명" -- direct user-supplied
// Toss Bank Manage-screen screenshots) -- see AccountService.setNickname's own
// doc comment. A blank submission clears it back to unset, matching the backend's
// own convention.
function AccountNicknameScreen({ account, onBack, onSaved }: { account: Account; onBack: () => void; onSaved: (nickname: string | null) => void }) {
  const { t } = useI18n();
  const [input, setInput] = useState(account.nickname ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const updated = await setAccountNickname(account.id, input.trim());
      onSaved(updated.nickname);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ManageSubScreen title="Account nickname" onBack={onBack}>
      <form onSubmit={handleSave} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <input
          type="text"
          placeholder="e.g. My savings"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          maxLength={50}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
        />
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </form>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '16px', lineHeight: 1.5 }}>
        Leave this blank to remove your nickname -- your account will show as "itunda Bank" again.
      </p>
    </ManageSubScreen>
  );
}

// Real "Change password" screen (2026-09-12, direct user-supplied Toss Bank
// Manage-screen screenshots) -- PUT /api/v1/auth/pin already exists and already
// covers exactly this case (re-proving the current credential to set a new one);
// reuses PinSetupCard.tsx's exact current-credential/new/confirm shape and the
// same real PinPad component, minus its one-time-only "pinSet !== false" upgrade
// gate -- this is a general "change it again" flow reachable any time from here.
function ChangePasswordScreen({ onBack }: { onBack: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<'credential' | 'pin' | 'confirm' | 'success'>('credential');
  const [currentCredential, setCurrentCredential] = useState('');
  const [newPin, setNewPin] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submitCurrentCredential = (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentCredential.trim()) return;
    setError(null);
    setStep('pin');
  };

  const handleFirstPin = (pin: string) => {
    setNewPin(pin);
    setStep('confirm');
  };

  const handleConfirmPin = async (pin: string) => {
    if (pin !== newPin) {
      setError("That didn't match. Try again.");
      setStep('pin');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await setPin(currentCredential, pin);
      setStep('success');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      setStep('credential');
    } finally {
      setBusy(false);
    }
  };

  return (
    <ManageSubScreen title="Change password" onBack={onBack}>
      {step === 'success' ? (
        <>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-green)' }}>Your password has been updated.</p>
          <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={onBack}>Done</button>
        </>
      ) : (
        <>
          {step === 'credential' && (
            <form onSubmit={submitCurrentCredential} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <input
                type="password"
                placeholder="Your current password"
                value={currentCredential}
                onChange={(e) => setCurrentCredential(e.target.value)}
                required
                style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
              />
              <button type="submit" className="itunda-btn itunda-btn-primary">Continue</button>
            </form>
          )}
          {step === 'pin' && <PinPad label="Create a new 6-digit password" onComplete={handleFirstPin} error={error} disabled={busy} />}
          {step === 'confirm' && <PinPad label="Confirm your new password" onComplete={handleConfirmPin} error={error} disabled={busy} />}
        </>
      )}
    </ManageSubScreen>
  );
}
