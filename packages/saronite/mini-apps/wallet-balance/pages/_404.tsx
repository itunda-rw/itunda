import React from 'react';
import { colors } from '../../../../packages/ids-react-native/src/colors';
import { SafeAreaView, StyleSheet, Text, TouchableOpacity } from 'react-native';
import { createRoute } from '@granite-js/react-native';
import { closeView } from '@itunda/saronite-react-native';

/**
 * Real requirement, not itunda-specific convention -- see pay-bills'
 * pages/_404.tsx for the full account of why granite's router requires this
 * unconditionally (`getScreenPathMapConfig` throws without it).
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
  container: { flex: 1, padding: 24, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.background },
  title: { fontSize: 17, fontWeight: '700', color: colors.textPrimary, marginBottom: 16 },
  closeButton: { paddingVertical: 14, paddingHorizontal: 20 },
  closeButtonText: { color: colors.textTertiary, fontWeight: '600' },
});
