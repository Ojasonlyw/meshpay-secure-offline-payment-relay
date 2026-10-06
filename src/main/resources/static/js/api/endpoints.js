/** Thin wrappers for existing endpoint contracts; no runtime dependencies. */
import { request } from "./client.js";
/** @param {AbortSignal} signal @returns {Promise<Array>} */
export const getAccounts = (signal) => request("/api/accounts", { signal });
/** @param {AbortSignal} signal @returns {Promise<Array>} */
export const getPayments = (signal) => request("/api/payments", { signal });
/** @param {AbortSignal} signal @returns {Promise<object>} */
export const getSummary = (signal) =>
  request("/api/dashboard/summary", { signal });
/** @param {AbortSignal} signal @returns {Promise<object>} */
export const getMesh = (signal) => request("/api/mesh/state", { signal });
/** @param {AbortSignal} signal @returns {Promise<Array>} */
export const getTransactions = (signal) =>
  request("/api/transactions", { signal });
/** @param {string|null} vpa @param {AbortSignal} signal @returns {Promise<object>} */
export const getCashflow = (vpa, signal) =>
  request(
    "/api/dashboard/cashflow" +
      (vpa ? "?accountVpa=" + encodeURIComponent(vpa) : ""),
    { signal },
  );
/** @param {AbortSignal} signal @returns {Promise<object>} */
export const getActivity = (signal) =>
  request("/api/dashboard/activity", { signal });
/** @param {AbortSignal} signal @returns {Promise<object>} */
export const getNetwork = (signal) =>
  request("/api/dashboard/network-stats", { signal });
/** @param {AbortSignal} signal @returns {Promise<object>} */
export const getVolume = (signal) =>
  request("/api/dashboard/transaction-volume", { signal });
/** @param {AbortSignal} signal @returns {Promise<object>} */
export const getSecurity = (signal) =>
  request("/api/dashboard/security-events", { signal });
/** @param {AbortSignal} signal @returns {Promise<Array>} */
export const getRoutes = (signal) => request("/api/mesh/routes", { signal });
/** @param {string} path @param {object|null} body @returns {Promise<*>} */
export const post = (path, body) =>
  request(path, {
    method: "POST",
    ...(body
      ? {
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }
      : {}),
  });
