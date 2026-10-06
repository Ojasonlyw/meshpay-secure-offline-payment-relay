/** Owns views analytics. */
import {setState, getState} from '../state/store.js';
import {$, node} from '../ui/dom.js';
import {money, compactMoney} from '../format/money.js';
import {linePath, chartPoint} from '../ui/charts.js';
import {chartDate} from '../format/dates.js';

/** renderCashflow owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderCashflow(data) {
    setState({cashflow: data});
    $("cashflow-heading").title = data.accountVpa || "All accounts";
    document.querySelectorAll("[data-cashflow]").forEach(el => el.textContent = money(data.netMovement));
    document.querySelectorAll("[data-cashflow-rate]").forEach(el => {
      el.textContent = `Credits ${compactMoney(data.totalCreditVolume)}`;
      el.classList.toggle("danger", Number(data.netMovement) < 0);
      el.classList.toggle("positive", Number(data.netMovement) >= 0);
    });
    const points = getState().range === "recent" ? data.points.slice(-7) : data.points;
    const periodDebits = points.reduce((total, point) => total + Number(point.debits || 0), 0);
    const periodCredits = points.reduce((total, point) => total + Number(point.credits || 0), 0);
    document.querySelectorAll("[data-chart-total]").forEach(el => el.textContent = compactMoney(periodDebits));
    const max = Math.max(1, ...points.flatMap(p => [Number(p.credits), Number(p.debits)]));
    const hasDebits = points.some(point => Number(point.debits) > 0);
    const hasCredits = points.some(point => Number(point.credits) > 0);
    const hasMovement = hasDebits || hasCredits;
    const latestMovementIndex = hasMovement
      ? points.findLastIndex(point => Number(point.credits) > 0 || Number(point.debits) > 0) : -1;
    document.querySelector("[data-flow-red]").setAttribute("d", hasDebits ? linePath(points, "debits", max) : "");
    document.querySelector("[data-flow-white]").setAttribute("d", hasCredits ? linePath(points, "credits", max) : "");
    const chart = document.querySelector(".line-chart");
    chart.querySelector(".chart-empty").hidden = hasMovement;
    const dot = document.querySelector(".chart-dot");
    dot.hidden = !hasMovement;
    const tip = document.querySelector("[data-chart-tip]");
    tip.hidden = !hasMovement;
    if (hasMovement) {
      const point = points[latestMovementIndex];
      const coordinate = chartPoint(points, latestMovementIndex, Number(point.debits) > 0 ? "debits" : "credits", max);
      dot.setAttribute("cx", coordinate.x.toFixed(1));
      dot.setAttribute("cy", coordinate.y.toFixed(1));
      tip.style.setProperty("--tip-x", `${(coordinate.x / 720 * 100).toFixed(2)}%`);
      tip.style.setProperty("--tip-y", `${(coordinate.y / 260 * 100).toFixed(2)}%`);
      tip.classList.toggle("below", coordinate.y < 84);
      tip.querySelector("[data-chart-tip-date]").textContent = chartDate.format(new Date(`${point.bucket}T00:00:00Z`));
      tip.querySelector("[data-chart-tip-values]").textContent = `Debits ${compactMoney(point.debits)} · Credits ${compactMoney(point.credits)}`;
    }
    chart.setAttribute("aria-label", hasMovement
      ? `${getState().range === "recent" ? "Recent seven days" : "All fifteen days"} balance spending: debits ${compactMoney(periodDebits)}, credits ${compactMoney(periodCredits)}`
      : `${getState().range === "recent" ? "Recent seven days" : "All fifteen days"}: no spending or credits`);
    $("cashflow-map").replaceChildren(...data.points.flatMap(point => ["credits", "debits", "netMovement"].map(key => {
      const cell = node("span");
      cell.style.setProperty("--heat", String(Math.min(28, Math.round(Math.abs(Number(point[key])) * 28 / max))));
      cell.title = `${point.bucket}: ${key} ${money(point[key])}`;
      return cell;
    })));
    document.querySelector(".day-pills").style.gridTemplateColumns = `repeat(${points.length}, 1fr)`;
    document.querySelector(".day-pills").replaceChildren(...points.map((point, index) =>
      node("span", index === latestMovementIndex ? "active" : "", point.bucket.slice(-2))));
  }

/** renderVolume owns its existing dashboard behavior.
 * @param {*} data
 * @returns {*}
 */
export function renderVolume(data) {
    setState({volume: data});
    document.querySelectorAll("[data-bar-total]").forEach(el => el.textContent = compactMoney(data.settledAmount));
    const max = Math.max(1, ...data.points.map(p => Number(p.settledAmount)));
    [...document.querySelectorAll(".bars span")].forEach((bar, index) => {
      const point = data.points[index];
      bar.style.setProperty("--h", `${Math.round(Number(point?.settledAmount || 0) * 100 / max)}%`);
      bar.title = `${point?.bucket || ""}: ${money(point?.settledAmount || 0)}`;
      bar.classList.toggle("active", index === data.points.length - 1);
    });
    [...document.querySelectorAll(".bar-labels span")].forEach((label, index) => {
      const point = data.points[index];
      label.textContent = point ? new Date(`${point.bucket}T00:00:00Z`).toLocaleDateString("en-IN", {weekday: "short", timeZone: "UTC"}) : "";
    });
  }

/** Wire this module once after the document is ready. @returns {void} */
export function init() {
document.querySelectorAll("[data-range]").forEach(button => button.addEventListener("click", () => {
    setState({range: button.dataset.range});
    document.querySelectorAll("[data-range]").forEach(el => {
      el.classList.toggle("active", el === button);
      el.setAttribute("aria-pressed", String(el === button));
    });
    if (getState().cashflow) renderCashflow(getState().cashflow);
  }));
}
