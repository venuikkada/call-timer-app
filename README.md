# CallGalli

An Android app that limits how long phone calls last and stops one person from calling again and again in a day.

- **1st call** from a number: ends automatically at **10:00**.
- **2nd answered call** that day: ends at **5:00**.
- **After that**: calls from that number are rejected before they ring ("Blocked by limit") until midnight.
- **Emergency numbers** (112, 100, 101, 102, 108) and your **Always Allowed** people are never timed or blocked.

No login, no internet permission, no ads. **Your call data never leaves your phone.**

---

## 1. Install the APK (sideload)

### Build it
You need Android Studio (or JDK 17+ and the Android SDK).

```bash
./gradlew :app:assembleRelease
```

The APK is at `app/build/outputs/apk/release/app-release.apk`.
(It is signed with the debug key so you can test it. Use your own keystore before publishing on the Play Store.)

### Put it on the phone
**Option A – USB cable (fastest):**
1. On the phone: *Settings → About phone →* tap **Build number** 7 times to unlock Developer options.
2. *Settings → Developer options →* turn on **USB debugging**.
3. Connect the phone and run:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Option B – WhatsApp / Google Drive / file manager:**
1. Send the `.apk` file to yourself (WhatsApp "Document", Drive, or USB copy).
2. Open it on the phone. Android asks to **allow installs from this source** → allow → **Install**.
3. If Play Protect warns about an unknown app, tap **More details → Install anyway** (it is your own app).

---

## 2. Set CallGalli as the default phone app and call screener

CallGalli can only end calls if it is the **default phone app**, and can only reject calls before they ring if it is the **call screening app**. The app's onboarding asks for both. If you skipped them:

**Default phone app**
- Stock Android / Pixel / Motorola: *Settings → Apps → Default apps → Phone app →* **CallGalli**
- Samsung: *Settings → Apps → Choose default apps → Phone app →* **CallGalli**
- Xiaomi / Redmi / Poco: *Settings → Apps → Manage apps → ⋮ → Default apps → Phone →* **CallGalli**
- Oppo / Realme / OnePlus / Vivo: *Settings → Apps → Default apps → Phone / Dialer →* **CallGalli**

**Call screening ("Caller ID & spam app")**
- *Settings → Apps → Default apps → Caller ID & spam app →* **CallGalli**
- Or in CallGalli: *Settings → Phone setup → Call screening → Fix*.

**Battery (important on Xiaomi, Samsung, Oppo, Vivo)**
In CallGalli open *Settings → Battery settings* and follow the hint for your phone (Autostart on, battery "No restrictions" / "Unrestricted"). Without this, some phones pause apps and a call might not be cut on time.

To stop using CallGalli, just pick your old phone app again in *Default apps*.

---

## 3. Project structure

```
call-timer-app/
├── rules-engine/                     Pure Kotlin. No Android. All call-limit rules live here.
│   └── src/main/kotlin/com/callgalli/rules/
│       ├── RulesEngine.kt            Stages (Fresh → Limited → Blocked), decisions, counts
│       ├── CallClock.kt              Talk-time clock (pauses on hold), colour bands, 60/30 s warnings
│       ├── DayKeys.kt                "2026-11-20" day key → midnight reset with no background job
│       ├── PhoneNumbers.kt           +91 / 0 / spaces / dashes → one number; emergency list
│       └── RuleSettings.kt           10 / 5 min defaults, validated 1–60
│   └── src/test/kotlin/...           Unit tests, incl. the acceptance-test scenarios
│
└── app/src/main/java/com/callgalli/
    ├── CallGalliApp.kt               App + simple dependency container
    ├── data/                         Room: RuleEntity, CallSessionEntity, DailyUsageEntity, DAOs,
    │                                 CallRepository (engine ↔ database), contacts, settings
    ├── telecom/
    │   ├── CallGalliScreeningService Rejects Stage 3 numbers before they ring (kept in call log)
    │   ├── CallGalliInCallService    Default-dialer service: receives every call
    │   ├── CallTracker               Runs the timer, warnings, Call.disconnect() at 0
    │   ├── CallTimerService          Foreground "phone call" service (works with screen off)
    │   ├── CallNotifications         Incoming call, live countdown, "ended by limit" alerts
    │   └── Receivers                 Notification buttons, reboot clean-up
    ├── system/                       Roles (dialer/screening), battery + OEM settings, vibration
    └── ui/                           Compose + Material 3
        ├── onboarding/               4 screens explaining each permission
        ├── home/                     Today card, Active now card, numbers used today
        ├── contacts/                 Contacts & Rules (search, Always Allow, long-press reset)
        ├── blocked/                  Blocked Log + 7-day history per number
        ├── allowed/                  Always Allowed (emergency numbers locked on top)
        ├── settings/                 Limits, reset time, warnings, dark mode, clear data
        ├── dialer/                   Dial pad (Android requires one for the phone-app role)
        ├── incall/                   Incoming / in-call screen with the timer pill
        ├── components/, theme/       Shared cards, chips, pill; deep-teal theme, dark mode
```

All text is in `app/src/main/res/values/strings.xml`. To add Telugu or Hindi later, copy it to `values-te/` or `values-hi/` and translate.

## 4. How it works (short)

| Event | What happens |
|---|---|
| Number calls, it is at Stage 3 | `CallScreeningService` answers `setDisallowCall + setRejectCall` → phone never rings, saved as **Blocked by limit** |
| Call is picked up (state ACTIVE) | Engine uses up a stage and returns the limit (10 or 5 min). Clock starts now, not while ringing |
| On hold / call waiting | Clock pauses for the call on hold |
| Conference | Timed as one call (the first participant's timer) |
| 60 s and 30 s left | Full-width banner + short vibration + heads-up notification |
| 0 s | `Call.disconnect()`, notification "Call with Ravi ended by 10-min limit." |
| Midnight | Day key changes → everyone is Fresh. No alarm or background job |
| App killed mid-call | Android rebinds the service; the saved timer continues (no fresh 10 min) |
| Phone reboots mid-call | Call no longer exists → session closed using the last saved heartbeat. Stages stay used |

## 5. Run the tests

```bash
./gradlew :rules-engine:test
```

35 unit tests cover number matching, the midnight / custom reset, all three stages, emergency and Always Allowed numbers, hold pause, warnings, colours, and the acceptance-test scenarios (10:00 cut, 5:00 cut, third call rejected, reset).

## 6. Manual test checklist on a real phone

Use two phones (or a friend). Tip: set first-call = 1 min and repeat-call = 1 min in Settings to test quickly.

1. Friend calls → answer → call ends at the limit (±2 s); notification says "ended by … limit"; History shows **Ended by limit**.
2. Friend calls again → answer → ends at the repeat limit.
3. Friend calls a third time → your phone does not ring; it appears in **Blocked Log**.
4. Change the reset time to 2 minutes from now → after it passes, the friend is **Fresh** again.
5. Emergency numbers: check they show locked at the top of **Always Allowed**. Their "never timed" rule is covered by unit tests — **do not place real emergency calls to test.**
6. Toggle **Always allow** on the friend → their calls are never timed or blocked.
7. During a timed call: lock the screen, swipe CallGalli away from recents → the call is still cut on time.

## v1 scope

Built: onboarding, rules engine, in-call timer and warnings, Home, Contacts & Rules, Blocked Log, Always Allowed, basic Settings (English).
Later: "Unlock for today" override, backup/restore, Telugu & Hindi, charts, iOS.
