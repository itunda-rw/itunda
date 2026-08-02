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
  cancelFund,
  closeView,
  contributeToFund,
  createPremiumFund,
  enrollInsurance,
  getInsurancePlans,
  getMyPolicies,
  getMyPremiumFunds,
} from '@itunda/saronite-react-native';
import type {
  InsurancePlan,
  InsurancePolicy,
  InsurancePremiumFund,
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

  const load = useCallback(() => {
    setError(null);
    Promise.all([getInsurancePlans(), getMyPolicies(), getMyPremiumFunds()])
      .then(([plansResult, policiesResult, fundsResult]) => {
        setPlans(plansResult.plans);
        setPolicies(policiesResult.policies);
        setFunds(fundsResult.funds);
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
              </View>
            );
          })}
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
  policyStatus: { fontSize: 12, color: '#04C065', fontWeight: '700', textTransform: 'capitalize' },
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
  planName: { fontSize: 15, fontWeight: '700', color: '#191F28' },
  provider: { fontSize: 12, color: '#8B95A1', marginTop: 2 },
  premium: { fontSize: 13, color: '#4E5968', marginTop: 4, fontWeight: '600' },
  enrollButton: { backgroundColor: '#3182F6', borderRadius: 8, paddingVertical: 8, paddingHorizontal: 14 },
  enrollButtonDone: { backgroundColor: '#E5E8EB' },
  enrollButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  closeButton: { alignItems: 'center', paddingVertical: 14 },
  closeButtonText: { color: '#8B95A1', fontWeight: '600' },
});
