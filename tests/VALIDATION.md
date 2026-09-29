# Demonstration validation

RomantiSide version 0.2.0-demo adds optional Claude conversations, a five-minute preference reflection, reviewable quest profiles and system instructions, and generated side quests with duplicate screening. The earlier rose branding was visually checked on the Pixel. APK metadata confirms the RomantiSide label and rose launcher resource. The launcher includes an Android 13+ monochrome variant; the notification and header use the same rose design. Existing application/storage identifiers are retained so installation updates preserve local data.

Verified September 29, 2026 with Node 24.18.0, JDK 17, Android Gradle Plugin 8.7.3, Gradle 8.13, and a physical Pixel 6a running Android API 36. The application compiles and targets API 35, with minimum API 26.

## Results

- Android debug APK and instrumentation APK build: passed.
- Android debug lint: passed with zero errors. Warnings remain for synchronous preference commits, target/dependency versions, and the intentional JavaScript-enabled local WebView. The WebView only loads bundled assets; remote content does not receive the bridge.
- Eleven Node app-logic tests: passed. Includes timezone-stable social event IDs, confirmed recent mood selection, local-day curated versus lifetime generated retirement, persistent duplicate history, server/browser dedup parity, bounded conversation history, unrated replies, and quest-bank validity.
- Thirty-one backend tests: passed (five Circle HTTP tests, seventeen Claude transport/API tests, nine personalization tests). Claude tests use controlled upstream responses and cover credentials isolation, input/output bounds, urgent-support behavior, quotas, timeouts, strict tool schemas, profile prompts, and generated quest duplicate checks. Circle coverage includes isolated/private friendships, ties, idempotent counts, rolling window/all-time behavior, persistence across restart, deletion, authorization, input validation, atomic rejection, and registration rate limiting.
- Twenty-three Android integration checks: passed, zero failed, zero skipped. These cover concurrent native/UI persistence, notification Done/Skip, inline RemoteInput replies, opt-out, snooze state and quiet-time timestamps, data clearing, actual WebView interactions, notification posting with actions, bounded chat/profile/quest transport, storage overflow handling, generated quest retirement and stale-action replay, plus the profile/chat WebView flow. The optional native Circle HTTP check also passed against the updated backend.

The actual WebView test enters a synthetic mood/energy check-in, verifies its saved state and recommendation, completes a quest twice to test deduplication, submits neutral feedback, and verifies a rendered mood bar has nonzero computed height under the content-security policy. Empty garden, lens-off, and Circle opt-in screens are checked too. A separate test-only native transport facade captures AI requests without making network calls. That test verifies explicit consent, profile approval, generated quest deduplication, native persistence, editable chat summaries, and rejection of late canceled replies. Full transcripts and raw preference notes are not written to state.

The native Circle check creates disposable profiles, syncs a completion, repeats the sync, connects the profiles, checks mutual visibility, and deletes the profiles. Original local state, Circle credentials, and reminder settings are restored after testing. Test reports expose neither bearer tokens nor invite codes.

The home, Circle sample-ranking, five-minute yap, approved-profile, and personalized queue screens were visually inspected on the device. System-bar overlap found in the first build was fixed by applying insets to the parent container. Sample ranking rows are explicitly labelled as fictional.

## Remaining manual and release checks

The automated suite does not validate live microphone transcription accuracy, camera appearance across physical scenes, selecting a real address-book contact, battery behavior over several days, reboot delivery on every manufacturer, screen-reader usability, production HTTPS deployment, or any clinical outcome. No mood or happiness efficacy result is implied by software test success. The Circle service is a development service with self-reported completion counts.

GitHub Actions is configured to run Node tests, build both APKs, and run lint; a local successful build does not confirm a hosted CI run until GitHub executes it.

## Live Claude verification

Synthetic text only was used for live Claude Sonnet 4.6 requests. Chat and profile extraction returned valid responses. The initial quest generation returned a rejected malformed response; strict tool schemas were added and a subsequent actual HTTP request returned three valid generated quests. There are no automatic retries of billed model requests. Local validation still rejects invalid values, and the UI keeps drafts when a request fails. Live software success does not establish clinical efficacy, perfect semantic deduplication, or comprehensive activity safety.

Full transcripts and raw longer reflections stay in memory unless the user explicitly confirms a short check-in summary. Approved profiles, generated activity history, and queued quests are saved locally. Provider retention remains separate. Credentials were loaded from a private environment file outside the repository.
