# State: StockFlip

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Requirements defined — roadmap pending
Last activity: 2026-03-01 — Milestone v1.0 started (Watch Creation Simplification)

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01)

**Core value:** Users get notified when a stock or metric reaches a target — without specifying direction manually.
**Current focus:** v1.0 — Watch Creation Simplification

## Milestone Goal

Remove the "Riktning" (direction) dropdown from PriceTarget and KeyMetrics dialogs. Auto-infer direction from current price vs target at save time. Clean up dialog layouts.

**11 requirements** across 3 XML files + Kotlin logic in add/edit flows.

## Next Action

Roadmap not yet created. Run `/gsd:plan-phase 1` after `/clear`.

Or create roadmap first: spawn gsd-roadmapper with:
- Start phase: 1
- Files: .planning/PROJECT.md, .planning/REQUIREMENTS.md
- Note: single phase milestone, all 11 WCS requirements → Phase 1

## Accumulated Context

- Codebase map in `.planning/codebase/` (7 documents, created 2026-03-01)
- Direction dropdown exists in 3 XML files: `dialog_add_price_target.xml`, `dialog_add_key_metrics.xml`, `dialog_watch_item_detail.xml`
- IDs: `directionInput` (add dialogs), `detailDirection` (PriceTarget edit), `detailKeyMetricsDirection` (KeyMetrics edit)
- Direction field stays in WatchType data model — no DB migration needed; just set programmatically
- Infer logic: currentPrice/currentMetricValue < target → ABOVE, else → BELOW, if no current value → ABOVE
- MainActivity.kt has the add dialog logic; StockDetailFragment.kt + AlertsFragment.kt handle edit flows
- DailyMove keeps its direction (UP/DOWN/BOTH) — intentional, out of scope
