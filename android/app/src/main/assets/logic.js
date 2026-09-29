(function (root) {
  "use strict";
  const quests = [
    {
      id: "light",
      title: "Find a little lovely",
      action:
        "Look for one small detail you like: light on a wall, a leaf, or your favorite mug. Spend 60 seconds noticing its color, shape, and texture.",
      principle: "Savoring",
      minutes: 2,
      icon: "sun",
      color: "peach",
      why: "Savoring means deliberately attending to a pleasant experience. This is an adaptation of studied exercises, not a tested treatment.",
      evidence:
        "https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.791040/full",
    },
    {
      id: "thanks",
      title: "Send a tiny thank-you",
      action:
        "Think of someone who made today easier. Send them one specific sentence about what you appreciated. No need to wait for a reply.",
      principle: "Gratitude & connection",
      minutes: 3,
      icon: "heart",
      color: "pink",
      why: "Inspired by longer gratitude-visit exercises, shortened here to a message. Choose someone you feel comfortable contacting.",
      evidence: "https://doi.org/10.1037/0003-066X.60.5.410",
    },
    {
      id: "gentle",
      title: "Be on your own side",
      action:
        "Finish this sentence: “Today feels difficult because…” Then write one thing you would say to a friend in the same situation.",
      principle: "Self-compassion",
      minutes: 2,
      icon: "cloud",
      color: "lavender",
      why: "A compassionate response makes room for difficult feelings. This short adaptation is not the eight-week program in the linked study.",
      evidence: "https://doi.org/10.1002/jclp.21923",
    },
    {
      id: "strength",
      title: "Use your quiet superpower",
      action:
        "Pick a strength you value—curiosity, kindness, or creativity. Use it in one small way: ask a thoughtful question, help someone, or sketch an idea.",
      principle: "Character strengths",
      minutes: 5,
      icon: "spark",
      color: "yellow",
      why: "Using personal strengths in a new way is a studied positive psychology exercise. The shortened activity here needs its own evaluation.",
      evidence: "https://doi.org/10.1037/0003-066X.60.5.410",
    },
    {
      id: "good",
      title: "Keep one good thing",
      action:
        "Write down one thing that went reasonably well today and why it happened. A small comfort counts. Skip this if it feels forced.",
      principle: "Gratitude",
      minutes: 2,
      icon: "leaf",
      color: "sage",
      why: "This is a shorter adaptation of the “three good things” exercise. Noticing something good does not erase what was difficult.",
      evidence: "https://doi.org/10.1037/0003-066X.60.5.410",
    },
    {
      id: "hello",
      title: "A little human connection",
      action:
        "Send a low-pressure hello to someone you trust, or ask a classmate or coworker how their day is going. You choose the person and the pace.",
      principle: "Social connection",
      minutes: 3,
      icon: "heart",
      color: "pink",
      why: "Small social interactions can support a sense of connection. This optional activity is a product hypothesis, not a guaranteed mood boost.",
      evidence: "https://doi.org/10.1037/a0037323",
    },
    {
      id: "outside",
      title: "Take the scenic minute",
      action:
        "If it is safe and accessible, step outside or pause near a window. Notice three things in the natural world. Sitting still is welcome.",
      principle: "Attention & savoring",
      minutes: 3,
      icon: "leaf",
      color: "sage",
      why: "This combines a brief attention shift with savoring. It is not the same dose or activity as longer nature interventions in research.",
      evidence:
        "https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.791040/full",
    },
    {
      id: "future",
      title: "A postcard from tomorrow",
      action:
        "Imagine one small part of tomorrow going well. Write a sentence about it, then choose one tiny step that would help it happen.",
      principle: "Optimism & agency",
      minutes: 3,
      icon: "spark",
      color: "yellow",
      why: "Inspired by best-possible-self writing, with a much shorter duration. Pairing a hopeful picture with a doable step is a design choice to test.",
      evidence: "https://doi.org/10.1371/journal.pone.0222386",
    },
  ];
  const dayKey = (t) => {
    let d = new Date(t);
    return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
  };
  function defaults() {
    return {
      entries: [],
      completed: [],
      skipped: [],
      memories: [],
      feedback: [],
      generatedQuests: [],
      questHistory: [],
      questProfile: null,
      quests: quests,
      preferences: { reminders: false, checkInHour: 10, questHour: 14 },
      onboarded: false,
    };
  }
  function normalize(s) {
    const d = defaults();
    if (!s || typeof s !== "object") return d;
    const retired = new Set(
      [
        ...(Array.isArray(s.completed) ? s.completed : []),
        ...(Array.isArray(s.skipped) ? s.skipped : []),
      ].map((x) => x.id),
    );
    const generated = Array.isArray(s.generatedQuests)
      ? s.generatedQuests.filter((q) => validGenerated(q) && !retired.has(q.id))
      : [];
    return {
      ...d,
      ...s,
      entries: Array.isArray(s.entries) ? s.entries : [],
      completed: Array.isArray(s.completed) ? s.completed : [],
      skipped: Array.isArray(s.skipped) ? s.skipped : [],
      memories: Array.isArray(s.memories) ? s.memories : [],
      feedback: Array.isArray(s.feedback) ? s.feedback : [],
      generatedQuests: generated,
      questHistory: Array.isArray(s.questHistory) ? s.questHistory : [],
      quests: [...generated, ...quests],
      preferences: { ...d.preferences, ...s.preferences },
    };
  }
  function suggestion(text) {
    const t = String(text).toLowerCase();
    if (/\b(overwhelm|overwhelmed|anxious|stressed|stress|worried)\b/.test(t))
      return "Overwhelmed";
    if (/\b(sad|down|lonely|low|upset)\b/.test(t)) return "Low";
    if (/\b(happy|great|excited|joy|good)\b/.test(t)) return "Good";
    if (/\b(tired|drained|sleepy|exhausted)\b/.test(t)) return "Flat";
    return null;
  }
  function queue(s, now = Date.now()) {
    const today = dayKey(now);
    const latest = s.entries.filter((e) => e.confirmed).slice(-1)[0];
    let priority = ["light", "thanks", "strength"];
    if (latest && now - latest.at < 86400000) {
      if (latest.energy === 1) priority = ["gentle", "light", "good"];
      else if (latest.mood <= 2) priority = ["gentle", "hello", "outside"];
      else if (latest.mood >= 4) priority = ["strength", "thanks", "future"];
    }
    const done = new Set(
      [...s.completed, ...s.skipped]
        .filter((x) => dayKey(x.at) === today)
        .map((x) => x.id),
    );
    const generated = (s.generatedQuests || []).filter(validGenerated);
    const retired = new Set([...s.completed, ...s.skipped].map((x) => x.id));
    const fresh = generated.filter((q) => !retired.has(q.id));
    const suitable = fresh.filter(
      (q) =>
        !(
          latest &&
          now - latest.at < 86400000 &&
          latest.energy === 1 &&
          q.minutes > 2
        ),
    );
    const bank = [...fresh, ...quests];
    const ids = [
      ...suitable.map((q) => q.id),
      ...priority,
      ...bank.map((q) => q.id),
    ].filter((id, i, a) => a.indexOf(id) === i && !done.has(id));
    return ids.map((id) => bank.find((q) => q.id === id)).filter(Boolean);
  }
  function validGenerated(q) {
    return (
      q &&
      /^generated-[a-f0-9]{32}$/.test(q.id) &&
      typeof q.title === "string" &&
      q.title.length <= 80 &&
      typeof q.action === "string" &&
      q.action.length <= 500 &&
      Number.isInteger(q.minutes) &&
      q.minutes >= 1 &&
      q.minutes <= 5 &&
      typeof q.activityKey === "string"
    );
  }
  const dedup =
    typeof module !== "undefined"
      ? require("./quest-dedup.js")
      : root.QuestDedup;
  const canonical = dedup.canonicalText;
  const duplicate = (a, b) =>
    (a.id && a.id === b.id) || dedup.isDuplicate(a, b);
  function addGenerated(s, candidates, now = Date.now()) {
    const history = [
      ...quests,
      ...(s.generatedQuests || []),
      ...(s.questHistory || []),
    ];
    const accepted = [];
    for (const q of candidates || []) {
      if (!validGenerated(q) || history.some((h) => duplicate(q, h))) continue;
      const item = { ...q, generated: true, createdAt: now };
      accepted.push(item);
      history.push(item);
      if (accepted.length === 3) break;
    }
    s.generatedQuests = [...accepted, ...(s.generatedQuests || [])];
    s.questHistory = [
      ...(s.questHistory || []),
      ...accepted.map((q) => ({
        id: q.id,
        activityKey: q.activityKey,
        title: q.title,
        action: q.action,
      })),
    ];
    s.quests = [...s.generatedQuests, ...quests];
    return accepted;
  }
  function chatHistory(messages, text) {
    const history = messages.slice(-10);
    while (
      history.length &&
      history.reduce((n, m) => n + m.content.length, 0) + text.length > 12000
    )
      history.splice(0, 2);
    return [...history, { role: "user", content: text }];
  }
  function dailyMeans(entries) {
    const days = {};
    entries
      .filter(
        (e) =>
          e.confirmed && Number.isInteger(e.mood) && e.mood >= 1 && e.mood <= 5,
      )
      .forEach((e) => {
        const key = dayKey(e.at);
        (days[key] ??= []).push(e.mood);
      });
    return Object.entries(days).map(([day, v]) => ({
      day,
      value: v.reduce((a, b) => a + b, 0) / v.length,
    }));
  }
  // A timezone change must never turn an already synced moment into a new event.
  const completionEventId = (event) => `${event.id}:${event.at}`;
  const api = {
    completionEventId,
    validGenerated,
    canonical,
    duplicate,
    addGenerated,
    chatHistory,
    quests,
    defaults,
    normalize,
    suggestion,
    queue,
    dayKey,
    dailyMeans,
  };
  if (typeof module !== "undefined") module.exports = api;
  root.Wonder = api;
})(typeof window !== "undefined" ? window : globalThis);
