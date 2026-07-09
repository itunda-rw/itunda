/**
 * Tells the React Native CLI (used by both settings.gradle.kts's autolinking
 * and app/build.gradle.kts's `react { autolinkLibrariesWithApp() }`) where
 * the real Android project actually lives. Without this, `config` can't find
 * an `android/` folder here at all — by design, this brownfield host-app has
 * no android/ios folders of its own; the real Android app is
 * itunda/android/app, wired in separately via react{} in its build.gradle.kts.
 */
module.exports = {
  project: {
    android: {
      sourceDir: '../../android',
      packageName: 'com.itunda.app',
    },
  },
};
