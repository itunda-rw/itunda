import React from 'react';
import { SafeAreaView, StyleSheet, Text, TouchableOpacity } from 'react-native';
import { createRoute } from '@granite-js/react-native';
import { closeView } from '@itunda/saronite-react-native';

/**
 * Real requirement, not itunda-specific convention (2026-07-13,
 * granite-adoption stage 7 completion): granite's own router unconditionally
 * requires a `/_404` route to exist for every app -- confirmed directly from
 * `@granite-js/react-native/src/router/utils/screen.tsx`'s `getScreenPathMapConfig`,
 * which throws `"404 page not found. Please create a _404.ts or _404.tsx
 * file..."` if no screen resolves to path `/_404`, regardless of whether the
 * app's real routes are otherwise fully correct. pay-bills only had
 * `pages/index.tsx` (its one real route), so this threw on every launch.
 */
function NotFoundPage() {
  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Page not found</Text>
      <TouchableOpacity style={styles.closeButton} onPress={() => closeView()}>
        <Text style={styles.closeButtonText}>Close</Text>
      </TouchableOpacity>
    </SafeAreaView>
  );
}

export const Route = createRoute('/_404', {
  component: NotFoundPage,
});

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: '#F2F4F6' },
  title: { fontSize: 17, fontWeight: '700', color: '#191F28', marginBottom: 16 },
  closeButton: { paddingVertical: 14, paddingHorizontal: 20 },
  closeButtonText: { color: '#8B95A1', fontWeight: '600' },
});
