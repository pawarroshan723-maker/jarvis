# Jarvis Auto — Full Audit & Fix Report

> Bugs · logic errors · accuracy of results · improvements · UI/UX · performance/optimization
> Prepared for the fix pass after the **Google AI Studio → GitHub sync**.

| | |
|---|---|
| **Repository** | `pawarroshan723-maker/jarvis` |
| **Commit audited** | `01c49c0` — *"chore: improve security and command processing"* (`main`) |
| **Audit date** | 2026-09-29 |
| **App** | "Jarvis Auto" — Kotlin 2.2 / Jetpack Compose / Room, `minSdk 24`, `targetSdk 36`, applicationId `com.aistudio.jarvisauto.kzrxn` |
| **Scope** | All 50 Kotlin files (46 main + 4 test = **22,318 lines**), `AndroidManifest.xml`, resources, Gradle build files |
| **Method** | Line-by-line manual review of all engine / service / data / system / hardware / voice code, `MainActivity` and the ViewModel · **selective review** of the ≈ 9 k lines of Compose screens (targeted reading + pattern scans for font sizes, semantics, insets, list keys, confirmations, state saving) · scratch-harness reproduction of pure-logic bugs (Appendix B) · verification of platform / Gemini API behaviour against official docs (Appendix A) |
| **Not done (important)** | UI findings (§4.15) are pattern-based and **not exhaustive** — the large Compose screens were not read line by line, so expect further UI issues. The project was **not compiled or run** (the review sandbox has no JDK / Android SDK / Maven access) and **no device testing** was possible. Items that depend on OEM/OS behaviour are marked 🧪 *Needs device test*. Nothing in the source tree was modified — this file is the only change. |

---

## How to use this document

1. Read **§1 Executive summary** and **§2 "Fix first" list** (15 minutes).
2. Work through **§3 Roadmap** top-to-bottom; every task is a checkbox and points to a finding ID.
3. Each finding in **§4** has: *where* (file:line at commit `01c49c0`), *what happens*, *impact*, a concrete *fix*, and (for the serious ones) *how to verify*.
4. **§5** lists cross-cutting architecture recommendations, **§6** a test plan (table-driven test cases taken from the reproductions), and the appendices hold the evidence.

**Severity**

| Icon | Level | Meaning |
|---|---|---|
| 🔴 | **Critical** | Crash, data loss, safety-critical failure, privacy/security exposure, or a headline feature that is broken for most users |
| 🟠 | **High** | Wrong/unsafe behaviour that users will hit in normal use, or a serious reliability/performance problem |
| 🟡 | **Medium** | Real defect or UX/perf problem with a narrower scope or an easy workaround |
| 🔵 | **Low** | Polish, maintainability, minor inaccuracy |

**Confidence tags** — *Confirmed (code)*: read directly in source · *Confirmed (repro)*: reproduced in the scratch harness · *Doc-verified*: platform/API behaviour verified in official docs · 🧪 *Needs device test*: strongly suggested by code + docs, but I could not run it.

**Effort** — **S** < ½ day · **M** ½–2 days · **L** > 2 days.

---

## 1. Executive summary

Jarvis Auto is an ambitious, feature-rich assistant (offline + Gemini voice assistant, sensor automations, SMS/call automation, scheduled tasks, Marathi/English support). The code is readable, uses a sensible stack (Room + Flow + ViewModel + Compose) and already contains several good defensive touches:

* Auto-SMS / missed-call replies default to **off**, with a per-number anti-spam cooldown (`AutoCallSmsManager`).
* The alarm receiver is **not exported** and correctly uses `goAsync()` + a timed wake lock.
* Sensitive DB/prefs are excluded from cloud backup; the API-key text field is masked; `.env` is git-ignored.
* Gemini model IDs (3.5 / 3.6 / 3.7 / 3.8 Flash, 3.5 / 3.1 Flash-Lite, 2.5 Flash) are **valid** as of today (verified against the live model list, Appendix A) and the quota circuit-breaker idea is sound.
* The SMS receiver is protected with `BROADCAST_SMS`; the FGS declares types and the `specialUse` subtype property.

However, the review found **128** distinct issues (4 critical, 37 high, 62 medium, 25 low). They cluster into six themes:

1. **Lifecycle bugs make the core feature die.** A trivial screen rotation permanently destroys the singleton speech manager (no TTS, no mic, no error) — **LIFE-01**. Toggling the service without mic permission crashes on Android 14+ — **LIFE-02**.
2. **The SMS/call automation cannot work as shipped.** `RECEIVE_SMS` and `READ_CALL_LOG` are declared but never requested at runtime — **TEL-01**. The SOS feature can report success while sending nothing, and claims "location transmitting" without sending a location — **TEL-02**.
3. **The intent engine hijacks ordinary questions** through unanchored `contains()` checks ("help", "what time…", "start…", "model…", "features…") and performs risky actions (call, SMS, close app, change model) with no confirmation — **INTENT-01…08**, **KNOW-03**.
4. **The rules engine has logic errors**: edge-trigger state is reset *after* guards so rules stop re-firing (the default Face-Down rule), "battery full" fires on discharge, and "load presets" duplicates rules so one shake toggles the torch twice (net zero) — **SENS-01, SENS-02, RULE-01**.
5. **Accuracy of results is undermined by overclaiming**: SMS marked *DELIVERED* without delivery reports, actions reported as successful when Android silently blocked them (background-activity-launch rules), static "DSP: ON" / "CONFLICT SAFE" badges, fabricated noise/"human-voice confidence" metrics — **TEL-05, HW-01, VOICE-03, UX-05**.
6. **Privacy & policy exposure**: the UI is forced to show over the lock screen with no way to turn it off, SMS bodies/phone numbers are written to logcat and read aloud by default, the API key travels in the URL and lives in plaintext prefs, and the manifest requests a dozen permissions the code never uses (several are Play-restricted) — **SEC-01, SEC-02, AI-04, MAN-01, MAN-02**.

Performance: main-thread I/O at start-up (installed-app scan + duplicate contact queries) and inside voice commands (YouTube scraping with a 2.2 s blocking wait), root-level `collectAsState` of ~50 flows, always-on 60 fps animations, per-sensor-event DB reads — **PERF-01…05**.

Test coverage is thin exactly where the bugs are: **no behavioural tests** for the intent engine, conflict engine, scheduled tasks, telephony flows, DB or ViewModel (only constants/defaults are asserted) — **BLD-03**.

### Scoreboard

| Area | 🔴 Critical | 🟠 High | 🟡 Medium | 🔵 Low | Total |
|---|---:|---:|---:|---:|---:|
| Lifecycle, crashes & reliability (`LIFE`) | 2 | 3 | 5 | 1 | **11** |
| Voice pipeline (STT/TTS/VAD) (`VOICE`) | · | 1 | 5 | 4 | **10** |
| Intent engine & routing (`INTENT`) | · | 6 | 8 | 1 | **15** |
| Offline knowledge engine (`KNOW`) | · | 2 | 2 | 1 | **5** |
| Gemini / AI integration (`AI`) | · | 5 | 4 | 1 | **10** |
| Conversation memory (`MEM`) | · | 2 | 1 | · | **3** |
| Sensors (`SENS`) | · | 2 | 7 | 3 | **12** |
| Automation rules (`RULE`) | · | 1 | · | · | **1** |
| Telephony, SMS, calls & SOS (`TEL`) | 2 | 3 | 6 | 1 | **12** |
| Scheduled tasks (`SCH`) | · | 3 | 3 | 2 | **8** |
| Data layer (Room) (`DB`) | · | 1 | 2 | 1 | **4** |
| Hardware layer (`HW`) | · | 1 | 4 | 1 | **6** |
| Security & privacy (`SEC`) | · | 2 | 2 | 1 | **5** |
| Manifest, permissions & policy (`MAN`) | · | 2 | · | 1 | **3** |
| Build, deps, tests & docs (`BLD`) | · | · | 3 | 2 | **5** |
| UI / UX (`UX`) | · | 2 | 7 | 3 | **12** |
| Performance & optimisation (`PERF`) | · | 1 | 3 | 2 | **6** |
| **Total** | **4** | **37** | **62** | **25** | **128** |

---

## 2. "Fix first" list (highest value per unit of effort)

| # | ID | One-line reason | Effort |
|---|---|---|---|
| 1 | **LIFE-01** | Rotation/Back permanently kills TTS + mic | S–M |
| 2 | **LIFE-02** | Service toggle crashes without mic permission (Android 14+) | S |
| 3 | **TEL-01** | SMS/call automation silently dead — permissions never requested | S |
| 4 | **TEL-02** | SOS: false success, no location, no confirmation | M |
| 5 | **SEC-01** | UI forced over lock screen; no setting to disable | S |
| 6 | **SEC-02** | SMS bodies + phone numbers in release logcat | S |
| 7 | **SENS-01** | Edge-trigger ordering bug: default rules stop re-firing | S |
| 8 | **SENS-02** | "Battery full" fires when *discharging*; "low" fires while charging | S |
| 9 | **RULE-01** | "Load presets" duplicates rules → double TTS / torch double-toggle | S |
| 10 | **AI-01** | A valid Gemini answer containing "quota exceeded" trips a 15-min lockout | S |
| 11 | **AI-02** | Gemini 3 default thinking = HIGH + temperature 0.7 → slow/degraded voice replies | S |
| 12 | **SCH-01** | Scheduled SMS can execute twice (cold-start race, manual "run now") | M |
| 13 | **DB-01** | Any schema bump wipes user data *and* skips re-seeding | M |
| 14 | **INTENT-01/02/03** | "help", "start…", "model…" swallow normal questions and change settings | M |
| 15 | **PERF-01** | Main-thread I/O at start-up and during commands | S–M |

---

## 3. Roadmap (checklist)

### 3.0 Quick wins — each ≤ 1 hour, high payoff

- [ ] `MainActivity.onDestroy`: only call `onAppDestroyed()` when `isFinishing && !isChangingConfigurations` (**LIFE-01**, partial)
- [ ] Check `RECORD_AUDIO` before `startForegroundService`, wrap in try/catch (**LIFE-02**)
- [ ] Default `key_show_above_lockscreen` to `false` and add a Settings toggle (**SEC-01**)
- [ ] Delete/guard every `Log.*` that prints phone numbers, SMS bodies, contact names (**SEC-02**)
- [ ] `GeminiAssistantEngine`: evaluate `isSuccessful` *before* the quota-string check (**AI-01**)
- [ ] Remove `?key=$apiKey` from both endpoints — keep only `x-goog-api-key` (**AI-04**)
- [ ] Send `thinkingLevel: "low"` by default and drop `temperature = 0.7` for Gemini 3 (**AI-02**)
- [ ] `BATTERY_FULL` requires `isCharging`; `BATTERY_LOW` requires `!isCharging` (**SENS-02**)
- [ ] Add `RECEIVE_SMS` + `READ_CALL_LOG` to the runtime permission request/status list (**TEL-01**)
- [ ] Include `HAND_WAVE` in the "Gestures" filter; make the chip row scrollable (**UX-07**)
- [ ] Run `getInstalledApps()` / `loadContacts()` on `Dispatchers.IO` (**PERF-01**)
- [ ] Remove the unused permissions (location, Bluetooth, calendar, `WRITE_CONTACTS`, `WRITE_SETTINGS`, `READ_SMS`, `CAMERA`) (**MAN-01**)
- [ ] `.gitignore`: add `*.jks`, `*.keystore`, `my-upload-key.jks` (**SEC-04**)
- [ ] `enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))` + `Modifier.imePadding()` at the root (**UX-04**)
- [ ] Replace the static "DSP: ON" / "✓ CONFLICT SAFE" badges with real state or remove them (**UX-05**)

### 3.1 Phase 0 — stop crashes, data loss, privacy leaks, safety failures  *(≈ 3–5 days)*

- [ ] **LIFE-01** reversible speech-manager lifecycle · **LIFE-02** FGS start guard · **LIFE-05** coroutine exception policy
- [ ] **TEL-01** request the right SMS/call permissions · **TEL-02** SOS redesign · **TEL-05** truthful SMS status
- [ ] **SEC-01** lock-screen exposure · **SEC-02** PII in logs · **AI-04** key handling
- [ ] **SENS-01** edge-trigger ordering · **SENS-02** battery rule semantics · **RULE-01** preset de-duplication
- [ ] **AI-01** quota misclassification · **AI-02** thinking level/temperature · **AI-03** deadline + error taxonomy · **AI-09** never SMS an AI failure text
- [ ] **SCH-01** idempotent task execution · **DB-01** migrations instead of destructive fallback
- [ ] **MAN-01** least-privilege manifest · **SEC-04** keystore hygiene

### 3.2 Phase 1 — accuracy & trust  *(≈ 1–2 weeks)*

- [ ] **LIFE-03** one `CommandProcessor` for UI + background · **LIFE-04** ref-counted sensor ownership
- [ ] **INTENT-01…15**, **KNOW-01…05**, **MEM-01…03** — anchored intent router, confirmations for call/SMS/close-app, truthful results
- [ ] **VOICE-01** wake-word · **VOICE-02** echo guard · **VOICE-03** remove/replace fake DSP metrics
- [ ] **TEL-03…12** auto-reply policy, OTP/privacy, multipart, PHONE_STATE, simulate buttons, one-tap call/SMS
- [ ] **SCH-02…08** scheduling reliability · **HW-01…06** background-launch, audio, DND · **UX-06** confirmations

### 3.3 Phase 2 — UX, accessibility, performance  *(≈ 1–2 weeks)*

- [ ] **UX-01…04, 07…12** state restoration, typography/contrast/touch targets, semantics, insets, onboarding
- [ ] **PERF-01…06** main-thread I/O, recomposition scope, sensor rates, animations
- [ ] **SENS-03…12**, **VOICE-04…10**, **LIFE-06…11**

### 3.4 Phase 3 — hardening & maintainability  *(ongoing)*

- [ ] **AI-05…08, AI-10**, **DB-02…04**, **MAN-02…03**, **BLD-01…05**, **SEC-03, SEC-05**
- [ ] Architecture recommendations in §5 · test plan in §6

---

## 4. Detailed findings

### Index of all findings

| ID | Severity | Title |
|---|---|---|
| [LIFE-01](#life-01--speech-manager-is-permanently-destroyed-on-rotation--back--app-goes-mute-and-deaf) | 🔴 Critical | Speech manager is permanently destroyed on rotation / Back → app goes mute *and* deaf |
| [LIFE-02](#life-02--mic-foreground-service-can-crash-the-app-on-start-android-14) | 🔴 Critical | Mic foreground service can crash the app on start (Android 14+) |
| [LIFE-03](#life-03--two-divergent-command-pipelines-ui-vs-background-give-different-answers) | 🟠 High | Two divergent command pipelines (UI vs. background) give different answers |
| [LIFE-04](#life-04--sensormic-ownership-mismatch-between-ui-and-service) | 🟠 High | Sensor/mic ownership mismatch between UI and service |
| [LIFE-05](#life-05--uncaught-coroutine-exceptions-crash-the-process-cancellationexception-is-swallowed) | 🟠 High | Uncaught coroutine exceptions crash the process; `CancellationException` is swallowed |
| [LIFE-06](#life-06--speech-state-machine-has-holes-stuck-states-dead-callbacks-lying-hud) | 🟡 Medium | Speech state machine has holes (stuck states, dead callbacks, lying HUD) |
| [LIFE-07](#life-07--the-same-utterance-can-be-dispatched-twice) | 🟡 Medium | The same utterance can be dispatched twice |
| [LIFE-08](#life-08--wake-lock-state-is-wrong-latched-on-untoggleable-never-released) | 🟡 Medium | Wake-lock state is wrong (latched "ON", untoggleable, never released) |
| [LIFE-09](#life-09--permission-prompts-fire-on-every-activity-creation-without-rationale) | 🟡 Medium | Permission prompts fire on every Activity creation, without rationale |
| [LIFE-10](#life-10--commands-are-not-serialised-busy-flags-are-booleans) | 🟡 Medium | Commands are not serialised; busy flags are booleans |
| [LIFE-11](#life-11--back-on-the-hud-kills-the-task-back-is-always-intercepted) | 🔵 Low | Back on the HUD kills the task; Back is always intercepted |
| [VOICE-01](#voice-01--wake-word-extraction-corrupts-commands-and-the-strict-lock-isnt-strict) | 🟠 High | Wake-word extraction corrupts commands and the "strict" lock isn't strict |
| [VOICE-02](#voice-02--echo-guard-only-catches-an-exact-complete-echo) | 🟡 Medium | Echo guard only catches an exact, complete echo |
| [VOICE-03](#voice-03--human-voice-detection-and-dsp--noise-cancellation-are-not-implemented-the-ui-reports-fabricated-metrics) | 🟡 Medium | "Human voice detection" and "DSP / noise cancellation" are not implemented; the UI reports fabricated metrics |
| [VOICE-04](#voice-04--tts-pipeline-per-utterance-reconfiguration-on-the-main-thread-no-chunking-single-pending-slot) | 🟡 Medium | TTS pipeline: per-utterance reconfiguration on the main thread, no chunking, single pending slot |
| [VOICE-05](#voice-05--internal-player-ducking-sticks-local-player-has-no-audio-focus) | 🟡 Medium | Internal-player ducking sticks; local player has no audio focus |
| [VOICE-06](#voice-06--auto-language-decodes-english-with-the-marathi-model-first) | 🔵 Low | "Auto" language decodes English with the Marathi model first |
| [VOICE-07](#voice-07--emergency-media-words-bypass-the-wake-word-lock) | 🔵 Low | "Emergency" media words bypass the wake-word lock |
| [VOICE-08](#voice-08--magic-numbers-and-documentation-drift) | 🔵 Low | Magic numbers and documentation drift |
| [VOICE-09](#voice-09--transliteration--speech-normalisation-rough-edges) | 🔵 Low | Transliteration / speech-normalisation rough edges |
| [VOICE-10](#voice-10--continuous-recogniser-restart-loop-battery-earcons-throttling) | 🟡 Medium | Continuous recogniser restart loop: battery, earcons, throttling |
| [INTENT-01](#intent-01--over-broad-contains-intents-swallow-normal-questions) | 🟠 High | Over-broad `contains()` intents swallow normal questions |
| [INTENT-02](#intent-02--unanchored-open--start--launch-regex-hijacks-questions-failure-path-never-falls-back) | 🟠 High | Unanchored "open / start / launch" regex hijacks questions; failure path never falls back |
| [INTENT-03](#intent-03--casual-questions-silently-change-the-gemini-model-setting) | 🟠 High | Casual questions silently change the Gemini model setting |
| [INTENT-04](#intent-04--calls--sms-wrong-recipient-and-unintended-send-risks-no-confirmation) | 🟠 High | Calls & SMS: wrong-recipient and unintended-send risks, no confirmation |
| [INTENT-05](#intent-05--alarm--timer-parsing-produces-wrong-times) | 🟠 High | Alarm & timer parsing produces wrong times |
| [INTENT-06](#intent-06--voice-scheduling-substring-flags-past-time-wrap-unsupported-recipients) | 🟠 High | Voice scheduling: substring flags, past-time wrap, unsupported recipients |
| [INTENT-07](#intent-07--music-query-extraction-corrupts-titles-play-anything-is-music) | 🟡 Medium | Music query extraction corrupts titles; "play anything" is music |
| [INTENT-08](#intent-08--close--hijacks-sentences-mangles-app-names-and-kills-by-substring) | 🟡 Medium | "Close …" hijacks sentences, mangles app names and kills by substring |
| [INTENT-09](#intent-09--app-launch-mapping-open-phonepe-opens-the-dialer) | 🟡 Medium | App-launch mapping: "open PhonePe" opens the Dialer |
| [INTENT-10](#intent-10--time-in-india-uses-the-device-time-zone-date-questions-answered-with-todays-date) | 🟡 Medium | "Time in India" uses the device time zone; date questions answered with today's date |
| [INTENT-11](#intent-11--voice-rule-builder-silent-defaults-and-lost-phrases) | 🟡 Medium | Voice rule builder: silent defaults and lost phrases |
| [INTENT-12](#intent-12--volume--mute-commands-never-restore-state-and-use-inconsistent-steps) | 🟡 Medium | Volume / mute commands never restore state and use inconsistent steps |
| [INTENT-13](#intent-13--intentresultsuccess-conflates-not-understood-with-understood-but-failed) | 🟡 Medium | `IntentResult.success` conflates "not understood" with "understood but failed" |
| [INTENT-14](#intent-14--structure-a-1350-line-function-per-call-allocations-inline-strings) | 🔵 Low | Structure: a 1,350-line function, per-call allocations, inline strings |
| [INTENT-15](#intent-15--calendar-questions-create-events-the-reply-says-created-though-only-a-ui-was-opened) | 🟡 Medium | "Calendar" questions create events; the reply says "Created" though only a UI was opened |
| [KNOW-01](#know-01--partial-match-math-answers-a-fragment-of-the-question) | 🟠 High | Partial-match math answers a fragment of the question |
| [KNOW-02](#know-02--answers-in-the-wrong-language-raw-double-output) | 🟡 Medium | Answers in the wrong language, raw `Double` output |
| [KNOW-03](#know-03--greeting--quote--fact--settings-triggers-swallow-real-questions-and-cause-side-effects) | 🟠 High | Greeting / quote / fact / settings triggers swallow real questions and cause side-effects |
| [KNOW-04](#know-04--offline-answers-bypass-conversation-memory-labels-wrong-chain-runs-twice) | 🟡 Medium | Offline answers bypass conversation memory; labels wrong; chain runs twice |
| [KNOW-05](#know-05--content-claims-and-data-quality) | 🔵 Low | Content claims and data quality |
| [AI-01](#ai-01--a-valid-answer-containing-quota-exceeded-trips-a-15-minute-model-lock-out) | 🟠 High | A valid answer containing "quota exceeded" trips a 15-minute model lock-out |
| [AI-02](#ai-02--gemini-3-defaults-to-high-thinking-temperature-07-is-discouraged--slow-degraded-voice-replies) | 🟠 High | Gemini 3 defaults to HIGH thinking; temperature 0.7 is discouraged → slow, degraded voice replies |
| [AI-03](#ai-03--unbounded-latency-no-offline-fast-fail-misleading-failure-message) | 🟠 High | Unbounded latency, no offline fast-fail, misleading failure message |
| [AI-04](#ai-04--api-key-handling-in-the-url-plaintext-at-rest-embedded-in-the-apk) | 🟠 High | API key handling: in the URL, plaintext at rest, embedded in the APK |
| [AI-05](#ai-05--hard-coded-model-catalog-will-rot-one-model-already-has-a-shutdown-date) | 🟡 Medium | Hard-coded model catalog will rot (one model already has a shutdown date) |
| [AI-06](#ai-06--searchmaps-tool-heuristics-are-substring-based-and-expensive) | 🟡 Medium | Search/Maps tool heuristics are substring-based and expensive |
| [AI-07](#ai-07--emptyblocked-responses-are-recorded-as-success-no-output-cap) | 🟡 Medium | Empty/blocked responses are recorded as success; no output cap |
| [AI-08](#ai-08--cloud-ai-toggle-is-not-persisted-and-not-enforced) | 🟡 Medium | "Cloud AI" toggle is not persisted and not enforced |
| [AI-09](#ai-09--scheduled-gemini-tasks-can-sms-a-raw-prompt-or-an-error-string-to-a-third-party) | 🟠 High | Scheduled Gemini tasks can SMS a raw prompt or an error string to a third party |
| [AI-10](#ai-10--promptcachinghealth-check-details) | 🔵 Low | Prompt/caching/health-check details |
| [MEM-01](#mem-01--any-inforat-word-becomes-a-location-and-forces-the-weather-topic-persisted) | 🟠 High | Any "in/for/at <word>" becomes a location and forces the Weather topic (persisted) |
| [MEM-02](#mem-02--whether--weather-rewrite-corrupts-general-questions) | 🟠 High | "whether" → "weather" rewrite corrupts general questions |
| [MEM-03](#mem-03--plaintext-persistence-and-unsynchronised-state) | 🟡 Medium | Plaintext persistence and unsynchronised state |
| [SENS-01](#sens-01--edge-trigger-state-is-reset-after-the-early-return-guards--rules-stop-re-firing) | 🟠 High | Edge-trigger state is reset *after* the early-return guards → rules stop re-firing |
| [SENS-02](#sens-02--battery-rules-ignore-charging-state-spurious-start-up-events-fabricated-85-) | 🟠 High | Battery rules ignore charging state; spurious start-up events; fabricated 85 % |
| [SENS-03](#sens-03--pocket-guard-compares-raw-centimetres-to-30--shake-can-be-permanently-disabled) | 🟡 Medium | Pocket guard compares raw centimetres to 3.0 → shake can be permanently disabled |
| [SENS-04](#sens-04--shake-threshold-hard-coded-detector-silent-per-start-rewrite-of-user-rules) | 🟡 Medium | Shake threshold: hard-coded detector, silent per-start rewrite of user rules |
| [SENS-05](#sens-05--per-event-db-query-event-spam-always-on-sensors) | 🟡 Medium | Per-event DB query, event spam, always-on sensors |
| [SENS-06](#sens-06--screen-off-reliability-non-wake-up-sensors-no-wake-lock) | 🟡 Medium | Screen-off reliability: non-wake-up sensors, no wake lock |
| [SENS-07](#sens-07--rule-actions-run-sequentially-on-the-collector-coroutine) | 🟡 Medium | Rule actions run sequentially on the collector coroutine |
| [SENS-08](#sens-08--rule-results-are-reported-untruthfully-sos-is-a-steady-torch) | 🟡 Medium | Rule results are reported untruthfully; "SOS" is a steady torch |
| [SENS-09](#sens-09--hud-mirrors-of-torchvolume-go-stale) | 🟡 Medium | HUD mirrors of torch/volume go stale |
| [SENS-10](#sens-10--testtriggerrule-re-implements-executerule) | 🔵 Low | `testTriggerRule` re-implements `executeRule` |
| [SENS-11](#sens-11--orientation-classification-is-portrait-only-and-un-debounced) | 🔵 Low | Orientation classification is portrait-only and un-debounced |
| [SENS-12](#sens-12--hand-wave-heuristics-are-prone-to-false-triggers-the-preset-is-enabled) | 🔵 Low | Hand-wave heuristics are prone to false triggers; the preset is enabled |
| [RULE-01](#rule-01--load-presets-duplicates-rules-ui-voice-and-sms--one-shake-toggles-the-torch-twice) | 🟠 High | "Load presets" duplicates rules (UI, voice and SMS) — one shake toggles the torch twice |
| [TEL-01](#tel-01--receive_sms-and-read_call_log-are-never-requested--incoming-sms-and-caller-features-are-dead) | 🔴 Critical | `RECEIVE_SMS` and `READ_CALL_LOG` are never requested → incoming-SMS and caller features are dead |
| [TEL-02](#tel-02--emergency-sos-false-success-promises-a-location-it-never-sends-no-confirmation) | 🔴 Critical | Emergency SOS: false success, promises a location it never sends, no confirmation |
| [TEL-03](#tel-03--auto-reply-answers-anyone-short-codes-banks-otp-with-generic-keywords-and-leaks-device-state) | 🟠 High | Auto-reply answers *anyone* (short codes, banks, OTP) with generic keywords and leaks device state |
| [TEL-04](#tel-04--sms-bodies-incl-otps-are-read-aloud-by-default-even-when-locked) | 🟠 High | SMS bodies (incl. OTPs) are read aloud by default, even when locked |
| [TEL-05](#tel-05--status-delivered-without-any-delivery-report-composer-fallback-counted-as-success) | 🟠 High | Status "DELIVERED" without any delivery report; composer fallback counted as success |
| [TEL-06](#tel-06--multipart-sms-is-processed-as-separate-messages) | 🟡 Medium | Multipart SMS is processed as separate messages |
| [TEL-07](#tel-07--phone_state-handling-duplicates-static-state-deprecated-api) | 🟡 Medium | `PHONE_STATE` handling: duplicates, static state, deprecated API |
| [TEL-08](#tel-08--receivers-do-heavy-work-without-goasync) | 🟡 Medium | Receivers do heavy work without `goAsync()` |
| [TEL-09](#tel-09--inserts-into-contentsmssent-always-fail) | 🔵 Low | Inserts into `content://sms/sent` always fail |
| [TEL-10](#tel-10--simulate-call--sms-buttons-send-real-sms) | 🟡 Medium | "Simulate Call / SMS" buttons send real SMS |
| [TEL-11](#tel-11--one-tap-call--sms-in-the-contacts-directory-sends-hello-from-jarvis-immediately) | 🟡 Medium | One-tap Call / SMS in the contacts directory sends "Hello from Jarvis" immediately |
| [TEL-12](#tel-12--contact-search-main-thread-queries-per-keystroke-alphabetical-first-match) | 🟡 Medium | Contact search: main-thread queries, per-keystroke, alphabetical "first match" |
| [SCH-01](#sch-01--a-scheduled-sms-can-run-twice-cold-start-race-manual-run-now) | 🟠 High | A scheduled SMS can run twice (cold-start race, manual "run now") |
| [SCH-02](#sch-02--gemini-tasks-run-inside-a-broadcast-window--anr-risk) | 🟠 High | Gemini tasks run inside a broadcast window → ANR risk |
| [SCH-03](#sch-03--overdue-and-failed-tasks-are-mishandled-timezone-changes-ignored) | 🟠 High | Overdue and failed tasks are mishandled; time/zone changes ignored |
| [SCH-04](#sch-04--exact-alarm-handling-is-silent-play-policy-doze) | 🟡 Medium | Exact-alarm handling is silent; Play policy; Doze |
| [SCH-05](#sch-05--recurrence-semantics-and-no-spam-guard-new-sms-tasks-default-to-hourly-forever) | 🟡 Medium | Recurrence semantics and no spam guard; new SMS tasks default to "hourly forever" |
| [SCH-06](#sch-06--tasks-with-an-unresolved-recipient-are-created-and-armed) | 🟡 Medium | Tasks with an unresolved recipient are created and armed |
| [SCH-07](#sch-07--default-time-can-be-in-the-past-ist-hard-coded-for-every-user) | 🔵 Low | Default time can be in the past; IST hard-coded for every user |
| [SCH-08](#sch-08--stale-alarms-after-delete-all-noisy-lock-screen-notifications) | 🔵 Low | Stale alarms after "Delete all"; noisy lock-screen notifications |
| [DB-01](#db-01--destructive-migration-wipes-user-data-and-by-design-skips-re-seeding) | 🟠 High | Destructive migration wipes user data and (by design) skips re-seeding |
| [DB-02](#db-02--unbounded-tables-and-missing-indices) | 🟡 Medium | Unbounded tables and missing indices |
| [DB-03](#db-03--seeding-is-asynchronous-and-non-transactional-defaults-ship-enabled) | 🟡 Medium | Seeding is asynchronous and non-transactional; defaults ship *enabled* |
| [DB-04](#db-04--minor-data-layer-issues) | 🔵 Low | Minor data-layer issues |
| [HW-01](#hw-01--hands-free-launches-are-silently-blocked-by-background-activity-launch-rules--yet-reported-as-success) | 🟠 High | Hands-free launches are silently blocked by Background Activity Launch rules — yet reported as success |
| [HW-02](#hw-02--youtube-scraping-with-a-spoofed-browser-user-agent) | 🟡 Medium | YouTube scraping with a spoofed browser User-Agent |
| [HW-03](#hw-03--play-local-song-can-never-find-files-jarvisaudioplayer-problems) | 🟡 Medium | "Play local song" can never find files; `JarvisAudioPlayer` problems |
| [HW-04](#hw-04--mute--ringer--dnd-never-checked-partial-application-side-effects-rounding) | 🟡 Medium | Mute / ringer / DND: never checked, partial application, side-effects, rounding |
| [HW-05](#hw-05--audio-focus-leak-on-stop-media-key-results-are-always-success) | 🟡 Medium | Audio-focus leak on STOP; media-key results are always "success" |
| [HW-06](#hw-06--fabricated-battery-value-85-) | 🔵 Low | Fabricated battery value (85 %) |
| [SEC-01](#sec-01--ui-is-forced-over-the-lock-screen-and-wakes-the-screen-with-no-way-to-turn-it-off) | 🟠 High | UI is forced over the lock screen (and wakes the screen) with no way to turn it off |
| [SEC-02](#sec-02--phone-numbers-and-sms-bodies-are-written-to-logcat-in-release-builds) | 🟠 High | Phone numbers and SMS bodies are written to logcat in release builds |
| [SEC-03](#sec-03--backup-rules-are-inconsistent-allowbackuptrue) | 🟡 Medium | Backup rules are inconsistent; `allowBackup="true"` |
| [SEC-04](#sec-04--signingkeystore-hygiene-a-fresh-clone-may-not-build-the-debug-variant) | 🟡 Medium | Signing/keystore hygiene; a fresh clone may not build the debug variant |
| [SEC-05](#sec-05--lock-screen-notification-content) | 🔵 Low | Lock-screen notification content |
| [MAN-01](#man-01--a-dozen-declared-permissions-are-unused-ui-descriptions-are-false) | 🟠 High | A dozen declared permissions are unused; UI descriptions are false |
| [MAN-02](#man-02--store-policy-blockers-smscall-log-groups-exact-alarms-fgs-special-use) | 🟠 High | Store-policy blockers (SMS/Call-log groups, exact alarms, FGS special-use) |
| [MAN-03](#man-03--manifesttheme-polish) | 🔵 Low | Manifest/theme polish |
| [BLD-01](#bld-01--release-build-is-unshrunk-and-carries-unuseddebug-dependencies-and-a-1-mb-icon) | 🟡 Medium | Release build is unshrunk and carries unused/debug dependencies and a 1 MB icon |
| [BLD-02](#bld-02--outdated-dependencies-and-an-over-full-version-catalog) | 🟡 Medium | Outdated dependencies and an over-full version catalog |
| [BLD-03](#bld-03--tests-dont-cover-the-code-where-the-bugs-are) | 🟡 Medium | Tests don't cover the code where the bugs are |
| [BLD-04](#bld-04--no-readme-ci-or-static-analysis) | 🔵 Low | No README, CI or static analysis |
| [BLD-05](#bld-05--no-localization-template-names) | 🔵 Low | No localization; template names |
| [UX-01](#ux-01--all-ui-state-is-lost-on-rotation-no-remembersaveable-anywhere) | 🟠 High | All UI state is lost on rotation (no `rememberSaveable` anywhere) |
| [UX-02](#ux-02--tiny-text-low-contrast-and-sub-48-dp-touch-targets) | 🟠 High | Tiny text, low contrast and sub-48 dp touch targets |
| [UX-03](#ux-03--accessibility-semantics-are-missing) | 🟡 Medium | Accessibility semantics are missing |
| [UX-04](#ux-04--system-bars-and-keyboard-insets) | 🟡 Medium | System bars and keyboard insets |
| [UX-05](#ux-05--status-indicators-state-things-that-are-not-true) | 🟡 Medium | Status indicators state things that are not true |
| [UX-06](#ux-06--destructive--costly-actions-have-no-confirmation-or-undo) | 🟡 Medium | Destructive / costly actions have no confirmation or undo |
| [UX-07](#ux-07--rules-screen-defects) | 🟡 Medium | Rules screen defects |
| [UX-08](#ux-08--input-controls-and-validation) | 🟡 Medium | Input controls and validation |
| [UX-09](#ux-09--model-selection-and-hud-density) | 🔵 Low | Model selection and HUD density |
| [UX-10](#ux-10--logs-time-formats-status-messages-toasts) | 🔵 Low | Logs, time formats, status messages, toasts |
| [UX-11](#ux-11--volume-slider-fires-the-system-volume-panel-on-every-drag-frame) | 🔵 Low | Volume slider fires the system volume panel on every drag frame |
| [UX-12](#ux-12--no-onboarding-risky-defaults-are-already-on) | 🟡 Medium | No onboarding; risky defaults are already on |
| [PERF-01](#perf-01--main-thread-io-at-start-up-and-inside-voice-commands) | 🟠 High | Main-thread I/O at start-up and inside voice commands |
| [PERF-02](#perf-02--recomposition-scope-and-lifecycle-unaware-collection) | 🟡 Medium | Recomposition scope and lifecycle-unaware collection |
| [PERF-03](#perf-03--sensor-recogniser-and-wake-lock-load-add-up) | 🟡 Medium | Sensor, recogniser and wake-lock load add up |
| [PERF-04](#perf-04--always-on-60-fps-animations) | 🟡 Medium | Always-on 60 fps animations |
| [PERF-05](#perf-05--allocation-and-disk-churn) | 🔵 Low | Allocation and disk churn |
| [PERF-06](#perf-06--lazy-lists-without-stable-keys) | 🔵 Low | Lazy lists without stable keys |

### 4.1 Lifecycle, crashes & reliability

### LIFE-01 — Speech manager is permanently destroyed on rotation / Back → app goes mute *and* deaf
**Severity:** 🔴 Critical · **Category:** Lifecycle · **Confidence:** Confirmed (code) · **Effort:** S–M

**Where**
* `MainActivity.kt:73-76` — `onDestroy()` → `viewModel.onAppDestroyed()` unconditionally.
* `MainActivity.kt:85-92` — Back on the HUD tab → `closeAppAndReleaseMic()` + `finishAndRemoveTask()`.
* `JarvisViewModel.kt:1168-1183` — both helpers call `speechManager.destroy()` when the foreground service is not running.
* `JarvisSpeechManager.kt:1068-1107` — `destroy()` sets `isDestroyed = true` (**it is never set back to `false` anywhere**), calls `textToSpeech.shutdown()`, sets `textToSpeech = null`, removes *all* handler callbacks and unregisters the screen receiver.
* `JarvisApplication.kt:67-76` — the manager is an Application-scoped `lazy` singleton, so a new Activity/ViewModel gets the *same dead instance*.

**What happens**
`onDestroy()` also runs on **configuration changes** (rotation, dark-mode switch, font-scale, locale, split-screen resize) — the activity declares no `configChanges` and the orientation is not locked. Unless the automation service happens to be running, the first rotation kills the singleton. Afterwards:

* `startListening()` still returns `true` but early-returns (`:702`) → nothing listens, no error shown;
* `scheduleContinuousRestart()` returns immediately (`:966`);
* `speak()` parks text in `pendingSpeech` forever, because `isTtsReady` stays `true` while `textToSpeech == null` (`:1002-1006`) → no audio, no error;
* the "Re-init Voice" button in Diagnostics only *speaks a test phrase* (`VoiceDiagnosticsDialog.kt:991` → `onTestTts()`), so it cannot recover either.

If the service *is* running, `onAppDestroyed()` calls `stopListening()`, which silently ends the always-on loop and releases the always-on wake lock on every rotation.

**Impact** The headline capability (voice in / voice out) breaks after a trivial user action and stays broken until the process dies.

**Fix**
```kotlin
// MainActivity.kt
override fun onDestroy() {
    super.onDestroy()
    if (isFinishing && !isChangingConfigurations) viewModel.onAppDestroyed()
}
```
The guard above fixes rotation; the Back / `finishAndRemoveTask()` path still reaches `destroy()` while the process usually survives, so the next points are needed too:
1. Never call `destroy()` from UI lifecycle code. Split it into `releaseAudio()` (stop mic, abandon focus — fully reversible) and `shutdown()` (terminal; process end/tests only).
2. Make the manager self-healing: `ensureReady()` recreates `SpeechRecognizer`/`TextToSpeech` when `null`, `shutdown()` resets `isTtsReady = false`, and `isDestroyed` becomes a reversible state.
3. Release the mic on real visibility changes (`ProcessLifecycleOwner` `onStop` for `SCREEN_ON_ONLY`/`MANUAL`), not on `Activity.onDestroy`.
4. Don't stop the always-on listener from `onAppDestroyed` when the service owns it.

**Verify** Instrumented test: `launch → scenario.recreate() → speechManager.speak("x")` must reach `SPEAKING`. Manual: rotate twice, tap the orb, speak.

### LIFE-02 — Mic foreground service can crash the app on start (Android 14+)
**Severity:** 🔴 Critical · **Category:** Lifecycle · **Confidence:** Doc-verified + Confirmed (code) · **Effort:** S

**Where** `JarvisViewModel.kt:382-389` (`toggleService`), `JarvisAutomationService.kt:52-61` (`start`), `:116-124` (`startForeground(..., SPECIAL_USE or MICROPHONE)`), manifest `foregroundServiceType="specialUse|microphone"`.

**What happens** On Android 14+ (targetSdk 34+), `startForeground()` with the `microphone` type requires the **runtime `RECORD_AUDIO` permission to be granted at that moment**, otherwise the system throws `SecurityException` (docs: *"…might cause your app to crash"*). The top-bar **ACTIVE/IDLE** toggle is always enabled and the launch-time permission dialog (`MainActivity.kt:294-303`) can be denied. Further, a microphone FGS **cannot be created while the app is in the background** (while-in-use restriction). The service returns `START_STICKY`, so a system restart after process death (no visible UI, `intent == null`) calls `startForeground` from the background (🧪 verify on API 34/35/36). On API 30–33 there is no crash but the mic silently records silence.

**Impact** Expected crash per the platform docs (not run here): install → deny microphone → tap **IDLE/ACTIVE**.

**Fix**
```kotlin
fun start(context: Context): Boolean {
    val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (!micOk) return false                       // UI must request the permission first
    return try {
        ContextCompat.startForegroundService(
            context, Intent(context, JarvisAutomationService::class.java).setAction(ACTION_START))
        true
    } catch (e: Exception) {                       // SecurityException / ForegroundServiceStartNotAllowedException
        Log.w(TAG, "FGS start refused", e); false
    }
}
// in onStartCommand — build the type mask from what is actually granted
val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
    (if (hasMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
```
Return `START_NOT_STICKY` (or, on a `null` intent, `stopSelf()` and post a "Tap to resume Jarvis" notification) so a background restart cannot crash. Make the toggle reflect a "microphone permission needed" state.

**Verify** Android 14/15: deny mic → toggle → no crash + visible message; `adb shell am kill` while running → no crash loop.

### LIFE-03 — Two divergent command pipelines (UI vs. background) give different answers
**Severity:** 🟠 High · **Category:** Architecture / Accuracy · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `JarvisApplication.handleVoiceCommand` (`:85-175`, used whenever `activeCommandDispatcher == null`, i.e. UI closed and the service keeps listening) vs. `JarvisViewModel.submitVoiceCommand` (`:519-628`).

**Differences found**
1. **Cloud toggle**: the ViewModel honours `_isOnlineIntelligenceEnabled` (`:566`); the Application path ignores it and always may call Gemini — so "Cloud AI: off" does not hold in the background.
2. **Failed local actions** (`success=false`: flashlight unavailable, "app not found", mute failed): the Application path forwards the command to Gemini (which then hallucinates a status); the ViewModel path speaks the local failure text.
3. **Threading**: the ViewModel path runs `processCommand` — which does blocking I/O — on `viewModelScope` (**Main**); the Application path on `Dispatchers.Default` (PERF-01).
4. **Memory/UI state**: only the ViewModel refreshes `activeLocationContext`/summary; neither records offline-engine answers into `ConversationMemory` (KNOW-04).
5. **History labels**: `OFFLINE_CORE` / `OFFLINE_KNOWLEDGE` / `GEMINI_ONLINE` are assigned inconsistently; canned failure text is stored as `GEMINI_ONLINE`.
6. The weather / real-time keyword lists are copy-pasted (`JarvisApplication.kt:122-146` vs `JarvisViewModel.kt:567-585`) and will drift.

**Fix** One `CommandProcessor` (use-case) that both callers delegate to:
```kotlin
class CommandProcessor(private val deps: Deps, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val gate = Mutex()                              // also fixes LIFE-10
    suspend fun handle(raw: String, origin: Origin): Outcome = gate.withLock {
        withContext(io) { /* normalise → route → execute → respond → history/memory */ }
    }
}
```
Read the cloud toggle from a persisted settings repository, return a typed `Outcome` (`Handled`, `Failed(reason)`, `NeedsConfirmation`, `FallbackToCloud`) instead of `success=false` overloads (INTENT-13).

### LIFE-04 — Sensor/mic ownership mismatch between UI and service
**Severity:** 🟠 High · **Category:** Lifecycle / Battery · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `JarvisViewModel.kt:199-201` (`init { sensorHub.startListening() }`, never stopped in `onCleared`), `JarvisAutomationService.kt:377-384` (`onDestroy` → `sensorHub.stopListening()` + `speechManager.stopListening()`), `SensorHub.kt:122-154` (not ref-counted; `stopListening()` unregisters everything).

**What happens** Toggling the service **off** while the Sensors tab is open freezes telemetry. Conversely, after the UI is closed with the service off, accelerometer (`SENSOR_DELAY_UI`), light, proximity and the battery receiver keep running in the cached process (battery drain).

**Fix** Ref-counted `acquire(owner)`/`release(owner)` in `SensorHub` (owners: `SERVICE`, `UI`), register only the sensors needed by enabled rules (+ UI when visible), and drive the UI owner with `repeatOnLifecycle(STARTED)`.

### LIFE-05 — Uncaught coroutine exceptions crash the process; `CancellationException` is swallowed
**Severity:** 🟠 High · **Category:** Reliability · **Confidence:** Confirmed (code) · **Effort:** S

**Where** `JarvisApplication.kt:19` (scope has no `CoroutineExceptionHandler`), `:177-183` (`onCreate` launch), `JarvisAutomationService.kt:36,149-163` (collector calls `repository.getActiveRulesSync()` and `shouldExecuteRule()` unguarded), `JarvisApplication.kt:166-173` and `JarvisViewModel.kt:622-626` (`catch (e: Exception)` also catches `CancellationException`).

**What happens** Any DB/IO error inside those `launch` blocks propagates to the thread's uncaught-exception handler → app crash. When the ViewModel is cleared mid-request, the swallowed `CancellationException` makes the app *speak* "Encountered an internal error…" after the user closed the screen; the ViewModel variant doesn't even log the exception.

**Fix**
```kotlin
private val ceh = CoroutineExceptionHandler { _, t -> Log.e(TAG, "Unhandled coroutine failure", t) }
val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + ceh)
...
try { … } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e(TAG, "cmd failed", e); … }
```
Same handler for `serviceScope`, and wrap the per-event work in `runCatching`.

### LIFE-06 — Speech state machine has holes (stuck states, dead callbacks, lying HUD)
**Severity:** 🟡 Medium · **Category:** Reliability · **Confidence:** Confirmed (code) · **Effort:** M

* `textToSpeech?.speak(...)` return value ignored (`JarvisSpeechManager.kt:1032`). If it returns `ERROR` (text > `getMaxSpeechInputLength()` = 4000, engine died) **no `onError` callback fires**, `isTtsSpeaking` stays `true` and the state stays `SPEAKING`; the recognizer never restarts until the user taps the orb. Add a return-code check + a TTS watchdog.
* `speak(text, onCompleted)` **never invokes `onCompleted`** (`:986-1040`; the wake-word greeting relies on it at `:913`).
* `onFallbackToSystemSpeech` is assigned by the ViewModel (`JarvisViewModel.kt:206`) but never invoked → the "fall back to Google speech dialog" collector in `MainActivity.kt:273-292` is dead.
* `SpeechState.ERROR` is never assigned anywhere → the HUD's red "SYSTEM NOTICE // RETRY" state is unreachable.
* `startListening()` always returns `true` (`:736`) and sets `LISTENING` even when the recognizer is `null` (`startRecognizerSession` returns silently, `:746-752`) → HUD shows "LISTENING" while nothing listens, and the `if (started) … else handleLaunchSystemDialog()` branch in the HUD tap handler (`MainActivity.kt:254-264`) is unreachable.
* `onError` ignores `ERROR_SERVER`, `ERROR_TOO_MANY_REQUESTS`, `ERROR_LANGUAGE_NOT_SUPPORTED/UNAVAILABLE`, `ERROR_SERVER_DISCONNECTED` (`:1186-1227`) and restarts on a fixed 300–500 ms with no exponential back-off.
* The processing watchdog fires after 6 s (`:816-826`) but a Gemini cascade can take far longer (AI-03) → the mic re-opens while the reply is pending.
* `onInit` failure (`:628-630`) is logged only — no retry, no user-visible message (devices without a TTS engine stay silent).

**Fix** Return real status from `startListening()`; set `ERROR` + message + fall back to the system dialog; back-off (300 ms → 5 s) after consecutive errors; TTS watchdog (`onStart` within 3 s or reset); surface "no TTS engine / voice data missing" with the existing `openTtsSettings()`.

### LIFE-07 — The same utterance can be dispatched twice
**Severity:** 🟡 Medium · **Category:** Reliability · **Confidence:** Confirmed (code) · 🧪 Needs device test · **Effort:** S

**Where** `JarvisSpeechManager.kt:1234-1281` (`onResults`) vs `:833-839,849-958` (silence timer → `commitAndSendSpeech`).

`onResults` guards `isTtsSpeaking`/cool-down/`SPEAKING` but **not `PROCESSING`**. After the silence timer commits (state → `PROCESSING`, `speechRecognizer.stopListening()` at `:940`), the recognizer is allowed to deliver the captured speech via `onResults`, which re-enters `commitAndSendSpeech()` and executes the command a second time (call/SMS/alarm twice). The 2 s silence timer equals `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` (2000), so the two paths race.

**Fix** Ignore `onResults`/`onPartialResults` while `PROCESSING`; dedupe identical text within ~3 s; call `cancel()` (not `stopListening()`) after committing.

### LIFE-08 — Wake-lock state is wrong (latched "ON", untoggleable, never released)
**Severity:** 🟡 Medium · **Category:** State / Battery · **Confidence:** Confirmed (code) · **Effort:** S

**Where** `JarvisWakeLockManager.kt:23-49,51-79,105-119`; `HardwareController.kt:433-442`; `JarvisViewModel.kt:786-794`.

* `acquireCpuWakeLock()` sets `_isWakeLockActive = true` (`:31`) but nothing resets it when the timed lock expires; `releaseCpuWakeLock()` and `releaseAll()` are **never called anywhere** (grep). After the first voice command the HUD/Rules pill shows **"WAKELOCK: ON"** permanently.
* The persistent-lock toggle reads that same shared flag (`togglePersistentWakeLock` → `currentlyActive = isWakeLockActive.value`), so once a temporary lock has been taken the user's tap tries to *disable* a persistent lock that does not exist.
* `persistentWakeLock.acquire()` has **no timeout** (`:62`) — an indefinite partial wake lock.
* The temporary lock object is created once with the first tag; later tags are ignored.
* The always-on mic lock is `acquire(2h)` (`JarvisSpeechManager.kt:315`) and is not re-acquired after expiry → after 2 h the screen-off mic silently loses CPU. `stopListening()` releases it unconditionally (`:807`) even in always-on modes.

**Fix** Separate `persistentActive` and `temporaryActive` flows (UI shows persistent only); reset via `onRelease`/timeout `Handler`; give the persistent lock a bounded timeout with a notification; add `releaseAll()` to service/app shutdown; re-acquire the mic lock on each session start.

### LIFE-09 — Permission prompts fire on every Activity creation, without rationale
**Severity:** 🟡 Medium · **Category:** UX / Lifecycle · **Confidence:** Confirmed (code) · **Effort:** S

`MainActivity.kt:294-303` launches `RECORD_AUDIO + CAMERA + POST_NOTIFICATIONS` from `LaunchedEffect(Unit)` — on every launch **and every rotation**. `CAMERA` is not needed (torch works without it; the camera is opened through an intent — see MAN-01). Ask contextually (mic when the user first taps the orb, notifications when the service is first enabled, SMS/contacts when the feature is switched on), show a rationale, and remember denials.

### LIFE-10 — Commands are not serialised; busy flags are booleans
**Severity:** 🟡 Medium · **Category:** Reliability · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisApplication.kt:91-92,171-173` and `JarvisViewModel.kt:526-527,625` set `isProcessing=true` … `finally false`. Two overlapping commands (voice + typed, or rapid speech) clear the flag when the *first* finishes, and their TTS replies `QUEUE_FLUSH` each other. Serialise through the `CommandProcessor` mutex (LIFE-03) or use an in-flight counter, and support cancel-on-new-command.

### LIFE-11 — Back on the HUD kills the task; Back is always intercepted
**Severity:** 🔵 Low · **Category:** UX · **Confidence:** Confirmed (code) · **Effort:** S

`MainActivity.kt:85-92`: `BackHandler(enabled = true)` → on tab 0 calls `finishAndRemoveTask()`, removing the app from Recents — surprising for an "always-on" assistant and it disables the predictive-back animation (targetSdk 36). Let Back minimise (`moveTaskToBack(true)`) and offer an explicit "Quit Jarvis" action.

### 4.2 Voice pipeline (STT / TTS / VAD)

### VOICE-01 — Wake-word extraction corrupts commands and the "strict" lock isn't strict
**Severity:** 🟠 High · **Category:** Voice accuracy · **Confidence:** Confirmed (repro) · **Effort:** S–M

**Where** `JarvisSpeechManager.kt:209-246` (slice at `:232`), list at `:216-220`.

1. **Slicing bug** — the prefix is matched on a *normalised* string (punctuation → space, whitespace collapsed) but the command is cut from the *original* by `clean.substring(prefix.length)`. Reproduction (Appendix B):
   `"Hey Jarvis play music"` → `play music` ✅ · `"Hey, Jarvis play music"` → **`s play music`** · `"Jarvis, what's the time?"` → **`, what's the time?`** (leading comma breaks the exact-match intents such as `clean == "status"`). Recognizers with auto-punctuation produce exactly such text.
2. **Not "strict"** — step 3 (`lower.contains(prefix)`, `:237-243`) accepts the wake word *anywhere* (`"Tell my friend Jarvis is cool"` is accepted and the name stripped), while the HUD says *"Requires 'Jarvis' wake-word. Background chatter & music ignored."* and the voice reply says *"strictly require"*.
3. **False triggers** — the phonetic list contains `सर्व्हिस` ("service"), `javis`, `jarves`, `dharvis`; any Marathi sentence containing "service" passes the gate.
4. Test coverage (`ExampleUnitTest.kt:373-408`) only uses clean input.

**Fix** Normalise once and keep an index map, or strip with an anchored regex on the original text:
`^\W*(?:(?:hey|ok|okay|hi|हे|ओके|हाय)\W+)?(?:jarvis|जार्व्हिस|जार्विस|…)\b\W*`. When locked, require the wake word in the first two tokens; remove `सर्व्हिस`; use a small edit-distance match instead of ad-hoc misspellings. Add punctuation/case/Devanagari test cases (§6).

### VOICE-02 — Echo guard only catches an exact, complete echo
**Severity:** 🟡 Medium · **Category:** Voice accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisSpeechManager.kt:637-670`. The condition is `cleanCandidate == cleanUtterance || (len > 5 && cleanCandidate == cleanUtterance)` — the second clause is identical to the first, and the KDoc promises *"full containment"*. Real acoustic echoes are fragments/mis-transcriptions of the reply, so they pass. Use containment / token-overlap (≥ 60 %) against utterances spoken in the last ~3 s, and ignore results whose audio began while TTS was active.

### VOICE-03 — "Human voice detection" and "DSP / noise cancellation" are not implemented; the UI reports fabricated metrics
**Severity:** 🟡 Medium · **Category:** Accuracy of result / Trust · **Confidence:** Confirmed (code) · **Effort:** S (relabel) / L (implement)

**Where** `HumanVoiceActivityDetector.kt` (whole file), `NavigationAndHeader.kt:339-356`, `CoreHudScreen.kt:127,238-258`, `JarvisSpeechManager.kt:28-43` (KDoc).

* No `AudioRecord` exists in the detector; `NoiseSuppressor.create()/AcousticEchoCanceler.create()` are never called (only `isAvailable()`); `isNoiseSuppressorActive = nsAvailable || aecAvailable` is *availability*, not activity.
* `humanVoiceConfidence` is set to constants **0.85 / 0.98** (`:189`) or a formula on the recogniser's RMS; "VOCAL 98 %" is not a measurement.
* "NOISE: xx dB" is `noiseFloor * 3.5 + 35` (`:154`), an invented linear map from the recogniser's RMS labelled "calibrated SPL".
* The top bar shows a **hard-coded "DSP: ON"** badge; a default string even claims *"Vocal Filter 300Hz-3.4kHz"*.
* There are two independent 2-second silence timers (VAD + manager) that both call `commitAndSendSpeech()`.

**Impact** Users are told noise cancellation/voice verification is active when nothing measures or filters audio (the system recogniser does its own processing).

**Fix** Either relabel to what exists ("Mic level"), delete the fake indicators and the second timer — or implement it properly: own `AudioRecord` with the effects bound to the session and a real VAD (WebRTC/Silero), bearing Android 10+ concurrent-capture rules in mind.

### VOICE-04 — TTS pipeline: per-utterance reconfiguration on the main thread, no chunking, single pending slot
**Severity:** 🟡 Medium · **Category:** Performance / Reliability · **Confidence:** Confirmed (code) · **Effort:** M

* `MarathiTtsManager.prepareAndConfigureTts` runs on **every** `speak()` on the main thread (`JarvisSpeechManager.kt:1026-1027`): up to three `isLanguageAvailable` IPCs, `setLanguage`, `tts.voices` (IPC, builds a `Set`), `setVoice`, `setPitch`, `setSpeechRate` (`MarathiTtsManager.kt:90-198, 312-358`). Cache the decision per target locale and only reconfigure on change.
* No chunking to `TextToSpeech.getMaxSpeechInputLength()` (4000) — long answers make `speak()` fail (LIFE-06).
* `pendingSpeech` holds **one** string (`:69,1002-1006`); at start-up only the last early utterance is spoken.
* `QUEUE_FLUSH` (`:1032`) makes an incoming-call announcement, an SMS read-aloud and a rule message cut each other off. Use a priority queue (`QUEUE_ADD` for non-urgent, `FLUSH` only for user interrupts).
* `selectBestVoiceForLocale` fallback 3/4 matches `name.contains("india")` for *any* target locale — an en-US target could pick a Hindi/Marathi voice.

### VOICE-05 — Internal-player ducking sticks; local player has no audio focus
**Severity:** 🟡 Medium · **Category:** UX / Audio · **Confidence:** Confirmed (code) · **Effort:** S

`duckInternalPlayer(true)` is called on speech start (`JarvisSpeechManager.kt:1132`) and for urgent media commands (`:886`) but restored only in TTS `onDone/onError`, `stopSpeaking()` and `destroy()`. Ignored speech (wake-word gate, echo guard, `ERROR_NO_MATCH`) leaves the internal player at 15 % volume. `JarvisAudioPlayer` requests **no audio focus** (so it plays over calls/navigation and never pauses), calls blocking `prepare()` (`:35`) and does not release the `MediaPlayer` on completion (`:37-40`). Un-duck on every path that ends a listening cycle; add `AudioFocusRequest` + listener; use `prepareAsync()`.

### VOICE-06 — "Auto" language decodes English with the Marathi model first
**Severity:** 🔵 Low · **Category:** Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisSpeechManager.kt:520-526,752-759`, `VoiceLanguage.kt:19`: `AUTO` → `mr-IN`; `EXTRA_ADDITIONAL_LANGUAGES` is honoured only by some recognisers, so English commands in "Dual" mode are frequently mis-transcribed. Default to the device locale/`en-IN`, or run language-ID on partial results and restart with the detected locale.

### VOICE-07 — "Emergency" media words bypass the wake-word lock
**Severity:** 🔵 Low · **Category:** Logic · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisSpeechManager.kt:883-888` exempts `stop/pause/mute/…` from the lock, and `:1312-1318` calls `controlMedia(PAUSE)` (main thread) on *every* partial result. Anyone/TV in the room can pause media while the HUD claims a strict lock. Gate behind "media is actually playing" and document the exception in the UI.

### VOICE-08 — Magic numbers and documentation drift
**Severity:** 🔵 Low · **Category:** Maintainability · **Confidence:** Confirmed (code) · **Effort:** S

`MINIMUM_SPEECH_LENGTH_MILLIS = 1500` is used for the system dialog but the in-app recogniser uses a literal `3000L` (`:773`); the class doc advertises a *"2-second silence trigger"* while `onResults` commits immediately (`:1273`). Centralise constants and update the docs.

### VOICE-09 — Transliteration / speech-normalisation rough edges
**Severity:** 🔵 Low · **Category:** Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`MarathiTtsManager.kt:445-451`: `"aani"→"aani"` is a no-op; `"Jjaarvhisa"`/`"jaarvhisa"` can never match the generator (word-final schwa is suppressed → `jaarvhis`); `"siddha"→"sajj"` corrupts "सिद्धांत"; chandrabindu (ँ) is not handled. `normalizeTextForSpeech` maps "1–3 AM" to "सकाळी" (should be रात्री/पहाटे). `OfflineIntentEngine.kt:362` has a dead branch (`amPm.isNotEmpty()` is always true).

### VOICE-10 — Continuous recogniser restart loop: battery, earcons, throttling
**Severity:** 🟡 Medium · **Category:** Performance / Battery · **Confidence:** Code + 🧪 Needs device test · **Effort:** M

Every result/error schedules a new session after 200–850 ms (`:965-984`). The Google recogniser commonly plays start/stop earcons and may answer `ERROR_TOO_MANY_REQUESTS`; together with the 2 h partial wake lock, sensors and Compose animations this is a large battery cost. Add exponential back-off on consecutive errors, prefer the on-device recogniser (`createOnDeviceSpeechRecognizer`, API 33, `EXTRA_PREFER_OFFLINE`), duck/mute the earcon stream while starting, gate sessions with a real VAD, and show a "listening time / battery" indicator.

### 4.3 Intent engine & command routing (accuracy)

> Root cause for most items below: `OfflineIntentEngine.processCommand` (`OfflineIntentEngine.kt:33-1377`) is one ~1,350-line function of ~40 sequential `clean.contains("…")` tests on the raw lower-cased utterance, evaluated **before** the cloud fallback. Any sentence that merely *contains* a trigger word is captured, and several branches perform real side-effects (call, SMS, close app, change model, create events/rules). See INTENT-14 and §5 for the structural fix. All "repro" lines come from the scratch harness in Appendix B.

### INTENT-01 — Over-broad `contains()` intents swallow normal questions
**Severity:** 🟠 High · **Category:** Accuracy of result · **Confidence:** Confirmed (repro) · **Effort:** M

**Where** help `:326` · time `:338-349` · date `:397` · battery `:546` · light `:557` · orientation `:571` · diagnostics/"how are you" `:310` · plus `OfflineKnowledgeEngine.kt:670-671`.

| Utterance | What the app does | Should |
|---|---|---|
| "Can you **help** me write a poem" / "please **help** me call Rahul" | canned help text (the call/alarm/SMS branches are *below* the help branch, so polite phrasing never reaches them) | route to the real intent / LLM |
| "**what time** does the movie start", "what time is sunrise tomorrow" | speaks the *current time* | LLM / search |
| "**what day** is Diwali", "what date is Ganesh Chaturthi" | today's date | LLM / search |
| "what is the **battery** capacity of the iPhone 15" | device battery % | LLM |
| "how much **light** does a plant need" | lux reading | LLM |
| "what is the **orientation** of the sun" | device attitude | LLM |
| "what **commands** does git have" | Jarvis help text | LLM |
| "what time is my den**tist** appointment" | "The current time in **India** is…" (`contains("ist")`) | — |

**Fix** Anchored patterns, word boundaries and whole-utterance/verb-first structure, e.g. time: `^(?:what(?:'s| is)(?: the)? time(?: is it)?(?: now)?(?: in (?:india|ist))?|tell me the time|time please)\??$`; require ≤ N tokens for chit-chat intents; return `NotHandled` (fall through to the LLM) when confidence is low. Add the table above as unit tests (§6).

### INTENT-02 — Unanchored "open / start / launch" regex hijacks questions; failure path never falls back
**Severity:** 🟠 High · **Category:** Accuracy · **Confidence:** Confirmed (repro) · **Effort:** S

**Where** `OfflineIntentEngine.kt:830-860`. `openAppRegex = (?:open|launch|start|उघडा|चालू करा)\s+(.+)` is applied with `find()` (unanchored) and so is `trailingOpenRegex`.
*Repro:* "how do I **start learning Kotlin**" → target `learning kotlin`; "when does the market **open**" → target `when does the market`. No such app → `IntentResult(success=false, "Could not find an installed application matching '…'", canFallbackToGemini=false)`. On the ViewModel path that text is *spoken* instead of asking Gemini (LIFE-03 #2).
**Fix** Anchor (`^(?:please\s+)?(?:open|launch|start)\s+(.+)$`), treat as an app-launch only if the target resolves to an installed app with confidence, otherwise return `NotHandled` so the question reaches the LLM.

### INTENT-03 — Casual questions silently change the Gemini model setting
**Severity:** 🟠 High · **Category:** Logic / Side-effects · **Confidence:** Confirmed (code) · **Effort:** S

**Where** `OfflineIntentEngine.kt:1254-1357`. Trigger: utterance contains *any* of `gemini|model|fallback|जेमिनी|मॉडेल`; inside, nested `contains` on `latest`, `flash`, `auto`, `cascade`, `3.8`, `3.7`, `3.6`, `3.5`, `3.1`, `2.5`, `preview`…
"what is the **latest model** of iPhone" → persists `GEMINI_FLASH_LATEST` and says *"Gemini Flash Latest model engaged"*; "what car **model** has a **2.5** litre engine" → switches to 2.5 Flash; the `auto` branch selects `CASCADE_LITE` (not `AUTO_CASCADE`). The tier is written to SharedPreferences (`GeminiAssistantEngine.setSelectedModelTier`). The `3.8`/`3.7` branches are duplicated (`:1290-1299` and `:1300-1309`, the second pair is unreachable).
**Fix** Require an explicit command shape (`^(?:use|switch to|set)(?: the)?(?: gemini)?\s+(model|tier)?\s*…`) against a whitelist map; confirm the change out loud; remove dead branches.

### INTENT-04 — Calls & SMS: wrong-recipient and unintended-send risks, no confirmation
**Severity:** 🟠 High · **Category:** Safety / Accuracy · **Confidence:** Confirmed (repro + code) · **Effort:** M

**Where** `OfflineIntentEngine.kt:862-953`, `TelephonyAlarmManager.kt:148-188,265-278,372-470`.

1. **No confirmation** before `ACTION_CALL` / `SmsManager.sendTextMessage`, although speech-to-text errors (especially Marathi names) are common.
2. **Ambiguous names**: `callContactByName` / `sendSmsToContact` take `matches.first()` (`:181,271`) of a list sorted by `DISPLAY_NAME ASC` (`:400,442`) — "Rahul" calls/texts the alphabetically first "Rahul …".
3. **Marathi "ला" stripping** — `.replace("ला","")` (`:889`) removes *every* "ला" in the name: "सुशीला" → "सुशी" (a different contact). Repro confirmed.
4. **Digits from anywhere** — `dialDigits = clean.filter { isDigit || '+' }` (`:867`): "call Rahul at 10:30" → dials `1030`.
5. **Default body is sent** — if no body is parsed, `"Hello from Jarvis"` (`:915`) is sent. The KDoc example *"text Mom I will be home soon"* does **not** parse a body (repro: target = `mom i will be home soon`, body = none); only `saying|that|with text` work; `bodySeparators` (`:913`) is unused.
6. **Lower-cased text** — parsing runs on `clean` (lower-cased), so SMS bodies and calendar titles lose capitalisation ("i'll be home at 5").
7. `startsWith("text")` also matches "text to speech settings" / "textbook…" → tries to SMS a contact called "to speech settings" or opens the composer.
8. Calls use `startActivity(ACTION_CALL)`, which Android blocks from a background context (HW-01); SMS goes through `SmsManager` and is not affected.

**Fix** A `PendingAction` confirmation state ("Call **Rahul Patil (mobile)**? Say *yes* to confirm", 5 s timeout); disambiguate when > 1 match; rank matches (exact > prefix > token) instead of alphabetical; strip Marathi `ला` only as a whole trailing token (`(?<=\s)ला(?=\s|$)`); take digits only from a contiguous phone-number pattern (≥ 7 digits); ask "What should I say?" when the body is missing; parse on the original text and lower-case only for matching.

### INTENT-05 — Alarm & timer parsing produces wrong times
**Severity:** 🟠 High · **Category:** Accuracy · **Confidence:** Confirmed (repro) · **Effort:** M

**Where** `OfflineIntentEngine.kt:1065-1130`, `TelephonyAlarmManager.kt:473-522`.
* `hourOnlyRegex = (?:for|at|वाजता|ला)?\s*(\d{1,2})\s*(?:o'?clock|am|pm|वाजता)?` (`:1087`) makes everything but the digits optional → it grabs the **first 1–2 digit number anywhere**. Repro: "set alarm for **45** minutes" → hour 45 → clamped to **23:00** and the app says "Alarm set for 23:00"; "wake me up **in 8 hours**" → 08:00; "alarm 5 minutes from now" → 05:00.
* `isAm = clean.contains("am")` (`:1079`) is a substring test ("ex**am**", "s**am**e", "**Sam**") → "alarm for my exam at 12 pm" → 00:00.
* Timers support minutes/seconds only (`:1118-1119`): "timer for **1 hour**" silently falls through to other intents.
* The Marathi confirmation literally speaks the placeholder **"सकाळी/वेळेसाठी"** (`:1101`).
* `setAlarm(..., skipUi = true)` returns "Alarm set" although the clock app may not have processed it (HW-01).
**Fix** Anchored absolute (`(?:at|for)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b`) and relative (`in\s+(\d+)\s*(minutes?|hours?)`) grammars; **reject** hour > 23 and ask again instead of clamping; word-boundary am/pm; support hours; convert relative alarms to absolute times; fix the Marathi text.

### INTENT-06 — Voice scheduling: substring flags, past-time wrap, unsupported recipients
**Severity:** 🟠 High · **Category:** Accuracy / Safety · **Confidence:** Confirmed (repro) · **Effort:** M

**Where** `OfflineIntentEngine.kt:955-1063`.
* `isGeminiTask` uses `clean.contains("ai")` (`:967`) → any sentence with "d**ai**ly", "ag**ai**n", "em**ai**l", "w**ai**t" becomes a *Gemini* task. Repro: "schedule text **daily** to 9876543210 saying good morning" → `isGeminiTask=true`. `useIndiaTime = contains("ist")` fires on "dent**ist**", "l**ist**".
* `isAm = contains("am")` (`:990`): "schedule text to **Ram** at 12 pm" → hour **0** (repro).
* No hour given → `hour = current hour`, `minute = (now.minute + 5) % 60` (`:1010-1012`): at minute ≥ 55 the time wraps into the **past** (10:57 → 10:02) and the task runs tomorrow.
* Recipient must be digits (`numRegex`, `:1015-1016`) although the prompt promises "phone number **or contact**" (`:1021-1025`); contact names are never parsed.
* A *recurring* SMS is created immediately with no confirmation (see SCH-05).
**Fix** Word-boundary parsing; carry the hour (`Calendar.add(MINUTE, 5)`); resolve names via the contact search; read the plan back and require *yes*.

### INTENT-07 — Music query extraction corrupts titles; "play anything" is music
**Severity:** 🟡 Medium · **Category:** Accuracy · **Confidence:** Confirmed (repro) · **Effort:** S

`OfflineIntentEngine.kt:700-803` derives `songQuery` with ~100 chained `.replace()` calls over the whole string, and `HardwareController.kt:523-546` repeats it. Substrings are removed *inside* titles: "**Display** Picture" → "Dis Picture", "**Open** Arms" → " Arms", "Re**play**" → "Re"; every Marathi "वर" is stripped (`:542`: "ईश्वर", "सावरकर"…). `startsWith("play ") && !contains(game|football|cricket)` (`:689`) makes "play chess / play with me / play guitar" a music request.
**Fix** Anchored prefix/suffix parsing: `^play\s+(?:(?:the\s+)?songs?\s+)?(?<q>.+?)(?:\s+(?:on|in)\s+(?<app>youtube|vlc|spotify))?$`; no substring replacement.

### INTENT-08 — "Close …" hijacks sentences, mangles app names and kills by substring
**Severity:** 🟡 Medium · **Category:** Accuracy / Safety · **Confidence:** Confirmed (repro) · **Effort:** S

`OfflineIntentEngine.kt:634-668`, `HardwareController.kt:928-975`.
* `startsWith("close ")|"exit "|"kill "` matches "**close the door**" → presses Home and answers "Closed the door and returned to Home".
* `.replace("app","")` mangles names: "whatsapp" → "whats", "apple music" → "le music".
* The fuzzy branch passes **every installed package whose label or package name contains the text** to `killBackgroundProcesses` (`:946-958`), with no minimum length.
* `killBackgroundProcesses` only affects cached background processes — it cannot stop a foreground app or a running foreground service (e.g. a music player), yet the reply says "Closed X".
**Fix** Resolve to one app (exact/normalised label) with a minimum length; confirm when ambiguous; reply truthfully ("sent X to the background"); never bulk-kill.

### INTENT-09 — App-launch mapping: "open PhonePe" opens the Dialer
**Severity:** 🟡 Medium · **Category:** Accuracy · **Confidence:** Confirmed (repro) · **Effort:** S

`HardwareController.kt:302-388`. `query.contains("phone")` → Dialer, so **"open PhonePe"** (a very common Indian UPI app) launches the dialer; likewise `contains("store")`, `"photo"`, `"map"`, `"clock"`, `"email"`, `"browser|chrome"` (opens google.com and reports "Web Browser"). The final fallbacks `installed.firstOrNull { appName.contains(query) }` / `packageName.contains(query)` (`:379-380`) have **no minimum length**, so a two-letter garbage query launches an arbitrary app.
**Fix** Resolve by exact label → normalised label → package; special-cases only on *exact* match; require ≥ 3 chars; score candidates and ask when tied.

### INTENT-10 — "Time in India" uses the device time zone; date questions answered with today's date
**Severity:** 🟡 Medium · **Category:** Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`OfflineIntentEngine.kt:338-428`: the answer is built from `Calendar.getInstance()` (device zone) but labelled *"The current time in India is…"* — wrong whenever the device is outside IST. `contains("ist")` triggers on "dentist/list". Use `ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))` when India is asked; anchor the patterns; speak dates with locale-aware formatting.

### INTENT-11 — Voice rule builder: silent defaults and lost phrases
**Severity:** 🟡 Medium · **Category:** Logic · **Confidence:** Confirmed (repro) · **Effort:** S

`OfflineIntentEngine.kt:1215-1236,1379-1494`. Unknown action silently becomes `TOGGLE_FLASHLIGHT` (`:1481-1483`); the speak phrase is lost — `text.substringAfter("speak","").substringAfter("say","")…` (`:1478`) returns `""` whenever a later delimiter is missing (repro: "…shake **speak hello there**" → `param = ''`); `contains("dark"|"time"|"say")` are substring tests; no duplicate check; created rules are enabled at once with a 4 s cooldown.
**Fix** Ask a clarifying question when trigger/action is unknown; capture the phrase with a regex (`(?:say|speak)\s+"?(.+?)"?$`); de-dupe (RULE-01).

### INTENT-12 — Volume / mute commands never restore state and use inconsistent steps
**Severity:** 🟡 Medium · **Category:** Logic / UX · **Confidence:** Confirmed (code) · **Effort:** S

**Where** `OfflineIntentEngine.kt:254,477-532`, `JarvisViewModel.kt:796-812`, `HardwareController.kt:117-129,139-163`.
* "mute", "silent mode", "meeting mode" set the music and notification streams to 0 and the ringer to SILENT **without saving the previous values**. "unmute" then hard-codes media volume **60 %** and ringer NORMAL (`:527-532`) and says *"Audio and ringer restored to standard level"*. The default rule pair *Face-Down → MUTE_ALL* / *Face-Up → SET_VOLUME 75* only restores the **music** stream, so the **ringer stays silent** after the phone is picked up — the user misses calls.
* Step sizes are inconsistent: voice ±25 %, UI buttons ±15 %, `HardwareController.increase/decreaseVolume` default ±10. "increase the volume **by 10 percent**" applies +25.
**Fix** Save an `AudioSnapshot` (per stream + ringer mode) before muting and restore exactly that on unmute/face-up; one shared step constant; parse "by N percent".

### INTENT-13 — `IntentResult.success` conflates "not understood" with "understood but failed"
**Severity:** 🟡 Medium · **Category:** Design / Accuracy · **Confidence:** Confirmed (code) · **Effort:** M

`OfflineIntentEngine.kt:16-21`. Hardware failures (flashlight, media, mute, app launch) return `success=false` *with* a useful spoken message, but callers treat `false` as "unknown → maybe Gemini" (LIFE-03 #2); `canFallbackToGemini` is `true` only for the final `UNKNOWN` (`:1371-1376`). Several branches speak success text regardless of the result (`muteAllAudio()` result ignored, `:482-484`). Replace with a sealed `Outcome { Handled(text) · Failed(reason, text) · NeedsConfirmation(prompt, action) · NotHandled }`.

### INTENT-14 — Structure: a 1,350-line function, per-call allocations, inline strings
**Severity:** 🔵 Low · **Category:** Maintainability / Performance · **Confidence:** Confirmed (code) · **Effort:** L

Regex objects and keyword lists are rebuilt on every command (`:534,831-832,974,992,998,1015-1016,1033,1081-1087,1118-1119`), English/Marathi strings are inline, and behaviour depends on branch order — which is exactly why INTENT-01…11 exist. Introduce `interface IntentMatcher { fun match(u: Utterance): Match?; suspend fun execute(m: Match): Outcome }`, register by priority, hoist Regex to `companion object`, move strings to resources. (Also enables table-driven tests, §6.)

### INTENT-15 — "Calendar" questions create events; the reply says "Created" though only a UI was opened
**Severity:** 🟡 Medium · **Category:** Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`OfflineIntentEngine.kt:1155-1173`: any utterance containing "calendar" without "open/show" launches an insert-event screen whose title is the *entire lower-cased sentence* ("is there anything on my calendar today"). `TelephonyAlarmManager.addCalendarEvent` (`:551-573`) only starts `ACTION_INSERT` (the user must still tap Save) but returns *"Created calendar event"*, which is spoken; the start time is always "now + 1 h" regardless of what was said. Parse date/time or ask for it; say "Opening the calendar to add …".

### 4.4 Offline knowledge engine

### KNOW-01 — Partial-match math answers a fragment of the question
**Severity:** 🟠 High · **Category:** Accuracy of result · **Confidence:** Confirmed (repro) · **Effort:** M

**Where** `OfflineKnowledgeEngine.kt:244-437`. Each specialised regex (`power`, `percent`, `sqrt`, `trig`, `log`, `mod`…) runs `find()` on the *whole* text and answers about the fragment it found, ignoring the rest.
*Repro:* "2 + 3 ^ 2" → answers **9** (power regex) instead of **11**; "100 + 15% of 200" → **30** (in Marathi) instead of **130**. `InfixParser.parseFactor()` returns `0.0` on garbage (`:475`), so "5 +" → 5 and "(5 + 3" → 8; trig/log are unanchored ("Pakistan 5" → tan(5°), "blog 10" → log 10).
**Fix** Require the *entire* normalised utterance to match a math grammar; support `^`, unary minus and functions in the parser; throw on malformed input and fall through to the LLM; say "I can't divide by zero" instead of falling through silently.

### KNOW-02 — Answers in the wrong language, raw `Double` output
**Severity:** 🟡 Medium · **Category:** Accuracy / UX · **Confidence:** Confirmed (repro) · **Effort:** S

"15% of 200" (English) matches the *Marathi* percent branch first (`:344`) → "200.0 चे 15.0 टक्के 30 होतात, सर."; km↔mi, m↔ft, kg↔lb, kg→g **always** answer in Marathi (`:528-577`); °C/°F mixes both languages in one sentence; values are printed as raw `Double` ("200.0", "sin(30.0°)", "Logarithm … of 100.0") because only some branches call `formatNumber`. Detect the language once, format through one helper, and pass an English/Marathi template pair.

### KNOW-03 — Greeting / quote / fact / settings triggers swallow real questions and cause side-effects
**Severity:** 🟠 High · **Category:** Accuracy / Side-effects · **Confidence:** Confirmed (code) · **Effort:** M

* `tryEvaluateQuote` (`:209-216`): `contains("thought"|"विचार"|"motivat"…)` → a random quote for "एक प्रश्न **विचार**ू का?" / "what are your **thoughts** on…".
* `lookupOfflineFact` (`:639-884`): `contains("नमस्कार")`, `"good morning"`, `"thank you"` return only the greeting and **discard the real question** ("नमस्कार, आजचे हवामान काय आहे?", "good morning, what's the weather in Pune"); `contains("features"|"capabilities")` → Jarvis's capabilities for "features of the iPhone 15"; `contains("burns")` → **first-aid instructions** for "how many calories burns walking"; `contains("नाव काय आहे")` → identity for "what's this song's name".
* `tryLaunchSettings` (`:609-633`): `contains("वायफाय"|"ब्लूटूथ"|"लोकेशन"|"डिस्प्ले"|"साउंड"|"स्टोरेज"|"ॲप्स")` **launches Settings** for informational questions ("वायफाय म्हणजे काय?").
**Fix** Word-boundary + whole/short-utterance gating (chit-chat intents only when ≤ 3–4 tokens); never launch UI for a question; hand everything else to the LLM.

### KNOW-04 — Offline answers bypass conversation memory; labels wrong; chain runs twice
**Severity:** 🟡 Medium · **Category:** Logic · **Confidence:** Confirmed (code) · **Effort:** S

`OfflineIntentEngine.kt:1359-1368` already calls `OfflineKnowledgeEngine.answerQuery` and returns `success=true`; therefore (a) the second `answerQuery` in the callers (`JarvisApplication.kt:115-119`, `JarvisViewModel.kt:550-565`) never handles anything — the `conversationMemory.addTurn` calls there are dead, so follow-ups ("and its population?") have no context; (b) history is labelled `OFFLINE_CORE` instead of `OFFLINE_KNOWLEDGE`; (c) each unhandled utterance runs the ~120-condition chain twice. Record memory and labels once, inside the unified processor (LIFE-03).

### KNOW-05 — Content claims and data quality
**Severity:** 🔵 Low · **Category:** Accuracy / Maintainability · **Confidence:** Confirmed (code) · **Effort:** M

The header claims *"300+ Indian States, World Countries", "Trie / Hash lookup", "<2 ms"*; the code has ~25 states, ~20 countries and a linear `when`. Quote attributions to Shivaji Maharaj / Sambhaji Maharaj / J. Phule are unverified. Time-sensitive facts (capitals, office-holders) will age. Move the data to a versioned JSON resource with sources, add a content review step, and fix the header.

### 4.5 Gemini / AI integration

### AI-01 — A valid answer containing "quota exceeded" trips a 15-minute model lock-out
**Severity:** 🟠 High · **Category:** Logic / Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

**Where** `GeminiAssistantEngine.kt:475-483` (and the health-check copy at `:654-662`).
`isQuotaExhausted = code == 429 || body.contains("RESOURCE_EXHAUSTED") || body.contains("quota exceeded")` is evaluated on the **raw body before `isSuccessful`**. A *successful* (HTTP 200) response whose answer text contains those words ("What does RESOURCE_EXHAUSTED mean in gRPC?", "fix quota exceeded error in Google Cloud") is classified `RateLimited`: the good answer is discarded, `recordModelQuotaExhausted()` blocks that model for 15 minutes (and links `gemini-3.8-flash` ⇄ `gemini-flash-latest`, `:55-66`), and the next tier is tried. The 15-minute lock-out is also fixed regardless of RPM-vs-daily limits.
**Fix**
```kotlin
val err = if (!isSuccessful) runCatching { JSONObject(body).optJSONObject("error") }.getOrNull() else null
val quota = code == 429 || err?.optString("status") == "RESOURCE_EXHAUSTED"
val cooldown = parseRetryDelaySeconds(err)            // error.details[].retryDelay (RetryInfo); default 60 s
```
Only inspect the body when the HTTP status is an error; set the cool-down from `retryDelay`.

### AI-02 — Gemini 3 defaults to HIGH thinking; temperature 0.7 is discouraged → slow, degraded voice replies
**Severity:** 🟠 High · **Category:** Performance / Accuracy · **Confidence:** Doc-verified + Confirmed (code) · **Effort:** S

**Where** `GeminiAssistantEngine.kt:441-447` (thinking config only when `enableHighThinking`; `temperature = 0.7` always), cascades `:222-263`.
**Docs (Appendix A):** *"If `thinking_level` is not specified, Gemini 3 will default to `high`"* (3.1 Flash-Lite defaults to `minimal`; 3.8 Flash supports `low/medium/high` and **`minimal` returns an error**). *"For all Gemini 3 models, we strongly recommend keeping the temperature at its default value of 1.0 … lowering it may lead to looping or degraded performance."*
**Consequences**
1. The "non-high-thinking" path is **not** fast — it inherits the model default (dynamic HIGH on the Flash models), so the *High Thinking* toggle changes little and every voice query pays reasoning latency and tokens; the "⚡ lightning-fast voice responses" description of Cascade Lite is not backed by the request.
2. `temperature=0.7` on Gemini 3 contradicts the vendor guidance.
3. `thinkingLevel` is a Gemini 3 parameter; `gemini-2.5-flash` uses `thinkingBudget`. If the user selects **2.5 Flash + High Thinking** (`:249-262`), the request returns HTTP 400 and the retry (AI-03 #4) repeats the same body.
**Fix** Keep a per-model capability map and set an explicit level:
```kotlin
private fun thinkingConfig(model: String, hard: Boolean): JSONObject? = when {
    model.startsWith("gemini-3")   -> JSONObject().put("thinkingLevel", if (hard) "high" else "low")  // 'minimal' is rejected by 3.8
    model.startsWith("gemini-2.5") -> JSONObject().put("thinkingBudget", if (hard) -1 else 0)
    else -> null
}
// omit "temperature" for gemini-3* (default 1.0)
```
Prefer explicit model IDs over `*-latest` aliases (AI-05) so the meaning of these parameters can't change underneath you.

### AI-03 — Unbounded latency, no offline fast-fail, misleading failure message
**Severity:** 🟠 High · **Category:** Reliability / UX · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `GeminiAssistantEngine.kt:33-37` (connect 30 s / read 45 s / write 30 s, no `callTimeout`), `:186-410` (cascade), `:342-368` (retry), `:402-409` (final text), `:471` (blocking `execute()`).
1. Worst case ≈ 5 models × (1 + retry-without-tools) × 30–45 s ≈ **minutes**, sequential, with **no overall deadline** — the speech manager's 6 s watchdog re-opens the mic long before (LIFE-06).
2. **No offline fast-fail**: `UnknownHostException`/`ConnectException` → `ExceptionError` → the loop tries every remaining model, each failing the same way.
3. `execute()` is **not cancellable** — cancelling the coroutine (ViewModel cleared, task cancelled) leaves the HTTP call running to its timeout.
4. The comment says *"if tools caused a 400, retry without tools"* but the retry runs for **any** `ApiResult.Error` (401/403/404/500/503), doubling load on failing/overloaded models.
5. The final message *"Cloud intelligence rate limit reached across all tiers…"* (and the Marathi twin) is spoken for **any** failure — no network, invalid key (400/403), retired model (404) — so the user is misdiagnosed and cannot fix it.
6. A model that answers 404 (unknown/retired) is not remembered, so it is retried first on every query.
**Fix**
```kotlin
private suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, r: Response) = c.resume(r)
        override fun onFailure(call: Call, e: IOException) { if (!c.isCancelled) c.resumeWithException(e) }
    })
    c.invokeOnCancellation { cancel() }
}
// withTimeout(15_000) around the whole cascade; client.newBuilder().callTimeout(10, SECONDS)
```
Classify errors: offline → answer immediately ("I'm offline"); 400/401/403 → "API key / configuration problem" (do **not** cascade); 404 → mark model unavailable for 24 h; 429 → cool-down from `retryDelay`; 5xx → next tier; retry-without-tools only for a 400 that mentions tools.

### AI-04 — API key handling: in the URL, plaintext at rest, embedded in the APK
**Severity:** 🟠 High · **Category:** Security · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `GeminiAssistantEngine.kt:421,625` (`…:generateContent?key=$apiKey` — *and* the same key again in the `x-goog-api-key` header at `:467,646`); `:132-139` (custom key stored in plaintext `jarvis_gemini_prefs`); `BuildConfig.GEMINI_API_KEY` (secrets plugin, `app/build.gradle.kts`); `VoiceDiagnosticsDialog.kt:111` (editor pre-filled with the *effective* key, so saving copies the embedded build key into prefs).
* A key in the query string ends up in proxies, crash reports and any future logging interceptor; the header is the documented method (docs' `curl` sample).
* A key compiled into `BuildConfig` is extractable from any distributed APK (obfuscation does not hide string constants; release also has `isMinifyEnabled=false`).
**Fix** Header only; never log the URL. Store user-supplied keys with `EncryptedSharedPreferences`/a Keystore-wrapped DataStore. Do not ship a shared key in the APK: proxy through your own backend or use Firebase AI Logic + App Check (the `firebase-appcheck-*` dependencies are already present but unused — BLD-01). Restrict the key by Android package + SHA-1 in Google Cloud, set quotas, and rotate the current key if an APK was ever shared.

### AI-05 — Hard-coded model catalog will rot (one model already has a shutdown date)
**Severity:** 🟡 Medium · **Category:** Maintainability / Reliability · **Confidence:** Doc-verified · **Effort:** M

Verified against the live docs (2026-09-24, Appendix A): all IDs currently exist, **but** `gemini-3.1-flash-lite` has a **shutdown date of 2027-05-07** (replacement `gemini-3.5-flash-lite`); `gemini-3-flash-preview` is a *preview* model (replacement `gemini-3.6-flash`); `gemini-flash-latest` / `gemini-flash-lite-latest` are aliases that are "hot-swapped" with two weeks' notice; `gemini-3.5-flash` is described as the "legacy" Flash. The list is duplicated in `GeminiModels.kt`, `GeminiAssistantEngine.kt:222-263,556-561` and `OfflineIntentEngine.kt:1254-1357`, and frozen by a unit test that asserts literal IDs and counts (`ExampleUnitTest.kt:22-53`).
**Fix** One `ModelCatalog` (id, family, thinking-param kind, supports Search/Maps, status, sunset date) loaded from a bundled JSON (optionally refreshed from `models.list`); tests assert catalog *invariants*; show a Diagnostics warning for models within 90 days of sunset; prefer explicit IDs in production.

### AI-06 — Search/Maps tool heuristics are substring-based and expensive
**Severity:** 🟡 Medium · **Category:** Accuracy / Cost · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisApplication.kt:122-146`, `JarvisViewModel.kt:567-585`: `contains("rate")` (gene**rate**, accu**rate**), `"rain"` (t**rain**, b**rain**), `"live"` (de**live**r), `"where"` (some**where**), `"near"` (li**near**), `"current"` (con**current**), `"whether"` (a very common word), and the sticky `activeTopic == "Weather"` (MEM-01) turn ordinary questions into grounded-search calls (latency + billing). `useMaps` wins over `useSearch` (`GeminiAssistantEngine.kt:453-457`), and Maps grounding is requested **without a user location** (no location code exists), so "near me" cannot be answered. Use word-boundary matching or let the model decide (enable `googleSearch` for online queries); send `retrievalConfig.latLng` only if location is intentionally supported.

### AI-07 — Empty/blocked responses are recorded as success; no output cap
**Severity:** 🟡 Medium · **Category:** Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`GeminiAssistantEngine.kt:490-511`: when `candidates` is missing (safety block / `promptFeedback.blockReason`) or has no text (e.g. `finishReason=MAX_TOKENS`), the code returns `Success("I received no textual response from the intelligence core, sir.")` — counted as a **successful attempt** (stops the cascade), stored in conversation memory as a *model turn*, and spoken. There is no `maxOutputTokens`, so replies can exceed the TTS 4000-char limit (LIFE-06). Inspect `finishReason`/`promptFeedback`, treat empty output as a failure (next tier or a specific "blocked by safety filters" message) and set `maxOutputTokens` (≈ 300–500 for voice).

### AI-08 — "Cloud AI" toggle is not persisted and not enforced
**Severity:** 🟡 Medium · **Category:** Privacy / Logic · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisViewModel.kt:106-110,391-393`: `_isOnlineIntelligenceEnabled` is initialised from "an API key exists", never saved, and consulted only by `submitVoiceCommand`. `JarvisApplication.handleVoiceCommand` (background path) and `ScheduledTaskManager` Gemini tasks ignore it, so "Cloud AI: off" still sends data to Google. Persist the setting, enforce it in the single processor (LIFE-03) and scheduler, and surface it in Diagnostics.

### AI-09 — Scheduled Gemini tasks can SMS a raw prompt or an error string to a third party
**Severity:** 🟠 High · **Category:** Accuracy / Safety · **Confidence:** Confirmed (code) · **Effort:** S–M

**Where** `ScheduledTaskManager.kt:308-391`.
* `GEMINI_TO_SMS`: if the AI text contains "strict offline mode"/"API key" it falls back to `task.messageText` — which is the user's **instruction** ("remind him about the meeting"), not a message — and **sends it to the recipient**. Other failure strings (e.g. *"I received no textual response…"*) are sent verbatim.
* `queryAssistant` never throws (it returns canned strings), so the `catch` blocks are dead; canned error text is stored as `lastResultText` and `GEMINI_QUERY` is marked successful.
* Scheduled prompts use the interactive engine → they append turns to the user's `ConversationMemory` and mutate `activeLocation/Topic/Subject`; the "sir" persona leaks into SMS drafts.
**Fix** Add a stateless `generate(prompt, persona = false): Result<String>`; on failure **send nothing**, mark the run `FAILED` and notify the user; keep memory out of background jobs.

### AI-10 — Prompt/caching/health-check details
**Severity:** 🔵 Low · **Category:** Performance / Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

* `buildContextualSystemInstruction()` (`ConversationMemory.kt:424-469`) is rebuilt per request, ~2 KB, with a second-precision clock **at the start** of the prompt (defeats implicit prefix caching) and hard-coded IST.
* Multi-turn history is replayed as text without thought signatures (docs: not enforced for text, but omitting them *"will degrade the model's reasoning"*).
* `testModelHealth` sends a real request (counts against quota) with `maxOutputTokens = 5`, which can yield empty output on thinking models; HTTP 200 is reported as healthy.
* `isComplexQuery` (`:162-174`) treats "java", "program", "function", "compose", "explain" as hard → high thinking for "TV **program**", "**explain** the rules of cricket".
Move the clock to the end/user turn, use word-boundary heuristics, and give the health check a tiny prompt with an explicit low thinking level.

### 4.6 Conversation memory

### MEM-01 — Any "in/for/at <word>" becomes a location and forces the Weather topic (persisted)
**Severity:** 🟠 High · **Category:** Accuracy / State · **Confidence:** Confirmed (repro) · **Effort:** S

**Where** `ConversationMemory.kt:308-339` (fallback regex), `:179-183` (sets location **and** `setActiveTopic("Weather")`), `:347-349` (also applied to *model replies*), `:113-126` (persisted to SharedPreferences).
Fallback `\b(?:in|for|at|around|weather in|whether in)\s+([A-Za-z]{3,20})\b` treats the next word as a city unless it is in a tiny deny-list. *Repro:* "recipe **for pasta**" → location **Pasta**; "explain recursion **in programming**" → **Programming**; "find a movie **for children**" → **Children**; "best player **at cricket**" → **Cricket**; model text "photosynthesis happens **in plants**" → **Plants**.
The bogus location is injected into the system instruction (*"Geographic Location: 'Children' (for any weather, local time…)"*), shown as the HUD badge ("LOC: CHILDREN"), and — through `activeTopic == "Weather"` — makes later unrelated queries count as weather/real-time (AI-06).
**Fix** Accept a location only from `KNOWN_CITIES`/a geocoder and only when the query is weather-like; never set the topic from a location alone; never derive from model text; expire context after N minutes.

### MEM-02 — "whether" → "weather" rewrite corrupts general questions
**Severity:** 🟠 High · **Category:** Accuracy · **Confidence:** Confirmed (repro) · **Effort:** S

`ConversationMemory.kt:162-177` rewrites "whether" whenever the sentence contains any of `today|full|tomorrow|forecast|report|rain|temperature|"in "|"for "` (the last two occur in almost every sentence) or the persisted topic is Weather. *Repro:* "Tell me **whether** Python is good **for** beginners" → "Tell me **weather** Python is good for beginners"; "I wonder whether to go **in** the morning" → "…weather to go…". The corrupted text is what gets sent to Gemini. Replace only when followed by a weather noun/city ("whether in Pune", "whether report") and never because of stale topic state.

### MEM-03 — Plaintext persistence and unsynchronised state
**Severity:** 🟡 Medium · **Category:** Privacy / Concurrency · **Confidence:** Confirmed (code) · **Effort:** M

`ConversationMemory.kt:93-111` writes **all turns as plaintext JSON on every `addTurn`**; `normalizeAndEnrichQuery` (`:162-224`) mutates shared state without `@Synchronized` while other methods are synchronized; scheduled tasks share the instance (AI-09). Use encrypted storage with retention (e.g. 24 h), an opt-out, and atomic state updates.

### 4.7 Sensors & automation rules

### SENS-01 — Edge-trigger state is reset *after* the early-return guards → rules stop re-firing
**Severity:** 🟠 High · **Category:** Logic · **Confidence:** Confirmed (repro) · **Effort:** S

**Where** `SensorConflictEngine.kt:45-193`. The rule's "was the condition active?" flag (`ruleConditionActiveMap`) is updated only at the very end (`:175-192`), i.e. **after** the cooldown guard (`:46-49`), pocket guard, face-down guard, no-op guard (`:83-115`) and oscillation guard have already `return`ed. A "condition became false" event that is swallowed by a guard therefore never resets the flag, and the next real rising edge is reported as *"Condition already active (level hold)"*.
*Concrete failure — the default rule "Face-Down Meeting Silence" (FLIP_FACE_DOWN → MUTE_ALL):* flip face-down (fires, volume 0) → pick up (event is swallowed by the *"already muted"* guard at `:99-103`; flag stays `true`) → raise the volume → flip face-down again → **suppressed** (repro, Appendix B: current order `fire=False (Condition already active)`, fixed order `fire=True`). The same happens for PROXIMITY_NEAR/FAR, CHARGER_* rules whose reset event falls inside the cooldown.
**Fix** Compute the condition and update the edge map **first**, then apply guards to the rising-edge decision:
```kotlin
val cond = evaluateCondition(rule, event)                 // pure function
val wasActive = active.put(rule.id, cond) ?: false
val rising = cond && !wasActive
if (!rising && !isPulse(event)) return false to (if (cond) "Condition already active" else "Condition not met")
// …now cooldown / pocket / face-down / no-op / oscillation guards…
```

### SENS-02 — Battery rules ignore charging state; spurious start-up events; fabricated 85 %
**Severity:** 🟠 High · **Category:** Logic / Accuracy · **Confidence:** Confirmed (repro) · **Effort:** S

**Where** `SensorConflictEngine.kt:162-172`, `SensorHub.kt:94-120`, `HardwareController.kt:842-848`.
* `BATTERY_FULL -> event.level >= 99` ignores `isCharging` and the rule's threshold. Unplugged, when the level drops 100 → 99 the default rule *"Full Battery Charge Notice"* speaks *"Battery fully charged at one hundred percent, sir. You may disconnect the charger"* while **discharging** (repro).
* `BATTERY_LOW -> level <= threshold` ignores charging: plugging in at 15 % announces *"Battery depleted… Please connect charger"* while charging.
* The first sticky `ACTION_BATTERY_CHANGED` is compared with default telemetry (`isCharging=false`, `level=100`) and emits spurious `ChargerStatusChanged`/`BatteryChanged` each time the service starts (e.g. *"Power source connected"* whenever it starts on a charger).
* `SensorHub.kt:99` and `HardwareController.kt:844` return a **fabricated 85 %** when the level is unavailable (the second is dead code).
**Fix** `BATTERY_FULL: isCharging && level >= max(threshold, 99)`; `BATTERY_LOW: !isCharging && level <= threshold`; seed `prev*` from the first broadcast without emitting; return `null`/"unknown" instead of 85.

### RULE-01 — "Load presets" duplicates rules (UI, voice and SMS) — one shake toggles the torch twice
**Severity:** 🟠 High · **Category:** Logic / UX · **Confidence:** Confirmed (repro) · **Effort:** S

**Where** defaults `JarvisDatabase.kt:50-141` (10 rules) and `:148-209` (10 SMS rules); UI presets `JarvisViewModel.kt:834-961` (13 rules) and `:963-1032` (10 SMS rules); voice presets `OfflineIntentEngine.kt:1190-1213` (9 rules).
There is no de-duplication and no unique index. **10 of the 13 UI presets duplicate default rules (7 of those are enabled)**, all 9 voice presets do, and every press/voice command adds them again. Consequences: spoken rules fire twice (and `QUEUE_FLUSH` cuts them); **"Shake to Toggle Flashlight (Pocket Protected)" + "Pocket-Guard Shake Flashlight" are both enabled SHAKE → TOGGLE_FLASHLIGHT rules, so one shake toggles the torch twice — net no visible effect** (repro). Duplicate SMS keyword rules only clutter the list (the first match wins, `AutoCallSmsManager.kt:304-315`). `analyzeRuleConflicts` excludes SHAKE and only checks ON-vs-OFF (`SensorConflictEngine.kt:249-262`), so nothing warns.
**Fix** Unique key on `(triggerType, actionType, actionParam, triggerThreshold)` (or a `presetKey` column) with upsert; "Load presets" inserts only the missing ones and reports "3 added, 10 already present"; add duplicate and double-TOGGLE detection to the conflict analysis.

### SENS-03 — Pocket guard compares raw centimetres to 3.0 → shake can be permanently disabled
**Severity:** 🟡 Medium · **Category:** Logic / Device compatibility · **Confidence:** Confirmed (code) · 🧪 Needs device test · **Effort:** S

`SensorHub.kt:252,263`, `SensorConflictEngine.kt:54`. `isInPocket = isProximityNear || proximityDistance < 3.0f`. Many phones expose a binary proximity sensor whose *far* value is 1.0, 3.0, 5.0 or 8.0 (`maximumRange`). On sensors whose *far* reading is ≤ 3 cm (e.g. `maximumRange` 1.0 or 3.0) the device is **always "in pocket"** → shake never fires and the pocket-guarded actions (flashlight, max volume, launch app/camera) are permanently suppressed. Use only `isProximityNear` (already normalised against `maximumRange` at `:187`).

### SENS-04 — Shake threshold: hard-coded detector, silent per-start rewrite of user rules
**Severity:** 🟡 Medium · **Category:** Logic · **Confidence:** Confirmed (code) · **Effort:** S

`SensorHub.kt:253` emits `Shake` only for force ≥ **24.0** regardless of rules; `SensorConflictEngine.kt:127` treats `triggerThreshold < 20` as 26; `JarvisRepository.enforceSafeShakeThreshold` (`:70-86`, called from `JarvisApplication.onCreate`) rewrites every *enabled* SHAKE rule below 24 to 26 **on every app start** (also renames "Shake to Toggle Flashlight…"), silently overriding the user, skipping disabled rules (unsafe when enabled later) and swallowing exceptions. Validate in the form (min/max + message), do a one-time versioned migration instead, and feed the per-rule threshold to the detector.

### SENS-05 — Per-event DB query, event spam, always-on sensors
**Severity:** 🟡 Medium · **Category:** Performance / Battery · **Confidence:** Confirmed (code) · **Effort:** M

`JarvisAutomationService.kt:149-163` runs `repository.getActiveRulesSync()` for **every** event; `SensorHub.kt:166` emits `LightChanged` for any Δ > 3 lux (outdoors or near a window the reading fluctuates by more than that on most reports), so the DB can be queried continuously even when no light rule exists; `:122-142` registers all sensors permanently (accelerometer at `SENSOR_DELAY_UI`) and allocates a `SensorTelemetry` copy per accelerometer sample (16–60 Hz). Cache enabled rules from a `StateFlow`, use relative thresholds + hysteresis (±15 %, ≥ 1 s), register only sensors required by enabled rules, throttle UI telemetry to ~5 Hz, use `maxReportLatency` batching.

### SENS-06 — Screen-off reliability: non-wake-up sensors, no wake lock
**Severity:** 🟡 Medium · **Category:** Reliability · **Confidence:** Code + 🧪 Needs device test · **Effort:** M

`SensorHub.kt:64-66` uses default (non-wake-up) sensors and the service holds no wake lock (only the optional persistent lock or the always-on mic lock). With the screen off and CPU idle, events are batched/suspended, so shake / wave / flip rules are unreliable in exactly the pocket/desk scenarios they target. Use `getDefaultSensor(type, true)` (wake-up) for the sensors rules need, or hold a bounded partial wake lock while rules are armed; expose a "reliability vs battery" setting; add OEM battery-optimisation guidance (Xiaomi/Samsung often kill FGS).

### SENS-07 — Rule actions run sequentially on the collector coroutine
**Severity:** 🟡 Medium · **Category:** Performance / Responsiveness · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisAutomationService.kt:149-163,166-289`: `executeRule` runs inline; `PLAY_MUSIC` blocks up to 2.2 s (`HardwareController.resolveYouTubeVideoId`, `:448-460`) plus network, `LAUNCH_APP` scans installed apps. Meanwhile events queue in `MutableSharedFlow(extraBufferCapacity = 64)` (`SensorHub.kt:77`) and are **dropped** by `tryEmit` when full. Run each action in its own child job on `Dispatchers.IO` with a timeout.

### SENS-08 — Rule results are reported untruthfully; "SOS" is a steady torch
**Severity:** 🟡 Medium · **Category:** Accuracy of result · **Confidence:** Confirmed (code) · **Effort:** S–M

`JarvisAutomationService.kt:190-205`: `MUTE_ALL` logs *"Media & ringtone muted to silent"* and stays `success = true` even if `muteAllAudio()` returned false (no DND access, HW-04); `SET_VOLUME`, `MAX_VOLUME`, `VIBRATE`, `START_LISTENING` never fail. `FLASHLIGHT_SOS` (here and in the UI test, `JarvisViewModel.kt:680-685`) only turns the torch **on steadily**, vibrates and speaks — no SOS/strobe pattern and **no auto-off** (battery, heat), despite the name "Flashlight SOS / Strobe Pulse". The Console shows ✓ for failed or non-existent behaviour. Return an `ActionResult` from `HardwareController` and log/announce it truthfully; implement SOS (`··· −−− ···`, ~200 ms unit, max duration, cancel) or rename the action.

### SENS-09 — HUD mirrors of torch/volume go stale
**Severity:** 🟡 Medium · **Category:** Accuracy of result · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisViewModel.kt:172-176,546-547,1035-1046`, `HardwareController.kt:54,106`: `_flashState`/`_currentVolume` are manual mirrors updated only by ViewModel actions. A torch toggled by a rule, Quick Settings or another app, or volume changed with the hardware buttons, leaves the HUD wrong; `isTorchOn` is never synced (no `CameraManager.TorchCallback`), so `toggleFlashlight()` can "turn off" an already-off torch. Register a `TorchCallback` (expose a `StateFlow`) and observe volume via `ContentObserver`/`VOLUME_CHANGED_ACTION`.

### SENS-10 — `testTriggerRule` re-implements `executeRule`
**Severity:** 🔵 Low · **Category:** Maintainability · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisViewModel.kt:665-747` duplicates the service's action switch (different SOS/mute/volume behaviour), runs on **Main**, ignores results and always logs `isSuccess = true`. Delegate to the shared executor.

### SENS-11 — Orientation classification is portrait-only and un-debounced
**Severity:** 🔵 Low · **Category:** Logic · **Confidence:** Confirmed (code) · **Effort:** S

`SensorHub.kt:220-225`: `UPRIGHT` only when `|y| > 6.5` (landscape → `UNKNOWN`); no hysteresis/dwell (z ≈ ±7 flickers); `FLAT_FACE_UP` needs |x|,|y| < 5, so a phone on a ~35° stand is `UNKNOWN`. Low-pass filter, hysteresis bands, ~500 ms dwell, landscape support.

### SENS-12 — Hand-wave heuristics are prone to false triggers; the preset is enabled
**Severity:** 🔵 Low · **Category:** Logic / Privacy · **Confidence:** Confirmed (code) · **Effort:** S

`SensorHub.kt:171-181,196-211`: the ambient "shadow wave" (drop to < 40 % then recover to ≥ 70 % within 80–1200 ms, previous lux > 30) fires on ordinary lighting changes. The preset *"Wave Hand Voice Command Trigger"* ships `isEnabled = true` (`JarvisViewModel.kt:838-845`) and opens the **microphone** and wakes the screen. Ship it disabled, add a confirm haptic, or require proximity + light agreement.

### 4.8 Telephony, SMS, calls & SOS

### TEL-01 — `RECEIVE_SMS` and `READ_CALL_LOG` are never requested → incoming-SMS and caller features are dead
**Severity:** 🔴 Critical · **Category:** Functionality / Permissions · **Confidence:** Doc-verified + Confirmed (code) · **Effort:** S

**Where** `TelephonyAlarmManager.kt:40-58` (`ALL_PERMISSIONS` — mic, camera, call, phone-state, `SEND_SMS`, `READ_SMS`, contacts, location, notifications, Bluetooth) and `:65-145` (`getAllPermissionsStatus`, 8 rows). `grep` shows `RECEIVE_SMS` and `READ_CALL_LOG` appear **only in the manifest**, nowhere in code.
* `SMS_RECEIVED` is delivered only to apps holding the runtime `RECEIVE_SMS` permission. Since Android 8.0 the system grants **only the permissions explicitly requested** (requesting `SEND_SMS`/`READ_SMS` does not grant `RECEIVE_SMS`). So `JarvisSmsReceiver` never fires: *auto-read SMS aloud*, *auto-reply* and the SMS log do nothing by default.
* Since Android 9 the number in `PHONE_STATE` requires `READ_CALL_LOG` (plus `READ_PHONE_STATE`). Without it `EXTRA_INCOMING_NUMBER` is `null`, the receiver substitutes **"Private Number"** (`JarvisTelephonyReceivers.kt:66`), every call is announced as *"Incoming call from Private Number"*, contact names never resolve, and missed-call auto-replies target `"Private Number"` → "Invalid phone number".
* The Permissions screen lists neither, and Diagnostics shows "SMS & Contacts Access ✓" (SEND_SMS + READ_CONTACTS) although incoming SMS cannot work.
**Fix** Request both (behind the feature toggles *Auto-read SMS*, *Auto-reply*, *Announce calls*) with rationale, list them in the status screen and Diagnostics, and disable the toggles with a "Grant permission" action when missing. *Note:* after granting `READ_CALL_LOG` the system sends `PHONE_STATE` twice (TEL-07). For Play distribution see MAN-02 (SMS/Call-log groups are restricted to default handlers).

### TEL-02 — Emergency SOS: false success, promises a location it never sends, no confirmation
**Severity:** 🔴 Critical · **Category:** Safety / Accuracy · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `AutoCallSmsManager.kt:31,444-453`, `AutoCallSmsView.kt:412-443`, `TelephonyAlarmManager.kt:191-248`, voice trigger `OfflineIntentEngine.kt:275-285`.
* The field is labelled *"Emergency Contact (Name or Phone #)"* and the picker fills digits, but a typed **name** is reduced by `sendSms()` to `""` (`:193`) → "Invalid phone number". `triggerEmergencySos` **ignores the result** and always speaks *"Emergency SOS alert message transmitted to designated contact."*
* The default text is *"EMERGENCY: User requires urgent assistance. **Location coordinates transmitting.**"* — but no location is ever collected or sent (no location API is used anywhere; the location permissions are requested but unused, MAN-01). The recipient is told coordinates are coming when none will.
* One tap on **SOS** sends (no hold-to-confirm, countdown or cancel); the voice command `"sos"` / "send sos" does the same with no confirmation → accidental alerts are easy.
* No delivery tracking, retry, second contact or call fallback; failure is never surfaced.
**Fix**
```kotlin
suspend fun triggerEmergencySos(): SosResult {
    val number = resolveToNumber(config.emergencyContact) ?: return SosResult.Failed("No valid emergency number")
    val fix = runCatching { location.currentOrLast(timeoutMs = 4_000) }.getOrNull()      // only if location is kept
    val body = buildString { append(config.emergencySmsText); fix?.let { append(" https://maps.google.com/?q=${it.latitude},${it.longitude}") } }
    return smsSender.sendAndAwait(number, body)          // sentIntent/deliveryIntent → SENT/DELIVERED/FAILED
        .also { notifyUser(it); log(it) }
}
```
UI: hold-to-activate (3 s ring + vibration + **Cancel**); validate/resolve the contact when it is saved; voice SOS requires a spoken confirmation; never claim "location transmitting" unless it is in the message; retry + notify on failure.

### TEL-03 — Auto-reply answers *anyone* (short codes, banks, OTP) with generic keywords and leaks device state
**Severity:** 🟠 High · **Category:** Privacy / Safety / Cost · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `AutoCallSmsManager.kt:228-256,292-339`, default rules `JarvisDatabase.kt:148-209`, mode switches `OfflineIntentEngine.kt:234-261`.
* No sender filtering: replies go to short codes, alphanumeric IDs (banks/OTP), toll-free/premium numbers and landlines. Replying to spam confirms your number is live and can cost money.
* Default keyword rules are generic (`status`, `help`, `urgent`, `meeting`, `driving`, `good morning`, `call me`, `where are you`; `CONTAINS`): a bank SMS containing "transaction **status**" gets *"Jarvis Diagnostics: Battery at 63%, On battery. Systems nominal."*
* Templates interpolate `{battery} {charging} {lux} {time}` for **any** sender — telemetry leak.
* The voice commands "driving mode" / "meeting mode" silently switch auto-SMS **and** missed-call replies on.
* The 3-minute cool-down is an in-memory per-number map (lost on process death) with no hourly cap.
**Fix** Default to *saved contacts only* (setting); ignore non-numeric senders, short codes and OTP-looking messages; disable telemetry placeholders for unknown senders; persist cool-downs and add a per-hour cap; require explicit confirmation + a visible banner when a mode enables auto-reply.

### TEL-04 — SMS bodies (incl. OTPs) are read aloud by default, even when locked
**Severity:** 🟠 High · **Category:** Privacy · **Confidence:** Confirmed (code) · **Effort:** S

`AutoCallSmsManager.kt:25` (`isAutoReadSmsEnabled = true`), `:279-283`: the full message is spoken (*"Message received from …: <body>"*) through the speaker whenever an SMS arrives. Default it **off**; when on, speak only the sender by default, redact 4–8-digit codes ("a verification code"), and read the body only when unlocked or on a headset, honouring ringer mode/DND. (Also see TEL-01: today it cannot run at all.)

### TEL-05 — Status "DELIVERED" without any delivery report; composer fallback counted as success
**Severity:** 🟠 High · **Category:** Accuracy of result · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `TelephonyAlarmManager.kt:233-236` (`sendTextMessage(…, null, null)`), `AutoCallSmsManager.kt:245-247,335-337`, `ScheduledTaskManager.kt:375-377,409-411,510-570`.
`sendTextMessage` returns immediately; with no `sentIntent`/`deliveryIntent` the app cannot know about no-signal, airplane mode, invalid number or carrier blocks — yet it logs `status = "DELIVERED"` and speaks *"SMS dispatched successfully"*. When `SEND_SMS` is missing or `SmsManager` throws, `sendSmsDirect` opens the SMS composer and returns **success = true** (`:520-526,562-566`); from a background alarm that activity start is blocked (HW-01), so nothing is sent *or* drafted but the task is marked executed.
**Fix** Pass `PendingIntent`s and update statuses `QUEUED → SENT → DELIVERED / FAILED(resultCode)`; the composer fallback becomes a "needs your attention" notification (not success); use wording like "handed to the network" until SENT is confirmed.

### TEL-06 — Multipart SMS is processed as separate messages
**Severity:** 🟡 Medium · **Category:** Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisTelephonyReceivers.kt:26-37` loops over `messages` and treats each PDU as an SMS. Long messages (153 chars/part) and Marathi/UCS-2 messages (67–70 chars/part) are split; each part is logged, announced and keyword-matched separately (a keyword in part 2 is missed by part 1). Join parts per originating address before processing.

### TEL-07 — `PHONE_STATE` handling: duplicates, static state, deprecated API
**Severity:** 🟡 Medium · **Category:** Reliability · **Confidence:** Doc/community-verified + code · 🧪 Needs device test · **Effort:** S–M

`JarvisTelephonyReceivers.kt:44-95`. (a) With both `READ_PHONE_STATE` and `READ_CALL_LOG` granted, the system delivers **two** `RINGING` broadcasts (first without a number) → duplicate announcements and log rows (each is passed to `onCallRinging`). (b) `lastState`/`savedNumber` are **static fields** (`:46-50`) — lost if the process dies between RINGING and IDLE, so missed-call detection silently fails. (c) Rejected calls are treated as "missed" and auto-replied. (d) Prefer `TelephonyCallback` (API 31+)/`CallScreeningService`. Debounce RINGING (≈ 500 ms) preferring the numbered one, and persist state.

### TEL-08 — Receivers do heavy work without `goAsync()`
**Severity:** 🟡 Medium · **Category:** Reliability · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisSmsReceiver.onReceive` (`:18-41`) lazily builds `AutoCallSmsManager` → `JarvisSpeechManager` (TTS + `SpeechRecognizer`) on the main thread of a cold-started process, launches coroutines and returns; the process may be reclaimed before the auto-reply is sent. Use `goAsync()` with a bounded scope (or expedited WorkManager) and do not construct the recogniser for a receiver.

### TEL-09 — Inserts into `content://sms/sent` always fail
**Severity:** 🔵 Low · **Category:** Dead code / Noise · **Confidence:** Confirmed (code) · **Effort:** S

`TelephonyAlarmManager.kt:250-263`, `ScheduledTaskManager.kt:493-508`: only the default SMS app may write the SMS provider (Android 4.4+), so each send throws, is caught and logged. Remove.

### TEL-10 — "Simulate Call / SMS" buttons send real SMS
**Severity:** 🟡 Medium · **Category:** Safety / Cost · **Confidence:** Confirmed (code) · **Effort:** S

`AutoCallSmsView.kt:739-759` (`+15550199`, "Tony Stark") → `simulateIncomingSms` → `onSmsReceived`: with auto-SMS enabled it **sends a real SMS** to that international number; a simulated missed call with missed-call replies on does too. Make simulation a dry-run (inject a fake `SmsSender`) or debug-only.

### TEL-11 — One-tap Call / SMS in the contacts directory sends "Hello from Jarvis" immediately
**Severity:** 🟡 Medium · **Category:** UX / Safety · **Confidence:** Confirmed (code) · **Effort:** S

`SystemCommsScreen.kt:249-250,1302-1325`: two adjacent icon buttons — **Call** dials at once, **SMS** sends the canned text at once. Add a confirm/compose step and separate the targets.

### TEL-12 — Contact search: main-thread queries, per-keystroke, alphabetical "first match"
**Severity:** 🟡 Medium · **Category:** Performance / Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`TelephonyAlarmManager.kt:372-470`, `JarvisViewModel.kt:203,216-223`. `loadContacts` runs `ContentResolver` queries on `viewModelScope` (**Main**): twice at start-up (`refreshPermissions()` inside `init` *and* an explicit call right after it), again on every permission result and on **every keystroke** of the search box (no debounce). Results are sorted by name, not relevance. `lookupContactNameByNumber`'s fallback `NUMBER LIKE %last10%` misses formatted numbers ("+91 98765 43210"). `enrichExistingLogsWithContactNames` (`AutoCallSmsManager.kt:81-86,393-409`) re-queries every unknown number on each launch (≤ 100 logs × 2 queries, triggered from both the manager and the ViewModel) and never caches negative lookups.
**Fix** IO dispatcher, 250 ms debounce, relevance ranking, negative caching by normalised number, `PhoneNumberUtils.compare`.

### 4.9 Scheduled tasks

### SCH-01 — A scheduled SMS can run twice (cold-start race, manual "run now")
**Severity:** 🟠 High · **Category:** Logic / Data integrity · **Confidence:** Confirmed (code) · **Effort:** M

**Where** `ScheduledTaskManager.kt:292-473,624-649`; `JarvisApplication.kt:177-183`; `BootCompletedReceiver.kt:31`; `ScheduledTaskReceiver.kt:33-63`; `JarvisViewModel.kt:351-356`.
1. **Cold-start race.** When an exact alarm wakes a dead process, `Application.onCreate` launches `rescheduleAllPendingTasks()` (`:181`) *while* `ScheduledTaskReceiver` concurrently runs `executeTask()`. `rescheduleAll…` reads the still-`PENDING`, now-due task, computes the *next* trigger (a `ONCE` task → tomorrow), writes `task.copy(scheduledTimeMillis = …)` and arms another alarm; `executeTask` writes `EXECUTED` from its earlier read. The last write wins: either the row reverts to `PENDING` (fires again tomorrow) or it stays `EXECUTED` with an alarm armed for tomorrow — and `executeTask` rejects only `!isEnabled || CANCELLED` (`:298`), **not `EXECUTED`** → **a duplicate SMS the next day**.
2. Manual **"Execute now"** on a `ONCE` task (`JarvisViewModel.kt:351-356`) marks it `EXECUTED` but never cancels the still-armed alarm → it runs again at the original time.
3. `BootCompletedReceiver` calls `rescheduleAllPendingTasks()` without `goAsync()`, so the process may be reclaimed mid-way.
**Fix**
```kotlin
@Query("UPDATE scheduled_tasks SET status='RUNNING' WHERE id=:id AND status='PENDING' AND scheduledTimeMillis<=:now")
suspend fun claim(id: Long, now: Long): Int          // rows == 1 → this caller owns the run
// executeTask: if (repository.claim(id, now) == 0) return false
```
Call `rescheduleAll` only for boot / package-replaced / time-change and skip tasks due within a grace window (never from `Application.onCreate`); cancel the alarm after a manual run of a `ONCE` task; use `goAsync()` in the boot receiver.

### SCH-02 — Gemini tasks run inside a broadcast window → ANR risk
**Severity:** 🟠 High · **Category:** Reliability · **Confidence:** Confirmed (code) · **Effort:** M

`ScheduledTaskReceiver.kt:42-63` holds the broadcast (`goAsync()`) with a 60 s wake lock while `executeTask` awaits `queryAssistant` — up to *minutes* (AI-03). Receivers have a limited window (about 10 s for foreground and ~60 s for background broadcasts) → ANR/"app isn't responding". Add a hard `withTimeout(40 s)` **and** move Gemini work to WorkManager (expedited) or a short foreground service; finish the broadcast immediately.

### SCH-03 — Overdue and failed tasks are mishandled; time/zone changes ignored
**Severity:** 🟠 High · **Category:** Logic · **Confidence:** Confirmed (code) · **Effort:** M

`ScheduledTaskManager.kt:624-649`, `JarvisDao.kt:96`.
* A `ONCE` task missed while the device was off (or during an app update) is **silently pushed to tomorrow at the same time** (`calculateNextTriggerMillis` adds a day) — a day-late birthday SMS — instead of running within a grace window or being flagged *missed*.
* Recurring tasks whose last run failed get `status = "FAILED"` but the alarm stays armed (`:456-459`); after a reboot they are not re-armed because the query filters `status = 'PENDING'` → the schedule silently stops.
* No receiver for `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`, so `LOCAL`-zone tasks are wrong after travel or a clock change.
**Fix** Grace window (≈ 15 min) then a `MISSED` state + notification; re-arm failed recurring tasks; register time/timezone receivers.

### SCH-04 — Exact-alarm handling is silent; Play policy; Doze
**Severity:** 🟡 Medium · **Category:** Reliability / Policy · **Confidence:** Doc-verified + code · **Effort:** S–M

`ScheduledTaskManager.kt:221-268`: when `canScheduleExactAlarms()` is false the code silently falls back to `setAndAllowWhileIdle` (inexact — can be minutes late in Doze) while the confirmation shows an exact time; there is no `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` flow. On Android 14+ `SCHEDULE_EXACT_ALARM` is **denied by default** for new installs; the manifest also declares `USE_EXACT_ALARM` (auto-granted on 13+, masking the problem locally) which Play restricts to alarm/calendar apps (MAN-02). Short repeats (5/15 min) are throttled by Doze. Detect, explain and deep-link to "Alarms & reminders", listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, document Doze limits, prefer WorkManager for ≥ 15 min repeats.

### SCH-05 — Recurrence semantics and no spam guard; new SMS tasks default to "hourly forever"
**Severity:** 🟡 Medium · **Category:** Logic / Safety · **Confidence:** Confirmed (code) · **Effort:** S–M

`calculateNextTriggerMillis` (`:69-107`): `EVERY_X_MINUTES`/`HOURLY` are rescheduled from `now` (drift = run latency each cycle, `minute` ignored for HOURLY); only two zones are supported (any other id silently uses the device zone). There is **no minimum interval and no repetition cap for SMS tasks**: the form offers *"Every 5 Min"* (`ScheduledTasksView.kt:503-508`) and its default repeat is **HOURLY** (`:103`), so a new *Auto SMS* repeats every hour indefinitely — cost, harassment and carrier-limit risk. Default to `ONCE`; minimum 60 min for SMS; add end date/max runs; anchor `next = previousScheduled + interval`.

### SCH-06 — Tasks with an unresolved recipient are created and armed
**Severity:** 🟡 Medium · **Category:** Validation · **Confidence:** Confirmed (code) · **Effort:** S

`createAndScheduleTask` (`:121-144`) checks `targetPhoneNumber.isBlank()` **before** resolving names; `resolveTargetPhoneNumber` (`:475-491`) strips letters when no contact matches, so `cleanTargetPhone == ""` and the task is armed and fails at run time (*"Invalid recipient"*). Validate after resolution; ambiguous names as in INTENT-04.

### SCH-07 — Default time can be in the past; IST hard-coded for every user
**Severity:** 🔵 Low · **Category:** UX / Logic · **Confidence:** Confirmed (code) · **Effort:** S

`ScheduledTasksView.kt:98-102`: `selectedMinute = (now.minute + 10) % 60` with `selectedHour = HOUR_OF_DAY` wraps into the past after :50; the default zone is `"Asia/Kolkata"` for everyone (`:98`, `ScheduledTask.kt:18`, `JarvisViewModel.kt:295`) while voice-created tasks default to `LOCAL` (inconsistent). Default to the device zone; carry the hour.

### SCH-08 — Stale alarms after "Delete all"; noisy lock-screen notifications
**Severity:** 🔵 Low · **Category:** Hygiene / Privacy · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisViewModel.kt:366-373` cancels alarms only for *pending* tasks (others leak PendingIntents; the receiver tolerates missing rows). `postExecutionNotification` (`ScheduledTaskManager.kt:572-595`) shows recipient + full message on the lock screen in a **HIGH-importance** channel (heads-up on every run of a 5-minute task). Cancel by id list; use `VISIBILITY_PRIVATE` with a redacted public version; group repeats.

### 4.10 Data layer (Room)

### DB-01 — Destructive migration wipes user data and (by design) skips re-seeding
**Severity:** 🟠 High · **Category:** Data loss · **Confidence:** Doc-verified + Confirmed (code) · **Effort:** M

**Where** `JarvisDatabase.kt:12-16,26-32`: `version = 4`, `exportSchema = false`, `.fallbackToDestructiveMigration(dropAllTables = false)`, no `Migration`s.
Any future entity/column change (the app already went through 4 versions) **deletes every user rule, scheduled task, auto-SMS rule and log** on update. Room does **not** call `Callback.onCreate` after a destructive migration (only `onDestructiveMigration`), so even the default rules are not re-seeded → an empty app. `exportSchema = false` also prevents migration testing.
**Fix**
```kotlin
@Database(entities = [...], version = 5, exportSchema = true, autoMigrations = [AutoMigration(from = 4, to = 5)])
...
Room.databaseBuilder(...).addMigrations(/* manual ones */)      // no destructive fallback in release
    .addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = seed(db)                  // synchronous, inside the create transaction
        override fun onDestructiveMigration(db: SupportSQLiteDatabase) { needsSeed = true }   // debug builds only
        override fun onOpen(db: SupportSQLiteDatabase) { if (needsSeed) seed(db) }
    })
// build.gradle.kts: ksp { arg("room.schemaLocation", "$projectDir/schemas") }  + commit /schemas
```

### DB-02 — Unbounded tables and missing indices
**Severity:** 🟡 Medium · **Category:** Storage / Performance / Privacy · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisDao.kt:38,48,58-61` only `LIMIT` on read; nothing ever prunes `execution_logs` (a light rule can fire thousands of times), `command_history` or `call_sms_logs` (stores full SMS bodies **indefinitely**). `ORDER BY timestamp DESC LIMIT n` has no index → scan + sort as tables grow. Add `@Entity(indices = [Index("timestamp")])`, prune on insert/daily (e.g. 500 rows / 30 days) and a retention setting.

### DB-03 — Seeding is asynchronous and non-transactional; defaults ship *enabled*
**Severity:** 🟡 Medium · **Category:** Reliability / UX · **Confidence:** Confirmed (code) · **Effort:** S

`JarvisDatabase.kt:38-49`: `onCreate` launches `populateInitialRules` on `Dispatchers.IO` *after* creation, one insert per statement; if the process dies part-way the DB stays partially seeded and `onCreate` never runs again; the first reads (and the service on a fresh install) may see zero rules. 7 of the 10 seeded rules are **enabled** (e.g. *Face-Down → mute*, *Face-Up → volume 75 %*) with no onboarding (UX-12). Seed synchronously inside `onCreate` (`db.insert`) or `withTransaction`, use unique keys / `INSERT OR IGNORE`, and let first-run onboarding opt the user in.

### DB-04 — Minor data-layer issues
**Severity:** 🔵 Low · **Category:** Maintainability / Privacy · **Confidence:** Confirmed (code) · **Effort:** S–M

`getDatabase` uses double-checked locking without the inner re-check (`:25-34`) — latent (single caller today); `OnConflictStrategy.REPLACE` on auto-increment entities; stringly-typed `triggerType/actionType/status/taskType` (a typo silently no-ops — use enums + `TypeConverter`); plaintext PII (SMS bodies, numbers) at rest — consider field encryption/SQLCipher plus retention (DB-02).

### 4.11 Hardware layer

### HW-01 — Hands-free launches are silently blocked by Background Activity Launch rules — yet reported as success
**Severity:** 🟠 High · **Category:** Accuracy of result / Platform limits · **Confidence:** Doc-verified + Confirmed (code) · 🧪 Needs device test · **Effort:** M

**Where** every `startActivity(...)` — 22 call sites in `HardwareController.kt` (`:211-968`), 9 in `TelephonyAlarmManager.kt` (`:159,166,289,491,516,529,542,567,581`), `OfflineKnowledgeEngine.kt:628`, `JarvisAutomationService.kt:96`.
Since Android 10 apps cannot start activities from the background, and a **foreground service is *not* an exemption** (exemptions: visible window, IME, system-sent `PendingIntent` such as a notification tap, `SYSTEM_ALERT_WINDOW`, …; Appendix A). Voice- or sensor-triggered actions that run while the screen is off or the app is backgrounded — open app, dial, YouTube/VLC/Spotify, camera, alarm/timer/calendar UI, settings, "go home" — call `startActivity` from an Application/Service context; the launch is dropped (logcat: *"Background activity start"*), but the code returns success and the assistant says *"Opening YouTube now, sir."* In addition, `ACTION_VOICE_TRIGGER` (`JarvisAutomationService.kt:87-98`) starts the Activity from a Service started by a notification action, which the Android 12 **notification-trampoline** restriction blocks.
**Fix**
1. Return truthful results: verify the target became foreground (or track the rejection) and otherwise answer *"I can't open apps while the phone is locked — unlock, or enable 'Display over other apps'."*
2. Use sanctioned mechanisms: `TelecomManager.placeCall()` for calls (works from the background with `CALL_PHONE`), `AlarmManager`/`setAlarmClock` for alarms, a full-screen-intent notification for urgent UI, or `SYSTEM_ALERT_WINDOW` if an overlay is acceptable.
3. Start Activities with `PendingIntent.getActivity` from the notification directly, not via a service trampoline.

### HW-02 — YouTube scraping with a spoofed browser User-Agent
**Severity:** 🟡 Medium · **Category:** Policy / Reliability · **Confidence:** Confirmed (code) · **Effort:** M

`HardwareController.kt:448-504,608-651`: scrapes `https://www.youtube.com/results?search_query=…` with a desktop-Chrome User-Agent (`:473`) and regex-extracts the first `"videoId"`. This violates YouTube's terms (automated scraping), is brittle (consent redirects, layout changes, first id may be an ad/Short/live), blocks the caller up to 2.2 s (`FutureTask` + a raw `Thread` per call that is not cancelled on timeout) and runs on **Main** via the ViewModel command path (PERF-01). The player list puts **ReVanced/Vanced** (unofficial patched clients; Vanced is discontinued) *before* the official app (`:608-615`, `<queries>`).
**Fix** Remove scraping; use `MEDIA_PLAY_FROM_SEARCH` to the user's default/official app, or the YouTube Data API (key + quota) if direct play is required; drop Vanced/ReVanced; official app first.

### HW-03 — "Play local song" can never find files; `JarvisAudioPlayer` problems
**Severity:** 🟡 Medium · **Category:** Functionality · **Confidence:** Confirmed (code) · **Effort:** S–M

`findLocalAudioUri` (`HardwareController.kt:390-419`) queries `MediaStore.Audio`, but the manifest declares **no `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE`** → other apps' media is invisible, so local search never matches (and the *auto* path burns a query first, `:549-558`). `JarvisAudioPlayer`: blocking `prepare()` (`:35`), listeners installed *after* `start()` (`:37-46`), no audio focus (VOICE-05), `MediaPlayer` kept after completion, touched from several threads (TTS callbacks call `duckVolume`) without confinement; `LIKE %query%` isn't escaped. Add the right permission (runtime) or remove the feature; `prepareAsync`, focus handling, cleanup on completion.

### HW-04 — Mute / ringer / DND: never checked, partial application, side-effects, rounding
**Severity:** 🟡 Medium · **Category:** Logic / Accuracy · **Confidence:** Confirmed (code + repro) · **Effort:** M

`HardwareController.kt:109-186`.
* Nowhere is `NotificationManager.isNotificationPolicyAccessGranted` checked or the user sent to `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` (grep: no matches). Without DND access, setting the ringer to SILENT (and commonly the notification stream to 0) throws `SecurityException`; `muteAllAudio()` mutes `STREAM_MUSIC` first, then throws → **partial mute + `false`** (which the Application path then sends to Gemini, LIFE-03). `ACCESS_NOTIFICATION_POLICY` is declared but unused.
* `setMaxVolume()` forces the ringer to NORMAL (`:157`): a *media* "max volume" command can un-silence the ringer in a meeting; the preset *Outdoor Sunlight Sound Boost* (light → MAX_VOLUME) can blast headphones.
* `setMediaVolumePercent` truncates with `toInt()` (`:112`): asking 50 % on a 15-step stream sets step 7 → reads back 46 % while UI/TTS say 50 % (repro, Appendix B).
**Fix** DND-access onboarding; snapshot/restore (INTENT-12); `roundToInt()`; cap at a safe level when a wired/BT headset is connected; never touch the ringer in `setMaxVolume`.

### HW-05 — Audio-focus leak on STOP; media-key results are always "success"
**Severity:** 🟡 Medium · **Category:** Logic · **Confidence:** Confirmed (code) · **Effort:** S

`controlMedia(STOP)` requests `AUDIOFOCUS_GAIN_TRANSIENT` with a `null` listener and never abandons it (`HardwareController.kt:901-909`) — apps that lost focus wait forever. `dispatchMediaKeyEvent` cannot tell whether any session consumed the key, but the function returns `Pair(true, "Playback paused")` (`:911-919`); `PLAY` resumes the internal player **and** sends a system PLAY key, so two sources may play. Abandon focus after a short delay, and report "sent" rather than "paused".

### HW-06 — Fabricated battery value (85 %)
**Severity:** 🔵 Low · **Category:** Accuracy · **Confidence:** Confirmed (code) · **Effort:** S

`HardwareController.getBatteryLevel()` returns `85` on null/exception (`:842-848`; currently unused) and `BatteryManager.getIntProperty` returns `Integer.MIN_VALUE`/`0` on unsupported devices without validation. Return a nullable/`Result` (see also SENS-02).

### 4.12 Security & privacy

### SEC-01 — UI is forced over the lock screen (and wakes the screen) with no way to turn it off
**Severity:** 🟠 High · **Category:** Security / Privacy · **Confidence:** Confirmed (code) · **Effort:** S

**Where** `MainActivity.kt:49-64`; `JarvisViewModel.kt:181-188` (a setter exists, but `setShowAboveLockscreen` is **not referenced by any UI** — grep); `JarvisAutomationService.kt:341,367` (notification + channel `VISIBILITY_PUBLIC` with Mic/Play/Next/Stop actions).
`showAboveLockscreen` defaults to **`true`** and there is no toggle, so every user gets `setShowWhenLocked(true)` + `setTurnScreenOn(true)`. If Jarvis is the foreground app when the device locks or is woken, its UI appears **over the lock screen without authentication** — SMS/call logs (with message bodies), contacts, one-tap call/SMS, SOS, scheduled tasks, Diagnostics (API-key editor, masked). `setTurnScreenOn(true)` also lights the display whenever the Activity is (re)started.
**Fix** Default `false` + a Settings switch. If kept, show only the HUD (orb + last reply) above the keyguard and redact/lock the sensitive tabs until `KeyguardManager.isKeyguardLocked == false` (or call `requestDismissKeyguard`); `setRecentsScreenshotEnabled(false)`/`FLAG_SECURE` on sensitive screens; foreground-service notification `VISIBILITY_PRIVATE` with a public version and `setAuthenticationRequired(true)` on the Mic/Stop actions (API 31+).

### SEC-02 — Phone numbers and SMS bodies are written to logcat in release builds
**Severity:** 🟠 High · **Category:** Privacy · **Confidence:** Confirmed (code) · **Effort:** S

`AutoCallSmsManager.kt:158,200,266,325`, `JarvisTelephonyReceivers.kt:35,67`, `ScheduledTaskManager.kt:559`, `TelephonyAlarmManager.kt:329,358`. Example: `Log.i(TAG, "Incoming SMS from $number ($contactName): $messageBody")` — the full text (OTPs, banking) plus number and contact name at INFO. The release type has `isMinifyEnabled = false` and no log stripping (211 `Log.*` calls in total), so this is readable via `adb logcat`/bug reports.
**Fix** Remove PII from messages; route through a logging facade that is a no-op in release (Timber/`if (BuildConfig.DEBUG)`); add R8 `-assumenosideeffects class android.util.Log { v(...); d(...); i(...); }` after enabling minification (BLD-01).

### SEC-03 — Backup rules are inconsistent; `allowBackup="true"`
**Severity:** 🟡 Medium · **Category:** Privacy · **Confidence:** Confirmed (code) · **Effort:** S

`res/xml/data_extraction_rules.xml`: `<cloud-backup>` excludes the DB and three prefs files, but `<device-transfer>` excludes **only** `jarvis_gemini_prefs.xml` → the Room DB (SMS bodies, numbers), `jarvis_context_memory.xml` (conversation) and `jarvis_auto_call_sms_prefs.xml` (emergency contact) are copied device-to-device. Mirror the exclusions in `<device-transfer>` (and add `jarvis_core_db-journal`) or set `allowBackup="false"`.

### SEC-04 — Signing/keystore hygiene; a fresh clone may not build the debug variant
**Severity:** 🟡 Medium · **Category:** Security / Build · **Confidence:** Confirmed (code) + 🧪 verify · **Effort:** S

`app/build.gradle.kts:26-40`: the release config reads `KEYSTORE_PATH` or `${rootDir}/my-upload-key.jks` — **a key file inside the repository tree** — while `.gitignore` ignores only `debug.keystore` and `*.apk`, **not** `*.jks`/`*.keystore`; a dropped-in upload key can be committed by accident. `debugConfig` requires `${rootDir}/debug.keystore` (git-ignored) with `android/android`, so a fresh clone cannot `assembleDebug` until that file exists (AGP: "Keystore file … not found").
**Fix** Ignore `*.jks`, `*.keystore`, `keystore.properties`; make debug signing conditional (`if (file.exists())`) or use the default debug keystore; document key generation; use Play App Signing.

### SEC-05 — Lock-screen notification content
**Severity:** 🔵 Low · **Category:** Privacy · **Confidence:** Confirmed (code) · **Effort:** S

The service notification shows *"Last action: <rule name>"* publicly (`JarvisAutomationService.kt:285,341`) and scheduled-task notifications show recipient + message (SCH-08). Use `VISIBILITY_PRIVATE` with redacted public versions.

### 4.13 Manifest, permissions & store policy

### MAN-01 — A dozen declared permissions are unused; UI descriptions are false
**Severity:** 🟠 High · **Category:** Privacy / Trust · **Confidence:** Confirmed (code) · **Effort:** S

Full table in **Appendix C**. Declared but never used by code: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`, `READ_CALENDAR`, `WRITE_CALENDAR`, `WRITE_CONTACTS`, `WRITE_SETTINGS`, `READ_SMS`, `CAMERA`, `FLASHLIGHT` (the calendar is opened through intents; the torch uses `CameraManager.setTorchMode`, which needs no permission). Yet the app **prompts** for camera, location and Bluetooth (`ALL_PERMISSIONS`, `TelephonyAlarmManager.kt:40-58`) and describes them falsely: *"Camera & Torch — required for flashlight control…"*, *"GPS Location — enables location-aware responses, telemetry…"* (`:104-120`). `BLUETOOTH`/`BLUETOOTH_ADMIN` lack `android:maxSdkVersion="30"`; `BLUETOOTH_SCAN` lacks `usesPermissionFlags="neverForLocation"`.
**Fix** Remove the unused permissions; request the rest just-in-time with accurate rationale; if you implement SOS location (TEL-02) keep *one* location permission and say why.

### MAN-02 — Store-policy blockers (SMS/Call-log groups, exact alarms, FGS special-use)
**Severity:** 🟠 High · **Category:** Policy · **Confidence:** Doc-verified · **Effort:** decision

* Google Play restricts the **SMS** (`READ_SMS`, `SEND_SMS`, `RECEIVE_SMS`…) and **Call Log** (`READ_CALL_LOG`…) permission groups to the *default SMS / Phone / Assistant handler*: *"Apps lacking default handler capability may not declare use of the above permissions in the manifest."*
* `USE_EXACT_ALARM` is a Play-reviewed permission for alarm-clock/calendar apps; `SCHEDULE_EXACT_ALARM` is denied by default on Android 14+.
* `FOREGROUND_SERVICE_SPECIAL_USE` needs a Play Console declaration/review.
As designed, the app is **sideload/personal-use only**. To publish, either become the default SMS/Assistant role (`RoleManager`), redesign around user-consent flows/Notification Listener, or drop those features. Make this a conscious decision before investing in the SMS/call automation.

### MAN-03 — Manifest/theme polish
**Severity:** 🔵 Low · **Category:** Maintainability · **Confidence:** Confirmed (code) · **Effort:** S

`Theme.MyApplication` extends `android:Theme.DeviceDefault.NoActionBar` (OEM-dependent window background before Compose draws — possible flash 🧪; prefer an explicit dark `windowBackground`/splash theme); orientation is not restricted although layouts are portrait-designed (UX-01, UX-04); `<queries>` lists ReVanced/Vanced/NewPipe (HW-02); template names `com.example` / `MyApplicationTheme` (BLD-05).

### 4.14 Build, dependencies, tests & docs

### BLD-01 — Release build is unshrunk and carries unused/debug dependencies and a 1 MB icon
**Severity:** 🟡 Medium · **Category:** APK size / Security / Startup · **Confidence:** Confirmed (code) · **Effort:** M

`app/build.gradle.kts:42-49` `isMinifyEnabled = false` (no `isShrinkResources`; `:88` extended icons, `:113-114` App Check deps), `proguard-rules.pro` is the empty template. Consequences: the full **`material-icons-extended`** library ships in the APK (several MB of dex), no log stripping (SEC-02), no obfuscation. `firebase-appcheck-recaptcha` and **`firebase-appcheck-debug`** (a *debug* provider) are `implementation` dependencies of **release**, plus the Firebase BoM and `google-services` plugin, but there is no `google-services.json` (`missingGoogleServicesStrategy = WARN`) and no Firebase code. `res/drawable/ic_jarvis_core.jpg` is **1024×1024, 968 KB** (~4 MB decoded) used as the adaptive-icon foreground *and* as the `<monochrome>` layer — an opaque JPEG, so themed icons render as a solid square.
**Fix** Enable R8 + shrinkResources with keep rules (Room, `org.json` use), replace extended icons with the ~25 used vectors (or rely on R8), drop unused Firebase deps (or `debugImplementation` for App Check debug), replace the JPEG with a vector/WebP (432×432 foreground + a real monochrome vector).

### BLD-02 — Outdated dependencies and an over-full version catalog
**Severity:** 🟡 Medium · **Category:** Maintainability / Security · **Confidence:** Confirmed (code) + Doc-verified · **Effort:** S–M

`gradle/libs.versions.toml`: Compose BOM **2024.09.00** (Sep 2024; the April 2026 release is BOM 2026.04.01 — Compose 1.11), OkHttp **4.10.0** (2022; 4.12.0 is the last 4.x, 5.x is stable), lifecycle 2.8.7, activity-compose 1.10.1, Room 2.7.0. The catalog declares many unused libraries (coil, retrofit, moshi, CameraX, DataStore, accompanist, credentials…). Enable Renovate/Dependabot, bump, and prune the catalog. Note Compose 1.12 requires compileSdk 37 / AGP 9.

### BLD-03 — Tests don't cover the code where the bugs are
**Severity:** 🟡 Medium · **Category:** Quality · **Confidence:** Confirmed (code) · **Effort:** L

Unit tests: one file (`ExampleUnitTest.kt`, 437 lines) of enum/constant asserts, `2 + 2`, knowledge-engine happy paths, wake-word on *clean* input, and the circuit breaker; `ExampleRobolectricTest`/`ExampleInstrumentedTest` are templates; `GreetingScreenshotTest` records `src/test/screenshots/greeting.png` into the source tree with no verify task. **No behavioural tests** for `OfflineIntentEngine` (1,495 lines), `SensorConflictEngine`, `ScheduledTaskManager` (`calculateNextTriggerMillis`), `AutoCallSmsManager` (only its config defaults and preset strings are asserted), `TelephonyAlarmManager`, Room DAOs/migrations, the ViewModel or any screen (except one top-bar screenshot). Some tests assert literal model IDs/counts (AI-05) and will break on catalog updates without catching defects. See §6 for a concrete plan seeded with the reproductions in Appendix B.

### BLD-04 — No README, CI or static analysis
**Severity:** 🔵 Low · **Category:** Process · **Confidence:** Confirmed · **Effort:** S

There is no README (build steps, `.env`/API-key setup, permissions rationale, sideload-only note), no CI, no lint baseline/detekt/ktlint. Add a GitHub Actions job: `./gradlew testDebugUnitTest lintDebug assembleDebug`, plus Dependabot/Renovate.

### BLD-05 — No localization; template names
**Severity:** 🔵 Low · **Category:** Maintainability · **Confidence:** Confirmed (code) · **Effort:** M

0 `stringResource` usages; ≥ 79 literal `Text("…")` plus hundreds of inline English/Marathi strings in engines → no translation workflow and mixed-language UI/TTS. Template names remain (`namespace com.example`, `MyApplicationTheme`, `Theme.MyApplication`, `ExampleUnitTest`); the `applicationId` (`com.aistudio.jarvisauto.kzrxn`) is permanent once published — decide before release. Move UI text to `strings.xml` / `values-mr/`.

### 4.15 UI / UX

### UX-01 — All UI state is lost on rotation (no `rememberSaveable` anywhere)
**Severity:** 🟠 High · **Category:** UX · **Confidence:** Confirmed (code) · **Effort:** S–M

`rememberSaveable` appears **0 times** in the UI; every form, dialog, filter and scroll uses `remember`. Examples: the HUD command field (`CoreHudScreen.kt:138-139`); the Add-Rule dialog fields (`AutomationsScreen.kt:916-919`) — the ViewModel keeps `showAddRuleDialog = true`, so the dialog reopens **blank**; the scheduled-task form (`ScheduledTasksView.kt:98-104`); the Console tab; the SMS composer. Combined with LIFE-01 the rotation experience is broken. Use `rememberSaveable` (or hoist to the ViewModel with `SavedStateHandle`); consider locking portrait until done.

### UX-02 — Tiny text, low contrast and sub-48 dp touch targets
**Severity:** 🟠 High · **Category:** Accessibility / Readability · **Confidence:** Confirmed (code + computed) · **Effort:** M

* **Font sizes**: 184 of the 331 explicit `fontSize` literals (**56 %**) are ≤ 10 sp — `7.5 sp`×3, `8 sp`×26, `8.5 sp`×14, `9 sp`×53, `9.5 sp`×11, `10 sp`×77 — while `Type.kt` claims *"minimum 11sp"*; screens bypass `MaterialTheme.typography`.
* **Contrast** (WCAG relative-luminance, computed): `TextMuted #64748B` on `SurfaceDark` = **3.83 : 1**, on `SurfaceVariantDark` = **3.50 : 1**, on `ObsidianDark` = 4.14 : 1 — all below AA 4.5 : 1 for normal text, yet `TextMuted` is used for 8–10 sp labels; `NeonRed` on `SurfaceVariantDark` = 4.42 : 1.
* **Touch targets**: top-bar pills ≈ 24–36 dp, HUD buttons 38 dp, `CLEAR` ≈ 15 dp high, `DISMISS`/`MORE ▼` text buttons with 2–4 dp padding, time-stepper buttons 24 dp; the source even comments *"48dp accessible touch target"* on a 38 dp box (`CoreHudScreen.kt:359`) and *"48dp minimum"* on a ≈ 29 dp pill (`ArcReactorCore.kt:222`).
**Fix** ≥ 12 sp for body/labels (11 sp absolute minimum), use theme typography, lighten `TextMuted` to ≥ 4.5 : 1 (≈ `#8FA0B8`), `Modifier.minimumInteractiveComponentSize()` (48 dp) on every clickable, and honour the system font scale (test at 200 %).

### UX-03 — Accessibility semantics are missing
**Severity:** 🟡 Medium · **Category:** Accessibility · **Confidence:** Confirmed (code) · **Effort:** M

66 `Modifier.clickable` usages, **0** with a `role`; custom toggles (service, cloud, wake-word lock, fallback) have no `stateDescription`/`toggleable`; 53 icons have `contentDescription = null` (4 are *inside* `IconButton`s — the time steppers, `ScheduledTasksView.kt:1387-1399`); the Arc-Reactor orb (the main control) has no description and the mic icon says a static "Microphone State"; section titles are not marked as headings; several `LazyColumn`/`LazyRow` lists lack keys (PERF-06). Use `Button`/`IconButton`/`Switch` or `clickable(role = Role.Button, onClickLabel = …)`/`toggleable`, add `semantics { heading() }`, and run Accessibility Scanner/`AccessibilityChecks`.

### UX-04 — System bars and keyboard insets
**Severity:** 🟡 Medium · **Category:** UX · **Confidence:** Doc/community-verified + code · 🧪 Needs device test · **Effort:** S

`MainActivity.kt:47` calls `enableEdgeToEdge()` with defaults (`SystemBarStyle.auto`): icon colour follows the **device's** light/dark setting while the app is always dark (`Theme.kt:30-38` ignores `darkTheme`) → on a light-mode device the status/navigation icons are dark on `SurfaceDark`. There is **no `imePadding()`/`WindowInsets.ime`** anywhere (grep), `Scaffold(contentWindowInsets = WindowInsets(0,0,0,0))`, and the manifest relies on `adjustResize` — under edge-to-edge `adjustResize` no longer resizes content, so the HUD "Ask Jarvis" field, SMS composer and forms at the bottom can be hidden behind the keyboard.
**Fix**
```kotlin
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
)
// root content: Modifier.fillMaxSize().imePadding()   (+ consumeWindowInsets where appropriate)
```

### UX-05 — Status indicators state things that are not true
**Severity:** 🟡 Medium · **Category:** Accuracy of result / Trust · **Confidence:** Confirmed (code) · **Effort:** S

1. `NavigationAndHeader.kt:339-356` — hard-coded green **"DSP: ON"** (VOICE-03).
2. `SensorsScreen.kt:92-106` — hard-coded **"✓ CONFLICT SAFE"**; the `conflictWarnings` parameter (`:59`) is never read, although the Rules tab lists warnings.
3. **"WAKELOCK: ON"** latched after the first command (LIFE-08).
4. **"JARVIS WAKE-WORD LOCK (STRICT)"** overclaims (VOICE-01/07).
5. **"NOISE: xx dB" / "VOCAL 98 %"** are synthetic (VOICE-03).
6. Diagnostics: `testMicrophoneHardware` returns **`true`** when `getMinBufferSize ≤ 0` and in the `catch` (`VoiceDiagnostics.kt:212,225`), so "AudioRecord stream successfully allocated" can be shown after a failure (and reports failure while the app itself holds the mic); "Network ✓" ignores captive portals (no `NET_CAPABILITY_VALIDATED`); **"Re-init Voice"** only speaks a test phrase (`VoiceDiagnosticsDialog.kt:991`); "ALL SYSTEMS OPERATIONAL" is unreachable without an API key/network although offline is a supported state (treat those as *warnings*).
7. "SMS & Contacts Access ✓" while `RECEIVE_SMS` is missing (TEL-01); false permission descriptions (MAN-01).
Bind badges to real state or remove them; add a *warning* level distinct from *failed*.

### UX-06 — Destructive / costly actions have no confirmation or undo
**Severity:** 🟡 Medium · **Category:** UX / Safety · **Confidence:** Confirmed (code) · **Effort:** S

**SOS** (`AutoCallSmsView.kt:435-442`), **PURGE** logs/history (`ConsoleLogsScreen.kt:98-125`), clear call/SMS logs (`AutoCallSmsView.kt:801`), one-tap Call/SMS (TEL-11), **LOAD PRESETS** (RULE-01), tapping the context badge clears conversation memory (`CoreHudScreen.kt:553-571`). (The scheduler *does* confirm deletions — use the same pattern.) Add confirm dialogs or a Snackbar with **Undo**.

### UX-07 — Rules screen defects
**Severity:** 🟡 Medium · **Category:** Bugs · **Confidence:** Confirmed (code) · **Effort:** S

* The **Gestures** filter is `SHAKE || contains("PROXIMITY")` (`AutomationsScreen.kt:121`) — **`HAND_WAVE` rules never appear** under it (only under "All"), though *wave* is the app's headline gesture.
* The five filter chips are a plain `Row` with 9 sp text (`:310-339`); on narrow screens they overflow/clip — use `LazyRow`/`FlowRow`.
* **"⚡ LOAD 11+ PRESETS"** adds duplicates on every press and gives no count (RULE-01); the "WAKELOCK" pill lies (LIFE-08).

### UX-08 — Input controls and validation
**Severity:** 🟡 Medium · **Category:** UX / Validation · **Confidence:** Confirmed (code) · **Effort:** M

* Add-rule dialog: `thresholdText.toFloatOrNull() ?: 0f` (`AutomationsScreen.kt:1173`) silently turns bad input into **0** (e.g. *light ≤ 0 lux*); no per-trigger range/unit (label "Lux / % / Force"); no numeric keyboard; `SET_VOLUME` isn't validated and the fallbacks disagree (**50** in the test path `JarvisViewModel.kt:696`, **60** in the service `JarvisAutomationService.kt:207` and the conflict engine `SensorConflictEngine.kt:92`); default light-above threshold is 800 lux in the dropdown but 1000 in templates/presets; the auto-name is `"Custom Rule ${millis % 1000}"`; `LAUNCH_APP` takes free text although an installed-apps list exists; `addRule` hard-codes a 4 s cooldown with no UI.
* Scheduler time input is a **±1 h / ±5 min stepper** of 24 dp buttons with null descriptions (`ScheduledTasksView.kt:610-660` and the edit dialog `:1386-1400`) — no Material `TimePicker`; minutes can only change in 5-min steps from the default.
Use numeric keyboards with ranges/units and inline errors, a `TimePicker`, and shared constants.

### UX-09 — Model selection and HUD density
**Severity:** 🔵 Low · **Category:** UX · **Confidence:** Confirmed (code) · **Effort:** S–M

The top-bar model pill **cycles through 13 tiers by repeated taps** (`JarvisViewModel.kt:403-410`) and changes the model silently; the HUD's expandable panel lists all 13 chips plus mic modes, wake lock, fallback… on one long scroll. Use a picker (bottom sheet) with explanations, and move advanced options to a Settings screen.

### UX-10 — Logs, time formats, status messages, toasts
**Severity:** 🔵 Low · **Category:** UX · **Confidence:** Confirmed (code) · **Effort:** S

Console/Comms/Rules show `HH:mm:ss` only (`ConsoleLogsScreen.kt:226,299`, `AutoCallSmsView.kt:856`, `AutomationsScreen.kt:869`) — no date, 24 h regardless of locale, and a new `SimpleDateFormat` per item per recomposition. `systemStatusMessage` is rendered only on the Comms tab (`SystemCommsScreen.kt:134-157`) although set by HUD/Rules actions, and is never auto-dismissed. 7 `Toast` sites (e.g. one per mic tap) — use a Snackbar host.

### UX-11 — Volume slider fires the system volume panel on every drag frame
**Severity:** 🔵 Low · **Category:** UX / Performance · **Confidence:** Confirmed (code) · **Effort:** S

`CoreHudScreen.kt:809-813`: `onValueChange = { onSetVolume(it.toInt()) }` → `setStreamVolume(…, FLAG_SHOW_UI)` per frame: flickering system panel + IPC storm; the value is also a stale mirror (SENS-09). Apply on `onValueChangeFinished` (or throttle) without `FLAG_SHOW_UI`.

### UX-12 — No onboarding; risky defaults are already on
**Severity:** 🟡 Medium · **Category:** UX / Safety · **Confidence:** Confirmed (code) · **Effort:** M

First launch asks mic + camera + notifications at once (LIFE-09) with no explanation; seeded rules that change volume/mute are **enabled** (DB-03); SMS read-aloud is on (TEL-04); there is no guided setup for **DND access, exact alarms, battery-optimisation exemption, TTS voice pack, default language**, and the Diagnostics screen is hidden behind a small "i". Add a 4–5 step onboarding + a persistent "Setup checklist" with live status and deep links.

### 4.16 Performance & optimisation

### PERF-01 — Main-thread I/O at start-up and inside voice commands
**Severity:** 🟠 High · **Category:** Performance / ANR risk · **Confidence:** Confirmed (code) · 🧪 measure · **Effort:** S–M

* `JarvisViewModel.init` (`:199-212`) runs on Main: `loadInstalledApps()` (`:275-279`, `viewModelScope.launch` = `Dispatchers.Main.immediate`) → `HardwareController.getInstalledApps()` (`:823-839`) iterates `getInstalledApplications(GET_META_DATA)` and calls `getLaunchIntentForPackage` **and** `getApplicationLabel` (loads resources from each APK) for *every* package (hundreds; typically 0.2–2 s). `refreshPermissions()` → `loadContacts("")` plus an explicit `loadContacts("")` (`:203,216`) → `ContentResolver` queries on Main.
* `submitVoiceCommand` (`:529-532`) runs `processCommand` on Main; it can block on `resolveYouTubeVideoId` (≤ 2.2 s), `findLocalAudioUri`, `launchAppByName` → `getInstalledApps()`, contact search, `sendSms`.
**Fix** `withContext(Dispatchers.IO)` for all of it; build the app list once via `queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), 0)` (label + package in one call) and cache with `ACTION_PACKAGE_ADDED/REMOVED` invalidation; enable `StrictMode` (debug) and add a startup Macrobenchmark.

### PERF-02 — Recomposition scope and lifecycle-unaware collection
**Severity:** 🟡 Medium · **Category:** Performance / Battery · **Confidence:** Confirmed (code) · **Effort:** M

`MainAppContent` (`MainActivity.kt:81-142`) collects **49** StateFlows with `collectAsState()` at the root; `collectAsStateWithLifecycle` is used **0** times although `lifecycle-runtime-compose` is a dependency — collection (and any `WhileSubscribed` upstream) continues while the Activity is stopped. High-rate flows (`audioRms` ~10–20 Hz, `telemetry` 16–60 Hz, `humanVoiceConfidence`, `backgroundNoiseLevel`) are read in the `Scaffold` content lambda, so the whole `when(selectedTab)` re-executes at that rate; `CoreHudScreen` (1,217 lines) passes `audioRms` as a plain `Float` into the hero `item`, so ~50 composables recompose per RMS update.
**Fix** `collectAsStateWithLifecycle`; pass high-rate values as `State`/lambdas (`audioRms: () -> Float`) read only in draw modifiers; sample telemetry to ~5 Hz for UI; `derivedStateOf`; split 1.2–1.4 k-line composables; use immutable/stable parameter types.

### PERF-03 — Sensor, recogniser and wake-lock load add up
**Severity:** 🟡 Medium · **Category:** Battery · **Confidence:** Code (cross-ref) · 🧪 measure · **Effort:** M

Accelerometer at `SENSOR_DELAY_UI` all the time and one `SensorTelemetry` copy per sample (SENS-05); a DB query per event (SENS-05); continuous recogniser restarts (VOICE-10); partial wake locks (LIFE-08); always-on animations (PERF-04). Add a battery-impact estimate/"listening time" indicator, adaptive modes, and profile with Battery Historian/Perfetto.

### PERF-04 — Always-on 60 fps animations
**Severity:** 🟡 Medium · **Category:** Performance / Battery · **Confidence:** Confirmed (code) · **Effort:** S

`ArcReactorCore.kt:69-99`: three `rememberInfiniteTransition` animations (rotation, counter-rotation, pulse) redraw a gradient-filled `Canvas` at 60 fps forever, even when IDLE. The tween *duration* depends on `speechState` (`:75,85`), so changing state re-targets the transition and the rotation snaps back (visible glitch). Animate only while LISTENING/PROCESSING/SPEAKING (low-rate when idle), keep durations constant and scale with `Modifier.graphicsLayer`.

### PERF-05 — Allocation and disk churn
**Severity:** 🔵 Low · **Category:** Performance · **Confidence:** Confirmed (code) · **Effort:** S

`SimpleDateFormat` per item/recomposition (UX-10); `AutoCallSmsManager.updateConfig` writes 9 prefs **per keystroke** (`AutoCallSmsView.kt:395-398,418-421`); `ConversationMemory.saveTurnsToDisk` rewrites the JSON every turn; `Calendar.getInstance` inside composition (`ScheduledTasksView.kt:100`); Regex/list rebuilds per command (INTENT-14); 211 `Log.*` calls with string interpolation evaluate even in release. Hoist/`remember`, `DateTimeFormatter`, debounce config writes, DataStore, R8 log stripping.

### PERF-06 — Lazy lists without stable keys
**Severity:** 🔵 Low · **Category:** Performance / UX · **Confidence:** Confirmed (code) · **Effort:** S

`AutoCallSmsView.kt:854` (`items(filteredLogs)`), `SystemCommsScreen.kt:404,1252` (`items(permissions)`, `items(filteredContacts)`), several `LazyRow`s: position-based identity causes needless recomposition and lost item state when the list changes. Pass `key = { it.id }`.

---

## 5. Cross-cutting recommendations (root causes, not symptoms)

Most individual findings trace back to a few structural choices. Fixing these once removes whole classes of bugs.

1. **One command pipeline** (`CommandProcessor`, LIFE-03): *normalise → strip wake-word/politeness → route → (confirm) → execute → respond → memory/history*, running on an IO dispatcher behind a `Mutex`. Both the UI and the background service call it; nothing else touches `OfflineIntentEngine`/Gemini directly.
2. **Typed intents with anchored matching** (INTENT-14): `IntentMatcher` objects registered by priority; each declares its grammar (anchored regex or token rules), a minimum confidence, and whether it needs confirmation. **Default = fall through to the LLM** — the local engine should only take an utterance it is sure about. Confirmation policy: *always* confirm call, SMS, SOS, close-app, model change, rule creation.
3. **Truthful action results**: replace `Pair<Boolean,String>`/`success=false` overloads with `sealed interface ActionResult { Ok · PermissionDenied(permission) · BlockedInBackground · NotFound · Failed(cause) }` returned by `HardwareController`/`TelephonyAlarmManager`. The responder (not the executor) writes the spoken text, so the assistant never says "Opening YouTube" when Android blocked it (HW-01, TEL-05, SENS-08).
4. **Explicit lifecycle ownership**: the *service* owns mic, TTS, sensors and wake locks; the UI only observes and sends intents. Reference-count sensors, make the speech manager restartable, tie mic release to `ProcessLifecycleOwner`/screen state — never to `Activity.onDestroy` (LIFE-01, LIFE-04, LIFE-08).
5. **Settings & secrets layer**: DataStore for preferences (cloud on/off, lock-screen, read-aloud, auto-reply policy, retention); Keystore-backed storage for keys; one place that enforces "Cloud AI off means no network" (AI-04, AI-08).
6. **Dependency injection instead of Application-as-service-locator**: `JarvisApplication` exposes ~15 lazy singletons and receivers reach in through `context.applicationContext as JarvisApplication`; construction order and threading are implicit (SMS receiver cold-starts TTS). Use Hilt/Koin or a manual container so components can be built with fakes in tests.
7. **Logging facade** that is a no-op in release, scrubs PII and adds a *command trace* (which matcher fired and why) surfaced in Diagnostics — it will make intent bugs visible immediately (SEC-02).
8. **Data layer discipline**: exported schemas + migrations, retention/pruning, `timestamp` indices, enums via `TypeConverter`, unique keys for presets (DB-01…04, RULE-01).
9. **UI system**: design tokens (typography ≥ 12 sp, accessible colours), reusable `JarvisButton`/`JarvisToggle` with 48 dp targets, roles and state descriptions; split the 1.2–1.4 k-line screens; hoist state with `rememberSaveable`/`SavedStateHandle`; `collectAsStateWithLifecycle`; edge-to-edge + IME insets done once at the root (UX-01…04, PERF-02).
10. **Truth in UI**: an indicator must be bound to a measured state or removed (UX-05, VOICE-03).

---

## 6. Test plan

### 6.1 JVM unit tests to add first (table-driven; each row is a confirmed defect above)

**Intent router** (`OfflineIntentEngine` / future `IntentRouter`)

| Utterance | Expected | Finding |
|---|---|---|
| "can you help me write a poem" | NotHandled → LLM | INTENT-01 |
| "please help me call Rahul" | CallIntent(name = "rahul") + confirmation | INTENT-01/04 |
| "what time is it" | Time(local) | — |
| "what time does the movie start" | NotHandled → LLM | INTENT-01 |
| "what's the time in India" (device in another zone) | Time(Asia/Kolkata) | INTENT-10 |
| "what time is my dentist appointment" | NotHandled | INTENT-01/10 |
| "how do I start learning Kotlin" | NotHandled (no app-launch failure text) | INTENT-02 |
| "open PhonePe" | LaunchApp(PhonePe) — **not** Dialer | INTENT-09 |
| "close the door" | NotHandled (no Home press) | INTENT-08 |
| "what is the latest model of iPhone" | NotHandled; Gemini tier unchanged | INTENT-03 |
| "set alarm for 7:30 am" / "wake me up in 8 hours" | Alarm 07:30 / now + 8 h | INTENT-05 |
| "set alarm for 45 minutes" | clarification (not 23:00) | INTENT-05 |
| "timer for 1 hour" | Timer(3600 s) | INTENT-05 |
| "सुशीला ला कॉल करा" | name = "सुशीला" | INTENT-04 |
| "call Rahul at 10:30" | name = "rahul", **not** number 1030 | INTENT-04 |
| "text Mom I will be home soon" | SMS(to = "mom", body = "I will be home soon") (original casing) | INTENT-04 |
| "text to speech settings" | not an SMS | INTENT-04 |
| "schedule text daily to 9876543210 saying good morning" | AUTO_SMS, DAILY (not Gemini) | INTENT-06 |
| "schedule text to Ram at 12 pm … 9876543210" | 12:00 (not 00:00) | INTENT-06 |
| at 10:57, "schedule text … in 5 minutes" | 11:02 (not 10:02) | INTENT-06 |
| "play Display Picture" / "play Open Arms" | query "display picture" / "open arms" | INTENT-07 |
| "create rule when shake say hello there" | speak-param = "hello there" | INTENT-11 |

**Wake word** — `"Hey, Jarvis play music"` → `(true, "play music")`; `"Jarvis, what's the time?"` → `(true, "what's the time?")`; locked + `"Tell my friend Jarvis is cool"` → not accepted; `"सर्व्हिस सेंटर कुठे आहे"` → not accepted (VOICE-01).

**Knowledge/math** — `"2 + 3 ^ 2"` = 11; `"100 + 15% of 200"` = 130 or NotHandled; `"15% of 200"` (English) → English answer "30"; `"5 +"` → NotHandled; `"10 / 0"` → "can't divide by zero"; `"Pakistan 5"` → NotHandled; `"features of the iPhone 15"`, `"how many calories burns walking"`, `"good morning, what's the weather in Pune"` → NotHandled (KNOW-01/03).

**Memory** — `"recipe for pasta"` → location null, topic unchanged; `"Tell me whether Python is good for beginners"` unchanged; `"weather in Pune"` → Pune/Weather; model text "happens in plants" doesn't set a location (MEM-01/02).

**Conflict engine (sequence tests with a fake clock)** — the Face-Down sequence in Appendix B must fire on events 1 and 3 (SENS-01); `BATTERY_FULL` at 99 % **not charging** → no fire, 100 % charging → fire; `BATTERY_LOW` at 15 % while charging → no fire (SENS-02); duplicate default+preset rules produce a conflict warning (RULE-01); a proximity sensor with `maximumRange = 1.0` and far = 1.0 is **not** "in pocket" (SENS-03).

**Gemini engine (MockWebServer)** — HTTP 200 whose text contains "quota exceeded" → `Success`, no cool-down (AI-01); 429 with `retryDelay: 30s` → cool-down 30 s; no network → immediate offline message within 1 s; 404 → model marked unavailable, next tier used; request JSON contains `thinkingLevel: low` for `gemini-3.*`, `thinkingBudget` for `gemini-2.5*`, and **no** `temperature` for Gemini 3 (AI-02/03).

**Scheduling** — `calculateNextTriggerMillis` with a fake clock: ONCE in the past, DAILY across DST start/end, `LOCAL` after a zone change, HOURLY anchored to previous schedule; `claim()` under two concurrent callers → exactly one run; overdue ONCE within grace window → runs, beyond → `MISSED` (SCH-01/03/05).

**Telephony** — multipart join per sender (TEL-06); sender filter (short code/alphanumeric ignored) (TEL-03); SOS with a *name* resolves to a number or fails visibly, never reports success on failure (TEL-02); SMS status stays `SENT` until a delivery report arrives (TEL-05).

### 6.2 Instrumented / Compose tests

* `ActivityScenario.recreate()` → speech manager still speaks and listens (LIFE-01); deny mic → toggle service → no crash (LIFE-02).
* Room `MigrationTestHelper` for every version step; seeded rules survive migration (DB-01).
* Compose: rotation keeps form input (UX-01); IME does not cover the HUD input (UX-04); `AccessibilityChecks.enable()` and min-touch-target assertions (UX-02/03); the Gestures filter shows `HAND_WAVE` rules (UX-07).
* Receiver tests: `SMS_RECEIVED` with a 2-part PDU produces one message; `PHONE_STATE` RINGING twice → one announcement.

### 6.3 Manual device matrix (🧪 items)

| Dimension | Values |
|---|---|
| Android | 10, 12, 13, 14, 15, 16 (FGS + background-launch + exact-alarm behaviour differ) |
| OEM | Pixel + Samsung + Xiaomi/Oppo (battery managers, proximity sensor ranges, TTS voices) |
| Permissions | all granted · mic denied · SMS denied · DND access off · exact alarms off · notifications off |
| Device state | screen on / off / locked · airplane · slow network · Doze · dual-SIM |
| UI | rotation · light + dark system theme · font scale 200 % · TalkBack · gesture + 3-button nav · keyboard open |
| Voice | Marathi voice pack present/absent · AUTO/EN/MR · headset · noisy room · TV playing "stop"/"Jarvis" |

### 6.4 Performance & battery

Macrobenchmark cold-start (before/after PERF-01), JankStats on the HUD while listening (PERF-02/04), `StrictMode` (debug) for disk/network on main, Perfetto/Battery Historian for 1 h always-on listening with rules armed (VOICE-10, SENS-05/06, LIFE-08).

---

## Appendix A — External facts verified for this report (as of 2026-09-29)

| Claim | Evidence |
|---|---|
| Gemini IDs used by the app currently exist and are **stable**: `gemini-3.8-flash` (2026-09-02), `gemini-3.7-flash` (2026-08-13), `gemini-3.6-flash` (2026-07-21), `gemini-3.5-flash` (2026-05-19, "legacy"), `gemini-3.5-flash-lite` (2026-07-21), `gemini-3.1-flash-lite`; `gemini-3-flash-preview` is a *preview*; `gemini-2.5-flash` stable | Models page (last updated 2026-09-24) — <https://ai.google.dev/gemini-api/docs/models> |
| `gemini-3.1-flash-lite` **shutdown 2027-05-07** → `gemini-3.5-flash-lite`; `gemini-3-flash-preview` replacement `gemini-3.6-flash`; `gemini-2.5-flash` no shutdown announced | Deprecations — <https://ai.google.dev/gemini-api/docs/deprecations> |
| `*-latest` aliases are hot-swapped with a 2-week notice | Models page → "Model version name patterns → Latest" |
| Gemini 3: *"If `thinking_level` is not specified, Gemini 3 will default to `high`"*; 3.1 Flash-Lite defaults to `minimal`; `low/medium/high` valid | Gemini 3 guide — <https://ai.google.dev/gemini-api/docs/generate-content/gemini-3> |
| 3.8 Flash: thinking `low/medium/high`, *"minimal is not supported and returns an error"*; Search + Maps grounding supported | <https://ai.google.dev/gemini-api/docs/models/gemini-3.8-flash> |
| *"For all Gemini 3 models, we strongly recommend keeping the temperature parameter at its default value of 1.0… may lead to looping or degraded performance"* | Gemini 3 guide (above) |
| Documented auth style is the `x-goog-api-key` header | Gemini 3 guide `curl` sample |
| Android 14: permissions must be granted **before** `startForeground()`; microphone type needs `RECORD_AUDIO`; failing → `SecurityException`, *"might cause your app to crash"* | <https://developer.android.com/about/versions/14/changes/fgs-types-required> |
| A microphone (while-in-use) FGS **cannot be created from the background**, even with a background-start exemption | <https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start> |
| Background Activity Launch: allowed only with a visible window, IME, system-sent `PendingIntent` (e.g. notification tap), `SYSTEM_ALERT_WINDOW`, etc.; a foreground service is **not** on the list | <https://developer.android.com/guide/components/activities/secure-bal> |
| Android 12 notification-trampoline restriction (no `startActivity` from a service/receiver started by a notification tap/action) | <https://developer.android.com/about/versions/12/behavior-changes-12> |
| Since Android 8.0 only explicitly requested permissions in a group are granted | Android 8.0 behaviour change (quoted in <https://stackoverflow.com/questions/47855036>) |
| `RECEIVE_SMS` requires a runtime grant (Android 6+) | <https://google-developer-training.github.io/android-developer-phone-sms-course/Lesson%202/2_p_2_sending_sms_messages.html> |
| Android 9+: `READ_CALL_LOG` needed for the number in `PHONE_STATE`; with both permissions the receiver is called **twice** (first without the number) | <https://stackoverflow.com/questions/52009874> |
| Play: SMS & Call-Log permission groups only for default SMS/Phone/Assistant handlers; manifests of other apps *"may not declare use"* | <https://support.google.com/googleplay/android-developer/answer/16558241> |
| `SCHEDULE_EXACT_ALARM` denied by default on Android 14+ for new installs; `USE_EXACT_ALARM` is Play-restricted | <https://developer.android.com/about/versions/14/changes/schedule-exact-alarms> |
| Room does **not** call `Callback.onCreate` after a destructive migration; use `onDestructiveMigration` | <https://stackoverflow.com/questions/56247178> · <https://developer.android.com/reference/androidx/room/RoomDatabase.Callback> |
| `enableEdgeToEdge()` default `SystemBarStyle.auto` follows the **device** dark/light setting (dark icons on dark UI in light mode) | <https://developer.android.com/reference/androidx/activity/SystemBarStyle> · community reports |
| Latest Compose BOM known at review time: **2026.04.01** (Compose 1.11); 1.12 needs compileSdk 37 / AGP 9 | <https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html> |
| OkHttp 5.x is stable (4.12.0 is the last 4.x) | <https://github.com/square/okhttp/releases> |

---

## Appendix B — Reproduction harness

The scratch script below was **not committed as code**; it ports the exact regexes and control flow of the Kotlin sources to Python's `re` (identical semantics for the constructs used) to demonstrate the pure-logic defects. Run with `python3 repro.py`. Use its rows as the seed for the unit tests in §6.1.

<details>
<summary><b>Output (as run on 2026-09-29)</b></summary>

```text

======== BUG-VOICE: extractWakeWordAndCommand slices ORIGINAL text by NORMALISED prefix length ========
'Hey Jarvis play music'                       -> (True, 'play music')
'Hey, Jarvis play music'                      -> (True, 's play music')
'Hey Jarvis,  play music'                     -> (True, ',  play music')
"Jarvis, what's the time?"                    -> (True, ", what's the time?")
'Tell my friend Jarvis is cool'               -> (True, 'Tell my friend  is cool')

======== BUG-INTENT: scheduled-task voice parser substring flags ('ai','am','ist') ========
'schedule text daily to 9876543210 saying good morning'
    {'isGeminiTask': True, 'isAm': False, 'isPm': False, 'useIndiaTime': False}  (12 pm -> hour=12)
'schedule text to ram at 12 pm saying hello 9876543210'
    {'isGeminiTask': False, 'isAm': True, 'isPm': True, 'useIndiaTime': False}  (12 pm -> hour=0)
'schedule text to my dentist at 5 pm 9876543210'
    {'isGeminiTask': False, 'isAm': False, 'isPm': True, 'useIndiaTime': True}  (12 pm -> hour=12)

======== BUG-INTENT: alarm hour regex grabs first number anywhere ========
'set alarm for 45 minutes'             -> hour=45  clamped_to_0..23=23  [hour-only regex]
'wake me up in 8 hours'                -> hour=8  clamped_to_0..23=8  [hour-only regex]
'set alarm 5 minutes from now'         -> hour=5  clamped_to_0..23=5  [hour-only regex]
'set alarm for 7:30 am'                -> hour=7  clamped_to_0..23=7  [colon form]

======== BUG-INTENT: 'text <name> <body>' example from source comment does not parse body ========
'text mom i will be home soon'             -> target='mom i will be home soon' body=None
'text mom saying i will be home soon'      -> target='mom' body='i will be home soon'
'text to speech settings'                  -> target='to speech settings' body=None

======== BUG-INTENT: parseRuleFromVoice speak/say chain (substringAfter with '' default) ========
'create rule when shake speak hello there'              -> param=''
'create rule when shake say hello there'                -> param=''
'create rule when shake speak hello say hi सांगा x'     -> param='x'

======== BUG-KNOWLEDGE: partial-match regexes answer only a fragment of the utterance ========
'2 + 3 ^ 2'      -> regex answers 9.0 (correct = 11)
'100 + 15% of 200' -> matches Marathi-percent branch: True -> answer 30.0 (correct = 130), spoken in Marathi
'15% of 200'     -> Marathi branch hit for ENGLISH query: True

======== BUG-MEMORY: 'whether'->'weather' rewrite corrupts general questions ========
'Tell me whether Python is good for beginners'
   -> 'Tell me weather Python is good for beginners'
'I wonder whether to go in the morning'
   -> 'I wonder weather to go in the morning'
'Whether it is legal to record calls'
   -> 'Whether it is legal to record calls'

======== BUG-MEMORY: extractLocationFromQuery fallback treats any 'in/for/at <word>' as a city and sets topic=Weather ========
'Give me a recipe for pasta'               -> location=Pasta
'Explain recursion in programming'         -> location=Programming
'Find a movie for children'                -> location=Children
'Who is the best player at cricket'        -> location=Cricket

======== BUG-INTENT: over-broad contains() intents (word-level false positives) ========
contains('help') hits: 'Can you help me write a poem'
contains('help') hits: 'please help me call Rahul'
contains('what time') hits: 'what time does the movie start'
contains('what time') hits: 'what time is sunrise tomorrow'
contains('ist') hits: 'what time is my dentist appointment'
contains('start') hits: 'how do I start learning Kotlin'
contains('model') hits: 'what is the latest model of iPhone'
contains('features') hits: 'what are the features of iPhone 15'
contains('burns') hits: 'how many calories burns when walking'
openAppRegex on 'how do I start learning kotlin' -> learning kotlin
trailingOpenRegex on 'when does the market open' -> when does the market

======== BUG-INTENT: launchAppByName / close-app string mangling ========
'close whatsapp' -> target after .replace('app','') = whats
'close apple music' -> le music
'open phonepe' hits query.contains('phone') -> True
Marathi name 'सुशीला' after .replace('ला','') -> सुशी
Song 'display picture' after .replace('play','') -> dis picture
Song 'open arms' after .replace('open','') ->  arms

======== BUG-SENSOR: SensorConflictEngine edge-state is reset AFTER early-return guards ========
--- CURRENT order
t=100: flip face-down (vol=60) -> fire=True (rising edge)
t=130: pick up (vol=0) -> fire=False (already muted)  state={1: True}
t=160: flip face-down again (vol=50) -> fire=False (Condition already active (level hold))
--- FIXED order (reset state first)
t=100: flip face-down (vol=60) -> fire=True (rising edge)
t=130: pick up (vol=0) -> fire=False (already muted)  state={1: False}
t=160: flip face-down again (vol=50) -> fire=True (rising edge)

======== BUG-RULES: duplicate default+preset SHAKE->TOGGLE_FLASHLIGHT rules cancel each other ========
rules firing per shake: 2 -> torch state after ONE shake starting OFF: OFF (no visible effect)

======== BUG-SENSOR: BATTERY_FULL fires on discharge at 99% ========
unplugged, level drops 100->99 (charging=False) -> True => speaks 'fully charged... you may disconnect the charger'

======== BUG-HW: percent<->stream-step rounding (toInt() truncation) ========
ask 50% -> stream 7/15 -> reads back 46% (UI/TTS say 50%)
ask 30% -> stream 4/15 -> reads back 26% (UI/TTS say 30%)
ask 90% -> stream 13/15 -> reads back 86% (UI/TTS say 90%)
```

</details>

<details>
<summary><b>Script (<code>repro.py</code>)</b></summary>

```python
"""
Scratch reproduction harness (NOT part of the repo).
Ports the exact regexes / control flow of the Kotlin sources to Python `re`
to demonstrate logic defects found by static review.
Java/ICU and Python regex semantics are equivalent for the constructs used here.
"""
import re

def hdr(t): print("\n" + "="*8 + " " + t + " " + "="*8)

# ---------------------------------------------------------------- 1. wake word
hdr("BUG-VOICE: extractWakeWordAndCommand slices ORIGINAL text by NORMALISED prefix length")
def extract_wake(raw):
    clean = raw.strip()
    lower = re.sub(r"[^\w\s]", " ", clean.lower())      # punctuation -> space
    lower = re.sub(r"\s+", " ", lower).strip()
    prefixes = ["hey jarvis","ok jarvis","hi jarvis","jarvis please","jarvis"]
    for p in prefixes:
        if lower == p: return (True, "")
    for p in prefixes:
        if lower.startswith(p + " "):
            return (True, clean[min(len(p), len(clean)):].strip())   # <- Kotlin: clean.substring(minOf(prefix.length, clean.length))
    for p in prefixes:
        if p in lower:
            return (True, re.sub(r"(?i)\b"+p+r"\b","",clean).strip())
    return (False, raw)
for s in ["Hey Jarvis play music", "Hey, Jarvis play music", "Hey Jarvis,  play music", "Jarvis, what's the time?", "Tell my friend Jarvis is cool"]:
    print(f"{s!r:45} -> {extract_wake(s)}")

# ---------------------------------------------------------------- 2. scheduling parse
hdr("BUG-INTENT: scheduled-task voice parser substring flags ('ai','am','ist')")
def sched_flags(clean):
    isGemini = "gemini" in clean or "prompt" in clean or "ai" in clean
    isAm = "am" in clean; isPm = "pm" in clean
    india = "india" in clean or "ist" in clean
    return dict(isGeminiTask=isGemini, isAm=isAm, isPm=isPm, useIndiaTime=india)
for s in ["schedule text daily to 9876543210 saying good morning",
          "schedule text to ram at 12 pm saying hello 9876543210",
          "schedule text to my dentist at 5 pm 9876543210"]:
    f = sched_flags(s)
    hour = 12
    if f["isPm"] and 1 <= hour <= 11: hour += 12
    elif f["isAm"] and hour == 12: hour = 0
    print(f"{s!r}\n    {f}  (12 pm -> hour={hour})")

hdr("BUG-INTENT: alarm hour regex grabs first number anywhere")
hour_only = re.compile(r"(?:for|at|वाजता|ला)?\s*(\d{1,2})\s*(?:o'?clock|am|pm|वाजता)?")
for s in ["set alarm for 45 minutes", "wake me up in 8 hours", "set alarm 5 minutes from now", "set alarm for 7:30 am"]:
    m = re.search(r"(\d{1,2}):(\d{2})", s)
    if m: h = int(m.group(1)); note="colon form"
    else:
        m2 = hour_only.search(s); h = int(m2.group(1)) if m2 else None; note="hour-only regex"
    print(f"{s!r:38} -> hour={h}  clamped_to_0..23={min(max(h,0),23) if h is not None else None}  [{note}]")

# ---------------------------------------------------------------- 3. SMS parser
hdr("BUG-INTENT: 'text <name> <body>' example from source comment does not parse body")
en = re.compile(r"(?:send\s+(?:sms|message)\s+to|text)\s+([a-zA-Z0-9\+\s]+?)(?:\s+(?:saying|that|with text)\s+(.*))?$")
for s in ["text mom i will be home soon", "text mom saying i will be home soon", "text to speech settings"]:
    m = en.search(s); print(f"{s!r:42} -> target={m.group(1)!r} body={m.group(2)!r}")

# ---------------------------------------------------------------- 4. voice rule builder
hdr("BUG-INTENT: parseRuleFromVoice speak/say chain (substringAfter with '' default)")
def substring_after(s, delim, missing): 
    i = s.find(delim); return missing if i < 0 else s[i+len(delim):]
for t in ["create rule when shake speak hello there", "create rule when shake say hello there", "create rule when shake speak hello say hi सांगा x"]:
    after = substring_after(substring_after(substring_after(t,"speak",""),"say",""),"सांगा","").strip()
    print(f"{t!r:55} -> param={after!r}")

# ---------------------------------------------------------------- 5. math
hdr("BUG-KNOWLEDGE: partial-match regexes answer only a fragment of the utterance")
pow_re = re.compile(r"(\d+(?:\.\d+)?)\s*(?:to the power of|power of|\^|चा घात|cha ghat)\s*(\d+(?:\.\d+)?)")
m = pow_re.search("2 + 3 ^ 2"); print("'2 + 3 ^ 2'      -> regex answers", float(m.group(1))**float(m.group(2)), "(correct = 11)")
pct2 = re.compile(r"(\d+(?:\.\d+)?)\s*(?:टक्के|takke|%)\s*(?:चे|che|ऑफ|of)?\s*(\d+(?:\.\d+)?)")
m = pct2.search("100 + 15% of 200"); print("'100 + 15% of 200' -> matches Marathi-percent branch:", bool(m), "-> answer", (float(m.group(1))/100)*float(m.group(2)), "(correct = 130), spoken in Marathi")
m = pct2.search("15% of 200"); print("'15% of 200'     -> Marathi branch hit for ENGLISH query:", bool(m))

# ---------------------------------------------------------------- 6. whether -> weather
hdr("BUG-MEMORY: 'whether'->'weather' rewrite corrupts general questions")
def normalize(q):
    lower = q.lower()
    if "whether" in lower:
        ctx = any(k in lower for k in ["today","full","tomorrow","forecast","report","rain","temperature","in ","for "])
        if ctx: q = re.sub(r"(?i)\bwhether\b", "weather", q)
    return q
for q in ["Tell me whether Python is good for beginners", "I wonder whether to go in the morning", "Whether it is legal to record calls"]:
    print(f"{q!r}\n   -> {normalize(q)!r}")

hdr("BUG-MEMORY: extractLocationFromQuery fallback treats any 'in/for/at <word>' as a city and sets topic=Weather")
loc = re.compile(r"(?i)\b(?:in|for|at|around|weather in|whether in)\s+([A-Za-z]{3,20})\b")
nonc = {"the","today","tomorrow","this","my","our","full","detail","degrees","celsius","python","kotlin","java"}
for q in ["Give me a recipe for pasta", "Explain recursion in programming", "Find a movie for children", "Who is the best player at cricket"]:
    m = loc.search(q); w = m.group(1) if m else None
    print(f"{q!r:42} -> location={'%s'%w.capitalize() if w and w.lower() not in nonc else None}")

# ---------------------------------------------------------------- 7. hoisted intent contains()
hdr("BUG-INTENT: over-broad contains() intents (word-level false positives)")
tests = {
 "help": ["Can you help me write a poem", "please help me call Rahul"],
 "what time": ["what time does the movie start", "what time is sunrise tomorrow"],
 "ist": ["what time is my dentist appointment"],
 "start": ["how do I start learning Kotlin"],
 "model": ["what is the latest model of iPhone"],
 "features": ["what are the features of iPhone 15"],
 "burns": ["how many calories burns when walking"],
}
for k, qs in tests.items():
    for q in qs: print(f"contains({k!r}) hits: {q!r}")
open_re = re.compile(r"(?:open|launch|start|उघडा|चालू करा)\s+(.+)")
print("openAppRegex on 'how do I start learning kotlin' ->", open_re.search("how do i start learning kotlin").group(1))
tr_re = re.compile(r"(.+)\s+(?:open|launch|start|उघडा|चालू करा)")
print("trailingOpenRegex on 'when does the market open' ->", tr_re.search("when does the market open").group(1))

hdr("BUG-INTENT: launchAppByName / close-app string mangling")
print("'close whatsapp' -> target after .replace('app','') =", "whatsapp".replace("app",""))
print("'close apple music' ->", "apple music".replace("app",""))
print("'open phonepe' hits query.contains('phone') ->", "phone" in "phonepe")
print("Marathi name 'सुशीला' after .replace('ला','') ->", "सुशीला".replace("ला",""))
print("Song 'display picture' after .replace('play','') ->", "display picture".replace("play",""))
print("Song 'open arms' after .replace('open','') ->", "open arms".replace("open",""))

# ---------------------------------------------------------------- 8. Edge-trigger state machine
hdr("BUG-SENSOR: SensorConflictEngine edge-state is reset AFTER early-return guards")
class Rule:  # default rule: Face-Down Meeting Silence
    id=1; cooldown=5; triggerType="FLIP_FACE_DOWN"; actionType="MUTE_ALL"; last=0
def should_execute(rule, event_orientation, now, volume, state, fixed=False):
    # guard 1: cooldown
    def condition(): return event_orientation == "FLAT_FACE_DOWN"
    if fixed:
        cond = condition(); was = state.get(rule.id, False)
        if not cond: state[rule.id] = False
    if now - rule.last < rule.cooldown: return (False, "cooldown")
    if rule.actionType == "MUTE_ALL" and volume == 0: return (False, "already muted")   # no-op guard (returns BEFORE state update)
    if not fixed:
        cond = condition(); was = state.get(rule.id, False)
        if cond:
            if not was: state[rule.id] = True; return (True, "rising edge")
            return (False, "Condition already active (level hold)")
        state[rule.id] = False; return (False, "Condition not met")
    else:
        if cond:
            if not was: state[rule.id] = True; return (True, "rising edge")
            return (False, "Condition already active (level hold)")
        return (False, "Condition not met")
for fixed in (False, True):
    print(f"--- {'FIXED order (reset state first)' if fixed else 'CURRENT order'}")
    st, r, t, vol = {}, Rule(), 100, 60
    r.last = 0
    ok, why = should_execute(r, "FLAT_FACE_DOWN", t, vol, st, fixed); print(f"t={t}: flip face-down (vol={vol}) -> fire={ok} ({why})")
    if ok: r.last = t; vol = 0                     # rule ran: muted
    t += 30; ok, why = should_execute(r, "UPRIGHT", t, vol, st, fixed); print(f"t={t}: pick up (vol={vol}) -> fire={ok} ({why})  state={st}")
    vol = 50                                       # user raises volume
    t += 30; ok, why = should_execute(r, "FLAT_FACE_DOWN", t, vol, st, fixed); print(f"t={t}: flip face-down again (vol={vol}) -> fire={ok} ({why})")

# ---------------------------------------------------------------- 9. double toggle
hdr("BUG-RULES: duplicate default+preset SHAKE->TOGGLE_FLASHLIGHT rules cancel each other")
torch = False
rules = ["Shake to Toggle Flashlight (Pocket Protected)", "Pocket-Guard Shake Flashlight"]   # after 'Load presets'
for r in rules: torch = not torch
print("rules firing per shake:", len(rules), "-> torch state after ONE shake starting OFF:", "ON" if torch else "OFF (no visible effect)")

# ---------------------------------------------------------------- 10. battery full
hdr("BUG-SENSOR: BATTERY_FULL fires on discharge at 99%")
def battery_full(level, charging): return level >= 99      # SensorConflictEngine: event.level >= 99 (isCharging ignored)
print("unplugged, level drops 100->99 (charging=False) ->", battery_full(99, False), "=> speaks 'fully charged... you may disconnect the charger'")

# ---------------------------------------------------------------- 11. volume rounding
hdr("BUG-HW: percent<->stream-step rounding (toInt() truncation)")
mx = 15
for pct in (50, 30, 90):
    tgt = int(pct/100*mx); back = int(tgt/mx*100)
    print(f"ask {pct}% -> stream {tgt}/{mx} -> reads back {back}% (UI/TTS say {pct}%)")
```

</details>

---

## Appendix C — Manifest permission audit

| Permission | Used by code? | Runtime-requested? | Verdict |
|---|---|---|---|
| `RECORD_AUDIO` | Yes — recogniser, diagnostics | Yes (launch, Comms) | **Keep**; gate the FGS mic type on it (LIFE-02) |
| `MODIFY_AUDIO_SETTINGS` | Not needed by the calls used (`setStreamVolume`, `ringerMode`, media keys) | normal | Likely removable — verify |
| `CAMERA` | **No** — torch uses `CameraManager.setTorchMode`; camera opens via intent | Yes (launch, Comms) | **Remove** |
| `FLASHLIGHT` | No (legacy) | normal | **Remove** |
| `CALL_PHONE` | Yes (`ACTION_CALL`) | Yes | Keep (or `TelecomManager.placeCall`, HW-01) |
| `READ_PHONE_STATE` | Yes — `PHONE_STATE` receiver | In `ALL_PERMISSIONS`, hidden from status list | Keep |
| `READ_CALL_LOG` | **Needed** for the caller number | **No** | **Request** (TEL-01) or drop the feature |
| `SEND_SMS` | Yes | Yes | Keep |
| `READ_SMS` | No | Yes | **Remove** |
| `RECEIVE_SMS` | **Needed** for `SMS_RECEIVED` | **No** | **Request** (TEL-01) |
| `READ_CONTACTS` | Yes | Yes | Keep |
| `WRITE_CONTACTS` | No | No | **Remove** |
| `SET_ALARM` | Yes | normal | Keep |
| `SCHEDULE_EXACT_ALARM` | Yes | special access; no flow | Keep + add the flow (SCH-04) |
| `USE_EXACT_ALARM` | Implicitly | auto-granted | Remove for Play (MAN-02) |
| `READ_CALENDAR` / `WRITE_CALENDAR` | No — only `ACTION_INSERT` intent | No | **Remove** |
| `ACCESS_FINE_LOCATION` / `COARSE` | **No** | Yes (asked!) | **Remove** or implement SOS location |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | No | normal | **Remove** |
| `BLUETOOTH_CONNECT` / `SCAN` | No | Yes (asked on 12+) | **Remove** |
| `KILL_BACKGROUND_PROCESSES` | Yes (close app) | normal | Keep — limited effect (INTENT-08) |
| `FOREGROUND_SERVICE`, `_SPECIAL_USE`, `_MICROPHONE` | Yes | normal | Keep |
| `POST_NOTIFICATIONS` | Yes | Yes | Keep |
| `VIBRATE`, `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Yes | normal | Keep |
| `ACCESS_NOTIFICATION_POLICY` | Declared; never checked/requested | No | Add a DND-access flow or drop the mute claims (HW-04) |
| `WRITE_SETTINGS` | No | No | **Remove** |
| *(missing)* `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Code queries `MediaStore.Audio` | — | Add if local playback stays (HW-03) |

---

## Appendix D — Findings per source file (hot-spot map)

Files referenced by findings, most-affected first (use this to batch fixes per file).

| File | # | Finding IDs |
|---|---:|---|
| `JarvisViewModel.kt` | 24 | LIFE-01, LIFE-02, LIFE-03, LIFE-04, LIFE-05, LIFE-06, LIFE-08, LIFE-10, INTENT-12, KNOW-04, AI-06, AI-08, SENS-08, SENS-09, SENS-10, SENS-12, RULE-01, TEL-12, SCH-01, SCH-07, SCH-08, SEC-01, UX-08, UX-09 |
| `OfflineIntentEngine.kt` | 18 | VOICE-09, INTENT-02, INTENT-03, INTENT-04, INTENT-05, INTENT-06, INTENT-07, INTENT-08, INTENT-10, INTENT-11, INTENT-12, INTENT-13, INTENT-15, KNOW-04, AI-05, RULE-01, TEL-02, TEL-03 |
| `HardwareController.kt` | 12 | LIFE-08, INTENT-07, INTENT-08, INTENT-09, INTENT-12, SENS-02, SENS-09, HW-01, HW-02, HW-03, HW-04, HW-05 |
| `JarvisSpeechManager.kt` | 11 | LIFE-01, LIFE-06, LIFE-07, LIFE-08, VOICE-01, VOICE-02, VOICE-03, VOICE-04, VOICE-05, VOICE-06, VOICE-07 |
| `JarvisAutomationService.kt` | 10 | LIFE-02, LIFE-04, LIFE-05, SENS-05, SENS-07, SENS-08, HW-01, SEC-01, SEC-05, UX-08 |
| `TelephonyAlarmManager.kt` | 10 | INTENT-04, INTENT-05, TEL-01, TEL-02, TEL-05, TEL-09, TEL-12, HW-01, SEC-02, MAN-01 |
| `SensorHub.kt` | 9 | LIFE-04, SENS-02, SENS-03, SENS-04, SENS-05, SENS-06, SENS-07, SENS-11, SENS-12 |
| `ScheduledTaskManager.kt` | 8 | AI-09, TEL-05, TEL-09, SCH-01, SCH-03, SCH-04, SCH-08, SEC-02 |
| `AutoCallSmsManager.kt` | 7 | RULE-01, TEL-02, TEL-03, TEL-04, TEL-05, TEL-12, SEC-02 |
| `GeminiAssistantEngine.kt` | 7 | AI-01, AI-02, AI-03, AI-04, AI-05, AI-06, AI-07 |
| `JarvisApplication.kt` | 7 | LIFE-01, LIFE-03, LIFE-05, LIFE-10, KNOW-04, AI-06, SCH-01 |
| `MainActivity.kt` | 7 | LIFE-01, LIFE-02, LIFE-06, LIFE-09, LIFE-11, SEC-01, UX-04 |
| `AutoCallSmsView.kt` | 6 | TEL-02, TEL-10, UX-06, UX-10, PERF-05, PERF-06 |
| `ScheduledTasksView.kt` | 6 | SCH-05, SCH-07, UX-01, UX-03, UX-08, PERF-05 |
| `SensorConflictEngine.kt` | 6 | SENS-01, SENS-02, SENS-03, SENS-04, RULE-01, UX-08 |
| `CoreHudScreen.kt` | 5 | VOICE-03, UX-01, UX-02, UX-06, UX-11 |
| `AutomationsScreen.kt` | 4 | UX-01, UX-07, UX-08, UX-10 |
| `ConversationMemory.kt` | 4 | AI-10, MEM-01, MEM-02, MEM-03 |
| `JarvisDatabase.kt` | 4 | RULE-01, TEL-03, DB-01, DB-03 |
| `JarvisTelephonyReceivers.kt` | 4 | TEL-01, TEL-06, TEL-07, SEC-02 |
| `ExampleUnitTest.kt` | 3 | VOICE-01, AI-05, BLD-03 |
| `OfflineKnowledgeEngine.kt` | 3 | INTENT-01, KNOW-01, HW-01 |
| `SystemCommsScreen.kt` | 3 | TEL-11, UX-10, PERF-06 |
| `VoiceDiagnosticsDialog.kt` | 3 | LIFE-01, AI-04, UX-05 |
| `ArcReactorCore.kt` | 2 | UX-02, PERF-04 |
| `ConsoleLogsScreen.kt` | 2 | UX-06, UX-10 |
| `JarvisDao.kt` | 2 | SCH-03, DB-02 |
| `MarathiTtsManager.kt` | 2 | VOICE-04, VOICE-09 |
| `NavigationAndHeader.kt` | 2 | VOICE-03, UX-05 |
| `ScheduledTaskReceiver.kt` | 2 | SCH-01, SCH-02 |
| `BootCompletedReceiver.kt` | 1 | SCH-01 |
| `GeminiModels.kt` | 1 | AI-05 |
| `HumanVoiceActivityDetector.kt` | 1 | VOICE-03 |
| `JarvisWakeLockManager.kt` | 1 | LIFE-08 |
| `ScheduledTask.kt` | 1 | SCH-07 |
| `SensorsScreen.kt` | 1 | UX-05 |
| `Theme.kt` | 1 | UX-04 |
| `Type.kt` | 1 | UX-02 |
| `VoiceDiagnostics.kt` | 1 | UX-05 |
| `VoiceLanguage.kt` | 1 | VOICE-06 |

---

*End of report. Nothing in the source tree was modified by this audit; this file is the only change.*
