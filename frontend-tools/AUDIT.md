# Frontend audit — 2026-10-06

Audited the complete dashboard template, stylesheet, script, API response records, error handler, setup and CI before changing application sources. Starting revision: `6e85e29`; clean working tree on `main`.

## Confirmed findings

| Source | Lines | Bytes |
| --- | ---: | ---: |
| dashboard.html | 607 | 24,688 |
| dashboard.css | 1,508 | 28,625 |
| dashboard.js | 1,034 | 44,017 |

The script has 44 listener registrations, 55 distinct ID lookup targets and 11 separately wired error elements. It combines state, requests, validation, navigation, mutations and all renderers in one IIFE. Eleven endpoints are read after each three-second delay; a selected-account correction can add another cashflow read. The theoretical latency-free idle rate is 220 reads/minute, excluding startup/corrections. This is an estimate; browser measurements will be recorded separately.

The five duplicate sidebar destinations and two misleading topbar actions exist. Connect refreshes data. Crypto copy, mesh-ready trend and assets wording misrepresent the simulated demo. Payment selects contain fixed fixture accounts. HTTP errors discard status, error code and trace. Notices overwrite one another indefinitely. CSS repeats exact color literals and has four important declarations. Frontend tooling and visual tests are absent.

## Corrections and additional findings

- The heatmap is data-driven: credits, debits and net movement set each cell's heat. Weekday labels are replaced from volume buckets. Preserve both; only static sparklines and flow curves are decorative.
- Manual sidebar collapse is inert: no handler or collapsed desktop CSS exists. Preserve the current automatic tablet compaction; defer a new desktop layout.
- Analytics validators only check a few top-level fields; malformed nested dates/numbers can throw during rendering. Summary validation omits several displayed totals.
- Cashflow can race account selection. The initial account selection is resolved after concurrent reads, provoking an extra request; that correction skips validation.
- Replacing whole table/device lists every poll discards focused elements. Delegate events and preserve retained row focus.
- Transaction rows lack Enter activation despite being focusable. The drawer trap omits its button and leaves the background focusable. Fix these without changing default styling.
- Invalid route feedback is cleared only when the destination changes, not the source.
- The optional demo PIN is ignored by backend authorization; retain the existing explicitly demo-labelled input.
- Preserve `[hidden]` and reduced-motion important declarations. They are intentional accessibility protections.

## Preservation and risks

No dynamic HTML injection or inline handlers were found. Existing safe DOM construction, vendored fonts/icons/licenses, INR formatting, timeout, reduced motion, skip link, labelled tables, dialogs and keyboard behavior must remain. Backend source, DTOs, migrations and endpoint contracts are outside the write scope.

Content removal necessarily changes pixels and some text geometry. Record exact exceptions, retain the original baseline, and lock a separate cleanup reference before structural changes. Preserve CSS source order, including overrides appended after media queries. Use the same pinned Linux Chromium environment locally and in CI to avoid platform rasterization differences.

The active host Java is 26, not the required 17. Existing PostgreSQL/Redis services use ports 55432/6380 and contain user data. The test harness uses a separate Compose project, isolated data and Java 17. Only its own disposable services may be removed.

User approval of the implementation plan authorizes the enumerated content corrections. No additional approval checkpoints are needed for those edits.
