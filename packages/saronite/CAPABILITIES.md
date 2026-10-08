# Saronite capability reference

This file is generated from `packages/saronite/sdk-manifest.json`.

| Domain | Permission | Stability | Methods |
|---|---|---|---|
| `navigation` | none | stable | `closeView`, `openURL` |
| `account` | identity | stable | `getWalletBalance` |
| `bills` | payments | stable | `getPendingBills`, `payBill`, `getBillProviders`, `buyAirtime`, `getAutoPaySettings`, `setAutoPay`, `clearAutoPay` |
| `rewards` | identity | stable | `getRewardTasks`, `claimRewardTask` |
| `insurance` | payments | stable | `getInsurancePlans`, `getMyPolicies`, `enrollInsurance` |
| `savings` | payments | experimental | `createPremiumFund`, `contributeToFund`, `cancelFund`, `getMyPremiumFunds` |
| `claims` | identity | experimental | `submitClaim`, `getMyClaims` |
| `agriculture` | identity | experimental | `getCropIndexCatalog`, `getMyCropIndexPolicies`, `enrollCropIndexPolicy`, `cancelCropIndexPolicy`, `getCropIndexSeasonIndex` |
| `referrals` | identity | experimental | `getReferralInfo` |
| `activity` | identity | experimental | `reportSteps`, `getTodaySteps` |
| `profile` | identity | experimental | `updateProfilePhoto`, `requestEmailVerification`, `confirmEmailVerification` |

The registry is the source of truth for the public SDK capability inventory. Native host implementations and DevTools mocks must not expose methods absent from the registry.
