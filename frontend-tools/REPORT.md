# Frontend implementation report — 2026-10-06

Implemented on `frontend-refactor` in the nested repository containing `pom.xml`.
The approved brief's visual freeze, not a new dashboard design, governed the work.
The frontend-design skill guided interaction/accessibility quality while the explicit
preservation constraint kept the existing aesthetics unchanged.

## Delivered

The 1,034-line JavaScript IIFE is replaced with 30 native modules; the largest is about
192 lines. API, state, formatting, UI, views and services have explicit ownership and
documented exports. No framework, production npm package, CDN asset or build step was added.
The application still starts with Java 17 Maven alone.

The 1,508-line stylesheet is extracted into seven ordered partials (largest 255 lines).
Repeated exact white-alpha values become identical-valued tokens. All selectors, media
queries and declaration order are preserved, including appended overrides. A semantic
PostCSS comparison verifies the complete cascade against frozen cleanup source. The
redundant empty-state padding important flag is removed only after computed-style checks;
`[hidden]` and both reduced-motion important rules remain. No speculative selector pruning
was performed.

Eight wrapper-free Thymeleaf fragments retain the same landmarks, SVG scaffolding, DOM
hooks and classes. Description, matching dark theme metadata and an orange SVG favicon are
added. Original vendored Manrope fonts/icons/licenses are unchanged.

## Content corrections

Only Home, Accounts, Transactions, Payments, Mesh bridge, Activity and Analytics remain
in navigation. Recipients, Vaults, Reports, Earn, Invoices, OTHER, Applications and Messages
are removed. Notifications accurately describes security notifications and keeps its action.
Connect is Refresh; Cash is Ledger: demo; the crypto claim is gone; the route action reads
Add mesh route; Total assets reads Ledger balance. Active-link readiness and the sidebar
connection/bridge footer use real summary and freshness data. Static sparklines/flow curves
are decorative; the heatmap, weekday labels, cashflow and volume charts retain real data.

Both payment selects use API accounts, preserve still-valid choices and prefer Alice/Bob.
If accounts disappear, fallbacks avoid the other valid/default choice whenever two accounts
exist. Enter searches records and Ctrl/Cmd+K focuses search. No new desktop sidebar layout
was introduced: automatic tablet compaction remains; manual collapse remains a documented
pre-existing limitation.

The original reference is immutable. Content cleanup is independently locked at `89a459d`;
attributable sidebar/topbar/text reflow is enumerated in `content-exceptions.json`.

## Reliability and observed traffic

The state store exposes `get`, `set(patch)` and `subscribe`. Rendering validates every
consumed field, including nested analytics, optional values, dates and numeric metrics.
Requests retain a 12-second timeout; `ApiError` carries HTTP status, errorCode and traceId,
including response-header fallback. A body-read timeout remains a timeout, not invalid JSON.
Failures preserve loaded data and label it stale. Queued successful notices expire after
four seconds; failures stay dismissible, with selectable/copyable trace information.

Writes are single-flight: old reads are canceled/drained, mutation controls are disabled,
the existing payload is submitted once, and all reads refresh afterward when visible. No
payment submission is automatically retried. Per-resource reads cannot overlap; rapid
cashflow selection changes cannot replace current results with old data. Hidden documents
cancel/suspend reads and refresh on return. Failure delays double up to 60 seconds and reset
after success. Delegated list handlers and retained-row focus restoration avoid per-poll
listener proliferation and unnecessary focus loss. Transaction Enter activation, drawer
focus trapping/skip-link focus and route-validation clearing are repaired.

| Resources | Poll interval |
| --- | ---: |
| Payments, summary, mesh state | 3 seconds |
| Accounts, transactions, network statistics | 10 seconds |
| Cashflow, volume, activity, security, routes | 15 seconds |

Real-browser 60-second idle samples, excluding startup, measured **209 → 92 GET requests**,
a **56.0% reduction**. Raw endpoint counts are in `measurements/before-network.json` and
`after-network.json`. The latency-free estimate is 220 → 98/minute, not the measured result.
This small local sample is not a load test, SLA or first-load byte claim. Module/doc overhead
can increase static source bytes and initial file requests; the improvement measured here
is recurring traffic and maintainability.

## Verification

- ESLint (including unsafe-HTML rules), Stylelint and Prettier checks pass.
- 17 Node tests pass: contracts/formatting, selections/search/store, error metadata, timeout,
  body timeout, backoff/coalescing, rapid stale selection, complete CSS cascade preservation,
  module size and unchanged backend/vendor/reference files.
- Nine Playwright tests pass: send, four gossip rounds, SETTLED, exact Alice/Bob balance
  changes, balanced reconciliation, second-flush DUPLICATE with unchanged balances; filters,
  search, account/device selection, route save, reset confirm/cancel, dialog focus restoration,
  keyboard/mobile navigation, invalid payment prevention, persistent trace-bearing errors
  without retries, malformed-response recovery, visibility suspension, stale cashflow and
  queued notice timing.
- At 1440, 1280, 1024, 768, 430 and 360, height 1000 and DPR 1, **26 stable locked screenshots
  have zero changed pixels**. All 32 states have identical retained computed styles. Six
  settled invalid-form DOM comparisons against the frozen cleanup implementation also
  have **zero changed pixels**. There are no new axe violations across normal/dialog/drawer
  states. Original and cleanup screenshot images remain unchanged.
- Lighthouse accessibility is **100 before and 100 after**. The script fails on a lower score.
- Containerized Java 17 Maven `-DskipTests package` succeeds without frontend compilation.
  This is a packaging check; the backend Testcontainers suite was not rerun in this phase.
- No backend Java source, DTO, migration, backend configuration or `pom.xml` changes were
  made. Existing local PostgreSQL/Redis services and their data were preserved.

### Explicit acceptance deviation

The original requirement for zero raw differences in all 32 historical screenshots is
**not claimed as met**. Six historical invalid captures contain Chromium-owned validation
popovers and timing-dependent smooth-scroll/full-page composites. Desktop differences
are confined to native validation painting; the 360px historical capture also has an
unstable full-page composite. These raw differences remain visible in reports/diff PNGs.

The default visual gate requires zero application pixels for the six settled invalid states,
rendering original source revision `89a459d` with identical fonts, icons, fixtures and fixed
time; it checks native invalidity, then closes the native popover and settles scrolling.
All 32 retained computed-style comparisons are still exact. No existing screenshot was
replaced or broadly masked. `visual:historical-strict` retains the original raw comparison
and reports these six failures. `measurements/final-visual.json` records the final gate and
historical differences. This is an explained test-reference limitation, not a claim that
browser-owned popovers are pixel-identical.

## Tooling, commits and handoff

Original baselines, cleanup references, fixtures, accessibility findings and computed styles
are committed. Development instructions are in `frontend-tools/README.md`; deferred work is
in `SUGGESTIONS.md`; the root README describes the updated frontend. `frontend.yml` adds an
independent CI job using the same digest-pinned browser/Java/data images. It uploads visual
diffs, failure screenshots/traces, behavior reports, app logs and Lighthouse results.
**Hosted CI has not been observed; nothing was deployed or pushed.**

Separate commits cover audit, baseline tooling, content cleanup, JavaScript extraction,
CSS extraction, Thymeleaf extraction, reliability fixes, formatting and final verification.
The isolated preview remains available at <http://localhost:18080>. Its own demo balances
changed during verification; the user's existing local database did not.

The full development audit has five related high-severity Stylelint/glob advisory entries;
the production-only npm audit is clean (zero production dependencies). The lint inputs are
repository-controlled. A compatible upstream remediation is deferred and documented, not
hidden behind a breaking downgrade. No fully clean development audit is claimed.
