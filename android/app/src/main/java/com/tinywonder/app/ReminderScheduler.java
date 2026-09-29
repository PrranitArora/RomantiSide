package com.tinywonder.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class ReminderScheduler {
    static final String CHECK_IN = "com.tinywonder.app.CHECK_IN";
    static final String QUEST = "com.tinywonder.app.QUEST";
    static final String SNOOZED = "com.tinywonder.app.SNOOZED";
    static final String DONE = "com.tinywonder.app.DONE";
    static final String SNOOZE = "com.tinywonder.app.SNOOZE";
    static final String SKIP = "com.tinywonder.app.SKIP";
    static final String REPLY = "com.tinywonder.app.REPLY";
    static final String REPLY_KEY = "check_in_reply";
    private static final String CHANNEL = "tiny_wonder_gentle_reminders";
    private static final String SAVED_CHANNEL = "tiny_wonder_saved_checkins";
    static final int CHECK_IN_ID = 601;
    static final int QUEST_ID = 602;
    private static final Object SCHEDULE_LOCK = new Object();

    static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Daily side quests", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("One optional side quest at a surprise time in your chosen daily window. Quiet hours: 9 pm–8 am.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        cancelLegacyCheckIns(context);
    }

    static boolean canNotify(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL);
        return manager.areNotificationsEnabled() && (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    static boolean quietNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour < 8 || hour >= 21;
    }

    static void scheduleDaily(Context context) {
        synchronized (SCHEDULE_LOCK) {
            createChannel(context);
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            boolean saved = StateStore.edit(context, state -> {
                migratePreferences(state);
                if (state.getJSONObject("preferences").optBoolean("reminders"))
                    state.put(DailyQuestPlan.PLAN, DailyQuestPlan.select(state, now, zone, () -> ThreadLocalRandom.current().nextDouble()));
            });
            if (!saved || !StateStore.enabled(context) || !canNotify(context)) {
                cancelAlarm(context, QUEST, 102);
                cancelAlarm(context, SNOOZED, 103);
                return;
            }
            JSONObject state = StateStore.read(context), plan = state.optJSONObject(DailyQuestPlan.PLAN);
            if (plan != null) scheduleAt(context, QUEST, 102, Math.max(now + 1000, plan.optLong("at")), null);
            reconcileSnooze(context);
            JSONObject snooze = StateStore.read(context).optJSONObject("nativeSnooze");
            if (snooze != null && snooze.optJSONObject("quest") != null) {
                long next = allowAfterQuietHours(Math.max(now + 1000, snooze.optLong("at")));
                scheduleAt(context, SNOOZED, 103, next, snooze.optJSONObject("quest"));
            }
        }
    }

    static void configure(Context context, boolean enabled, int startHour, int endHour) {
        synchronized (SCHEDULE_LOCK) {
            StateStore.edit(context, state -> {
                JSONObject prefs = state.optJSONObject("preferences");
                if (prefs == null) { prefs = new JSONObject(); state.put("preferences", prefs); }
                prefs.put("reminders", enabled).put("questStartHour", startHour).put("questEndHour", endHour);
                migratePreferences(state);
            });
            if (enabled) scheduleDaily(context); else cancel(context);
        }
    }

    private static void migratePreferences(JSONObject state) throws JSONException {
        JSONObject prefs = state.optJSONObject("preferences");
        if (prefs == null) { prefs = new JSONObject(); state.put("preferences", prefs); }
        int start = DailyQuestPlan.start(prefs), end = DailyQuestPlan.end(prefs);
        prefs.put("questStartHour", start).put("questEndHour", end);
        prefs.remove("checkInHour"); prefs.remove("questHour");
    }

    static void cancelLegacyCheckIns(Context context) {
        cancelAlarm(context, CHECK_IN, 101);
        PendingIntent reply = PendingIntent.getBroadcast(context, 301, new Intent(context, ReminderReceiver.class).setAction(REPLY),
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_MUTABLE);
        if (reply != null) reply.cancel();
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        notifications.cancel(CHECK_IN_ID);
        notifications.deleteNotificationChannel(SAVED_CHANNEL);
    }

    private static void cancelAlarm(Context context, String action, int requestCode) {
        PendingIntent alarm = PendingIntent.getBroadcast(context, requestCode, new Intent(context, ReminderReceiver.class).setAction(action),
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (alarm != null) {
            context.getSystemService(AlarmManager.class).cancel(alarm);
            alarm.cancel();
        }
    }

    static boolean deliverDaily(Context context) {
        synchronized (SCHEDULE_LOCK) {
            cancelLegacyCheckIns(context);
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            JSONObject initial = StateStore.read(context);
            if (!StateStore.enabled(context) || !canNotify(context) || !DailyQuestPlan.due(initial, now, zone)) return false;
            JSONObject quest = chooseQuest(context);
            final boolean[] claimed = {false};
            boolean saved = StateStore.edit(context, state -> {
                if (!DailyQuestPlan.due(state, now, zone)) return;
                JSONObject days = state.optJSONObject(DailyQuestPlan.DAYS);
                if (days == null) { days = new JSONObject(); state.put(DailyQuestPlan.DAYS, days); }
                days.put(DailyQuestPlan.day(now, zone).toString(), new JSONObject().put("at", now).put("timezone", zone.getId())
                    .put("questId", quest == null ? JSONObject.NULL : quest.optString("id")).put("posted", false));
                claimed[0] = true;
            });
            if (!saved || !claimed[0]) return false;
            // The durable claim precedes notify: a crash or duplicate broadcast can
            // skip a delivery, but can never send a second automatic quest that day.
            boolean posted = quest != null && showQuest(context, quest);
            if (posted) StateStore.edit(context, state -> {
                JSONObject days = state.optJSONObject(DailyQuestPlan.DAYS);
                if (days != null && days.optJSONObject(DailyQuestPlan.day(now, zone).toString()) != null)
                    days.getJSONObject(DailyQuestPlan.day(now, zone).toString()).put("posted", true);
            });
            return posted;
        }
    }

    private static long allowAfterQuietHours(long candidate) {
        Calendar next = Calendar.getInstance(); next.setTimeInMillis(candidate);
        int hour = next.get(Calendar.HOUR_OF_DAY);
        if (hour >= 21) { next.add(Calendar.DATE, 1); next.set(Calendar.HOUR_OF_DAY, 8); next.set(Calendar.MINUTE, 0); }
        else if (hour < 8) { next.set(Calendar.HOUR_OF_DAY, 8); next.set(Calendar.MINUTE, 0); }
        next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0);
        return next.getTimeInMillis();
    }

    private static PendingIntent alarmIntent(Context context, String action, int requestCode, JSONObject quest) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(action);
        if (quest != null) intent.putExtra("quest", quest.toString());
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleAt(Context context, String action, int requestCode, long when, JSONObject quest) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        // Inexact by design: no exact-alarm permission, no persistent service.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, alarmIntent(context, action, requestCode, quest));
    }

    static void snooze(Context context, JSONObject quest) {
        if (!StateStore.enabled(context)) return;
        JSONObject current = StateStore.read(context);
        if (handled(current, quest.optString("id"))) {
            cancelSnooze(context); return;
        }
        long when = allowAfterQuietHours(System.currentTimeMillis() + 30 * 60 * 1000L);
        StateStore.edit(context, state -> state.put("nativeSnooze", new JSONObject().put("at", when).put("quest", quest)));
        scheduleAt(context, SNOOZED, 103, when, quest);
        context.getSystemService(NotificationManager.class).cancel(QUEST_ID);
    }

    static void cancelSnooze(Context context) {
        cancelAlarm(context, SNOOZED, 103);
        StateStore.edit(context, state -> state.remove("nativeSnooze"));
    }

    static void reconcileSnooze(Context context) {
        JSONObject state = StateStore.read(context);
        JSONObject snooze = state.optJSONObject("nativeSnooze");
        if (snooze == null) return;
        JSONObject quest = snooze.optJSONObject("quest");
        if (!StateStore.enabled(context) || quest == null
            || handled(state, quest.optString("id"))) cancelSnooze(context);
    }

    static void cancel(Context context) {
        cancelLegacyCheckIns(context);
        cancelAlarm(context, QUEST, 102);
        cancelAlarm(context, SNOOZED, 103);
        context.getSystemService(NotificationManager.class).cancelAll();
        StateStore.edit(context, state -> state.remove("nativeSnooze"));
    }

    private static PendingIntent open(Context context) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 201, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification.Builder base(Context context) {
        Notification publicVersion = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RomantiSide").setContentText("A gentle moment is waiting.").build();
        return new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB85D70).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setContentIntent(open(context)).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER);
    }

    private static PendingIntent action(Context context, String action, int requestCode, JSONObject quest, boolean mutable) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(action);
        if (quest != null) intent.putExtra("quest", quest.toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    static boolean showQuest(Context context, JSONObject selected) {
        if (!StateStore.enabled(context) || !canNotify(context) || quietNow()) return false;
        JSONObject quest = selected == null ? chooseQuest(context) : selected;
        if (quest == null) return false;
        JSONObject current = StateStore.read(context);
        if (handled(current, quest.optString("id"))) {
            if (selected != null) cancelSnooze(context);
            return false;
        }
        String title = trim(quest.optString("title", "A little everyday wonder"), 160);
        String instruction = trim(quest.optString("action", "Pause and notice one small thing you enjoy around you."), 1500);
        Notification notification = base(context).setContentTitle(title).setContentText(instruction)
            .setStyle(new Notification.BigTextStyle().bigText(instruction))
            .addAction(new Notification.Action.Builder(android.R.drawable.checkbox_on_background, "Done", action(context, DONE, 302, quest, false)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_lock_idle_alarm, "30 min later", action(context, SNOOZE, 303, quest, false)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Skip", action(context, SKIP, 304, quest, false)).build())
            .build();
        context.getSystemService(NotificationManager.class).notify(QUEST_ID, notification);
        return true;
    }

    static JSONObject chooseQuest(Context context) {
        JSONObject state = StateStore.read(context);
        JSONArray quests = state.optJSONArray("quests");
        if (quests == null || quests.length() == 0) quests = curatedQuests();
        if (quests != null && quests.length() > 0) {
            String[] order = {"light", "thanks", "gentle", "strength", "good", "hello", "outside", "future"};
            String[] priority = new String[order.length];
            int offset = (int) Math.floorMod(LocalDate.now().toEpochDay(), order.length);
            for (int i = 0; i < order.length; i++) priority[i] = order[(offset + i) % order.length];
            JSONArray entries = state.optJSONArray("entries");
            JSONObject latest = null;
            if (entries != null) for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry != null && entry.optBoolean("confirmed") && (latest == null || entry.optLong("at") >= latest.optLong("at"))) latest = entry;
            }
            if (latest != null && System.currentTimeMillis() - latest.optLong("at") < 86_400_000) {
                if (latest.optInt("energy") == 1) priority = new String[]{"gentle", "light", "good"};
                else if (latest.optInt("mood", 3) <= 2) priority = new String[]{"gentle", "hello", "outside"};
                else if (latest.optInt("mood", 3) >= 4) priority = new String[]{"strength", "thanks", "future"};
            }
            LinkedHashSet<String> queue = new LinkedHashSet<>();
            boolean lowEnergy = latest != null && System.currentTimeMillis() - latest.optLong("at") < 86_400_000 && latest.optInt("energy") == 1;
            for (int i = 0; i < quests.length(); i++) {
                JSONObject quest = quests.optJSONObject(i);
                if (quest != null && quest.optString("id").startsWith("generated-") && !notifiedBefore(state, quest.optString("id"))
                    && (!lowEnergy || quest.optInt("minutes", 5) <= 2)) queue.add(quest.optString("id"));
            }
            java.util.Collections.addAll(queue, priority);
            for (int i = 0; i < quests.length(); i++) {
                JSONObject quest = quests.optJSONObject(i);
                if (quest != null) queue.add(quest.optString("id"));
            }
            for (String id : queue) {
                if (id.isEmpty() || handled(state, id) || (id.startsWith("generated-") && notifiedBefore(state, id))) continue;
                for (int i = 0; i < quests.length(); i++) {
                    JSONObject candidate = quests.optJSONObject(i);
                    if (candidate != null && id.equals(candidate.optString("id")) && !candidate.optString("action").isEmpty()) return candidate;
                }
            }
            return null; // All available quests are done or skipped: leave room to rest.
        }
        return null;
    }

    private static boolean notifiedBefore(JSONObject state, String id) {
        JSONObject days = state.optJSONObject(DailyQuestPlan.DAYS);
        if (days == null) return false;
        java.util.Iterator<String> keys = days.keys();
        while (keys.hasNext()) {
            JSONObject delivery = days.optJSONObject(keys.next());
            if (delivery != null && id.equals(delivery.optString("questId"))) return true;
        }
        return false;
    }

    static JSONArray curatedQuests() {
        String[][] catalog = {
            {"light", "Find a little lovely", "Look for one small detail you like: light on a wall, a leaf, or your favorite mug. Spend 60 seconds noticing its color, shape, and texture.", "Savoring", "2"},
            {"thanks", "Send a tiny thank-you", "Think of someone who made today easier. Send them one specific sentence about what you appreciated. No need to wait for a reply.", "Gratitude & connection", "3"},
            {"gentle", "Be on your own side", "Finish this sentence: Today feels difficult because… Then write one thing you would say to a friend in the same situation.", "Self-compassion", "2"},
            {"strength", "Use your quiet superpower", "Pick a strength you value—curiosity, kindness, or creativity. Use it in one small way: ask a thoughtful question, help someone, or sketch an idea.", "Character strengths", "5"},
            {"good", "Keep one good thing", "Write down one thing that went reasonably well today and why it happened. A small comfort counts. Skip this if it feels forced.", "Gratitude", "2"},
            {"hello", "A little human connection", "Send a low-pressure hello to someone you trust, or ask a classmate or coworker how their day is going. You choose the person and the pace.", "Social connection", "3"},
            {"outside", "Take the scenic minute", "If it is safe and accessible, step outside or pause near a window. Notice three things in the natural world. Sitting still is welcome.", "Attention & savoring", "3"},
            {"future", "A postcard from tomorrow", "Imagine one small part of tomorrow going well. Write a sentence about it, then choose one tiny step that would help it happen.", "Optimism & agency", "3"}
        };
        JSONArray quests = new JSONArray();
        try {
            for (String[] item : catalog) quests.put(new JSONObject().put("id", item[0]).put("title", item[1]).put("action", item[2])
                .put("principle", item[3]).put("minutes", Integer.parseInt(item[4])));
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        return quests;
    }

    static boolean alreadyToday(JSONArray items, String id) {
        if (items == null) return false;
        Calendar today = Calendar.getInstance(); today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && id.equals(item.optString("id")) && item.optLong("at") >= today.getTimeInMillis()) return true;
        }
        return false;
    }

    static boolean handled(JSONObject state, String id) {
        for (String key : new String[]{"completed", "skipped"}) {
            JSONArray items = state.optJSONArray(key);
            if (!id.startsWith("generated-")) {
                if (alreadyToday(items, id)) return true;
            } else if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null && id.equals(item.optString("id"))) return true;
                }
            }
        }
        return false;
    }

    static String trim(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
}
