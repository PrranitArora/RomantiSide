package com.tinywonder.app;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Notification actions run without bringing up the app or requiring a backend. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!StateStore.enabled(context) || action == null) return;
        if (ReminderScheduler.CHECK_IN.equals(action)) {
            ReminderScheduler.showCheckIn(context);
            ReminderScheduler.reschedule(context, action);
        } else if (ReminderScheduler.QUEST.equals(action)) {
            ReminderScheduler.showQuest(context, null);
            ReminderScheduler.reschedule(context, action);
        } else if (ReminderScheduler.SNOOZED.equals(action)) {
            JSONObject quest = questFrom(intent);
            StateStore.edit(context, state -> state.remove("nativeSnooze"));
            if (ReminderScheduler.quietNow()) ReminderScheduler.snooze(context, quest);
            else ReminderScheduler.showQuest(context, quest);
        } else if (ReminderScheduler.SNOOZE.equals(action)) {
            ReminderScheduler.snooze(context, questFrom(intent));
        } else if (ReminderScheduler.DONE.equals(action) || ReminderScheduler.SKIP.equals(action)) {
            JSONObject quest = questFrom(intent);
            String id = quest.optString("id", "notice-one-small-good-thing");
            String key = ReminderScheduler.DONE.equals(action) ? "completed" : "skipped";
            StateStore.edit(context, state -> {
                JSONArray events = StateStore.array(state, key);
                if (!ReminderScheduler.alreadyToday(events, id)) events.put(new JSONObject().put("id", id).put("at", System.currentTimeMillis()).put("source", "notification"));
            });
            ReminderScheduler.reconcileSnooze(context);
            context.getSystemService(NotificationManager.class).cancel(ReminderScheduler.QUEST_ID);
        } else if (ReminderScheduler.REPLY.equals(action)) {
            Bundle input = RemoteInput.getResultsFromIntent(intent);
            CharSequence raw = input == null ? null : input.getCharSequence(ReminderScheduler.REPLY_KEY);
            if (raw == null || raw.toString().trim().isEmpty()) return;
            String text = ReminderScheduler.trim(raw.toString().trim(), 2000);
            StateStore.edit(context, state -> StateStore.array(state, "entries").put(new JSONObject()
                .put("id", UUID.randomUUID().toString()).put("at", System.currentTimeMillis()).put("text", text)
                .put("mood", JSONObject.NULL).put("energy", JSONObject.NULL).put("source", "notification").put("confirmed", false)));
            ReminderScheduler.acknowledgeCheckIn(context);
        }
    }

    private JSONObject questFrom(Intent intent) {
        try { return new JSONObject(intent.getStringExtra("quest")); }
        catch (JSONException | NullPointerException ignored) {
            JSONObject fallback = new JSONObject();
            try { fallback.put("id", "notice-one-small-good-thing").put("title", "Notice a tiny wonder").put("action", "Pause and notice one small thing you enjoy around you."); }
            catch (JSONException impossible) { }
            return fallback;
        }
    }
}
