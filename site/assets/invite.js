"use strict";

// The same page answers /i/<code>, /@<name>, /c/<name> and /r/<code>. It only changes its wording:
// nothing about the profile, channel or room is ever fetched or shown here.
(() => {
  const INVITE_CODE = /^[abcdefghjkmnpqrstuvwxyz23456789]{12}$/i;
  const KINDS = [
    { name: "invite", pattern: /^\/i\/([^/]+)\/?$/ },
    { name: "profile", pattern: /^\/@([^/]+)\/?$/ },
    { name: "channel", pattern: /^\/c\/([^/]+)\/?$/ },
    { name: "room", pattern: /^\/r\/([^/]+)\/?$/ },
  ];

  const path = window.location.pathname;
  const match = KINDS.map((kind) => ({ kind: kind.name, found: kind.pattern.exec(path) })).find((entry) => entry.found);
  const kind = match ? match.kind : "invite";

  document.querySelectorAll("[data-invite-text]").forEach((element) => {
    const text = element.dataset[kind];
    if (text) element.textContent = text;
  });
  const heading = document.querySelector("h1");
  if (heading) document.title = heading.textContent;

  const decode = (text) => {
    try {
      return decodeURIComponent(text);
    } catch (error) {
      return "";
    }
  };
  const broken = Boolean(match) && kind === "invite" && !INVITE_CODE.test(decode(match.found[1]));
  const brokenNote = document.querySelector("[data-invite-broken]");
  if (brokenNote) brokenNote.hidden = !broken;

  const agent = navigator.userAgent;
  const android = /Android/i.test(agent);
  const apple = /iPhone|iPad|iPod/i.test(agent) || (/Macintosh/i.test(agent) && navigator.maxTouchPoints > 1);
  const note = document.querySelector("[data-platform-note]");
  if (note && !android) {
    note.textContent = apple ? note.dataset.ios : note.dataset.desktop;
    note.hidden = false;
  }

  // A browser usually keeps a link to the site it is already showing to itself, so the button hands
  // the very same address to blesk through an intent; without blesk installed it reloads this page.
  const openApp = document.querySelector("[data-open-app]");
  if (openApp && android && match && !broken) {
    const target = `blesk.su${path}${window.location.search}`;
    const fallback = encodeURIComponent(window.location.href);
    const extras = `scheme=https;package=app.blesk;S.browser_fallback_url=${fallback}`;
    openApp.querySelector("a").href = `intent://${target}#Intent;${extras};end`;
    openApp.hidden = false;
  }
})();
