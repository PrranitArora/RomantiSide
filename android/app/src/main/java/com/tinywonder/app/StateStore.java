package com.tinywonder.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;

/** One app-private JSON document shared by the UI and notification receivers. */
final class StateStore {
    private static final Object LOCK = new Object();
    private static final String FILE = "tiny_wonder";
    private static final String KEY = "state";
    private static final int MAX_STATE_CHARS = 2_000_000;

    interface Edit { void apply(JSONObject state) throws JSONException; }

    static JSONObject read(Context context) {
        synchronized (LOCK) {
            try { return new JSONObject(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "{}")); }
            catch (JSONException ignored) { return new JSONObject(); }
        }
    }

    static boolean save(Context context, String value) {
        if (value == null || value.length() > MAX_STATE_CHARS) return false;
        final boolean saved;
        synchronized (LOCK) {
            try {
                JSONObject validated = new JSONObject(value);
                JSONObject latest = read(context);
                // Receivers may append events while an open form still holds an older state.
                // UI copies replace matching check-ins so explicitly confirmed ratings win.
                for (String key : new String[]{"entries", "completed", "skipped", "feedback"}) {
                    validated.put(key, mergeEvents(latest.optJSONArray(key), validated.optJSONArray(key), key));
                }
                if (latest.has("nativeSnooze")) validated.put("nativeSnooze", latest.get("nativeSnooze"));
                else validated.remove("nativeSnooze");
                String serialized = validated.toString();
                // Independent native events can make a merged state larger than its
                // incoming UI copy. Check the actual persisted value before writing.
                if (serialized.length() > MAX_STATE_CHARS) return false;
                saved = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, serialized).commit();
            } catch (JSONException ignored) { return false; }
        }
        if (saved) ReminderScheduler.reconcileSnooze(context);
        return saved;
    }

    private static JSONArray mergeEvents(JSONArray latest, JSONArray incoming, String kind) {
        LinkedHashMap<String, JSONObject> events = new LinkedHashMap<>();
        for (JSONArray source : new JSONArray[]{latest, incoming}) {
            if (source == null) continue;
            for (int i = 0; i < source.length(); i++) {
                JSONObject event = source.optJSONObject(i);
                if (event == null) continue;
                String id = event.optString("id");
                String key = id.isEmpty() ? event.toString() : id;
                if ("completed".equals(kind) || "skipped".equals(kind)) {
                    Calendar day = Calendar.getInstance(); day.setTimeInMillis(event.optLong("at"));
                    key += ":" + day.get(Calendar.YEAR) + ":" + day.get(Calendar.DAY_OF_YEAR);
                } else if ("feedback".equals(kind)) key += ":" + event.optLong("at");
                events.put(key, event);
            }
        }
        ArrayList<JSONObject> ordered = new ArrayList<>(events.values());
        ordered.sort(Comparator.comparingLong(event -> event.optLong("at")));
        return new JSONArray(ordered);
    }

    static void edit(Context context, Edit edit) {
        synchronized (LOCK) {
            JSONObject state = read(context);
            try {
                edit.apply(state);
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, state.toString()).commit();
            } catch (JSONException ignored) { }
        }
    }

    static void clear(Context context) {
        synchronized (LOCK) {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    static JSONArray array(JSONObject state, String key) throws JSONException {
        JSONArray values = state.optJSONArray(key);
        if (values == null) { values = new JSONArray(); state.put(key, values); }
        return values;
    }

    static boolean enabled(Context context) {
        JSONObject prefs = read(context).optJSONObject("preferences");
        return prefs != null && prefs.optBoolean("reminders", false);
    }

    static int hour(Context context, String key, int fallback) {
        JSONObject prefs = read(context).optJSONObject("preferences");
        return Math.min(20, Math.max(8, prefs == null ? fallback : prefs.optInt(key, fallback)));
    }
}
