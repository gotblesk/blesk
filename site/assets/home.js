"use strict";

// Without this script every feature is listed one after another; with it the chips become tabs.
const initTabs = () => {
  const list = document.querySelector("[data-tabs]");
  if (!list) return;
  const tabs = Array.from(list.querySelectorAll("[role='tab']"));
  const panels = tabs.map((tab) => document.getElementById(tab.getAttribute("aria-controls")));
  if (panels.some((panel) => !panel)) return;

  const select = (index, moveFocus, animate) => {
    tabs.forEach((tab, i) => {
      const selected = i === index;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
      panels[i].hidden = !selected;
      panels[i].classList.toggle("fade", selected && animate);
    });
    if (moveFocus) tabs[index].focus();
  };

  panels.forEach((panel, i) => {
    panel.setAttribute("role", "tabpanel");
    panel.setAttribute("aria-labelledby", tabs[i].id);
    panel.tabIndex = 0;
  });

  list.addEventListener("click", (event) => {
    const tab = event.target.closest("[role='tab']");
    if (!tab || tab.getAttribute("aria-selected") === "true") return;
    select(tabs.indexOf(tab), false, true);
    if (navigator.vibrate) navigator.vibrate(6);
  });

  list.addEventListener("keydown", (event) => {
    const current = tabs.indexOf(document.activeElement);
    if (current < 0) return;
    const targets = { ArrowRight: current + 1, ArrowLeft: current - 1, Home: 0, End: tabs.length - 1 };
    if (!(event.key in targets)) return;
    event.preventDefault();
    select((targets[event.key] + tabs.length) % tabs.length, true, true);
  });

  list.hidden = false;
  select(0, false, false);
};

// Each tap on «Отправить» adds the next line of a short dialogue; older messages leave from the top.
const MAX_MESSAGES = 7;

const initChat = () => {
  const feed = document.querySelector("[data-msgs]");
  const composer = document.querySelector("[data-composer]");
  const script = document.querySelector("[data-script]");
  if (!feed || !composer || !script) return;

  const draft = composer.querySelector("[data-draft]");
  const send = composer.querySelector("[data-send]");
  const lines = Array.from(script.content.querySelectorAll("p")).map((line) => ({
    side: line.dataset.side,
    text: line.textContent,
  }));
  if (lines.length === 0) return;
  let step = 0;

  send.addEventListener("click", () => {
    const line = lines[step % lines.length];
    const message = document.createElement("p");
    message.className = `m ${line.side} pop`;
    message.textContent = line.text;
    feed.append(message);
    while (feed.children.length > MAX_MESSAGES) feed.firstElementChild.remove();
    step += 1;
    draft.textContent = lines[step % lines.length].text;
    if (navigator.vibrate) navigator.vibrate(8);
  });

  draft.textContent = lines[0].text;
  composer.hidden = false;
};

initTabs();
initChat();
