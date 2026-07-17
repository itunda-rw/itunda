import React, { useCallback, useState } from 'react';
import { SafeAreaView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { createRoute } from '@granite-js/react-native';
import { closeView, getWalletBalance, getPendingBills } from '@itunda/saronite-react-native';

/**
 * Real, minimal third-party mini-app UI used to live-verify
 * docs/TOSS_PARITY_MATRIX.md's Partner SDK mobile runtime loader (2026-07-17) --
 * "Hello from Partner Demo Co", proving the mechanism (download + render inside
 * granite's real runtime, on the exact same native bridge every first-party mini-app
 * uses) rather than anything visually elaborate.
 *
 * Deliberately calls TWO real bridge methods to exercise both real behaviors this
 * pass built:
 *  - getWalletBalance(): the one call `PartnerMiniAppPermissions.ALLOWED`'s
 *    "wallet:read" scope actually covers -- this partner's real submitted manifest
 *    requests exactly that scope, so this should genuinely succeed against the real
 *    backend, proving the reused bridge/host-bridge/network stack.
 *  - getPendingBills(): covered by no scope at all (no "bills:read" exists in the
 *    allow-list) -- this should always fail with the real SARONITE_SCOPE_DENIED
 *    rejection SaroniteBridge.kt's `requireScope(null, promise)` produces, proving
 *    runtime scope enforcement is real, not just review-time visibility of the
 *    requested scopes.
 */
export default function PartnerDemoPage() {
  const [walletResult, setWalletResult] = useState<string | null>(null);
  const [billsResult, setBillsResult] = useState<string | null>(null);

  const runWalletCall = useCallback(() => {
    getWalletBalance()
      .then((result) => setWalletResult(`wallet:read OK -- total balance ${result.totalBalance} ${result.currency}`))
      .catch((err: Error) => setWalletResult(`wallet:read FAILED -- ${err.message}`));
  }, []);

  const runBillsCall = useCallback(() => {
    getPendingBills()
      .then(() => setBillsResult('bills call unexpectedly SUCCEEDED (scope enforcement is broken)'))
      .catch((err: Error) => setBillsResult(`bills call denied as expected -- ${err.message}`));
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Hello from Partner Demo Co</Text>
      <Text style={styles.body}>
        This screen's JS was downloaded at runtime from a real bundleUrl -- it was never
        compiled into itunda's own app.
      </Text>

      <View style={styles.spacer} />
      <TouchableOpacity style={styles.actionButton} onPress={runWalletCall}>
        <Text style={styles.actionButtonText}>Call getWalletBalance() -- approved scope</Text>
      </TouchableOpacity>
      {walletResult && <Text style={styles.result}>{walletResult}</Text>}

      <View style={styles.spacer} />
      <TouchableOpacity style={styles.actionButton} onPress={runBillsCall}>
        <Text style={styles.actionButtonText}>Call getPendingBills() -- no approved scope</Text>
      </TouchableOpacity>
      {billsResult && <Text style={styles.result}>{billsResult}</Text>}

      <View style={styles.spacer} />
      <TouchableOpacity style={styles.closeButton} onPress={() => closeView()}>
        <Text style={styles.closeButtonText}>Close</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

export const Route = createRoute('/', {
  component: PartnerDemoPage,
  screenOptions: { headerShown: false },
});

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: '#F2F4F6' },
  title: { fontSize: 20, fontWeight: '800', marginBottom: 12, color: '#191F28' },
  body: { fontSize: 14, color: '#4E5968' },
  spacer: { height: 16 },
  actionButton: { backgroundColor: '#3182F6', paddingVertical: 14, borderRadius: 8, alignItems: 'center' },
  actionButtonText: { color: '#FFFFFF', fontWeight: '700' },
  result: { marginTop: 8, fontSize: 13, color: '#191F28' },
  closeButton: { paddingVertical: 14, alignItems: 'center' },
  closeButtonText: { color: '#8B95A1', fontWeight: '600' },
});
