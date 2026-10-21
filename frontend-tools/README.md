# Frontend development and verification

The application uses native browser modules and ordinary Thymeleaf/static resources.
Running Maven requires Java 17, PostgreSQL and Redis, not Node, npm or a frontend build.
Everything in this directory is development-only. There are no production npm dependencies.

## Reproducible isolated preview

Run these commands from the repository directory containing `pom.xml`:

```powershell
docker compose -f frontend-tools/compose.yaml up -d app
docker compose -f frontend-tools/compose.yaml run --rm tools npm ci
```

Open <http://localhost:18080>. The isolated project is `meshpay-frontend`: its database,
Redis namespace/container, Maven cache and demo signing keys are separate from the normal
local setup. PostgreSQL/Redis ports are not published. Existing local data services are
not stopped, reset or migrated by these commands. The app mounts live resources for editing.
The browser and all service images are pinned by version and digest in `compose.yaml`.

## Checks

Use the pinned Linux browser container on Windows as well as CI:

```powershell
docker compose -f frontend-tools/compose.yaml run --rm tools npm run lint
docker compose -f frontend-tools/compose.yaml run --rm tools npm run format:check
docker compose -f frontend-tools/compose.yaml run --rm tools npm test
docker compose -f frontend-tools/compose.yaml run --rm tools npm run e2e
docker compose -f frontend-tools/compose.yaml run --rm tools npm run visual
docker compose -f frontend-tools/compose.yaml run --rm tools npm run lighthouse
```

Behavior tests deliberately make demo payments and reset mesh holdings, but only in the
isolated database. Each settlement uses INR 1; resetting does not undo ledger balances.
Do not point these tests at a database whose balances or mesh holdings you need to preserve.
Visual tests use committed API fixtures, not live balances. They freeze browser dates, load
vendored fonts, settle animations and use height 1000, DPR 1 and all six specified widths.

For quick host-only lint/unit/format checks, use a recent compatible Node (24+):

```powershell
cd frontend-tools
npm ci
npm run lint
npm test
npm run format:check
```

`npm run format` formats first-party assets/templates/tools, never vendored assets or locked
reference JSON/images. Browser checks should still use the container; platform font rendering
is not interchangeable. The lockfile pins development versions.

## Visual reference policy

`baseline/` is the immutable original dashboard. `cleanup/` is the separately locked,
approved content correction reference. `content-exceptions.json` explains attributable
changes. Capture commands refuse to replace existing references. No screenshots were updated
to accommodate refactor differences.

`visual` requires exact zero-pixel comparisons for 26 stable cleanup screenshots and six
settled invalid-form DOM comparisons, plus exact retained computed styles for all 32 states
and no new axe violations. The invalid-form comparison serves the untouched cleanup source
at Git revision `89a459d`, including the same fonts/icons, with deterministic fixtures. It
asserts native invalidity, then closes the browser-owned validation bubble and fixes scrolling
before comparing the application. A full Git history is required; CI uses `fetch-depth: 0`.

The six historical invalid screenshots include timing-dependent native popovers/smooth
scrolling, including a 360px full-page compositing artifact. Their raw differences remain in
`actual/result.json` and PNG diff files. **The original all-32 raw-pixel criterion is not
claimed as passed.** `npm run visual:historical-strict` retains that strict historical check
and currently fails for those six captures. Neither reference is silently replaced or masked.

Generated screenshots/reports live in ignored `actual/`, `test-results/` and
`playwright-report/`. CI uploads these, including diffs, traces, logs and Lighthouse output.
Hosted CI results must be observed separately; adding a workflow is not a hosted success.

## Ownership and maintenance

- `static/js/main.js`: one-time initialization.
- `api/`: safe HTTP errors, endpoint contracts and nested validation.
- `state/store.js`: `get()`, `set(patch)`, `subscribe()` snapshots.
- `format/` and `ui/`: formatters, DOM creation, charts, notices, dialogs and navigation.
- `views/`: rendering and delegated event ownership, including account fallback selection.
- `services/`: polling, serialized writes, connection freshness and search priority.
- `static/css/`: numbered partials linked in original cascade order; do not reorder links.
- `templates/fragments/`: wrapper-free Thymeleaf blocks, including original SVG scaffolding.

Intervals are in `static/js/config.js`. Successful reads reset bounded exponential backoff;
hidden documents cancel reads and refresh on return. Mutations drain old reads, disable write
controls, submit once and refresh afterward. Never add automatic payment POST retries.

`refactor.mjs` records the one-time mechanical extraction, not a build step. Do not run its
CSS/HTML extraction stages on an already extracted project. `network.mjs` measures one
minute of real read traffic; `smoke.mjs` reports live browser startup errors.

Full development `npm audit` currently reports five related high-severity advisory entries
through Stylelint's glob dependencies. Only repository-controlled patterns are passed to
lint. Production audit (`npm audit --omit=dev`) reports zero dependencies with vulnerabilities.
Do not apply the suggested downgrade to Stylelint 7 as an automatic "fix"; track a compatible
upstream patch. This is documented technical debt, not a clean full dependency audit claim.

Stop only this preview with:

```powershell
docker compose -f frontend-tools/compose.yaml down
```

This keeps isolated volumes and signing keys for reuse. No command here deletes existing
databases. Normal Java 17 Maven startup continues to work independently of these tools.
