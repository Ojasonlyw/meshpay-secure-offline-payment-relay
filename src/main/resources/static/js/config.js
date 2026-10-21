/** Shared frontend timing and lifecycle constants. */
export const REQUEST_TIMEOUT = 12000;
export const NOTICE_DURATION = 4000;
export const MAX_BACKOFF = 60000;
export const POLL_INTERVALS = Object.freeze({
  payments: 3000,
  summary: 3000,
  mesh: 3000,
  accounts: 10000,
  transactions: 10000,
  network: 10000,
  cashflow: 15000,
  volume: 15000,
  activity: 15000,
  security: 15000,
  routes: 15000,
});
