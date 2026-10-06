/** Owns ui navigation. */
import {$} from './dom.js';

/** setDrawer owns its existing dashboard behavior.
 * @param {*} open
 * @returns {*}
 */
export function setDrawer(open) {
    $("sidebar").classList.toggle("open", open);
    $("drawer-backdrop").hidden = !open;
    $("menu-toggle").setAttribute("aria-expanded", String(open));
    $("menu-toggle").setAttribute(
      "aria-label",
      open ? "Close navigation" : "Open navigation",
    );
    document.body.classList.toggle("drawer-open", open);
    if(open)document.querySelector('.page').setAttribute('aria-hidden','true');
    else document.querySelector('.page').removeAttribute('aria-hidden');
    if (window.innerWidth < 768) $("sidebar").inert = !open;
    if (open) {
      $("sidebar").setAttribute("role", "dialog");
      $("sidebar").setAttribute("aria-modal", "true");
      $("sidebar").querySelector("a").focus();
    } else {
      $("sidebar").removeAttribute("role");
      $("sidebar").removeAttribute("aria-modal");
    }
  }

/** updateNavigation owns its existing dashboard behavior.

 * @returns {*}
 */
export function updateNavigation() {
    const current = location.hash || "#overview";
    document.querySelectorAll(".nav-link").forEach((link) => {
      const active = link.getAttribute("href") === current;
      link.classList.toggle("active", active);
      if (active) link.setAttribute("aria-current", "location");
      else link.removeAttribute("aria-current");
    });
  }

/** resizeNavigation owns its existing dashboard behavior.

 * @returns {*}
 */
export function resizeNavigation() {
    setDrawer(false);
    $("sidebar").inert = mobile.matches;
  }

export const mobile = window.matchMedia("(max-width: 767px)");

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
$("main").tabIndex=-1;
document.querySelectorAll("[data-focus]").forEach(button => button.addEventListener("click", () => {
    location.hash = `#${button.dataset.focus}`;
  }));
$("menu-toggle").addEventListener("click", () =>
    setDrawer(!$("sidebar").classList.contains("open")),
  );
$("drawer-backdrop").addEventListener("click", () => {
    setDrawer(false);
    $("menu-toggle").focus();
  });
document.addEventListener("keydown", (event) => {
    if (!$("sidebar").classList.contains("open")) return;
    if (event.key === "Escape") {
      setDrawer(false);
      $("menu-toggle").focus();
    }
    if (event.key === "Tab") {
      const links = [...$("sidebar").querySelectorAll("a, button:not(:disabled)")].filter(el=>el.getClientRects().length);
      const first = links[0],
        last = links[links.length - 1];
      if(!$("sidebar").contains(document.activeElement)){event.preventDefault();first.focus();return;}
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  });
document.querySelectorAll(".sidebar a").forEach((link) => {
    link.addEventListener("click", () => {
      if ($("sidebar").classList.contains("open")) {
        setDrawer(false);
        const target = document.querySelector(link.getAttribute("href"));
        target.setAttribute("tabindex", "-1");
        target.focus({ preventScroll: true });
      }
    });
  });
mobile.addEventListener("change", resizeNavigation);
window.addEventListener("hashchange", updateNavigation);
document.querySelectorAll(".nav-link").forEach((link) => {
    link.title = link.textContent.trim();
    link.setAttribute("aria-label", link.textContent.trim());
  });
resizeNavigation();
updateNavigation();
}
