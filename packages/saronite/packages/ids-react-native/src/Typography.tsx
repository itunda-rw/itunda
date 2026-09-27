import React from 'react';
import { Text, TextStyle, StyleSheet } from 'react-native';
import { colors } from './colors';

interface TypographyProps {
  children: React.ReactNode;
  style?: TextStyle;
}

export const Header: React.FC<TypographyProps> = ({ children, style }) => (
  <Text style={[styles.header, style]}>{children}</Text>
);

export const Title: React.FC<TypographyProps> = ({ children, style }) => (
  <Text style={[styles.title, style]}>{children}</Text>
);

export const BodyBold: React.FC<TypographyProps> = ({ children, style }) => (
  <Text style={[styles.bodyBold, style]}>{children}</Text>
);

export const BodyMedium: React.FC<TypographyProps> = ({ children, style }) => (
  <Text style={[styles.bodyMedium, style]}>{children}</Text>
);

export const Label: React.FC<TypographyProps> = ({ children, style }) => (
  <Text style={[styles.label, style]}>{children}</Text>
);

export const Caption: React.FC<TypographyProps> = ({ children, style }) => (
  <Text style={[styles.caption, style]}>{children}</Text>
);

export const Display: React.FC<TypographyProps> = ({ children, style }) => (
  <Text style={[styles.display, style]}>{children}</Text>
);

const styles = StyleSheet.create({
  header: {
    fontSize: 24,
    lineHeight: 32,
    fontWeight: '700',
    color: colors.textPrimary,
  },
  title: {
    fontSize: 20,
    lineHeight: 28,
    fontWeight: '700',
    color: colors.textPrimary,
  },
  bodyBold: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '700',
    color: colors.textPrimary,
  },
  bodyMedium: {
    fontSize: 16,
    lineHeight: 24,
    fontWeight: '500',
    color: colors.textSecondary,
  },
  label: { fontSize: 15, lineHeight: 20, fontWeight: '500', color: colors.textSecondary },
  caption: { fontSize: 13, lineHeight: 18, fontWeight: '400', color: colors.textTertiary },
  display: { fontSize: 24, lineHeight: 32, fontWeight: '700', color: colors.textPrimary },
});
