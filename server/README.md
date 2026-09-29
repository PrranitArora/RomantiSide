# RomantiSide private friends demo API

This is an optional, working local backend for sharing side-quest completion counts with friends who knowingly exchange private invitation codes. It is a prototype, not a deployed production service. Node.js 24 or newer is required; it has no external dependencies.

## Run locally

From this directory, run `npm start` or `node server.mjs`. Run `npm test` for the integration tests. The default listener is `http://127.0.0.1:8787`. Check `GET /health` to confirm it is ready.

`HOST`, `PORT`, and `DATA_FILE` can override the listener and storage file. By default, `data/state.json` is created on the first write; the directory is ignored by Git. If you override `DATA_FILE`, keep that path outside version control. Use one server process per data file. Writes use a temporary file, file flush, and atomic rename; failed writes do not replace the in-memory state. Existing malformed state causes startup to fail instead of silently losing profiles. Protect local backups as you would the original file.

For an Android device or emulator connected through ADB, run `adb reverse tcp:8787 tcp:8787` to forward the device's loopback port to this host. The Android debug integration uses the loopback URL. Start the server before opting into friends; no public deployment is performed here. HTTP is appropriate only for this loopback development setup. A production service needs HTTPS, a production credential lifecycle, operational security, abuse prevention, and a privacy review. Changing HOST to a network address exposes the demo and is not a production deployment procedure.

## Consent and information shared

Profile creation and sharing are optional. A user chooses a display name and receives a random 12-character invitation code. **Sharing that code is permission for its recipient to connect the two profiles mutually.** Both people then see each other's display name and completed-quest counts. Keep the code private and exchange it knowingly; there is no public discovery, contact upload, contact matching, or friends-of-friends leaderboard. Possession of another person's invitation code is required. The code is not an account credential and cannot authenticate as that person.

Only a pseudonymous display name, profile ID, invitation code, token hash, friendship IDs, and `{id, at}` completion events are stored. The API rejects extra fields such as mood, notes, photos, or contacts. The app should keep any contact nickname local. Leaderboards return aggregate counts only, not completion timestamps or event IDs. No analytics, advertising, email, or SMS provider is used. Bearer tokens are returned once at creation, held privately by the client, and stored only as SHA-256 hashes on the server. Do not log, commit, or share tokens.

Counts are **self-reported**, not validated wellness outcomes. The server enforces duplicate and volume limits but trusts a client that reports finishing a quest; this is not an anti-cheat system. Rankings must not be marketed as measures of happiness, mental health, or effort. An empty account is ranked at zero. Ties share a rank using competition ranking (1, 1, 3). No public leaderboard exists.

Deleting a profile removes its token, code, completion history, and reciprocal friendships from this server file. It does not erase independently retained backups or the user's local app journal. There is no password recovery, profile editing, code rotation, or individual unfriend endpoint in this prototype; leave sharing by deleting the profile. A new profile receives a new credential and code.

## API contract

All responses are JSON with `Cache-Control: no-store`. POST requests require `Content-Type: application/json`. All `/v1` operations except profile creation require `Authorization: Bearer <token>`. No browser CORS access is enabled. Error responses are `{ "error": "machine_code", "message": "Short explanation" }` with the appropriate HTTP status.

### `POST /v1/profile`

Request: `{ "displayName": "Aki" }`.

Response **201**: `{ "token": "<43-character-secret>", "code": "<12-character-code>", "displayName": "Aki" }`.

Names are trimmed and NFC-normalized, with 1–32 Unicode code points, at most 128 UTF-8 bytes, and no control or bidi override characters. Pseudonyms are encouraged. Duplicate display names are allowed because profile IDs identify people. Codes use `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`; code input accepts lowercase and surrounding whitespace.

### `POST /v1/events`

Request: `{ "events": [{ "id": "light:2026-9-29", "at": 1790700000000 }] }`.

Response **200**: `{ "accepted": 1, "duplicates": 0, "totalCompleted": 1 }`.

The client supplies a stable event ID, normally `questId:localday`, and an integer epoch-millisecond timestamp. IDs begin with an alphanumeric character and contain only letters, numbers, colons, underscores, or hyphens, up to 128 characters. IDs are unique per profile across all time. Resending an existing ID is a successful no-op, even when a different valid timestamp is supplied; it cannot move the original event into a different ranking window. A request is atomic: validation or daily-cap failure saves none of its new events.

Send batches of at most **500 events** under the **64 KiB request limit**. The server accepts historical timestamps from 1970 onward, and rejects timestamps more than five minutes in the future. At most **16 new completions per UTC day** are accepted as a rough volume guard: adjacent local days can both contribute their eight quests to one UTC day. The local-day string in an ID is not a trusted date field; UTC-day limits use `at`. This guard is not anti-cheat validation. Events slightly ahead of the server clock are stored but do not count until their timestamp arrives. There is no 30-day historical cutoff. Chunk larger histories, keeping stable IDs when retrying. The prototype caps one profile at 20,000 events, the server at 100,000 events, 500 profiles, and a 32 MiB state file; capacity errors use 507.

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
