import React, { useCallback, useEffect, useState } from 'react';
import { colors } from '../../../../packages/ids-react-native/src/colors';
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
  closeView,
  getPendingBills,
  payBill,
  getBillProviders,
  buyAirtime,
  getAutoPaySettings,
  setAutoPay,
  clearAutoPay,
} from '@itunda/saronite-react-native';
import type { PendingBill, BillProvider, BillAutoPaySetting } from '@itunda/saronite-react-native';

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
 *
 * Real Kakao Pay 자동납부 (automatic bill payment) section added (2026-08-18) --
 * `getBillProviders`/`getAutoPaySettings`/`setAutoPay`/`clearAutoPay` were wired to
 * bank-mfe already (docs Section 162) but never reached this mini-app or its native
 * `SaroniteBridge.kt` bridge, even though the base pay-bill flow above has been real
 * since 2026-07-13. `getBillProviders` is needed here for the same reason bank-mfe's
 * own picker needs it: `setAutoPay`/`clearAutoPay` take a real providerId (e.g. "b1"),
 * not `PendingBill.provider`'s display name -- there is no other honest way for this
 * screen to resolve which provider a user means.
 *
 * "Buy airtime" section added (2026-09-07, Bills product-completeness pass) --
 * bank-mfe's web BillsView.tsx has had this since 2026-08-17, but Android/iOS never
 * did: their only path to Bills is this mini-app, and `buyAirtime` had never reached
 * the native bridge on either platform until now. Provider selection is optional
 * (an empty string means "default provider", same as bank-mfe's own `|| undefined`
 * fallback) since airtime purchases don't need the same providerId precision
 * `setAutoPay` requires -- the backend's `BuyAirtimeRequest.provider` is a plain,
 * optional display-name string, not an id.
 */
export default function PayBillsPage() {
  const [bills, setBills] = useState<PendingBill[] | null>(null);
  const [providers, setProviders] = useState<BillProvider[]>([]);
  const [airtimeProviders, setAirtimeProviders] = useState<BillProvider[]>([]);
  const [autoPaySettings, setAutoPaySettings] = useState<BillAutoPaySetting[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [payingId, setPayingId] = useState<string | null>(null);
  const [autoPayProviderIndex, setAutoPayProviderIndex] = useState(0);
  const [autoPayAccount, setAutoPayAccount] = useState('');
  const [autoPayMax, setAutoPayMax] = useState('');
  const [savingAutoPay, setSavingAutoPay] = useState(false);
  const [clearingProviderId, setClearingProviderId] = useState<string | null>(null);
  const [airtimePhone, setAirtimePhone] = useState('');
  const [airtimeAmount, setAirtimeAmount] = useState('');
  const [airtimeProviderIndex, setAirtimeProviderIndex] = useState<number | null>(null);
  const [buyingAirtime, setBuyingAirtime] = useState(false);

  const load = useCallback(() => {
    setError(null);
    getPendingBills()
      .then((result) => setBills(result.bills))
      .catch((err: Error) => setError(err.message));
    getBillProviders()
      .then((result) => {
        setProviders(result.providers.filter((p) => p.category !== 'airtime'));
        setAirtimeProviders(result.providers.filter((p) => p.category === 'airtime'));
      })
      .catch(() => {
        setProviders([]);
        setAirtimeProviders([]);
      });
    getAutoPaySettings()
      .then((result) => setAutoPaySettings(result.autoPay))
      .catch(() => setAutoPaySettings([]));
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

  const handleBuyAirtime = async () => {
    const amount = Number(airtimeAmount);
    if (!airtimePhone.trim() || !Number.isFinite(amount) || amount <= 0) {
      Alert.alert('Buy airtime', 'Enter a phone number and a valid amount.');
      return;
    }
    const provider = airtimeProviderIndex !== null ? airtimeProviders[airtimeProviderIndex] : undefined;
    setBuyingAirtime(true);
    try {
      const result = await buyAirtime(airtimePhone.trim(), amount, provider?.name ?? '');
      Alert.alert('Airtime sent', result.message);
      setAirtimePhone('');
      setAirtimeAmount('');
    } catch (err) {
      Alert.alert('Airtime purchase failed', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setBuyingAirtime(false);
    }
  };

  const handleSetAutoPay = async () => {
    const provider = providers[autoPayProviderIndex];
    const maxAmount = Number(autoPayMax);
    if (!provider || !autoPayAccount.trim() || !Number.isFinite(maxAmount) || maxAmount <= 0) {
      Alert.alert('Auto-pay', 'Choose a biller, account number, and a valid maximum amount.');
      return;
    }
    setSavingAutoPay(true);
    try {
      const result = await setAutoPay(provider.id, autoPayAccount.trim(), maxAmount);
      setAutoPaySettings((prev) => [...prev.filter((s) => s.providerId !== provider.id), result.autoPay]);
      setAutoPayAccount('');
      setAutoPayMax('');
      Alert.alert('Auto-pay on', `${provider.name} will be paid automatically, up to ${maxAmount.toLocaleString()} RWF.`);
    } catch (err) {
      Alert.alert('Could not set up auto-pay', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setSavingAutoPay(false);
    }
  };

  const handleClearAutoPay = async (providerId: string) => {
    setClearingProviderId(providerId);
    try {
      await clearAutoPay(providerId);
      setAutoPaySettings((prev) => prev.filter((s) => s.providerId !== providerId));
    } catch (err) {
      Alert.alert('Could not turn off auto-pay', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setClearingProviderId(null);
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

      <View style={styles.autoPayCard}>
        <Text style={styles.autoPayTitle}>Buy airtime</Text>
        <Text style={styles.autoPaySubtitle}>Top up any phone number instantly.</Text>
        <TextInput
          placeholder="Phone number"
          placeholderTextColor="#8B95A1"
          keyboardType="phone-pad"
          value={airtimePhone}
          onChangeText={setAirtimePhone}
          style={styles.input}
        />
        <TextInput
          placeholder="Amount (RWF)"
          placeholderTextColor="#8B95A1"
          keyboardType="numeric"
          value={airtimeAmount}
          onChangeText={setAirtimeAmount}
          style={styles.input}
        />
        {airtimeProviders.length > 0 && (
          <View style={styles.providerPicker}>
            {airtimeProviders.map((p, i) => (
              <TouchableOpacity
                key={p.id}
                style={[styles.providerChip, i === airtimeProviderIndex && styles.providerChipSelected]}
                onPress={() => setAirtimeProviderIndex(i === airtimeProviderIndex ? null : i)}
              >
                <Text style={i === airtimeProviderIndex ? styles.providerChipTextSelected : styles.providerChipText}>
                  {p.logo} {p.name}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        )}
        <TouchableOpacity style={styles.payButton} disabled={buyingAirtime} onPress={handleBuyAirtime}>
          <Text style={styles.payButtonText}>{buyingAirtime ? 'Sending…' : 'Buy airtime'}</Text>
        </TouchableOpacity>
      </View>

      {providers.length > 0 && (
        <View style={styles.autoPayCard}>
          <Text style={styles.autoPayTitle}>Auto-pay</Text>
          <Text style={styles.autoPaySubtitle}>
            Register a bill once and it's paid automatically every cycle, up to the cap you set.
          </Text>

          {autoPaySettings.filter((s) => s.active).map((s) => {
            const provider = providers.find((p) => p.id === s.providerId);
            return (
              <View key={s.id} style={styles.autoPayRow}>
                <View style={styles.rowLeft}>
                  <Text style={styles.provider}>{provider ? `${provider.logo} ${provider.name}` : s.providerId}</Text>
                  <Text style={styles.due}>{s.accountNumber} · up to {s.maxAmount.toLocaleString()} RWF</Text>
                </View>
                <TouchableOpacity
                  style={styles.offButton}
                  disabled={clearingProviderId === s.providerId}
                  onPress={() => handleClearAutoPay(s.providerId)}
                >
                  <Text style={styles.offButtonText}>
                    {clearingProviderId === s.providerId ? '…' : 'Turn off'}
                  </Text>
                </TouchableOpacity>
              </View>
            );
          })}

          <View style={styles.providerPicker}>
            {providers.map((p, i) => (
              <TouchableOpacity
                key={p.id}
                style={[styles.providerChip, i === autoPayProviderIndex && styles.providerChipSelected]}
                onPress={() => setAutoPayProviderIndex(i)}
              >
                <Text style={i === autoPayProviderIndex ? styles.providerChipTextSelected : styles.providerChipText}>
                  {p.logo} {p.name}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
          <TextInput
            placeholder="Account number"
            placeholderTextColor="#8B95A1"
            value={autoPayAccount}
            onChangeText={setAutoPayAccount}
            style={styles.input}
          />
          <TextInput
            placeholder="Maximum amount per bill (RWF)"
            placeholderTextColor="#8B95A1"
            keyboardType="numeric"
            value={autoPayMax}
            onChangeText={setAutoPayMax}
            style={styles.input}
          />
          <TouchableOpacity style={styles.payButton} disabled={savingAutoPay} onPress={handleSetAutoPay}>
            <Text style={styles.payButtonText}>{savingAutoPay ? 'Saving…' : 'Turn on auto-pay'}</Text>
          </TouchableOpacity>
        </View>
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
  container: { flex: 1, paddingHorizontal: 20, paddingVertical: 16, backgroundColor: colors.background },
  title: { fontSize: 20, fontWeight: '700', marginBottom: 16, color: colors.textPrimary },
  body: { fontSize: 15, color: colors.textSecondary },
  error: { color: colors.error },
  spacer: { flex: 1 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: 24,
    padding: 16,
    marginBottom: 10,
  },
  rowLeft: { flex: 1 },
  provider: { fontSize: 15, fontWeight: '700', color: colors.textPrimary },
  due: { fontSize: 12, color: colors.textTertiary, marginTop: 2 },
  amount: { fontSize: 15, fontWeight: '700', color: colors.textPrimary, marginRight: 12 },
  payButton: { backgroundColor: colors.primaryIndigo, borderRadius: 16, minHeight: 48, paddingVertical: 12, paddingHorizontal: 16 },
  payButtonText: { color: colors.surface, fontWeight: '700', fontSize: 13 },
  closeButton: { alignItems: 'center', paddingVertical: 14 },
  closeButtonText: { color: colors.textTertiary, fontWeight: '600' },
  autoPayCard: {
    backgroundColor: colors.surface,
    borderRadius: 24,
    padding: 16,
    marginTop: 4,
  },
  autoPayTitle: { fontSize: 15, fontWeight: '700', color: colors.textPrimary, marginBottom: 4 },
  autoPaySubtitle: { fontSize: 12, color: colors.textTertiary, marginBottom: 10 },
  autoPayRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: colors.background,
  },
  offButton: { backgroundColor: colors.background, borderRadius: 16, minHeight: 48, paddingVertical: 12, paddingHorizontal: 16 },
  offButtonText: { color: colors.textPrimary, fontWeight: '700', fontSize: 13 },
  providerPicker: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 8 },
  providerChip: {
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: 16,
    paddingVertical: 6,
    paddingHorizontal: 12,
  },
  providerChipSelected: { backgroundColor: colors.primaryIndigo, borderColor: colors.primaryIndigo },
  providerChipText: { color: colors.textPrimary, fontSize: 12, fontWeight: '600' },
  providerChipTextSelected: { color: colors.surface, fontSize: 12, fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: 16,
    padding: 10,
    marginTop: 8,
    fontSize: 14,
    color: colors.textPrimary,
  },
});
