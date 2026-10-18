"use strict";

// The menu is a link to the footer until this script turns it into a bottom sheet.
(() => {
  const opener = document.querySelector("[data-menu-open]");
  const sheet = document.getElementById("menu");
  if (!opener || !sheet || typeof sheet.showModal !== "function") return;

  const focusableSelector = "a[href], button:not([disabled]), [tabindex]:not([tabindex='-1'])";
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  let returnFocus = true;
  let closingTimer = 0;

  const finishClose = () => {
    window.clearTimeout(closingTimer);
    sheet.removeEventListener("animationend", onClosingAnimationEnd);
    sheet.classList.remove("is-closing");
    if (sheet.open) sheet.close();
  };

  function onClosingAnimationEnd(event) {
    if (event.target === sheet) finishClose();
  }

  const close = ({ animate = true, restoreFocus = true } = {}) => {
    if (!sheet.open || sheet.classList.contains("is-closing")) return;
    returnFocus = restoreFocus;
    if (!animate || reducedMotion.matches) {
      finishClose();
      return;
    }
    sheet.classList.add("is-closing");
    sheet.addEventListener("animationend", onClosingAnimationEnd);
    // Covers browsers that skip the animation event, for example in a background tab.
    closingTimer = window.setTimeout(finishClose, 400);
  };

  const open = (event) => {
    event.preventDefault();
    if (sheet.open) return;
    sheet.showModal();
    opener.setAttribute("aria-expanded", "true");
    const closeButton = sheet.querySelector("[data-menu-close]");
    if (closeButton) closeButton.focus();
  };

  opener.setAttribute("role", "button");
  opener.setAttribute("aria-haspopup", "dialog");
  opener.addEventListener("click", open);
  opener.addEventListener("keydown", (event) => {
    if (event.key === " ") open(event);
  });

  sheet.addEventListener("close", () => {
    opener.setAttribute("aria-expanded", "false");
    if (returnFocus) opener.focus();
    returnFocus = true;
  });

  // Escape plays the closing animation instead of the instant native close.
  sheet.addEventListener("cancel", (event) => {
    event.preventDefault();
    close();
  });

  sheet.addEventListener("click", (event) => {
    if (event.target === sheet || event.target.closest("[data-menu-close]")) {
      close();
    } else if (event.target.closest("a[href]")) {
      close({ animate: false, restoreFocus: false });
    }
  });

  sheet.addEventListener("keydown", (event) => {
    if (event.key !== "Tab") return;
    const items = Array.from(sheet.querySelectorAll(focusableSelector)).filter((item) => item.offsetParent !== null);
    if (items.length === 0) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  });
})();
