package com.tinywonder.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Device integration checks using only the Android framework; no external test runner. */
public final class SmokeTestRunner extends Instrumentation {
    private Context target;
    private final StringBuilder lines = new StringBuilder();
    private int passed;
    private int failed;
    private int skipped;
    private boolean testCircle;

    private interface Check { void run() throws Exception; }

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        testCircle = arguments != null && "true".equalsIgnoreCase(arguments.getString("circle"));
        start();
    }

    @Override public void onStart() {
        super.onStart();
        JSONObject original = null;
        try {
            target = getTargetContext();
            original = StateStore.read(target);
            ReminderScheduler.cancel(target);
            ReminderScheduler.createChannel(target);

            runCheck("stale UI save preserves native append and local edit", this::staleSaveMerge);
            runCheck("confirmation replaces its entry without losing a native reply", this::confirmationUpdate);
            runCheck("low energy controls the notification quest", this::lowEnergyChoice);
            runCheck("completed and skipped quests exhaust the queue", this::exhaustedChoice);
            runCheck("notification Done is idempotent", this::doneIdempotence);
            runCheck("notification Skip is idempotent and excludes the quest", this::skipIdempotence);
            runCheck("RemoteInput saves text with unconfirmed, unset ratings", this::remoteReply);
            runCheck("empty RemoteInput adds no entry", this::emptyReply);
            runCheck("RemoteInput text respects the storage length limit", this::longReply);
            runCheck("opt-out ignores receiver actions and replies", this::optOut);
            runCheck("snooze survives a stale UI save and cannot be resurrected", this::snoozeMerge);
            runCheck("finishing a snoozed quest clears its reminder", this::completeSnooze);
            runCheck("snooze timestamps respect quiet hours", this::snoozeQuietHours);
            runCheck("clear removes data and opt-in state", this::clearData);
            runCheck("state capacity failures are explicit and preserve previous data", this::stateCapacity);
            runCheck("native chat input and output boundaries", ChatClientValidation::run);
            runCheck("personalized queue respects energy and lifetime retirement", this::personalizedQueue);
            runCheck("retired personalized quest cannot be snoozed or resurrected", this::personalizedSnooze);
            runCheck("stale personalized notification actions do not recount or change skipped quests", this::personalizedReplay);
            if (testCircle) runCheck("native Circle join, sync, friendship, mutual board, and deletion", this::circleIntegration);
            runCheck("actual WebView check-in, quest, feedback, garden, and opt-in UI", this::webViewUi);
            runCheck("actual WebView AI profile approval, deduplication, review, privacy, and cancellation", this::aiWebViewUi);

            if (ReminderScheduler.canNotify(target) && !ReminderScheduler.quietNow()) {
                runCheck("allowed notification posts its actionable quest", this::notificationPosted);
            } else {
                skipped++;
                lines.append("SKIP allowed notification posting: permission/channel disabled or current quiet hours\n");
            }
        } catch (Throwable error) {
            recordFailure("runner setup", error);
        } finally {
            if (target != null && original != null) {
                try {
                    ReminderScheduler.cancel(target);
                    StateStore.clear(target);
                    StateStore.save(target, original.toString());
                    // nativeSnooze is native-owned: save intentionally ignores UI copies.
                    if (original.has("nativeSnooze")) {
                        final Object originalSnooze = original.get("nativeSnooze");
                        StateStore.edit(target, state -> state.put("nativeSnooze", originalSnooze));
                    }
                    if (StateStore.enabled(target)) ReminderScheduler.scheduleDaily(target);
                    lines.append("RESTORED original local state and enabled reminder schedule\n");
                } catch (Throwable error) {
                    recordFailure("original state restoration", error);
                }
            }
        }

        String summary = passed + " passed, " + failed + " failed, " + skipped + " skipped";
        Bundle result = new Bundle();
        result.putInt("count", passed + failed + skipped);
        result.putInt("passed", passed);
        result.putInt("failed", failed);
        result.putInt("skipped", skipped);
        result.putString("lines", lines.toString());
        result.putString("stream", "\nRomantiSide native smoke tests: " + summary + "\n" + lines);
        if (failed > 0) result.putString("error", summary);
        finish(failed == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void runCheck(String name, Check check) {
        try {
            check.run();
            passed++;
            lines.append("PASS ").append(name).append('\n');
        } catch (Throwable error) {
            recordFailure(name, error);
        }
    }

    private void recordFailure(String name, Throwable error) {
        failed++;
        lines.append("FAIL ").append(name).append(": ").append(Log.getStackTraceString(error)).append('\n');
    }

    private void reset(boolean enabled) throws Exception {
        ReminderScheduler.cancel(target);
        StateStore.clear(target);
        JSONObject state = new JSONObject()
            .put("entries", new JSONArray()).put("completed", new JSONArray()).put("skipped", new JSONArray())
            .put("feedback", new JSONArray()).put("memories", new JSONArray())
            .put("preferences", new JSONObject().put("reminders", enabled).put("checkInHour", 10).put("questHour", 14))
            .put("quests", new JSONArray().put(quest("light")).put(quest("gentle")).put(quest("strength")));
        StateStore.save(target, state.toString());
    }

    private JSONObject quest(String id) throws Exception {
        return new JSONObject().put("id", id).put("title", "Smoke test " + id)
            .put("action", "A local integration test quest.").put("minutes", 1);
    }

    private JSONObject entry(String id, long at, boolean confirmed) throws Exception {
        return new JSONObject().put("id", id).put("at", at).put("text", "Test reflection " + id)
            .put("confirmed", confirmed).put("mood", confirmed ? 3 : JSONObject.NULL)
            .put("energy", confirmed ? 2 : JSONObject.NULL).put("source", "app");
    }

    private JSONObject find(JSONArray values, String id) throws Exception {
        if (values != null) for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            if (id.equals(item.optString("id"))) return item;
        }
        throw new AssertionError("Missing expected record: " + id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void deliver(String action, JSONObject quest) {
        Intent intent = new Intent(target, ReminderReceiver.class).setAction(action);
        if (quest != null) intent.putExtra("quest", quest.toString());
        new ReminderReceiver().onReceive(target, intent);
    }

    private void reply(String text) {
        Intent intent = new Intent(target, ReminderReceiver.class).setAction(ReminderScheduler.REPLY);
        RemoteInput input = new RemoteInput.Builder(ReminderScheduler.REPLY_KEY).setLabel("Reflection").build();
        Bundle results = new Bundle();
        results.putCharSequence(ReminderScheduler.REPLY_KEY, text);
        RemoteInput.addResultsToIntent(new RemoteInput[]{input}, intent, results);
        new ReminderReceiver().onReceive(target, intent);
    }

    private void staleSaveMerge() throws Exception {
        reset(true);
        long now = System.currentTimeMillis();
        JSONObject first = entry("first", now - 3000, true);
        StateStore.edit(target, state -> StateStore.array(state, "entries").put(first));
        JSONObject stale = StateStore.read(target);
        JSONObject received = entry("native-reply", now - 2000, false);
        StateStore.edit(target, state -> StateStore.array(state, "entries").put(received));
        stale.getJSONArray("entries").put(entry("local-new", now - 1000, true));
        StateStore.save(target, stale.toString());
        JSONArray entries = StateStore.read(target).getJSONArray("entries");
        require(entries.length() == 3, "Stale UI save discarded an independent entry");
        require("first".equals(entries.getJSONObject(0).getString("id")), "Entries are not chronological");
        require("native-reply".equals(entries.getJSONObject(1).getString("id")), "Native append was not preserved");
        require("local-new".equals(entries.getJSONObject(2).getString("id")), "Local append was not preserved");
    }

    private void confirmationUpdate() throws Exception {
        reset(true);
        JSONObject pending = entry("pending", System.currentTimeMillis() - 1000, false);
        StateStore.edit(target, state -> StateStore.array(state, "entries").put(pending));
        JSONObject ui = StateStore.read(target);
        JSONObject newer = entry("unrelated", System.currentTimeMillis(), false);
        StateStore.edit(target, state -> StateStore.array(state, "entries").put(newer));
        find(ui.getJSONArray("entries"), "pending").put("confirmed", true).put("mood", 4).put("energy", 1);
        StateStore.save(target, ui.toString());
        JSONArray entries = StateStore.read(target).getJSONArray("entries");
        JSONObject saved = find(entries, "pending");
        require(entries.length() == 2, "Confirmation duplicated the existing entry or lost an append");
        require(saved.getBoolean("confirmed") && saved.getInt("mood") == 4 && saved.getInt("energy") == 1,
            "Explicitly confirmed ratings did not replace the pending entry");
        require(!find(entries, "unrelated").getBoolean("confirmed"), "An unrelated reply was changed");
    }

    private void lowEnergyChoice() throws Exception {
        reset(true);
        JSONObject rating = entry("low-energy", System.currentTimeMillis(), true).put("mood", 5).put("energy", 1);
        StateStore.edit(target, state -> StateStore.array(state, "entries").put(rating));
        JSONObject chosen = ReminderScheduler.chooseQuest(target);
        require(chosen != null && "gentle".equals(chosen.optString("id")), "Low energy must take priority over high mood");
    }

    private void exhaustedChoice() throws Exception {
        reset(true);
        long now = System.currentTimeMillis();
        StateStore.edit(target, state -> {
            StateStore.array(state, "completed").put(new JSONObject().put("id", "light").put("at", now));
            StateStore.array(state, "completed").put(new JSONObject().put("id", "strength").put("at", now));
            StateStore.array(state, "skipped").put(new JSONObject().put("id", "gentle").put("at", now));
        });
        require(ReminderScheduler.chooseQuest(target) == null, "An exhausted queue offered another quest");
    }

    private void personalizedQueue() throws Exception {
        reset(true);
        String longId = "generated-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String shortId = "generated-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        JSONObject longer = quest(longId).put("minutes", 4);
        JSONObject shorter = quest(shortId).put("minutes", 1);
        JSONObject lowEnergy = entry("personalized-low-energy", System.currentTimeMillis(), true).put("energy", 1);
        StateStore.edit(target, state -> {
            StateStore.array(state, "quests").put(longer);
            StateStore.array(state, "entries").put(lowEnergy);
        });
        require("gentle".equals(ReminderScheduler.chooseQuest(target).optString("id")), "A long generated activity overrode low-energy priority");
        StateStore.edit(target, state -> StateStore.array(state, "quests").put(shorter));
        require(shortId.equals(ReminderScheduler.chooseQuest(target).optString("id")), "A fresh short personalized quest was not prioritized");
        long priorDay = System.currentTimeMillis() - 2 * 86_400_000L;
        StateStore.edit(target, state -> {
            StateStore.array(state, "completed").put(new JSONObject().put("id", shortId).put("at", priorDay));
            StateStore.array(state, "skipped").put(new JSONObject().put("id", longId).put("at", priorDay));
            // Keep only the personalized queue to ensure exhaustion never revives a retired quest.
            state.put("quests", new JSONArray().put(shorter).put(longer));
        });
        require(ReminderScheduler.chooseQuest(target) == null, "A previous-day generated completion or skip was offered again");
    }

    private void personalizedSnooze() throws Exception {
        reset(true);
        JSONObject generated = quest("generated-cccccccccccccccccccccccccccccccc");
        StateStore.edit(target, state -> StateStore.array(state, "completed").put(new JSONObject()
            .put("id", generated.getString("id")).put("at", System.currentTimeMillis() - 2 * 86_400_000L)));
        ReminderScheduler.snooze(target, generated);
        require(!StateStore.read(target).has("nativeSnooze"), "A retired personalized quest was snoozed");
        StateStore.edit(target, state -> state.put("nativeSnooze", new JSONObject()
            .put("at", System.currentTimeMillis() + 60_000).put("quest", generated)));
        ReminderScheduler.reconcileSnooze(target);
        require(!StateStore.read(target).has("nativeSnooze"), "A stale personalized snooze survived reconciliation");
    }

    private void personalizedReplay() throws Exception {
        reset(true);
        JSONObject completed = quest("generated-dddddddddddddddddddddddddddddddd");
        JSONObject skipped = quest("generated-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        long priorDay = System.currentTimeMillis() - 2 * 86_400_000L;
        StateStore.edit(target, state -> {
            StateStore.array(state, "completed").put(new JSONObject().put("id", completed.getString("id")).put("at", priorDay));
            StateStore.array(state, "skipped").put(new JSONObject().put("id", skipped.getString("id")).put("at", priorDay));
        });
        deliver(ReminderScheduler.DONE, completed);
        deliver(ReminderScheduler.SKIP, completed);
        deliver(ReminderScheduler.DONE, skipped);
        deliver(ReminderScheduler.SKIP, skipped);
        JSONObject saved = StateStore.read(target);
        require(saved.getJSONArray("completed").length() == 1 && saved.getJSONArray("skipped").length() == 1,
            "A stale generated quest action changed its lifetime completion or skip");
        require(saved.getJSONArray("completed").getJSONObject(0).getLong("at") == priorDay,
            "Replaying an old completion changed its date or ranking period");
    }

    private void stateCapacity() throws Exception {
        reset(false);
        JSONObject original = StateStore.read(target).put("capacityMarker", "keep this value");
        require(StateStore.save(target, original.toString()), "A valid state did not report a successful save");
        String oversized = new JSONObject().put("capacityMarker", "must not replace")
            .put("oversized", new String(new char[2_000_001]).replace('\0', 'x')).toString();
        require(!StateStore.save(target, oversized), "An oversized state reported success");
        require(!StateStore.save(target, "{invalid-json") && !StateStore.save(target, null), "An unreadable state reported success");
        require("keep this value".equals(StateStore.read(target).optString("capacityMarker")), "A rejected save replaced valid data");

        // Two individually valid snapshots can exceed capacity after the native
        // append merge; reject that merged value without losing either stored data.
        String largeText = new String(new char[1_050_000]).replace('\0', 'x');
        JSONObject first = entry("capacity-first", System.currentTimeMillis() - 1000, false).put("text", largeText);
        original.put("entries", new JSONArray().put(first));
        require(StateStore.save(target, original.toString()), "An under-capacity state did not save");
        JSONObject incoming = new JSONObject().put("capacityMarker", "must not replace")
            .put("entries", new JSONArray().put(entry("capacity-second", System.currentTimeMillis(), false).put("text", largeText)));
        require(!StateStore.save(target, incoming.toString()), "A merged state beyond capacity reported success");
        JSONObject saved = StateStore.read(target);
        require("keep this value".equals(saved.optString("capacityMarker")) && saved.getJSONArray("entries").length() == 1
            && "capacity-first".equals(saved.getJSONArray("entries").getJSONObject(0).optString("id")), "Merged capacity failure damaged existing data");
        reset(false);
    }

    private void doneIdempotence() throws Exception {
        reset(true);
        JSONObject quest = quest("light");
        deliver(ReminderScheduler.DONE, quest);
        deliver(ReminderScheduler.DONE, quest);
        JSONArray completed = StateStore.read(target).getJSONArray("completed");
        require(completed.length() == 1, "Duplicate Done delivery created multiple completions");
        require("notification".equals(completed.getJSONObject(0).getString("source")), "Notification source was lost");
        JSONObject ui = StateStore.read(target);
        ui.getJSONArray("completed").put(new JSONObject().put("id", "light").put("at", System.currentTimeMillis()).put("source", "app"));
        StateStore.save(target, ui.toString());
        require(StateStore.read(target).getJSONArray("completed").length() == 1, "Same-day UI and receiver completion were duplicated");
    }

    private void skipIdempotence() throws Exception {
        reset(true);
        deliver(ReminderScheduler.SKIP, quest("light"));
        deliver(ReminderScheduler.SKIP, quest("light"));
        require(StateStore.read(target).getJSONArray("skipped").length() == 1, "Duplicate Skip delivery created multiple events");
        JSONObject chosen = ReminderScheduler.chooseQuest(target);
        require(chosen != null && !"light".equals(chosen.optString("id")), "A skipped quest was selected again");
    }

    private void remoteReply() throws Exception {
        reset(true);
        reply("  Tired after class, but glad to be home.  ");
        JSONArray entries = StateStore.read(target).getJSONArray("entries");
        require(entries.length() == 1, "RemoteInput was not persisted exactly once");
        JSONObject saved = entries.getJSONObject(0);
        require("Tired after class, but glad to be home.".equals(saved.getString("text")), "Reply text was not trimmed accurately");
        require(!saved.getBoolean("confirmed") && saved.isNull("mood") && saved.isNull("energy"), "Reply fabricated mood or energy ratings");
        require("notification".equals(saved.getString("source")) && !saved.getString("id").isEmpty(), "Reply provenance or identity is missing");
    }

    private void emptyReply() throws Exception {
        reset(true);
        reply(" \n\t ");
        require(StateStore.read(target).getJSONArray("entries").length() == 0, "A blank reply was persisted");
    }

    private void longReply() throws Exception {
        reset(true);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 2100; i++) text.append('x');
        reply(text.toString());
        JSONObject saved = StateStore.read(target).getJSONArray("entries").getJSONObject(0);
        require(saved.getString("text").length() == 2000, "Long reply exceeded the documented input limit");
    }

    private void optOut() throws Exception {
        reset(false);
        deliver(ReminderScheduler.DONE, quest("light"));
        deliver(ReminderScheduler.SKIP, quest("gentle"));
        deliver(ReminderScheduler.SNOOZE, quest("light"));
        reply("This must not be saved after opt-out.");
        JSONObject saved = StateStore.read(target);
        require(saved.getJSONArray("entries").length() == 0 && saved.getJSONArray("completed").length() == 0
            && saved.getJSONArray("skipped").length() == 0 && !saved.has("nativeSnooze"), "Receiver changed state after reminders were disabled");
        require(!ReminderScheduler.showQuest(target, quest("light")), "Quest notification ignored opt-out");
    }

    private void snoozeMerge() throws Exception {
        reset(true);
        JSONObject stale = StateStore.read(target);
        ReminderScheduler.snooze(target, quest("light"));
        long when = StateStore.read(target).getJSONObject("nativeSnooze").getLong("at");
        StateStore.save(target, stale.toString());
        JSONObject retained = StateStore.read(target);
        require(retained.has("nativeSnooze") && retained.getJSONObject("nativeSnooze").getLong("at") == when,
            "A stale UI save discarded the native snooze");
        ReminderScheduler.cancelSnooze(target);
        StateStore.save(target, retained.toString());
        require(!StateStore.read(target).has("nativeSnooze"), "A stale UI copy resurrected a canceled snooze");
    }

    private void completeSnooze() throws Exception {
        reset(true);
        ReminderScheduler.snooze(target, quest("light"));
        deliver(ReminderScheduler.DONE, quest("light"));
        require(!StateStore.read(target).has("nativeSnooze"), "Notification completion left its snooze pending");
        ReminderScheduler.snooze(target, quest("gentle"));
        JSONObject ui = StateStore.read(target);
        ui.getJSONArray("skipped").put(new JSONObject().put("id", "gentle").put("at", System.currentTimeMillis()).put("source", "app"));
        StateStore.save(target, ui.toString());
        require(!StateStore.read(target).has("nativeSnooze"), "UI skip left its snooze pending");
        require(!ReminderScheduler.showQuest(target, quest("gentle")), "A skipped snoozed quest was displayed again");
    }

    private void snoozeQuietHours() throws Exception {
        reset(true);
        long before = System.currentTimeMillis();
        ReminderScheduler.snooze(target, quest("light"));
        long at = StateStore.read(target).getJSONObject("nativeSnooze").getLong("at");
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(at);
        int hour = time.get(Calendar.HOUR_OF_DAY);
        require(at > before && hour >= 8 && hour < 21, "Snooze is in the past or during quiet hours");
    }

    private void clearData() throws Exception {
        reset(true);
        reply("Temporary reflection to delete.");
        ReminderScheduler.snooze(target, quest("light"));
        ReminderScheduler.cancel(target);
        StateStore.clear(target);
        require(StateStore.read(target).length() == 0, "Clear retained local data");
        require(!StateStore.enabled(target), "Clear retained reminders opt-in");
    }

    /** Optional local-server integration. Original account credentials never leave this method. */
    private void circleIntegration() throws Exception {
        SharedPreferences prefs = target.getSharedPreferences("tiny_wonder_circle", Context.MODE_PRIVATE);
        Map<String, ?> originalCredentials = new HashMap<>(prefs.getAll());
        String nativeSyntheticToken = "";
        String secondSyntheticToken = "";
        Throwable failure = null;
        CircleClient client = new CircleClient(target);
        try {
            require(prefs.edit().clear().commit(), "Could not isolate synthetic Circle credentials");
            require(!new JSONObject(client.status()).getBoolean("joined"), "Synthetic Circle did not start signed out");
            JSONObject joined = nativeCircle(client, "join", new JSONObject().put("displayName", "Integration sample"));
            nativeSyntheticToken = prefs.getString("token", "");
            require(!nativeSyntheticToken.isEmpty() && joined.getBoolean("joined"), "Native Circle did not create its synthetic profile");
            JSONObject nativeStatus = new JSONObject(client.status());
            require(!nativeStatus.has("token") && !nativeStatus.optString("code").isEmpty(), "Public Circle status exposed a token or omitted its code");
            require("Integration sample".equals(nativeStatus.optString("displayName")), "Native Circle saved the wrong display name");

            JSONObject completion = new JSONObject().put("id", "light:smoke-" + UUID.randomUUID())
                .put("at", System.currentTimeMillis()).put("text", "This private extra must be dropped by the native transport.");
            JSONObject payload = new JSONObject().put("events", new JSONArray().put(completion)).put("period", "all");
            JSONObject synced = nativeCircle(client, "sync", payload);
            require(synced.optInt("accepted") == 1, "Native Circle did not accept exactly one synthetic completion");
            assertSelfCount(synced, 1, 1);
            JSONObject duplicate = nativeCircle(client, "sync", payload);
            require(duplicate.optInt("accepted") == 0, "Native Circle replay was not idempotent");
            assertSelfCount(duplicate, 1, 1);
            JSONObject board = nativeCircle(client, "board", new JSONObject().put("period", "week"));
            assertSelfCount(board, 1, 1);

            JSONObject second = circleHttp("POST", "/v1/profile", new JSONObject().put("displayName", "Second sample"), "");
            secondSyntheticToken = second.getString("token");
            JSONObject added = nativeCircle(client, "add", new JSONObject().put("code", second.getString("code")).put("period", "all"));
            require(added.optBoolean("added") && added.has("friend"), "Native add did not preserve its friend response");
            require("Second sample".equals(added.getJSONObject("friend").optString("displayName")), "Native add returned the wrong synthetic friend");
            assertSelfCount(added, 1, 2);
            JSONObject mutual = circleHttp("GET", "/v1/leaderboard?period=all", null, secondSyntheticToken);
            assertSelfCount(mutual, 0, 2);
            boolean foundOtherCompletion = false;
            JSONArray people = mutual.getJSONArray("people");
            for (int i = 0; i < people.length(); i++) {
                JSONObject person = people.getJSONObject(i);
                if (!person.optBoolean("isYou") && person.optInt("completed") == 1) foundOtherCompletion = true;
            }
            require(foundOtherCompletion, "The second synthetic profile did not see its mutual friend's completion");

            JSONObject left = nativeCircle(client, "leave", new JSONObject());
            require(left.optBoolean("deleted") && !left.optBoolean("joined"), "Native Circle did not confirm account deletion");
            require(!new JSONObject(client.status()).getBoolean("joined") && prefs.getString("token", "").isEmpty(), "Native leave retained its synthetic credentials");
            nativeSyntheticToken = "";
            require(circleHttp("DELETE", "/v1/profile", null, secondSyntheticToken).optBoolean("deleted"), "Second synthetic account deletion was not confirmed");
            secondSyntheticToken = "";
        } catch (Throwable error) {
            failure = error;
        } finally {
            // These tokens were created inside this test; never delete the backed-up account.
            String possibleSynthetic = prefs.getString("token", "");
            String originalToken = originalCredentials.get("token") instanceof String ? (String) originalCredentials.get("token") : "";
            if (nativeSyntheticToken.isEmpty() && !possibleSynthetic.isEmpty() && !possibleSynthetic.equals(originalToken)) nativeSyntheticToken = possibleSynthetic;
            if (!nativeSyntheticToken.isEmpty() && !nativeSyntheticToken.equals(originalToken)) {
                try { require(circleHttp("DELETE", "/v1/profile", null, nativeSyntheticToken).optBoolean("deleted"), "Synthetic native account cleanup was not confirmed"); }
                catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
            }
            if (!secondSyntheticToken.isEmpty()) {
                try { require(circleHttp("DELETE", "/v1/profile", null, secondSyntheticToken).optBoolean("deleted"), "Second synthetic account cleanup was not confirmed"); }
                catch (Throwable cleanup) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
            }
            try {
                restorePreferences(prefs, originalCredentials);
                require(prefs.getAll().equals(originalCredentials), "Original Circle credentials did not restore exactly");
                lines.append("RESTORED original Circle credentials\n");
            } catch (Throwable restore) { if (failure == null) failure = restore; else failure.addSuppressed(restore); }
        }
        if (failure != null) throw new AssertionError("Synthetic Circle integration failed", failure);
    }

    private JSONObject nativeCircle(CircleClient client, String action, JSONObject payload) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> type = new AtomicReference<>();
        AtomicReference<String> text = new AtomicReference<>();
        client.request(action, payload.toString(), (eventType, eventText) -> {
            type.set(eventType); text.set(eventText); complete.countDown();
        });
        boolean timely = complete.await(12, TimeUnit.SECONDS);
        // Drain a timed-out request before restoring existing credentials. The test's
        // small sync/add requests have at most two HTTP operations, each with 8s timeouts.
        if (!timely) require(complete.await(45, TimeUnit.SECONDS), "Circle request did not settle before credential restoration");
        require(timely, "Native Circle " + action + " exceeded its 12-second test deadline");
        require("circle".equals(type.get()), "Native Circle " + action + " did not return a success callback");
        JSONObject response = new JSONObject(text.get());
        require(action.equals(response.optString("action")), "Native Circle callback action was incorrect");
        require(!response.has("token"), "Native Circle callback exposed its bearer token");
        return response;
    }

    private void assertSelfCount(JSONObject board, int count, int expectedPeople) throws Exception {
        JSONArray people = board.getJSONArray("people");
        require(people.length() == expectedPeople, "Circle board had an unexpected number of synthetic people");
        int selfRows = 0;
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.getJSONObject(i);
            if (person.optBoolean("isYou")) { selfRows++; require(person.optInt("completed") == count, "Circle board had an unexpected self completion count"); }
        }
        require(selfRows == 1, "Circle board did not identify exactly one self row");
    }

    private JSONObject circleHttp(String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:8787" + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            require(status >= 200 && status < 300, "Synthetic Circle HTTP request failed with status " + status);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    require(output.size() + count <= 262144, "Synthetic Circle response exceeded the test limit");
                    output.write(buffer, 0, count);
                }
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }

    @SuppressWarnings("unchecked")
    private void restorePreferences(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor edit = prefs.edit().clear();
        for (Map.Entry<String, ?> item : values.entrySet()) {
            Object value = item.getValue(); String key = item.getKey();
            if (value instanceof String) edit.putString(key, (String) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Long) edit.putLong(key, (Long) value);
            else if (value instanceof Float) edit.putFloat(key, (Float) value);
            else if (value instanceof Set) edit.putStringSet(key, new HashSet<>((Set<String>) value));
            else throw new AssertionError("Unexpected original preference value type");
        }
        require(edit.commit(), "Original Circle credentials could not be written back");
    }

    private void webViewUi() throws Exception {
        reset(false);
        Intent launch = new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = startActivitySync(launch);
        try {
            AtomicReference<WebView> found = new AtomicReference<>();
            runOnMainSync(() -> found.set(findWebView(activity.getWindow().getDecorView())));
            WebView webView = found.get();
            require(webView != null, "MainActivity did not contain its WebView");

            long deadline = SystemClock.uptimeMillis() + 10_000;
            boolean ready = false;
            while (SystemClock.uptimeMillis() < deadline) {
                JSONObject readiness = evaluateJson(webView,
                    "return {ready: document.readyState === 'complete' && typeof state !== 'undefined'"
                    + " && typeof Wonder !== 'undefined' && typeof checkin === 'function'"
                    + " && typeof Circle !== 'undefined' && typeof AI !== 'undefined' && !!window.Native};",
                    Math.min(1000, Math.max(1, deadline - SystemClock.uptimeMillis())));
                if (readiness.optBoolean("ready")) { ready = true; break; }
                SystemClock.sleep(75);
            }
            require(ready, "Local app scripts/bridge did not become ready within ten seconds");

            JSONObject empty = evaluateJson(webView,
                "state = Wonder.defaults(); state.onboarded = true; save(); closeModal(); go('today');"
                + " const todayReady = !!document.querySelector('[data-action=checkin]');"
                + " go('you'); const emptyGarden = document.querySelector('#main').textContent;"
                + " const emptyBars = document.querySelectorAll('.bar').length;"
                + " go('lens'); const cameraLabel = document.querySelector('#lens-label').textContent;"
                + " const cameraOff = stream === null; go('today');"
                + " return {todayReady, emptyGarden, emptyBars, cameraLabel, cameraOff,"
                + " entries: state.entries.length, memories: state.memories.length};");
            require(empty.getBoolean("todayReady"), "Today did not render its check-in action");
            require(empty.getInt("entries") == 0 && empty.getInt("memories") == 0 && empty.getInt("emptyBars") == 0,
                "Fresh garden showed fabricated entries, memories, or a mood chart");
            require(empty.getString("emptyGarden").contains("Every garden starts somewhere"), "Garden empty state was missing");
            require(empty.getBoolean("cameraOff") && empty.getString("cameraLabel").contains("off"),
                "Entering the lens started a camera or omitted its off state");

            JSONObject checkin = evaluateJson(webView,
                "checkin(); document.querySelector('[data-mood=\"4\"]').click();"
                + " document.querySelector('[data-energy=\"1\"]').click();"
                + " document.querySelector('#reflection').value = 'Synthetic UI smoke reflection';"
                + " saveCheckin(); return {entries: state.entries, firstQuest: Wonder.queue(state)[0]?.id,"
                + " modalClosed: !document.querySelector('.modal')};");
            require(checkin.getBoolean("modalClosed"), "Saving the UI check-in did not finish its form");
            require("gentle".equals(checkin.getString("firstQuest")), "UI low-energy queue did not lead with gentle");
            JSONArray persistedEntries = StateStore.read(target).getJSONArray("entries");
            require(persistedEntries.length() == 1, "Actual JavaScript bridge did not persist exactly one UI check-in");
            JSONObject persisted = persistedEntries.getJSONObject(0);
            require(persisted.getBoolean("confirmed") && persisted.getInt("mood") == 4 && persisted.getInt("energy") == 1,
                "DOM mood/energy selections did not reach native storage correctly");
            require("Synthetic UI smoke reflection".equals(persisted.getString("text")), "UI reflection text was not persisted");

            JSONObject completed = evaluateJson(webView,
                "openQuest('gentle'); finishQuest();"
                + " const feedbackButtons = document.querySelectorAll('[data-feedback]').length;"
                + " document.querySelector('[data-feedback=\"0\"]').click();"
                + " const feedbackClosed = !document.querySelector('.modal');"
                + " openQuest('gentle'); finishQuest(); closeModal();"
                + " return {feedbackButtons, feedbackClosed, completed: state.completed.length, feedback: state.feedback};");
            require(completed.getInt("feedbackButtons") == 3 && completed.getBoolean("feedbackClosed"),
                "Feedback choices did not render or neutral feedback did not close the form");
            JSONObject afterQuest = StateStore.read(target);
            require(completed.getInt("completed") == 1 && afterQuest.getJSONArray("completed").length() == 1,
                "Repeated UI completion counted the same quest twice");
            JSONArray feedback = afterQuest.getJSONArray("feedback");
            require(feedback.length() == 1 && feedback.getJSONObject(0).getInt("value") == 0
                && "gentle".equals(feedback.getJSONObject(0).getString("id")),
                "Neutral feedback did not persist through the actual bridge");

            JSONObject garden = evaluateJson(webView,
                "go('you'); const bar = document.querySelector('.bar');"
                + " return {bars: document.querySelectorAll('.bar').length,"
                + " barHeight: bar ? parseFloat(getComputedStyle(bar).height) : 0,"
                + " text: document.querySelector('#main').textContent};");
            require(garden.getInt("bars") == 1 && garden.getDouble("barHeight") > 4,
                "Real WebView/CSP prevented the mood chart bar from receiving a visible height");
            require(garden.getString("text").contains("Synthetic UI smoke reflection"), "Garden did not display the saved reflection");

            JSONObject circleStatus = evaluateJson(webView, "return {joined: !!Circle.status().joined};");
            if (circleStatus.getBoolean("joined")) {
                skipped++;
                lines.append("SKIP Circle opt-in UI: existing account retained; synthetic events were not synced\n");
            } else {
                JSONObject circle = evaluateJson(webView,
                    "go('circle'); return {tab, text: document.querySelector('#main').textContent,"
                    + " canJoin: !!document.querySelector('[data-circle=join]'),"
                    + " ranks: document.querySelectorAll('.rank-row').length, joined: !!Circle.status().joined};");
                require("circle".equals(circle.getString("tab")) && circle.getBoolean("canJoin")
                    && circle.getString("text").contains("Opt-in only"), "Circle opt-in screen failed to render");
                require(!circle.getBoolean("joined") && circle.getInt("ranks") == 0, "Circle joined or showed fabricated rankings without opt-in");
            }
        } finally {
            runOnMainSync(activity::finish);
            waitForIdleSync();
            long finishDeadline = SystemClock.uptimeMillis() + 3000;
            while (!activity.isDestroyed() && SystemClock.uptimeMillis() < finishDeadline) SystemClock.sleep(25);
            require(activity.isDestroyed(), "Test Activity did not close before native state restoration");
        }
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private void aiWebViewUi() throws Exception {
        reset(false);
        JSONObject initial = StateStore.read(target).put("onboarded", true);
        initial.put("entries", new JSONArray().put(entry("ai-ui-existing", System.currentTimeMillis(), true)
            .put("mood", 4).put("energy", 1).put("text", "Synthetic UI smoke reflection")));
        require(StateStore.save(target, initial.toString()), "Could not seed the synthetic AI UI state");
        Activity activity = startActivitySync(new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            AtomicReference<WebView> found = new AtomicReference<>();
            runOnMainSync(() -> {
                WebView webView = findWebView(activity.getWindow().getDecorView());
                found.set(webView);
                if (webView != null) {
                    // Android's injected method properties cannot be overridden in JS.
                    // Replace the bridge before reloading, using a test-only facade
                    // that has no network transport and delegates actual local storage.
                    webView.removeJavascriptInterface("Native");
                    webView.addJavascriptInterface(new SyntheticAiBridge(target), "Native");
                    webView.reload();
                }
            });
            WebView webView = found.get();
            require(webView != null, "AI UI activity did not contain a WebView");
            boolean ready = false;
            long deadline = SystemClock.uptimeMillis() + 10_000;
            while (SystemClock.uptimeMillis() < deadline) {
                JSONObject readiness = evaluateJson(webView,
                    "return {ready: document.readyState === 'complete' && typeof AI !== 'undefined'"
                    + " && typeof native !== 'undefined' && typeof native.testTransportMarker === 'function'"
                    + " && native.testTransportMarker() === 'synthetic-ai-transport-v1'};",
                    Math.min(1000, Math.max(1, deadline - SystemClock.uptimeMillis())));
                if (readiness.optBoolean("ready")) { ready = true; break; }
                SystemClock.sleep(75);
            }
            require(ready, "Synthetic AI transport was not installed; stopped before any AI action");
        JSONObject flow = evaluateJson(webView, """
            const calls = () => JSON.parse(native.capturedRequests());
            const output = {};
            try {
              output.syntheticTransport = native === window.Native && native.testTransportMarker() === 'synthetic-ai-transport-v1';
              if (!output.syntheticTransport) throw new Error('Synthetic transport unavailable; stopped before any AI action.');

              const rawYap = 'SYNTHETIC_RAW_YAP_DO_NOT_PERSIST. I like drawing and calm indoor breaks, and I would rather avoid crowds.';
              AI.openProfile();
              document.querySelector('#yap-draft').value = rawYap;
              document.querySelector('#yap-draft').dispatchEvent(new Event('input'));
              document.querySelector('[data-ai="extract"]').click();
              output.profileRequestCorrect = calls().profile.length === 1 && calls().profile[0].payload.text === rawYap;
              const profile = {
                summary: 'I enjoy creative, quiet indoor activities.', preferences: ['drawing', 'quiet breaks'],
                avoid: ['crowds'], moodContext: 'A busy afternoon', energyStyle: 'Small seated activities'
              };
              window.onNativeEvent({type: 'profile', text: JSON.stringify({
                requestId: calls().profile[0].requestId, profile,
                systemPrompt: 'Synthetic preference context: optional quiet creative activities; the user can skip.'
              })});
              output.awaitedApproval = !state.questProfile && !JSON.parse(native.getState()).questProfile
                && !!document.querySelector('[data-ai="approve"]');
              document.querySelector('[data-ai="approve"]').click();
              output.questRequestCorrect = calls().quests.length === 1
                && calls().quests[0].payload.profile.summary === profile.summary
                && calls().quests[0].payload.history.length >= Wonder.quests.length
                && calls().quests[0].payload.mood === 4 && calls().quests[0].payload.energy === 1;
              const quest = (id, title, action, activityKey) => ({
                id: 'generated-' + id.repeat(32), title, action, activityKey, minutes: 1,
                mechanism: 'strengths', principle: 'Character strengths', icon: 'spark', color: 'yellow',
                why: 'A synthetic optional example for UI verification.', evidence: 'https://doi.org/10.1037/0003-066X.60.5.410'
              });
              const first = quest('1', 'Fold a paper mountain', 'Fold a scrap of paper into one mountain-shaped crease.', 'fold-paper-mountain');
              const duplicate = quest('2', 'Another paper mountain', first.action, 'crease-paper-mountain');
              const second = quest('3', 'Listen for a distant sound', 'Listen for one quiet sound and sketch a line that represents it.', 'listen-quiet-sound');
              window.onNativeEvent({type: 'quests', text: JSON.stringify({
                requestId: calls().quests[0].requestId, quests: [first, duplicate, second], duplicatesFiltered: 0, exhausted: false
              })});
              const profileSaved = JSON.parse(native.getState());
              output.profilePersisted = profileSaved.questProfile?.profile.summary === profile.summary;
              output.freshQueueOnly = profileSaved.generatedQuests.length === 2
                && !profileSaved.generatedQuests.some(q => q.id === duplicate.id)
                && Wonder.queue(state).filter(q => q.id.startsWith('generated-')).length === 2;
              output.rawYapAbsent = !JSON.stringify(profileSaved).includes('SYNTHETIC_RAW_YAP_DO_NOT_PERSIST');
              closeModal();

              AI.beginChat();
              output.consentFirst = calls().chat.length === 0 && !!document.querySelector('[data-ai="chat-consent"]')
                && document.querySelector('.modal').textContent.includes('Anthropic');
              document.querySelector('[data-ai="chat-consent"]').click();
              const rawChat = 'SYNTHETIC_RAW_CHAT_DO_NOT_PERSIST. My afternoon feels scattered after class.';
              document.querySelector('#chat-draft').value = rawChat;
              document.querySelector('#chat-draft').dispatchEvent(new Event('input'));
              document.querySelector('[data-ai="send-chat"]').click();
              output.chatRequestCorrect = calls().chat.length === 1 && calls().chat[0].payload.messages.length === 1
                && calls().chat[0].payload.messages[0].role === 'user' && calls().chat[0].payload.messages[0].content === rawChat
                && calls().chat[0].payload.mood === null && calls().chat[0].payload.energy === null
                && calls().chat[0].payload.profile.summary === profile.summary;
              const answer = {
                reply: 'SYNTHETIC_ASSISTANT_REPLY_DO_NOT_PERSIST. A scattered afternoon can feel tiring.',
                summary: 'A busy afternoon left me scattered; I want a quiet creative break.',
                suggestedMood: 2, suggestedEnergy: 1, suggestedQuestId: 'gentle', urgentSupport: false
              };
              window.onNativeEvent({type: 'chat', text: JSON.stringify({requestId: calls().chat[0].requestId, ...answer})});
              output.chatRemainedDraft = state.entries.length === 1 && JSON.parse(native.getState()).entries.length === 1
                && document.querySelector('.chat-log').textContent.includes(answer.reply);
              document.querySelector('[data-ai="review-chat"]').click();
              output.reviewPrefilled = document.querySelector('#reflection').value === answer.summary && mood === 2 && energy === 1;
              document.querySelector('[data-mood="5"]').click();
              document.querySelector('[data-energy="2"]').click();
              document.querySelector('[data-action="savecheckin"]').click();
              const saved = JSON.parse(native.getState());
              const latest = saved.entries[saved.entries.length - 1];
              output.editsPersisted = latest.text === answer.summary && latest.mood === 5 && latest.energy === 2 && latest.confirmed;
              output.onlySummarySaved = saved.entries.length === 2 && !JSON.stringify(saved).includes('SYNTHETIC_RAW_CHAT_DO_NOT_PERSIST')
                && !JSON.stringify(saved).includes('SYNTHETIC_ASSISTANT_REPLY_DO_NOT_PERSIST')
                && !JSON.stringify(saved).includes('SYNTHETIC_RAW_YAP_DO_NOT_PERSIST');

              AI.beginChat();
              document.querySelector('[data-ai="chat-consent"]').click();
              document.querySelector('#chat-draft').value = 'SYNTHETIC_CANCELLED_CHAT_DO_NOT_PERSIST';
              document.querySelector('[data-ai="send-chat"]').click();
              const abandonedId = calls().chat[1].requestId;
              closeModal();
              const beforeLate = native.getState();
              window.onNativeEvent({type: 'chat', text: JSON.stringify({
                requestId: abandonedId, ...answer, summary: 'LATE_CALLBACK_MUST_NOT_SURVIVE'
              })});
              output.lateIgnored = calls().cancelled === 1 && !document.querySelector('.modal')
                && native.getState() === beforeLate && state.entries.length === 2;
            } finally {
              AI.close();
            }
            return output;
            """, 10_000);
        for (String check : new String[]{"syntheticTransport", "profileRequestCorrect", "awaitedApproval", "questRequestCorrect",
            "profilePersisted", "freshQueueOnly", "rawYapAbsent", "consentFirst", "chatRequestCorrect", "chatRemainedDraft",
            "reviewPrefilled", "editsPersisted", "onlySummarySaved", "lateIgnored"}) {
            require(flow.optBoolean(check), "AI WebView regression failed: " + check);
        }
        JSONObject saved = StateStore.read(target);
        require(saved.getJSONArray("generatedQuests").length() == 2 && saved.getJSONArray("entries").length() == 2,
            "AI UI results did not persist through the actual native bridge");
        require(!saved.toString().contains("DO_NOT_PERSIST") && !saved.toString().contains("LATE_CALLBACK_MUST_NOT_SURVIVE"),
            "AI raw transcript or abandoned callback reached native storage");
        evaluateJson(webView, "AI.openProfile(); document.querySelector('#toast')?.classList.remove('show'); return {shown: true};");
        captureSyntheticScreen("profile");
        evaluateJson(webView, "closeModal(); go('quests'); return {shown: true};");
        captureSyntheticScreen("quests");
        evaluateJson(webView, "AI.openProfile(); document.querySelector('[data-ai=\"new-yap\"]').click(); return {shown: true};");
        captureSyntheticScreen("yap");
        } finally {
            runOnMainSync(activity::finish);
            waitForIdleSync();
            long deadline = SystemClock.uptimeMillis() + 3000;
            while (!activity.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25);
            require(activity.isDestroyed(), "Synthetic AI activity did not close before restoring real app state");
        }
    }

    private void captureSyntheticScreen(String name) throws Exception {
        waitForIdleSync();
        SystemClock.sleep(150);
        android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
        require(screenshot != null, "Could not capture synthetic AI screen " + name);
        java.io.File directory = target.getFilesDir();
        require(directory.isDirectory() || directory.mkdirs(), "Could not create screenshot output directory");
        java.io.File file = new java.io.File(directory, "ui-synthetic-ai-" + name + ".png");
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(file)) {
            require(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream), "Could not save synthetic screen");
        } finally { screenshot.recycle(); }
        byte[] header = new byte[8];
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            require(input.read(header) == 8 && java.util.Arrays.equals(header,
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}), "Synthetic screenshot is not a PNG");
        }
        lines.append("SCREENSHOT ").append(file.getAbsolutePath()).append(" (").append(file.length()).append(" bytes)\n");
    }

    /** Test-only bridge: actual local persistence, synthetic replies, no network methods. */
    public static final class SyntheticAiBridge {
        private final Context context;
        private final JSONObject calls = new JSONObject();
        private int cancelled;

        SyntheticAiBridge(Context context) {
            this.context = context;
            try {
                calls.put("profile", new JSONArray()).put("quests", new JSONArray()).put("chat", new JSONArray());
            } catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
        }
        @android.webkit.JavascriptInterface public String testTransportMarker() { return "synthetic-ai-transport-v1"; }
        @android.webkit.JavascriptInterface public String getState() { return StateStore.read(context).toString(); }
        @android.webkit.JavascriptInterface public boolean saveState(String value) { return StateStore.save(context, value); }
        @android.webkit.JavascriptInterface public String getCircleStatus() { return "{\"joined\":false}"; }
        @android.webkit.JavascriptInterface public String notificationStatus() { return "{\"permission\":false,\"enabled\":false}"; }
        @android.webkit.JavascriptInterface public void profileRequest(String id, String payload) { capture("profile", id, payload); }
        @android.webkit.JavascriptInterface public void questsRequest(String id, String payload) { capture("quests", id, payload); }
        @android.webkit.JavascriptInterface public void chatRequest(String id, String payload) { capture("chat", id, payload); }
        @android.webkit.JavascriptInterface public synchronized void cancelChat() { cancelled++; }
        @android.webkit.JavascriptInterface public void stopVoice() { }
        @android.webkit.JavascriptInterface public synchronized String capturedRequests() {
            try { calls.put("cancelled", cancelled); } catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
            return calls.toString();
        }
        private synchronized void capture(String kind, String id, String payload) {
            try { calls.getJSONArray(kind).put(new JSONObject().put("requestId", id).put("payload", new JSONObject(payload))); }
            catch (org.json.JSONException invalid) { throw new AssertionError("Synthetic AI request was not JSON", invalid); }
        }
    }

    private JSONObject evaluateJson(WebView webView, String body) throws Exception {
        return evaluateJson(webView, body, 3000);
    }

    private JSONObject evaluateJson(WebView webView, String body, long timeoutMs) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        String script = "(function(){try{return JSON.stringify((function(){" + body
            + "})());}catch(error){return JSON.stringify({error:String(error),stack:error.stack||''});}})()";
        runOnMainSync(() -> webView.evaluateJavascript(script, value -> {
            result.set(value);
            complete.countDown();
        }));
        require(complete.await(timeoutMs, TimeUnit.MILLISECONDS), "WebView JavaScript callback timed out");
        Object value = new JSONTokener(result.get()).nextValue();
        require(value instanceof String, "WebView did not return a JSON string: " + result.get());
        JSONObject parsed = new JSONObject((String) value);
        require(!parsed.has("error"), "WebView script failed: " + parsed.optString("error") + " " + parsed.optString("stack"));
        return parsed;
    }

    private void notificationPosted() throws Exception {
        reset(true);
        require(ReminderScheduler.showQuest(target, quest("light")), "Allowed quest was not accepted for posting");
        NotificationManager manager = target.getSystemService(NotificationManager.class);
        long deadline = SystemClock.uptimeMillis() + 2000;
        do {
            for (StatusBarNotification posted : manager.getActiveNotifications()) {
                if (posted.getId() == ReminderScheduler.QUEST_ID) {
                    require(posted.getNotification().actions != null && posted.getNotification().actions.length == 3,
                        "Posted quest does not expose Done, Later, and Skip actions");
                    return;
                }
            }
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("No active quest notification appeared within two seconds");
    }
}
