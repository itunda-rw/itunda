import React, { useCallback, useState } from 'react';
import { colors } from '../../../../packages/ids-react-native/src/colors';
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
 * Deliberately calls TWO real bridge methods to exercise the current trust boundary:
 *  - getWalletBalance(): first-party-only. PartnerMiniAppPermissions.ALLOWED does not
 *    grant wallet:read, so a partner bundle must receive SARONITE_SCOPE_DENIED before
 *    the native bridge reads credentials or calls the account endpoint.
 *  - getPendingBills(): no approved partner scope covers bill data, so it must also
 *    receive SARONITE_SCOPE_DENIED. These calls verify runtime enforcement rather than
 *    relying on the partner manifest or UI to hide sensitive bridge methods.
 */
export default function PartnerDemoPage() {
  const [walletResult, setWalletResult] = useState<string | null>(null);
  const [billsResult, setBillsResult] = useState<string | null>(null);

  const runWalletCall = useCallback(() => {
    getWalletBalance()
      .then((result) => setWalletResult(`wallet access unexpectedly SUCCEEDED (first-party-only boundary is broken)`))
      .catch((err: Error) => setWalletResult(`wallet access denied as expected -- ${err.message}`));
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
        <Text style={styles.actionButtonText}>Call getWalletBalance() -- first-party only</Text>
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
  container: { flex: 1, padding: 24, backgroundColor: colors.background },
  title: { fontSize: 20, fontWeight: '700', marginBottom: 12, color: colors.textPrimary },
  body: { fontSize: 14, color: colors.textSecondary },
  spacer: { height: 16 },
  actionButton: { backgroundColor: colors.primaryIndigo, paddingVertical: 14, borderRadius: 8, alignItems: 'center' },
  actionButtonText: { color: colors.surface, fontWeight: '700' },
  result: { marginTop: 8, fontSize: 13, color: colors.textPrimary },
  closeButton: { paddingVertical: 14, alignItems: 'center' },
  closeButtonText: { color: colors.textTertiary, fontWeight: '600' },
});
