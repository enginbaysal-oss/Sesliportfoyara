# Implementation Plan: Convert to Compose Multiplatform (Android + Desktop)

This plan outlines the steps to convert the existing Android-only project into a Compose Multiplatform project supporting Android (APK) and Desktop (EXE).

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/gradle/libs.versions.toml)
- Add Kotlin Multiplatform and Compose Multiplatform plugin definitions.
- Add desktop-specific dependencies if needed.

#### [MODIFY] [build.gradle.kts (root)](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/build.gradle.kts)
- Apply the Compose Multiplatform and Kotlin Multiplatform plugins.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/app/build.gradle.kts)
- Refactor to use `kotlin("multiplatform")` and `id("org.jetbrains.compose")`.
- Configure `androidTarget` and `jvm("desktop")`.
- Define source sets: `commonMain`, `androidMain`, `desktopMain`.

### Source Code Refactoring

#### [NEW] [App.kt](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/app/src/commonMain/kotlin/com/example/sesliportfoyara/App.kt)
- Move shared UI logic (Greeting and Theme) to common code.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/app/src/androidMain/kotlin/com/example/sesliportfoyara/MainActivity.kt)
- Update to call the shared `App` composable.

#### [NEW] [main.kt](file:///C:/Users/engin/AndroidStudioProjects/Sesliportfoyara/app/src/desktopMain/kotlin/com/example/sesliportfoyara/main.kt)
- Create the desktop entry point using `application` and `Window`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to verify Android build.
- Run `./gradlew :app:run` to verify Desktop app launch (if environment supports).

### Manual Verification
- Verify the project syncs correctly in Android Studio.
- Check that `androidMain` and `desktopMain` source sets are recognized.
