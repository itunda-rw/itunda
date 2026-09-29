import { useState } from 'react';
import { IconShieldCheck } from './icons/ItundaIcons';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { IdsButton } from './IdsButton';
import { QrScanCamera, parseQrParam } from './QrScanCamera';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { collectWithFacePay } from './lib/facepay';
import {
  collectPayment, previewPaymentIntent, fetchLoyaltyBalance, payByStaticQr,
  type CollectPaymentResult, type PaymentIntentPreview, type MerchantCouponView,
} from './lib/shopping';

function couponDiscountLabel(c: MerchantCouponView['coupon']) {
  return c.discountType === 'PERCENT' ? `${c.discountValue}% off` : `${c.discountValue.toLocaleString('en-US')} RWF off`;
}

export function PayByCodeCard({ onPaid, facePayEnrolled }: { onPaid: (result: CollectPaymentResult) => void; facePayEnrolled: boolean }) {
  const { t } = useI18n();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real device binding (2026-07-20) -- found while wiring Face Pay into this card:
  // like TransferFlow, a code payment carries a real Idempotency-Key and can real-403
  // with DEVICE_NOT_VERIFIED on a device's first money-moving action, but this card
  // never handled it -- it just showed the raw error string with no actionable next
  // step. Same fix as TransferFlow/Savings: a real step-up prompt, not a dead end.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  // Real coupon-apply-at-payment (item 149) -- see lib/shopping.ts's own doc comment
  // on previewPaymentIntent. Only reachable on the non-Face-Pay path: FacePayService's
  // own collect() has no couponId param at all (a real, separate, smaller gap), so
  // Face Pay stays a direct one-step pay exactly as before.
  const [preview, setPreview] = useState<PaymentIntentPreview | null>(null);
  const [eligibleCoupons, setEligibleCoupons] = useState<MerchantCouponView[]>([]);
  const [selectedCouponId, setSelectedCouponId] = useState<string | null>(null);
  // Real Toss Place-style loyalty balance check (see lib/shopping.ts's own doc
  // comment on fetchLoyaltyBalance) -- shown alongside the coupon picker in this same
  // pre-payment preview step, since it's the identical real moment a customer would
  // want to know before choosing to redeem.
  const [loyaltyBalance, setLoyaltyBalance] = useState(0);
  const [redeemPoints, setRedeemPoints] = useState(false);

  // Real camera-scan support (2026-08-19): scanning sets `code` state AND passes the
  // scanned value straight through as an explicit param, since a scan's payDirect/
  // preview call happens in the same tick as setCode and can't rely on the (still stale)
  // `code` closure -- the typed-code path still reads from state via handleConfirm below,
  // which only ever runs after a real render (the preview step) so state is fresh there.
  //
  // Real bug fix (product-feel audit, §234): `manualEntry` used to be gated behind
  // `!facePayEnrolled` everywhere it was checked, so a Face-Pay-enrolled user could
  // never reach the camera at all -- typed-code entry was their ONLY path, a direct
  // violation of the standing "no manual codes" law (see
  // [[feedback_no_manual_codes_ux]]: scan/QR beats typing whenever both exist).
  // Face Pay only changes how the payment is AUTHORIZED after a code is found (face
  // vs. nothing extra), not how the code itself is captured -- scanning and Face Pay
  // are orthogonal, so `manualEntry` alone (not `facePayEnrolled`) now decides which
  // capture mode renders.
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualEntry, setManualEntry] = useState(false);

  const payDirect = async (rawCode: string, couponId?: string, pointsToRedeem?: number) => {
    setNeedsDeviceVerification(false);
    setSubmitting(true);
    try {
      const result = facePayEnrolled ? await collectWithFacePay(rawCode) : await collectPayment(rawCode, couponId, pointsToRedeem);
      onPaid(result);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submitCode = async (rawCode: string) => {
    const trimmed = rawCode.trim();
    if (!trimmed) return;
    setCode(trimmed);
    setError(null);
    if (facePayEnrolled) {
      await payDirect(trimmed);
      return;
    }
    setSubmitting(true);
    try {
      const r = await previewPaymentIntent(trimmed);
      const eligible = r.coupons.filter((c) => c.eligible && !c.alreadyRedeemed);
      const balance = await fetchLoyaltyBalance(r.merchantId).catch(() => 0);
      if (eligible.length === 0 && balance <= 0) {
        await payDirect(trimmed);
      } else {
        setPreview(r);
        setEligibleCoupons(eligible);
        setLoyaltyBalance(balance);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      setSubmitting(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    void submitCode(code);
  };

  const handleScan = (raw: string) => void submitCode(parseQrParam(raw, 'intentId'));

  // Real "naming complex conditions" / "simplify ternary operators" fix (2026-08-19) --
  // Toss's own real Frontend Fundamentals guide (frontend-fundamentals.com/code-quality/
  // code), directly requested via "deep search... make sure we don't have that weird vibe
  // coded code and architecture mistakes." This used to be a nested ternary crossing
  // `submitting`/`facePayEnrolled` inline in the render -- readable as 4 unnamed branches
  // is exactly what that guide calls out; an if-chain (its own suggested refactor target)
  // reads top-to-bottom instead of requiring the reader to track two crossed booleans.
  let payButtonLabel: string;
  if (submitting && facePayEnrolled) payButtonLabel = 'Authorizing…';
  else if (submitting) payButtonLabel = 'Paying…';
  else if (facePayEnrolled) payButtonLabel = '😊 Pay';
  else payButtonLabel = 'Pay';

  // Real cap: MerchantLoyaltyPointsService.validateAndComputeRedemption's own real
  // rule is min(pointsToRedeem, paymentAmount) -- mirrored here so the client sends
  // exactly what the backend would actually apply, not an inflated request.
  const redeemableAmount = Math.min(loyaltyBalance, preview?.amount ?? 0);
  const handleConfirm = () => payDirect(code.trim(), selectedCouponId ?? undefined, redeemPoints ? redeemableAmount : undefined);

  const handleCancel = () => {
    setPreview(null);
    setEligibleCoupons([]);
    setSelectedCouponId(null);
    setLoyaltyBalance(0);
    setRedeemPoints(false);
    setError(null);
  };

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>{manualEntry ? 'Pay by code' : 'Scan to pay'}</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        {manualEntry
          ? (facePayEnrolled ? 'Enter the code the merchant shows you to authorize with your face.' : 'Enter the payment code the merchant shows you.')
          : (facePayEnrolled ? 'Point your camera at the merchant\'s QR code — you\'ll confirm with your face.' : 'Point your camera at the merchant\'s QR code to pay instantly and earn cashback.')}
      </p>
      {needsDeviceVerification ? (
        // Real fix (2026-08-10) -- see TransferFlow's own identical fix for the full
        // account. handleConfirm -> payDirect resets needsDeviceVerification itself.
        <DeviceStepUpPrompt onVerified={handleConfirm} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : !manualEntry && !preview ? (
        <>
          {!scanUnavailable && !submitting && (
            <QrScanCamera onDetect={handleScan} onUnavailable={() => setScanUnavailable(true)} />
          )}
          {submitting && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>Looking up code…</p>
          )}
          <button
            type="button"
            className="itunda-btn itunda-btn-secondary"
            style={{ width: '100%' }}
            onClick={() => setManualEntry(true)}
          >
            {scanUnavailable ? 'Enter code manually' : 'No camera? Enter code instead'}
          </button>
        </>
      ) : preview ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{preview.businessName}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700 }}>{preview.amount.toLocaleString('en-US')} RWF</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Apply a coupon?</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <input type="radio" name="coupon" checked={selectedCouponId === null} onChange={() => setSelectedCouponId(null)} />
              No coupon
            </label>
            {eligibleCoupons.map((c) => (
              <label key={c.coupon.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                <input type="radio" name="coupon" checked={selectedCouponId === c.coupon.id} onChange={() => setSelectedCouponId(c.coupon.id)} />
                {c.coupon.title} — {couponDiscountLabel(c.coupon)}
              </label>
            ))}
          </div>
          {loyaltyBalance > 0 && (
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <input type="checkbox" checked={redeemPoints} onChange={(e) => setRedeemPoints(e.target.checked)} />
              Use my {loyaltyBalance.toLocaleString('en-US')} points ({redeemableAmount.toLocaleString('en-US')} RWF off)
            </label>
          )}
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
          <div style={{ display: 'flex', gap: '10px' }}>
            <IdsButton fullWidth style={{ flex: 1 }} disabled={submitting} onClick={handleConfirm}>
              {submitting ? 'Paying…' : 'Pay'}
            </IdsButton>
            <IdsButton variant="tinted" onClick={handleCancel} disabled={submitting}>Cancel</IdsButton>
          </div>
        </div>
      ) : (
        <>
          <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
            <input
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="Payment code"
              required
              autoFocus
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
              {payButtonLabel}
            </button>
          </form>
          {!scanUnavailable && (
            <button
              type="button"
              className="itunda-btn itunda-btn-secondary"
              style={{ width: '100%', marginTop: '10px' }}
              onClick={() => setManualEntry(false)}
            >
              Scan a QR code instead
            </button>
          )}
        </>
      )}
      {error && !preview && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

// Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see lib/shopping.ts's own
// payByStaticQr doc comment. Genuinely distinct from PayByCodeCard above: that pays a
// merchant-preset amount off a fresh per-sale code; this pays a merchant's own
// permanent merchantId with the CUSTOMER choosing the amount, matching Kakao's own real
// small-vendor use case (a market stall's one printed, unchanging code).
export function PayByStaticQrCard({ onPaid }: { onPaid: (result: CollectPaymentResult) => void }) {
  const { t } = useI18n();
  const [merchantId, setMerchantId] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualEntry, setManualEntry] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const numericAmount = Number(amount);
    if (!merchantId.trim()) { setError('Scan or enter the merchant\'s code first.'); return; }
    if (!numericAmount || numericAmount <= 0) { setError('Enter a valid amount.'); return; }
    setSubmitting(true);
    try {
      const result = await payByStaticQr(merchantId.trim(), numericAmount);
      onPaid(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleScan = (raw: string) => {
    setMerchantId(parseQrParam(raw, 'merchantId'));
    setManualEntry(true);
  };

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Pay a merchant's static QR</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        For a merchant with one permanent code (like a market stall) -- scan their code, then say how much you're paying.
      </p>
      {!manualEntry && !merchantId ? (
        <>
          {!scanUnavailable && <QrScanCamera onDetect={handleScan} onUnavailable={() => setScanUnavailable(true)} />}
          <button
            type="button"
            className="itunda-btn itunda-btn-secondary"
            style={{ width: '100%' }}
            onClick={() => setManualEntry(true)}
          >
            {scanUnavailable ? 'Enter merchant ID manually' : 'No camera? Enter merchant ID instead'}
          </button>
        </>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <input
            type="text" value={merchantId} onChange={(e) => setMerchantId(e.target.value)} placeholder="Merchant ID" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '10px' }}>
            <input
              type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)" required autoFocus
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>{submitting ? 'Paying…' : 'Pay'}</button>
          </div>
          {!scanUnavailable && (
            <button
              type="button"
              className="itunda-btn itunda-btn-secondary"
              onClick={() => { setManualEntry(false); setMerchantId(''); }}
            >
              Scan a QR code instead
            </button>
          )}
        </form>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>}
    </div>
  );
}

export function PaymentConfirmation({ result, onDone }: { result: CollectPaymentResult; onDone: () => void }) {
  return (
    <div style={{ textAlign: 'center', padding: '28px 0' }}>
      <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
      <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '4px' }}>Paid {result.merchantName}</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, marginBottom: '4px' }}>{result.amount.toLocaleString('en-US')} RWF</p>
      {result.channel === 'FACE_PAY' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>😊 Authorized with Face Pay</p>
      )}
      {result.cashbackEarned > 0 && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-green)', marginBottom: '16px' }}>
          +{result.cashbackEarned.toLocaleString('en-US')} RWF cashback earned
        </p>
      )}
      <button className="itunda-btn itunda-btn-secondary" onClick={onDone} style={{ marginTop: '8px' }}>Done</button>
    </div>
  );
}

// Real hyperlocal neighborhood setup (2026-07-20) -- shared across every Hood-tab
// module (Marketplace/Community/Jobs/Property), same "one small component, four real
// call sites" shape this project already uses for offer bubbles etc. See
// lib/neighborhood.ts's own doc comment for the full backend account.
// Real second neighborhood (2026-08-04) -- isSecond mirrors Android HoodShared.kt's own
// NeighborhoodSetupPrompt(isSecond) and iOS's own isSecond port exactly, same copy.
