# Call Timer

An Android app that limits how long phone calls last and stops one person from calling again and again in a day.
**You keep using your phone's normal dialer** (Google Phone, Samsung Phone, Mi Dialer…). Call Timer works quietly in the background.

- **1st call** with a number: ends automatically at **10:00**.
- **2nd answered call** that day: ends at **5:00**.
- **After that**: calls from that number are rejected until midnight and logged as "Blocked by limit".
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
**Option A – USB cable:**
1. On the phone: *Settings → About phone →* tap **Build number** 7 times to unlock Developer options.
2. *Settings → Developer options →* turn on **USB debugging**.
3. Connect the phone and run:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

**Option B – WhatsApp / Google Drive / file manager:**
1. Send the `.apk` file to yourself (WhatsApp "Document", Drive, or USB copy).
2. Open it on the phone → **allow installs from this source** → **Install**.
3. If Play Protect warns about an unknown app, tap **More details → Install anyway**.

---

## 2. Set it up (about 1 minute)

You do **not** change your default phone app. The app's 4 setup screens ask for:

| What | Why | Required? |
|---|---|---|
| **Call permissions** (Phone + Call logs) | See when a call rings/starts/ends, who it is with, and end it when time is up | **Yes** |
| Contacts | Show "Ravi" instead of a number | Recommended |
| Notifications | Time left, warnings, "ended by limit" | Recommended |
| **Show timer over calls** ("Display over other apps") | The floating "07:42 left" pill on top of your phone app's call screen | Recommended |
| Call screening ("Caller ID & spam app") | Numbers **not saved in contacts** are blocked before the phone even rings | Optional |
| Battery: no restrictions | Keeps the watcher alive on Xiaomi, Samsung, Oppo, Vivo | **Strongly recommended** |

### "Restricted setting" message (Android 13+ sideloaded apps)
If Android blocks the **Call logs** permission with "Restricted setting":
1. *Settings → Apps → Call Timer →* tap **⋮** (top right) → **Allow restricted settings**.
2. Go back to Call Timer → *Settings → Phone setup → Call permissions → Fix*.

### Battery (important)
In Call Timer open *Settings → Battery settings* and follow the hint for your phone (Autostart on, battery "No restrictions" / "Unrestricted"). Without this, some phones stop background apps and a call might not be cut on time.

Call Timer shows a small silent "Watching call time" notification all the time. That keeps it alive; you can't swipe it away.

---

## 3. What works differently because you keep your own phone app

Android gives full call control only to the default phone app. As a background app, Call Timer gets less information:

| Rule | Status |
|---|---|
| 10 min / 5 min / blocked until midnight | ✅ Works for incoming and outgoing |
| Third call from a **saved contact** | ✅ Rejected, but the phone may ring for a split second first |
| Third call from an **unsaved number** | ✅ Never rings (if call screening is on) |
| Timer starts when the call connects | ✅ Incoming. ⚠️ Outgoing: starts when you dial (Android doesn't tell other apps when the other person answers), so about 10–30 s of ringing counts |
| Pause on hold | ❌ Not possible: hold isn't visible to other apps |
| Call waiting / conference | ⚠️ The first call is timed. If you switch to the waiting call, "end" cuts the call you are on now |
| Dial a blocked number | ⚠️ Can't stop the dial. You get a warning with a **Hang up** button, and the call is cut at 5 min |
| Dual SIM | ✅ Counted per number, not per SIM |
| Emergency numbers | ✅ Never timed. Android also never lets apps end an emergency call |

---

## 4. Project structure

```
call-timer-app/
├── rules-engine/                     Pure Kotlin. No Android. All call-limit rules live here.
│   ├── src/main/kotlin/com/callgalli/rules/
│   │   ├── RulesEngine.kt            Stages (Fresh → Limited → Blocked), decisions, counts
│   │   ├── CallClock.kt              Talk-time clock, colour bands, 60/30 s warnings
│   │   ├── DayKeys.kt                "2026-11-20" day key → midnight reset with no background job
│   │   ├── PhoneNumbers.kt           +91 / 0 / spaces / dashes → one number; emergency list
│   │   └── RuleSettings.kt           10 / 5 min defaults, validated 1–60
│   └── src/test/kotlin/...           35 unit tests, incl. the acceptance-test scenarios
│
└── app/src/main/java/com/callgalli/
    ├── CallGalliApp.kt               App + simple dependency container
    ├── data/                         Room: RuleEntity, CallSessionEntity, DailyUsageEntity, DAOs,
    │                                 CallRepository (engine ↔ database), contacts, settings
    ├── telecom/
    │   ├── CallMonitor               Call state machine (RINGING → OFFHOOK → IDLE), timer, warnings, cut-off
    │   ├── CallMonitorService        Always-on foreground watcher (works with screen off / app closed)
    │   ├── TimerOverlay              Floating pill + warning banner over the phone app
    │   ├── CallGalliScreeningService Optional: rejects unsaved Stage 3 numbers before they ring
    │   ├── CallNotifications         Live countdown, warnings, "ended by limit", over-limit dial warning
    │   └── Receivers                 Phone-state / outgoing-call broadcasts, Hang up button, boot
    ├── system/                       Permissions & roles, battery + phone-maker settings, vibration
    └── ui/                           Compose + Material 3: onboarding, home, contacts, blocked log,
                                      always allowed, settings, shared components and theme
```

All text is in `app/src/main/res/values/strings.xml`. To add Telugu or Hindi later, copy it to `values-te/` or `values-hi/` and translate.

## 5. Run the tests

```bash
./gradlew :rules-engine:test
```

Render the screens to PNG (saved in `app/screenshots/`, no phone needed):

```bash
./gradlew :app:testDebugUnitTest -Proborazzi.record=true
```

## 6. Test checklist on a real phone

Tip: in Settings, set first call = 1 min and repeat call = 1 min to test quickly. Use a friend's phone.

1. Friend calls → answer in your normal phone app → the floating pill counts down → the call ends at the limit (±2 s) → notification "Call with … ended by 1-min limit". History shows **Ended by limit**.
2. Friend calls again → answer → ends at the repeat limit.
3. Friend calls a third time → rejected right away and listed in **Blocked Log**.
4. You call the friend → you get the "limit reached" warning with **Hang up**.
5. Set the reset time to 2 minutes from now → after it passes, the friend is **Fresh** again.
6. Turn on **Always allow** for the friend → their calls are never timed or rejected.
7. During a timed call, lock the screen and swipe Call Timer away from recents → the call is still cut on time.
8. Emergency numbers: check they show locked in **Always Allowed**. **Do not place real emergency calls to test.**

## v1 scope

Built: onboarding, rules engine, in-call timer and warnings, Home, Contacts & Rules, Blocked Log, Always Allowed, basic Settings (English).
Later: "Unlock for today" override, backup/restore, Telugu & Hindi, charts, iOS.
