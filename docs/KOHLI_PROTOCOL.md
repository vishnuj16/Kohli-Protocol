# **KOHLI PROTOCOL**

### **Personal Food Discipline & Digital Enforcement System**

> **Eat well. Log everything. Earn the Kohli.**

---

# **1\. PURPOSE**

Kohli Protocol is a private, single-user Android application designed to help enforce disciplined eating habits.

The philosophy is simple:

> **Rational Vishnu sets the rules. The app prevents Impulsive Vishnu from casually breaking them.**

The application combines:

* mandatory food logging  
* AI-assisted calorie estimation  
* daily food ratings  
* weekly performance evaluation  
* game rewards  
* food-delivery restrictions  
* nighttime payment restrictions  
* guardian-controlled security  
* biometric privacy  
* local-only data storage

The system is intentionally strict and blunt.

---

# **2\. DAILY FOOD LOGGING**

The app has four primary meal slots:

* Breakfast  
* Lunch  
* Evening / snacks  
* Dinner

Each asks:

> **What did you eat?**

The user can enter a natural-language description and optionally attach a photograph from the gallery.

A camera option may also be provided.

Every meal can be explicitly marked:

> **Skipped**

A skipped meal is considered **logged**, not missing.

There is also:

> **\+ Add Food**

for arbitrary food consumed outside the normal meal slots.

Examples:

* chocolate  
* biscuits  
* random snacks  
* drinks  
* late-night food  
* anything else

Food can be added at any time.

Food consumed after midnight belongs to the new calendar day.

---

# **3\. REMINDERS**

The application sends reminders when meal windows have passed.

It also sends end-of-day reminders approximately:

* **8 PM**  
* **10–11 PM**

The purpose is to ensure that the day is completely logged.

A day with a missing required meal entry is considered incomplete.

A deliberately skipped meal is valid because it has been explicitly recorded.

---

# **4\. DAILY AI ANALYSIS**

At the end of each day, the application sends the day's food information to an AI provider.

Information may include:

* descriptions  
* photographs  
* meal type  
* arbitrary food entries  
* skipped meals

The AI returns structured JSON containing:

* minimum calorie estimate for each meal  
* maximum calorie estimate for each meal  
* minimum daily calorie estimate  
* maximum daily calorie estimate  
* daily rating  
* daily category  
* optional confidence information

Calorie values are always estimates/ranges rather than falsely precise numbers.

Example:

{  
  "daily\_minimum\_calories": 1850,  
  "daily\_maximum\_calories": 2300,  
  "rating": 4,  
  "category": "fair play"  
}

---

# **5\. THE FIVE DAILY CATEGORIES**

Every day receives one of five ratings:

| Score | Category |
| ----- | ----- |
| **1** | **Fatass whale** |
| **2** | **Black hole** |
| **3** | **Fine init** |
| **4** | **Fair play** |
| **5** | **Bro is Kohli** |

The categories are numerical and linear.

**1 is the worst. 5 is the best.**

---

# **6\. DAILY AI APPROVAL**

AI analysis is not immediately considered final.

The following day, the user sees the previous day's analysis.

Example:

> **Yesterday**

> Estimated calories: **1,850–2,300**

> Rating: **4 — Fair play**

> **Accept**

> **Doesn't look right**

If the user rejects the result, the application sends the original information to the AI again.

The original descriptions and photographs remain stored.

---

# **7\. AI FAILURE**

If AI analysis fails:

> **Analysis Pending**

The original food information remains safely stored.

The app should:

* retry automatically approximately once per day  
* provide an **Analyze Now** button  
* handle API/rate-limit failures  
* validate returned JSON before accepting it

AI failure itself does not constitute eating failure.

---

# **8\. THE BIRYANI PARAMETER**

The weekly success threshold is called:

# **BIRYANI PARAMETER**

The Biryani Parameter determines the minimum weekly average required to unlock the user's games.

Default:

> **3.5**

The weekly average is calculated from the seven daily ratings.

Example:

3 \+ 4 \+ 4 \+ 5 \+ 3 \+ 4 \+ 4  
\= 27 / 7  
\= 3.86

Since:

> **3.86 ≥ Biryani Parameter 3.5**

the week succeeds.

---

# **9\. BIRYANI PARAMETER CONFIGURATION**

The Biryani Parameter is manually configurable.

The user may:

### **Increase it**

No authorization required.

Example:

> 3.5 → 4.0

### **Decrease it**

Requires **Guardian Gate** authorization.

The user cannot simply lower the standard during a difficult week.

The current Biryani Parameter should be prominently displayed in the application.

---

# **10\. GUARDIAN GATE**

**Guardian Gate** is the name of the application's guardian/MFA security mechanism.

It protects sensitive administrative actions such as:

* reducing the Biryani Parameter  
* removing restricted applications  
* changing guardian information  
* disabling major enforcement rules  
* emergency overrides

The system uses trusted guardians through protected email/SMS verification.

The exact number of confirmations required can be defined during implementation.

The important rule is:

> **The user can make the rules stricter alone, but cannot make them easier without Guardian Gate approval.**

---

# **11\. WEEKLY RESULT**

A week succeeds only if:

### **1\. All required food logs are complete**

AND

### **2\. Weekly average ≥ Biryani Parameter**

Example:

Biryani Parameter \= 3.5

Weekly average \= 3.71

Result:  
SUCCESS

If either condition fails:

> **WEEK FAILED**

---

# **12\. GAME ACCESS**

The current controlled games are:

* Chess.com  
* eFootball

### **Successful week**

> 🎮 **Games unlocked**

### **Failed week**

> 🔒 **Games locked**

The first week begins with the games locked.

The first successful weekly evaluation unlocks them.

---

# **13\. FOOD-DELIVERY RESTRICTIONS**

The following applications are blocked:

* Swiggy  
* Zomato  
* Zepto  
* Blinkit

The application should intercept attempts to open them and display a restriction screen.

Example:

> 🔒 **APPLICATION RESTRICTED**

> This application is currently blocked by your personal discipline rules.

Additional applications can be added manually.

Adding an application is easy.

Removing one requires **Guardian Gate** authorization.

---

# **14\. NIGHTTIME PAYMENT RESTRICTION**

PhonePe is blocked between:

> **9 PM → 9 AM**

The purpose is to prevent late-night food purchases through UPI.

Additional payment/UPI applications can be added to the restriction list.

Removing one requires Guardian Gate authorization.

---

# **15\. MOTIVATION GALLERY**

The application contains a dedicated:

# **Motivation**

section.

The user can upload photographs into this section.

The app displays them in a visually appealing gallery.

The gallery is intended to provide positive personal motivation and can contain photographs of people, places, goals, memories, or anything else the user associates with improving himself.

The gallery remains private and is protected by the application's biometric authentication.

---

# **16\. EXERCISE**

The application includes a manual exercise section.

Initially this will primarily support walking and other manually entered activities.

Example:

> **\+ Add Workout**

Possible activities:

* Walking  
* Running  
* Cycling  
* Gym  
* Other

Health Connect integration will be added later to automatically retrieve:

* steps  
* distance  
* exercise information

Exercise is secondary to the food-restriction system and should not delay the initial build.

---

# **17\. WEIGHT TRACKING**

Weight tracking is optional and initially disabled.

It can be enabled later when the user has access to a weighing machine.

It may eventually provide:

* weight history  
* trend charts  
* weekly report integration

Weight does not directly determine game access.

---

# **18\. WEEKLY AI REPORT**

At the end of every week, the application sends the week's collected information to an AI provider.

The report can include:

* daily food records  
* calorie ranges  
* daily ratings  
* daily categories  
* exercise information  
* weight information when enabled  
* weekly average  
* Biryani Parameter  
* success/failure result

The AI produces a structured weekly report containing:

* summary  
* good behaviours  
* poor behaviours  
* recurring patterns  
* recommendations  
* next-week focus

The AI should be direct and honest.

---

# **19\. WEEKLY PDF**

The weekly report is generated as a PDF and stored locally.

It should contain:

* date range  
* Biryani Parameter  
* weekly average  
* weekly category  
* game-access result  
* daily ratings  
* calorie ranges  
* exercise information  
* weight information if enabled  
* AI summary  
* recommendations

Charts may include:

* daily rating  
* calorie range  
* steps  
* weight trend

The PDF is also emailed to the user programmatically.

---

# **20\. LOCAL-ONLY ARCHITECTURE**

The application is designed to store its data locally.

There is:

* no cloud database  
* no Firebase database  
* no user account  
* no telemetry  
* no analytics service  
* no application backend

Local storage includes:

* food records  
* photographs  
* AI results  
* ratings  
* reports  
* exercise data  
* weight data  
* restrictions  
* guardian configuration  
* audit logs

The only information sent externally is information deliberately transmitted to an AI provider or required email/SMS services.

---

# **21\. SECURITY & PRIVACY**

The application requires biometric authentication.

Fingerprint authentication should protect:

* food history  
* photographs  
* calorie information  
* reports  
* motivation gallery  
* settings  
* guardian configuration  
* health information

Sensitive screens should use Android's secure-window protections where appropriate.

The restriction screen itself should reveal minimal information.

---

# **22\. GUARDIAN / MFA SECURITY**

Guardian Gate protects sensitive changes.

Possible protected actions include:

* lowering the Biryani Parameter  
* removing an application from the restricted list  
* changing guardian information  
* disabling important restrictions  
* emergency overrides

Guardian information itself cannot be casually changed.

If a guardian loses access to an email address or phone number, changing that guardian requires verification through the existing protected process.

The user should not be able to approve his own guardian change.

---

# **23\. EMERGENCY OVERRIDE**

An emergency override may exist for situations where a genuine exception is necessary.

The process requires Guardian Gate authorization.

Overrides should be:

* temporary where possible  
* explicitly recorded  
* visible in the audit log

---

# **24\. AUDIT LOG**

Important actions are recorded locally.

Examples:

* food logged  
* food edited  
* food marked skipped  
* AI analysis requested  
* AI analysis accepted/rejected  
* AI analysis regenerated  
* weekly result generated  
* games unlocked/locked  
* restricted app added  
* restricted app removal requested  
* Biryani Parameter changed  
* guardian changed  
* override requested/granted  
* enforcement disabled  
* enforcement restored  
* suspicious clock change detected

---

# **25\. ENFORCEMENT & TAMPERING**

The application should restore its restrictions after reboot.

It should detect relevant enforcement-service failures where Android permits.

The system should also account for:

* force stopping  
* disabling enforcement permissions  
* clock manipulation  
* application removal  
* device restarts

A normal Android application cannot silently reinstall itself after being uninstalled.

Therefore the implementation should investigate Android device-management capabilities rather than relying on automatic self-reinstallation.

The ultimate escape route of completely resetting/reconfiguring the device cannot be eliminated by an ordinary application.

---

# **26\. TIME HANDLING**

Time-based restrictions should not blindly trust a mutable system clock.

The application should use Android's monotonic elapsed-time facilities alongside wall-clock timestamps.

This allows it to detect suspicious changes to:

* system time  
* timezone  
* elapsed duration

The PhonePe restriction remains:

> **21:00–09:00**

---

# **27\. DATABASE**

The application should use a local Room/SQLite database.

Core entities include:

Meal  
FoodEntry  
FoodPhoto  
DailyAnalysis  
WeeklyReport  
ExerciseEntry  
WeightEntry  
RestrictedApplication  
RestrictionSchedule  
GameAccess  
Guardian  
AuditEvent

Photographs are stored in private application storage.

---

# **28\. AI ARCHITECTURE**

AI access should use a provider abstraction rather than hard-coding the entire application around Gemini.

Conceptually:

AIProvider  
 ├── GeminiProvider  
 ├── ClaudeProvider  
 └── OpenAIProvider

Core functions:

analyzeMeal()  
generateWeeklyReport()

The deterministic application rules remain separate from AI output.

AI provides estimates and assessments.

The application decides whether the user has earned game access.

---

# **29\. TECHNOLOGY**

Initial Android stack:

* Kotlin  
* Android Studio  
* Jetpack  
* Room / SQLite  
* DataStore  
* BiometricPrompt  
* Android notifications  
* appropriate Android application-management APIs  
* local file storage  
* HTTP/JSON AI integration  
* PDF generation

Health Connect comes later.

---

# **30\. DEVELOPMENT STRATEGY**

The user intends to use Claude Pro as the primary coding assistant rather than attempting the entire project alone.

The project should be implemented incrementally.

### **Phase 1 — Enforcement**

Prove:

* app blocking  
* restriction screen  
* PhonePe schedule  
* game access control  
* reboot recovery  
* tamper detection  
* biometric authentication

Test on the **Motorola Edge 40**.

### **Phase 2 — Local database**

Implement:

* meals  
* arbitrary food  
* skipped meals  
* photographs  
* daily records  
* weekly records  
* audit logs

### **Phase 3 — Food UI**

Implement:

* meal logging  
* reminders  
* gallery upload  
* camera  
* arbitrary food  
* skipped meals

### **Phase 4 — AI**

Implement:

* provider abstraction  
* daily analysis  
* JSON validation  
* calorie ranges  
* 1–5 rating  
* approval/rejection  
* retries

### **Phase 5 — Weekly system**

Implement:

* Biryani Parameter  
* weekly average  
* game unlocking  
* AI weekly report  
* PDF  
* email

### **Phase 6 — Guardian security**

Implement:

* Guardian Gate  
* MFA  
* protected configuration  
* emergency overrides  
* audit logging

### **Phase 7 — Health Connect**

Implement steps/exercise integration.

### **Phase 8 — Weight tracking**

Implement optional weight functionality.

---

# **31\. MINIMUM VIABLE PRODUCT**

The first usable version requires:

Food logging  
      \+  
Photos  
      \+  
Skipped meals  
      \+  
Arbitrary food  
      \+  
AI analysis  
      \+  
Five daily ratings  
      \+  
Biryani Parameter  
      \+  
Weekly evaluation  
      \+  
Game lock/unlock  
      \+  
Swiggy/Zomato/Zepto/Blinkit blocking  
      \+  
PhonePe 9 PM–9 AM restriction  
      \+  
Guardian Gate  
      \+  
Fingerprint lock  
      \+  
Local storage

Health Connect, weight tracking and additional analytics can follow.

---

# **32\. THE CORE RULE**

## **THE BIRYANI PARAMETER**

The entire reward mechanism ultimately comes down to:

> **Did Vishnu actually log everything?**

and

> **Was his weekly average good enough?**

With the default:

> **Biryani Parameter \= 3.5**

If:

complete logs  
AND  
weekly average \>= 3.5

then:

> 🎮 **GAMES UNLOCKED**

Otherwise:

> 🔒 **GAMES LOCKED**

The user may make the Biryani Parameter stricter himself.

Making it easier requires:

> **Guardian Gate.**

---

# **33\. PROJECT MOTTO**

> **LOG EVERYTHING.**

> **HIDE NOTHING.**

> **DON'T GAME THE SYSTEM.**

> **BEAT THE BIRYANI PARAMETER.**

> **CHASE THE KOHLI.**

> **BRO IS KOHLI.**

---

# **34\. CURRENT STATUS**

**Concept:** Defined  
**Core rules:** Defined  
**Food system:** Defined  
**AI system:** Defined  
**Biryani Parameter:** Defined  
**Game system:** Defined  
**App restrictions:** Defined  
**Night payment restriction:** Defined  
**Motivation gallery:** Defined  
**Guardian Gate:** Defined  
**Biometric security:** Defined  
**Local-only architecture:** Defined  
**Development phases:** Defined  
**Testing device:** Available

### **First engineering task**

> **Build and test the Android enforcement prototype on the Motorola Edge 40\.**

Do not build the entire application at once.

First prove that the application can reliably enforce the rules.

Then build the food system around it.

---

# **KOHLI PROTOCOL**

### **A completely unnecessary amount of software engineering dedicated to making sure one man stops ordering biryani at 11:47 PM.**

