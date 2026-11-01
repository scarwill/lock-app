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
- **Naye features**
  - *Walk-to-Unlock* (`StepGate.kt`): sawal ki jagah 50/100/200 kadam, din mein 3 baar,
    2.5 kadam/second se tez nahi gine jaate (phone hilana kaam nahi karta).
  - *Exam countdown* (`ScreenTime.kt`): quiz ke upar "NEET mein X din bache" aur aaj locked
    apps par kitne minute gaye.
  - *My questions* (`lib/screens/my_questions.dart`): khud ke MCQ, free mein ek saath 25,
    naye sawal 3 din tak pehle aate hain.
  - *Kids Lock* (Premium, `lib/screens/kids_lock.dart`, `KidsTileService.kt`): parent ke
    phone par chune apps PIN/fingerprint se 15/30/60 min ke liye khulte hain.
  - *Uninstall protection* (`GuardService.kt`): sirf Parent mode aur Kids Lock mein.
    Student jab chahe app hata sakta hai.
  - *Lock band na ho*: battery optimization zaroori, company ke hisaab se autostart,
    battery saver aur recents-lock ke steps, aur chowkidar (`WatchdogJob.kt`, GuardService,
    recents se swipe par 2 sec mein restart). Home par asli status aur "phone ne lock band
    kiya" warning.
  - *Premium*: abhi Settings mein "Testing: Premium ON" switch; Play billing baad mein.
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
