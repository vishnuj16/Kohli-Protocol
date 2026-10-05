# KOHLI PROTOCOL — Development & Architecture Guide

## Project Summary
Kohli Protocol is a private, single-user Android application designed for personal food logging discipline and strict digital enforcement. 
For complete rules, requirements, and architecture details, refer to `docs/KOHLI_PROTOCOL_SPEC.md`.

---

## Technical Stack & Environment
- **Language:** Kotlin
- **Build System:** Gradle (Kotlin DSL)
- **Target SDK:** 34 | **Min SDK:** 26
- **Architecture Pattern:** MVVM / Clean Architecture
- **Local Storage:** Room Database, Jetpack DataStore, Private File System
- **Security:** BiometricPrompt API, Secure Window Flags (`FLAG_SECURE`)
- **Testing Hardware:** Motorola Edge 40 via ADB (`adb devices`)
- **Dev Tools:** Linux Terminal / VS Code / Claude Code (No Android Studio)

---

## Build & Deployment Commands
- **Compile Debug APK:** `./gradlew assembleDebug`
- **Install on Device:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- **Launch MainActivity:** `adb shell am start -n com.vishnu.kohliprotocol/.MainActivity`
- **View Runtime Logs:** `adb logcat | grep -E "KohliProtocol|AndroidRuntime"`

---

## Core Rules & Non-Negotiables
1. **Local-Only Data Architecture:** NO cloud databases (Firebase, Supabase, external APIs for storage). Everything stays local on device.
2. **AI Integration:** AI providers (Gemini, Claude, OpenAI) are strictly used via HTTP/JSON abstraction for daily meal evaluation and weekly summary generation.
3. **Core Philosophy:** "Rational Vishnu sets the rules. The app prevents Impulsive Vishnu from casually breaking them."
4. **The Biryani Parameter:** Default `3.5`. Can be increased freely, but DECREASING or disabling rules REQUIRES Guardian Gate authorization.
5. **Phase-Based Incremental Execution:** Build and verify features strictly according to the phase roadmap defined in prompt sessions.

---

## Code Style & Conventions
- Use AndroidX libraries exclusively (`android.useAndroidX=true`).
- Keep UI clean, dark-themed, and responsive.
- Standard Android package root: `com.vishnu.kohliprotocol`.
- Use Kotlin Coroutines and Flow for asynchronous operations and database queries.
- Validate all incoming JSON responses from AI services before writing to Room DB.
