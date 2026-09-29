package com.tinywonder.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Calendar;
import java.util.LinkedHashSet;
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

    static void createChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Gentle check-ins & side quests", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Your opt-in daily check-in and small wellbeing side quests. Quiet hours: 9 pm–8 am.");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        NotificationChannel saved = new NotificationChannel(SAVED_CHANNEL, "Saved check-ins", NotificationManager.IMPORTANCE_LOW);
        saved.setDescription("Silent confirmations when you reply to a check-in.");
        saved.setSound(null, null);
        saved.enableVibration(false);
        saved.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        context.getSystemService(NotificationManager.class).createNotificationChannel(saved);
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
        if (!StateStore.enabled(context)) return;
        createChannel(context);
        scheduleAt(context, CHECK_IN, 101, nextHour(StateStore.hour(context, "checkInHour", 10)), null);
        scheduleAt(context, QUEST, 102, nextHour(StateStore.hour(context, "questHour", 14)), null);
        JSONObject snooze = StateStore.read(context).optJSONObject("nativeSnooze");
        if (snooze != null && snooze.optJSONObject("quest") != null) {
            long next = allowAfterQuietHours(Math.max(System.currentTimeMillis() + 1000, snooze.optLong("at")));
            scheduleAt(context, SNOOZED, 103, next, snooze.optJSONObject("quest"));
        }
    }

    static void reschedule(Context context, String action) {
        if (!StateStore.enabled(context)) return;
        if (CHECK_IN.equals(action)) scheduleAt(context, CHECK_IN, 101, nextHour(StateStore.hour(context, "checkInHour", 10)), null);
        else if (QUEST.equals(action)) scheduleAt(context, QUEST, 102, nextHour(StateStore.hour(context, "questHour", 14)), null);
    }

    private static long nextHour(int hour) {
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, 0); next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DATE, 1);
        return next.getTimeInMillis();
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
        if (alreadyToday(current.optJSONArray("completed"), quest.optString("id"))
            || alreadyToday(current.optJSONArray("skipped"), quest.optString("id"))) {
            cancelSnooze(context); return;
        }
        long when = allowAfterQuietHours(System.currentTimeMillis() + 30 * 60 * 1000L);
        StateStore.edit(context, state -> state.put("nativeSnooze", new JSONObject().put("at", when).put("quest", quest)));
        scheduleAt(context, SNOOZED, 103, when, quest);
        context.getSystemService(NotificationManager.class).cancel(QUEST_ID);
    }

    static void cancelSnooze(Context context) {
        context.getSystemService(AlarmManager.class).cancel(alarmIntent(context, SNOOZED, 103, null));
        StateStore.edit(context, state -> state.remove("nativeSnooze"));
    }

    static void reconcileSnooze(Context context) {
        JSONObject state = StateStore.read(context);
        JSONObject snooze = state.optJSONObject("nativeSnooze");
        if (snooze == null) return;
        JSONObject quest = snooze.optJSONObject("quest");
        if (!StateStore.enabled(context) || quest == null
            || alreadyToday(state.optJSONArray("completed"), quest.optString("id"))
            || alreadyToday(state.optJSONArray("skipped"), quest.optString("id"))) cancelSnooze(context);
    }

    static void cancel(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        manager.cancel(alarmIntent(context, CHECK_IN, 101, null));
        manager.cancel(alarmIntent(context, QUEST, 102, null));
        manager.cancel(alarmIntent(context, SNOOZED, 103, null));
        context.getSystemService(NotificationManager.class).cancelAll();
        StateStore.edit(context, state -> state.remove("nativeSnooze"));
    }

    private static PendingIntent open(Context context) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 201, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Notification.Builder base(Context context) {
        Notification publicVersion = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tiny Wonder").setContentText("A gentle moment is waiting.").build();
        return new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFED704F).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setContentIntent(open(context)).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_REMINDER);
    }

    private static PendingIntent action(Context context, String action, int requestCode, JSONObject quest, boolean mutable) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(action);
        if (quest != null) intent.putExtra("quest", quest.toString());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (mutable ? PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_IMMUTABLE);
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    static boolean showCheckIn(Context context) {
        if (!StateStore.enabled(context) || !canNotify(context) || quietNow()) return false;
        String prompt = "How are you feeling, and what is one thing you need today? Reply in a few words. You can confirm your mood later.";
        RemoteInput input = new RemoteInput.Builder(REPLY_KEY).setLabel("How are you feeling?").build();
        Notification.Action reply = new Notification.Action.Builder(android.R.drawable.ic_menu_edit, "Reply", action(context, REPLY, 301, null, true))
            .addRemoteInput(input).setAllowGeneratedReplies(false).build();
        Notification notification = base(context).setContentTitle("Two minutes for you")
            .setContentText(prompt).setStyle(new Notification.BigTextStyle().bigText(prompt)).addAction(reply).build();
        context.getSystemService(NotificationManager.class).notify(CHECK_IN_ID, notification);
        return true;
    }

    static void acknowledgeCheckIn(Context context) {
        if (!canNotify(context)) return;
        Notification notification = base(context).setContentTitle("Your check-in is saved")
            .setContentText("Open Tiny Wonder whenever you want to confirm your mood.")
            .setChannelId(SAVED_CHANNEL).build();
        context.getSystemService(NotificationManager.class).notify(CHECK_IN_ID, notification);
    }

    static boolean showQuest(Context context, JSONObject selected) {
        if (!StateStore.enabled(context) || !canNotify(context) || quietNow()) return false;
        JSONObject quest = selected == null ? chooseQuest(context) : selected;
        if (quest == null) return false;
        JSONObject current = StateStore.read(context);
        if (alreadyToday(current.optJSONArray("completed"), quest.optString("id"))
            || alreadyToday(current.optJSONArray("skipped"), quest.optString("id"))) {
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
        if (quests != null && quests.length() > 0) {
            String[] priority = {"light", "thanks", "strength"};
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
            java.util.Collections.addAll(queue, priority);
            for (int i = 0; i < quests.length(); i++) {
                JSONObject quest = quests.optJSONObject(i);
                if (quest != null) queue.add(quest.optString("id"));
            }
            for (String id : queue) {
                if (id.isEmpty() || alreadyToday(state.optJSONArray("completed"), id) || alreadyToday(state.optJSONArray("skipped"), id)) continue;
                for (int i = 0; i < quests.length(); i++) {
                    JSONObject candidate = quests.optJSONObject(i);
                    if (candidate != null && id.equals(candidate.optString("id")) && !candidate.optString("action").isEmpty()) return candidate;
                }
            }
            return null; // All available quests are done or skipped: leave room to rest.
        }
        JSONObject fallback = new JSONObject();
        try {
            fallback.put("id", "light").put("title", "Notice a tiny wonder")
                .put("action", "Pause for one minute. Notice one small thing you enjoy: a color, a sound, or the warmth of your drink. Give it your attention without needing to change how you feel.")
                .put("principle", "Savoring").put("minutes", 1);
        } catch (JSONException ignored) { }
        return fallback;
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

    static String trim(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
}
