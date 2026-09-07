import { useI18n } from './i18n/I18nContext';
import type { Listing, VehicleFieldsState } from './lib/marketplace';

// Real 당근카 (Karrot Vehicles) listing-creation fields + detail-view breakdown
// (itunda Hood redesign, 2026-08-28) -- extracted into its own file since
// HoodMarketplaceCards.tsx/HoodListingCard.tsx are both already at or near the
// 500-line new-file cap. A vehicle is still a regular Listing (see backend
// Listing.vehicleIsLeaseTakeover's own doc comment); this is purely the extra
// fields NewListingCard shows/sends when a seller marks a listing as a vehicle,
// reusing every other Marketplace field/flow unchanged. VehicleFieldsState/
// emptyVehicleFieldsState/vehicleFieldsToRequest live in lib/marketplace.ts (a plain
// .ts file) rather than here, so this component-only file keeps React Fast Refresh
// happy (a .tsx file mixing component and non-component exports breaks it).

const inputStyle: React.CSSProperties = { flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' };

function ToggleChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        borderRadius: '999px', padding: '7px 12px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 600,
        border: 'none', cursor: 'pointer', width: 'fit-content',
        background: active ? 'var(--itunda-brand)' : 'var(--itunda-grey-100)',
        color: active ? '#fff' : 'var(--itunda-grey-900)',
      }}
    >
      {label}
    </button>
  );
}

export function VehicleListingFieldsForm({ state, onChange }: { state: VehicleFieldsState; onChange: (next: VehicleFieldsState) => void }) {
  const { t } = useI18n();
  const set = (patch: Partial<VehicleFieldsState>) => onChange({ ...state, ...patch });
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <ToggleChip label="🚗 This is a vehicle (당근카)" active={state.isVehicle} onClick={() => set({ isVehicle: !state.isVehicle })} />
      {state.isVehicle && (
        <>
          <div style={{ display: 'flex', gap: '10px' }}>
            <input type="number" value={state.mileageKm} onChange={(e) => set({ mileageKm: e.target.value })} placeholder={t('hood.vehicle.mileagePlaceholder')} style={inputStyle} />
            <input type="number" value={state.insuranceClaimCount} onChange={(e) => set({ insuranceClaimCount: e.target.value })} placeholder={t('hood.vehicle.insuranceClaimsPlaceholder')} style={inputStyle} />
          </div>
          <ToggleChip label="Lease takeover (렌트 승계)" active={state.isLeaseTakeover} onClick={() => set({ isLeaseTakeover: !state.isLeaseTakeover })} />
          {state.isLeaseTakeover && (
            <>
              <div style={{ display: 'flex', gap: '10px' }}>
                <input type="number" value={state.leaseTotalAcquisitionCost} onChange={(e) => set({ leaseTotalAcquisitionCost: e.target.value })} placeholder={t('hood.vehicle.leaseAcquisitionCostPlaceholder')} style={inputStyle} />
                <input type="number" value={state.leaseMonthlyPayment} onChange={(e) => set({ leaseMonthlyPayment: e.target.value })} placeholder={t('hood.vehicle.leaseMonthlyPaymentPlaceholder')} style={inputStyle} />
              </div>
              <div style={{ display: 'flex', gap: '10px' }}>
                <input type="number" value={state.leaseRemainingMonths} onChange={(e) => set({ leaseRemainingMonths: e.target.value })} placeholder={t('hood.vehicle.leaseMonthsRemainingPlaceholder')} style={inputStyle} />
                <input type="number" value={state.leaseTotalMonths} onChange={(e) => set({ leaseTotalMonths: e.target.value })} placeholder={t('hood.vehicle.leaseTotalMonthsPlaceholder')} style={inputStyle} />
              </div>
              <div style={{ display: 'flex', gap: '10px' }}>
                <input type="number" value={state.leaseSubsidyAmount} onChange={(e) => set({ leaseSubsidyAmount: e.target.value })} placeholder={t('hood.vehicle.leaseSubsidyPlaceholder')} style={inputStyle} />
                <input type="number" value={state.leaseReturnFee} onChange={(e) => set({ leaseReturnFee: e.target.value })} placeholder={t('hood.vehicle.leaseReturnFeePlaceholder')} style={inputStyle} />
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}

function row(label: string, value: string) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
      <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-600)' }}>{label}</span>
      <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{value}</span>
    </div>
  );
}

// Real lease-takeover cost breakdown shown on a vehicle listing's detail card --
// mirrors the reference's own 인수비/월 납입금/승계 지원금/만기후 반납 rows.
export function VehicleDetailSection({ listing }: { listing: Listing }) {
  if (listing.vehicleMileageKm == null && listing.vehicleInsuranceClaimCount == null) return null;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '14px', borderRadius: '12px', background: 'var(--itunda-grey-50)' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Vehicle info</h4>
      {listing.vehicleMileageKm != null && row('Mileage', `${listing.vehicleMileageKm.toLocaleString('en-US')} km`)}
      {listing.vehicleInsuranceClaimCount != null && row('Insurance claims', String(listing.vehicleInsuranceClaimCount))}
      {listing.vehicleIsLeaseTakeover && (
        <>
          <h5 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginTop: '4px' }}>Lease takeover</h5>
          {listing.leaseTotalAcquisitionCost != null && row('Total acquisition cost', `${listing.leaseTotalAcquisitionCost.toLocaleString('en-US')} RWF`)}
          {listing.leaseMonthlyPayment != null && row('Monthly payment', `${listing.leaseMonthlyPayment.toLocaleString('en-US')} RWF`)}
          {listing.leaseRemainingMonths != null && listing.leaseTotalMonths != null && row('Remaining', `${listing.leaseRemainingMonths} / ${listing.leaseTotalMonths} months`)}
          {listing.leaseSubsidyAmount != null && row('Subsidy', `${listing.leaseSubsidyAmount.toLocaleString('en-US')} RWF`)}
          {listing.leaseReturnFee != null && row('Return fee at end', `${listing.leaseReturnFee.toLocaleString('en-US')} RWF`)}
        </>
      )}
    </div>
  );
}
