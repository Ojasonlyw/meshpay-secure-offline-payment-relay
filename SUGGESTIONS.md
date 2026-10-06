# Deferred frontend ideas

These are not implemented by the visual-freeze refactor.

- Implement the currently inert manual desktop sidebar-collapse control only as a separate
  approved layout change. Automatic tablet compaction is preserved.
- Evaluate push updates or a read-only aggregate dashboard endpoint in a future backend phase.
  The current work deliberately keeps every API contract and backend source unchanged.
- Consider route-level loading/bundling only if measurements justify it. Native modules now
  improve ownership; this refactor does not claim smaller first-load JavaScript bytes.
- Replace static sparklines and ledger flow curves with genuine metrics in a separately
  designed chart phase. They are decorative now; the working heatmap and volume/cashflow
  charts remain real data views.
- Add broader CSS coverage for unusual data, empty/error states and reduced motion before
  pruning dormant selectors. No speculative selector removal was performed.
- Evaluate stronger focus restoration when dismissing queued errors and clearer early
  feedback when fewer than two accounts are available; preserve existing normal styling.
- Track an upstream compatible fix for the development-only Stylelint/glob advisory chain.
- Consider a settled, browser-independent custom validation message in a future UI change if
  strict browser-popover screenshots become necessary. Native validation behavior remains.
- Real payments, PIN authentication, device networking, access control and deployment require
  separate backend/product work. This remains a simulated demo ledger, not banking software.
