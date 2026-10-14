import { motion } from 'framer-motion';
import { Wallet as AccountIcon } from 'lucide-react';
import { useI18n } from './i18n/I18nContext';

export function PayHubQuickActions({ onCardsClick }: { onCardsClick: () => void }) {
  const { t } = useI18n();

  return (
    <motion.div
      whileTap={{ scale: 0.98 }}
      className="itunda-flat-section"
      onClick={onCardsClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onCardsClick(); } }}
      style={{ display: 'flex', alignItems: 'center', gap: '12px', cursor: 'pointer' }}
    >
      <div style={{ width: '38px', height: '38px', borderRadius: '999px', backgroundColor: 'color-mix(in srgb, var(--itunda-indigo) 12%, transparent)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <AccountIcon size={18} color="var(--itunda-indigo)" />
      </div>
      <span style={{ fontWeight: '600', fontSize: 'var(--itunda-type-scale-15-size)', color: 'var(--itunda-grey-900)' }}>{t('quickActions.cards')}</span>
    </motion.div>
  );
}

