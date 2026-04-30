# StockFlip Design System — "Nordisk Precision"

A design system distilled from the **StockFlip** Android app — a personal Swedish stock & crypto alerting tool with 7 alert types, market-hour-aware background updates, and a hybrid View + Jetpack Compose UI.

The visual language is named **Nordisk Precision (NP)** in the codebase. It combines a **pine-teal primary** and **sand-blue secondary** against muted nordic neutrals, with typographic voice split across a restrained editorial serif (DM Serif Display), a sans body (system default), and a tabular mono (JetBrains Mono) reserved exclusively for numbers.

## Sources

- **Codebase** — `StockFlip/` (Kotlin · Android Material 3)
  - Color tokens: `app/src/main/res/values/colors.xml` (+ `values-night/`) and `app/src/main/java/com/stockflip/ui/theme/Color.kt`
  - Theme: `ui/theme/Theme.kt`
  - Type scale: `ui/theme/Type.kt` — Material 3 Typography + `NordikNumericStyle`
  - Shapes: `ui/theme/Shape.kt` — includes a custom grouped-list shape system (`GroupPosition`, `groupShape()`)
  - Spacing: `ui/theme/Spacing.kt` — 3·6·12·24 scale
  - Components: `ui/components/` + `ui/components/cards/` (Compose)
    - `PriceTargetCard`, `DailyMoveCard`, `MetricAlertCard`, `PairCard`, `CombinedAlertCard` — original alert cards
    - `High52wCard` — 52-week / all-time-high drawdown alert card *(new)*
    - `PriceRangeCard` — price interval alert card *(new)*
    - `MultipleWatchesCard` — stock-list card with watch count + triggered badge *(new)*
    - `OverviewSummaryCard` — gradient header with Nära/Utlösta/Aktiva *(new)*
    - `IntradayChart` — Canvas chart with period SegmentedButton *(new)*
    - `PairPerformanceChart` — dual normalized series + spread chart with signal state *(new)*
  - XML layouts: `app/src/main/res/layout/*.xml`
  - User manual (Swedish): `docs/MANUAL.md`
  - PRD + design notes: `.planning/PROJECT.md`, `StockFlip/PRD`

## Product context

StockFlip is a single-developer Android app that monitors Yahoo Finance + Finnhub data and pushes notifications when user-configured alert conditions are met.

- **Single surface**: Android mobile (min SDK 24, target SDK 35).
- **Three bottom-nav tabs**: *Aktier* (stocks), *Par* (pairs), *Bevakningar* (alerts).
- **Seven alert types**: price target, daily move, 52-week drawdown, key metrics (PE/PS/yield), pair spread, price range, combined (AND/OR/NOT).
- **Hybrid UI**: Fragment/View-binding screens with Jetpack Compose cards embedded via `ComposeView`.
- **Markets supported**: Stockholm (.ST), US, London, Frankfurt, Tokyo, Oslo, Crypto.

## Index

| File | What it is |
|---|---|
| `README.md` | This document — content + visual foundations, iconography, asset manifest. |
| `colors_and_type.css` | All color + type tokens as CSS vars and utility classes. |
| `SKILL.md` | Agent Skill frontmatter so this system can be invoked as a Claude Code skill. |
| `fonts/` | DM Serif Display + JetBrains Mono (regular + semibold) TTFs lifted from the app. |
| `assets/` | Logos, launcher images, and `icons.js` — the full NP icon set as inline SVG paths. |
| `preview/` | Design-system review cards (colors, type, components, etc). |
| `ui_kits/android/` | High-fidelity HTML + JSX recreation of the Android app (Stocks, Pairs, Alerts, Detail). |

---

## CONTENT FUNDAMENTALS

**Language.** The product ships in **Swedish**. Nearly all user-facing copy is in Swedish; the only occasional Engelska is ticker metadata (exchange codes, currency codes). Code comments in the repo are mixed Swedish/English.

**Tone.** Precise, literal, calm, lightly formal. No marketing tone, no cheerleading, no exclamation marks. The voice reads like a well-made financial instrument panel — each label names exactly what it measures.

**Casing.** Sentence case everywhere. No TitleCase for headers. Ticker codes are UPPERCASE. Currency codes are UPPERCASE ("SEK", "USD"). Sorting/filter labels use a colon pattern: `Sortering: Bokstavsordning`, `Status: Aktiv`.

**Pronouns.** Second-person singular "du" is standard in onboarding and errors ("Tryck + för att bevaka en aktie"). System messages do not use "jag/vi".

**Emoji.** Essentially never in the UI except **country flags** (🇸🇪 🇺🇸 🇬🇧 🇩🇪 🇯🇵 🇳🇴) used as the only exchange identifier on stock detail screens.

**Numbers.** Swedish locale throughout: comma decimal separator, `#,##0.00` pattern, space thousands separator. Percentages always show a sign for changes: `+2.5%` / `−1.8%`. Always rendered in **JetBrains Mono** to preserve alignment.

**Status vocabulary.** `Aktiv` / `Inaktiverad` / `Triggad`, `Utlöst idag`, `Utlöst 12 nov`, `Ny` (badge for unseen triggers). Engångslarm = one-shot; återkommande = recurring.

**Empty states.** Two short lines — title + imperative subtitle with the `+` glyph. Example: *"Inga bevakningar ännu / Tryck + för att bevaka en aktie"*.

**Errors.** Plain and direct: *"Det finns inga träffar för din sökning."*, *"Kunde inte uppdatera bevakning: %1$s"*. No apologies, no exclamation marks.

---

## VISUAL FOUNDATIONS

### Color

A dual-tone **Pine & Sand** palette sitting on cool nordic neutrals.

- **Primary — Pine teal** `#1F8A7A` (light) / `#3AB7A4` (dark). Used for affordances, FAB, active tab indicators, the "Ny" badge.
- **Secondary — Sand blue** `#5E7FA3` / `#7FA8C9`. Used for chips and neutral affordances.
- **Tertiary — Warm amber** `#B8842F` / `#E7B65C`. **Reserved for triggered alerts** — semantically distinct from error red: "condition met" is not a failure.
- **Error** `#B94A5A` / `#E06C75` — only for destructive actions and failure states.
- **Price up** `#238B57` / `#3CCB7F`; **price down** `#B94A5A` / `#E06C75`. Exposed via `CompositionLocal` (`LocalPriceUp` / `LocalPriceDown`) so any screen auto-adapts.
- **Pair chart series** — hard-coded in `PairPerformanceChart.kt`, not in `Color.kt`: **Series A** `#0057D9` (solid line + circle), **Series B** `#D97706` (dashed line + square). Exposed as `--np-pair-series-a` / `--np-pair-series-b` in `colors_and_type.css`.
- **Background** `#F5F8FB` (a cool paper white, not pure white) / `#09111D` (near-black blue).
- **Surfaces** step up subtly: `surface` → `surfaceContainerLow` → `surfaceContainer` → `surfaceContainerHigh`. Dark mode uses the same ladder with ~6 lightness steps.

### Typography

Three font families, each with a reserved role:

- **DM Serif Display** — editorial serif, used for Material 3 `display*` and `headline*` roles. Used sparingly; the app itself uses `titleMedium` (system sans SemiBold) for most screens, so the serif appears primarily in marketing / splash / high-level labels.
- **System sans** (`FontFamily.Default`) — body and titles. `titleMedium` (15sp SemiBold) is the workhorse for card headlines; `labelMedium` (11sp Medium, 0.6sp letter-spacing) for ticker codes.
- **JetBrains Mono** — **numeric only**. Price, percent, ratio, spread. Tabular figures baked in. `SemiBold 15sp` for primary numbers; `Normal 12sp` for secondary. This is the single most recognizable typographic decision in the system.

### Spacing

A tight 4-step scale: **3 · 6 · 12 · 24 dp**. The 3dp card gap produces an iOS-style grouped-list density.

### Radii

`extraSmall 8 / small 14 / medium 20 / large 22 / extraLarge 28` — plus a local `ListCardShape = 10dp` and a grouped-list `groupShape(position)` that renders `FIRST/MIDDLE/LAST` with a `2dp` inner corner so consecutive cards in the same ticker group read as one connected unit.

### Cards

**Almost no shadows.** Cards use `elevation = 0dp` with a **1dp hairline border** (`LocalCardBorder` — `#D6E0E8` / `#213345`) instead. Only the Notes card uses `Widget.Material3.CardView.Elevated`. Card padding is a consistent 12dp.

### Backgrounds

No full-bleed imagery, no repeating patterns, no gradients, no illustrations. The background is a single flat tone. Depth is created by surface tonal steps (`surfaceContainer*` tokens), hairline borders, and a 1dp `colorOutline` divider above the bottom nav.

### Borders & dividers

Horizontal dividers inside cards are drawn with `colorOutlineVariant` at 0.5–1dp. The nav divider above the BottomNavigationView is 1dp `colorOutline`. The **StatusStripe** (5dp left edge of a triggered card) animates from transparent → tertiary amber on state change.

### Shadows

Effectively **none**. The system intentionally avoids Material elevation. The only elevation is a subtle shadow on the elevated Notes card.

### Animation

Used only as **state transitions**, never for decoration. All color and container changes use `tween(400ms, FastOutSlowInEasing)` — the Material 3 standard easing. Triggered-state animation: border + container color + left stripe all animate together. No bounces, no springs for card chrome. Simple slide-in/-out for fragment transitions (`slide_in_left/right`, `slide_out_left/right`).

### States

- **Hover** — n/a on mobile-only product.
- **Press** — Material ripple tinted with `colorPrimary` (see `Widget.StockFlip.CardView` `rippleColor`). No scale-down, no color change.
- **Active / Selected** — chip swaps from `secondaryContainer` (tonal neutral) → `primaryContainer` (pine-tinted) with no border. Bottom-nav items use a tinted pill indicator (see `selector_bottom_nav_indicator.xml`).
- **Disabled** — the `Inaktiverad` label + reduced opacity, driven by `isActive` flag on `WatchItem`.
- **Triggered** — amber StatusStripe + amber border + `tertiaryContainer` background + amber "Utlöst idag" pill.

### Transparency & blur

Used sparingly. Alpha is used to de-emphasize metadata (`.copy(alpha = 0.6f)` on "Uppdaterad HH:MM" lines). No backdrop blur anywhere.

### Imagery vibe

No photography. Country **flag emoji** are the only pictorial asset on-screen. Charts are line-based, drawn in Compose with `LocalPriceUp/Down` tints.

### Layout rules

- Screen horizontal margin: **12 dp**.
- Card external horizontal margin: 8–12 dp; card vertical gap between siblings: **3 dp** (grouped list) or 8 dp (independent cards).
- Fixed elements: top AppBar (`actionBarSize`, centered title), bottom `BottomNavigationView` with 1dp divider, FAB positioned bottom-right 16dp margin.
- Detail screens are single-column `NestedScrollView` with the hero card at top and stat cells as a 3-column equal-weight row beneath a divider.

---

## ICONOGRAPHY

**System.** The app uses **Material Symbols-style filled glyphs** hand-rolled as Android `<vector>` drawables at a 24×24 viewport. There is **no icon font** and **no third-party icon library dependency**. Inline Compose icons (`Icons.Default.ExpandMore`, etc.) are used for a small number of fallbacks inside Compose components.

**Coverage.** See `app/src/main/res/drawable/ic_*.xml` — the full set lifted into this design system as `assets/icons.js`:

`stock` (line chart), `compare_arrows` (two linked nodes), `notifications` (bell), `notifications_off`, `add`, `fab_add` (extra-bold plus — 4dp bar for FAB legibility), `arrow_back`, `arrow_upward`, `arrow_downward`, `crypto`, `delete`, `search`, `paid`, `expand_more`, `expand_less`.

**Stroke & fill.** Icons are **solid-fill** (not stroked) at 24×24, tinted via `app:tint` against the current `colorOnSurface` / `colorOnPrimary`. The one exception is `ic_compare_arrows` which is rendered as two filled circles + a connecting stroke. The FAB plus (`ic_fab_add`) has a thicker 4dp bar for legibility at small sizes against the teal FAB.

**Emoji as icons.** The **only** emoji used as iconography are **flag emoji** (🇸🇪 🇺🇸 🇬🇧 🇩🇪 🇯🇵 🇳🇴) on stock-detail screens, set at `18sp` next to the company name.

**Unicode as icons.** The status triangle and alert indicator use standard Android system drawables (`@android:drawable/ic_dialog_alert`), tinted with `colorError`. No decorative unicode glyphs.

**Logo.** The app's launcher icon is a teal rounded square with a stylized chart glyph (`assets/ic_launcher_playstore.png` at full resolution; `assets/ic_launcher.png` and `ic_launcher_round.png` at xxxhdpi).

---

## Caveats for agents using this system

- **No marketing website, no docs website, no desktop UI** — the entire system is calibrated for a single mobile Android surface. When producing web or presentation material, preserve the NP color + type vocabulary but adapt layout norms (denser mobile cards may look cramped at desktop widths).
- **The display serif (DM Serif Display) is under-used in the live app.** It lives in the Typography scale but the real screens lean on system sans + JetBrains Mono. Treat the serif as available but not default.
- **Copy is Swedish.** Translate intentionally; don't mix languages in a single screen.
- **Tertiary amber ≠ error red.** Triggered state uses amber; only destructive actions and real failures use error red.
