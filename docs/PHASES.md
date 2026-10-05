# KOHLI PROTOCOL — Phase-Wise Development Roadmap

## Project Package Architecture
- **Root Package:** `com.vishnu.kohliprotocol`
- **Lead Developer:** Vishnu

---

## Overview Strategy

Phase 1: Enforcement & App Blocking (Core Lock System)
Phase 2: Local Database & Architecture (Room & DataStore)
Phase 3: Core UI & Food Logging System
Phase 4: AI Analysis Integration & Daily Approval
Phase 5: Biryani Parameter & Game Access System
Phase 6: Guardian Gate & Security (MFA & Fingerprint)
Phase 7: Exports, PDF Generation & Polish

---

## Phase 1: Enforcement & App Blocking (Core Lock System)
*Goal: Prove the app can reliably intercept target applications, enforce time-based rules, and survive device reboots on the Motorola Edge 40.*

### Deliverables:
- **Accessibility Service / Usage Stats Service:** Intercept launches of targeted food-delivery applications (`in.swiggy.android`, `com.application.zomato`, `com.zeptoconsumerapp`, `com.grofers.customerapp`).
- **Restriction Overlay Screen:** Full-screen blocking UI displaying enforcement warning when restricted apps are launched.
- **Nighttime Payment Restriction:** Automatically block payment/UPI apps (e.g., `com.phonepe.app`) during the 9 PM – 9 AM window using `SystemClock.elapsedRealtime()` to avoid clock tampering.
- **Foreground Service:** Persistent Android foreground service with notification to prevent background termination by OS battery optimization.
- **Boot Receiver:** `BOOT_COMPLETED` listener to automatically re-engage services upon device restart.

### Verification Checklist:
1. Open Swiggy/Zomato -> App is immediately intercepted and blocked by the restriction screen.
2. Adjust system time -> Payment restrictions enforce correctly during the 9 PM – 9 AM window.
3. Reboot device -> Enforcement resumes without manual app launch.

---

## Phase 2: Local Database & Architecture (Room & DataStore)
*Goal: Set up the local-only data engine using Room SQLite and DataStore.*

### Deliverables:
- **Room Database Schema:**
  - `Meal`: `id`, `date`, `mealType` (Breakfast, Lunch, Evening, Dinner), `timestamp`, `isSkipped`
  - `FoodEntry`: `id`, `mealId`, `description`, `photoPath`, `timestamp`
  - `DailyAnalysis`: `date`, `minCal`, `maxCal`, `rating` (1–5), `category`, `isApproved`
  - `WeeklyReport`: `startDate`, `endDate`, `averageRating`, `biryaniParameter`, `result`
  - `AuditEvent`: `timestamp`, `actionType`, `description`
- **DataStore Storage:** Key-value configuration for active state (`biryaniParameter = 3.5`, `isGamesUnlocked = false`).
- **Internal File Storage Manager:** Helper classes to safely save and retrieve meal photos in app-private directories.

### Verification Checklist:
1. Execute Room DAO unit tests / mock operations.
2. Confirm persistent storage survives app kills and process restarts.

---

## Phase 3: Core UI & Food Logging System
*Goal: Build the daily meal interface, photo integration, skipped meal tracking, and local reminders.*

### Deliverables:
- **Dashboard UI:** Clean dark-mode screen showing today's 4 primary meal slots.
- **Meal Logging Screen:**
  - Natural-language text description input.
  - Camera capture and gallery pick for photo attachments.
  - Explicit **"Mark Skipped"** state handling.
- **"+ Add Food" Component:** Arbitrary entry screen for extra snacks or off-window eating.
- **Motivation Gallery UI:** Private grid view displaying locally saved inspirational photos.
- **Notification Engine:** Local Android notification alerts scheduled for 8 PM and 10 PM if meals remain unlogged.

### Verification Checklist:
1. Log a meal with a photo attached.
2. Mark a slot as explicitly skipped.
3. Add an arbitrary midnight snack.
4. Verify all entries reflect accurately in the daily log history.

---

## Phase 4: AI Analysis Integration & Daily Approval
*Goal: Integrate AI provider abstraction for daily calorie estimation and 1–5 scale ratings.*

### Deliverables:
- **`AIProvider` Abstraction:** Flexible API wrapper interface supporting Gemini, Claude, and OpenAI HTTP endpoints.
- **JSON Parser & Validator:** Send end-of-day meals/photos to the configured AI endpoint and strictly parse structured response:
  ```json
  {
    "daily_minimum_calories": 1850,
    "daily_maximum_calories": 2300,
    "rating": 4,
    "category": "Fair play"
  }
  ```

- **Approval UI ("Yesterday's Rating"):** Morning prompt displaying previous day's AI rating with **"Accept"** or **"Doesn't look right"** options.
- **Retry Mechanism:** Background `WorkManager` retries for network drops or API rate limits.

### Verification Checklist:

1. Submit test day data to the AI service.
2. Confirm valid JSON handling and correct rendering in the daily approval UI.

---

## Phase 5: Biryani Parameter & Game Access System

*Goal: Implement the weekly performance evaluation and automated game locking/unlocking engine.*

### Deliverables:

* **Biryani Parameter Engine:**
  * 7-day rolling / weekly average rating calculation.
  * Completeness validation (all required meal slots explicitly logged or skipped).
  * Core Logic: `Weekly Average >= Biryani Parameter AND Complete Logs = SUCCESS`.

* **Game Lock Controller:** Dynamic app blocking integration for targeted game applications (e.g., Chess.com, eFootball).
* **Rule Adjustment Logic:** User can freely increase the Biryani Parameter threshold, but decreasing it requires Guardian authorization.

### Verification Checklist:

1. Mock a passing week (e.g., 3.86 vs target 3.5) -> Games unlock.
2. Mock an incomplete log or low rating average -> Games lock instantly.

---

## Phase 6: Guardian Gate & Security (MFA & Biometrics)

*Goal: Secure the application against self-sabotage, clock manipulation, and unapproved configuration changes.*

### Deliverables:

* **Biometric Integration:** Protect sensitive screens (history, motivation gallery, settings) using `BiometricPrompt` API.
* **Guardian Gate Workflow:** Secondary email/SMS verification required for:
  * Lowering the Biryani Parameter.
  * Removing apps from restriction lists.
  * Modifying guardian contacts or emergency overrides.

* **Audit Logger:** Write local security events (overrides, time changes, parameter edits) to `AuditEvent` table.

### Verification Checklist:

1. Access restricted screens -> Fingerprint prompt requested.
2. Attempt parameter reduction -> Triggers Guardian Gate prompt.

---

## Phase 7: Exports, PDF Generation & Polish

*Goal: Generate end-of-week PDF reports, handle email delivery, and apply final UI polish.*

### Deliverables:

* **Weekly PDF Generator:** Render local PDF summary containing weekly averages, calorie ranges, ratings, and AI recommendations.
* **Automated Email Dispatch:** Programmatically email generated weekly PDF reports.
* **UI & System Polish:** Custom dark theme, window flags (`FLAG_SECURE`) on biometric screens, and smooth transitions.

### Verification Checklist:

1. Trigger end-of-week action -> Verify PDF document creation and successful email dispatch.

---

### Step 2: Update Package Name in `app/build.gradle.kts` & `MainActivity.kt`

Make sure your package names match `vishnu` across your setup:

1. **`app/build.gradle.kts`**:
   Update `namespace` and `applicationId` to:
   ```kotlin
   namespace = "com.vishnu.kohliprotocol"
   defaultConfig {
       applicationId = "com.vishnu.kohliprotocol"
   }
   ```

2. **Directory Structure & `MainActivity.kt`**:
```bash
mv app/src/main/java/com/vyas app/src/main/java/com/vishnu
```

Inside `app/src/main/java/com/vishnu/kohliprotocol/MainActivity.kt`, set the package header to:
```kotlin
package com.vishnu.kohliprotocol
```

3. **`AndroidManifest.xml`**:
Ensure `MainActivity` points to `.MainActivity` under package `com.vishnu.kohliprotocol`.

Everything is now aligned around **Vishnu** and fully documented inside `docs/PHASES.md` for Claude Code to execute!