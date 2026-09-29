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
  assert.equal(W.queue(s, now)[0].id, W.queue(W.defaults(), now)[0].id);
  s.entries[0].confirmed = true;
  s.entries[0].at = now - 86400001;
  assert.equal(W.queue(s, now)[0].id, W.queue(W.defaults(), now)[0].id);
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
test("old fixed reminders migrate to a daytime quest window without losing consent or native metadata", () => {
  assert.equal(W.defaults().preferences.reminders, false);
  const s = W.normalize({
    nativeSnooze: { at: now },
    entries: null,
    preferences: { reminders: true, questHour: 16, checkInHour: 10 },
    nativeQuestPlan: { at: now + 1000 },
  });
  assert.equal(s.nativeSnooze.at, now);
  assert.deepEqual(s.entries, []);
  assert.deepEqual(s.preferences, {
    reminders: true,
    questStartHour: 9,
    questEndHour: 20,
  });
  assert.deepEqual(s.nativeQuestPlan, { at: now + 1000 });
});
test("notification windows stay within waking hours and never wrap overnight", () => {
  assert.deepEqual(
    W.notificationPreferences({
      reminders: true,
      questStartHour: 8,
      questEndHour: 21,
    }),
    { reminders: true, questStartHour: 8, questEndHour: 21 },
  );
  for (const [start, end] of [
    [20, 8],
    [12, 12],
    [7, 21],
    [8, 22],
    [NaN, 20],
  ]) {
    const p = W.notificationPreferences({
      reminders: true,
      questStartHour: start,
      questEndHour: end,
    });
    assert.deepEqual(p, {
      reminders: true,
      questStartHour: 9,
      questEndHour: 20,
    });
  }
  assert.equal(
    W.notificationPreferences({ reminders: "true" }).reminders,
    false,
  );
});
test("a user who never checks in or creates a profile still has varied positive-psychology quests every day", () => {
  const s = W.defaults();
  const firsts = new Set();
  for (let day = 0; day < 8; day++) {
    const queued = W.queue(s, now + day * 86400000);
    assert.equal(queued.length, 8);
    assert(
      queued.every((q) => q.principle && q.evidence.startsWith("https://")),
    );
    firsts.add(queued[0].id);
  }
  assert.equal(firsts.size, 8);
  assert.equal(s.entries.length, 0);
  assert.equal(s.questProfile, null);
});
test("quest identifiers are unique and all activities have sources and bounded time", () => {
  assert.equal(new Set(W.quests.map((q) => q.id)).size, W.quests.length);
  for (const q of W.quests) {
    assert(q.action.length > 30);
    assert(q.minutes > 0 && q.minutes <= 5);
    assert(q.evidence.startsWith("https://"));
  }
});
const generated = (suffix, action, activityKey, title = "A fresh moment") => ({
  id: "generated-" + suffix.repeat(32),
  title,
  action,
  activityKey,
  minutes: 2,
  principle: "Savoring",
  icon: "sun",
  color: "peach",
});
test("generated quests survive reload and are retired across days after completion or skip", () => {
  const s = W.defaults();
  const q = generated(
    "a",
    "Listen quietly to a favorite song and notice one instrument.",
    "listen-music",
  );
  assert.equal(W.addGenerated(s, [q], now).length, 1);
  const loaded = W.normalize(JSON.parse(JSON.stringify(s)));
  assert.equal(W.queue(loaded, now)[0].id, q.id);
  assert(loaded.quests.some((x) => x.id === q.id));
  loaded.completed.push({ id: q.id, at: now });
  assert(!W.queue(loaded, now + 86400000).some((x) => x.id === q.id));
  loaded.completed = [];
  loaded.skipped.push({ id: q.id, at: now });
  assert(!W.queue(loaded, now + 86400000).some((x) => x.id === q.id));
});
test("local lifetime history blocks synonyms even beyond the server history window", () => {
  const s = W.defaults();
  const first = generated(
    "a",
    "Take a short walk around your room.",
    "walk-room",
    "A small walk",
  );
  W.addGenerated(s, [first], now);
  s.generatedQuests = []; // History remains even if a future version prunes full retired cards.
  const repeat = generated(
    "b",
    "Go for a quick stroll around the room.",
    "stroll-room",
    "A different title",
  );
  const distinct = generated(
    "c",
    "Draw a familiar object using three colors.",
    "draw-object",
    "A color sketch",
  );
  assert.deepEqual(
    W.addGenerated(s, [repeat, distinct], now).map((q) => q.id),
    [distinct.id],
  );
  assert.equal(s.questHistory.length, 2);
});
test("new quest batches filter invalid identities and duplicate actions", () => {
  const s = W.defaults();
  const q = generated(
    "a",
    "Listen to a favorite piece of music.",
    "listen-music",
  );
  assert.equal(
    W.addGenerated(s, [
      { ...q, id: 'unsafe"' },
      q,
      { ...q, id: "generated-" + "b".repeat(32), title: "Renamed" },
    ]).length,
    1,
  );
});
test("conversation history stays within character and turn limits while keeping whole pairs", () => {
  const messages = Array.from({ length: 10 }, (_, i) => ({
    role: i % 2 ? "assistant" : "user",
    content: "x".repeat(i % 2 ? 1800 : 2000),
  }));
  const result = W.chatHistory(messages, "z".repeat(2000));
  assert(result.reduce((n, m) => n + m.content.length, 0) <= 12000);
  assert(result.length <= 12);
  assert.equal(result[0].role, "user");
  result.forEach((m, i) => assert.equal(m.role, i % 2 ? "assistant" : "user"));
  assert.equal(result.at(-1).content, "z".repeat(2000));
});
test("browser and server semantic duplicate screening agree on representative activities", async () => {
  const { validateAndDedupeQuests } = await import(
    "../server/personalization.mjs"
  );
  for (const [first, second, key1, key2] of [
    ["Take a short walk.", "Go for a quick stroll.", "walk", "stroll"],
    [
      "Write a thank you note to a friend.",
      "Jot a gratitude message to a friend.",
      "write-thanks",
      "write-gratitude",
    ],
    [
      "Draw a leaf.",
      "Listen to some quiet music.",
      "draw-leaf",
      "listen-music",
    ],
  ]) {
    const prior = { title: "Prior", action: first, activityKey: key1 };
    const candidate = {
      title: "New",
      action: second,
      activityKey: key2,
      mechanism: "savoring",
      minutes: 2,
    };
    const server = validateAndDedupeQuests([candidate], [prior]);
    assert.equal(W.duplicate(candidate, prior), server.duplicateCount === 1);
  }
});
