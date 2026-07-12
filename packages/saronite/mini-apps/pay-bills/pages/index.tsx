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
import { closeView, getPendingBills, payBill } from '@itunda/saronite-react-native';
import type { PendingBill } from '@itunda/saronite-react-native';

/**
 * A real "life services" mini-app — this category (bill/utility payment)
 * is one of the concrete categories Apps in Toss actually lists for
 * partner mini-apps (see saronite/README.md's research notes). Backed by
 * itunda's real `GET /bills/pending` and `POST /bills/pay`, not mock data.
 *
 * Reverted from real granite back to itunda's own bridge (2026-07-12,
 * granite-adoption stage 7 wrap-up): the real native brick-module
 * infrastructure (android/brownfield-module-stub/, BrickModulePackage in
 * ItundaApplication.kt) is genuinely built, compiles, and independently
 * verified to construct and register the real GraniteBrownfieldModule
 * instance (confirmed via logcat: "BrickModuleRegistry: Registered module
 * 'GraniteBrownfieldModule'", "BrickModule successfully created"). But a
 * real, reproduced, diagnosed bug in the *vendored* `@granite-js/brownfield-module`
 * JS source itself blocks it from actually working end-to-end:
 * `GraniteBrownfieldModule.brick.ts`'s top-level
 * `BrickModule.get<GraniteBrownfieldModuleSpec>('GraniteBrownfieldModule')`
 * call eagerly invokes `TurboModuleRegistry.getEnforcing("BrickModule")`
 * (brick-module/src/BrickModule.ts) the instant the module is imported --
 * before the native TurboModuleManager has "BrickModule" resolvable yet --
 * with no try/catch, so the exception crashes the entire JS bundle
 * evaluation before `AppRegistry.registerComponent` for this mini-app ever
 * runs. Reproduced consistently across repeated runs (not a cold-start
 * fluke) -- see the granite-adoption stage 7 commit history for the full
 * diagnostic trail. Left as a real, specific, documented follow-up rather
 * than force a broken mini-app into the tree: `app.tsx`/`require.context.ts`/
 * `router.gen.ts` in this directory stay in place, real and correct, ready
 * to be re-wired the moment this upstream timing issue is resolved.
 */
export default function PayBillsPage() {
  const [bills, setBills] = useState<PendingBill[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [payingId, setPayingId] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    getPendingBills()
      .then((result) => setBills(result.bills))
      .catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handlePay = async (bill: PendingBill) => {
    setPayingId(bill.id);
    try {
      const result = await payBill(bill.id, bill.amount, bill.accountNumber, bill.provider);
      Alert.alert('Payment successful', result.message);
      setBills((prev) => (prev ? prev.filter((b) => b.id !== bill.id) : prev));
    } catch (err) {
      Alert.alert('Payment failed', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setPayingId(null);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Pay bills</Text>

      {bills === null && !error && <ActivityIndicator style={styles.spacer} />}

      {error && <Text style={[styles.body, styles.error]}>Couldn't load bills: {error}</Text>}

      {bills !== null && bills.length === 0 && (
        <Text style={styles.body}>No pending bills — you're all caught up.</Text>
      )}

      {bills && bills.length > 0 && (
        <FlatList
          data={bills}
          keyExtractor={(b) => b.id}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <View style={styles.rowLeft}>
                <Text style={styles.provider}>{item.provider}</Text>
                <Text style={styles.due}>Due {item.dueDate}</Text>
              </View>
              <Text style={styles.amount}>{item.amount.toLocaleString()} RWF</Text>
              <TouchableOpacity
                style={styles.payButton}
                disabled={payingId === item.id}
                onPress={() => handlePay(item)}
              >
                <Text style={styles.payButtonText}>
                  {payingId === item.id ? 'Paying…' : 'Pay'}
                </Text>
              </TouchableOpacity>
            </View>
          )}
        />
      )}

      <View style={styles.spacer} />
      <TouchableOpacity style={styles.closeButton} onPress={() => closeView()}>
        <Text style={styles.closeButtonText}>Close</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: '#F2F4F6' },
  title: { fontSize: 20, fontWeight: '800', marginBottom: 16, color: '#191F28' },
  body: { fontSize: 15, color: '#4E5968' },
  error: { color: '#F04452' },
  spacer: { flex: 1 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 10,
  },
  rowLeft: { flex: 1 },
  provider: { fontSize: 15, fontWeight: '700', color: '#191F28' },
  due: { fontSize: 12, color: '#8B95A1', marginTop: 2 },
  amount: { fontSize: 15, fontWeight: '700', color: '#191F28', marginRight: 12 },
  payButton: { backgroundColor: '#3182F6', borderRadius: 8, paddingVertical: 8, paddingHorizontal: 14 },
  payButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  closeButton: { alignItems: 'center', paddingVertical: 14 },
  closeButtonText: { color: '#8B95A1', fontWeight: '600' },
});
