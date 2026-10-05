We are starting Phase 1 of Kohli Protocol.

CONTEXT TO READ:
1. Read `CLAUDE.md` for environment rules, technical stack, and commands.
2. Read `docs/PHASES.md` (Phase 1 section).
3. Read ONLY Sections 1, 2, 13, 14, 25, and 26 from `docs/KOHLI_PROTOCOL_SPEC.md` to understand the restriction philosophy and time-handling rules. DO NOT read the entire spec document.

TASK — PHASE 1: Enforcement & App Blocking
Implement Phase 1 core functionality under package `com.vishnu.kohliprotocol`:

1. Accessibility Service / App Monitoring Service:
   - Create a service to monitor active foreground apps.
   - Target food delivery package names: 
     - Swiggy (`in.swiggy.android`)
     - Zomato (`com.application.zomato`)
     - Zepto (`com.zepto.express`)
     - Blinkit (`com.grofers.customerapp`)

2. Restriction Overlay Screen:
   - Create a full-screen Activity (`RestrictionActivity`) alerting Vishnu that the target app is blocked by his personal discipline rules.
   - If a restricted app is launched, intercept it immediately and show this overlay.

3. Nighttime Payment Restriction:
   - Automatically block PhonePe (`com.phonepe.app`) between 9:00 PM (21:00) and 9:00 AM (09:00).
   - Use `SystemClock.elapsedRealtime()` alongside wall-clock time to detect time/clock tampering.

4. Foreground Service & Boot Recovery:
   - Keep the enforcement service active in a persistent Android Foreground Service with an ongoing notification ("Kohli Protocol — Personal Discipline Enforcement Active").
   - Create a `BOOT_COMPLETED` BroadcastReceiver (`BootReceiver`) to automatically start the service on device reboot.

5. Manifest Configuration:
   - Configure `AndroidManifest.xml` with required permissions (Accessibility Service, Foreground Service, Receive Boot Completed, System Alert Window, Query All Packages).

CONSTRAINTS:
- Do NOT execute simulation tasks, or adb commands.
- Output clean, compilation-ready Kotlin code.
- After creating/modifying files, summarize the code changes and tell me to proceed with manual compilation and adb installation.