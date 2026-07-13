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
import { claimRewardTask, closeView, getRewardTasks } from '@itunda/saronite-react-native';
import type { RewardTask } from '@itunda/saronite-react-native';

/**
 * A real rewards/points mini-app — one of the most common categories in
 * Toss's actual Apps in Toss ecosystem (step-count rewards, quiz rewards,
 * referral bonuses). Backed by itunda's real `GET /rewards/tasks` and
 * `POST /rewards/claim`, including the real once-only claim guard the
 * backend enforces (a second claim attempt on an already-claimed task
 * correctly surfaces as a failure here, not a silent no-op).
 *
 * Migrated onto real granite (2026-07-13), same pattern pay-bills and
 * wallet-balance proved out first: `createRoute` registration added below,
 * `app.tsx`/`require.context.ts`/`router.gen.ts` added alongside.
 */
export default function RewardTasksPage() {
  const [tasks, setTasks] = useState<RewardTask[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [claimingId, setClaimingId] = useState<string | null>(null);

  const load = useCallback(() => {
    setError(null);
    getRewardTasks()
      .then((result) => {
        setTasks(result.tasks);
        setTotal(result.rewardsTotal);
      })
      .catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleClaim = async (task: RewardTask) => {
    setClaimingId(task.id);
    try {
      const result = await claimRewardTask(task.id);
      setTotal(result.newBalance);
      setTasks((prev) =>
        prev
          ? prev.map((t) => (t.id === task.id ? { ...t, claimed: true } : t))
          : prev,
      );
      Alert.alert('Reward claimed', result.message);
    } catch (err) {
      Alert.alert('Couldn\'t claim reward', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setClaimingId(null);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Rewards</Text>
      <Text style={styles.total}>{total.toLocaleString()} RWF earned</Text>

      {tasks === null && !error && <ActivityIndicator style={styles.spacer} />}

      {error && <Text style={[styles.body, styles.error]}>Couldn't load rewards: {error}</Text>}

      {tasks && (
        <FlatList
          data={tasks}
          keyExtractor={(t) => t.id}
          renderItem={({ item }) => (
            <View style={styles.row}>
              <View style={styles.rowLeft}>
                <Text style={styles.taskTitle}>{item.title}</Text>
                <Text style={styles.subtitle}>{item.subtitle}</Text>
              </View>
              <Text style={styles.rewardAmount}>+{item.rewardAmount} RWF</Text>
              <TouchableOpacity
                style={[styles.claimButton, item.claimed && styles.claimButtonDone]}
                disabled={item.claimed || claimingId === item.id}
                onPress={() => handleClaim(item)}
              >
                <Text style={styles.claimButtonText}>
                  {item.claimed ? 'Claimed' : claimingId === item.id ? 'Claiming…' : 'Claim'}
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
  component: RewardTasksPage,
});

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: '#F2F4F6' },
  title: { fontSize: 20, fontWeight: '800', color: '#191F28' },
  total: { fontSize: 28, fontWeight: '900', color: '#3182F6', marginTop: 4, marginBottom: 16 },
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
  taskTitle: { fontSize: 15, fontWeight: '700', color: '#191F28' },
  subtitle: { fontSize: 12, color: '#8B95A1', marginTop: 2 },
  // Real fix (2026-07-13): #31CE66 was a one-off green that didn't match itunda's
  // own already-established Toss green500 (#04C065 -- android/.../TdsColors.kt,
  // ios/.../TdsTheme.swift, packages/design-tokens/tokens.css all agree), the exact
  // class of drift design-tokens.css's own header comment already documented fixing
  // once for bank-mfe's green -- found again here via a repo-wide color audit.
  rewardAmount: { fontSize: 14, fontWeight: '700', color: '#04C065', marginRight: 12 },
  claimButton: { backgroundColor: '#3182F6', borderRadius: 8, paddingVertical: 8, paddingHorizontal: 14 },
  claimButtonDone: { backgroundColor: '#E5E8EB' },
  claimButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 13 },
  closeButton: { alignItems: 'center', paddingVertical: 14 },
  closeButtonText: { color: '#8B95A1', fontWeight: '600' },
});
