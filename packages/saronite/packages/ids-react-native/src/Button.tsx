import React from 'react';
import { ActivityIndicator, Pressable, StyleSheet, ViewStyle } from 'react-native';
import { colors } from './colors';
import { BodyBold } from './Typography';
import { Text } from 'react-native';
import { layout } from './layout';

export type ButtonType = 'primary' | 'secondary' | 'tertiary' | 'destructive';

interface ButtonProps {
  title: string;
  onPress: () => void;
  type?: ButtonType;
  disabled?: boolean;
  loading?: boolean;
  style?: ViewStyle;
  accessibilityHint?: string;
  accessibilityValue?: { text?: string; now?: number; min?: number; max?: number };
}

export const Button: React.FC<ButtonProps> = ({
  title,
  onPress,
  type = 'primary',
  disabled = false,
  loading = false,
  style,
  accessibilityHint,
  accessibilityValue,
}) => {
  const unavailable = disabled || loading;
  const isPrimary = type === 'primary';
  const isDestructive = type === 'destructive';
  const isTertiary = type === 'tertiary';

  return (
    <Pressable
      style={({ pressed }) => [
        styles.button,
        isPrimary && styles.primary,
        type === 'secondary' && styles.secondary,
        isTertiary && styles.tertiary,
        isDestructive && styles.destructive,
        pressed && !unavailable && styles.pressed,
        unavailable && styles.disabled,
        style,
      ]}
      onPress={onPress}
      disabled={unavailable}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: unavailable, busy: loading }}
      accessibilityValue={accessibilityValue}
    >
      {loading ? (
        <ActivityIndicator
          accessibilityLabel="Loading"
          color={isPrimary || isDestructive ? colors.card : colors.primaryIndigo}
        />
      ) : (
        <Text style={[
          styles.text,
          isPrimary && styles.primaryText,
          type === 'secondary' && styles.secondaryText,
          isTertiary && styles.tertiaryText,
          isDestructive && styles.destructiveText,
        ]}>{title}</BodyBold>
      )}
    </Pressable>
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
  secondary: { backgroundColor: colors.card },
  tertiary: { backgroundColor: 'transparent' },
  destructive: { backgroundColor: colors.error },
  pressed: { opacity: 0.86 },
  disabled: { opacity: 0.45 },
  text: { fontSize: 16, fontWeight: '600' },
  primaryText: { color: colors.card },
  secondaryText: { color: colors.textPrimary },
  tertiaryText: { color: colors.primaryIndigo },
  destructiveText: { color: colors.card },
});