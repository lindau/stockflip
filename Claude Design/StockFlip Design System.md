---
name: stockflip-design
description: Use this skill to generate well-branded interfaces and assets for StockFlip, either for production or throwaway prototypes/mocks/etc. StockFlip is a Swedish Android app for stock & crypto alerting with a Material 3 "Nordisk Precision" design system — pine-teal primary, sand-blue secondary, amber triggered states, JetBrains Mono for all numbers, and a calm, literal Swedish copy voice. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping.
user-invocable: true
---

Read the README.md file within this skill, and explore the other available files (`colors_and_type.css`, `assets/`, `fonts/`, `ui_kits/android/`, `preview/`).

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.

Key rules:
- **Copy voice is Swedish**, precise and literal. Sentence case. No exclamation marks. Second-person "du".
- **All numbers** (price, %, ratios, dates) use **JetBrains Mono** with tabular figures. Use a `+` sign for positive price changes.
- **Triggered ≠ error** — use the amber tertiary for "condition met", red error only for destructive actions and failures.
- **Cards have hairline 1dp borders, no elevation.** Background depth comes from surface tonal steps, not shadows.
- **Pine teal (`#1F8A7A` / `#3AB7A4`) is primary**; use for FAB, active indicators, badges. Don't over-use it.
- **Only emoji** the brand uses are **country flags** on detail screens.
- **Tokens live in `colors_and_type.css`** — use its CSS vars (`--np-primary`, `--np-tertiary`, etc.) rather than re-inventing colors.

If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some questions, and act as an expert designer who outputs HTML artifacts or production code, depending on the need.
