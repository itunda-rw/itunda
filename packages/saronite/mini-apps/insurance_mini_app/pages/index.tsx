import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { createRoute } from '@granite-js/react-native';
import {
  cancelCropIndexPolicy,
  cancelFund,
  closeView,
  contributeToFund,
  createPremiumFund,
  enrollCropIndexPolicy,
  enrollInsurance,
  getCropIndexCatalog,
  getInsurancePlans,
  getMyClaims,
  getMyCropIndexPolicies,
  getMyPolicies,
  getMyPremiumFunds,
  submitClaim,
} from '@itunda/saronite-react-native';
import type {
  CropIndexCatalogEntry,
  InsuranceClaim,
  InsurancePlan,
  InsurancePolicy,
  InsurancePremiumFund,
  WeatherIndexCropType,
  WeatherIndexPolicy,
} from '@itunda/saronite-react-native';

/**
 * A real insurance/protection mini-app — this category is one of the
 * concrete categories Apps in Toss actually lists for partner mini-apps.
 * Replaces a previous version of this file that was a disconnected stub:
 * no package.json (so it wasn't even a resolvable workspace member), a
 * `handleApply` that only called `Alert.alert` with no real request, and an
 * import of `@itunda/ids-react-native`, a directory with loose source files
 * and no package.json of its own -- confirmed by direct inspection, not
 * actually installable either. Rebuilt (2026-07-13) on the same real,
 * working pattern pay-bills/wallet-balance/reward-tasks already prove out:
 * backed by itunda's real `GET /insurance/plans`, `GET /insurance/my-policies`,
 * and `POST /insurance/enroll` (services/backend/insurance), not mock data —
 * itunda's own real "Mutuelle de Santé" plan (Rwanda's actual community-based
 * health insurance) is one of the five real plans this now genuinely loads.
 */
export default function InsurancePage() {
  const [plans, setPlans] = useState<InsurancePlan[] | null>(null);
  const [policies, setPolicies] = useState<InsurancePolicy[] | null>(null);
  const [funds, setFunds] = useState<InsurancePremiumFund[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);

  // Real Ejo Heza ya Moto-style premium savings fund -- mirrors bank-mfe's
  // InsuranceView exactly (services/micro-frontends/bank-mfe/src/BankDashboard.tsx),
  // just in RN components instead of web React. Lets a user save toward a specific
  // active policy's next premium ahead of time.
  const [creatingFundPolicyId, setCreatingFundPolicyId] = useState<string | null>(null);
  const [newFundDaily, setNewFundDaily] = useState('0');
  const [fundBusyId, setFundBusyId] = useState<string | null>(null);
  const [contributeAmount, setContributeAmount] = useState<Record<string, string>>({});

  // Real claims filing -- closes InsuranceController.submitClaim/getMyClaims, which
  // existed on the backend (rate-limited, real policy-ownership + active-status checks)
  // with zero mobile client anywhere until now. One open claim form per policy at a time,
  // same UX shape as the premium-fund creation form above.
  const [claims, setClaims] = useState<InsuranceClaim[]>([]);
  const [filingClaimPolicyId, setFilingClaimPolicyId] = useState<string | null>(null);
  const [claimDescription, setClaimDescription] = useState('');
  const [claimAmount, setClaimAmount] = useState('');
  const [claimBusy, setClaimBusy] = useState(false);

  // Real Rwanda NAIS-style parametric/weather-index crop insurance -- closes
  // WeatherIndexInsuranceController, which existed on the backend with zero mobile
  // client anywhere until now. Structurally separate from the claims-based plans above:
  // no individual claim is ever filed, a published district+season rainfall index
  // auto-triggers payout for every enrolled policy at once.
  const [cropCatalog, setCropCatalog] = useState<CropIndexCatalogEntry[] | null>(null);
  const [cropPolicies, setCropPolicies] = useState<WeatherIndexPolicy[]>([]);
  const [enrollingCropType, setEnrollingCropType] = useState<WeatherIndexCropType | null>(null);
  const [cropDistrict, setCropDistrict] = useState('');
  const [cropSeason, setCropSeason] = useState('');
  const [cropInsuredAmount, setCropInsuredAmount] = useState('');
  const [cropBusyKey, setCropBusyKey] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    Promise.all([
      getInsurancePlans(),
      getMyPolicies(),
      getMyPremiumFunds(),
      getMyClaims(),
      getCropIndexCatalog(),
      getMyCropIndexPolicies(),
    ])
      .then(([plansResult, policiesResult, fundsResult, claimsResult, cropCatalogResult, cropPoliciesResult]) => {
        setPlans(plansResult.plans);
        setPolicies(policiesResult.policies);
        setFunds(fundsResult.funds);
        setClaims(claimsResult.claims);
        setCropCatalog(cropCatalogResult.catalog);
        setCropPolicies(cropPoliciesResult.policies);
      })
      .catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const activePlanIds = new Set(
    (policies ?? []).filter((p) => p.status === 'active').map((p) => p.planId),
  );

  const handleEnroll = async (plan: InsurancePlan) => {
    setEnrollingId(plan.id);
    try {
      const result = await enrollInsurance(plan.id);
      Alert.alert('Enrolled', result.message);
      setPolicies((prev) => (prev ? [...prev, result.policy] : [result.policy]));
    } catch (err) {
      Alert.alert("Couldn't enroll", err instanceof Error ? err.message : 'Please try again');
    } finally {
      setEnrollingId(null);
    }
  };

  const handleCreateFund = async (policyId: string) => {
    const daily = Number(newFundDaily || '0');
    if (!Number.isFinite(daily) || daily < 0) {
      Alert.alert('Invalid amount', 'Daily contribution must be zero or a positive number.');
      return;
    }
    setFundBusyId(policyId);
    try {
      const result = await createPremiumFund(policyId, daily);
      setFunds((prev) => [...prev, result.fund]);
      setCreatingFundPolicyId(null);
      setNewFundDaily('0');
      Alert.alert('Started saving', 'Started saving toward your next premium.');
    } catch (err) {
      Alert.alert("Couldn't start saving", err instanceof Error ? err.message : 'Please try again');
    } finally {
      setFundBusyId(null);
    }
  };

  const handleContribute = async (fundId: string) => {
    const amount = Number(contributeAmount[fundId] || '0');
    if (!Number.isFinite(amount) || amount <= 0) {
      Alert.alert('Invalid amount', 'Contribution amount must be greater than zero.');
      return;
    }
    setFundBusyId(fundId);
    try {
      const result = await contributeToFund(fundId, amount);
      setFunds((prev) => prev.map((f) => (f.id === fundId ? result.fund : f)));
      setContributeAmount((prev) => ({ ...prev, [fundId]: '' }));
      Alert.alert('Added', 'Contribution added toward your next premium.');
    } catch (err) {
      Alert.alert("Couldn't add contribution", err instanceof Error ? err.message : 'Please try again');
    } finally {
      setFundBusyId(null);
    }
  };

  const handleSubmitClaim = async (policyId: string) => {
    const trimmedDescription = claimDescription.trim();
    const amount = Number(claimAmount || '0');
    if (!trimmedDescription) {
      Alert.alert('Description required', 'Please describe what happened.');
      return;
    }
    if (!Number.isFinite(amount) || amount <= 0) {
      Alert.alert('Invalid amount', 'Claim amount must be greater than zero.');
      return;
    }
    setClaimBusy(true);
    try {
      const result = await submitClaim(policyId, trimmedDescription, amount);
      setClaims((prev) => [result.claim, ...prev]);
      setFilingClaimPolicyId(null);
      setClaimDescription('');
      setClaimAmount('');
      Alert.alert('Claim submitted', 'Your claim has been submitted for review.');
    } catch (err) {
      Alert.alert("Couldn't submit claim", err instanceof Error ? err.message : 'Please try again');
    } finally {
      setClaimBusy(false);
    }
  };

  const handleEnrollCrop = async (cropType: WeatherIndexCropType) => {
    const trimmedDistrict = cropDistrict.trim();
    const trimmedSeason = cropSeason.trim();
    const insuredAmount = Number(cropInsuredAmount || '0');
    if (!trimmedDistrict) {
      Alert.alert('District required', 'Please enter your district.');
      return;
    }
    if (!trimmedSeason) {
      Alert.alert('Season required', 'Please enter the season (e.g. 2026B).');
      return;
    }
    if (!Number.isFinite(insuredAmount) || insuredAmount <= 0) {
      Alert.alert('Invalid amount', 'Insured amount must be greater than zero.');
      return;
    }
    setCropBusyKey(cropType);
    try {
      const result = await enrollCropIndexPolicy(cropType, trimmedDistrict, trimmedSeason, insuredAmount);
      setCropPolicies((prev) => [result.policy, ...prev]);
      setEnrollingCropType(null);
      setCropDistrict('');
      setCropSeason('');
      setCropInsuredAmount('');
      Alert.alert('Enrolled', 'Your crop is now covered for this season.');
    } catch (err) {
      Alert.alert("Couldn't enroll", err instanceof Error ? err.message : 'Please try again');
    } finally {
      setCropBusyKey(null);
    }
  };

  const handleCancelCropPolicy = async (policyId: string) => {
    setCropBusyKey(policyId);
    try {
      const result = await cancelCropIndexPolicy(policyId);
      setCropPolicies((prev) => prev.map((p) => (p.id === policyId ? result.policy : p)));
      Alert.alert('Cancelled', 'Crop policy cancelled.');
    } catch (err) {
      Alert.alert("Couldn't cancel", err instanceof Error ? err.message : 'Please try again');
    } finally {
      setCropBusyKey(null);
    }
  };

  const handleCancelFund = async (fundId: string) => {
    setFundBusyId(fundId);
    try {
      const result = await cancelFund(fundId);
      setFunds((prev) => prev.map((f) => (f.id === fundId ? result.fund : f)));
      Alert.alert('Cancelled', 'Premium fund cancelled and refunded to your wallet.');
    } catch (err) {
      Alert.alert("Couldn't cancel", err instanceof Error ? err.message : 'Please try again');
    } finally {
      setFundBusyId(null);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Insurance</Text>

      {plans === null && !error && <ActivityIndicator style={styles.spacer} />}

      {error && <Text style={[styles.body, styles.error]}>Couldn't load insurance: {error}</Text>}

      {policies !== null && policies.length > 0 && (
        <View style={styles.myPoliciesSection}>
          <Text style={styles.sectionLabel}>My coverage</Text>
          {policies.map((policy) => {
            const fund = funds.find((f) => f.policyId === policy.id && f.status === 'active');
            const pct =
              fund && fund.targetAmount > 0
                ? Math.min(100, Math.round((fund.currentAmount / fund.targetAmount) * 100))
                : 0;
            return (
              <View key={policy.id} style={styles.policyCard}>
                <View style={styles.policyRow}>
                  <Text style={styles.policyName}>{policy.planName}</Text>
                  <Text style={styles.policyStatus}>{policy.status}</Text>
                </View>

                {policy.status === 'active' && !fund && (
                  <View style={styles.fundSection}>
                    {creatingFundPolicyId === policy.id ? (
                      <View style={styles.fundForm}>
                        <TextInput
                          style={styles.fundInput}
                          keyboardType="numeric"
                          placeholder="Daily contribution (0 = manual only)"
                          value={newFundDaily}
                          onChangeText={setNewFundDaily}
                        />
                        <View style={styles.fundButtonRow}>
                          <TouchableOpacity
                            style={styles.fundButton}
                            disabled={fundBusyId === policy.id}
                            onPress={() => handleCreateFund(policy.id)}
                          >
                            <Text style={styles.fundButtonText}>
                              {fundBusyId === policy.id ? '...' : 'Start saving'}
                            </Text>
                          </TouchableOpacity>
                          <TouchableOpacity
                            style={styles.fundButtonSecondary}
                            onPress={() => setCreatingFundPolicyId(null)}
                          >
                            <Text style={styles.fundButtonSecondaryText}>Cancel</Text>
                          </TouchableOpacity>
                        </View>
                      </View>
                    ) : (
                      <TouchableOpacity
                        style={styles.fundButton}
                        onPress={() => setCreatingFundPolicyId(policy.id)}
                      >
                        <Text style={styles.fundButtonText}>Save for next premium</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                )}

                {fund && (
                  <View style={styles.fundSection}>
                    <Text style={styles.fundProgressLabel}>
                      Saved toward next premium: {fund.currentAmount.toLocaleString()} /{' '}
                      {fund.targetAmount.toLocaleString()} RWF
                    </Text>
                    <View style={styles.fundProgressTrack}>
                      <View style={[styles.fundProgressFill, { width: `${pct}%` }]} />
                    </View>
                    <View style={styles.fundButtonRow}>
                      <TextInput
                        style={[styles.fundInput, styles.fundInputFlex]}
                        keyboardType="numeric"
                        placeholder="Add amount (RWF)"
                        value={contributeAmount[fund.id] ?? ''}
                        onChangeText={(text) =>
                          setContributeAmount((prev) => ({ ...prev, [fund.id]: text }))
                        }
                      />
                      <TouchableOpacity
                        style={styles.fundButton}
                        disabled={fundBusyId === fund.id}
                        onPress={() => handleContribute(fund.id)}
                      >
                        <Text style={styles.fundButtonText}>
                          {fundBusyId === fund.id ? '...' : 'Add'}
                        </Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={styles.fundButtonSecondary}
                        disabled={fundBusyId === fund.id}
                        onPress={() => handleCancelFund(fund.id)}
                      >
                        <Text style={styles.fundButtonSecondaryText}>Cancel fund</Text>
                      </TouchableOpacity>
                    </View>
                  </View>
                )}

                {policy.status === 'active' && (
                  <View style={styles.fundSection}>
                    {filingClaimPolicyId === policy.id ? (
                      <View style={styles.fundForm}>
                        <TextInput
                          style={styles.fundInput}
                          placeholder="What happened?"
                          value={claimDescription}
                          onChangeText={setClaimDescription}
                          multiline
                        />
                        <TextInput
                          style={styles.fundInput}
                          keyboardType="numeric"
                          placeholder="Claim amount (RWF)"
                          value={claimAmount}
                          onChangeText={setClaimAmount}
                        />
                        <View style={styles.fundButtonRow}>
                          <TouchableOpacity
                            style={styles.fundButton}
                            disabled={claimBusy}
                            onPress={() => handleSubmitClaim(policy.id)}
                          >
                            <Text style={styles.fundButtonText}>{claimBusy ? '...' : 'Submit claim'}</Text>
                          </TouchableOpacity>
                          <TouchableOpacity
                            style={styles.fundButtonSecondary}
                            onPress={() => setFilingClaimPolicyId(null)}
                          >
                            <Text style={styles.fundButtonSecondaryText}>Cancel</Text>
                          </TouchableOpacity>
                        </View>
                      </View>
                    ) : (
                      <TouchableOpacity
                        style={styles.fundButtonSecondary}
                        onPress={() => {
                          setFilingClaimPolicyId(policy.id);
                          setClaimDescription('');
                          setClaimAmount('');
                        }}
                      >
                        <Text style={styles.fundButtonSecondaryText}>File a claim</Text>
                      </TouchableOpacity>
                    )}
                  </View>
                )}
              </View>
            );
          })}
        </View>
      )}

      {claims.length > 0 && (
        <View style={styles.myPoliciesSection}>
          <Text style={styles.sectionLabel}>My claims</Text>
          {claims.map((claim) => (
            <View key={claim.id} style={styles.policyCard}>
              <View style={styles.policyRow}>
                <Text style={styles.policyName}>{claim.description}</Text>
                <Text
                  style={[
                    styles.policyStatus,
                    claim.status === 'REJECTED' && styles.claimStatusRejected,
                    claim.status === 'SUBMITTED' && styles.claimStatusPending,
                  ]}
                >
                  {claim.status}
                </Text>
              </View>
              <Text style={styles.fundProgressLabel}>{claim.amount.toLocaleString()} RWF</Text>
              {claim.decisionReason && (
                <Text style={styles.fundProgressLabel}>{claim.decisionReason}</Text>
              )}
            </View>
          ))}
        </View>
      )}

      {cropPolicies.length > 0 && (
        <View style={styles.myPoliciesSection}>
          <Text style={styles.sectionLabel}>My crop policies</Text>
          {cropPolicies.map((policy) => (
            <View key={policy.id} style={styles.policyCard}>
              <View style={styles.policyRow}>
                <Text style={styles.policyName}>
                  {policy.cropType} · {policy.district} · {policy.season}
                </Text>
                <Text
                  style={[
                    styles.policyStatus,
                    policy.status === 'CANCELLED' && styles.claimStatusPending,
                    policy.status === 'SEASON_ENDED_NO_PAYOUT' && styles.claimStatusPending,
                  ]}
                >
                  {policy.status}
                </Text>
              </View>
              <Text style={styles.fundProgressLabel}>
                Insured {policy.insuredAmount.toLocaleString()} RWF · Premium{' '}
                {policy.premiumAmount.toLocaleString()} RWF
              </Text>
              {policy.status === 'ENROLLED' && (
                <TouchableOpacity
                  style={[styles.fundButtonSecondary, styles.fundSection]}
                  disabled={cropBusyKey === policy.id}
                  onPress={() => handleCancelCropPolicy(policy.id)}
                >
                  <Text style={styles.fundButtonSecondaryText}>
                    {cropBusyKey === policy.id ? '...' : 'Cancel policy'}
                  </Text>
                </TouchableOpacity>
              )}
            </View>
          ))}
        </View>
      )}

      {cropCatalog && (
        <View style={styles.myPoliciesSection}>
          <Text style={styles.sectionLabel}>Crop insurance</Text>
          {cropCatalog.map((entry) => (
            <View key={entry.cropType} style={styles.row}>
              <View style={styles.cropCard}>
                <View style={styles.rowLeft}>
                  <Text style={styles.planName}>{entry.name}</Text>
                  <Text style={styles.provider}>{entry.description}</Text>
                  <Text style={styles.premium}>{entry.premiumRatePercent}% of insured amount</Text>
                </View>
                {enrollingCropType === entry.cropType ? (
                  <View style={[styles.fundForm, styles.fundSection]}>
                    <TextInput
                      style={styles.fundInput}
                      placeholder="District"
                      value={cropDistrict}
                      onChangeText={setCropDistrict}
                    />
                    <TextInput
                      style={styles.fundInput}
                      placeholder="Season (e.g. 2026B)"
                      value={cropSeason}
                      onChangeText={setCropSeason}
                    />
                    <TextInput
                      style={styles.fundInput}
                      keyboardType="numeric"
                      placeholder="Insured amount (RWF)"
                      value={cropInsuredAmount}
                      onChangeText={setCropInsuredAmount}
                    />
                    <View style={styles.fundButtonRow}>
                      <TouchableOpacity
                        style={styles.fundButton}
                        disabled={cropBusyKey === entry.cropType}
                        onPress={() => handleEnrollCrop(entry.cropType)}
                      >
                        <Text style={styles.fundButtonText}>
                          {cropBusyKey === entry.cropType ? '...' : 'Enroll'}
                        </Text>
                      </TouchableOpacity>
                      <TouchableOpacity
                        style={styles.fundButtonSecondary}
                        onPress={() => setEnrollingCropType(null)}
                      >
                        <Text style={styles.fundButtonSecondaryText}>Cancel</Text>
                      </TouchableOpacity>
                    </View>
                  </View>
                ) : (
                  <TouchableOpacity
                    style={[styles.fundButtonSecondary, styles.fundSection]}
                    onPress={() => {
                      setEnrollingCropType(entry.cropType);
                      setCropDistrict('');
                      setCropSeason('');
                      setCropInsuredAmount('');
                    }}
                  >
                    <Text style={styles.fundButtonSecondaryText}>Insure this crop</Text>
                  </TouchableOpacity>
                )}
              </View>
            </View>
          ))}
        </View>
      )}

      {plans && (
        <>
          <Text style={styles.sectionLabel}>Available plans</Text>
          <FlatList
            data={plans}
            keyExtractor={(p) => p.id}
            renderItem={({ item }) => {
              const alreadyEnrolled = activePlanIds.has(item.id);
              return (
                <View style={styles.row}>
                  <View style={styles.rowLeft}>
                    <Text style={styles.planName}>{item.name}</Text>
                    <Text style={styles.provider}>{item.provider}</Text>
                    <Text style={styles.premium}>
                      {item.monthlyPremium.toLocaleString()} RWF / month
                    </Text>
                  </View>
                  <TouchableOpacity
                    style={[styles.enrollButton, alreadyEnrolled && styles.enrollButtonDone]}
                    disabled={alreadyEnrolled || enrollingId === item.id}
                    onPress={() => handleEnroll(item)}
                  >
                    <Text style={styles.enrollButtonText}>
                      {alreadyEnrolled
                        ? 'Enrolled'
                        : enrollingId === item.id
                          ? 'Enrolling…'
                          : 'Enroll'}
                    </Text>
                  </TouchableOpacity>
                </View>
              );
            }}
          />
        </>
      )}

      <View style={styles.spacer} />
      <TouchableOpacity style={styles.closeButton} onPress={() => closeView()}>
        <Text style={styles.closeButtonText}>Close</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

export const Route = createRoute('/', {
  component: InsurancePage,
});

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: '#F2F4F6' },
  title: { fontSize: 20, fontWeight: '800', marginBottom: 16, color: '#191F28' },
  body: { fontSize: 15, color: '#4E5968' },
  error: { color: '#F04452' },
  spacer: { flex: 1 },
  sectionLabel: { fontSize: 13, fontWeight: '700', color: '#8B95A1', marginBottom: 8, marginTop: 4 },
  myPoliciesSection: { marginBottom: 16 },
  policyCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 12,
    marginBottom: 8,
  },
  policyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  policyName: { fontSize: 14, fontWeight: '700', color: '#191F28' },
  // Real WCAG AA contrast fix (item 240, docs/ACCESSIBILITY.md finding #2): #04C065
  // measures 2.40:1 against this card's white background, failing even the lenient
  // 3.0:1 AA-large/UI threshold. Darkened to #05804A (5.01:1), matching the same fix
  // applied to packages/design-tokens/tokens.css --toss-green and the Android/iOS
  // semantic `success` token.
  policyStatus: { fontSize: 12, color: '#05804A', fontWeight: '700', textTransform: 'capitalize' },
  claimStatusPending: { color: '#8B95A1' },
  claimStatusRejected: { color: '#F04452' },
  fundSection: { marginTop: 10, gap: 8 },
  fundForm: { gap: 8 },
  fundInput: {
    borderWidth: 1,
    borderColor: '#E5E8EB',
    borderRadius: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    fontSize: 13,
    color: '#191F28',
  },
  fundInputFlex: { flex: 1 },
  fundButtonRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  fundButton: {
    backgroundColor: '#3182F6',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  fundButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  fundButtonSecondary: {
    backgroundColor: '#E5E8EB',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  fundButtonSecondaryText: { color: '#4E5968', fontWeight: '700', fontSize: 13 },
  fundProgressLabel: { fontSize: 11, color: '#8B95A1' },
  fundProgressTrack: {
    height: 6,
    borderRadius: 3,
    backgroundColor: '#E5E8EB',
    overflow: 'hidden',
  },
  fundProgressFill: { height: '100%', backgroundColor: '#3182F6' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 10,
  },
  rowLeft: { flex: 1 },
  cropCard: { flex: 1 },
  planName: { fontSize: 15, fontWeight: '700', color: '#191F28' },
  provider: { fontSize: 12, color: '#8B95A1', marginTop: 2 },
  premium: { fontSize: 13, color: '#4E5968', marginTop: 4, fontWeight: '600' },
  enrollButton: { backgroundColor: '#3182F6', borderRadius: 8, paddingVertical: 8, paddingHorizontal: 14 },
  enrollButtonDone: { backgroundColor: '#E5E8EB' },
  enrollButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  closeButton: { alignItems: 'center', paddingVertical: 14 },
  closeButtonText: { color: '#8B95A1', fontWeight: '600' },
});
