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
  claimRewardTask,
  closeView,
  confirmEmailVerification,
  getRewardTasks,
  getReferralInfo,
  requestEmailVerification,
  updateProfilePhoto,
} from '@itunda/saronite-react-native';
import type { ReferralInfo, RewardTask } from '@itunda/saronite-react-native';

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
 *
 * Real referral + profile-completion actions added 2026-07-17: task_referral
 * and task_profile went real-activity-verified on the backend the same day
 * (see docs/TOSS_PARITY_MATRIX.md's Rewards row), but this screen had no way
 * for a real user to actually become eligible for either -- tapping Claim
 * always real-403'd with no path forward. `ReferralPanel`/`ProfilePanel`
 * below are the missing UI: sharing a real referral code, setting a real
 * profile photo URL, and requesting/confirming a real email-verification
 * token (delivered via a real in-app Notification, not echoed here -- see
 * AuthService.requestEmailVerification's own doc comment).
 */
export default function RewardTasksPage() {
  const [tasks, setTasks] = useState<RewardTask[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [claimingId, setClaimingId] = useState<string | null>(null);
  const [referralInfo, setReferralInfo] = useState<ReferralInfo | null>(null);

  const load = useCallback(() => {
    setError(null);
    getRewardTasks()
      .then((result) => {
        setTasks(result.tasks);
        setTotal(result.rewardsTotal);
      })
      .catch((err: Error) => setError(err.message));
  }, []);

  const loadReferralInfo = useCallback(() => {
    getReferralInfo()
      .then(setReferralInfo)
      .catch(() => {
        // Non-fatal: the referral panel just stays in its loading state --
        // the task list itself (the more important real data) already loaded.
      });
  }, []);

  useEffect(() => {
    load();
    loadReferralInfo();
  }, [load, loadReferralInfo]);

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

  const refreshEligibility = () => {
    load();
    loadReferralInfo();
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
              <View style={styles.rowTop}>
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
              {!item.claimed && item.id === 'task_referral' && (
                <ReferralPanel info={referralInfo} onRefresh={refreshEligibility} />
              )}
              {!item.claimed && item.id === 'task_profile' && (
                <ProfilePanel onUpdated={refreshEligibility} />
              )}
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

function ReferralPanel({ info, onRefresh }: { info: ReferralInfo | null; onRefresh: () => void }) {
  return (
    <View style={styles.panel}>
      {info ? (
        <>
          <Text style={styles.panelLabel}>Your referral code</Text>
          <Text selectable style={styles.referralCode}>{info.referralCode ?? '—'}</Text>
          <Text style={styles.panelHint}>
            {info.referredCount} referred · {info.completedReferralCount} completed a transfer
          </Text>
        </>
      ) : (
        <ActivityIndicator />
      )}
      <TouchableOpacity style={styles.panelButton} onPress={onRefresh}>
        <Text style={styles.panelButtonText}>Refresh</Text>
      </TouchableOpacity>
    </View>
  );
}

function ProfilePanel({ onUpdated }: { onUpdated: () => void }) {
  const [photoUrl, setPhotoUrl] = useState('');
  const [savingPhoto, setSavingPhoto] = useState(false);
  const [requestingEmail, setRequestingEmail] = useState(false);
  const [verificationRequested, setVerificationRequested] = useState(false);
  const [code, setCode] = useState('');
  const [confirming, setConfirming] = useState(false);

  const savePhoto = async () => {
    if (!photoUrl.trim()) return;
    setSavingPhoto(true);
    try {
      await updateProfilePhoto(photoUrl.trim());
      Alert.alert('Profile photo saved');
      onUpdated();
    } catch (err) {
      Alert.alert('Couldn\'t save photo', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setSavingPhoto(false);
    }
  };

  const sendVerification = async () => {
    setRequestingEmail(true);
    try {
      await requestEmailVerification();
      setVerificationRequested(true);
      Alert.alert('Check your notifications', 'A real verification code was sent to your itunda notifications.');
    } catch (err) {
      Alert.alert('Couldn\'t send verification', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setRequestingEmail(false);
    }
  };

  const confirmVerification = async () => {
    if (!code.trim()) return;
    setConfirming(true);
    try {
      await confirmEmailVerification(code.trim());
      Alert.alert('Email verified');
      onUpdated();
    } catch (err) {
      Alert.alert('Couldn\'t verify email', err instanceof Error ? err.message : 'Please try again');
    } finally {
      setConfirming(false);
    }
  };

  return (
    <View style={styles.panel}>
      <Text style={styles.panelLabel}>Profile photo (URL)</Text>
      <View style={styles.inlineRow}>
        <TextInput
          style={styles.input}
          value={photoUrl}
          onChangeText={setPhotoUrl}
          placeholder="https://…"
          autoCapitalize="none"
          autoCorrect={false}
        />
        <TouchableOpacity style={styles.panelButton} disabled={savingPhoto} onPress={savePhoto}>
          <Text style={styles.panelButtonText}>{savingPhoto ? 'Saving…' : 'Save'}</Text>
        </TouchableOpacity>
      </View>

      <Text style={[styles.panelLabel, styles.panelLabelSpaced]}>Verify email</Text>
      {!verificationRequested ? (
        <TouchableOpacity style={styles.panelButton} disabled={requestingEmail} onPress={sendVerification}>
          <Text style={styles.panelButtonText}>{requestingEmail ? 'Sending…' : 'Send verification code'}</Text>
        </TouchableOpacity>
      ) : (
        <View style={styles.inlineRow}>
          <TextInput
            style={styles.input}
            value={code}
            onChangeText={setCode}
            placeholder="Code from your notifications"
            autoCapitalize="none"
            autoCorrect={false}
          />
          <TouchableOpacity style={styles.panelButton} disabled={confirming} onPress={confirmVerification}>
            <Text style={styles.panelButtonText}>{confirming ? 'Confirming…' : 'Confirm'}</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
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
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 10,
  },
  rowTop: {
    flexDirection: 'row',
    alignItems: 'center',
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
  panel: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#E5E8EB',
  },
  panelLabel: { fontSize: 12, fontWeight: '700', color: '#4E5968' },
  panelLabelSpaced: { marginTop: 12 },
  panelHint: { fontSize: 12, color: '#8B95A1', marginTop: 4 },
  referralCode: { fontSize: 20, fontWeight: '900', color: '#191F28', marginTop: 4, letterSpacing: 1 },
  panelButton: {
    marginTop: 8,
    alignSelf: 'flex-start',
    backgroundColor: '#E8F3FF',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 14,
  },
  panelButtonText: { color: '#3182F6', fontWeight: '700', fontSize: 13 },
  inlineRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 4 },
  input: {
    flex: 1,
    backgroundColor: '#F2F4F6',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 10,
    fontSize: 13,
    color: '#191F28',
  },
});
