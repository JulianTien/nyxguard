# Android Setup

## First run

1. Copy `android/local.properties.example` to `android/local.properties`.
2. Fill in `sdk.dir`.
3. Optionally set `nyxGuardApiBaseUrl` or `nyxGuardLocalApiBaseUrl`.

## Commands

```bash
cd android
./gradlew test
./gradlew assembleDebug
./gradlew connectedAndroidTest
```
