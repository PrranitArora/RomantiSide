package com.tinywonder.app;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.DoubleSupplier;
import org.json.JSONObject;

/** Deterministic planner checks: no alarms, device clock changes, storage, or network. */
final class DailyQuestPlanValidation {
    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    private static final DoubleSupplier NO_DRAW = () -> {
        throw new AssertionError("A valid persisted plan unexpectedly drew another random time");
    };

    static void run() throws Exception {
        defaultBounds();
        remainingWindow();
        preservedPlans();
        expiredWindow();
        normalizedWindows();
        halfOpenWindows();
        timezoneChanges();
        attemptedLocalDates();
        malformedPlans();
    }

    private static void defaultBounds() throws Exception {
        JSONObject state = state();
        long beforeStart = at("2026-09-29T07:00:00", LA);
        JSONObject first = DailyQuestPlan.select(state, beforeStart, LA, () -> 0.0);
        require(first.getLong("at") == at("2026-09-29T09:00:00", LA),
            "Default fraction zero did not choose 09:00");
        require(first.getInt("startHour") == 9 && first.getInt("endHour") == 20,
            "Default plan lost the 09:00–20:00 window");
        require("2026-09-29".equals(first.getString("day"))
            && LA.getId().equals(first.getString("timezone")), "Plan did not record its local date and timezone");

        JSONObject last = DailyQuestPlan.select(state, beforeStart, LA, () -> Math.nextDown(1.0));
        require(last.getLong("at") == at("2026-09-29T20:00:00", LA) - 1,
            "Largest valid random fraction escaped the exclusive 20:00 boundary");
        state.put(DailyQuestPlan.PLAN, first);
        long scheduled = first.getLong("at");
        require(!DailyQuestPlan.due(state, scheduled - 1, LA), "Plan was due before its timestamp");
        require(DailyQuestPlan.due(state, scheduled, LA), "Plan was not due at its timestamp");
        require(!DailyQuestPlan.due(state, at("2026-09-29T20:00:00", LA), LA),
            "Plan remained due at the exclusive window end");
        state.getJSONObject("preferences").put("reminders", false);
        require(!DailyQuestPlan.due(state, scheduled, LA), "A disabled plan was due");
    }

    private static void remainingWindow() throws Exception {
        JSONObject state = state();
        long now = at("2026-09-29T14:37:11.123", LA);
        JSONObject first = DailyQuestPlan.select(state, now, LA, () -> 0.0);
        require(first.getLong("at") == now + 1000, "Midday enable did not start in the remaining window");
        require("2026-09-29".equals(first.getString("day")), "Midday enable unnecessarily skipped today");
        JSONObject last = DailyQuestPlan.select(state, now, LA, () -> Math.nextDown(1.0));
        require(last.getLong("at") >= now + 1000 && last.getLong("at") < at("2026-09-29T20:00:00", LA),
            "Midday random time was outside the remaining window");
        JSONObject nearEnd = DailyQuestPlan.select(state, at("2026-09-29T19:59:59.500", LA), LA, () -> 0.0);
        require(nearEnd.getLong("at") == at("2026-09-30T09:00:00", LA),
            "Insufficient remaining time produced a past or out-of-window alarm");
    }

    private static void preservedPlans() throws Exception {
        JSONObject state = state();
        long firstOpen = at("2026-09-29T07:00:00", LA);
        JSONObject planned = DailyQuestPlan.select(state, firstOpen, LA, () -> 0.375);
        state.put(DailyQuestPlan.PLAN, planned);
        assertSamePlan(planned, DailyQuestPlan.select(state, firstOpen + 60000, LA, NO_DRAW), "reopen");

        // Parsing a saved JSON copy models loading the same plan after process death or reboot.
        JSONObject reloaded = new JSONObject(state.toString());
        assertSamePlan(planned, DailyQuestPlan.select(reloaded, at("2026-09-29T08:00:00", LA), LA, NO_DRAW),
            "reboot-equivalent JSON reload");
        long overdue = planned.getLong("at") + 30 * 60 * 1000L;
        assertSamePlan(planned, DailyQuestPlan.select(reloaded, overdue, LA, NO_DRAW), "overdue in-window reopen");
        require(DailyQuestPlan.due(reloaded, overdue, LA), "An overdue in-window plan could no longer be delivered");
        require(!DailyQuestPlan.due(reloaded, at("2026-09-30T10:00:00", LA), LA),
            "Yesterday's plan became due on the next local date");
    }

    private static void expiredWindow() throws Exception {
        JSONObject state = state();
        JSONObject today = DailyQuestPlan.select(state, at("2026-09-29T07:00:00", LA), LA, () -> 0.25);
        state.put(DailyQuestPlan.PLAN, today);
        long end = at("2026-09-29T20:00:00", LA);
        JSONObject tomorrow = DailyQuestPlan.select(state, end, LA, () -> 0.0);
        require("2026-09-30".equals(tomorrow.getString("day"))
            && tomorrow.getLong("at") == at("2026-09-30T09:00:00", LA),
            "Expired window did not move to tomorrow's allowed window");
        state.put(DailyQuestPlan.PLAN, tomorrow);
        assertSamePlan(tomorrow, DailyQuestPlan.select(state, end + 60000, LA, NO_DRAW), "tomorrow's queued plan");
        require(!DailyQuestPlan.due(state, end + 60000, LA), "Tomorrow's plan was due today");

        // Local calendar days, rather than fixed 24-hour increments, also handle year boundaries.
        JSONObject newYear = DailyQuestPlan.select(state(), at("2026-12-31T22:00:00", LA), LA, () -> 0.0);
        require(newYear.getLong("at") == at("2027-01-01T09:00:00", LA), "Year rollover chose the wrong local date");
    }

    private static void normalizedWindows() throws Exception {
        require(DailyQuestPlan.start(null) == 9 && DailyQuestPlan.end(null) == 20, "Missing preferences lost defaults");
        int[][] cases = {{-100, -100, 8, 9}, {100, 100, 20, 21}, {19, 8, 19, 20}, {8, 99, 8, 21}, {12, 12, 12, 13}};
        for (int[] item : cases) {
            JSONObject preferences = new JSONObject().put("reminders", true)
                .put("questStartHour", item[0]).put("questEndHour", item[1]);
            require(DailyQuestPlan.start(preferences) == item[2] && DailyQuestPlan.end(preferences) == item[3],
                "Invalid or reversed window was not normalized: " + item[0] + "/" + item[1]);
            JSONObject state = new JSONObject().put("preferences", preferences);
            JSONObject plan = DailyQuestPlan.select(state, at("2026-09-29T07:00:00", LA), LA, () -> 0.5);
            require(DailyQuestPlan.withinWindow(state, plan.getLong("at"), LA), "Normalized plan escaped its window");
        }
        JSONObject state = state();
        JSONObject old = DailyQuestPlan.select(state, at("2026-09-29T07:00:00", LA), LA, () -> 0.5);
        state.put(DailyQuestPlan.PLAN, old);
        state.getJSONObject("preferences").put("questStartHour", 16).put("questEndHour", 18);
        require(!DailyQuestPlan.due(state, at("2026-09-29T17:00:00", LA), LA),
            "An alarm from the previous window was accepted");
        JSONObject revised = DailyQuestPlan.select(state, at("2026-09-29T10:00:00", LA), LA, NO_DRAW);
        require(revised.getInt("startHour") == 16 && revised.getInt("endHour") == 18
            && DailyQuestPlan.withinWindow(state, revised.getLong("at"), LA), "Edited window was not applied safely");
    }

    private static void halfOpenWindows() throws Exception {
        JSONObject state = state();
        require(!DailyQuestPlan.withinWindow(state, at("2026-09-29T08:59:59.999", LA), LA), "Default window began early");
        require(DailyQuestPlan.withinWindow(state, at("2026-09-29T09:00:00", LA), LA), "Default start was excluded");
        require(!DailyQuestPlan.withinWindow(state, at("2026-09-29T20:00:00", LA), LA), "Default end was included");
        state.getJSONObject("preferences").put("questStartHour", 8).put("questEndHour", 21);
        require(!DailyQuestPlan.withinWindow(state, at("2026-09-29T07:59:59.999", LA), LA), "Custom window began early");
        require(DailyQuestPlan.withinWindow(state, at("2026-09-29T08:00:00", LA), LA), "08:00 start was excluded");
        require(DailyQuestPlan.withinWindow(state, at("2026-09-29T20:59:59.999", LA), LA), "Last allowed millisecond was excluded");
        require(!DailyQuestPlan.withinWindow(state, at("2026-09-29T21:00:00", LA), LA), "21:00 quiet hours were included");
        JSONObject last = DailyQuestPlan.select(state, at("2026-09-29T07:00:00", LA), LA, () -> Math.nextDown(1.0));
        state.put(DailyQuestPlan.PLAN, last);
        require(last.getLong("at") < at("2026-09-29T21:00:00", LA), "Random time reached the 21:00 boundary");
        require(!DailyQuestPlan.due(state, at("2026-09-29T21:00:00", LA), LA), "Delivery remained due in quiet hours");
    }

    private static void timezoneChanges() throws Exception {
        JSONObject state = state();
        JSONObject original = DailyQuestPlan.select(state, at("2026-09-29T07:00:00", LA), LA, () -> 0.5);
        state.put(DailyQuestPlan.PLAN, original);
        long now = original.getLong("at");
        ZoneId newYork = ZoneId.of("America/New_York");
        require(!DailyQuestPlan.due(state, now, newYork), "The old timezone's plan was accepted after travel");
        JSONObject sameDate = DailyQuestPlan.select(state, now, newYork, NO_DRAW);
        require(newYork.getId().equals(sameDate.getString("timezone")) && sameDate.getLong("at") > now
            && DailyQuestPlan.withinWindow(state, sameDate.getLong("at"), newYork), "Timezone change produced an unsafe plan");
        state.put(DailyQuestPlan.PLAN, sameDate);
        require(DailyQuestPlan.due(state, sameDate.getLong("at"), newYork), "Replanned timezone alarm was never due");
        require(!DailyQuestPlan.due(state, sameDate.getLong("at"), LA), "Replanned alarm retained its old timezone");

        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        JSONObject nextDate = DailyQuestPlan.select(state, now, tokyo, () -> 0.0);
        require("2026-09-30".equals(nextDate.getString("day"))
            && nextDate.getLong("at") == at("2026-09-30T09:00:00", tokyo),
            "Timezone change across midnight used a UTC or previous local date");
    }

    private static void attemptedLocalDates() throws Exception {
        require("nativeQuestPlan".equals(DailyQuestPlan.PLAN) && "nativeQuestDays".equals(DailyQuestPlan.DAYS),
            "Native planner storage keys changed");
        JSONObject state = state();
        JSONObject original = DailyQuestPlan.select(state, at("2026-09-29T07:00:00", LA), LA, () -> 0.25);
        state.put(DailyQuestPlan.PLAN, original);
        JSONObject days = new JSONObject().put("2026-09-29", new JSONObject().put("attempted", original.getLong("at")));
        state.put(DailyQuestPlan.DAYS, days);
        require(!DailyQuestPlan.due(state, original.getLong("at"), LA), "A claimed local day remained due");
        JSONObject next = DailyQuestPlan.select(state, original.getLong("at"), LA, () -> 0.0);
        require("2026-09-30".equals(next.getString("day")), "A claimed date was scheduled again");
        state.getJSONObject("preferences").put("reminders", false);
        state.getJSONObject("preferences").put("reminders", true);
        require(!DailyQuestPlan.due(state, original.getLong("at"), LA), "Opt-out/re-enable cleared the daily claim");

        // A backward clock/date change must skip every date already claimed, not only the latest date.
        days.put("2026-09-30", new JSONObject().put("attempted", at("2026-09-30T12:00:00", LA)).put("posted", true));
        JSONObject rollback = DailyQuestPlan.select(state, at("2026-09-29T10:00:00", LA), LA, () -> 0.0);
        require("2026-10-01".equals(rollback.getString("day")), "Clock rollback repeated an already attempted date");
        state.put(DailyQuestPlan.PLAN, rollback);
        require(!DailyQuestPlan.due(state, at("2026-09-29T15:00:00", LA), LA), "Rollback accepted a future plan");

        // This instant is September 30 in UTC but still September 29 in Los Angeles.
        JSONObject localState = state();
        localState.put(DailyQuestPlan.DAYS, new JSONObject().put("2026-09-30", new JSONObject().put("attempted", true)));
        long localEvening = Instant.parse("2026-09-30T02:00:00Z").toEpochMilli();
        JSONObject localPlan = DailyQuestPlan.select(localState, localEvening, LA, () -> 0.0);
        require("2026-09-29".equals(localPlan.getString("day")), "Daily claim lookup used UTC instead of local date");
        localState.put(DailyQuestPlan.PLAN, localPlan);
        require(DailyQuestPlan.due(localState, localPlan.getLong("at"), LA), "Another date's marker blocked today's plan");
    }

    private static void malformedPlans() throws Exception {
        long morning = at("2026-09-29T10:00:00", LA);
        JSONObject state = state();
        JSONObject valid = DailyQuestPlan.select(state, at("2026-09-29T07:00:00", LA), LA, () -> 0.0);
        JSONObject beforeWindow = new JSONObject(valid.toString()).put("at", at("2026-09-29T08:00:00", LA));
        state.put(DailyQuestPlan.PLAN, beforeWindow);
        require(!DailyQuestPlan.due(state, morning, LA), "Malformed out-of-window plan was accepted as due");
        JSONObject repaired = DailyQuestPlan.select(state, morning, LA, NO_DRAW);
        require(repaired.getLong("at") >= morning + 1000 && DailyQuestPlan.withinWindow(state, repaired.getLong("at"), LA),
            "Malformed timestamp was not repaired into the remaining window");
        JSONObject missingAt = new JSONObject(valid.toString());
        missingAt.remove("at");
        state.put(DailyQuestPlan.PLAN, missingAt);
        require(!DailyQuestPlan.due(state, morning, LA), "Plan without a timestamp was accepted as due");
        JSONObject invalidDay = new JSONObject(valid.toString()).put("day", "not-a-date");
        state.put(DailyQuestPlan.PLAN, invalidDay);
        JSONObject replaced = DailyQuestPlan.select(state, morning, LA, () -> 0.25);
        require("2026-09-29".equals(replaced.getString("day")) && replaced.getLong("at") > morning,
            "Malformed local date prevented replanning");
    }

    private static JSONObject state() throws Exception {
        return new JSONObject().put("preferences", new JSONObject().put("reminders", true));
    }

    private static long at(String localDateTime, ZoneId zone) {
        return LocalDateTime.parse(localDateTime).atZone(zone).toInstant().toEpochMilli();
    }

    private static void assertSamePlan(JSONObject expected, JSONObject actual, String operation) throws Exception {
        for (String key : new String[]{"at", "day", "timezone", "startHour", "endHour", "fraction"}) {
            Object before = expected.get(key), after = actual.get(key);
            boolean equal = before instanceof Number && after instanceof Number
                ? ((Number) before).doubleValue() == ((Number) after).doubleValue() : before.toString().equals(after.toString());
            require(equal, operation + " changed plan field " + key);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
