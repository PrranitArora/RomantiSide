/* Optional cloud experiences. Drafts and full chats live in memory only. */
const AI = (() => {
  let mode = "",
    pending = null,
    messages = [],
    response = null,
    draft = "",
    profileResult = null;
  let startedAt = 0,
    timer = null,
    listening = false,
    voiceTarget = "",
    voiceAllowed = false;
  const privacy = `<p class="small">When you send, this text goes through the RomantiSide server to Anthropic (Claude). Your approved profile is included when relevant. Full chats and raw yap notes aren’t saved by RomantiSide; provider retention policies still apply. Saved check-ins and profiles stay on this device. This is everyday wellbeing support, not therapy or emergency monitoring.</p>`;
  function available(method) {
    if (!native || typeof native[method] !== "function") {
      toast(
        "Install the Android demo with a configured Claude server to use this.",
      );
      return false;
    }
    return true;
  }
  function close() {
    if (pending) native?.cancelChat();
    pending = null;
    stopVoice();
    clearInterval(timer);
    timer = null;
    mode = "";
    messages = [];
    response = null;
    draft = "";
    profileResult = null;
    startedAt = 0;
  }
  function stopVoice() {
    if (listening) native?.stopVoice();
    listening = false;
  }
  function beginChat() {
    close();
    mode = "consent";
    modal(
      `<span class="pill">Two minutes · Claude</span><h2>Talk it through.</h2><p class="sub">A short conversation, with room for mixed feelings. You decide what to keep.</p>${privacy}<button class="primary" data-ai="chat-consent">Continue with Claude</button><button class="text-button" data-action="checkin">Use a private check-in instead</button>`,
    );
  }
  function chatView() {
    mode = "chat";
    modal(
      `<span class="pill">Claude · wellbeing reflection</span><h2>A little room to talk.</h2><div class="chat-log" aria-live="polite"><div class="chat-bubble assistant">What’s been taking up space today? A few words are enough.</div>${messages.map((m) => `<div class="chat-bubble ${m.role}"><span class="chat-speaker">${m.role === "user" ? "You" : "Claude"}</span>${esc(m.content)}</div>`).join("")}</div>${response ? `<div class="notice">${esc(response.summary)}${response.urgentSupport ? "<p>If you may act on thoughts of harm, contact local emergency services or a trusted person now. In the U.S., call or text 988.</p>" : '<button class="text-button" data-ai="review-chat">Review my check-in →</button>'}</div>` : ""}<label for="chat-draft">Your words</label><textarea id="chat-draft" maxlength="2000" placeholder="I feel scattered after class…" ${pending ? "disabled" : ""}>${esc(draft)}</textarea><div id="ai-status" class="small" role="status">${pending ? "Claude is thinking…" : "Only your confirmed ratings shape your mood history."}</div><div class="horizontal"><button class="text-button" data-ai="voice-chat" ${pending ? "disabled" : ""}>${icon("mic")} Voice to text</button><button class="primary compact" data-ai="send-chat" ${pending ? "disabled" : ""}>${pending ? "Thinking…" : "Send"}</button></div><p class="small">Closing discards this conversation. Save only a summary you agree with.</p>`,
    );
    $("#chat-draft").addEventListener("input", (e) => {
      draft = e.target.value;
    });
    const log = $(".chat-log");
    log.scrollTop = log.scrollHeight;
  }
  function request(kind, payload) {
    const method = {
      chat: "chatRequest",
      profile: "profileRequest",
      quests: "questsRequest",
    }[kind];
    if (!available(method) || pending) return false;
    const id = crypto.randomUUID();
    pending = { id, kind };
    try {
      native[method](id, JSON.stringify(payload));
    } catch {
      pending = null;
      toast("Could not start this request. Try again.");
      return false;
    }
    return true;
  }
  function sendChat() {
    const text = $("#chat-draft")?.value.trim();
    if (!text || pending) return;
    stopVoice();
    // A failed turn is removed before retry; never send two consecutive user turns.
    const history = Wonder.chatHistory(messages, text);
    if (
      request("chat", {
        messages: history,
        mood: null,
        energy: null,
        ...(state.questProfile ? { profile: state.questProfile.profile } : {}),
      })
    ) {
      messages = history;
      draft = "";
      chatView();
    }
  }
  function reviewChat() {
    const result = response;
    close();
    checkin();
    $("#reflection").value = result.summary || "";
    $("#mood-suggestion").innerHTML =
      '<div class="suggestion">Claude suggested this summary and these ratings. Edit anything that doesn’t fit. Nothing is recorded until you save.</div>';
    if (result.suggestedMood)
      $(`[data-mood="${result.suggestedMood}"]`)?.click();
    if (result.suggestedEnergy)
      $(`[data-energy="${result.suggestedEnergy}"]`)?.click();
  }
  function openProfile() {
    close();
    mode = "profile-home";
    if (!state.questProfile) return yapView();
    const p = state.questProfile;
    modal(
      `<span class="pill">Your quest compass</span><h2>Little things, more you.</h2>${profileMarkup(p.profile)}<details><summary>See my quest instructions</summary><pre class="prompt-preview">${esc(p.systemPrompt)}</pre></details><p class="small">New quests use this approved profile and your recent confirmed mood and energy. Past suggestions are checked for repeats.</p><button class="primary" data-ai="generate">Make my next quests</button><button class="secondary top-gap" data-ai="new-yap">Update with a five-minute yap</button><button class="text-button" data-ai="forget-profile">Forget this profile</button><div id="ai-status" role="status" class="small"></div>`,
    );
  }
  function profileMarkup(p) {
    return `<p class="sub">${esc(p.summary)}</p><div class="profile-section"><h3>Things that feel like you</h3><div class="chips">${p.preferences.map((x) => `<span class="pill">${esc(x)}</span>`).join("") || '<span class="small">Nothing specified yet.</span>'}</div><h3>Boundaries to respect</h3><p>${esc(p.avoid.join(" · ") || "No boundaries specified yet.")}</p><p class="small">${esc([p.moodContext, p.energyStyle].filter(Boolean).join(" · "))}</p></div>`;
  }
  function yapView() {
    mode = "yap";
    modal(
      `<span class="pill">Five minutes, entirely yours</span><h2>Go on. Have a yap.</h2><p class="sub">Your favorite little things. Your usual mood. What drains you, what feels good, what you’d rather skip.</p><div class="yap-timer" id="yap-clock">5:00</div><p class="small center">A gentle guide. Finish early or keep writing.</p><label for="yap-draft">What should your side quests know about you?</label><textarea class="yap-draft" id="yap-draft" maxlength="12000" placeholder="I love sketching and cozy cafés. Afternoons feel flat. I’d rather do quiet things than message people. I have limited mobility, so please keep activities seated…">${esc(draft)}</textarea><div class="horizontal"><button class="text-button" data-ai="voice-yap">${icon("mic")} Talk out loud</button><span id="yap-count" class="hint">${draft.length} / 12,000</span></div><div id="ai-status" class="small" role="status"></div>${privacy}<button class="primary" data-ai="extract">Send & build my quest profile</button><p class="small">Review before saving. Raw notes disappear when you close this session.</p>`,
    );
    $("#yap-draft").addEventListener("input", (e) => {
      draft = e.target.value;
      startTimer();
      $("#yap-count").textContent = `${draft.length} / 12,000`;
    });
    if (startedAt) startTimer();
  }
  function startTimer() {
    if (!startedAt) startedAt = Date.now();
    if (timer) return;
    const tick = () => {
      const left = Math.max(
        0,
        300 - Math.floor((Date.now() - startedAt) / 1000),
      );
      if ($("#yap-clock"))
        $("#yap-clock").textContent =
          `${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
      if (!left) {
        stopVoice();
        clearInterval(timer);
        timer = null;
        if ($("#ai-status"))
          $("#ai-status").textContent =
            "Five minutes made for you. You can keep typing or build your profile.";
      }
    };
    tick();
    timer = setInterval(tick, 1000);
  }
  function extract() {
    draft = $("#yap-draft")?.value.trim() || "";
    if (!draft) return toast("Tell me a little about you first.");
    stopVoice();
    clearInterval(timer);
    timer = null;
    if (request("profile", { text: draft }))
      busy("Turning your words into a profile to review…");
  }
  function busy(text) {
    document
      .querySelectorAll(".modal [data-ai]")
      .forEach((b) => (b.disabled = true));
    document
      .querySelectorAll(".modal textarea")
      .forEach((x) => (x.disabled = true));
    if ($("#ai-status")) $("#ai-status").textContent = text;
  }
  function profileReview() {
    mode = "profile-review";
    modal(
      `<span class="pill">A draft, for your approval</span><h2>Does this sound like you?</h2>${profileMarkup(profileResult.profile)}<details><summary>See the instructions for new quests</summary><pre class="prompt-preview">${esc(profileResult.systemPrompt)}</pre></details><p class="small">This profile becomes context for Claude’s quest instructions. Fixed wellbeing boundaries stay in place. We don’t infer a diagnosis.</p><button class="primary" data-ai="approve">Save profile & make my next quests</button><button class="text-button" data-ai="edit-yap">Edit what I shared</button><div id="ai-status" role="status" class="small"></div>`,
    );
  }
  function generate() {
    if (!state.questProfile) return;
    if ((state.generatedQuests || []).length >= 24) {
      document
        .querySelectorAll(".modal [data-ai]")
        .forEach((b) => (b.disabled = false));
      if ($("#ai-status"))
        $("#ai-status").textContent =
          "You already have plenty of invitations. Complete or skip some of your current personalized quests before making more.";
      return;
    }
    if (JSON.stringify(state).length > 1500000) {
      if ($("#ai-status"))
        $("#ai-status").textContent =
          "This device’s demo storage is nearly full. Your existing quests are still available; clear local data in Settings before creating more.";
      return;
    }
    const latest = state.entries
      .filter((e) => e.confirmed && Date.now() - e.at < 86400000)
      .slice(-1)[0];
    const history = [
      ...Wonder.quests,
      ...(state.questHistory || []).slice(-40).reverse(),
    ].map((q) => ({
      id: q.id,
      title: q.title,
      action: q.action,
      ...(q.activityKey ? { activityKey: q.activityKey } : {}),
    }));
    if (
      request("quests", {
        profile: state.questProfile.profile,
        history,
        mood: latest?.mood || null,
        energy: latest?.energy || null,
      })
    )
      busy("Making new invitations and checking for repeats…");
  }
  function voice(target) {
    if (!available("startVoice")) return;
    if (listening) {
      stopVoice();
      $("#ai-status").textContent = "Voice stopped. You can edit your words.";
      return;
    }
    voiceTarget = target;
    if (!voiceAllowed) {
      $("#ai-status").innerHTML =
        `<p>Android’s speech service may send audio to its provider. The transcript stays in your draft until you tap Send. You can stop at any time.</p><button class="secondary" data-ai="voice-consent">Start voice typing</button>`;
      return;
    }
    listening = true;
    if (target === "yap") startTimer();
    native.startVoice();
    $("#ai-status").textContent =
      "Listening. Tap the voice button to stop. Pauses may end a segment.";
  }
  function event(e) {
    if ((e.type === "voice" || e.type === "voiceError") && mode && listening) {
      listening = false;
      if (e.type === "voice") {
        const field = $(voiceTarget === "yap" ? "#yap-draft" : "#chat-draft");
        if (field) {
          field.value = (field.value + " " + e.text)
            .trim()
            .slice(0, voiceTarget === "yap" ? 12000 : 2000);
          field.dispatchEvent(new Event("input"));
        }
        if ($("#ai-status"))
          $("#ai-status").textContent =
            "Words captured. Tap voice again to add another segment, or edit your text.";
      } else if ($("#ai-status"))
        $("#ai-status").textContent =
          e.text || "Voice unavailable. You can keep typing.";
      return true;
    }
    if (!["chat", "profile", "quests", "chatError"].includes(e.type))
      return false;
    let data;
    try {
      data = JSON.parse(e.text);
    } catch {
      return true;
    }
    if (!pending || data.requestId !== pending.id) return true;
    const kind = pending.kind;
    pending = null;
    if (e.type === "chatError") {
      if (kind === "chat") {
        draft = messages.pop()?.content || "";
        chatView();
      }
      document
        .querySelectorAll(".modal [data-ai]")
        .forEach((b) => (b.disabled = false));
      document
        .querySelectorAll(".modal textarea")
        .forEach((x) => (x.disabled = false));
      if ($("#ai-status"))
        $("#ai-status").textContent =
          data.message ||
          "Claude is unavailable. Your draft is still here. Try again.";
      return true;
    }
    if (kind === "chat") {
      response = data;
      messages.push({ role: "assistant", content: data.reply });
      chatView();
    } else if (kind === "profile") {
      profileResult = {
        profile: data.profile,
        systemPrompt: data.systemPrompt,
      };
      profileReview();
    } else {
      const accepted = Wonder.addGenerated(state, data.quests || []);
      if (!save()) {
        state = read();
        close();
        render();
        return true;
      }
      close();
      render();
      modal(
        `<span class="pill">Made for your real life</span><h2>${accepted.length ? "Your next little moments." : "A little pause for now."}</h2><p class="sub">${accepted.length ? `${accepted.length} new ${accepted.length === 1 ? "quest is" : "quests are"} in your queue. Your next enabled quest notification will draw from this queue.` : "No new activities passed the checks this time. Your current queue is still here."}</p>${accepted.map(questCard).join("")}<p class="small">Checked against past suggestions by action, title, and activity meaning. Subtle similarities can still slip through; skip anything that feels repetitive.</p><button class="primary" data-action="close">Lovely. Back to my day.</button>`,
      );
    }
    return true;
  }
  const handlers = {
    "chat-consent": () => {
      if (available("chatRequest")) chatView();
    },
    "send-chat": sendChat,
    "review-chat": reviewChat,
    "new-yap": () => {
      draft = "";
      startedAt = 0;
      yapView();
    },
    "edit-yap": yapView,
    extract,
    generate,
    approve: () => {
      state.questProfile = { ...profileResult, updatedAt: Date.now() };
      if (!save()) {
        state = read();
        return;
      }
      draft = "";
      generate();
    },
    "forget-profile": () => {
      state.questProfile = null;
      if (!save()) {
        state = read();
        return;
      }
      openProfile();
      toast("Profile removed. Quest history is kept to avoid repeats.");
    },
    "voice-chat": () => voice("chat"),
    "voice-yap": () => voice("yap"),
    "voice-consent": () => {
      voiceAllowed = true;
      voice(voiceTarget);
    },
  };
  document.addEventListener("click", (e) => {
    const b = e.target.closest("[data-ai]");
    if (b && !b.disabled) handlers[b.dataset.ai]?.();
  });
  return { beginChat, openProfile, event, close, stopVoice };
})();
