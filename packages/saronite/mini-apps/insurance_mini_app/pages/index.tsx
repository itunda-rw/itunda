import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { createRoute } from '@granite-js/react-native';
import {
  closeView,
  enrollInsurance,
  getInsurancePlans,
  getMyPolicies,
} from '@itunda/saronite-react-native';
import type { InsurancePlan, InsurancePolicy } from '@itunda/saronite-react-native';

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
  const [error, setError] = useState<string | null>(null);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    Promise.all([getInsurancePlans(), getMyPolicies()])
      .then(([plansResult, policiesResult]) => {
        setPlans(plansResult.plans);
        setPolicies(policiesResult.policies);
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

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Insurance</Text>

      {plans === null && !error && <ActivityIndicator style={styles.spacer} />}

      {error && <Text style={[styles.body, styles.error]}>Couldn't load insurance: {error}</Text>}

      {policies !== null && policies.length > 0 && (
        <View style={styles.myPoliciesSection}>
          <Text style={styles.sectionLabel}>My coverage</Text>
          {policies.map((policy) => (
            <View key={policy.id} style={styles.policyRow}>
              <Text style={styles.policyName}>{policy.planName}</Text>
              <Text style={styles.policyStatus}>{policy.status}</Text>
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
  policyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 12,
    marginBottom: 8,
  },
  policyName: { fontSize: 14, fontWeight: '700', color: '#191F28' },
  policyStatus: { fontSize: 12, color: '#04C065', fontWeight: '700', textTransform: 'capitalize' },
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
