// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). The 4
// profile-management cards MyView.tsx renders first (notifications, profile photo,
// email/phone verification), each self-contained, paired in one file. Note:
// `fetchNotifications` stays ALSO imported in BankDashboard.tsx -- a settings-nav
// unread-badge check uses it too, a real shared dependency, not a leftover.

import { useEffect, useRef, useState } from 'react';
import { motion, useAnimation } from 'framer-motion';
import { Camera, Check } from 'lucide-react';
import { itundaSpring } from './lib/motion';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { fetchNotifications, markNotificationRead, markAllNotificationsRead, type NotificationItem } from './lib/notifications';
import { fetchProfile, updateProfilePhoto } from './lib/neighborhood';
import { uploadFile } from './lib/upload';
import { confirmEmailVerification, confirmPhoneVerification, requestEmailVerification, requestPhoneVerification } from './lib/verification';

// Real in-app notification inbox (item, found via a backend-module sweep) -- see
// lib/notifications.ts's own doc comment. Already ported to Android (SettingsScreen.kt)
// and iOS (SettingsViewModel.swift), but bank-mfe had zero client for the inbox itself.
export function NotificationsCard() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const load = () => {
    fetchNotifications()
      .then((r) => {
        setNotifications(r.notifications);
        setUnreadCount(r.unreadCount);
      })
      .catch(() => {});
  };
  useEffect(load, []);

  const handleRead = async (id: string) => {
    await markNotificationRead(id).catch(() => {});
    load();
  };
  const handleReadAll = async () => {
    await markAllNotificationsRead().catch(() => {});
    load();
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this widget
  // renders as one of many stacked sections on MyView's linear screen, not a
  // genuinely separate module (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Notifications</h3>
        {unreadCount > 0 && (
          <span
            role="button"
            onClick={handleReadAll}
            style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-indigo)', cursor: 'pointer' }}
          >
            Mark all read
          </span>
        )}
      </div>
      {notifications.length === 0 ? (
        <EmptyState message="You're all caught up — new activity will show up here." />
      ) : (
        notifications.slice(0, 10).map((n) => (
          <div
            key={n.id}
            role="button"
            tabIndex={0}
            onClick={() => !n.isRead && handleRead(n.id)}
            onKeyDown={(e) => { if ((e.key === 'Enter' || e.key === ' ') && !n.isRead) { e.preventDefault(); handleRead(n.id); } }}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '2px',
              padding: '10px 0',
              borderTop: '1px solid var(--itunda-grey-100)',
              cursor: n.isRead ? 'default' : 'pointer',
            }}
          >
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: n.isRead ? 400 : 700 }}>{n.title}</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{n.body}</span>
          </div>
        ))
      )}
    </div>
  );
}

// Real Toss 내 차 시세 (my car's market value) -- see lib/vehicles.ts's own doc comment
// for the full sourced account and honest scope boundary (a documented general
// depreciation estimate, not a real Carmart-style data partnership).
// Real email/phone verification (item 169) -- see lib/verification.ts's own doc
// comment. bank-mfe (the actual banking app) had zero client for either.
// Real profile photo upload (2026-08-13) -- found via the user's own "photo link vs.
// upload" UX audit request: the previous version of this card (2026-07-29, see git
// history) asked the user to paste a URL to a photo "hosted elsewhere," reasoning
// that itunda had no upload/storage pipeline to build a real picker on top of. That
// reasoning was stale even on the day it was written -- rw.itunda.marketplace.web.
// UploadController's own real `POST /api/v1/uploads` (multipart, validated,
// rate-limited local-disk storage) had existed since 2026-07-24, and this file's own
// Talk photo-message flow (handleSendPhoto, above) already used it. Profile photo
// was the one remaining "paste a link" holdout in bank-mfe; this wires it to the
// same real upload endpoint every other photo flow in this app already uses.
export function ProfilePhotoCard() {
  const { t } = useI18n();
  const [profilePhotoUrl, setProfilePhotoUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    fetchProfile()
      .then((u) => setProfilePhotoUrl(u.profilePhotoUrl))
      .catch(() => {
        // Real, non-critical -- the rest of "My" still works without this.
      });
  }, []);

  const handleFileSelected = async (file: File | undefined) => {
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      const user = await updateProfilePhoto(url);
      setProfilePhotoUrl(user.profilePhotoUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleFileSelected(e.target.files?.[0])}
      />
      <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
          style={{ width: '56px', height: '56px', borderRadius: '28px', flexShrink: 0, padding: 0, border: 'none', overflow: 'hidden', opacity: uploading ? 0.5 : 1 }}
          aria-label={profilePhotoUrl ? 'Change profile photo' : 'Add profile photo'}
        >
          {profilePhotoUrl ? (
            <img src={profilePhotoUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <div style={{ width: '100%', height: '100%', backgroundColor: 'var(--itunda-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Camera size={20} color="var(--itunda-grey-500)" />
            </div>
          )}
        </button>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: 'var(--itunda-grey-900)', margin: 0 }}>Profile photo</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '2px 0 0' }}>
            {uploading ? 'Uploading…' : 'Tap to choose a photo from your device'}
          </p>
        </div>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Toss-sourced passwordless-login rollout (2026-08-24) -- see backend
// User.pinSet's own doc comment. A real, non-blocking upgrade prompt for a
// pre-PIN-era user -- their existing password keeps working exactly as before either
// way (AuthService.login is shape-agnostic); this is purely an offered convenience,
// never forced. `currentCredential` is a plain text field (their existing password
// could be any shape, not necessarily 6 digits, so PinPad doesn't apply there);
// `newPin`/confirm reuse the real PinPad component RegisterPage/LoginPage already
// established.
export function VerificationCard() {
  const [status, setStatus] = useState<{ email: string | null; emailVerified: boolean; phoneVerified: boolean } | null>(null);

  const load = () => {
    fetchProfile().then((u) => setStatus({ email: u.email, emailVerified: u.emailVerified, phoneVerified: u.phoneVerified })).catch(() => {});
  };
  useEffect(load, []);

  if (!status || (status.emailVerified && status.phoneVerified)) return null;

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a lone
  // conditional section on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Verify your account</h3>
      {!status.phoneVerified && <VerificationRow kind="phone" onVerified={load} />}
      {!status.emailVerified && <VerificationRow kind="email" hasEmail={status.email !== null} onVerified={load} />}
    </div>
  );
}

export function VerificationRow({ kind, hasEmail = true, onVerified }: { kind: 'email' | 'phone'; hasEmail?: boolean; onVerified: () => void }) {
  const { t } = useI18n();
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [verified, setVerified] = useState(false);
  const shakeControls = useAnimation();

  const handleSend = async () => {
    setBusy(true);
    setError(null);
    try {
      await (kind === 'email' ? requestEmailVerification() : requestPhoneVerification());
      setSent(true);
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): only
      // reachable via stale client state (verified on another device/tab between
      // this row rendering and the tap) -- not really a failure, resolve forward by
      // refreshing so this row correctly disappears.
      if (err instanceof ApiError && (err.code === 'EMAIL_ALREADY_VERIFIED' || err.code === 'PHONE_ALREADY_VERIFIED')) {
        onVerified();
      } else {
        setError(err instanceof ApiError ? err.message : `Could not send a ${kind} verification code.`);
      }
    } finally {
      setBusy(false);
    }
  };

  const handleConfirm = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (busy || !code.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await (kind === 'email' ? confirmEmailVerification(code.trim()) : confirmPhoneVerification(code.trim()));
      // Real Toss-style "OTP Successful Animation" (60fps.design's own real
      // catalog of Toss's named interactions) -- a brief green checkmark moment
      // before navigating away, reusing TransferFlow's own established success-
      // checkmark language (itunda-green circle + white Check, spring scale-in)
      // rather than calling onVerified() instantly with zero feedback, which is
      // what every platform did before this pass.
      setVerified(true);
      setTimeout(onVerified, 650);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      // Real Toss-style wrong-code shake, matching PinPad's own already-
      // established error-shake pattern (itunda had it on the PIN pad but never
      // on this OTP field).
      shakeControls.start({ x: [0, -8, 8, -8, 8, 0], transition: { duration: 0.4 } });
    } finally {
      setBusy(false);
    }
  };

  // Real "Minimum Input" simplicity fix (Toss's own researched, sourced pattern --
  // toss.tech/article/4-ways-for-minimum-input, rule #2: "for fixed-digit fields like
  // ID or phone numbers, the CTA button becomes unnecessary" -- see
  // docs/DESIGN_REFERENCES.md §11). This code is a real, fixed 6-digit OTP
  // (AuthService.kt's own doc comment). Auto-confirms the instant the 6th digit is
  // typed; the button stays visible as a manual fallback rather than being removed
  // outright, since this is a security-sensitive identity-verification step.
  useEffect(() => {
    const trimmed = code.trim();
    if (trimmed.length === 6 && /^\d{6}$/.test(trimmed) && !busy) handleConfirm();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code]);

  if (kind === 'email' && !hasEmail) {
    return <EmptyState message="No email address on file to verify." />;
  }

  return (
    <div style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
      {verified ? (
        <motion.div
          initial={{ scale: 0.4, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: 'spring', ...itundaSpring.medium }}
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <span style={{ width: '22px', height: '22px', borderRadius: '11px', backgroundColor: 'var(--itunda-green)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Check size={14} color="#ffffff" />
          </span>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-green)' }}>
            {kind === 'email' ? 'Email verified' : 'Phone number verified'}
          </span>
        </motion.div>
      ) : !sent ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{kind === 'email' ? 'Email' : 'Phone number'} not verified</span>
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleSend} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
            {busy ? '…' : 'Send code'}
          </button>
        </div>
      ) : (
        <motion.form onSubmit={handleConfirm} animate={shakeControls} style={{ display: 'flex', gap: '8px' }}>
          <motion.input
            type="text" inputMode="numeric" pattern="[0-9]*" autoFocus placeholder="Enter code" value={code} onChange={(e) => setCode(e.target.value)} required
            animate={busy ? { opacity: [1, 0.55, 1] } : { opacity: 1 }}
            transition={busy ? { duration: 0.9, repeat: Infinity, ease: 'easeInOut' } : undefined}
            style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          {/* Real CTA-label-clarity fix (2026-08-24, docs/DESIGN_REFERENCES.md §11 --
              picked up the explicitly-flagged "not audited this pass" recommendation:
              cross-reference generic Confirm/Submit/OK labels against their real
              action). A bare "Confirm" doesn't state the outcome; states the specific
              action instead, matching the fix already applied to TransferFlow's own
              identical bare-"Confirm" button nearby. */}
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}>
            {busy ? '…' : kind === 'email' ? 'Verify email' : 'Verify phone number'}
          </button>
        </motion.form>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}
