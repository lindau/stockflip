# Plan för åtgärd av UI/UX-problem

## Översikt
Detta dokument beskriver en detaljerad plan för att åtgärda identifierade problem i StockFlip-appen.

---

## 1. Fel valuta för krypto på huvudsidan

### Problem
Krypto visar fel valuta på huvudsidan.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/CurrencyHelper.kt`
- **Ändring:** Uppdatera `formatPrice()` för att hantera krypto korrekt
- **Fil:** `app/src/main/java/com/stockflip/ui/components/cards/PairCard.kt`
- **Ändring:** Använd `CurrencyHelper.formatPrice()` istället för hårdkodad "SEK"
- **Fil:** Alla kortkomponenter som visar priser
- **Ändring:** Kontrollera att de använder korrekt valuta baserat på symbol

### Steg
1. Uppdatera `CurrencyHelper.formatPrice()` för att hantera krypto-symboler korrekt
2. Lägg till logik för att hämta valuta från `WatchItem` eller symbol
3. Uppdatera alla kortkomponenter att använda korrekt valuta

---

## 2. Aktiekorten är olika breda på huvudsidan

### Problem
Aktiekorten har olika bredder på huvudsidan.

### Åtgärd
- **Fil:** Alla kortkomponenter i `app/src/main/java/com/stockflip/ui/components/cards/`
- **Ändring:** Säkerställ att alla kort använder `Modifier.fillMaxWidth()`

### Steg
1. Kontrollera att alla `Card`-komponenter har `modifier.fillMaxWidth()`
2. Kontrollera att `ComposeWatchItemCard` använder `fillMaxWidth()`
3. Testa att alla kort har samma bredd

---

## 3. Ta bort status-text från bevakningskort

### Problem
Status-texten är redundant eftersom toggle-switchen redan visar status.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/ui/ComposeWatchItemCard.kt`
- **Ändring:** Sätt `showStatus = false` som standard
- **Fil:** Alla kortkomponenter som visar status-text
- **Ändring:** Ta bort eller dölj status-text-sektionen

### Steg
1. Uppdatera `ComposeWatchItemCard` att inte visa status som standard
2. Ta bort status-text från alla kortkomponenter (eller dölj den)
3. Testa att toggle-switchen fungerar korrekt

---

## 4. Kunna uppdatera en enskild aktie inifrån dess aktiesida

### Problem
Användare kan inte uppdatera aktiedata från aktiesidan.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/StockDetailFragment.kt`
- **Ändring:** Lägg till uppdateringsknapp och funktionalitet
- **Fil:** `app/src/main/res/layout/fragment_stock_detail.xml`
- **Ändring:** Lägg till uppdateringsknapp i UI

### Steg
1. Lägg till uppdateringsknapp i `fragment_stock_detail.xml`
2. Implementera `onRefresh()` i `StockDetailFragment`
3. Anropa `viewModel.loadStockData()` när knappen klickas
4. Visa loading-indikator under uppdatering

---

## 5. Dagens förändring visar "Börsen stängd" även när den är öppen

### Problem
Dagens förändring visar "Börsen stängd" även när börsen är öppen för enskilda aktier.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/StockDetailFragment.kt`
- **Ändring:** Förbättra logiken för att avgöra om börsen är öppen
- **Fil:** `app/src/main/java/com/stockflip/StockMarketScheduler.kt`
- **Ändring:** Lägg till metod för att kontrollera om börsen är öppen för en specifik symbol (krypto vs aktie)

### Steg
1. Uppdatera `displayStockData()` för att korrekt avgöra om börsen är öppen
2. Använd `StockMarketScheduler.isMarketOpen()` eller liknande
3. För krypto: börsen är alltid öppen
4. För aktier: kontrollera faktisk börsstatus

---

## 6. Dölj sorteringsknappen inne på aktiesidorna

### Problem
Sorteringsknappen visas på aktiesidorna där den inte behövs.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/MainActivity.kt`
- **Ändring:** Dölj sorteringsmenyn när `StockDetailFragment` är aktiv
- **Fil:** `app/src/main/java/com/stockflip/StockDetailFragment.kt`
- **Ändring:** Dölj toolbar-menyn när fragmentet visas

### Steg
1. I `MainActivity.navigateToStockDetail()`: dölj sorteringsmenyn
2. I `StockDetailFragment.onViewCreated()`: dölj sorteringsmenyn
3. Visa menyn igen när användaren går tillbaka till huvudsidan

---

## 7. Uppdatera expand/collapse-symboler

### Problem
Symbolerna för expand/collapse uppdateras inte korrekt.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/GroupedWatchItemAdapter.kt`
- **Ändring:** Säkerställ att ikonen uppdateras korrekt i `HeaderViewHolder.bind()`

### Steg
1. Kontrollera att `ic_expand_less` och `ic_expand_more` finns
2. Säkerställ att `bind()` uppdaterar ikonen korrekt baserat på `isExpanded`
3. Testa att ikonen ändras när sektionen expanderas/kollapsas

---

## 8. Fel valuta för krypto inne på dess sida

### Problem
Krypto visar fel valuta på dess detaljsida.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/StockDetailFragment.kt`
- **Ändring:** Uppdatera `displayStockData()` för att använda korrekt valuta för krypto
- **Fil:** `app/src/main/java/com/stockflip/YahooFinanceService.kt`
- **Ändring:** Säkerställ att valuta hämtas korrekt för krypto-symboler

### Steg
1. Identifiera krypto-symboler korrekt (t.ex. BTC-USD)
2. Använd korrekt valuta för krypto (t.ex. USD för BTC-USD)
3. Uppdatera `CurrencyHelper` för att hantera krypto-valutor

---

## 9. För krypto kommer börsen aldrig att vara stängd

### Problem
Krypto visar "Börsen stängd" även om kryptomarknaden är öppen 24/7.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/StockDetailFragment.kt`
- **Ändring:** Kontrollera om det är krypto innan visning av börsstatus
- **Fil:** `app/src/main/java/com/stockflip/StockSearchResult.kt`
- **Ändring:** Använd `isCryptoSymbol()` för att identifiera krypto

### Steg
1. I `displayStockData()`: kontrollera om `data.symbol` är krypto
2. Om krypto: visa alltid aktuell dagsförändring, aldrig "Börsen stängd"
3. Om aktie: använd normal börsstatus-logik

---

## 10. € och $ ska stå före beloppet, SEK efter

### Problem
Valutasymboler placeras fel: € och $ ska vara före, SEK efter.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/CurrencyHelper.kt`
- **Ändring:** Uppdatera `formatPrice()` för att placera symboler korrekt

### Steg
1. Uppdatera `formatPrice()` för att kontrollera valuta-typ
2. För USD, EUR, GBP, etc.: placera symbol före beloppet
3. För SEK, NOK, DKK, etc.: placera symbol efter beloppet
4. Uppdatera alla anrop till `formatPrice()` om nödvändigt

---

## 11. Flytta upp aktivtoggle så den är på samma höjd som aktienamnet

### Problem
Aktivtoggle är inte på samma höjd som aktienamnet.

### Åtgärd
- **Fil:** Alla kortkomponenter i `app/src/main/java/com/stockflip/ui/components/cards/`
- **Ändring:** Uppdatera layout för att alignera switch med aktienamnet

### Steg
1. I alla kortkomponenter: kontrollera `Row` med `verticalAlignment = Alignment.Top`
2. Säkerställ att `Switch` och `Text` (aktienamn) är på samma höjd
3. Testa i alla korttyper: `PriceRangeCard`, `DailyMoveCard`, `MetricAlertCard`, etc.

---

## 12. En aktie med kombinerade och vanliga bevakningar får två kort

### Problem
En aktie som har både kombinerade bevakningar och vanliga bevakningar får två aktiekort på huvudsidan.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/GroupedWatchItemAdapter.kt`
- **Ändring:** Uppdatera `buildFilteredList()` för att hantera kombinerade bevakningar korrekt

### Steg
1. I `buildFilteredList()`: kontrollera om en aktie har både kombinerade och vanliga bevakningar
2. Om så: gruppera dem under samma aktiekort
3. Uppdatera `MultipleWatchesWrapper` för att hantera båda typerna

---

## 13. För kombinerad bevakning räcker det med en gång aktienamn

### Problem
Kombinerade bevakningar visar aktienamnet flera gånger i kortet.

### Åtgärd
- **Fil:** `app/src/main/java/com/stockflip/ui/components/cards/CombinedAlertCard.kt`
- **Ändring:** Visa aktienamnet endast en gång i kortet

### Steg
1. I `CombinedAlertCard`: kontrollera hur aktienamn visas
2. Ta bort duplicerade visningar av aktienamn
3. Visa endast en gång i kortets header

---

## Implementeringsordning

### Fas 1: Kritiska buggar
1. Fel valuta för krypto (punkter 1, 8)
2. Börsstatus för krypto (punkt 9)
3. Dagens förändring visar fel status (punkt 5)

### Fas 2: UI-förbättringar
4. Aktiekortens bredd (punkt 2)
5. Status-text borttagning (punkt 3)
6. Valutasymbolplacering (punkt 10)
7. Toggle-höjd (punkt 11)

### Fas 3: Funktionella förbättringar
8. Uppdatera aktie från aktiesida (punkt 4)
9. Dölj sorteringsknapp (punkt 6)
10. Expand/collapse-symboler (punkt 7)

### Fas 4: Gruppering och visning
11. Dubblerade aktiekort (punkt 12)
12. Kombinerad bevakning - aktienamn (punkt 13)

---

## Testning

För varje ändring:
1. Testa med olika aktietyper (svenska, amerikanska, etc.)
2. Testa med krypto
3. Testa med kombinerade bevakningar
4. Testa expand/collapse-funktionalitet
5. Verifiera att alla kort har samma bredd
6. Kontrollera valutaplacering för olika valutor
