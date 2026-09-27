import React from 'react';
import { TouchableOpacity, View, StyleSheet } from 'react-native';
import { BodyBold, BodyMedium } from './Typography';
import { colors } from './colors';
import { layout } from './layout';

interface ListRowProps {
  title: string;
  subTitle?: string;
  rightElement?: React.ReactNode;
  onPress?: () => void;
  icon?: React.ReactNode;
}

export const ListRow: React.FC<ListRowProps> = ({ title, subTitle, rightElement, onPress, icon }) => (
  <TouchableOpacity
    onPress={onPress}
    disabled={!onPress}
    style={styles.container}
    accessibilityRole={onPress ? 'button' : 'text'}
    accessibilityLabel={title}
  >
    <View style={styles.leftContent}>
      {icon && <View style={styles.iconContainer}>{icon}</View>}
      <View style={styles.textContainer}>
        <BodyBold>{title}</BodyBold>
        {subTitle && <BodyMedium style={styles.subTitle}>{subTitle}</BodyMedium>}
      </View>
    </View>
    {rightElement && <View style={styles.rightContent}>{rightElement}</View>}
  </TouchableOpacity>
);

const styles = StyleSheet.create({
  container: {
    minHeight: layout.recommendedTouchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: layout.space.md,
    paddingHorizontal: layout.screenPaddingInline,
    backgroundColor: colors.card,
  },
  leftContent: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  iconContainer: {
    width: layout.controlHeight.sm,
    height: layout.controlHeight.sm,
    borderRadius: layout.iconRadius,
    backgroundColor: colors.background,
    marginRight: layout.inlineGap,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textContainer: { flex: 1, justifyContent: 'center' },
  subTitle: { marginTop: layout.tightGap / 2 },
  rightContent: { marginLeft: layout.inlineGap },
});
