package com.tinywonder.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.DoubleSupplier;
import org.json.JSONException;
import org.json.JSONObject;

/** Pure local-time planning; persistence and Android alarms are owned by the scheduler. */
final class DailyQuestPlan {
    static final String PLAN = "nativeQuestPlan";
    static final String DAYS = "nativeQuestDays";

    static int start(JSONObject preferences) {
        return Math.max(8, Math.min(20, preferences == null ? 9 : preferences.optInt("questStartHour", 9)));
    }

    static int end(JSONObject preferences) {
        return Math.max(start(preferences) + 1, Math.min(21, preferences == null ? 20 : preferences.optInt("questEndHour", 20)));
    }

    static LocalDate day(long now, ZoneId zone) { return Instant.ofEpochMilli(now).atZone(zone).toLocalDate(); }
    static long hour(LocalDate date, int hour, ZoneId zone) { return date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(); }

    static boolean attempted(JSONObject state, LocalDate date) {
        JSONObject days = state.optJSONObject(DAYS);
        return days != null && days.has(date.toString());
    }

    static boolean withinWindow(JSONObject state, long now, ZoneId zone) {
        JSONObject preferences = state.optJSONObject("preferences");
        LocalDate date = day(now, zone);
        return now >= hour(date, start(preferences), zone) && now < hour(date, end(preferences), zone);
    }

    static boolean due(JSONObject state, long now, ZoneId zone) {
        JSONObject plan = state.optJSONObject(PLAN), preferences = state.optJSONObject("preferences");
        LocalDate today = day(now, zone);
        return plan != null && preferences != null && preferences.optBoolean("reminders")
            && !attempted(state, today) && today.toString().equals(plan.optString("day"))
            && zone.getId().equals(plan.optString("timezone"))
            && plan.optInt("startHour") == start(preferences) && plan.optInt("endHour") == end(preferences)
            && plan.optLong("at", Long.MAX_VALUE) >= hour(today, start(preferences), zone)
            && plan.optLong("at", Long.MAX_VALUE) < hour(today, end(preferences), zone)
            && plan.optLong("at", Long.MAX_VALUE) <= now && withinWindow(state, now, zone);
    }

    static JSONObject select(JSONObject state, long now, ZoneId zone, DoubleSupplier random) throws JSONException {
        JSONObject preferences = state.optJSONObject("preferences"), prior = state.optJSONObject(PLAN);
        int start = start(preferences), end = end(preferences);
        LocalDate today = day(now, zone);
        if (prior != null && zone.getId().equals(prior.optString("timezone"))
            && prior.optInt("startHour") == start && prior.optInt("endHour") == end) {
            try {
                LocalDate plannedDay = LocalDate.parse(prior.getString("day"));
                long at = prior.optLong("at");
                if (!plannedDay.isBefore(today) && !plannedDay.isAfter(today.plusDays(1))
                    && !attempted(state, plannedDay) && at >= hour(plannedDay, start, zone) && at < hour(plannedDay, end, zone)
                    && (at >= now || (plannedDay.equals(today) && now < hour(today, end, zone))))
                    return new JSONObject(prior.toString());
            } catch (java.time.DateTimeException | JSONException ignored) { }
        }
        LocalDate chosenDay = today;
        if (now + 1000 >= hour(today, end, zone)) chosenDay = today.plusDays(1);
        while (attempted(state, chosenDay)) chosenDay = chosenDay.plusDays(1);
        long earliest = Math.max(hour(chosenDay, start, zone), now + 1000);
        long latest = hour(chosenDay, end, zone);
        double fraction = prior != null && chosenDay.toString().equals(prior.optString("day"))
            ? prior.optDouble("fraction", -1) : -1;
        if (!Double.isFinite(fraction) || fraction < 0 || fraction >= 1) fraction = random.getAsDouble();
        if (!Double.isFinite(fraction) || fraction < 0 || fraction >= 1) throw new IllegalArgumentException("Random fraction must be in [0, 1).");
        long at = earliest + (long) Math.floor(fraction * (latest - earliest));
        return new JSONObject().put("at", at).put("day", chosenDay.toString()).put("timezone", zone.getId())
            .put("startHour", start).put("endHour", end).put("fraction", fraction);
    }
}
