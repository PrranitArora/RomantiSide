# RomantiSide

![RomantiSide rose icon](artifacts/romantiside-icon.png)

**RomantiSide** brings together *romanticize* and *side quests*: one positive-psychology side quest delivered at a random daytime moment each day. A playful live camera and private friends ranking help people keep and share their progress. Mood surveys, check-ins, and Claude conversations are optional features inside this Android-only demonstration.

The initial audience is adult students and early-career professionals who want one doable next step when their day feels flat. This repository includes the implementation and a sourced investor/product research package. It has no verified commercial traction or efficacy results.

## Try the demonstration

Install [the debug Android APK](artifacts/romantiside-demo.apk) on Android 8 or newer. Android may ask you to allow installation from the application opening the file. This is a developer-signed demonstration, not a Play Store release.

1. Open **RomantiSide**, enable daily side quests, and allow Android notification permission. Choose your daytime window in **Settings**; the default is 9 am–8 pm. No check-in, profile, or backend is required.
2. Receive a side quest at a randomly chosen time in that window. Try the activity, then use **Done**, **30 minutes later**, or **Skip** directly from the notification. The test quest button lets you preview delivery.
3. When you choose to open the app, optionally record mood and energy, write a reflection, or start a Claude conversation. Review and confirm any reflection and ratings before saving. These check-ins do not send scheduled survey notifications.
4. Open **Wonder lens**. Allow the camera to try live pastel effects and illustrated stickers; save a moment to your local garden. An explicitly labelled illustration preview works without camera access.
5. Open **Circle** for the optional private ranking. The sample preview is labelled as fictional. Real counts require the companion service below and friends who opt in and exchange codes.

![Android home screen](artifacts/demo-today.png)

[View the Circle ranking screenshot](artifacts/demo-ranking.png) (explicitly fictional sample data). [View the five-minute personalization screen](artifacts/demo-yap.png).

[View onboarding](artifacts/demo-onboarding.png) and [the daytime-window settings](artifacts/demo-settings.png).

## What is implemented

| Feature | Demonstration behavior |
|---|---|
| Daily side quest | One automatic quest notification per local day at a persisted random time in the chosen daytime window. The default window is 9 am–8 pm; window boundaries can be selected between 8 am and 9 pm. |
| Optional in-app check-in | Local text reflection, five-point self-rated mood, three-point energy, optional Android speech transcription for up to two minutes. Speech may end at a pause. No scheduled check-in or mood-survey notifications. |
| Optional Claude conversation | A short text conversation through the development backend, with Android voice transcription as an input option. Claude can suggest a summary and a quest from the existing library; the user confirms the saved reflection and ratings. No Circle account is required. |
| Personalization from a longer reflection | A five-minute-style text or voice-transcribed “yap” becomes a draft preference profile and a visible system prompt for review. A separate generation step uses the confirmed profile, selected mood/energy, and previous activities to propose up to three new quests. |
| Automatic delivery | Opt-in quest delivery with 9 pm–8 am quiet hours and inexact Android alarms. Opening the app or rebooting preserves the saved daily selection rather than rerolling the time or sending another automatic quest. Existing scheduled check-in alarms are disabled. |
| Notification actions | Full quest instruction plus Done, 30 minutes later, and Skip. A requested snooze can repeat that quest; the app does not automatically add another daily quest notification. |
| Side quests | Eight curated activities work offline without check-ins or a profile. Optional generated suggestions enrich the queue using preferences and recent confirmed mood/energy when available. Activities draw on savoring, gratitude, strengths, self-compassion, connection, and optimism research. Generated activities receive server-defined principle labels and research links; those links do not validate each generated activity. |
| Wonder Lens | Live camera color treatments and procedural kawaii-inspired stickers. Three palettes, local captures, last five snapshots retained. No face analysis or generative scene transformation. |
| Personal garden | Real local completion counts, confirmed mood trends, reflections, and snapshots. Empty states contain no fabricated personal history. |
| Contact friends ranking | Explicit opt-in, native single-contact picker for a local label, private friend-code connections, completed-quest ranking over the last seven days or all time. Ties share a rank. |
| Privacy controls | Core experience works without an account. Claude is separately opt-in: chat text, selected mood/energy, and the profile/activity context used for personalization are sent through the backend to Anthropic. The backend does not persist chat transcripts or preference profiles. Local deletion, optional camera/microphone/reminder permissions, backup exclusion, no analytics or advertising SDKs. Separate Circle deletion removes shared profile, counts, and relationships. |

## What remains a proposal

The optional Claude integration uses a real model API when the development backend has a valid key, model access, and available credits. It is not a deployed production service or a clinically validated intervention. The local check-in still works without Claude; its text guess is a simple rule, and the user's confirmed ratings control mood history and recommendations. Model responses cannot reliably determine how someone feels.

There is no diagnostic mood inference, autonomous telephone call, SMS gateway, generative camera model, clinical assessment, or proven improvement in happiness. Voice input uses Android transcription followed by the same optional text conversation; it is not a telephone call or a real-time audio model. The camera filters run on the device. Richer voice interaction, optional computer vision for broad scene context, and adaptive recommendation models remain research proposals.

Circle is a working development service, not an externally deployed production backend. It uses pseudonymous bearer accounts and private codes; code possession establishes a mutual connection, not verified phone-number identity. There is no bulk address-book lookup. Counts are self-reported client events and are not cheat-proof. Counts update when each person opens or refreshes Circle; notification completions remain local until that sync. A production launch needs HTTPS hosting, stronger account recovery/invitation controls, operational security, abuse prevention, and a reviewed privacy policy.

## Research and investor materials

- [Investor and product brief](research/investor-brief.md): competitor matrix, differentiated proposition, product expansion, AI/ML architecture, go-to-market, illustrative economics, investor objections, and a 60-second pitch.
- [Positive psychology and measurement](research/psychology.md): source-linked activity matrix, evidence limits, check-in design, camera rationale, outcome measures, and feasibility/randomized study plans.
- [Social ranking product decision](research/social-ranking.md): consent model, implementation, and a proposed test of whether rankings help or discourage use.

The strongest positioning to test is **one unexpected invitation each day to do something worthwhile in the real world**. The user controls the daytime window; the app chooses a random moment within it. Cute design and AI check-ins alone are already offered by competitors. The proposed exercises are adaptations of research; neither this delivery pattern nor the exact app has demonstrated improved wellbeing. Changes in self-reported mood do not establish causation. Financial figures in the brief are explicit scenarios, not market estimates or forecasts.

## Build

Prerequisites: JDK 17, Android SDK platform 35 and build tools, `ANDROID_HOME` or an Android Studio `local.properties`. The Gradle wrapper downloads Gradle 8.13 if needed. Android Studio can open the `android` folder directly.

Windows:

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

macOS/Linux:

```sh
cd android
./gradlew :app:assembleDebug :app:lintDebug
```

The APK is generated at `android/app/build/outputs/apk/debug/app-debug.apk`. No AI API key, npm build, or web server is needed for the offline Android features. The optional Claude feature reads its key only from the backend environment; no key belongs in the Android source, WebView, or APK. The UI is packaged local HTML/CSS/JavaScript in a native Java host. Native Android owns permissions, speech, alarms, notification actions, the contact picker, and backend networking. External web content cannot enter the privileged WebView.

## Run Claude check-ins locally

Claude is an optional in-app feature. Daily quest notifications and the curated activity bank work without it, a profile, or a mood survey.

Use Node 24 or later. Create a private environment file outside this repository containing `ANTHROPIC_API_KEY` with your own Claude API key. Start the backend from the repository root, replacing the placeholder with that file's path:

```sh
node --env-file=<private-env-file> server/server.mjs
adb reverse tcp:8787 tcp:8787
```

The debug APK reaches this service through ADB reverse, using the same backend address as Circle. Claude chat uses a separate temporary session and does not require joining Circle. Open the check-in and accept the Claude data-sharing prompt before sending anything. Chat text, recent conversation context, and your approved quest profile (if present) are sent to your backend and Anthropic. Mood and energy suggestions remain tentative until you confirm them. The backend does not write chat transcripts to its data file; this is not a claim about Anthropic's retention. Only a reflection you confirm is saved to the local journal. Android's speech provider may process audio separately when voice input is used.

Set `ANTHROPIC_MODEL` to override the backend's default `claude-sonnet-4-6`. Optional `ANTHROPIC_WORKSPACE_ID` supplies the workspace header: keys scoped to a single Anthropic workspace do not require it; multi-workspace keys do. See the [server setup](server/README.md#optional-claude-setup) for configuration and [Anthropic authentication documentation](https://platform.claude.com/docs/en/manage-claude/authentication#select-a-workspace) for the distinction. If a key has been pasted into a chat or another shared surface, replace it in the private environment file with a rotated key before wider use. API requests can incur charges on the backend owner's Anthropic account.

If the backend, credentials, or model are unavailable, use the local check-in and existing side quests. No offline response is presented as Claude output.

## Personalize new side quests

Use the longer reflection to describe what you enjoy, your boundaries, accessibility needs, and how your day feels. Voice input uses Android transcription and can pause before five minutes; text remains editable. The backend accepts up to 12,000 characters, extracts a bounded draft profile, and builds a visible system prompt. Review the profile before using it. The generated prompt combines fixed wellbeing boundaries with your preferences as untrusted context; it does not give user text permission to replace the fixed instructions.

New quest generation uses that profile, your selected mood/energy, and recent activity history. The model proposes candidates, then the server checks their structure, sets research labels and visual metadata, and filters repeated titles, actions, activity keys, and common paraphrases. The Android client also keeps local lifetime history of generated activities. A generated quest is retired after **Done** or **Skip**; the curated offline bank can recur on another day. This history is local app data and disappears if that data is deleted.

The server accepts at most 100 prior activities under the shared 64 KiB request limit. The app sends its recent 40 generated activities plus the eight curated activities, while retaining older generated activity records locally. Duplicate screening catches exact repeats and some synonymous actions, such as a walk versus a stroll; it cannot guarantee detection of every semantic duplicate. The result may contain fewer than three quests. Up to 24 active generated quests may wait in the queue before generation pauses. Retired cards are removed from the active bank while their duplicate history is kept. Storage failures are reported rather than silently presented as successful saves. Generated suggestions have not each been clinically reviewed, and schema validation is not a comprehensive safety assessment.

## Run real Circle rankings locally

Use Node 24 or later. In a terminal from the repository root:

```sh
node server/server.mjs
```

For each attached Android device running the debug APK:

```sh
adb reverse tcp:8787 tcp:8787
```

Open Circle, choose your own display name, and exchange private codes manually with another participant. Each friend needs the app connected to the same service and must choose to join. The debug APK uses loopback via ADB reverse; only loopback cleartext is allowed in debug. The release resource leaves the service URL unset until an HTTPS deployment is configured. See [the server instructions](server/README.md) for its API, persistence, and limitations. Server data is excluded from Git.

## Verify

```sh
node --test tests/logic.test.cjs
node --test server/server.test.mjs
node --test server/chat.test.mjs
node --test server/personalization.test.mjs
```

Native integration tests use the Android framework directly, without a third-party test runner:

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.tinywonder.app.test/com.tinywonder.app.SmokeTestRunner
```

The native runner preserves and restores original local app state and reminder scheduling. It exercises the receiver and storage behavior using synthetic data. Notification posting is skipped if system permission is denied or quiet hours are active. UI smoke tests use synthetic reflections only. Tests must not be used to infer clinical effectiveness.

With the local Circle server running and ADB reverse configured, add `-e circle true` before the test component to exercise real native HTTP join, sync, friend ranking, and deletion. Those tests clean up their synthetic profiles and restore the original Circle credentials.

Verified on September 29, 2026: **24 Android integration checks passed**; **46 JavaScript/backend tests passed**. APK build and Android lint succeeded. Live Circle and synthetic Claude chat, profile extraction, and strict-format quest generation were verified in the preceding integration. See [validation details](tests/VALIDATION.md) for scope and remaining manual checks.

For optional source formatting, run `npm ci` and `npm run format`. The packaged app needs no npm runtime dependencies.

## Known limits

- Android may delay alarms during battery restrictions. Force-stopping an app prevents reminders until it is reopened; permission or channel denial also prevents delivery.
- The Android speech provider may process audio remotely. The app asks before voice use and stores only confirmed text; provider behavior is outside the app's local storage guarantee.
- Claude conversations require an online backend and Anthropic access. They provide general wellbeing reflection, not diagnosis, treatment, reliable emotion detection, or emergency monitoring. Backend tests with stubbed model responses do not establish clinical effectiveness or confirm a live key works.
- SharedPreferences stores local reflections and small photos in app-private storage, not an application-level encrypted database. Do not use this prototype for highly sensitive records.
- The app targets API 35 and was exercised on a Pixel 6a running API 36. Wider device, screen-reader, offline speech, and long-duration battery tests remain before release.
- Friend codes should be shared privately. A contact label is not evidence that an account belongs to that person. Do not present the ranking as a mental-health or wellbeing score.
