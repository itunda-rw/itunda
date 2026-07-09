/**
 * Points at itunda's real Android project. Not used for autolinking here --
 * the actual wiring is manual (see android/app/build.gradle.kts and
 * android/app/src/main/java/rw/itunda/app/miniapps/), not the RN CLI's
 * `react {}` Gradle plugin -- this file exists so `npx react-native start`
 * (run from packages/saronite/host-app) can find the real app directory
 * for tooling that expects it. Path/package fixed 2026-07-10 after the
 * packages/services restructure (was pointing at a pre-restructure path
 * and the wrong applicationId).
 */
module.exports = {
  project: {
    android: {
      sourceDir: '../../../android',
      packageName: 'rw.itunda.app',
    },
  },
};
