# Demonstration validation

Verified September 29, 2026 with Node 24.18.0, JDK 17, Android Gradle Plugin 8.7.3, Gradle 8.13, and a physical Pixel 6a running Android API 36. The application compiles and targets API 35, with minimum API 26.

## Results

- Android debug APK and instrumentation APK build: passed.
- Android debug lint: passed with zero errors. Warnings remain for synchronous preference commits, target/dependency versions, and the intentional JavaScript-enabled local WebView. The WebView only loads bundled assets; remote content does not receive the bridge.
- Six Node app-logic tests: passed. Includes timezone-stable social event IDs, confirmed recent mood selection, local-day completion/skip behavior, unrated replies, normalized state, and quest-bank validity.
- Five real-HTTP backend tests: passed. Includes isolated/private friendships, ties, idempotent counts, rolling window/all-time behavior, persistence across restart, deletion, authorization, input validation, atomic rejection, and registration rate limiting.
- Seventeen Android integration checks: passed, zero failed, zero skipped. These cover concurrent native/UI persistence, notification Done/Skip, inline RemoteInput replies, opt-out, snooze state and quiet-time timestamps, data clearing, actual WebView interactions, notification posting with actions, and native Circle HTTP operations.

The actual WebView test enters a synthetic mood/energy check-in, verifies its saved state and recommendation, completes a quest twice to test deduplication, submits neutral feedback, and verifies a rendered mood bar has nonzero computed height under the content-security policy. Empty garden, lens-off, and Circle opt-in screens are checked too.

The native Circle check creates disposable profiles, syncs a completion, repeats the sync, connects the profiles, checks mutual visibility, and deletes the profiles. Original local state, Circle credentials, and reminder settings are restored after testing. Test reports expose neither bearer tokens nor invite codes.

The home and Circle sample-ranking screens were visually inspected on the device. System-bar overlap found in the first build was fixed by applying insets to the parent container. Sample ranking rows are explicitly labelled as fictional.

## Remaining manual and release checks

The automated suite does not validate live microphone transcription accuracy, camera appearance across physical scenes, selecting a real address-book contact, battery behavior over several days, reboot delivery on every manufacturer, screen-reader usability, production HTTPS deployment, or any clinical outcome. No mood or happiness efficacy result is implied by software test success. The Circle service is a development service with self-reported completion counts.

GitHub Actions is configured to run Node tests, build both APKs, and run lint; a local successful build does not confirm a hosted CI run until GitHub executes it.
