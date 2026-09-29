import { useState } from 'react';
import { useI18n } from '../i18n/I18nContext';
import { RegisterView } from './PosRegister';
import { CatalogView } from './PosCatalog';

type Mode = 'REGISTER' | 'CATALOG';

// Real cash-register/POS UI -- the register-software half of "Toss Place" (see
// MerchantProductService.kt's own doc comment for why the hardware bundle stays out of
// scope but this doesn't need to). Checkout reuses the already-real generateQr/
// chargeCard flows unmodified, just with a cart-derived amount/description instead of a
// single typed-in amount. Register/Checkout/QR/Card live in PosRegister.tsx, Catalog +
// its Options/PriceTiers/Analytics/TimeDeal panels in PosCatalog.tsx/
// PosCatalogPanels.tsx -- mirroring Android/iOS's own already-proven split
// (CatalogScreen/ProductRow/ProductAnalyticsPanel as separate files) rather than
// inventing new organization.
export default function PosScreen() {
  const { t } = useI18n();
  const [mode, setMode] = useState<Mode>('REGISTER');

  return (
    <div style={{ maxWidth: '900px' }}>
      <div className="itunda-card" style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', maxWidth: '300px' }}>
        {(['REGISTER', 'CATALOG'] as Mode[]).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              flex: 1,
              padding: '10px',
              borderRadius: 'var(--itunda-control-radius, 12px)',
              fontSize: '14px',
              fontWeight: 700,
              color: mode === m ? 'var(--itunda-surface-default)' : 'var(--itunda-text-secondary)',
              backgroundColor: mode === m ? 'var(--itunda-brand)' : 'transparent',
            }}
          >
            {m === 'REGISTER' ? t('pos.modeRegister') : t('pos.modeCatalog')}
          </button>
        ))}
      </div>
      {mode === 'REGISTER' ? <RegisterView /> : <CatalogView />}
    </div>
  );
}
