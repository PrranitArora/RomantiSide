const test = require("node:test");
const assert = require("node:assert/strict");
const W = require("../android/app/src/main/assets/logic.js");
const now = new Date(2026, 8, 29, 14).getTime();
test("Circle event identity stays stable across timezone changes", () => {
  const original = process.env.TZ;
  try {
    const event = { id: "light", at: 1790730000000 };
    process.env.TZ = "America/Los_Angeles";
    const first = W.completionEventId(event);
    process.env.TZ = "Asia/Tokyo";
    assert.equal(W.completionEventId(event), first);
    assert.notEqual(
      W.completionEventId({ ...event, at: event.at + 86400000 }),
      first,
    );
  } finally {
    if (original === undefined) delete process.env.TZ;
    else process.env.TZ = original;
  }
});
test("low energy selects manageable quests using only a recent confirmed check-in", () => {
  const s = W.defaults();
  s.entries = [{ at: now, mood: 4, energy: 1, confirmed: true }];
  assert.deepEqual(
    W.queue(s, now)
      .slice(0, 3)
      .map((x) => x.id),
    ["gentle", "light", "good"],
  );
  s.entries[0].confirmed = false;
  assert.equal(W.queue(s, now)[0].id, "light");
  s.entries[0].confirmed = true;
  s.entries[0].at = now - 86400001;
  assert.equal(W.queue(s, now)[0].id, "light");
});
test("completed and skipped quests disappear only for the local calendar day", () => {
  const s = W.defaults();
  s.completed = [{ id: "light", at: now }];
  s.skipped = [{ id: "thanks", at: now }];
  const ids = W.queue(s, now).map((x) => x.id);
  assert(!ids.includes("light"));
  assert(!ids.includes("thanks"));
  assert(W.queue(s, now + 86400000).some((x) => x.id === "light"));
  s.completed = W.quests.map((q) => ({ id: q.id, at: now }));
  assert.equal(W.queue(s, now).length, 0);
});
test("unrated notification replies never become mood measurements", () => {
  const means = W.dailyMeans([
    { at: now, mood: 5, confirmed: true },
    { at: now + 1, mood: 3, confirmed: true },
    { at: now + 2, mood: null, confirmed: false },
    { at: now + 3, mood: 100, confirmed: true },
  ]);
  assert.equal(means.length, 1);
  assert.equal(means[0].value, 4);
});
test("native metadata and opt-out default survive normalization", () => {
  assert.equal(W.defaults().preferences.reminders, false);
  const s = W.normalize({
    nativeSnooze: { at: now },
    entries: null,
    preferences: { questHour: 16 },
  });
  assert.equal(s.nativeSnooze.at, now);
  assert.deepEqual(s.entries, []);
  assert.equal(s.preferences.questHour, 16);
  assert.equal(s.preferences.checkInHour, 10);
});
test("quest identifiers are unique and all activities have sources and bounded time", () => {
  assert.equal(new Set(W.quests.map((q) => q.id)).size, W.quests.length);
  for (const q of W.quests) {
    assert(q.action.length > 30);
    assert(q.minutes > 0 && q.minutes <= 5);
    assert(q.evidence.startsWith("https://"));
  }
});
