import React from 'react';
import { SafeAreaView, StyleSheet, Text, TouchableOpacity } from 'react-native';
import { createRoute } from '@granite-js/react-native';
import { closeView } from '@itunda/saronite-react-native';

/**
 * Real requirement, not itunda-specific -- see wallet-balance/pages/_404.tsx's own
 * header comment for the full account of why granite's router requires this file to
 * exist for every app, including this one-route Partner SDK demo.
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
