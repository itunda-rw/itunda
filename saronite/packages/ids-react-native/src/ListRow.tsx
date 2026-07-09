import React from 'react';
import { TouchableOpacity, View, StyleSheet } from 'react-native';
import { BodyBold, BodyMedium } from './Typography';
import { colors } from './colors';

interface ListRowProps {
  title: string;
  subTitle?: string;
  rightElement?: React.ReactNode;
  onPress?: () => void;
  icon?: React.ReactNode;
}

export const ListRow: React.FC<ListRowProps> = ({ title, subTitle, rightElement, onPress, icon }) => {
  return (
    <TouchableOpacity onPress={onPress} disabled={!onPress} style={styles.container}>
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
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    paddingHorizontal: 20,
    backgroundColor: colors.card,
  },
  leftContent: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  iconContainer: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: colors.background,
    marginRight: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  textContainer: {
    flex: 1,
    justifyContent: 'center',
  },
  subTitle: {
    marginTop: 4,
  },
  rightContent: {
    marginLeft: 16,
  }
});
