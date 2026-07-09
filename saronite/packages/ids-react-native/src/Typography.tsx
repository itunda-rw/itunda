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

const styles = StyleSheet.create({
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    color: colors.textPrimary,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: colors.textPrimary,
  },
  bodyBold: {
    fontSize: 16,
    fontWeight: 'bold',
    color: colors.textPrimary,
  },
  bodyMedium: {
    fontSize: 14,
    fontWeight: '500',
    color: colors.textSecondary,
  }
});
