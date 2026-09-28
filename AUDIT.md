# 🔍 JARVIS Auto — Deep Technical Audit

**Repository:** `pawarroshan723-maker/jarvis` (branch `arena/01a0e743-jarvis`)
**Audit date:** 2026-09-28
**Codebase:** Android app · Kotlin + Jetpack Compose · 21,118 LOC (main) / 460 LOC (tests)
**Single commit:** `2e1ab4e` — "build: initialize Android project structure"

---

## 1. Executive Summary

**JARVIS Auto** is an ambitious, offline-first, bilingual (English/Marathi) voice assistant for Android with:

- 🎙️ Continuous speech recognition with wake-word gating ("Jarvis"), TTS with Marathi voice fallback/transliteration
- 🤖 Gemini cloud intelligence with a multi-tier model "cascade" + offline knowledge/intent fallback
- 📡 Real-time sensor automation (shake, flip, proximity wave, light, battery) with a conflict-resolution engine
- ☎️ Auto call/SMS responder, scheduled SMS & Gemini-prompt tasks via AlarmManager
- 🔒 Lockscreen operation with WakeLocks, foreground service (specialUse|microphone)

The engineering shows genuine creativity (edge-triggered conflict engine, acoustic echo guard, TTS transliteration fallback), but the project is **not shippable in its current state**. The audit found **4 critical**, **7 high**, and ~20 medium issues spanning security, Play Store policy, correctness, and build reproducibility.

### Risk Scorecard

| Area | Grade | Headline |
|---|---|---|
| Security & Secrets | 🔴 **D** | API key baked into APK; exported receiver lets any app trigger SMS tasks |
| Play Store compliance | 🔴 **D** | SMS/Call-Log/Contacts permissions + USE_EXACT_ALARM will be auto-rejected |
| Build reproducibility | 🔴 **D** | No Gradle wrapper; custom debug keystore gitignored; 48 MB of APKs committed |
| Gemini integration | 🟠 **C-** | Cascade hard-codes model IDs that don't exist → 404s on every first attempt |
| Reliability | 🟡 **C** | Destructive DB migration; 15 s WakeLock vs 45 s network timeout; command misfires |
| Performance / battery | 🟡 **C-** | Persistent WakeLock + always-on mic + polling collectors |
| Architecture | 🟡 **C-** | 1,358-line god-method; duplicated command pipelines; no DI |
| Testing | 🟡 **C** | 17 real unit tests exist, but they enshrine wrong model IDs; no coverage of core engines |
| Documentation | 🔴 **F** | No README, no LICENSE, no CI, zero docs |

> **Overall: C− — a strong prototype that needs a hardening pass before any distribution.**

---

## 2. Repository Snapshot

```
jarvis/
├── Jarvis-Auto.apk            24 MB  ← committed binary (should not be in git)
├── app-debug.apk              24 MB  ← committed binary (should not be in git)
├── app/                       single Gradle module
│   └── src/main/java/com/example/
│       ├── JarvisApplication.kt      (159)   service locator / voice dispatcher
│       ├── MainActivity.kt           (508)   Compose host + permission launchers
│       ├── engine/    (3,410 LOC)    Gemini engine, offline intent & knowledge, memory
│       ├── voice/     (2,335 LOC)    SpeechRecognizer, TTS, VAD, wake-word lock
│       ├── hardware/  (1,193 LOC)    torch, volume, media keys, app launch, YouTube
│       ├── sensor/    (  536 LOC)    SensorHub + conflict engine
│       ├── system/    (1,854 LOC)    telephony, auto-SMS, scheduled tasks, receivers
│       ├── service/   (  387 LOC)    foreground automation service
│       ├── data/      (  604 LOC)    Room DB v4 (6 entities) + repository
│       └── ui/        (10,134 LOC)   5 Compose screens, HUD, diagnostics dialog
├── gradle/libs.versions.toml  AGP 9.1.1 · Kotlin 2.2.10 · Compose BOM 2024.09.00
└── (no README, no LICENSE, no CI, no gradlew)
```

**Data stored on device (unencrypted):** automation rules, execution logs, **command history (every voice query + reply)**, **call/SMS logs incl. message bodies**, scheduled tasks with **recipient phone numbers**, custom Gemini API key, and a **persisted 16-turn conversation transcript** (SharedPreferences JSON).

---

## 3. Architecture Overview

```
Voice input ──► JarvisSpeechManager (SpeechRecognizer + VAD + EchoGuard + Wake-word lock)
                  │
                  ▼
      JarvisApplication.handleVoiceCommand   ◄──┐  (background/service path)
      JarvisViewModel.submitVoiceCommand     ◄──┤  (UI path)          ⚠ two divergent pipelines
                  │
                  ▼
      OfflineIntentEngine (~1,200-line matcher: EN + MR)
                  │ handled? ──yes──► TTS reply + CommandHistory(Room)
                  no
                  ▼
      OfflineKnowledgeEngine (math, conversions, facts, diagnostics)
                  │ handled? ──yes──► TTS reply
                  no
                  ▼
      GeminiAssistantEngine ──► model cascade (up to 6 models, 429/503 fallback)
                  │                     + googleSearch/googleMaps grounding tools
                  ▼
      ConversationMemory (16-turn sliding window persisted to SharedPreferences)

Parallel: JarvisAutomationService (FGS) ──► SensorHub events ──► SensorConflictEngine ──► executeRule
          JarvisSmsReceiver / JarvisCallReceiver ──► AutoCallSmsManager ──► SmsManager
          ScheduledTaskReceiver (AlarmManager exact) ──► ScheduledTaskManager ──► SMS / Gemini
```

---

## 4. Critical Findings 🔴

### C1 — Exported receiver allows any app to trigger scheduled SMS tasks
- **File:** `app/src/main/AndroidManifest.xml` (ScheduledTaskReceiver), `system/ScheduledTaskManager.kt` (`ACTION_EXECUTE_TASK = "com.example.jarvis.ACTION_EXECUTE_SCHEDULED_TASK"`)
- **Evidence:** The receiver is declared `android:exported="true"` with a **custom, unprotected action** and **no permission guard**. It executes the task directly: `taskManager.executeTask(taskId)`.
- **Impact:** Any malicious app on the device can broadcast this intent with a guessed/known task ID (IDs are sequential `Long`s) to fire the victim's scheduled SMS tasks on demand, or to boot-reschedule tasks — a classic *confused-deputy*. Because task execution **sends SMS without user interaction**, this can be used for toll fraud / harassment from the victim's SIM.
- **Fix:** Set `android:exported="false"` (your own `PendingIntent.getBroadcast` uses an *explicit* intent, so it still works), or guard with a signature-level custom permission. Also validate `intent.getPackage()` / sender identity in `onReceive`.

### C2 — Gemini API key is embedded in the shipped APK
- **Files:** `app/build.gradle.kts` (secrets plugin, `buildConfig = true`), `engine/GeminiAssistantEngine.kt` (`BuildConfig.GEMINI_API_KEY`, endpoint `...?key=$apiKey`)
- **Evidence:** `secrets { propertiesFileName = ".env" }` injects `GEMINI_API_KEY` into `BuildConfig`. Release builds ship with `isMinifyEnabled = false`, so the constant is trivially extractable (`apktool`/`strings`). The key is also passed **in the URL query string** (`?key=`), which leaks into HTTP-level logs, crash reporters, and proxy traces.
- **Impact:** Anyone with the APK gets a working Gemini key → quota theft and billing exposure for the owner. `.env.example` explicitly notes the key "will NOT be packaged" if commented — but it ships uncommented as a default.
- **Fix:** Ship **no** default key; proxy calls through a backend (or use Firebase AI / Vertex with App Check — the deps are already present but unused!). Use the `x-goog-api-key` **header** instead of the query param. Enable R8.

### C3 — Sensitive data persisted unencrypted + cloud backup enabled with default rules
- **Files:** `data/JarvisDatabase.kt`, `engine/GeminiAssistantEngine.kt` (custom key in `jarvis_gemini_prefs`), `engine/ConversationMemory.kt` (transcript JSON in `jarvis_context_memory`), `AndroidManifest.xml` (`allowBackup="true"`), `res/xml/backup_rules.xml` + `data_extraction_rules.xml` (**empty templates — nothing excluded**)
- **Impact:** Call/SMS logs, message bodies, scheduled-task phone numbers, the user's custom API key, and the full conversation transcript are (a) stored in plaintext SQLite/SharedPreferences and (b) **uploaded to the user's cloud backup** and included in D2D transfers by default.
- **Fix:** Exclude `jarvis_*` prefs and the DB from backup; encrypt the custom key with `EncryptedSharedPreferences`/Keystore; document retention; consider SQLCipher if the data model keeps SMS bodies.

### C4 — Default-ON auto-messaging can send paid SMS with no per-event confirmation
- **Files:** `system/AutoCallSmsManager.kt` (`isAutoSmsEnabled = true`, `isAutoReplyMissedCallsEnabled = true` defaults), `system/ScheduledTaskManager.kt` / `engine/OfflineIntentEngine.kt` (fallback number `"9876543210"`)
- **Evidence:**
  - Out of the box, **every incoming SMS and every missed call is auto-answered** with an SMS (anti-spam cooldown of 3 min is in-memory only and resets on process death).
  - A voice-scheduled SMS with no parseable recipient silently targets the hard-coded default `"9876543210"` — a real Indian mobile number range.
  - Voice commands `"emergency sos"` / `"sos"` fire `triggerEmergencySos()` with **no confirmation step**.
- **Impact:** Unintended financial cost, spam complaints, carrier throttling, and misdirected messages to a stranger; a mis-heard SOS texts a possibly wrong number.
- **Fix:** Default all auto-reply toggles to **off** with explicit opt-in onboarding; never fall back to a hard-coded number (require confirmation instead); add persistent per-number rate limiting; require a spoken confirmation ("confirm SOS") before sending.

---

## 5. High Findings 🟠

### H1 — Two 24 MB APKs committed to git
- `Jarvis-Auto.apk` and `app-debug.apk` (48 MB combined; the repo pack is 23.7 MB). They bloat every clone forever, invite confusion about which build is current, and the debug APK may contain the **real** `GEMINI_API_KEY` if built on a machine with `.env` populated (ties into C2).
- **Fix:** Delete from git (`git rm --cached`), add `*.apk` to `.gitignore`, distribute via GitHub Releases or Play internal testing.

### H2 — Fresh clone cannot build: no Gradle wrapper, missing debug keystore
- **Files:** `gradle/wrapper/gradle-wrapper.properties` exists but **`gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` are missing**; `app/build.gradle.kts` points the debug signing config at `${rootDir}/debug.keystore`, which is **gitignored**.
- **Impact:** `./gradlew` fails out of the box; even with a local Gradle 9.3.1 install, `assembleDebug` fails with "keystore not found" because AGP does not auto-generate a *custom* keystore path.
- **Fix:** Regenerate and commit the wrapper (`gradle wrapper`); fall back to AGP's default debug keystore (drop the custom debugConfig) or commit a known debug keystore (it is meant to be public).

### H3 — Cascade hard-codes Gemini model IDs that do not exist
- **Files:** `engine/GeminiModels.kt`, `engine/GeminiAssistantEngine.kt`, `test/.../ExampleUnitTest.kt`
- **Evidence (verified against the current model catalog, Sept 2026):** active Google models include Gemini 2.5 Flash / 2.5 Flash-Lite, **Gemini 3 Flash** (12/2025), Gemini 3.1 Pro (02/2026) and **Gemini 3.5 Flash** (05/2026) ([1](https://www.gradually.ai/en/gemini-models/)). There is **no 3.6 / 3.7 / 3.8 Flash**, and no evidence for `gemini-3.5-flash-lite` or `gemini-3.1-flash-lite-preview`.
  - `AUTO_CASCADE` (the default) starts with `gemini-3.5-flash-lite` → likely **404 on the very first attempt** of every query, adding latency before hitting valid aliases (`gemini-flash-latest`, `gemini-flash-lite-latest`).
  - `CASCADE_FLASH` burns **three guaranteed-404 attempts** (3.8 → 3.7 → 3.6) before reaching a valid model.
  - The unit test `verifyModernGeminiModelTiersAndAbsenceOfDeprecatedModels` *asserts the phantom IDs exist*, i.e., the tests codify the bug.
- **Impact:** Every cloud query pays a 404 round-trip tax; users on fixed tiers get hard failures with misleading error text ("High Demand").
- **Fix:** Fetch the model list at runtime (`GET /v1beta/models`) or pin to verified IDs (`gemini-3.5-flash`, `gemini-flash-latest`, `gemini-flash-lite-latest`, `gemini-2.5-flash-lite`); rewrite the test to validate against the live catalog.

### H4 — Google Play policy blockers (distribution-infeasible as declared)
- **Files:** `AndroidManifest.xml`, `system/TelephonyAlarmManager.kt` (`ALL_PERMISSIONS`)
- `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`, `CALL_PHONE`, `READ_CALL_LOG`, `READ/WRITE_CONTACTS` — all are **restricted permissions** requiring a declared, approved core use-case; a general "assistant" app is the canonical rejection example.
- `USE_EXACT_ALARM` is restricted to alarm-clock/calendar apps; this app also declares `SCHEDULE_EXACT_ALARM` (redundant duo).
- `FOREGROUND_SERVICE_SPECIAL_USE` requires a Play Console declaration; `SMS_RECEIVED` receiver at `android:priority="999"` is a red flag reviewers look for.
- The manifest `queries` and code paths target **YouTube ReVanced / Vanced / NewPipe** packages (`app.revanced.*`, `com.vanced.*`) — modded-app integration is itself a policy risk.
- **Fix:** For Play distribution, strip SMS/call-log features or move to `ACTION_SENDTO`/dialer-intent flows and apply with a permissions declaration video; drop `USE_EXACT_ALARM`; keep `priority` at default. Side-load/APK-direct distribution is the current realistic channel — say so explicitly in docs.

### H5 — Room `fallbackToDestructiveMigration` silently wipes user data
- **File:** `data/JarvisDatabase.kt` (version 4)
- Any future schema change (or a user upgrading across versions) **destroys all rules, logs, command history, SMS logs and scheduled tasks** without notice.
- **Fix:** Ship real `Migration`s and remove destructive fallback before release.

### H6 — Foreground-service WakeLock vs. network timeout mismatch in scheduled tasks
- **Files:** `system/ScheduledTaskReceiver.kt` (15 s wakelock), `engine/GeminiAssistantEngine.kt` (30 s connect / 45 s read timeouts)
- A `GEMINI_QUERY`/`GEMINI_TO_SMS` task that hits slow network outlives the 15 s wakelock; the CPU can sleep mid-flight and the `goAsync()` window can expire → tasks silently fail or send partially.
- **Fix:** Single source of truth: a coroutine `WorkManager` job (or extend wakelock to cover the request lifecycle), plus a hard per-task timeout under 15 s.

### H7 — Mass permission request & lockscreen exposure
- **Files:** `MainActivity.kt`, `system/TelephonyAlarmManager.kt`
- The Comms tab can fire a **single dialog requesting SMS + Call + Contacts + Location + Mic + Camera + Bluetooth at once** — both a UX anti-pattern and a review red flag.
- `MainActivity` sets `setShowWhenLocked(true)/setTurnScreenOn(true)` **unconditionally at launch**, so the full app UI (contacts, SMS history) renders **above the lock screen** whenever the notification mic action lifts the activity.
- **Fix:** Request permissions contextually, one capability at a time, with rationale screens; only arm show-when-locked while a voice session is actually active, and render a redacted lockscreen UI.

---

## 6. Medium Findings 🟡

| # | Finding | Evidence / File | Notes |
|---|---|---|---|
| M1 | **God method & god files** | `OfflineIntentEngine.processCommand` ≈ 1,200 lines, 90+ `contains()` branches; `SpeechManager` 1,233; `CoreHudScreen` 1,205; `SystemCommsScreen` 1,334; `ScheduledTasksView` 1,451 | Untestable, merge-hazard. Split into intent handlers per domain (a map of `IntentHandler` strategy objects); extract screen sections. |
| M2 | **Duplicated, divergent command pipelines** | `JarvisApplication.handleVoiceCommand` vs `JarvisViewModel.submitVoiceCommand` | Background path forces `enableHighThinking = false`, doesn't handle `CLEAR_CONVERSATION`, uses different fallback copy. Consolidate into one engine facade. |
| M3 | **"Clear conversation" voice command half-works** | `OfflineIntentEngine` returns success but only the ViewModel path calls `clearConversationMemory()`; the service/background path speaks "context reset" without resetting | Inconsistent behavior depending on entry point. |
| M4 | **Keyword `contains()` matching causes misfires** | e.g. `"model"` triggers the Gemini model-switcher branch for any sentence containing "model" ("explain the business model"); `"help"` hijacks any sentence with "help"; alarm regex `(\d{1,2})` matches any number in an alarm utterance | Requires phrase anchoring/word boundaries or a small NLU (e.g., regex+priority table, or on-device embeddings). |
| M5 | **Deprecated screen-wake API** | `JarvisWakeLockManager.wakeUpScreen` uses `SCREEN_BRIGHT_WAKE_LOCK` (deprecated since API 13, largely a no-op on modern devices) | Screen wake likely fails on many ROMs; use `setTurnScreenOn` + full-screen intent or `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` replacement paths. |
| M6 | **Battery-first concerns** | Persistent PARTIAL_WAKE_LOCK, always-on mic modes (2-hour wakelock), FGS starts with `microphone` type even when mic idle (privacy indicator always on), accelerometer at `SENSOR_DELAY_UI` streaming continuously | Document battery impact; drop `microphone` FGS type until a session actually needs it; gate accel to `NORMAL`. |
| M7 | **YouTube scraping for "direct autoplay"** | `HardwareController.fetchFirstVideoId` scrapes `youtube.com/results` HTML with a spoofed Chrome UA, raw `Thread`+`FutureTask` blocking the caller up to 2.2 s | Brittle (markup changes), ToS-gray, and blocks a Default dispatcher thread. Use `MEDIA_PLAY_FROM_SEARCH` only (it already exists in the same method) or the official YouTube Data API. |
| M8 | **Unused heavy dependencies inflate the 24 MB APK** | `firebase-ai`, `firebase-appcheck-*`, `retrofit`, `converter-moshi`, `moshi-kotlin`, `logging-interceptor` declared; engine uses raw OkHttp+org.json. `material-icons-extended` alone adds MBs (APK ships **6 dex files**) | R8 is disabled (`isMinifyEnabled=false`) so none of it is stripped. Prune deps, enable minify+resource shrinking, replace icon-extended with individual icons. |
| M9 | **Silent SMS-provider writes** | `TelephonyAlarmManager.recordSentSms` / `ScheduledTaskManager.recordSentSmsInSystemProvider` insert into `content://sms/sent` | Non-default SMS apps get SecurityException (caught & logged) — dead code on most devices; remove or feature-detect (only the default SMS app may write). |
| M10 | **Alarm/timer commands can misfire or silently no-op** | `"wake me up"` with no parseable time falls through to UNKNOWN; `HOURLY` computes `intervalMinutes/60` so "every 90 minutes" is impossible; bare `"set alarm"` matches any digit in the sentence | Add explicit time-validation and speak a failure instead of silence. |
| M11 | **OkHttp response hygiene & retry policy** | `executeGeminiRequest` doesn't `response.use { }`; fixed 250 ms cascade delay; no exponential backoff or Retry-After parsing on 429 | Minor leak risk + quota-unfriendly. |
| M12 | **All UI strings hard-coded** | `res/values/strings.xml` contains only `app_name`; EN/MR text lives in Kotlin string literals | Blocks localization and consistent copy; move to resources per locale. |
| M13 | **No README / LICENSE / CI / issue templates** | Repo root | The feature set is complex enough that docs are a functional requirement (permissions, SMS costs, wake-word, TTS voice pack installation). |
| M14 | **Tests partially wrong / fragile** | `ExampleUnitTest` asserts phantom model IDs (see H3); `GreetingScreenshotTest` targets SDK 36 via Robolectric 4.16 (verify matrix) and writes screenshots into `src/test/`; `androidTest` is the untouched template stub | No tests for `OfflineIntentEngine`, `SensorConflictEngine`, `AutoCallSmsManager`, `ConversationMemory` — the highest-value pure-logic targets. |
| M15 | **In-memory only anti-spam / cooldown state** | `AutoCallSmsManager.recentReplies` (ConcurrentHashMap, not persisted) | Restart during a messaging storm re-opens auto-replies. Persist last-replied timestamps in Room. |
| M16 | **Misleading app-close semantics** | `closeAppOrGoHome` uses `killBackgroundProcesses` — a no-op for most 3rd-party apps on modern Android, yet Jarvis replies "Closed YouTube…" | Overpromise; go-home alone is honest. |
| M17 | **`list rules` dead code** | `OfflineIntentEngine`: `val total = repository.allRules` (Flow, unused) and unused `bodySeparators` list in SMS parsing | Dead code / lint noise. |
| M18 | **Notification small-icon misuse** | FGS notification uses `R.mipmap.ic_launcher` as small icon | Adaptive launcher icons render as a grey/green blob on many devices; use a monochrome vector drawable. |
| M19 | **Stale Compose BOM on bleeding-edge toolchain** | Compose BOM `2024.09.00` with AGP 9.1.1 / Kotlin 2.2.10 (Sept 2026 stack) | Two years of Compose fixes missing; known interop bugs possible. Bump BOM. |
| M20 | **AGP 9 built-in Kotlin assumption** | No `org.jetbrains.kotlin.android` plugin anywhere; relies on AGP 9's built-in Kotlin | Breaks builds for anyone on AGP < 9; document required toolchain (JDK 17+, Gradle 9.3.1, AGP 9.1.1). |

---

## 7. Low Findings 🟢

- **L1** `uses-permission android.permission.FLASHLIGHT` is not a real permission (torch needs none) — remove.
- **L2** Legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions lack `android:maxSdkVersion="30"`.
- **L3** `CAMERA` runtime permission is requested at startup but the app never opens the camera device itself (torch + external intents need nothing).
- **L4** `metadata.json` (`requestFramePermissions: []`) contradicts the app's actual permission needs — stale AI Studio template artifact.
- **L5** Voice "offline knowledge" fact `text.contains("42")` hijacks any query containing the digits 42 (e.g., "who is player 42") → Hitchhiker's answer.
- **L6** Marathi alarm confirmation reads awkwardly: `"सकाळी/वेळेसाठी %02d:%02d चा अलार्म..."` (never says actual AM/PM).
- **L7** Trig/conversion answers are always English even in Marathi mode.
- **L8** `ExampleUnitTest` name undersells that it holds ~17 real tests.
- **L9** Version catalog declares ~15 unused entries (camera-*, datastore, navigation, coil, accompanist, play-services-location).
- **L10** `dependenciesInfo` inconsistent: excluded from APK, included in bundle.
- **L11** `googleServices.missing.passthrough=true` + `MissingGoogleServicesStrategy.WARN` hides the fact that Firebase can never initialize (no `google-services.json`), while Firebase deps are compiled in.

---

## 8. Deep Dive: Gemini Integration Correctness

**Cascade behavior today (default `AUTO_CASCADE`, easy query):**

| Attempt | Model | Status (verified against catalog [1](https://www.gradually.ai/en/gemini-models/)) |
|---|---|---|
| 1 | `gemini-3.5-flash-lite` | ⚠️ not a listed model → expected 404 |
| 2 | `gemini-flash-lite-latest` | ✅ alias exists |
| 3 | `gemini-3.1-flash-lite-preview` | ⚠️ not listed → 404 |
| 4 | `gemini-3.7-flash` | ❌ does not exist → 404 |
| 5 | `gemini-3.8-flash` | ❌ does not exist → 404 |

So the *happy path* costs an extra 404 round trip, and the flagship path (`CASCADE_FLASH`) burns **three** wasted calls before reaching `gemini-3.5-flash`/`gemini-flash-latest`. Combined with the key-in-URL issue (C2) and the unused official SDK (`firebase-ai` with App Check, already in the version catalog), the right refactor is:

1. Runtime model discovery via `GET /v1beta/models` cached for N hours.
2. Aliases only (`gemini-flash-latest`, `gemini-flash-lite-latest`, plus 1–2 pinnedGA models).
3. Honor `Retry-After` on 429; exponential backoff.
4. Move auth to header; optionally route through Firebase AI Logic with App Check (deps already present).

**What works well** (credit where due): `ConversationMemory` enforces strict user/model alternation for the REST contents array, dedupes trailing user turns, enriches elliptical follow-ups ("tell me more" → resolved subject), injects IST + device time into the system instruction, and the echo guard prevents TTS→mic feedback loops. The offline-knowledge-before-cloud ordering is a good latency hedge.

---

## 9. Deep Dive: Privacy & Data Flow

Data leaving the device:
1. **Voice queries + reply text** → Gemini REST (with full 16-turn history attached to *every* request).
2. **Battery %, charging state, ambient lux** → embedded in auto-reply SMS templates sent to *anyone who texts or calls-and-misses* (message is useful, but the default-on behavior is the issue — see C4).
3. **"Location context"** → `ACCESS_FINE_LOCATION` is declared/requested but **no code path actually obtains a GPS fix** (play-services-location dep is commented out); the "location" in prompts is purely from text extraction (`ConversationMemory.extractLocationFromQuery`). Requesting FINE location is therefore unjustified → remove it (drops one restricted permission).

Data at rest (see C3): Room `jarvis_core_db` (6 tables incl. SMS bodies), 5 SharedPreferences files, all cloud-backup-eligible today.

---

## 10. What's Done Well ✅

- **Offline-first design**: math, unit conversions, ~100 curated facts, device diagnostics, and full device-control intents work with zero connectivity — rare and valuable.
- **SensorConflictEngine**: edge-triggering + hysteresis + pocket-guard + no-op suppression is genuinely thoughtful engineering that most hobby automation apps lack.
- **Acoustic feedback-loop protection** (EchoGuard, TTS cooldown, wake-word gatekeeper with temporary unlock windows).
- **Marathi TTS strategy**: native mr-IN → hi-IN Devanagari fallback → phonetic transliteration → en-IN, with a one-tap voice-pack download intent. Well-conceived for the target audience.
- **Simulation hooks** (`simulateIncomingCall/Sms`) enable telephony testing without SIMs.
- **Permission status dashboard** with per-permission rationale strings.
- 17 real unit tests covering math engine, enums, and config (they need updating, but the habit exists).

---

## 11. Prioritized Remediation Roadmap

### P0 — do before anything else (≈ 1–2 days)
- [ ] **C1**: `exported=false` on `ScheduledTaskReceiver` (+ receiver-side action validation)
- [ ] **C2**: remove `?key=` (header auth); ship no default API key; enable R8; stop committing APKs (**H1**)
- [ ] **C4**: default auto-SMS/auto-reply toggles **OFF**; remove `"9876543210"` fallback; confirm-before-SOS
- [ ] **H2**: regenerate + commit Gradle wrapper; drop custom debug keystore config
- [ ] **C3**: exclude `jarvis_*` prefs & DB from backup; `EncryptedSharedPreferences` for the custom key
- [ ] **H3**: replace phantom model IDs with verified aliases/runtime discovery; fix the test

### P1 — hardening (≈ 1 week)
- [ ] **H5** real Room migrations; **H6** WorkManager for scheduled tasks; **H7** contextual permission flow + redacted lockscreen UI
- [ ] Consolidate the two command pipelines (M2); fix no-op "clear conversation" (M3)
- [ ] Persist anti-spam cooldowns (M15); prune unused deps & enable minify (M8)
- [ ] Add unit tests: `OfflineIntentEngine` (table-driven), `ConversationMemory`, `SensorConflictEngine`, `ScheduledTaskManager.calculateNextTriggerMillis`

### P2 — quality & distribution (ongoing)
- [ ] Split `processCommand` into handler strategies; extract screen components (M1)
- [ ] Word-boundary/anchored intent matching (M4)
- [ ] Strings to resources, README/LICENSE/CI, battery-profile documentation (M12/M13/M6)
- [ ] Decide distribution channel: Play (must execute H4 permission strategy) vs direct APK (document install + SMS disclaimers)

---

## 12. Appendix A — Voice Command Surface (implemented today)

| Domain | Examples (EN) | Marathi support |
|---|---|---|
| Device | "wake lock on/off", "wake screen", "lock status", "mute", "volume to 60%" | ✅ |
| Torch | "flashlight on/off/toggle", "lumos"/"nox" | ✅ |
| Media | "play <song> on youtube/vlc/spotify/local", pause/resume/next/previous/stop | ✅ |
| Apps | "open <app>", "close youtube", "go home" | ✅ |
| Calls/SMS | "call <name/number>", "send sms to <x> saying <body>" | ✅ |
| Scheduling | "schedule text/gemini … hourly/daily/at 7:30", alarms & timers | ✅ |
| Info | time/date (IST), battery, light, RAM/storage, orientation | ✅ |
| Knowledge | math, %, trig, logs, conversions, quotes, capitals, first-aid, personalities | ✅ |
| Engine control | "cascade lite/flash", "switch to gemini 3.5", "enable auto fallback" | ✅ |

## 13. Appendix B — Verified Toolchain

| Component | Version | Note |
|---|---|---|
| Gradle | 9.3.1 (properties only — **wrapper binaries missing**) | H2 |
| AGP | 9.1.1 | built-in Kotlin |
| Kotlin | 2.2.10 + KSP2 | |
| Compose BOM | 2024.09.00 | stale (M19) |
| Room | 2.7.0 (DB v4) | destructive migration (H5) |
| compileSdk / target | 36 (minor 36.1) / 36 | |
| minSdk | 24 | SpeechRecognizer on 24–30 depends on Google app presence |
| Signing | env-driven release; **gitignored custom debug keystore** | H2 |

---

*Generated by a full manual review of every Kotlin source file, the manifest, Gradle configuration, and the committed artifacts. Model-catalog claims verified against the published Gemini model table ([1](https://www.gradually.ai/en/gemini-models/)).*
