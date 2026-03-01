# Requirements: StockFlip

**Defined:** 2026-03-01
**Core Value:** Users get notified when a stock or metric reaches a target — without specifying direction manually.

## v1.0 Requirements

### Watch Creation Simplification (WCS)

Remove the "Riktning" (direction) dropdown from PriceTarget and KeyMetrics dialogs.
Auto-infer direction at save time: if current value < target → ABOVE, if current value > target → BELOW, if unavailable → ABOVE.

**Add dialogs:**
- [ ] **WCS-01**: "Riktning" dropdown removed from PriceTarget add dialog (`dialog_add_price_target.xml`)
- [ ] **WCS-02**: "Riktning" dropdown removed from KeyMetrics add dialog (`dialog_add_key_metrics.xml`)
- [ ] **WCS-03**: PriceTarget add dialog layout cleaned up — no gap where dropdown was, last field has correct bottom margin
- [ ] **WCS-04**: KeyMetrics add dialog layout cleaned up — direction removed, spacing adjusted before history card

**Edit dialog:**
- [ ] **WCS-05**: "Riktning" dropdown removed from PriceTarget section in edit dialog (`dialog_watch_item_detail.xml`)
- [ ] **WCS-06**: "Riktning" dropdown removed from KeyMetrics section in edit dialog (`dialog_watch_item_detail.xml`)
- [ ] **WCS-07**: Edit dialog layout cleaned up for both PriceTarget and KeyMetrics sections

**Business logic:**
- [ ] **WCS-08**: PriceTarget direction auto-inferred at create time (currentPrice < target → ABOVE, else → BELOW, no price → ABOVE)
- [ ] **WCS-09**: KeyMetrics direction auto-inferred at create time (currentMetricValue < target → ABOVE, else → BELOW, no value → ABOVE)
- [ ] **WCS-10**: PriceTarget direction re-inferred at edit/save time using same logic
- [ ] **WCS-11**: KeyMetrics direction re-inferred at edit/save time using same logic

## v2 Requirements

### Quality / Tech Debt

- **QUAL-01**: Split monolithic MainActivity (~2500 lines) into focused components
- **QUAL-02**: Add offline indicator when prices cannot be refreshed
- **QUAL-03**: Add duplicate watch item detection at creation time
- **QUAL-04**: Add database migration tests

## Out of Scope

| Feature | Reason |
|---------|--------|
| Remove direction from DailyMove | UP/DOWN/BOTH is intentional — user chooses rise, drop, or either |
| Remove direction from data model | DB migration risk; field stays in WatchType, just set automatically |
| Change ATH drop type selector | "% or SEK" is a meaningful choice unlike direction |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| WCS-01 | Phase 1 | Pending |
| WCS-02 | Phase 1 | Pending |
| WCS-03 | Phase 1 | Pending |
| WCS-04 | Phase 1 | Pending |
| WCS-05 | Phase 1 | Pending |
| WCS-06 | Phase 1 | Pending |
| WCS-07 | Phase 1 | Pending |
| WCS-08 | Phase 1 | Pending |
| WCS-09 | Phase 1 | Pending |
| WCS-10 | Phase 1 | Pending |
| WCS-11 | Phase 1 | Pending |

**Coverage:**
- v1.0 requirements: 11 total
- Mapped to phases: 11
- Unmapped: 0 ✓

---
*Requirements defined: 2026-03-01*
*Last updated: 2026-03-01 after initial definition*
