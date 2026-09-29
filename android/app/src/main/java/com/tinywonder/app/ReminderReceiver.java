package com.tinywonder.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Notification actions run without bringing up the app or requiring a backend. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ReminderScheduler.CHECK_IN.equals(action) || ReminderScheduler.REPLY.equals(action)) {
            ReminderScheduler.cancelLegacyCheckIns(context);
            return; // Old alarms/actions can no longer deliver or create a check-in.
        }
        if (!StateStore.enabled(context) || action == null) return;
        if (ReminderScheduler.QUEST.equals(action)) {
            ReminderScheduler.deliverDaily(context);
            ReminderScheduler.scheduleDaily(context);
        } else if (ReminderScheduler.SNOOZED.equals(action)) {
            JSONObject pending = StateStore.read(context).optJSONObject("nativeSnooze");
            if (pending == null || pending.optJSONObject("quest") == null) return;
            if (pending.optLong("at") > System.currentTimeMillis()) { ReminderScheduler.scheduleDaily(context); return; }
            JSONObject quest = pending.optJSONObject("quest");
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
                boolean handled = id.startsWith("generated-") ? ReminderScheduler.handled(state, id)
                    : ReminderScheduler.alreadyToday(events, id);
                if (!handled) events.put(new JSONObject().put("id", id).put("at", System.currentTimeMillis()).put("source", "notification"));
            });
            ReminderScheduler.reconcileSnooze(context);
            context.getSystemService(NotificationManager.class).cancel(ReminderScheduler.QUEST_ID);
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
