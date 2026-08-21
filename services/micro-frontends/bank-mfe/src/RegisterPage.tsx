import { useEffect, useState } from 'react';
import { ChevronDown, ChevronUp, Eye, EyeOff, ShieldCheck } from 'lucide-react';
import { ApiError, getTerms, register, type TermsDocument } from './lib/api';

// Real sign-up page (2026-08-04) -- closes docs/DESIGN_REFERENCES.md Section 8
// recommendation #7: bank-mfe had no registration page at all, unlike Android/iOS's
// real 3-step phone -> name -> password flow (LoginScreen.kt/.swift). Mirrors that same
// field set and step order on one scrolling form rather than a multi-step wizard --
// LoginPage.tsx's own single-form pattern already fits this content amount, and Toss's
// own real-name signup redesign (cited in this doc's "One thing, one page" section)
// explicitly favors a single reverse-stacking scroll over multiple "next"-tap screens
// for a form this size.
//
// Real Toss/Korean-fintech-style 약관 동의 (terms consent) section (2026-08-18) -- see
// backend TermsCatalog's own doc comment for the full sourced account. itunda had zero
// terms-consent UI anywhere before this. Follows the real, standard structure sourced
// from Korea's own regulatory backdrop (전자상거래법 dark-pattern amendment effective
// 2025-02-14, penalty increase 2026-09-11): an "Agree to all" toggle, every checkbox
// starting UNCHECKED (never pre-ticked -- that's the exact dark pattern the real 2025
// amendment bans), required items visually grouped before optional ones and tagged
// [Required]/[Optional], and the submit button genuinely disabled until every real
// required term is checked, not just visually discouraged.
export default function RegisterPage({ onRegistered, onBackToLogin }: { onRegistered: () => void; onBackToLogin: () => void }) {
  const [phoneNumber, setPhoneNumber] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [password, setPassword] = useState('');
  const [referralCode, setReferralCode] = useState('');
  const [showReferralField, setShowReferralField] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real "Minimum Input" simplicity addition (item 244, docs/DESIGN_REFERENCES.md §11),
  // matching the identical same-day fix on LoginPage.tsx: a local UI-only affordance,
  // not a security control. Especially valuable here -- a mistyped password at
  // registration silently locks the account behind a typo neither the user nor
  // itunda can recover.
  const [showPassword, setShowPassword] = useState(false);

  const [terms, setTerms] = useState<TermsDocument[]>([]);
  // Starts empty on purpose -- never pre-checked. See this file's own top doc comment.
  const [acceptedTermsIds, setAcceptedTermsIds] = useState<Set<string>>(new Set());
  const [expandedTermsId, setExpandedTermsId] = useState<string | null>(null);

  useEffect(() => {
    getTerms()
      .then(setTerms)
      .catch(() => {
        // Best-effort: if the real terms catalog can't be fetched, fail toward MORE
        // friction, not less -- an empty `terms` list means `requiredTermsIds` below is
        // also empty, but the real gate is still enforced server-side
        // (AuthService.register's own RequiredTermsNotAcceptedException fires
        // regardless), so this never silently bypasses consent, it just can't render
        // the real checklist to react to.
      });
  }, []);

  const requiredTerms = terms.filter((t) => t.required);
  const optionalTerms = terms.filter((t) => !t.required);
  const allRequiredAccepted = requiredTerms.length > 0 && requiredTerms.every((t) => acceptedTermsIds.has(t.id));
  const allAccepted = terms.length > 0 && terms.every((t) => acceptedTermsIds.has(t.id));

  const toggleTerm = (id: string) => {
    setAcceptedTermsIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleAll = () => {
    setAcceptedTermsIds(allAccepted ? new Set() : new Set(terms.map((t) => t.id)));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(phoneNumber, password, firstName, lastName, Array.from(acceptedTermsIds), referralCode);
      onRegistered();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Can't connect right now. Please try again in a moment.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ minHeight: '100svh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <form
        onSubmit={handleSubmit}
        className="itunda-card"
        style={{ width: '360px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '16px' }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
          <ShieldCheck size={24} color="var(--itunda-indigo)" />
          <h1 style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Itunda</h1>
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginTop: '-8px' }}>
          Create your Itunda account.
        </p>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Phone number</span>
          {/* Real "Minimum Input" simplicity fix (item 244, docs/DESIGN_REFERENCES.md §11,
              rule #4), matching the identical same-day fix on LoginPage.tsx. */}
          <input
            type="tel"
            autoFocus
            value={phoneNumber}
            onChange={(e) => setPhoneNumber(e.target.value)}
            placeholder="+250788123456"
            required
            style={{
              padding: '12px 14px',
              borderRadius: '10px',
              border: '1px solid var(--itunda-grey-200)',
              fontSize: 'var(--itunda-type-scale-15-size)',
            }}
          />
        </label>

        <div style={{ display: 'flex', gap: '10px' }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>First name</span>
            <input
              type="text"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
              style={{
                padding: '12px 14px',
                borderRadius: '10px',
                border: '1px solid var(--itunda-grey-200)',
                fontSize: 'var(--itunda-type-scale-15-size)',
              }}
            />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px', flex: 1 }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Last name</span>
            <input
              type="text"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
              style={{
                padding: '12px 14px',
                borderRadius: '10px',
                border: '1px solid var(--itunda-grey-200)',
                fontSize: 'var(--itunda-type-scale-15-size)',
              }}
            />
          </label>
        </div>

        <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Password</span>
          <div style={{ position: 'relative' }}>
          <input
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            style={{
              width: '100%',
              boxSizing: 'border-box',
              padding: '12px 40px 12px 14px',
              borderRadius: '10px',
              border: '1px solid var(--itunda-grey-200)',
              fontSize: 'var(--itunda-type-scale-15-size)',
            }}
          />
          <button
            type="button"
            onClick={() => setShowPassword((v) => !v)}
            aria-label={showPassword ? 'Hide password' : 'Show password'}
            style={{ position: 'absolute', right: '10px', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', padding: '4px', display: 'flex', color: 'var(--itunda-grey-500)' }}
          >
            {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
          </div>
        </label>

        {showReferralField ? (
          <label style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Referral code (optional)</span>
            <input
              type="text"
              value={referralCode}
              onChange={(e) => setReferralCode(e.target.value)}
              style={{
                padding: '12px 14px',
                borderRadius: '10px',
                border: '1px solid var(--itunda-grey-200)',
                fontSize: 'var(--itunda-type-scale-15-size)',
              }}
            />
          </label>
        ) : (
          <button
            type="button"
            onClick={() => setShowReferralField(true)}
            style={{ background: 'none', border: 'none', padding: 0, textAlign: 'left', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)', cursor: 'pointer' }}
          >
            Have a referral code?
          </button>
        )}

        {terms.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', borderTop: '1px solid var(--itunda-grey-200)', paddingTop: '14px' }}>
            <label
              style={{
                display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 4px',
                fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', cursor: 'pointer',
              }}
            >
              <input type="checkbox" checked={allAccepted} onChange={toggleAll} style={{ width: '18px', height: '18px' }} />
              Agree to all
            </label>
            {/* Required terms grouped before optional ones -- see this file's own top
                doc comment for the real sourced reasoning. */}
            {[...requiredTerms, ...optionalTerms].map((term) => (
              <div key={term.id} style={{ borderTop: '1px solid var(--itunda-grey-100)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '8px 4px' }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: '8px', flex: 1, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', cursor: 'pointer' }}>
                    <input
                      type="checkbox"
                      checked={acceptedTermsIds.has(term.id)}
                      onChange={() => toggleTerm(term.id)}
                      style={{ width: '16px', height: '16px' }}
                    />
                    <span
                      style={{
                        fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, padding: '1px 6px', borderRadius: '4px',
                        color: term.required ? 'var(--itunda-red)' : 'var(--itunda-grey-500)',
                        background: term.required ? 'rgba(255,59,48,0.08)' : 'var(--itunda-grey-100)',
                      }}
                    >
                      {term.required ? 'Required' : 'Optional'}
                    </span>
                    <span>{term.title}</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => setExpandedTermsId(expandedTermsId === term.id ? null : term.id)}
                    aria-label={expandedTermsId === term.id ? 'Hide details' : 'View details'}
                    style={{ background: 'none', border: 'none', padding: '4px', display: 'flex', color: 'var(--itunda-grey-500)', cursor: 'pointer' }}
                  >
                    {expandedTermsId === term.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                  </button>
                </div>
                {expandedTermsId === term.id && (
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px 30px' }}>{term.summary}</p>
                )}
              </div>
            ))}
          </div>
        )}

        {error && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !allRequiredAccepted}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>

        <button
          type="button"
          onClick={onBackToLogin}
          style={{ background: 'none', border: 'none', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', cursor: 'pointer' }}
        >
          Already have an account? Log in
        </button>
      </form>
    </div>
  );
}
