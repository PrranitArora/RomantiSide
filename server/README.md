# RomantiSide development backend

This optional local backend supports Claude wellbeing check-ins and private side-quest rankings. Claude uses a separate temporary chat session; it does not require a Circle profile or sharing completion counts. Circle connects friends who knowingly exchange private invitation codes. This is a prototype, not a deployed production service. Node.js 24 or newer is required; it has no external dependencies.

## Run locally

From this directory, run `npm start` or `node server.mjs`. Run `npm test` for the integration tests. The default listener is `http://127.0.0.1:8787`. Check `GET /health` to confirm it is ready.

`HOST`, `PORT`, and `DATA_FILE` can override the listener and storage file. By default, `data/state.json` is created on the first write; the directory is ignored by Git. If you override `DATA_FILE`, keep that path outside version control. Use one server process per data file. Writes use a temporary file, file flush, and atomic rename; failed writes do not replace the in-memory state. Existing malformed state causes startup to fail instead of silently losing profiles. Protect local backups as you would the original file.

For an Android device or emulator connected through ADB, run `adb reverse tcp:8787 tcp:8787` to forward the device's loopback port to this host. The Android debug integration uses the loopback URL. Start the server before opting into friends; no public deployment is performed here. HTTP is appropriate only for this loopback development setup. A production service needs HTTPS, a production credential lifecycle, operational security, abuse prevention, and a privacy review. Changing HOST to a network address exposes the demo and is not a production deployment procedure.

## Optional Claude setup

Keep `ANTHROPIC_API_KEY` in the server environment or a private environment file outside this repository. Do not place a provider key in Android resources, JavaScript, the APK, or committed example files. From the repository root, replace the placeholder with your private file's path and run:

```sh
node --env-file=<private-env-file> server/server.mjs
```

`ANTHROPIC_MODEL` overrides the default `claude-sonnet-4-6`. Set optional `ANTHROPIC_WORKSPACE_ID` to send the `anthropic-workspace-id` header. A valid key, access to the chosen model, and available API credits are required; calls are billed to the backend owner's Anthropic account. Keys scoped to one workspace can omit the workspace header; multi-workspace keys require it. A workspace ID identifies request context and does not replace authentication. See [Anthropic's workspace selection instructions](https://platform.claude.com/docs/en/manage-claude/authentication#select-a-workspace).

If a key was pasted into a chat or another shared surface, rotate it and update the private environment file before wider use. Restart the backend after changing its environment. The provider key is never returned to the Android client. Starting this server or passing tests with stubbed model responses does not prove that a live key, model entitlement, or billing balance works.

The Android debug build uses the same server URL for Claude and Circle. The release URL remains unset until an HTTPS backend is configured. No provider key is required to use offline check-ins, camera effects, local quests, or the Circle-only server.

## Claude consent and information shared

Before using Claude, the app asks the user to opt into sharing conversation text, selected mood/energy, and any profile/activity context used for personalization with this backend and Anthropic. Android voice input first uses the system speech provider; its transcript can then be sent through the same text chat. The backend does not send audio to Claude or initiate telephone calls. Android's speech provider may process audio remotely under its own behavior.

Chat sessions are temporary and separate from Circle credentials and invitations. The backend processes the supplied conversation context but does not persist chat transcripts to the Circle data file. This does not make any promise about Anthropic's retention of API traffic. The Android journal stores a reflection only after the user reviews and confirms it with their ratings; model output does not automatically become a mood measurement.

The assistant is for general wellbeing reflection. Ordinary check-in recommendations use IDs from the existing research-informed library. The separate personalization flow can generate new activities from the user's reviewed preferences; it checks the candidate schema, assigns fixed research metadata, and filters duplicates. Neither flow establishes clinical benefit, diagnoses a condition, reliably infers emotions, provides emergency monitoring, or sends SMS. Generated activities have not each been clinically reviewed. If the service is unavailable, the app's local check-in and quests remain available.

## Claude API contract

All requests below are `POST` with JSON and share the 64 KiB body limit. Chat, profile extraction, and quest generation use a temporary chat bearer token, not a Circle token. No chat transcript or preference profile is persisted by this server. The client supplies needed context on each request.

### `POST /v1/chat/session`

Request: `{}`. Response **201**: `{ "token": "<43-character-secret>", "expiresAt": "<ISO timestamp>" }`. The default lifetime is two hours. Only the token hash and expiry are retained in memory, so restarting the service invalidates sessions. This local demo permits session creation without an account; its session token is not a production user-authentication system.

### `POST /v1/chat`

Use `Authorization: Bearer <chat-token>`. Request fields are `messages`, `mood`, `energy`, and an optional validated `profile`. Messages alternate `user` and `assistant`, start and end with `user`, and contain only `role` and `content`; each content string has 1–2,000 characters, with 12,000 total and at most 12 messages. Since the sequence must end with the user, the largest accepted alternating sequence has 11 messages. Mood is an integer 1–5 or `null`; energy is an integer 1–3 or `null`.

Response **200** has `reply` (up to 1,800 characters), `summary` (up to 400), nullable `suggestedMood`, `suggestedEnergy`, and `suggestedQuestId`, plus boolean `urgentSupport`. Quest IDs are restricted to the eight curated activities. Explicit selected mood/energy override model guesses. An urgent-support result clears ratings and the quest suggestion; this is a model flag, not reliable crisis detection or monitoring. The user must review and confirm any reflection and ratings before local saving.

### `POST /v1/chat/profile`

Use the same chat bearer token. Request: `{ "text": "<longer reflection, up to 12,000 characters>" }`. Response **200**: `{ "profile": { ... }, "systemPrompt": "<reviewable generated prompt>" }`.

The exact profile schema is:

| Field | Type and limit |
| --- | --- |
| `summary` | Nonempty string, up to 1,200 characters |
| `preferences` | Up to 12 nonempty strings, each up to 120 characters |
| `avoid` | Up to 12 nonempty strings, each up to 120 characters |
| `moodContext` | String up to 300 characters; empty is allowed |
| `energyStyle` | String up to 120 characters; empty is allowed |

Extra profile fields are rejected. The prompt combines fixed behavioral boundaries with JSON-encoded untrusted preference context. Text about overriding rules is not an authorized prompt instruction. The returned prompt makes personalization reviewable; it is not evidence of complete prompt-injection resistance.

### `POST /v1/chat/quests`

Use the same chat bearer token. Request fields: validated `profile`, `history`, selected `mood` or `null`, and selected `energy` or `null`. History accepts at most 100 activities, each with `title` up to 80 characters, `action` up to 500, and optional `activityKey` up to 80. Other client metadata is not sent to the model. The shared body-size limit can be reached before 100 activities.

One model call requests up to six candidates, yielding at most three accepted quests. Each candidate has exactly `title`, `action`, `mechanism`, `minutes`, and `activityKey`. Mechanism must be one of `savoring`, `gratitude`, `compassion`, `strengths`, `connection`, or `agency`; duration is an integer from one to five minutes. The activity key is a lowercase hyphenated slug. Invalid candidates and duplicates are omitted. IDs are derived from a SHA-256 hash of the normalized action. `principle`, `icon`, `color`, `why`, and the `evidence` URL are assigned by the server, not taken from model output.

Response **200**: `{ "quests": [ ... ], "duplicatesFiltered": 0, "exhausted": false }`. `exhausted` means fewer than three quests were accepted from that call; it does not prove that all possible new activities have been exhausted. There is no automatic retry or extra billed model call to fill the list.

Duplicate checks cover normalized titles, actions, stable activity keys, several activity synonyms, common intentions, and high token overlap. They compare supplied history and candidates in the same batch. They do not guarantee detection of all semantic duplicates or clinical safety. The Android client retains older generated activity records locally and permanently retires a generated quest on Done or Skip until local data is deleted; curated offline quests can recur on a new day.

### Claude limits and errors

Defaults: two concurrent provider requests; 30 requests per session/hour, 60 per source IP/hour, and 200 total/day. Profile extraction and generation consume the same quotas as chat. Session creation permits 10/IP/hour and 100 total/hour, with at most 500 active sessions. Counters live in process memory and reset on restart. `CHAT_SESSION_TTL_MS`, `CHAT_MAX_SESSIONS`, `CHAT_SESSIONS_PER_IP_HOUR`, `CHAT_SESSIONS_PER_HOUR`, `CHAT_MESSAGES_PER_SESSION_HOUR`, `CHAT_MESSAGES_PER_IP_HOUR`, `CHAT_MESSAGES_PER_DAY`, and `CHAT_CONCURRENCY` configure these bounded limits.

Responses use **400** for invalid input, **401** for an expired/unknown chat session, **429** for local quotas/concurrency, **502** for an unusable provider result, **503** for unavailable configuration or service, and **504** for a provider timeout. The response contains a short error code and message, never the provider key or raw upstream error body. Model success still requires separate live validation; test stubs do not prove provider access.

## Circle consent and information shared

Profile creation and sharing are optional. A user chooses a display name and receives a random 12-character invitation code. **Sharing that code is permission for its recipient to connect the two profiles mutually.** Both people then see each other's display name and completed-quest counts. Keep the code private and exchange it knowingly; there is no public discovery, contact upload, contact matching, or friends-of-friends leaderboard. Possession of another person's invitation code is required. The code is not an account credential and cannot authenticate as that person.

Circle stores only a pseudonymous display name, profile ID, invitation code, token hash, friendship IDs, and `{id, at}` completion events. Circle endpoints reject extra fields such as mood, notes, photos, or contacts. The app should keep any contact nickname local. Leaderboards return aggregate counts only, not completion timestamps or event IDs. No analytics, advertising, email, or SMS provider is used. Bearer tokens are returned once at creation, held privately by the client, and stored only as SHA-256 hashes on the server. Do not log, commit, or share tokens.

Counts are **self-reported**, not validated wellness outcomes. The server enforces duplicate and volume limits but trusts a client that reports finishing a quest; this is not an anti-cheat system. Rankings must not be marketed as measures of happiness, mental health, or effort. An empty account is ranked at zero. Ties share a rank using competition ranking (1, 1, 3). No public leaderboard exists.

Deleting a profile removes its token, code, completion history, and reciprocal friendships from this server file. It does not erase independently retained backups or the user's local app journal. There is no password recovery, profile editing, code rotation, or individual unfriend endpoint in this prototype; leave sharing by deleting the profile. A new profile receives a new credential and code.

## Circle API contract

All responses are JSON with `Cache-Control: no-store`. POST requests require `Content-Type: application/json`. Circle operations below, except profile creation, require `Authorization: Bearer <token>` using the Circle profile token. Chat has its own session credential. No browser CORS access is enabled. Error responses are `{ "error": "machine_code", "message": "Short explanation" }` with the appropriate HTTP status.

### `POST /v1/profile`

Request: `{ "displayName": "Aki" }`.

Response **201**: `{ "token": "<43-character-secret>", "code": "<12-character-code>", "displayName": "Aki" }`.

Names are trimmed and NFC-normalized, with 1–32 Unicode code points, at most 128 UTF-8 bytes, and no control or bidi override characters. Pseudonyms are encouraged. Duplicate display names are allowed because profile IDs identify people. Codes use `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`; code input accepts lowercase and surrounding whitespace.

### `POST /v1/events`

Request: `{ "events": [{ "id": "light:1790700000000", "at": 1790700000000 }] }`.

Response **200**: `{ "accepted": 1, "duplicates": 0, "totalCompleted": 1 }`.

The client supplies a stable event ID, normally `questId:completionTimestamp`, and an integer epoch-millisecond timestamp. IDs begin with an alphanumeric character and contain only letters, numbers, colons, underscores, or hyphens, up to 128 characters. IDs are unique per profile across all time. Resending an existing ID is a successful no-op, even when a different valid timestamp is supplied; it cannot move the original event into a different ranking window. A request is atomic: validation or daily-cap failure saves none of its new events.

Send batches of at most **500 events** under the **64 KiB request limit**. The server accepts historical timestamps from 1970 onward, and rejects timestamps more than five minutes in the future. At most **16 new completions per UTC day** are accepted as a development-service volume guard, covering both curated and generated quests. The timestamp string in an ID is not a trusted date field; UTC-day limits use `at`. This guard is not anti-cheat validation. Events slightly ahead of the server clock are stored but do not count until their timestamp arrives. There is no 30-day historical cutoff. Chunk larger histories, keeping stable IDs when retrying. The prototype caps one profile at 20,000 events, the server at 100,000 events, 500 profiles, and a 32 MiB state file; capacity errors use 507.

### `POST /v1/friends`

Request: `{ "code": "<friend's-private-code>" }`.

Response **200**: `{ "friend": { "id": "<opaque-profile-id>", "displayName": "Sam" }, "added": true }`.

The relationship is mutual immediately. Repeating the request returns `added: false` without duplicates. A caller cannot add itself. Unknown codes return 404. Profiles can have at most 100 direct friends. Fetch the leaderboard separately after adding; native clients may combine the returned `friend` and `added` fields with that response for the UI.

### `GET /v1/leaderboard?period=week|all`

The default period is `week`. This means the **last seven times 24 hours**, not a calendar week. `all` includes all accepted historical events through the current server time.

Response **200**:

```json
{
  "period": "week",
  "windowStart": "2026-09-22T18:00:00.000Z",
  "people": [
    { "id": "<opaque-id>", "displayName": "Aki", "completed": 3, "isYou": true, "rank": 1 }
  ],
  "selfReported": true
}
```

`windowStart` is an ISO UTC timestamp for `week` and `null` for `all`. The response contains only the requester and direct friends. Ties receive the same rank; a stable name/ID order determines display order within a tie. No friendship codes or credentials are returned.

### `DELETE /v1/profile`

Response **200**: `{ "deleted": true }`. The credential stops working immediately. There is no request body and no recovery operation.

### `GET /health`

Public response **200**: `{ "ok": true, "service": "tiny-wonder-friends-demo" }`. It reveals no people, counts, storage path, or credentials.

## Limits and validation

The server limits each source IP to 240 requests/minute, profile creation to 10/hour per IP, authenticated use to 90/minute per profile, and friendship attempts to 10/minute per profile. It does not trust `X-Forwarded-For`. Rate-limited responses use 429 and a 60-second retry hint; creation limits can require waiting for the hour window to reset. Limits are process-memory based and restart with the server. This is suitable for a local demonstration, not distributed attack resistance. Invalid auth uses 401, malformed data 400, wrong content type 415, oversized requests 413, and daily/friend caps 422.

Tests create isolated temporary storage and exercise real HTTP requests between multiple profiles, authentication, friendship isolation, tied ranks, historical and rolling-window counts, duplicate sync, validation, daily caps, deletion, durable restart, and rate limits. Tests do not read or overwrite normal server data.

Run `node --test server/chat.test.mjs` from the repository root for the Claude integration tests. These use controlled upstream responses rather than spending API credits. Run a separate, explicitly configured live check to verify provider access; use synthetic reflection text for that check.

Run `node --test server/personalization.test.mjs` for profile and generation validation, prompt boundaries, deterministic activity identities, repeated and paraphrased activity filtering, and count consistency. These test implementation behavior, not a model's adherence, clinical benefit, or comprehensive semantic duplicate detection.
