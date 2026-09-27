import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ViewStyle } from 'react-native';
import { colors } from './colors';
import { layout } from './layout';

interface ButtonProps { title: string; onPress: () => void; type?: 'primary' | 'secondary'; style?: ViewStyle; }

export const Button: React.FC<ButtonProps> = ({ title, onPress, type = 'primary', style }) => {
  const isPrimary = type === 'primary';
  return (
    <TouchableOpacity
      style={[styles.button, isPrimary ? styles.primary : styles.secondary, style]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={title}
    >
      <Text style={[styles.text, isPrimary ? styles.primaryText : styles.secondaryText]}>{title}</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: {
    minHeight: layout.controlHeight.md,
    minWidth: layout.minTouchTarget,
    paddingVertical: layout.space.md,
    paddingHorizontal: layout.space.xl,
    borderRadius: layout.buttonRadius,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primary: { backgroundColor: colors.primaryIndigo },
  secondary: { backgroundColor: colors.background },
  text: { fontSize: 16, fontWeight: '600' },
  primaryText: { color: colors.card },
  secondaryText: { color: colors.textPrimary },
});
