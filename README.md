# Kohli Protocol

> *Rational Vishnu sets the rules. The app prevents Impulsive Vishnu from casually breaking them.*

A private, single-user Android app for food-logging discipline with real enforcement: it blocks
food-delivery apps, locks payments at night, rates every day with AI, and only unlocks games after
a successful week. Relaxing any rule requires approval from trusted guardians.

All data stays on the device. The only outbound traffic is the AI analysis you trigger and the
emails/SMS sent for guardian codes and weekly reports.

---

## Features

**Enforcement**
- Blocks Swiggy, Zomato, Zepto and Blinkit with a full-screen overlay (accessibility service).
- Locks PhonePe from 21:00 to 09:00, and whenever the clock or timezone looks tampered with.
- Locks games (Chess.com, eFootball, plus any you add) until a successful week.
- Foreground service and boot receiver keep enforcement running after restarts.
- Restricted-app lists are editable: adding is instant, removing needs Guardian Gate.

**Food logging**
- Four daily meal slots (each logged or explicitly skipped) plus "+ Add Food" for anything else.
- Camera or gallery photos, stored privately and downscaled.
- Reminders at 20:00 and 22:30 if the day is incomplete.

**AI analysis**
- Nightly (23:00) and on-demand analysis gives each day a calorie range and a 1–5 rating
  (Fatass whale → Bro is Kohli).
- "Yesterday's rating" card: accept it, or dispute it and have it re-analyzed.
- Providers: Gemini (default chain `gemini-3.5-flash` → `3.5-flash-lite` → `3.1-flash-lite`, with
  backoff and multi-key fallback) or Claude (newest Opus).
- Every AI response is validated against a strict schema before it is saved.

**Weekly result**
- Weeks are anchored to the weekday of first launch.
- A week succeeds only if every meal slot was logged and the average rating ≥ the
  **Biryani Parameter** (default 3.5). Raising the parameter is free; lowering it needs guardians.
- An A4 PDF report with an AI weekly review is generated and emailed to you.

**Security**
- Fingerprint/PIN lock on the whole app; the Motivation gallery asks again on every entry.
- Screenshots blocked on private screens (`FLAG_SECURE`).
- **Guardian Gate:** one-time email/SMS codes to trusted guardians, bound to the exact requested
  change, single-use and expiring after 15 minutes. Codes are never stored, only a Keystore-keyed
  hash. Used for lowering the parameter, removing restricted apps, emergency overrides and guardian
  changes.
- An append-only audit log records unlocks, Guardian Gate steps, rule changes and overrides.

---

## Tech stack

| | |
|---|---|
| Language | Kotlin 1.9.22 |
| Build | Gradle (Kotlin DSL), AGP 8.2.2, KSP |
| SDK | min 26, target/compile 34 |
| UI | Jetpack Compose (Material 3, BOM 2024.02.00), custom dark design system |
| Storage | Room 2.6 (SQLite), DataStore Preferences, app-private files |
| Background | WorkManager, foreground service, accessibility service |
| Networking | OkHttp (raw HTTP/JSON for AI and Brevo, no SDKs) |
| Security | `androidx.biometric`, Android Keystore (AES-GCM keys, HMAC challenge codes) |
| Images | Coil |
| PDF | `android.graphics.pdf.PdfDocument` (no third-party library) |

---

## Project structure

```
app/src/main/java/com/vishnu/kohliprotocol/
├── MainActivity.kt          # 4-tab shell: Dashboard · Motivation · Discipline · Settings
├── AppContainer.kt          # manual dependency container
├── enforcement/             # accessibility monitor, overlay, policy, trusted clock, FG service, boot
├── data/
│   ├── local/               # Room database, entities, DAOs, converters
│   ├── preferences/         # DataStore, encrypted API keys, AI config
│   ├── repository/          # Food, Enforcement, Audit, Motivation repositories
│   ├── ai/                  # AIProvider, Gemini/Claude, prompts, strict parsers
│   ├── guardian/            # Guardian Gate: policy, requests, codes, delivery
│   ├── reports/             # weekly report pipeline, PDF generator, report email
│   ├── email/               # Brevo sender
│   └── restrictions/        # restriction categories, lists, emergency override
├── analysis/                # daily analysis manager + workers
├── weekly/                  # weekly evaluation (anchored weeks, games unlock)
├── reminders/               # meal reminders
├── security/                # biometric manager, session lock, SecureActivity
└── ui/                      # theme, components and screens
docs/                        # product spec (KOHLI_PROTOCOL.md) and phase roadmap (PHASES.md)
app/schemas/                 # exported Room schemas (keep: needed for migrations)
```

---

## Building and installing

No Android Studio required. You need the Android SDK (platform 34) and JDK 17.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.vishnu.kohliprotocol/.MainActivity
adb logcat | grep -E "KohliProtocol|AndroidRuntime"
```

If the SDK isn't found, create `local.properties` (git-ignored) with
`sdk.dir=/path/to/Android/Sdk`.

### First-run setup on the phone
1. **Settings → Enforcement setup:** enable the *Kohli Protocol Enforcement* accessibility service,
   allow notifications and disable battery optimization (important on Motorola and other OEM skins).
2. **Settings → AI settings:** add a Gemini (`AIza…`) or Claude (`sk-ant-…`) API key.
3. **Settings → Guardian code delivery:** enter the Guardian Brevo API key and a verified sender
   email; allow SMS if a guardian uses SMS.
4. **Settings → Guardians:** set up one or two guardians. Each confirms their contact with a code.
5. **Discipline → Report email:** your own Brevo key, verified sender and inbox for weekly reports.

### Optional dev keys
For development you can put AI keys in `local.properties`; they are compiled into debug builds
and used only when no key is saved in the app:
```properties
GEMINI_API_KEY=...
ANTHROPIC_API_KEY=...
```
`local.properties` is git-ignored. Never commit keys.

---

## Privacy and security notes
- No backend, no accounts, no analytics. Food logs, photos, ratings, reports, guardians and the
  audit log never leave the phone except as described above.
- API keys entered in the app are encrypted with the Android Keystore; nothing secret is in the APK.
- Known limits: SMS codes are copied into your own Messages app's Sent folder, and whoever owns a
  Brevo account can see what it sent. Ideally a guardian owns the Guardian Brevo account. A
  developer with the source can always bypass the app. Guardian Gate is designed to stop
  impulsive decisions, not a determined developer.

---

## Development history

Built in phases (see [`docs/PHASES.md`](docs/PHASES.md)):

1. Enforcement and app blocking
2. Local database and architecture
3. Core UI and food logging
4. AI analysis and daily approval
5. Biryani Parameter and game access
6. Guardian Gate and security
7. Weekly PDF reports and email
8. UI, typography and polish

---

## Licences

Personal project; all rights reserved.
Bundled fonts: **Bebas Neue** and **Poppins**, under the SIL Open Font License 1.1
(see [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/)).
