/**
 * Points at itunda's real Android project. Not used for autolinking here --
 * the actual wiring is manual (see android/app/build.gradle.kts and
 * android/app/src/main/java/rw/itunda/app/miniapps/), not the RN CLI's
 * `react {}` Gradle plugin -- this file exists so `npx react-native start`
 * (run from packages/saronite/host-app) can find the real app directory
 * for tooling that expects it. Path/package fixed 2026-07-10 after the
 * packages/services restructure (was pointing at a pre-restructure path
 * and the wrong applicationId).
 *
 * brick-module is deliberately excluded from Android RN autolinking. It uses
 * Toss's brick-codegen output rather than React Native's standard CMake/codegen
 * autolinking path, and itunda already wires BrickModulePackage plus the
 * brick_modules.gradle integration manually in android/settings.gradle.kts.
 * Allowing the RN CLI to autolink it as a normal CMake module produces an
 * Android-autolinking.cmake entry for a generated JNI directory that
 * brick-codegen owns instead, causing :app:configureCMakeDebug to fail.
 */
module.exports = {
  dependencies: {
    'brick-module': {
      platforms: {
        android: null,
      },
    },
  },
  project: {
    android: {
      sourceDir: '../../../android',
      packageName: 'rw.itunda.app',
    },
  },
};
