// Real "Membership" screen, adapted (itunda Pay redesign, 2026-08-28). The real
// Toss Pay reference aggregates points across many real THIRD-PARTY brands (Naver,
// Kakao, Coupang, CU, GS25, Shinsegae...) -- itunda has no such partnerships, and
// fabricating balances for brands itunda has no relationship with would violate
// this codebase's own standing honesty discipline. Confirmed with the user directly
// (AskUserQuestion): "Adapt Membership to itunda's own real points" -- this screen
// shows only itunda's own real data: rewards points (RewardsService), Pay Money
// balance (AccountType.PAY), and real per-merchant "Store points"
// (MerchantLoyaltyAccount, itunda's own honest analog to Toss's own "Store points"
// row -- surfaced to a client for the first time this pass, see
// MerchantLoyaltyPointsService.getMyBalances's own doc comment).

import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import { fetchAccounts } from './lib/account';
import { fetchRewardTasks, type RewardTasksResult } from './lib/rewards';
import { fetchMyLoyaltyBalances, type LoyaltyBalance } from './lib/coupons';
import { RewardsPreviewSection } from './PayHomeExtras';
import { useI18n } from './i18n/I18nContext';
import { IconBack } from './icons/ItundaIcons';

export function MembershipView({ onBack, onOpenRewards, onOpenPayMoney }: { onBack: () => void; onOpenRewards: () => void; onOpenPayMoney: () => void }) {
  const { t } = useI18n();
  const [payBalance, setPayBalance] = useState<number | null>(null);
  const [rewards, setRewards] = useState<RewardTasksResult | null>(null);
  const [loyalty, setLoyalty] = useState<{ balances: LoyaltyBalance[]; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([fetchAccounts(), fetchRewardTasks(), fetchMyLoyaltyBalances()])
      .then(([accounts, rewardTasks, loyaltyBalances]) => {
        setPayBalance(accounts.find((a) => a.type === 'PAY')?.balance ?? 0);
        setRewards(rewardTasks);
        setLoyalty(loyaltyBalances);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('pay.membershipLoadError')));
  }, [t]);

  const loading = payBalance === null || rewards === null || loyalty === null;
  const combinedTotal = (rewards?.rewardsTotal ?? 0) + (payBalance ?? 0);

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '14px 16px' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
          <h2 style={{ margin: 0, fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>{t('pay.membershipTitle')}</h2>
        </div>

        {loading ? (
          <div style={{ padding: '0 20px' }}>
            <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />
          </div>
        ) : (
          <div style={{ padding: '4px 20px 24px' }}>
            <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('pay.membershipTotal')}</p>
            <p style={{ margin: '6px 0 24px', fontSize: '32px', fontWeight: 700, letterSpacing: '-0.5px' }}>{combinedTotal.toLocaleString()} RWF</p>

            <button onClick={onOpenRewards} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)', textAlign: 'left' }}>
              <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-700)' }}>{t('pay.membershipAvailablePoints')}</span>
              <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{(rewards?.rewardsTotal ?? 0).toLocaleString()} RWF</span>
            </button>
            <button onClick={onOpenPayMoney} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)', textAlign: 'left' }}>
              <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-700)' }}>{t('pay.membershipPayMoney')}</span>
              <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{(payBalance ?? 0).toLocaleString()} RWF</span>
            </button>

            {loyalty && loyalty.balances.length > 0 && (
              <div style={{ marginTop: '16px' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>{t('pay.membershipStorePoints')}</p>
                {loyalty.balances.map((b) => (
                  <div key={b.merchantId} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0' }}>
                    <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>{b.merchantName}</span>
                    <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{b.pointBalance.toLocaleString()}</span>
                  </div>
                ))}
              </div>
            )}

            {rewards && <div style={{ marginTop: '8px' }}><RewardsPreviewSection tasks={rewards} onViewAll={onOpenRewards} /></div>}
          </div>
        )}
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', padding: '0 20px' }} role="alert">{error}</p>}
      </div>
    </div>
  );
}
