import React, { useEffect, useState } from 'react';
import { colors } from '../../../../packages/ids-react-native/src/colors';
import { Button, SafeAreaView, StyleSheet, Text, View } from 'react-native';
import { createRoute } from '@granite-js/react-native';
import { closeView, getWalletBalance, useVisibility } from '@itunda/saronite-react-native';
import type { WalletBalanceResult } from '@itunda/saronite-react-native';

/**
 * Entry page for the wallet-balance mini-app — Granite's real routing
 * convention is file-based (`pages/index.tsx` = the root route); Saronite
 * follows the same convention rather than inventing its own.
 *
 * This screen proves the bridge end-to-end: it calls the real
 * `getWalletBalance()` native method (backed by itunda's actual
 * `GET /wallet/balance`, not a mock) and renders whatever comes back,
 * including the real error path when there's no signed-in session.
 *
 * Migrated onto real granite (2026-07-13), same pattern pay-bills proved
 * out first: `createRoute` registration added below, `app.tsx`/
 * `require.context.ts`/`router.gen.ts` added alongside, `useVisibility`
 * itself untouched -- it's itunda's own bridge hook
 * (`@itunda/saronite-react-native`), unrelated to granite's routing layer.
 */
export default function IndexPage() {
  const visible = useVisibility();
  const [balance, setBalance] = useState<WalletBalanceResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!visible) return;
    setLoading(true);
    getWalletBalance()
      .then((result) => {
        setBalance(result);
        setError(null);
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [visible]);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Saronite example mini-app</Text>

      {loading && <Text style={styles.body}>Loading wallet balance…</Text>}

      {!loading && error && (
        <Text style={[styles.body, styles.error]}>Couldn't load balance: {error}</Text>
      )}

      {!loading && balance && (
        <View>
          <Text style={styles.balance}>
            {balance.totalBalance.toLocaleString()} {balance.currency}
          </Text>
          {balance.wallets.map((wallet) => (
            <Text key={wallet.id} style={styles.body}>
              {wallet.name}: {wallet.balance.toLocaleString()} {wallet.currency}
            </Text>
          ))}
        </View>
      )}

      <View style={styles.spacer} />
      <Button title="Close" onPress={() => closeView()} />
    </SafeAreaView>
  );
}

export const Route = createRoute('/', {
  component: IndexPage,
});

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: 'colors.background' },
  title: { fontSize: 20, fontWeight: '700', marginBottom: 16, color: 'colors.textPrimary' },
  balance: { fontSize: 32, fontWeight: '700', marginBottom: 12, color: 'colors.textPrimary' },
  body: { fontSize: 15, color: 'colors.textSecondary', marginBottom: 4 },
  error: { color: 'colors.error' },
  spacer: { flex: 1 },
});
