"use strict";

const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

// Without this script every feature panel is shown one after another; with it they become tabs.
const initTabs = () => {
  const list = document.querySelector("[data-tabs]");
  if (!list) return;
  const tabs = Array.from(list.querySelectorAll("[role='tab']"));
  const panels = tabs.map((tab) => document.getElementById(tab.getAttribute("aria-controls")));
  if (panels.some((panel) => !panel)) return;

  const select = (index, moveFocus) => {
    tabs.forEach((tab, i) => {
      const selected = i === index;
      tab.setAttribute("aria-selected", String(selected));
      tab.tabIndex = selected ? 0 : -1;
      panels[i].hidden = !selected;
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
    if (tab) select(tabs.indexOf(tab), false);
  });

  list.addEventListener("keydown", (event) => {
    const current = tabs.indexOf(document.activeElement);
    if (current < 0) return;
    const targets = { ArrowRight: current + 1, ArrowLeft: current - 1, Home: 0, End: tabs.length - 1 };
    if (!(event.key in targets)) return;
    event.preventDefault();
    select((targets[event.key] + tabs.length) % tabs.length, true);
  });

  list.hidden = false;
  select(0, false);
};

// Messages of one author form a column, as in the app: the aligned edge stays straight, and where
// widths differ the narrower message is joined to the wider one with a concave fillet.
const RADIUS = 20;
const FILLET = 12;
const SAME_WIDTH = RADIUS + FILLET + 4;

const shapeGroup = (group) => {
  const messages = Array.from(group.children);
  messages.forEach((message) => {
    message.style.width = "";
    delete message.dataset.top;
    delete message.dataset.bottom;
  });
  // offsetWidth ignores the entry animation's scale, so a message is measured at its real size.
  const widths = messages.map((message) => message.offsetWidth);

  // Widths too close for a fillet are evened out so the free edge runs straight.
  const runs = [];
  widths.forEach((width, index) => {
    const run = runs[runs.length - 1];
    if (run && Math.abs(run.width - width) < SAME_WIDTH) {
      run.members.push(index);
      run.width = Math.max(run.width, width);
    } else {
      runs.push({ width, members: [index] });
    }
  });
  runs.forEach((run) => {
    run.members.forEach((index) => {
      if (widths[index] < run.width) messages[index].style.width = `${run.width}px`;
      widths[index] = run.width;
    });
  });

  for (let i = 1; i < messages.length; i += 1) {
    if (widths[i - 1] === widths[i]) {
      messages[i - 1].dataset.bottom = "square";
      messages[i].dataset.top = "square";
    } else if (widths[i - 1] > widths[i]) {
      messages[i].dataset.top = "fillet";
    } else {
      messages[i - 1].dataset.bottom = "fillet";
    }
  }
};

const clock = new Intl.DateTimeFormat("ru-RU", { hour: "2-digit", minute: "2-digit" });

const timeLabel = (minutesAgo) => {
  const moment = new Date(Date.now() - minutesAgo * 60000);
  const label = document.createElement("time");
  label.className = "msg-time";
  label.dateTime = moment.toISOString();
  label.textContent = clock.format(moment);
  return label;
};

const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));

const initChat = () => {
  const feed = document.querySelector("[data-chat-feed]");
  const composer = document.querySelector("[data-chat-composer]");
  const script = document.querySelector("[data-chat-script]");
  if (!feed || !composer || !script) return;

  const field = composer.querySelector(".composer-field");
  const sendButton = composer.querySelector("button");
  const steps = Array.from(script.content.querySelectorAll("p")).map((line) => ({
    mine: line.dataset.from === "mine",
    text: line.textContent,
  }));
  if (steps.length === 0 || !steps[0].mine) return;

  feed.querySelectorAll("time[data-minutes-ago]").forEach((label) => {
    label.replaceWith(timeLabel(Number(label.dataset.minutesAgo)));
  });
  const opening = Array.from(feed.children).map((node) => node.cloneNode(true));
  let next = 0;
  let busy = false;

  const shapeAll = () => feed.querySelectorAll(".msg-group").forEach(shapeGroup);

  const scrollToEnd = () => {
    feed.scrollTo({ top: feed.scrollHeight, behavior: reducedMotion.matches ? "auto" : "smooth" });
  };

  // A new message sticks to the last group when the author is the same.
  const groupFor = (mine) => {
    const last = feed.lastElementChild;
    if (last && last.classList.contains(mine ? "mine" : "theirs")) return last;
    const group = document.createElement("div");
    group.className = `msg-group ${mine ? "mine" : "theirs"}`;
    feed.append(group);
    return group;
  };

  const fill = (bubble, text) => {
    bubble.classList.remove("typing");
    bubble.replaceChildren(document.createTextNode(`${text} `), timeLabel(0));
  };

  const addBubble = (mine) => {
    const group = groupFor(mine);
    const bubble = document.createElement("p");
    bubble.className = "msg is-new";
    group.append(bubble);
    return { group, bubble };
  };

  const send = (text) => {
    const { group, bubble } = addBubble(true);
    fill(bubble, text);
    shapeGroup(group);
    scrollToEnd();
  };

  const receive = async (text) => {
    const { group, bubble } = addBubble(false);
    bubble.classList.add("typing");
    const status = document.createElement("span");
    status.className = "visually-hidden";
    status.textContent = feed.dataset.typingLabel || "";
    bubble.append(status);
    for (let i = 0; i < 3; i += 1) {
      const dot = document.createElement("span");
      dot.className = "typing-dot";
      bubble.append(dot);
    }
    shapeGroup(group);
    scrollToEnd();
    await wait(reducedMotion.matches ? 400 : 1100);
    fill(bubble, text);
    shapeGroup(group);
    scrollToEnd();
  };

  const showNext = () => {
    field.textContent = steps[next < steps.length ? next : 0].text;
  };

  const restart = () => {
    feed.replaceChildren(...opening.map((node) => node.cloneNode(true)));
    shapeAll();
    next = 0;
  };

  composer.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (busy) return;
    busy = true;
    sendButton.setAttribute("aria-disabled", "true");
    if (next >= steps.length) restart();
    send(steps[next].text);
    next += 1;
    while (next < steps.length && !steps[next].mine) {
      await wait(reducedMotion.matches ? 200 : 500);
      await receive(steps[next].text);
      next += 1;
    }
    showNext();
    sendButton.removeAttribute("aria-disabled");
    busy = false;
  });

  if ("ResizeObserver" in window) {
    let lastWidth = 0;
    new ResizeObserver(([entry]) => {
      const width = Math.round(entry.contentRect.width);
      if (width === lastWidth) return;
      lastWidth = width;
      shapeAll();
    }).observe(feed);
  }

  shapeAll();
  // Widths measured with the fallback font are wrong once Onest arrives.
  if (document.fonts) document.fonts.ready.then(shapeAll);
  showNext();
  composer.hidden = false;
};

initTabs();
initChat();
