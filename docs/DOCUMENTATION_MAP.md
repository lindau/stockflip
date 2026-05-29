# Documentation Map

This file separates the documentation that is actively maintained from material that is reference-only or generated.

## Actively maintained

- `README.md` - project overview and setup
- `CLAUDE.md` - agent guidance for working in this repo
- `docs/MANUAL.md` and `app/src/main/assets/manual.md` - user manual; keep these two files identical
- `docs/CHANGELOG.md` and `app/src/main/assets/changelog.md` - changelog; keep these two files identical
- `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`, `.planning/codebase/*` - planning and codebase context used by the local workflow

## Reference / design material

- `Claude Design/StockFlip Design System.md`
- `Claude Design/StockFlip Design System README.md`
- `Claude Design/ui_kits/android/README.md`

These files are useful references, but they are not product documentation that users see in the app.

## Generated or ephemeral

- `app/build/**` - generated build output, never source-of-truth
- conflict or recovery leftovers such as `*.resolved`, `*.orig`, `*.bak`, `*.swp`, `*.swo`, and similar temporary files

## Cleanup rule of thumb

If a markdown file is not referenced anywhere, is not part of the synced manual/changelog pair, and is not needed as design or planning context, it should usually be removed instead of kept as a loose standalone note.
