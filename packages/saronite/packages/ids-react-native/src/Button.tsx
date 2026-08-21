import React from 'react';
import { TouchableOpacity, Text, StyleSheet, ViewStyle } from 'react-native';
import { colors } from './colors';

interface ButtonProps {
  title: string;
  onPress: () => void;
  type?: 'primary' | 'secondary';
  style?: ViewStyle;
}

export const Button: React.FC<ButtonProps> = ({ title, onPress, type = 'primary', style }) => {
  const isPrimary = type === 'primary';
  return (
    <TouchableOpacity 
      style={[
        styles.button, 
        isPrimary ? styles.primary : styles.secondary,
        style
      ]} 
      onPress={onPress}
    >
      <Text style={[styles.text, isPrimary ? styles.primaryText : styles.secondaryText]}>
        {title}
      </Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  button: {
    paddingVertical: 16,
    paddingHorizontal: 24,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primary: {
    backgroundColor: colors.primaryIndigo,
  },
  secondary: {
    backgroundColor: colors.background,
  },
  text: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  primaryText: {
    color: colors.card,
  },
  secondaryText: {
    color: colors.textPrimary,
  }
});
