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
  claimRewardTask,
  closeView,
  confirmEmailVerification,
  getRewardTasks,
  getReferralInfo,
  getTodaySteps,
  reportSteps,
  requestEmailVerification,
  updateProfilePhoto,
} from '@itunda/saronite-react-native';
import type { ReferralInfo, RewardTask } from '@itunda/saronite-react-native';

// Real Toss 만보기 (walking rewards) tier structure -- mirrors
// StepRewardTier.stepsRequired/rewardAmount exactly (services/backend/rewards's
// StepRewardService.kt). Purely for display (progress bar, "next tier" copy); the
// backend is the actual source of truth and authoritative validator/payer.
//
// Real lottery-style bonus (item 248, docs/DESIGN_REFERENCES.md Section 15) --
// lotteryOdds/lotteryBonusAmount mirror StepRewardTier's own real, stated constants,
// same "hardcoded for display, backend is authoritative" convention this file already
// established. Disclosing the real odds here (not just after a win) is what keeps this
// a bonus rather than the dark-pattern-style hidden mechanic this project has
// otherwise deliberately avoided building.
const STEP_TIERS = [
  { steps: 1000, rewardAmount: 50, lotteryOdds: 0.05, lotteryBonusAmount: 100 },
  { steps: 5000, rewardAmount: 150, lotteryOdds: 0.05, lotteryBonusAmount: 300 },
  { steps: 10000, rewardAmount: 300, lotteryOdds: 0.05, lotteryBonusAmount: 600 },
];

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

      <StepsPanel />

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
                <Text style={styles.rewardAmount}>+{item.rewardAmount.toLocaleString()} RWF</Text>
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

/**
 * Real Toss 만보기 (walking rewards) -- see StepRewardService's own doc comment on the
 * backend. `steps` is honestly a manually-entered count, not a real device pedometer/
 * HealthKit reading: no sensor integration exists on either native host app (a real,
 * separate, not-yet-started follow-up), and the backend's own doc comment already
 * names client-reported step data as the honest boundary here (a sanity ceiling, not
 * real anti-spoofing) -- so a manual entry is a real, honest way to use this feature
 * today, not a fabrication of sensor data that was never actually read.
 */
function StepsPanel() {
  const [steps, setSteps] = useState<number | null>(null);
  const [input, setInput] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    getTodaySteps()
      .then((r) => setSteps(r.steps))
      .catch(() => {
        // Non-fatal: the panel just stays in its loading state.
      });
  }, []);

  useEffect(load, [load]);

  const handleSubmit = async () => {
    const value = Number(input);
    if (!Number.isFinite(value) || value < 0) {
      setError('Enter a real step count.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const result = await reportSteps(Math.round(value));
      setSteps(result.steps);
      setInput('');
      if (result.newlyEarnedTiers.length > 0) {
        // Real lottery-style bonus (item 248) -- always named separately from the
        // guaranteed reward, never folded into one number.
        const bonusLine = result.lotteryBonusWonAmount > 0
          ? `\n\nPlus a lottery bonus: +${result.lotteryBonusWonAmount.toLocaleString()} RWF! 🎉`
          : '';
        Alert.alert('Walking reward earned', `+${result.newlyEarnedAmount.toLocaleString()} RWF for reaching ${result.newlyEarnedTiers.join(', ')} steps today${bonusLine}`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not report your steps.');
    } finally {
      setSubmitting(false);
    }
  };

  const nextTier = STEP_TIERS.find((t) => (steps ?? 0) < t.steps);

  return (
    <View style={[styles.row, styles.stepsPanel]}>
      <Text style={styles.taskTitle}>Today's steps</Text>
      <Text style={styles.stepsCount}>{(steps ?? 0).toLocaleString()}</Text>
      <Text style={styles.subtitle}>
        {nextTier
          ? `${(nextTier.steps - (steps ?? 0)).toLocaleString()} steps to +${nextTier.rewardAmount} RWF`
          : 'All tiers earned for today'}
      </Text>
      {/* Real lottery-style bonus (item 248) -- the real, stated odds shown up front,
          same disclosed-odds discipline bank-mfe's own identical addition already has. */}
      {nextTier && (
        <Text style={styles.panelHint}>
          Plus a {Math.round(nextTier.lotteryOdds * 100)}% chance of a +{nextTier.lotteryBonusAmount.toLocaleString()} RWF bonus
        </Text>
      )}
      <View style={styles.inlineRow}>
        <TextInput
          style={styles.input}
          value={input}
          onChangeText={setInput}
          placeholder="Log today's step count"
          keyboardType="number-pad"
        />
        <TouchableOpacity style={styles.panelButton} disabled={submitting} onPress={handleSubmit}>
          <Text style={styles.panelButtonText}>{submitting ? 'Logging…' : 'Log steps'}</Text>
        </TouchableOpacity>
      </View>
      {error && <Text style={[styles.body, styles.error]}>{error}</Text>}
    </View>
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
  container: { flex: 1, paddingHorizontal: 20, paddingVertical: 16, backgroundColor: colors.background },
  title: { fontSize: 20, fontWeight: '700', color: colors.textPrimary },
  total: { fontSize: 28, fontWeight: '700', color: colors.primaryIndigo, marginTop: 4, marginBottom: 16 },
  body: { fontSize: 15, color: colors.textSecondary },
  error: { color: colors.error },
  spacer: { flex: 1 },
  row: {
    backgroundColor: colors.surface,
    borderRadius: 24,
    padding: 16,
    marginBottom: 10,
  },
  rowTop: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  rowLeft: { flex: 1 },
  stepsPanel: { marginBottom: 16 },
  stepsCount: { fontSize: 26, fontWeight: '700', color: colors.textPrimary, marginTop: 4 },
  taskTitle: { fontSize: 15, fontWeight: '700', color: colors.textPrimary },
  subtitle: { fontSize: 12, color: colors.textTertiary, marginTop: 2 },
  // Real fix (2026-07-13): #31CE66 was a one-off green that didn't match itunda's
  // own already-established Toss green500 (#04C065 -- android/.../IdsColors.kt,
  // ios/.../IdsTheme.swift, packages/design-tokens/tokens.css all agree), the exact
  // class of drift design-tokens.css's own header comment already documented fixing
  // once for bank-mfe's green -- found again here via a repo-wide color audit.
  // Real WCAG AA contrast fix (item 240, docs/ACCESSIBILITY.md finding #2): that
  // #04C065 itself measures 2.40:1 against white, failing even the lenient 3.0:1
  // AA-large/UI threshold for this text. Darkened to colors.positive (5.01:1), matching
  // the same fix applied to packages/design-tokens/tokens.css --itunda-green and
  // the Android/iOS semantic `success` token.
  rewardAmount: { fontSize: 14, fontWeight: '700', color: colors.positive, marginRight: 12 },
  claimButton: { backgroundColor: colors.primaryIndigo, borderRadius: 16, minHeight: 48, paddingVertical: 12, paddingHorizontal: 16 },
  claimButtonDone: { backgroundColor: colors.divider },
  claimButtonText: { color: colors.surface, fontWeight: '700', fontSize: 13 },
  closeButton: { alignItems: 'center', paddingVertical: 14 },
  closeButtonText: { color: colors.textTertiary, fontWeight: '600' },
  panel: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
  },
  panelLabel: { fontSize: 12, fontWeight: '700', color: colors.textSecondary },
  panelLabelSpaced: { marginTop: 12 },
  panelHint: { fontSize: 12, color: colors.textTertiary, marginTop: 4 },
  referralCode: { fontSize: 20, fontWeight: '700', color: colors.textPrimary, marginTop: 4, letterSpacing: 1 },
  panelButton: {
    marginTop: 8,
    alignSelf: 'flex-start',
    backgroundColor: '#F5FAFF',
    borderRadius: 16,
    paddingVertical: 12,
    paddingHorizontal: 14,
  },
  panelButtonText: { color: colors.primaryIndigo, fontWeight: '700', fontSize: 13 },
  inlineRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 4 },
  input: {
    flex: 1,
    backgroundColor: colors.background,
    borderRadius: 16,
    paddingVertical: 12,
    paddingHorizontal: 12,
    fontSize: 13,
    color: colors.textPrimary,
  },
});
