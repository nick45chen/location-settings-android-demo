# Repository Guidelines

## Project Structure & Module Organization
This is a single-module Android app using Kotlin and Jetpack Compose.
- `app/`: Android application module.
- `app/src/main/java/com/example/myapplicationsetting/`: Kotlin source (entry in `MainActivity.kt`).
- `app/src/main/java/com/example/myapplicationsetting/ui/theme/`: Compose theming.
- `app/src/main/res/`: Resources (layouts are Compose-based, so UI assets live here).
- `app/src/main/AndroidManifest.xml`: App manifest.
- `app/src/test/`: JVM unit tests (JUnit).
- `app/src/androidTest/`: Instrumented UI tests (AndroidX, Espresso, Compose UI).
- `gradlew`, `gradle/`, `settings.gradle.kts`: Gradle wrapper and build configuration.

## Build, Test, and Development Commands
Run from the repository root:
- `./gradlew assembleDebug`: Builds the debug APK.
- `./gradlew installDebug`: Installs the debug build on a connected device/emulator.
- `./gradlew test`: Runs JVM unit tests in `app/src/test`.
- `./gradlew connectedAndroidTest`: Runs instrumented tests on a device/emulator.
- `./gradlew lint`: Runs Android Lint checks.
Tip: Opening the project in Android Studio is the easiest way to run/debug the app.

## Coding Style & Naming Conventions
- Language: Kotlin (JVM target 11), UI with Jetpack Compose.
- Indentation: 4 spaces; use Android Studio/Kotlin default formatting.
- Naming: classes/objects in `PascalCase`, functions and variables in `camelCase`.
- `@Composable` functions should use `PascalCase` for UI components.
- Resources use `lowercase_underscore` (e.g., `ic_settings`).

## Testing Guidelines
- Unit tests: JUnit in `app/src/test` (name files `*Test.kt`).
- Instrumented tests: AndroidX/Espresso/Compose in `app/src/androidTest`.
- Add tests for new logic and UI behavior when feasible; prefer small, focused tests.
Run tests with `./gradlew test` or `./gradlew connectedAndroidTest`.

## Commit & Pull Request Guidelines
- No Git history is present here, so use clear, imperative commit messages
  (e.g., "Add settings screen", "Fix theme preview crash").
- PRs should include a short summary, testing notes/commands run, and
  screenshots or screen recordings for UI changes.

## Configuration Notes
- `local.properties` is used for the Android SDK path and should not be committed.
- The app targets API 36 and requires at least API 26 (see `app/build.gradle.kts`).
