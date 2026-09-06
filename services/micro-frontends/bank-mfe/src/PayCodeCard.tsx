import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import JsBarcode from 'jsbarcode';
import { BankCardChip, CardContactlessGlyph } from './BankCardChip';
import { LockGlyph } from './icons/ItundaFaceSecurity';
import { PayFundingSourcePicker } from './PayFundingSourcePicker';
import { getStoredUser } from './lib/api';
import { type Account } from './lib/account';
import { type LinkedAccount, fetchLinkedAccounts } from './lib/overview';
import {
  generateCustomerPaymentCode, fetchNearbyAds, type CustomerPaymentCode, type NearbyMerchantAd,
} from './lib/shopping';

// Real customer-presented payment code (Pay-parity port, §238, corrected §240) --
// see lib/shopping.ts's own generateCustomerPaymentCode doc comment for the full
// sourced contract. §238's first pass was QR-only, based on secondhand reasoning
// about what KakaoPay "probably" shows -- the user then had us fetch KakaoPay's own
// real App Store screenshots directly (apps.apple.com), and separately sent 3 real
// screenshots of their own actual KakaoPay app. Both real, first-party sources
// agree on a materially different real design than §238 built:
// - The primary code is a real linear BARCODE (Code128, Korea's real 바코드결제
//   standard, works with plain laser POS scanners), with a small QR secondary next
//   to it -- not a big centered QR alone.
// - The real card also shows the funding account and a real "nearby benefits" row
//   (real nearby merchant discounts+distance) -- §238 deliberately dropped both as
//   "supplementary," but the real reference confirms they're part of the actual
//   core screen, not optional extras.
// - A real "포인트 사용" (use points) toggle exists in the reference, but itunda has
//   no separate points balance (cashback credits straight to the account, same
//   honest scope-down Android's own original MyPaymentCodeCard already
//   established) -- deliberately still not faked here.
// - The real screen's bottom card row mixes account + real linked bank cards +
//   Samsung-Pay NFC + membership -- itunda has no NFC/membership equivalent, so
//   Android's own account-only carousel was already an honest, deliberate
//   simplification of that row, not an inaccuracy -- kept as-is when this pass
//   restores everything else.
export function MyPaymentCodeCard({ accounts, onOpenCard }: { accounts: Account[]; onOpenCard: () => void }) {
  const [revealed, setRevealed] = useState(false);
  const [code, setCode] = useState<CustomerPaymentCode | null>(null);
  const [barcodeDataUrl, setBarcodeDataUrl] = useState<string | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [linkedAccount, setLinkedAccount] = useState<LinkedAccount | null>(null);
  const [nearbyAds, setNearbyAds] = useState<NearbyMerchantAd[]>([]);
  // Real swipeable funding-source selection (§240) -- defaults to the real itunda Pay
  // account (§257: this code always pays out of Pay money unless the customer
  // explicitly swipes to a different account), MAIN kept only as a defensive fallback.
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  // Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
  // "Facepay · QR Payment" Recent/Account/Card picker sheet) -- additive to the
  // existing AccountCardCarousel swipe below, not a replacement (see
  // PayFundingSourcePicker.tsx's own doc comment for why).
  const [showFundingPicker, setShowFundingPicker] = useState(false);
  const myPhoneNumber = getStoredUser()?.phoneNumber ?? '';
  const account = accounts.find((w) => w.id === selectedAccountId) ?? accounts.find((w) => w.type === 'PAY') ?? accounts.find((w) => w.type === 'MAIN') ?? accounts[0] ?? null;

  // Real auto-refresh shortly before the code's own real 2-minute expiry, matching
  // Android's identical MyPaymentCodeCard -- a customer standing at a register
  // should never have the code silently go stale mid-checkout.
  useEffect(() => {
    if (!revealed) return;
    let cancelled = false;
    let timeoutId: ReturnType<typeof setTimeout>;

    const refresh = () => {
      generateCustomerPaymentCode(account?.id)
        .then((result) => {
          if (cancelled) return;
          setCode(result);
          setError(null);
          const expiresInMs = new Date(result.expiresAt).getTime() - Date.now();
          timeoutId = setTimeout(refresh, Math.max(expiresInMs - 10_000, 5_000));
        })
        .catch(() => { if (!cancelled) setError('Could not load your payment code.'); });
    };
    refresh();

    return () => { cancelled = true; clearTimeout(timeoutId); };
    // Re-fetches against the newly-selected account if `account` changes while
    // already revealed, same real "don't silently keep charging the old account"
    // discipline Android's own LaunchedEffect(revealed, selectedAccount?.id) key
    // establishes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revealed, account?.id]);

  useEffect(() => {
    if (!code) { setBarcodeDataUrl(null); setQrDataUrl(null); return; }
    // Real contract: both the barcode and QR encode the RAW code, no
    // itunda://... URL wrapping -- see lib/shopping.ts's own doc comment for why
    // (a merchant's real camera scanner passes the decoded string straight
    // through as the code).
    const canvas = document.createElement('canvas');
    try {
      JsBarcode(canvas, code.code, { format: 'CODE128', displayValue: false, margin: 0, height: 60, width: 2 });
      setBarcodeDataUrl(canvas.toDataURL());
    } catch {
      setBarcodeDataUrl(null);
    }
    QRCode.toDataURL(code.code, { width: 140, margin: 1 }).then(setQrDataUrl).catch(() => setQrDataUrl(null));
  }, [code]);

  useEffect(() => {
    if (!code) return;
    const tick = () => setSecondsLeft(Math.max(Math.round((new Date(code.expiresAt).getTime() - Date.now()) / 1000), 0));
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [code]);

  // Real funding account -- same data AutoTopUpScreen/OverviewScreen already
  // fetch, read-only display here (matches the real reference's "충전계좌" row).
  useEffect(() => {
    fetchLinkedAccounts()
      .then((accounts) => setLinkedAccount(accounts.find((a) => a.status === 'LINKED') ?? null))
      .catch(() => {});
  }, []);

  // Real 당근(Karrot)-style radius-targeted nearby merchant benefits (matches the
  // real reference's own "주변 혜택" row) -- silent when location is denied or
  // nothing is nearby, same as every other nearby() caller in this codebase.
  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (position) => { fetchNearbyAds(position.coords.latitude, position.coords.longitude).then(setNearbyAds).catch(() => {}); },
      () => {},
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }, []);

  return (
    <div className="itunda-card" style={{ padding: '20px', marginBottom: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '140px', borderRadius: '14px', backgroundColor: 'var(--itunda-grey-100)' }}>
        {!revealed ? (
          // Real reveal gate (matching Android's identical 2026-08-13 fix, itself a
          // direct response to real KakaoPay research: the code screen requires an
          // explicit tap before showing the real barcode/QR, not an unprotected
          // display -- protects a customer whose unlocked phone someone else picks
          // up from having their payment code immediately visible).
          <button
            onClick={() => setRevealed(true)}
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '14px', padding: '20px', width: '100%' }}
          >
            <div style={{ width: '56px', height: '56px', borderRadius: '999px', backgroundColor: 'var(--itunda-white)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {/* Real fix (2026-08-24): was a raw Lucide Lock icon, itunda already
                  has its own real LockGlyph (itundaface) for this exact security
                  concept -- routes to it instead of a second, visually different
                  lock icon existing side by side. */}
              <LockGlyph size={24} />
            </div>
            <div>
              <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>Your payment code is hidden</p>
              <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)' }}>Protects you if someone else has your phone</p>
            </div>
            <span style={{ padding: '10px 28px', borderRadius: '999px', backgroundColor: 'var(--itunda-indigo)', color: 'var(--itunda-white)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>
              Tap to show
            </span>
          </button>
        ) : code && barcodeDataUrl && qrDataUrl ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', width: '100%', padding: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <img src={barcodeDataUrl} alt="Your payment barcode" style={{ flex: '1 1 0%', minWidth: 0, width: '100%', height: '60px', objectFit: 'contain', backgroundColor: 'var(--itunda-white)', borderRadius: '6px', padding: '4px' }} />
              <img src={qrDataUrl} alt="Your payment QR code" width={56} height={56} style={{ borderRadius: '6px', backgroundColor: 'var(--itunda-white)', padding: '3px' }} />
            </div>
            <p style={{ margin: 0, textAlign: 'center', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)' }}>
              {secondsLeft > 0 ? `Refreshes in ${secondsLeft}s` : 'Refreshing…'}
            </p>
          </div>
        ) : error ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        ) : (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
        )}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-600)' }}>
          {/* Real fold-in (2026-08-28) of what used to be a separate, read-only
              "Funding account" row -- now folded into the picker sheet's own
              Account tab, matching the real reference's single funding-source
              entry point instead of two separate real UI affordances. */}
          {account ? (account.type === 'PAY' ? 'itunda Pay' : account.type === 'MAIN' ? 'itunda Bank' : `itunda Pay ${account.currency}`) : linkedAccount?.provider}
        </span>
        <button onClick={() => setShowFundingPicker(true)} style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
          Change
        </button>
      </div>
      {showFundingPicker && (
        <PayFundingSourcePicker
          accounts={accounts}
          selectedAccountId={account?.id ?? null}
          onSelectAccount={setSelectedAccountId}
          onClose={() => setShowFundingPicker(false)}
          onOpenCard={onOpenCard}
          myPhoneNumber={myPhoneNumber}
        />
      )}
      {nearbyAds.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          <p style={{ margin: '0 0 10px', fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-900)' }}>Nearby benefits</p>
          <div style={{ display: 'flex', gap: '16px', overflowX: 'auto' }}>
            {nearbyAds.map((nearbyAd) => (
              <div key={nearbyAd.ad.id} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '64px', flexShrink: 0 }}>
                <div style={{ width: '40px', height: '40px', borderRadius: '999px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, color: 'var(--itunda-indigo)', fontSize: 'var(--itunda-type-scale-16-size)' }}>
                  {nearbyAd.businessName.charAt(0).toUpperCase()}
                </div>
                <p style={{ margin: '4px 0 0', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-800)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', width: '100%', textAlign: 'center' }}>{nearbyAd.businessName}</p>
                <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-600)' }}>{Math.round(nearbyAd.distanceKm * 1000)}m</p>
              </div>
            ))}
          </div>
        </div>
      )}
      {accounts.length > 1 && (
        <AccountCardCarousel accounts={accounts} selectedAccountId={account?.id ?? null} onSelect={setSelectedAccountId} />
      )}
    </div>
  );
}

// Real swipeable funding-source cards -- see MyPaymentCodeCard's own doc comment
// for why this stays a deliberate, honest simplification of the real reference's
// mixed account/card/membership row (itunda has no Samsung-Pay NFC or membership
// equivalent to include honestly). Settling on a card is a real selection: it's
// the accountId MyPaymentCodeCard's own code is generated against, matching the
// real "swipe to choose what you pay with" KakaoPay behavior. CSS scroll-snap is
// the web equivalent of Android's HorizontalPager -- no extra dependency needed.
function AccountCardCarousel({ accounts, selectedAccountId, onSelect }: { accounts: Account[]; selectedAccountId: string | null; onSelect: (id: string) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);

  const handleScroll = () => {
    const el = containerRef.current;
    if (!el || accounts.length === 0) return;
    const cardWidth = el.scrollWidth / accounts.length;
    const index = Math.min(Math.round(el.scrollLeft / cardWidth), accounts.length - 1);
    const w = accounts[index];
    if (w && w.id !== selectedAccountId) onSelect(w.id);
  };

  return (
    <div style={{ marginTop: '16px' }}>
      <div
        ref={containerRef}
        onScroll={handleScroll}
        style={{ display: 'flex', gap: '12px', overflowX: 'auto', scrollSnapType: 'x mandatory', paddingBottom: '4px' }}
      >
        {accounts.map((w) => (
          <div
            key={w.id}
            style={{
              scrollSnapAlign: 'center', flexShrink: 0, width: '220px', height: '139px', borderRadius: '16px', position: 'relative', overflow: 'hidden',
              background: `linear-gradient(135deg, ${accountCardColor(w.currency)} 0%, ${accountCardColor(w.currency)} 60%, rgba(0,0,0,0.18) 100%)`,
              padding: '16px 18px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
              boxShadow: '0 10px 20px -10px rgba(0,0,0,0.4)',
            }}
          >
            {/* Diagonal sheen, painted first so it sits behind the chip/wordmark/
                balance -- the same "flat color read as a card" fix (2026-08-26,
                direct user instruction: "all cards designs should resemble real
                card") applied to every card-shaped visual in this file. */}
            <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(115deg, rgba(255,255,255,0.18) 0%, rgba(255,255,255,0) 40%)' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <BankCardChip size={30} />
              <CardContactlessGlyph size={18} />
            </div>
            <div>
              <p style={{ margin: 0, color: 'var(--itunda-white)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)' }}>{w.type === 'PAY' ? 'itunda Pay' : w.type === 'MAIN' ? 'itunda Bank' : `itunda Pay ${w.currency}`}</p>
              <p style={{ margin: '2px 0 0', color: 'var(--itunda-white)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-19-size)' }}>
                {w.currency} {w.currency === 'RWF' ? w.availableBalance.toLocaleString('en-US') : w.availableBalance.toFixed(2)}
              </p>
            </div>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginTop: '10px' }}>
        {accounts.map((w) => (
          <div
            key={w.id}
            style={{
              width: w.id === selectedAccountId ? '8px' : '6px', height: w.id === selectedAccountId ? '8px' : '6px',
              borderRadius: '999px', backgroundColor: w.id === selectedAccountId ? 'var(--itunda-indigo)' : 'var(--itunda-grey-300)',
            }}
          />
        ))}
      </div>
    </div>
  );
}

function accountCardColor(currency: string): string {
  switch (currency) {
    case 'RWF': return '#2272EB';
    case 'USD': return '#04C065';
    case 'EUR': return '#7C5CFC';
    case 'GBP': return '#00898A';
    default: return 'var(--itunda-grey-700)';
  }
}

