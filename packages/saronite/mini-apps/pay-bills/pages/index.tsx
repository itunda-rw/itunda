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
import { closeView, getPendingBills, payBill } from '@itunda/saronite-react-native';
import type { PendingBill } from '@itunda/saronite-react-native';

/**
 * A real "life services" mini-app — this category (bill/utility payment)
 * is one of the concrete categories Apps in Toss actually lists for
 * partner mini-apps (see saronite/README.md's research notes). Backed by
 * itunda's real `GET /bills/pending` and `POST /bills/pay`, not mock data.
 * `closeView`/`getPendingBills`/`payBill` go through itunda's own
 * `SaroniteBrownfieldModule` (legacy `NativeModules`, `SaroniteBridge.kt` --
 * a completely separate bridge from granite's real `GraniteBrownfieldModule`
 * TurboModule), so none of this component's own logic was ever affected by
 * the upstream bug below -- only the route registration was blocked.
 *
 * Re-enabled on real granite (2026-07-13) after patching the actual root
 * cause: the vendored `brick-module@0.5.2`'s `BrickModule.get()`
 * (`node_modules/brick-module/dist/BrickModule.js`) eagerly called
 * `TurboModuleRegistry.getEnforcing("BrickModule")` synchronously *inside*
 * `get()` itself, before returning its Proxy -- so the instant anything
 * imported `@granite-js/react-native` (`app.tsx` below, transitively via its
 * `async-bridges.js`/`constant-bridges.js`, confirmed by reading those files
 * directly: both `require("@granite-js/brownfield-module")` at their own
 * top level), that eager resolution raced native's TurboModuleManager
 * registration and crashed JS bundle evaluation before this mini-app could
 * ever register -- not a granite-app-registration-time problem, a
 * module-*import*-time problem. Fixed via `patch-package`
 * (`packages/saronite/patches/brick-module+0.5.2.patch`): `get()` now only
 * resolves the native module lazily, inside the Proxy's own property-access
 * trap, the first time a method is actually called -- by then native
 * registration has long since completed. `app.tsx`/`require.context.ts`/
 * `router.gen.ts` were already real and correct (built 2026-07-12); this
 * change is `pages/index.tsx`'s own real route registration plus
 * re-including those three files in the active build (see tsconfig.json).
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

export const Route = createRoute('/', {
  component: PayBillsPage,
  // Real, live-found fix (2026-07-17): granite's router uses
  // @react-navigation/native-stack, which renders a real native header
  // (react-native-screens' RNSScreenStackHeaderConfig/RNSScreenStackHeaderSubview) by
  // default unless explicitly disabled. That header component crashed on this iOS
  // toolchain -- a real, confirmed `-[RCTView setType:]: unrecognized selector` crash,
  // a native view-config mismatch traced directly to it via a live crash log and view
  // hierarchy dump, not guessed. Disabling it here isn't just a workaround: this screen
  // already renders its own title (`styles.title`) and its own "Close" button, so a
  // second, redundant native header was never wanted in the first place -- same as
  // every other itunda mini-app screen hosted inside a native sheet/activity.
  screenOptions: { headerShown: false },
});

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
  due: { fontSize: 12, color: '#636E7C', marginTop: 2 },
  amount: { fontSize: 15, fontWeight: '700', color: '#191F28', marginRight: 12 },
  payButton: { backgroundColor: '#3182F6', borderRadius: 8, paddingVertical: 8, paddingHorizontal: 14 },
  payButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  closeButton: { alignItems: 'center', paddingVertical: 14 },
  closeButtonText: { color: '#636E7C', fontWeight: '600' },
});
