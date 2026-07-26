import { apiFetch } from './api';

// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
// backend VehicleValuationService's own doc comment for the full sourced account and
// honest scope boundary: no real Carmart-style used-car pricing database partnership
// exists here, this is itunda's own documented general depreciation estimate.

export interface Vehicle {
  id: string;
  make: string;
  model: string;
  modelYear: number;
  purchasePrice: number;
  purchaseDate: string;
  mileageKm: number;
  createdAt: string;
}

export interface VehicleValuation {
  vehicle: Vehicle;
  ageYears: number;
  expectedMileageKm: number;
  currentEstimatedValue: number;
  estimatedValueIn1Year: number;
  estimatedValueIn2Years: number;
  estimatedValueIn3Years: number;
}

export const fetchMyVehicles = () =>
  apiFetch<{ success: boolean; vehicles: Vehicle[] }>('/api/v1/vehicles').then((r) => r.vehicles);

export const registerVehicle = (make: string, model: string, modelYear: number, purchasePrice: number, purchaseDate: string, mileageKm: number) =>
  apiFetch<{ success: boolean; vehicle: Vehicle }>('/api/v1/vehicles', {
    method: 'POST',
    body: JSON.stringify({ make, model, modelYear, purchasePrice, purchaseDate, mileageKm }),
  }).then((r) => r.vehicle);

export const fetchVehicleValuation = (id: string) =>
  apiFetch<{ success: boolean; valuation: VehicleValuation }>(`/api/v1/vehicles/${id}/valuation`).then((r) => r.valuation);

export const updateVehicleMileage = (id: string, mileageKm: number) =>
  apiFetch<{ success: boolean; vehicle: Vehicle }>(`/api/v1/vehicles/${id}/mileage`, {
    method: 'POST',
    body: JSON.stringify({ mileageKm }),
  }).then((r) => r.vehicle);

export const removeVehicle = (id: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/vehicles/${id}`, { method: 'DELETE' });
