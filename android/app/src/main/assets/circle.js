"use strict";
// Circle shares only a chosen display name and completed-quest events.
// Contact labels remain on the device; the native layer retains API tokens.
const Circle = (() => {
  let board = null,
    period = "week",
    busy = false,
    error = "",
    sample = false,
    selectedLabel = "";
  function status() {
    try {
      return JSON.parse(window.Native.getCircleStatus());
    } catch {
      return { joined: false };
    }
  }
  function request(action, payload = {}) {
    if (!window.Native?.circleRequest) {
      toast("Friends rankings require the Android app and Circle service.");
      return;
    }
    if (busy) return;
    busy = true;
    error = "";
    window.Native.circleRequest(action, JSON.stringify({ period, ...payload }));
    if (tab === "circle" && !$(".modal")) render();
  }
  function sync() {
    sample = false;
    request("sync", {
      events: state.completed.map((e) => ({
        id: Wonder.completionEventId(e),
        at: e.at,
      })),
    });
  }
  function renderPage() {
    const s = status();
    let rows = board?.people || [];
    if (sample)
      rows = [
        { id: "sample1", displayName: "Mika · sample", completed: 8, rank: 1 },
        { id: "sample2", displayName: "Jules · sample", completed: 6, rank: 2 },
        {
          id: "sample3",
          displayName: "You · sample",
          completed: 4,
          rank: 3,
          isYou: true,
        },
      ];
    $("#main").innerHTML =
      `<div class="eyebrow">Your little circle</div><h1>Good things,<br>better together.</h1><p class="sub">A friendly nudge from your people.<br>See the little side quests you’ve each completed.</p><div class="circle-art"><span>✿</span><span>✳</span><span>❋</span><p>There’s room for everyone to grow.</p></div>${s.joined ? `<div class="card"><div class="small">Your private friend code</div><div class="friend-code">${esc(s.code)}</div><p class="small">Share this yourself with someone you want to connect with. They’ll see your display name and quest counts.</p></div><div class="horizontal"><button class="primary" data-circle="add">Add a friend</button><button class="secondary" data-circle="refresh" ${busy ? "disabled" : ""}>${busy ? "Updating…" : "Refresh counts"}</button></div>` : `<div class="card"><h3>Invite the people you know.</h3><p class="small">Choose a contact to label a friend, then enter the code they share with you. Your address book stays on your phone.</p><div class="spacer"></div><button class="primary" data-circle="join">Join with a display name</button></div>`}<div class="section-head"><h2>Side quest rankings</h2><span class="pill">${sample ? "Sample data" : s.joined ? "Private circle" : "Opt-in only"}</span></div><div class="chips"><button class="chip ${period === "week" ? "selected" : ""}" data-circle-period="week">Last 7 days</button><button class="chip ${period === "all" ? "selected" : ""}" data-circle-period="all">All time</button></div><div class="spacer"></div>${error ? `<div class="notice" role="status">${esc(error)} Your offline quests still work.</div>` : ""}${sample ? '<p class="small sample-label">Illustration only. These people and counts are made up.</p>' : ""}${rows.length ? `<div class="ranking" aria-label="Completed side quests ranking">${rows.map((p) => `<div class="rank-row ${p.isYou ? "rank-you" : ""}"><span class="rank-number">${p.rank}</span><span class="avatar ${p.isYou ? "pink" : "sage"}">${esc(p.displayName.slice(0, 1).toUpperCase())}</span><div class="rank-person"><strong>${esc(state.contactLabels?.[p.id] || p.displayName)}</strong><span class="small">${p.isYou ? "You" : state.contactLabels?.[p.id] ? "Connected contact" : "Circle friend"}</span></div><div class="rank-total">${p.completed}<span>quests</span></div></div>`).join("")}</div>` : `<div class="empty">${icon("heart")}<h3>${s.joined ? "Your circle starts with you." : "A little company for your quests."}</h3><p class="small">${s.joined ? "Refresh to share your completed count, then add a friend with their code." : "Only people who join and exchange a private code can appear. No one is added automatically."}</p></div>`}<p class="quiet-note">Same count, same rank. No penalties for quiet days.<br>Quest counts are not a measure of happiness or health.</p>${s.joined ? `<p class="small circle-sync-note">Counts update when each friend opens their circle or refreshes. ${board && !sample ? "Showing the latest server response." : ""}</p><button class="text-button" data-circle="leave">Leave Circle & delete my shared data</button>` : `<button class="text-button" data-circle="sample">${sample ? "Hide sample ranking" : "See a sample ranking"}</button>`}`;
  }
  function joinDialog() {
    modal(
      `<h2>A name for your circle.</h2><p class="sub">Use a nickname or a name your friends recognize.</p><label for="circle-name">Your display name</label><input id="circle-name" maxlength="32" autocomplete="nickname" placeholder="e.g. River"><div class="notice">Joining shares this name and completed side quest counts with friends you connect to by code. Your mood, reflections, photos, and contacts are never shared.</div><button class="primary" data-circle="join-submit">I’m in. Create my friend code.</button><p class="small">You can leave and delete your shared data at any time.</p>`,
    );
  }
  function addDialog() {
    selectedLabel = "";
    modal(
      `<h2>Make a little connection.</h2><p class="sub">Ask your friend for their private code. This connects you both and lets you see each other’s completed quest counts.</p><label for="contact-label">Contact label, only on your phone</label><input id="contact-label" maxlength="80" placeholder="Optional name"><button class="text-button" data-circle="pick">Choose from my contacts</button><div class="field"><label for="friend-code">Their private friend code</label><input id="friend-code" maxlength="32" autocapitalize="characters" autocomplete="off" spellcheck="false" placeholder="Paste or type their code"></div><p class="small">The contact picker reads only the person you select. It does not upload a phone number, invite anyone, or verify who owns a code.</p><button class="primary" data-circle="add-submit">Connect with my friend</button>`,
    );
  }
  function event(e) {
    if (e.type === "contactError") {
      toast(e.text || "Contact selection is unavailable.");
      return true;
    }
    if (e.type === "contactCancelled") return true;
    if (e.type === "contact") {
      if ($("#contact-label")) $("#contact-label").value = e.text || "";
      return true;
    }
    if (e.type === "circleError") {
      busy = false;
      error = e.text || "Couldn’t reach your circle. Try again soon.";
      toast(error);
      if (tab === "circle" && !$(".modal")) render();
      return true;
    }
    if (e.type !== "circle") return false;
    busy = false;
    let r;
    try {
      r = JSON.parse(e.text);
    } catch {
      toast("The circle response could not be read.");
      return true;
    }
    if (r.action === "leave") {
      board = null;
      sample = false;
      state.contactLabels = {};
      save();
      if ($(".modal [data-circle]")) closeModal();
      toast("Your shared profile and counts have been deleted.");
    } else if (r.action === "join") {
      if ($(".modal [data-circle]")) closeModal();
      sync();
      return true;
    } else {
      if (r.friend?.id && selectedLabel) {
        state.contactLabels = {
          ...state.contactLabels,
          [r.friend.id]: selectedLabel,
        };
        save();
        selectedLabel = "";
      }
      if (r.people) board = r;
      if ($(".modal [data-circle]")) closeModal();
    }
    if (tab === "circle") render();
    return true;
  }
  document.addEventListener("click", (e) => {
    const b = e.target.closest("button");
    if (!b) return;
    if (b.dataset.circlePeriod) {
      if (busy) return;
      period = b.dataset.circlePeriod;
      if (sample || !status().joined) render();
      else sync();
      return;
    }
    const a = b.dataset.circle;
    if (!a) return;
    if (a === "join") joinDialog();
    else if (a === "join-submit") {
      const displayName = $("#circle-name").value.trim();
      if (!displayName) {
        toast("Choose a display name first.");
        return;
      }
      request("join", { displayName });
    } else if (a === "add") addDialog();
    else if (a === "add-submit") {
      const code = $("#friend-code").value.trim().toUpperCase();
      if (!code) {
        toast("Enter the code your friend shared.");
        return;
      }
      selectedLabel = $("#contact-label").value.trim();
      request("add", { code });
    } else if (a === "pick") {
      if (window.Native?.pickContact) window.Native.pickContact();
      else toast("The contact picker is available in Android.");
    } else if (a === "refresh") sync();
    else if (a === "sample") {
      sample = !sample;
      render();
    } else if (a === "leave")
      modal(
        `<h2>Leave your circle?</h2><p class="sub">Your server profile, shared quest counts, and friend connections will be deleted. Your private check-ins and photos stay on this phone.</p><button class="primary" data-circle="leave-submit">Leave & delete shared data</button><button class="text-button" data-action="close">Stay in my circle</button>`,
      );
    else if (a === "leave-submit") request("leave");
  });
  return { render: renderPage, event, sync, status };
})();
