import { useState, type ReactNode } from 'react';
import { motion } from 'framer-motion';
import { IconAdd, IconShieldCheck } from './icons/ItundaIcons';
import { LockGlyph } from './icons/ItundaFaceSecurity';
import { MoneyBagGlyph } from './icons/ItundaFaceMisc';
import { BankCardChip, PetalMark } from './BankCardChip';
import { CARD_DESIGNS, DEFAULT_CARD_DESIGN, cardDesign } from './lib/card';

// Real Toss Bank "which color do you like?" issuance step (namu.wiki: 5 real named
// colorways; toss.tech's own engineering post on the picker's 3D touch-and-rotate
// interaction) -- direct user instruction 2026-08-27: "update itunda bank with all
// those cards designs allowing users to choose from those designs... that's how toss
// does it too". Picks from CARD_DESIGNS (lib/card.ts), itunda's own real front/back
// colorways validated in the standalone card-lineup design pass. Front-only during
// picking, matching that same pass's own real-photo-sourced finding: the real card's
// front is color and chip, nothing else -- no fabricated printed number here either,
// same "fully masked, no card exists yet" reasoning the previous single-design mockup
// already established. Split out of BankDashboard.tsx (not left inline) to stay under
// that file's own file-size-lint baseline, same convention PayHomeExtras.tsx/
// FullScreenFlow.tsx already established.
export function CardExplainer({ busy, onIssue }: { busy: boolean; onIssue: (design: string) => void }) {
  const [selected, setSelected] = useState(DEFAULT_CARD_DESIGN);
  const design = cardDesign(selected);
  const FEATURES: { glyph: ReactNode; label: string }[] = [
    { glyph: <MoneyBagGlyph size={22} />, label: 'No annual fee, ever' },
    { glyph: <IconAdd size={20} color="var(--itunda-indigo)" />, label: 'Issued instantly in the app -- no branch visit' },
    { glyph: <IconShieldCheck size={20} color="var(--itunda-indigo)" />, label: 'Set your own daily and monthly spend limits' },
    { glyph: <LockGlyph size={22} />, label: 'One-tap freeze if it’s ever lost' },
  ];
  const lockupColor = design.frontLight ? 'rgba(25,31,40,0.5)' : 'rgba(255,255,255,0.6)';

  return (
    <div style={{ textAlign: 'center', padding: '8px 0 4px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', fontWeight: 700, marginBottom: '14px' }}>Which finish do you like?</p>
      <motion.div
        initial={{ opacity: 0, y: 12, scale: 0.96 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ type: 'spring', stiffness: 260, damping: 22 }}
        style={{
          width: '138px', height: '219px', margin: '0 auto 16px', borderRadius: '14px', position: 'relative', overflow: 'hidden',
          background: design.front, boxShadow: '0 2px 4px rgba(15,18,24,0.18), 0 22px 34px -16px rgba(15,18,24,0.45)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', border: design.frontLight ? '1px solid #e2e2de' : 'none',
        }}
      >
        <div style={{
          position: 'absolute', inset: 0,
          background: 'radial-gradient(120% 90% at 30% 15%, rgba(255,255,255,0.10) 0%, rgba(255,255,255,0) 45%), radial-gradient(140% 100% at 85% 100%, rgba(0,0,0,0.14) 0%, rgba(0,0,0,0) 55%)',
        }}
        />
        <div style={{ position: 'absolute', top: '26%', left: '38%', transform: 'translate(-50%, -50%)' }}>
          <BankCardChip size={30} />
        </div>
        <div style={{ position: 'absolute', left: '18px', bottom: '16px', display: 'flex', alignItems: 'center', gap: '4px' }}>
          <PetalMark color={lockupColor} size={12} />
          <span style={{ fontSize: '10px', fontWeight: 700, color: lockupColor }}>itunda bank</span>
        </div>
      </motion.div>

      <div style={{ display: 'flex', justifyContent: 'center', gap: '10px', marginBottom: '18px' }}>
        {CARD_DESIGNS.map((d) => (
          <button
            key={d.id}
            onClick={() => setSelected(d.id)}
            aria-label={d.name}
            aria-pressed={d.id === selected}
            style={{
              width: '30px', height: '30px', borderRadius: '50%', position: 'relative', overflow: 'hidden',
              border: d.id === selected ? '2px solid var(--itunda-indigo)' : '2px solid transparent',
            }}
          >
            <span style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: '50%', backgroundColor: d.front }} />
            <span style={{ position: 'absolute', right: 0, top: 0, bottom: 0, width: '50%', backgroundColor: d.back }} />
          </button>
        ))}
      </div>

      <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1, duration: 0.3 }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-19-size)', fontWeight: 800, marginBottom: '6px' }}>Your own itunda card, in seconds</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '24px' }}>
          A real debit card for your itunda balance -- no paperwork, no waiting.
        </p>
      </motion.div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', textAlign: 'left', marginBottom: '24px' }}>
        {FEATURES.map((f, i) => (
          <motion.div
            key={f.label}
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: 0.16 + i * 0.06, duration: 0.25 }}
            style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 4px' }}
          >
            <span style={{ width: '36px', height: '36px', borderRadius: '10px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              {f.glyph}
            </span>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{f.label}</span>
          </motion.div>
        ))}
      </div>

      <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ delay: 0.4, duration: 0.25 }}>
        <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={busy} onClick={() => onIssue(selected)}>
          {busy ? 'Issuing…' : `Get your ${design.name} card`}
        </button>
      </motion.div>
    </div>
  );
}
