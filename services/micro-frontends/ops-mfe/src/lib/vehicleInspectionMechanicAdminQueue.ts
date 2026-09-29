import { apiFetch } from './api';

// Real vehicle-inspection mechanic moderation queue (Vehicle product-completeness
// pass, 2026-09-07) -- mechanics are real money-receiving business actors (identical
// shape to Merchant) that previously had zero admin lever at all, unlike
// MerchantModerationAdminController. Naturally small-cardinality (every registered
// mechanic, not a paged sub-selection) -- a plain list, not paginated, matching the
// backend's own non-paged response, same shape lib/merchantAdminQueues.ts's own
// fetchFeeWaiverCandidates already establishes.
export interface VehicleInspectionMechanic {
  mechanicId: string;
  businessName: string;
  available: boolean;
  suspended: boolean;
  createdAt: string;
}

export const fetchVehicleInspectionMechanics = () =>
  apiFetch<{ success: boolean; mechanics: VehicleInspectionMechanic[] }>('/api/v1/system/vehicle-inspection-mechanics').then((r) => r.mechanics);

export const suspendVehicleInspectionMechanic = (mechanicId: string) =>
  apiFetch<{ success: boolean; mechanic: VehicleInspectionMechanic }>(`/api/v1/system/vehicle-inspection-mechanics/${mechanicId}/suspend`, { method: 'POST' });

export const reactivateVehicleInspectionMechanic = (mechanicId: string) =>
  apiFetch<{ success: boolean; mechanic: VehicleInspectionMechanic }>(`/api/v1/system/vehicle-inspection-mechanics/${mechanicId}/reactivate`, { method: 'POST' });
