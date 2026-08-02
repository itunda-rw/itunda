// Real Rwanda National Agricultural Insurance Scheme (NAIS)-style parametric/weather-index
// crop insurance client -- see the backend's WeatherIndexInsuranceService.kt doc comment for
// the full sourced account (WFP: GoR covers 40% of premium, cooperatives/farmers 60%, for
// maize, rice, chilli peppers, French beans, Irish potatoes; the earlier Kilimo Salama pilot
// insured 37,000+ smallholders on satellite rainfall data). Structurally distinct from
// lib/insurance.ts's claims-based InsurancePlan/InsurancePolicy/InsuranceClaim: no claim is
// ever filed here -- a district+season's published rainfall index auto-pays out every
// enrolled policy in that district+season at once, or the season ends with no payout. The
// admin publish-index endpoint has no client here on purpose -- that's an internal/admin
// tool, out of scope for this customer-facing web client.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type WeatherIndexCropType = 'MAIZE' | 'RICE' | 'CHILLI_PEPPER' | 'FRENCH_BEANS' | 'IRISH_POTATO';
export type WeatherIndexPolicyStatus = 'ENROLLED' | 'PAYOUT_TRIGGERED' | 'SEASON_ENDED_NO_PAYOUT' | 'CANCELLED';

export interface CropIndexCatalogEntry {
  cropType: WeatherIndexCropType;
  name: string;
  premiumRatePercent: number;
  description: string;
}

export interface CropIndexPolicy {
  id: string;
  cropType: WeatherIndexCropType;
  district: string;
  season: string;
  insuredAmount: number;
  premiumAmount: number;
  status: WeatherIndexPolicyStatus;
  createdAt: string;
  payoutAt: string | null;
}

export interface SeasonRainfallIndexView {
  district: string;
  season: string;
  rainfallIndexPercent: number;
  droughtThresholdPercent: number;
  publishedAt: string;
  publishedByAdminId: string;
}

export const fetchCropIndexCatalog = () =>
  apiFetch<{ success: boolean; catalog: CropIndexCatalogEntry[] }>('/api/v1/insurance/crop-index/catalog').then((r) => r.catalog);

export const enrollInCropIndexPolicy = (cropType: WeatherIndexCropType, district: string, season: string, insuredAmount: number) =>
  apiFetch<{ success: boolean; policy: CropIndexPolicy }>('/api/v1/insurance/crop-index/policies', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ cropType, district, season, insuredAmount }),
  }).then((r) => r.policy);

export const fetchMyCropIndexPolicies = () =>
  apiFetch<{ success: boolean; policies: CropIndexPolicy[] }>('/api/v1/insurance/crop-index/policies').then((r) => r.policies);

export const cancelCropIndexPolicy = (policyId: string) =>
  apiFetch<{ success: boolean; policy: CropIndexPolicy }>(`/api/v1/insurance/crop-index/policies/${policyId}/cancel`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.policy);

export const fetchSeasonIndex = (district: string, season: string) =>
  apiFetch<{ success: boolean; index: SeasonRainfallIndexView | null }>(
    `/api/v1/insurance/crop-index/districts/${encodeURIComponent(district)}/seasons/${encodeURIComponent(season)}/index`,
  ).then((r) => r.index);
