# Morse Master Verification

- Unit: `./gradlew :app:testDebugUnitTest`
- Instrumented: `./gradlew :app:connectedDebugAndroidTest`
- Lint: `./gradlew :app:lintDebug`
- Manual runtime check:
  - Launch app
  - Confirm fallback works when Nano unavailable/busy
  - Confirm list expands at >90% and <400ms
