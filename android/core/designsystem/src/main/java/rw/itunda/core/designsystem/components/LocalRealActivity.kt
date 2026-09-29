package rw.itunda.core.designsystem.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity

// Real Activity channel, added 2026-08-09 after the locale-propagation fix
// (see MainActivity.kt's own doc comment) broke every `LocalContext.current as
// FragmentActivity` cast in the app: createConfigurationContext() returns a
// plain android.app.ContextImpl, not the original Activity, so that cast now
// throws ClassCastException on real devices (crash confirmed via physical-
// device logcat, never caught by compile-only or emulator verification).
// MainActivity provides the real `this` here, once, at the true composition
// root -- outside/alongside the locale-overridden LocalContext -- so any
// screen that genuinely needs the hosting Activity (BiometricPrompt via
// NIDABiometricAuth, AppLockScreen) reads it from here instead of casting
// LocalContext.
//
// Promoted here from :app's own ui/ package (2026-09-02, prerequisite for
// extracting PayTab into a new Feature module) -- DeviceStepUpHost reads this
// for its biometric-first path, and DeviceStepUpHost itself needs to be
// callable from any Feature module, not just :app.
val LocalRealActivity = staticCompositionLocalOf<FragmentActivity> {
    error("LocalRealActivity not provided -- must be set at MainActivity's setContent root")
}
