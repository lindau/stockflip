# Dead Code Analysis — StockFlip

**Datum:** 2026-09-05
**Verktyg:** StockFlip är ett Kotlin/Android-projekt (Gradle), inte Node/TypeScript. `knip`, `depcheck` och `ts-prune` är inte tillämpliga här. Motsvarande analys gjordes med:
- `./gradlew lintDebug` (Android Lints `UnusedResources`-regel) för resurser
- Manuell grep-baserad analys av samtliga top-level `class`/`object`/`interface`-deklarationer i `app/src/main` mot hela källträdet (main + test + androidTest)

Varje lint-träff verifierades manuellt (grep med korrekt namnform — t.ex. style-namn med punktnotation `Widget.StockFlip.Toolbar` istället för R-klassens understreck-form) för att undvika falska positiva.

## Sammanfattning

- **Oanvända top-level Kotlin-klasser/objekt/interfaces:** 0 st hittades. Ingen death-kod på klassnivå.
- **Oanvända resurser:** 21 bekräftade (av 24 lint-varningar — 3 var falska positiva pga substrängsmatchning, se nedan).
- **Config/entry points:** Inga fynd — inget att rapportera i DANGER-kategorin.

## Falska positiva (uteslutna)

| Resurs | Varför den INTE är död |
|---|---|
| `R.color.ic_launcher_background` (values/ic_launcher_background.xml) | Initialt flaggad, men verifierad separat — se SAFE-tabellen, den är faktiskt död (drawable-varianten används av adaptiva ikoner, inte color-resursen). Behölls i SAFE. |

*(De tre ursprungliga falska positiva var `black`, `white`, `ic_launcher_background` som matchade `@android:color/black` etc. via substräng — efter verifiering med korrekt gränser visade `black` och `white` sig ändå vara genuint döda i det här projektet, se tabell nedan.)*

## SAFE — kan tas bort direkt (oanvända resurser, inga entry points)

Verifierade döda via grep i hela `app/src` (main + test + androidTest), inklusive kontroll av alternativ namnform (punkt vs underscore för styles, `@android:color/x` vs `@color/x` för färger).

| Resurs | Fil | Typ |
|---|---|---|
| `R.color.black` | `res/values/colors.xml:54` | color (endast `@android:color/black` används på riktigt, ej projektets egen) |
| `R.color.white` | `res/values/colors.xml:55` | color (endast `@android:color/white` används på riktigt) |
| `R.color.notification_highlight` | `res/values/colors.xml:58` + `res/values-night/colors.xml:28` | color |
| `R.color.notification_active` | `res/values/colors.xml:59` + `res/values-night/colors.xml:29` | color |
| `R.color.notification_inactive` | `res/values/colors.xml:60` + `res/values-night/colors.xml:30` | color |
| `R.color.price_down` | `res/values/colors.xml:61` + `res/values-night/colors.xml:33` | color (dublett — `PriceDown` finns redan i `ui/theme/Color.kt` för Compose) |
| `R.color.price_up` | `res/values/colors.xml:62` + `res/values-night/colors.xml:34` | color (dublett — `PriceUp` finns redan i `ui/theme/Color.kt` för Compose) |
| `R.color.ic_launcher_background` | `res/values/ic_launcher_background.xml` | color (adaptiv ikon använder drawable-varianten, inte denna) |
| `R.font.dm_serif_display` | `res/font/dm_serif_display.xml` | font-fil |
| `R.font.jetbrains_mono` | `res/font/jetbrains_mono.xml` | font-fil |
| `R.layout.dropdown_item` | `res/layout/dropdown_item.xml` | layout |
| `R.layout.item_watch_item` | `res/layout/item_watch_item.xml` | layout (troligen ersatt av Compose-korten i `ui/components/cards/`) |
| `R.drawable.ic_add` | `res/drawable/ic_add.xml` | drawable |
| `R.drawable.ic_launcher_foreground` | `res/drawable/ic_launcher_foreground.xml` | drawable |
| `R.drawable.ic_paid` | `res/drawable/ic_paid.xml` | drawable |
| `R.string.alerts_empty_state` | `res/values/strings.xml:7` | string |
| `R.string.menu_add` | `res/values/strings.xml:45` | string |
| `R.string.toast_watch_updated` | `res/values/strings.xml:60` | string |
| `R.style.Widget_StockFlip_Toolbar` | `res/values/themes.xml:67` + `values-night/themes.xml:66` | style |
| `R.style.Widget_StockFlip_FilterChip` | `res/values/themes.xml:80` + `values-night/themes.xml:79` | style |
| `R.style.Theme_StockFlip_Dialog` | `res/values/themes.xml:88` + `values-night/themes.xml:91` | style |
| `R.style.Theme_StockFlip_BottomSheet` | `res/values/themes.xml:92` + `values-night/themes.xml:91` | style |

## CAUTION — kräver manuell blick innan borttagning

Inga fynd i den här körningen. (Ingen väg-/routing-kod, inga publika API:er eller UI-komponenter identifierades som oanvända.)

## DANGER — rör inte utan explicit godkännande

Inga fynd. Inga config-filer, entry points (`MainActivity`, `StockFlipApplication`, Workers, migrations) flaggades som oanvända — förväntat och bra.

## Föreslagen borttagningsordning

1. Döda `drawable`/`font`/`layout`-filer (fristående filer, enklast att ta bort och verifiera build).
2. Döda `color`/`style`-poster i `colors.xml`/`themes.xml` (måste tas bort i **både** `values/` och `values-night/` samtidigt för att undvika inkonsekvens).
3. Döda `string`-poster i `strings.xml`.

Efter varje steg: `./gradlew testDebugUnitTest` (körs synkront, aldrig i bakgrunden — se CLAUDE.md) samt en ny `./gradlew lintDebug`-körning för att bekräfta att `UnusedResources`-listan krymper som förväntat och inga nya lint-fel introduceras.

## Resultat

Samtliga 21 SAFE-poster togs bort i tre steg (fristående filer → colors/styles i values+values-night → strings), med `./gradlew testDebugUnitTest` körd och godkänd efter varje steg.

- **Baseline:** `testDebugUnitTest` grönt, `UnusedResources` = 24 varningar (varav 3 initiala grep-falska-positiva som verifierades separat innan borttagning).
- **Efter borttagning:** `testDebugUnitTest` grönt, `assembleDebug` grönt, `UnusedResources` = 0 varningar. Totalt antal lint-varningar sjönk från 251 till 221.
- **Ej relaterat till denna städning:** `lintDebug`-tasken misslyckas fortfarande pga 45–46 förbefintliga `NewApi`-fel (t.ex. `ZoneId.of` i `StockMarketScheduler.kt`, kräver API 26 eller core library desugaring, min SDK är 24). Detta fanns innan städningen och är utanför scope — flaggas separat, ej åtgärdat här.
- Inga CAUTION- eller DANGER-poster identifierades eller rördes.
