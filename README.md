# Quiz Lock

Android app jo chune hue apps (aur chaho to poora phone, unlock ke baad) tab tak
lock rakhta hai jab tak quiz ka sahi jawab na do. Flutter + native Kotlin.

## Kaise kaam karta hai

- **Setup (Flutter, `lib/`)**: mode (Student/Parent), lock type, apps chunna,
  quiz settings, free time/skip, caution, permissions, test run.
- **Lock (Kotlin, `android/app/src/main/kotlin/com/quizlock/app/`)**
  - `LockService.kt`: foreground service. Har 0.5 sec `UsageStatsManager` se
    dekhti hai kaunsa app saamne hai. Unlock (`USER_PRESENT`) aur restart
    (`BOOT_COMPLETED`) pakadti hai. Call ke time quiz chhupa deti hai.
  - `QuizOverlay.kt`: poori screen ka quiz, "Display over other apps" se.
    Back button band. Emergency call, skip, parent PIN, wapas jao.
  - `Quiz.kt`: agla sawal chunna (repeat nahi, galat sawal 24 ghante baad
    dobara, options shuffle). JVM tests: `android/app/src/test`.
  - `Store.kt`: settings, pending change, free time, stats, PIN (hash).
- **Sawal**: `assets/questions.json`. Abhi sirf 5 test sawal hain. Naye sawal
  isi format mein daalo.

## Rules

- Lock tight karna turant lagu. Dheela karna: Student mode mein 24 ghante baad,
  Parent mode mein PIN se.
- Restart karne par free time khatam ho jata hai aur lock screen quiz turant aata hai.

## Build

```
flutter test
cd android && ./gradlew :app:testDebugUnitTest && cd ..
flutter build apk --release --split-per-abi
```

APK: `build/app/outputs/flutter-apk/app-arm64-v8a-release.apk` (zyada tar phones).
Abhi release APK debug key se sign hota hai; Play Store se pehle apni signing key lagani hai.
