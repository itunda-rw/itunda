import React from 'react';
import { View, ScrollView, StyleSheet, Alert } from 'react-native';
import { Header, Title, ListRow, Button, colors } from '@itunda/ids-react-native';

/**
 * Itunda Mutuelle (Insurance) Mini-App
 * Developed using Toss's Granite framework architecture principles.
 * Uses file-based routing (pages/index.tsx) and relies exclusively on IDS for UI consistency.
 */
export default function MutuelleInsurancePage() {
  
  const handleApply = () => {
    // In a real mini-app, this would call the React Native bridge or an API
    // and route to the next page using Granite's router (e.g. router.push('/apply'))
    Alert.alert("Apply for Mutuelle", "Starting NIDA verification for health insurance...");
  };

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.headerContainer}>
          <Header>Mutuelle de Santé</Header>
          <Title style={styles.subtitle}>Protect your family today.</Title>
        </View>

        <View style={styles.section}>
          <Title style={styles.sectionTitle}>My Coverage</Title>
          <ListRow 
            title="Status: Uninsured" 
            subTitle="Your NID is not linked to an active policy."
            rightElement={<Button title="Link NID" type="secondary" onPress={() => {}} />}
          />
        </View>

        <View style={styles.section}>
          <Title style={styles.sectionTitle}>Available Plans</Title>
          <ListRow 
            title="Individual Mutuelle" 
            subTitle="RWF 3,000 / year"
          />
          <ListRow 
            title="Family Mutuelle" 
            subTitle="Cover up to 5 members • RWF 12,000 / year"
          />
        </View>
      </ScrollView>

      <View style={styles.bottomBar}>
        <Button 
          title="Apply Now" 
          onPress={handleApply} 
          style={styles.fullWidthButton} 
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scrollContent: {
    paddingBottom: 120, // Space for the bottom bar
  },
  headerContainer: {
    padding: 24,
    paddingTop: 40,
    backgroundColor: colors.card,
    borderBottomLeftRadius: 24,
    borderBottomRightRadius: 24,
  },
  subtitle: {
    marginTop: 8,
    color: colors.textSecondary,
    fontWeight: 'normal',
  },
  section: {
    marginTop: 24,
  },
  sectionTitle: {
    marginLeft: 24,
    marginBottom: 12,
  },
  bottomBar: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    padding: 24,
    backgroundColor: colors.card,
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.05,
    shadowRadius: 16,
    elevation: 10,
  },
  fullWidthButton: {
    width: '100%',
  }
});
