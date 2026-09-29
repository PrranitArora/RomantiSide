// Mirrored from server/personalization.mjs; parity is covered by tests.
(function (root) {
  function canonicalText(value) {
    return String(value)
      .normalize("NFKC")
      .toLowerCase()
      .replace(/[’']/gu, "")
      .replace(/[^\p{L}\p{N}]+/gu, " ")
      .trim()
      .replace(/\s+/gu, " ");
  }

  const SYNONYMS = Object.freeze({
    stroll: "walk",
    strolling: "walk",
    strolled: "walk",
    walking: "walk",
    walked: "walk",
    amble: "walk",
    saunter: "walk",
    wander: "walk",
    wandering: "walk",
    outdoors: "outside",
    outdoor: "outside",
    indoors: "inside",
    indoor: "inside",
    notice: "notice",
    noticing: "notice",
    observe: "notice",
    observing: "notice",
    spot: "notice",
    look: "notice",
    looking: "notice",
    colours: "color",
    colour: "color",
    colors: "color",
    sketch: "draw",
    sketching: "draw",
    doodle: "draw",
    doodling: "draw",
    drawing: "draw",
    photograph: "photo",
    photographing: "photo",
    picture: "photo",
    pictures: "photo",
    photos: "photo",
    write: "write",
    writing: "write",
    jot: "write",
    jotting: "write",
    journal: "write",
    journaling: "write",
    text: "send",
    message: "send",
    messaging: "send",
    sending: "send",
    gratitude: "thanks",
    grateful: "thanks",
    thankful: "thanks",
    appreciate: "thanks",
    appreciation: "thanks",
    thank: "thanks",
    hello: "hello",
    greet: "hello",
    greeting: "hello",
    hi: "hello",
    tomorrow: "future",
    nextday: "future",
    future: "future",
    kindness: "kind",
    kindly: "kind",
    compassionate: "kind",
    compassion: "kind",
    flowers: "flower",
    leaves: "leaf",
    trees: "tree",
    friends: "friend",
    trusted: "trust",
    breathe: "breathe",
    breathing: "breathe",
    breathingexercise: "breathe",
    listen: "listen",
    listening: "listen",
    song: "music",
    songs: "music",
    reading: "read",
    tidying: "tidy",
    organize: "tidy",
    organising: "tidy",
    organizing: "tidy",
    planning: "plan",
    choose: "choose",
    choosing: "choose",
  });
  const STOP = new Set(
    "a an the this that these those your you yourself i me my we our it its and or to of for from in on at by with as is are be being have has take taking go going spend try get give one two three four five 1 2 3 4 5 minute minutes second seconds short brief little small tiny gentle gently quick slowly slow just then before after can could may if safe accessible nearby around some something today now about into towards toward down up out".split(
      " ",
    ),
  );
  const words = (value) =>
    canonicalText(value)
      .split(" ")
      .map((word) => SYNONYMS[word] || word)
      .filter((word) => word && !STOP.has(word) && !/^\d+$/u.test(word));
  const verbs = new Set([
    "walk",
    "notice",
    "draw",
    "photo",
    "write",
    "send",
    "breathe",
    "listen",
    "read",
    "tidy",
    "plan",
    "choose",
  ]);

  function activitySignature(action) {
    const tokens = words(action);
    const verb = tokens.find((word) => verbs.has(word));
    if (!verb) return null;
    // Movement phrasing and duration do not turn a walk into a new activity.
    if (verb === "walk" || verb === "breathe") return verb;
    const objects = tokens
      .slice(tokens.indexOf(verb) + 1)
      .filter((word) => !verbs.has(word));
    const intent = objects.find((word) =>
      ["thanks", "hello", "future", "kind", "strength"].includes(word),
    );
    if (intent) return `${verb}:${intent}`;
    return null;
  }

  function isDuplicate(left, right) {
    if (
      canonicalText(left.action) === canonicalText(right.action) ||
      canonicalText(left.title) === canonicalText(right.title) ||
      (left.activityKey &&
        right.activityKey &&
        left.activityKey === right.activityKey)
    )
      return true;
    const leftSignature = activitySignature(left.action);
    const rightSignature = activitySignature(right.action);
    if (leftSignature && leftSignature === rightSignature) return true;
    const leftWords = words(left.action),
      rightWords = words(right.action);
    const leftVerb = leftWords.find((word) => verbs.has(word));
    const rightVerb = rightWords.find((word) => verbs.has(word));
    if (!leftVerb || leftVerb !== rightVerb) return false;
    const a = new Set(leftWords),
      b = new Set(rightWords);
    if (a.size < 4 || b.size < 4) return false;
    const shared = [...a].filter((word) => b.has(word)).length;
    return shared / (a.size + b.size - shared) >= 0.78;
  }

  const api = { canonicalText, isDuplicate };
  if (typeof module !== "undefined") module.exports = api;
  root.QuestDedup = api;
})(typeof window !== "undefined" ? window : globalThis);
