"use strict";
const $ = (s) => document.querySelector(s),
  esc = (s) =>
    String(s ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
const native = window.Native;
let state,
  tab = "today",
  mood = 0,
  energy = 0,
  stream = null,
  frame = 0,
  lensStyle = "petal",
  voiceTimer = null,
  voiceLeft = 120,
  lastQuest = null,
  cameraPending = false;
const icons = {
  home: "M3 10 12 3l9 7v10H3z M9 20v-7h6v7",
  spark: "m12 2 2.7 7.3L22 12l-7.3 2.7L12 22l-2.7-7.3L2 12l7.3-2.7z",
  sun: "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1.4 1.4 M17.6 17.6 19 19 M5 19l1.4-1.4 M17.6 6.4 19 5",
  heart:
    "M20.8 4.7a5.5 5.5 0 0 0-7.8 0L12 5.8l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.5a5.5 5.5 0 0 0 0-7.8Z",
  leaf: "M20 3C7 1 2 7 5 16c10 5 17-1 15-13Z M4 21 15 10",
  cloud: "M6 18a5 5 0 0 1-1-9 7 7 0 0 1 13-1 5 5 0 1 1 0 10Z",
  camera: "M3 7h4l2-3h6l2 3h4v13H3z M16 13a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  arrow: "M5 12h14 M13 6l6 6-6 6",
  close: "m6 6 12 12 M6 18 18 6",
  you: "M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-2a8 8 0 0 1 16 0v2",
  settings: "M4 7h16 M4 17h16 M8 4v6 M16 14v6",
  mic: "M9 4a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0z M5 10v1a7 7 0 0 0 14 0v-1 M12 18v4 M9 22h6",
  check: "m5 12 4 4L19 6",
};
const icon = (n) =>
  `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${icons[n] || icons.spark}"/></svg>`;
function art() {
  return `<svg viewBox="0 0 380 220" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><defs><linearGradient id="sky" x2="0" y2="1"><stop stop-color="#e9eddc"/><stop offset="1" stop-color="#f2ead6"/></linearGradient></defs><rect width="380" height="220" fill="url(#sky)"/><circle cx="280" cy="48" r="24" fill="#efcf8e"/><path d="M0 148Q80 99 154 148Q249 98 380 151V220H0" fill="#becaa6"/><path d="M0 175Q100 133 206 176T380 164V220H0" fill="#98b08b"/><path d="M212 220q-64-28-50-49t55-27" fill="none" stroke="#efe2c1" stroke-width="33"/><path d="M62 90V59l35-22 36 22v67H62" fill="#eedccb" stroke="#c0aa8b" stroke-width="2"/><path d="m51 60 45-30 48 31" fill="none" stroke="#a98071" stroke-width="8" stroke-linecap="round"/><path d="M91 87h18v39H91z" fill="#ae947d"/><rect x="74" y="64" width="14" height="17" rx="6" fill="#f9f2d9"/><rect x="109" y="64" width="14" height="17" rx="6" fill="#f9f2d9"/><path d="M303 115v68 M293 130l10 10 13-20" fill="none" stroke="#728964" stroke-width="6" stroke-linecap="round"/><path d="M303 68c-28 0-41 39-18 47-21 15-5 41 18 32 29 11 45-12 25-32 17-17-3-47-25-47" fill="#b2c197"/><path d="M256 56c-5-11-25-11-29 3-16-3-19 15-3 15h48c14-6 5-22-16-18" fill="#fffcf2"/><path d="M24 104c3-11 21-16 28-3 18-3 24 17 3 17H24c-13-2-12-13 0-14" fill="#fffcf2"/><ellipse cx="233" cy="176" rx="30" ry="7" fill="#7e976e" opacity=".4"/><path d="M211 152c-8-19 0-27 9-13 8-15 19-8 15 4 18 10 18 29 0 33-22 6-33-8-24-24" fill="#fcf6df" stroke="#cbbd9c" stroke-width="1.5"/><circle cx="220" cy="156" r="2" fill="#626c52"/><circle cx="234" cy="156" r="2" fill="#626c52"/><path d="m225 162 3 2 3-2" stroke="#626c52" fill="none" stroke-width="1.5"/><ellipse cx="216" cy="162" rx="4" ry="2.5" fill="#e8b8ac"/><ellipse cx="238" cy="162" rx="4" ry="2.5" fill="#e8b8ac"/><g fill="#eee5c5"><path d="m162 56 3 8 8 3-8 3-3 8-3-8-8-3 8-3z"/><path d="m347 130 2 6 6 2-6 2-2 6-2-6-6-2 6-2z"/></g><g fill="#e9bdb3"><circle cx="51" cy="166" r="4"/><circle cx="63" cy="186" r="3"/><circle cx="343" cy="195" r="4"/></g><g stroke="#617e55" stroke-width="2"><path d="m48 195 4-12 4 9 M142 184l-2-11-5 7 M328 186l-3-10 7 5"/></g></svg>`;
}
function read() {
  try {
    return Wonder.normalize(
      JSON.parse(
        native ? native.getState() : localStorage.getItem("tinywonder") || "{}",
      ),
    );
  } catch {
    return Wonder.defaults();
  }
}
function save() {
  state = Wonder.normalize(state);
  try {
    if (native && native.saveState(JSON.stringify(state)) === false)
      throw new Error("storage");
    if (!native) localStorage.setItem("tinywonder", JSON.stringify(state));
    return true;
  } catch {
    toast("Could not save: this device’s demo storage is full or unavailable.");
    return false;
  }
}
function toast(t) {
  $("#toast").textContent = t;
  $("#toast").classList.add("show");
  setTimeout(() => $("#toast").classList.remove("show"), 3000);
}
function todayItems(a) {
  return a.filter((x) => Wonder.dayKey(x.at) === Wonder.dayKey(Date.now()));
}
function nav() {
  const ns = [
    ["today", "home", "Today"],
    ["quests", "spark", "Side quests"],
    ["lens", "camera", "Wonder lens"],
    ["circle", "heart", "Circle"],
    ["you", "leaf", "Your garden"],
  ];
  $("#nav").innerHTML = ns
    .map(
      ([id, ico, label]) =>
        `<button data-tab="${id}" class="${tab === id ? "active" : ""}" ${tab === id ? 'aria-current="page"' : ""}><span class="navicon">${icon(ico)}</span>${label}</button>`,
    )
    .join("");
}
function go(t) {
  stopCamera();
  closeModal();
  tab = t;
  render();
  window.scrollTo(0, 0);
  if (t === "circle" && Circle.status().joined) Circle.sync();
}
function questCard(q) {
  return `<div class="card quest"><div class="questicon ${q.color}">${icon(q.icon)}</div><div class="grow"><h3>${esc(q.title)}</h3><div class="quest-meta"><span>${q.minutes} min</span><span>${esc(q.principle)}</span></div></div><button class="quest-open" data-quest="${q.id}" aria-label="Open ${esc(q.title)}">${icon("arrow")}</button></div>`;
}
function render() {
  nav();
  const main = $("#main");
  if (tab === "today") {
    const date = new Date().toLocaleDateString("en-US", {
      weekday: "long",
      month: "short",
      day: "numeric",
    });
    const checked = todayItems(state.entries).some((e) => e.confirmed),
      count = todayItems(state.completed).length;
    main.innerHTML = `<div class="eyebrow">${date}</div><h1>A little more wonder.<br>A little more you.</h1><p class="sub">Small moments. A softer kind of everyday.</p><div class="hero">${art()}<div class="hero-caption">A good day can start with something tiny.</div></div><div class="card check-card"><div class="grow"><div class="eyebrow">A moment for yourself · 2 min</div><h3>${checked ? "Thanks for checking in." : "How’s your inner weather?"}</h3><p class="small">${checked ? "You can feel more than one thing today." : "Sunny, cloudy, or a little of both. It all belongs."}</p></div><button class="round" data-action="checkin" aria-label="Start a two minute check-in">${icon("arrow")}</button></div><button class="card profile-invitation" data-action="personalize"><span class="questicon pink">${icon("spark")}</span><span class="grow"><strong>${state.questProfile ? "Little things, more you." : "Go on. Have a yap."}</strong><span class="small">${state.questProfile ? "Your profile · fresh side quests" : "Five minutes to make quests feel like you"}</span></span>${icon("arrow")}</button><div class="section-head"><h2>Your little side quests</h2><button class="text-button" data-tab="quests">See all →</button></div>${Wonder.queue(state).slice(0, 2).map(questCard).join("") || '<div class="empty">You made space for yourself today. Rest is welcome.</div>'}<p class="quiet-note">${count ? `${count} little ${count === 1 ? "moment" : "moments"} made today. ` : ""}<span class="footer-flower">✳</span> No streaks to protect. Just a little room to grow.</p>`;
  } else if (tab === "quests") {
    const latest = state.entries.filter((e) => e.confirmed).slice(-1)[0];
    main.innerHTML = `<div class="eyebrow">Real life, a little lighter</div><h1>A small invitation.</h1><p class="sub">Choose what fits. Skip what doesn’t.<br>You never have to earn a rest.</p><div class="notice">${latest ? "Picked from your last confirmed mood and energy." : "Start with a check-in to shape your suggestions."} <button class="text-button" data-action="checkin">Check in →</button></div><button class="secondary" data-action="personalize">${state.questProfile ? "Make new quests for me" : "Make quests feel like me"} ${icon("spark")}</button><div class="section-head"><h2>For your day</h2><span class="pill">${todayItems(state.completed).length} enjoyed today</span></div>${Wonder.queue(state).map(questCard).join("") || '<div class="empty">All done for today. A little pause is a good next step.</div>'}<button class="secondary" data-action="settings">Let quests come to me ${icon("arrow")}</button><p class="quiet-note">Inspired by positive psychology research.<br>These short adaptations haven’t been clinically tested.</p>`;
  } else if (tab === "lens") renderLens();
  else if (tab === "circle") Circle.render();
  else renderGarden();
  document
    .querySelectorAll("[data-height]")
    .forEach((el) => (el.style.height = el.dataset.height + "px"));
}
function modal(body) {
  $("#modal-root").innerHTML =
    `<div class="modal-backdrop"><section class="modal" role="dialog" aria-modal="true" aria-label="RomantiSide"><div class="modal-top"><span class="eyebrow">A little space for you</span><button class="round" data-action="close" aria-label="Close">${icon("close")}</button></div>${body}</section></div>`;
  document.body.style.overflow = "hidden";
  $(".modal button")?.focus();
}
function closeModal() {
  AI.close();
  stopVoice();
  $("#modal-root").innerHTML = "";
  document.body.style.overflow = "";
}
function checkin() {
  AI.close();
  mood = 0;
  energy = 0;
  modal(
    `<span class="pill">About 2 minutes · optional, always</span><h2>What’s your inner weather?</h2><button class="secondary top-gap" data-action="aichat">Talk it through with Claude ${icon("spark")}</button><p class="sub">You know your experience best. A few words, or a voice note, can help you pause.</p><div class="field"><label>How are you feeling right now?</label><div class="moods">${["Low", "Flat", "Okay", "Good", "Lovely"].map((v, i) => `<button class="mood" data-mood="${i + 1}" aria-pressed="false"><span class="face">${["◡̀", "◡", "◡", "◡̈", "✧"][i]}</span>${v}</button>`).join("")}</div></div><div class="field"><label>How much energy do you have?</label><div class="chips">${["A little", "Some", "Plenty"].map((v, i) => `<button class="chip" data-energy="${i + 1}" aria-pressed="false">${v}</button>`).join("")}</div></div><label for="reflection">What’s taking up space today?</label><textarea id="reflection" maxlength="2000" placeholder="I’m a bit drained after class, but the walk home was nice…"></textarea><div id="mood-suggestion"></div><div class="horizontal"><button class="text-button inline-icon" data-action="voice">${icon("mic")} Talk it through</button><span class="hint">Saved on this device</span></div><div id="voice-status" class="call-status" aria-live="polite"></div><div class="spacer"></div><button class="primary" data-action="savecheckin">That feels right. Save my check-in.</button><p class="small">Your ratings guide today’s quests. This private check-in uses simple word matching. Try Claude above for an optional conversation. It’s a wellbeing companion, not mental health care.</p>`,
  );
  $("#reflection").addEventListener("input", showSuggestion);
}
function showSuggestion() {
  const s = Wonder.suggestion($("#reflection")?.value || "");
  $("#mood-suggestion").innerHTML = s
    ? `<div class="suggestion">A word-based guess: <strong>${s}</strong>. Mixed feelings are normal; your chosen rating is what we’ll use.</div>`
    : "";
}
function saveCheckin() {
  if (!mood || !energy) {
    toast("Choose a mood and energy level first.");
    return;
  }
  state.entries.push({
    id: crypto.randomUUID(),
    at: Date.now(),
    mood,
    energy,
    text: $("#reflection").value.trim(),
    confirmed: true,
    source: "app",
  });
  if (!save()) {
    state = read();
    return;
  }
  closeModal();
  render();
  toast("A moment noticed. Your quests are ready.");
}
function voice() {
  if (!native) {
    toast("Voice check-in is available in the Android app.");
    return;
  }
  if (voiceTimer) {
    stopVoice();
    return;
  }
  const status = $("#voice-status");
  status.innerHTML = `<p>Android’s speech service transcribes your words and may send audio to its provider. RomantiSide saves only the text you confirm.</p><button class="secondary" id="voice-consent">Start voice reflection</button>`;
  $("#voice-consent").onclick = () => {
    native.startVoice();
    voiceLeft = 120;
    status.textContent = "Listening · 2:00 · tap Talk it through to stop";
    voiceTimer = setInterval(() => {
      voiceLeft--;
      if (voiceLeft <= 0) {
        stopVoice();
        return;
      }
      if ($("#voice-status"))
        $("#voice-status").textContent =
          `Listening · ${Math.floor(voiceLeft / 60)}:${String(voiceLeft % 60).padStart(2, "0")} · tap Talk it through to stop`;
    }, 1000);
  };
}
function stopVoice() {
  if (voiceTimer) {
    clearInterval(voiceTimer);
    voiceTimer = null;
    if (native) native.stopVoice();
    if ($("#voice-status"))
      $("#voice-status").textContent =
        "Voice stopped. You can edit your words.";
  }
}
function openQuest(id) {
  const q = state.quests.find((x) => x.id === id);
  if (!q) return;
  lastQuest = q;
  modal(
    `<span class="pill">${esc(q.principle)} · ${q.minutes} minutes</span><div class="quest-detail-icon ${q.color}">${icon(q.icon)}</div><h2>${esc(q.title)}</h2><p class="action-copy">${esc(q.action)}</p><div class="why"><strong>Why this little thing?</strong>${esc(q.why)}<br><a href="${q.evidence}" target="_blank" rel="noopener">Explore the research ↗</a></div><button class="primary" data-action="done">I made a little moment ${icon("check")}</button><div class="horizontal"><button class="text-button" data-action="skip">Not for me today</button><button class="text-button" data-action="swap">Try another</button></div>`,
  );
}
function finishQuest() {
  if (!lastQuest) return;
  const id = lastQuest.id;
  if (
    id.startsWith("generated-") &&
    [...state.completed, ...state.skipped].some((q) => q.id === id)
  ) {
    closeModal();
    render();
    toast("This invitation has already been completed or skipped.");
    return;
  }
  if (!todayItems(state.completed).some((q) => q.id === id))
    state.completed.push({ id, at: Date.now(), source: "app" });
  if (!save()) {
    state = read();
    return;
  }
  render();
  modal(
    `<div class="quest-detail-icon sage">${icon("leaf")}</div><h2>A small thing.<br>Still a real thing.</h2><p class="sub">How did that moment leave you feeling?</p><div class="chips">${["A little worse", "About the same", "A little better"].map((v, i) => `<button class="chip" data-feedback="${i - 1}">${v}</button>`).join("")}</div><p class="small">Any answer is useful. We don’t assume every activity helps.</p><button class="text-button" data-action="close">Skip this question</button>`,
  );
}
function renderGarden() {
  const days = Wonder.dailyMeans(state.entries),
    recent = days.slice(-7),
    unique = new Set(state.completed.map((x) => Wonder.dayKey(x.at))).size;
  $("#main").innerHTML =
    `<div class="eyebrow">Your garden</div><h1>Notice what grows.</h1><p class="sub">A record of your moments.<br>No scores to chase. No perfect days required.</p><div class="stats"><div class="stat"><div class="stat-value">${state.completed.length}</div><div class="small">little moments made</div></div><div class="stat"><div class="stat-value">${unique}</div><div class="small">days with a moment</div></div></div><div class="section-head"><h2>Your inner weather</h2><span class="pill">Self-reported · 1–5</span></div>${recent.length ? `<div class="card"><div class="chart" role="img" aria-label="Daily average mood: ${recent.map((d) => d.day + ": " + d.value.toFixed(1)).join(", ")}">${recent.map((d) => `<div class="chart-day"><span>${d.value.toFixed(1)}</span><div class="bar" data-height="${d.value * 15}"></div><span>${d.day.split("-").slice(1).join("/")}</span></div>`).join("")}</div><p class="small">Last ${recent.length} recorded days. More check-ins describe your experience; they don’t prove the app caused a change.</p></div>` : `<div class="empty">${icon("cloud")}<h3>Every garden starts somewhere.</h3><p class="small">Your confirmed check-ins will appear here.<br>There’s no sample data mixed with your own.</p><button class="text-button" data-action="checkin">Make your first check-in →</button></div>`}<div class="section-head"><h2>Recent reflections</h2></div>${
      state.entries.length
        ? state.entries
            .slice(-4)
            .reverse()
            .map(
              (e) =>
                `<div class="entry"><span class="small">${new Date(e.at).toLocaleDateString()} · ${e.confirmed ? ["Low", "Flat", "Okay", "Good", "Lovely"][e.mood - 1] : "Notification reply · not rated"}</span><p>${esc(e.text || "A quiet check-in. No words needed.")}</p>${!e.confirmed ? `<button class="text-button" data-confirm="${esc(e.id)}">Add my mood and energy →</button>` : ""}</div>`,
            )
            .join("")
        : '<p class="small">A few words are enough. Nothing here yet.</p>'
    }<div class="section-head"><h2>Things worth noticing</h2><button class="text-button" data-tab="lens">Open lens →</button></div>${
      state.memories.length
        ? `<div class="memories">${state.memories
            .slice(-4)
            .reverse()
            .map(
              (m) =>
                `<div class="memory"><img src="${m.image}" alt="A moment captured with Wonder Lens"><p>${new Date(m.at).toLocaleDateString()} · ${esc(m.style)}</p></div>`,
            )
            .join("")}</div>`
        : '<p class="small">Save a little lovely with your Wonder Lens.</p>'
    }<div class="rule"></div><p class="small">Mood ratings and activity counts are personal observations. A research study is needed to test whether RomantiSide improves wellbeing.</p><button class="text-button" data-action="settings">Reminders & privacy →</button>`;
}
function settings() {
  stopVoice();
  AI.close();
  let n = { permission: false, enabled: false };
  try {
    if (native) n = JSON.parse(native.notificationStatus());
  } catch {}
  modal(
    `<h2>At your own pace.</h2><p class="sub">Let a small invitation find you. Your schedule, your choice.</p><div class="switch-row"><label for="reminders">Daily check-in + one side quest<br><span class="small">Reply, complete, snooze, or skip from a notification.</span></label><input id="reminders" type="checkbox" ${state.preferences.reminders ? "checked" : ""}></div><div class="horizontal field"><div><label for="checktime">Check-in around</label><select id="checktime">${hours(state.preferences.checkInHour)}</select></div><div><label for="questtime">Side quest around</label><select id="questtime">${hours(state.preferences.questHour)}</select></div></div><p class="small">Quiet hours: 9 pm–8 am. Android may delay reminders to save battery. No SMS messages or phone calls are sent.</p><div class="notice">${native ? (n.permission ? "Android notifications are allowed." : "Android notification permission is needed.") : "Notifications require the installed Android app."}</div><button class="primary" data-action="savereminders">Save my rhythm</button><button class="secondary top-gap" data-action="testnotification">Try a side quest notification</button><button class="text-button" data-action="testcheckin">Try a check-in notification</button><div class="rule"></div><h3>Your little private space</h3><p class="small">Saved reflections, profiles, and lens snapshots stay in this app’s private storage. Optional Claude conversations and profile creation send the text you choose through our server to Anthropic. There are no analytics or advertising SDKs. Your Android speech provider may process voice audio remotely; voice is optional. Camera frames are processed on this device.</p><p class="small">This prototype is for adult everyday wellbeing. It does not diagnose conditions, offer therapy, or monitor emergencies.</p><button class="secondary" data-action="personalize">My quest profile & instructions</button><button class="secondary danger top-gap" data-action="delete">Delete my local data</button>`,
  );
}
function hours(selected) {
  return Array.from({ length: 13 }, (_, i) => i + 8)
    .map(
      (h) =>
        `<option value="${h}" ${h === selected ? "selected" : ""}>${h > 12 ? h - 12 : h}:00 ${h >= 12 ? "pm" : "am"}</option>`,
    )
    .join("");
}
function saveReminders() {
  state.preferences = {
    ...state.preferences,
    reminders: $("#reminders").checked,
    checkInHour: +$("#checktime").value,
    questHour: +$("#questtime").value,
  };
  save();
  if (native) {
    native.configureReminders(
      state.preferences.reminders,
      state.preferences.checkInHour,
      state.preferences.questHour,
    );
    if (state.preferences.reminders) native.requestNotifications();
  } else toast("Saved. Install Android to enable reminders.");
  closeModal();
  render();
  toast(
    state.preferences.reminders
      ? "Rhythm saved. Android permission is required."
      : "Reminders paused.",
  );
}
function renderLens() {
  $("#main").innerHTML =
    `<div class="eyebrow">The ordinary, a little magical</div><h1>Find a little lovely.</h1><p class="sub">A soft lens for your actual world.<br>Notice a detail. Stay with it for a moment.</p><div class="lens-stage"><span class="lens-label" id="lens-label">Camera is off</span><div class="lens-placeholder" id="lens-placeholder">${art()}<p class="small">Your morning mug. A patch of sunlight.<br>Something small, worth noticing.</p></div><video id="camera-video" playsinline muted></video><canvas id="lens-canvas" hidden></canvas><div class="lens-caption" id="lens-caption" hidden>there’s a little lovely here.</div></div><div class="chips"><button class="chip ${lensStyle === "petal" ? "selected" : ""}" data-style="petal">Petal glow</button><button class="chip ${lensStyle === "matcha" ? "selected" : ""}" data-style="matcha">Matcha morning</button><button class="chip ${lensStyle === "dream" ? "selected" : ""}" data-style="dream">Daydream</button></div><div class="spacer"></div><button class="primary" id="camera-button" data-action="camera">${icon("camera")} Open my Wonder Lens</button><button class="text-button" data-action="illustration">Try the illustrated preview</button><p class="quiet-note">Live color effects & playful stickers, processed on your device.<br>No face analysis. No images uploaded.</p>`;
}
async function startCamera() {
  if (cameraPending || stream) return;
  cameraPending = true;
  try {
    if (!navigator.mediaDevices?.getUserMedia)
      throw Error("Camera requires the installed Android app.");
    stream = await navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: { ideal: "environment" },
        width: { ideal: 960 },
        height: { ideal: 1280 },
      },
      audio: false,
    });
    if (tab !== "lens") {
      stopCamera();
      return;
    }
    const video = $("#camera-video");
    video.srcObject = stream;
    await video.play();
    $("#lens-placeholder").hidden = true;
    $("#lens-placeholder").style.display = "none";
    $("#lens-canvas").hidden = false;
    $("#lens-caption").hidden = false;
    $("#lens-label").textContent = "Live · on-device effect";
    $("#camera-button").innerHTML = "Keep this little moment";
    $("#camera-button").dataset.action = "capture";
    drawFrame();
  } catch (e) {
    stopCamera();
    toast(
      "Camera unavailable or permission denied. Try the illustrated preview.",
    );
  } finally {
    cameraPending = false;
  }
}
function drawOverlay(ctx, w, h) {
  const palettes = {
      petal: ["#ffe0db", "#fff3cb"],
      matcha: ["#deefd0", "#fff4d7"],
      dream: ["#e8dcff", "#fde1eb"],
    },
    p = palettes[lensStyle];
  ctx.fillStyle = p[0];
  ctx.globalAlpha = 0.16;
  ctx.fillRect(0, 0, w, h);
  ctx.globalAlpha = 1;
  for (let i = 0; i < 9; i++) {
    const x = (i * 137 + 40) % w,
      y = (i * 113 + 37) % h;
    ctx.save();
    ctx.translate(x, y);
    ctx.fillStyle = p[1];
    ctx.beginPath();
    ctx.moveTo(0, -9);
    ctx.lineTo(3, -3);
    ctx.lineTo(9, 0);
    ctx.lineTo(3, 3);
    ctx.lineTo(0, 9);
    ctx.lineTo(-3, 3);
    ctx.lineTo(-9, 0);
    ctx.lineTo(-3, -3);
    ctx.closePath();
    ctx.fill();
    ctx.restore();
  }
  ctx.fillStyle = "#fff9eee8";
  ctx.beginPath();
  ctx.ellipse(w * 0.79, h * 0.2, 40, 22, 0, 0, 7);
  ctx.fill();
  ctx.beginPath();
  ctx.arc(w * 0.75, h * 0.175, 18, 0, 7);
  ctx.arc(w * 0.81, h * 0.17, 20, 0, 7);
  ctx.fill();
  ctx.fillStyle = "#58684b";
  ctx.beginPath();
  ctx.arc(w * 0.77, h * 0.2, 2, 0, 7);
  ctx.arc(w * 0.82, h * 0.2, 2, 0, 7);
  ctx.fill();
  ctx.strokeStyle = "#58684b";
  ctx.lineWidth = 1.4;
  ctx.beginPath();
  ctx.arc(w * 0.795, h * 0.205, 4, 0, Math.PI);
  ctx.stroke();
  ctx.fillStyle = "#eeb6b4";
  ctx.beginPath();
  ctx.ellipse(w * 0.75, h * 0.21, 5, 2, 0, 0, 7);
  ctx.ellipse(w * 0.84, h * 0.21, 5, 2, 0, 0, 7);
  ctx.fill();
}
function drawFrame() {
  if (!stream || tab !== "lens") return;
  const c = $("#lens-canvas"),
    v = $("#camera-video");
  if (!c || !v) return;
  c.width = 600;
  c.height = 760;
  const ctx = c.getContext("2d");
  const vw = v.videoWidth || 600,
    vh = v.videoHeight || 760,
    scale = Math.max(600 / vw, 760 / vh);
  ctx.filter =
    lensStyle === "matcha"
      ? "saturate(0.7) contrast(0.93) brightness(1.12)"
      : lensStyle === "dream"
        ? "saturate(0.72) contrast(0.87) brightness(1.16)"
        : "saturate(0.8) contrast(0.91) brightness(1.12)";
  ctx.drawImage(
    v,
    (600 - vw * scale) / 2,
    (760 - vh * scale) / 2,
    vw * scale,
    vh * scale,
  );
  ctx.filter = "none";
  drawOverlay(ctx, 600, 760);
  frame = requestAnimationFrame(drawFrame);
}
function stopCamera() {
  cancelAnimationFrame(frame);
  if (stream) stream.getTracks().forEach((t) => t.stop());
  stream = null;
}
function illustrated() {
  stopCamera();
  $("#lens-placeholder").style.display = "grid";
  $("#lens-placeholder").hidden = false;
  $("#lens-canvas").hidden = true;
  $("#lens-label").textContent = "Illustration preview · not your camera";
  $("#lens-placeholder").style.filter =
    lensStyle === "dream" ? "hue-rotate(30deg)" : "none";
  $("#camera-button").textContent = "Open my real camera";
  $("#camera-button").dataset.action = "camera";
  toast("Illustrated preview. Live effects use your camera.");
}
function capture() {
  if (!stream) {
    toast("Open your camera first.");
    return;
  }
  const c = $("#lens-canvas");
  state.memories.push({
    id: crypto.randomUUID(),
    at: Date.now(),
    style: lensStyle,
    image: c.toDataURL("image/jpeg", 0.72),
  });
  state.memories = state.memories.slice(-5);
  save();
  toast("Saved to your garden. Only on this device.");
}
function onboarding() {
  modal(
    `<div class="onboard-art">${art()}</div><span class="pill">Made for ordinary, imperfect days</span><h2>Your life has little<br>lovely things in it.</h2><p class="sub">Let’s make room for them. Check in with yourself, try a tiny side quest, and notice what feels good.</p><div class="notice">For students and early-career adults, 18+. A wellbeing prototype inspired by positive psychology. No diagnosis. No pressure to feel positive.</div><button class="primary" data-action="begin">Let’s find a little wonder ${icon("arrow")}</button><p class="quiet-note">Private by default. Reminders and camera are your choice.</p>`,
  );
}
const actions = {
  aichat: () => {
    stopVoice();
    AI.beginChat();
  },
  personalize: () => {
    stopVoice();
    AI.openProfile();
  },
  close: closeModal,
  checkin,
  savecheckin: saveCheckin,
  voice,
  settings,
  savereminders: saveReminders,
  done: finishQuest,
  skip: () => {
    if (
      lastQuest &&
      !(
        lastQuest.id.startsWith("generated-") &&
        [...state.completed, ...state.skipped].some(
          (q) => q.id === lastQuest.id,
        )
      )
    ) {
      state.skipped.push({ id: lastQuest.id, at: Date.now(), source: "app" });
      if (!save()) {
        state = read();
        return;
      }
    }
    closeModal();
    render();
    toast("Skipped. Choosing what fits is part of it.");
  },
  swap: () => {
    const current = lastQuest?.id,
      list = Wonder.queue(state);
    const next =
      list[(list.findIndex((q) => q.id === current) + 1) % list.length];
    if (next) openQuest(next.id);
  },
  camera: startCamera,
  capture,
  illustration: illustrated,
  begin: () => {
    state.onboarded = true;
    save();
    closeModal();
  },
  testcheckin: () => {
    if (native) native.sendTestCheckInNotification();
    else toast("Install Android to try notifications.");
  },
  testnotification: () => {
    if (!native) {
      toast("Install Android to try notifications.");
      return;
    }
    native.sendTestNotification();
    toast(
      "If reminders are enabled and allowed, check your notification shade.",
    );
  },
  delete: () => {
    modal(
      `<h2>Start fresh?</h2><p class="sub">This deletes your reflections, quest profile, duplicate history, completed quests, photos, and reminder settings from this device. This can’t be undone.</p><p class="small">If you joined Circle, leave it from the Circle tab to delete your shared profile and counts too. Local deletion does not delete your Circle account.</p><button class="primary danger" data-action="confirmdelete">Delete my local data</button><button class="text-button" data-action="settings">Keep my data</button>`,
    );
  },
  confirmdelete: () => {
    stopCamera();
    if (native) native.deleteData();
    else localStorage.removeItem("tinywonder");
    state = Wonder.defaults();
    closeModal();
    tab = "today";
    render();
    toast("Your local data and reminders have been cleared.");
  },
};
document.addEventListener("click", (e) => {
  const b = e.target.closest("button,a");
  if (!b) return;
  if (b.dataset.tab) go(b.dataset.tab);
  else if (b.dataset.action) actions[b.dataset.action]?.();
  else if (b.dataset.quest) openQuest(b.dataset.quest);
  else if (b.dataset.mood) {
    mood = +b.dataset.mood;
    document.querySelectorAll("[data-mood]").forEach((x) => {
      x.classList.toggle("selected", x === b);
      x.setAttribute("aria-pressed", x === b ? "true" : "false");
    });
  } else if (b.dataset.energy) {
    energy = +b.dataset.energy;
    document.querySelectorAll("[data-energy]").forEach((x) => {
      x.classList.toggle("selected", x === b);
      x.setAttribute("aria-pressed", x === b ? "true" : "false");
    });
  } else if (b.dataset.feedback) {
    state.feedback.push({
      id: lastQuest?.id,
      at: Date.now(),
      value: +b.dataset.feedback,
    });
    save();
    closeModal();
    toast("Thanks. Your experience is what matters.");
  } else if (b.dataset.style) {
    lensStyle = b.dataset.style;
    document
      .querySelectorAll("[data-style]")
      .forEach((x) => x.classList.toggle("selected", x === b));
    if (!stream && $("#lens-label").textContent.startsWith("Illustration"))
      illustrated();
  } else if (b.dataset.confirm) {
    const existing = state.entries.find((x) => x.id === b.dataset.confirm);
    checkin();
    $("#reflection").value = existing?.text || "";
    showSuggestion();
    $("#mood-suggestion").innerHTML =
      '<div class="suggestion">Your earlier reply is below. These mood and energy ratings will be recorded for right now.</div>';
    const saveButton = $('[data-action="savecheckin"]');
    saveButton.removeAttribute("data-action");
    saveButton.onclick = () => {
      if (!mood || !energy) {
        toast("Choose mood and energy first.");
        return;
      }
      const current = state.entries.find((x) => x.id === existing.id);
      if (!current) {
        toast("This reflection is no longer available.");
        return;
      }
      current.receivedAt = current.receivedAt || current.at;
      current.at = Date.now();
      current.mood = mood;
      current.energy = energy;
      current.confirmed = true;
      current.text = $("#reflection").value.trim();
      save();
      closeModal();
      render();
    };
  }
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") closeModal();
  if (e.key === "Tab" && $(".modal")) {
    const focusable = [
      ...$(".modal").querySelectorAll("button,input,textarea,select,a[href]"),
    ].filter((x) => !x.disabled);
    const first = focusable[0],
      last = focusable.at(-1);
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  }
});
window.onNativeEvent = (event) => {
  if (AI.event(event) || Circle.event(event)) return;
  if (event.type === "voice") {
    if (!voiceTimer) return;
    stopVoice();
    if ($("#reflection")) {
      $("#reflection").value = ($("#reflection").value + " " + event.text)
        .trim()
        .slice(0, 2000);
      showSuggestion();
      $("#voice-status").textContent =
        "Words captured. Edit them and choose your ratings.";
    }
  } else if (event.type === "voiceError") {
    stopVoice();
    if ($("#voice-status"))
      $("#voice-status").textContent =
        event.message ||
        event.text ||
        "Voice unavailable. You can type your reflection.";
  } else if (event.type === "refresh") {
    state = read();
    if (!$(".modal")) {
      render();
      if (tab === "circle" && Circle.status().joined) Circle.sync();
    }
  } else if (event.type === "notificationPermission") {
    toast("Notification settings updated.");
  } else if (
    event.type === "notificationError" ||
    event.type === "notificationTest"
  ) {
    toast(event.text || "Notification settings updated.");
  }
};
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    AI.stopVoice();
    stopCamera();
    stopVoice();
  } else if (tab === "lens") renderLens();
});
$("#settings-button").innerHTML = icon("settings");
$("#settings-button").onclick = settings;
$("#brand").onclick = () => go("today");
state = read();
save();
render();
if (!state.onboarded) onboarding();
