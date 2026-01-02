# Analys av PRD Fas 1 - Implementeringsstatus (Uppdaterad)

## Översikt
Detta dokument analyserar hur mycket av PRD Fas 1 som är implementerat och vad som återstår, baserat på den uppdaterade PRD:en.

## ✅ Implementerat

### 1. Datamodell för bevakningar (6.1)

#### ✅ WatchType sealed class
- **Status**: Delvis implementerat
- **Implementation**: `app/src/main/java/com/stockflip/WatchType.kt`
- **Vad som finns**:
  - ✅ `PricePair` - Par-bevakning (befintlig funktionalitet)
  - ✅ `PriceTarget` - Målpris med `PriceDirection` (ABOVE/BELOW)
  - ✅ `ATHBased` - Drawdown från högsta (använder 52w high-data)
  - ✅ `KeyMetrics` - Nyckeltal (Fas 2 funktionalitet, redan implementerad)

- **Vad som saknas**:
  - ❌ Prisintervall (WITHIN_RANGE) - PRD kräver: `comparisonType: ≤, ≥, WITHIN_RANGE`
  - ❌ `SingleDailyMove` - PRD kräver: `SingleDailyMove(symbol, percentThreshold, direction)`

#### ⚠️ Dual system problem
- **Problem**: Det finns två parallella system:
  - `WatchType` (nyare system i `app/src/main/java/com/stockflip/`)
  - `WatchCriteria` (äldre system i root)
- **PRD krav (6.1)**: "Införa en gemensam modell för bevakningsregler"
- **Åtgärd behövs**: Enhetlig modell eller tydlig migration-strategi

#### ⚠️ Gemensam evalueringsfunktion
- **PRD krav (6.1)**: "Alla AlertRule ska kunna evalueras via en gemensam funktion, t.ex. evaluate(rule, marketData)"
- **Status**: Delvis - finns i `StockPriceUpdater.checkWatchCriteria()` men inte enhetlig för alla typer

### 2. Datahämtning (6.2)

#### ✅ Yahoo Finance integration
- **Status**: Implementerat
- **Implementation**: `YahooFinanceService`
- **Vad som finns**:
  - ✅ Senaste pris (`getStockPrice()`)
  - ✅ 52-veckors högsta (`fiftyTwoWeekHigh` i `Meta`)
  - ✅ Föregående stängning (`regularMarketPreviousClose` i `Meta`)
  - ✅ Dagshögsta (`regularMarketDayHigh`)

- **PRD krav**: ✅ Uppfyllt för datahämtning

#### ⚠️ Dagsförändring i procent
- **PRD krav (6.2)**: "Dagens prisförändring i procent (antingen direkt från API eller härlett från 'previous close')"
- **Status**: Data finns (`regularMarketPreviousClose`) men **ingen funktion för att beräkna dagsförändring**
- **Behöver implementeras**:
```kotlin
fun getDailyChangePercent(symbol: String): Double? {
    val currentPrice = getStockPrice(symbol) ?: return null
    val previousClose = getPreviousClose(symbol) ?: return null
    return ((currentPrice - previousClose) / previousClose) * 100
}
```

#### ✅ Uppdateringspolicy
- **Status**: Implementerat
- **Implementation**: 
  - WorkManager (`StockPriceUpdater`) - 15 minuters intervall
  - Uppdatering vid appstart (`MainActivity.loadInitialData()`)
  - Uppdatering vid refresh (`MainViewModel.refreshWatchItems()`)
- **PRD krav**: ✅ Uppfyllt

### 3. Nya bevakningstyper (6.3)

#### ⚠️ Målpris / prisintervall
- **PRD krav (6.3)**: "Villkor: pris ≤ X, pris ≥ X, eller pris inom [A, B]"
- **Status**: Delvis implementerat
- **Vad som finns**:
  - ✅ `PriceTarget` med `PriceDirection` (ABOVE/BELOW) - stödjer ≤ och ≥
- **Vad som saknas**:
  - ❌ Prisintervall (WITHIN_RANGE) - "pris inom [A, B]"
- **Behöver implementeras**:
```kotlin
// I WatchType.kt
data class PriceRange(
    val minPrice: Double,
    val maxPrice: Double
) : WatchType()
```

#### ✅ Drawdown från 52-veckors högsta
- **PRD krav (6.3)**: "Villkor: procentuell nedgång från 52w high ≥ Y %"
- **Status**: Implementerat (men använder termen "ATH")
- **Implementation**: `WatchType.ATHBased` med `DropType.PERCENTAGE`
- **Notering**: 
  - `getATH()` hämtar faktiskt `fiftyTwoWeekHigh` (korrekt data)
  - Men termen "ATH" kan vara förvirrande - PRD säger specifikt "52-veckors högsta"
- **PRD krav**: ✅ Uppfyllt (data är korrekt, terminologi kan förbättras)

#### ❌ Dagsrörelse i procent
- **PRD krav (6.3)**: "Villkor: dagsförändring i % ≥ +X eller ≤ -X"
- **Status**: **SAKNAS HELT**
- **PRD krav (6.1)**: `SingleDailyMove(symbol, percentThreshold, direction)` där `direction: upp, ned, eller båda`
- **Behöver implementeras**:
```kotlin
// I WatchType.kt
data class DailyMove(
    val percentThreshold: Double,
    val direction: MoveDirection
) : WatchType()

enum class MoveDirection {
    UP,      // Alert when daily change ≥ +threshold
    DOWN,    // Alert when daily change ≤ -threshold
    BOTH     // Alert when |daily change| ≥ threshold
}
```

### 4. Enskild aktie – detaljvy (6.4)

#### ❌ Dedikerad detaljvy
- **PRD krav (6.4)**: "För en vald aktie ska detaljvyn visa:"
  - Namn + ticker
  - Senaste pris
  - Dagens förändring i %
  - 52-veckors högsta
  - Sektion "Bevakningar" med snabbval och lista
- **Status**: **SAKNAS HELT**
- **Vad som finns**:
  - Dialog för att visa detaljer (`dialog_watch_item_detail.xml`)
  - Visar pris i listan (`item_watch_item.xml`)
- **Vad som saknas**:
  - ❌ Dedikerad Fragment/Activity för enskild aktie
  - ❌ Visning av dagsförändring i %
  - ❌ Visning av 52w high
  - ❌ Snabbval för att skapa bevakningar
  - ❌ Lista av befintliga bevakningar för aktien

#### ❌ Snabbval för bevakningar
- **PRD krav (6.4)**: "Snabbval för att skapa: målpris-larm, drawdown-larm, dagsrörelselarm"
- **Status**: **SAKNAS**
- **Behöver implementeras**: UI-komponenter i detaljvyn

#### ❌ Lista av befintliga bevakningar
- **PRD krav (6.4)**: "Lista med befintliga bevakningar för aktien: typ, villkor, status"
- **Status**: **SAKNAS**
- **Behöver implementeras**: RecyclerView eller liknande i detaljvyn

### 5. Alert-utvärdering & notiser (6.5)

#### ✅ Utökad utvärdering
- **PRD krav (6.5)**: "Befintlig mekanism ska utökas till att iterera över samtliga AlertRule (par + single)"
- **Status**: Implementerat
- **Implementation**: `StockPriceUpdater` itererar över både `stockPairs` och `stockWatches`
- **PRD krav**: ✅ Uppfyllt

#### ✅ Notisformat
- **PRD krav (6.5)**: "Notisen ska innehålla: aktiens namn/ticker, vilket villkor som triggade, det faktiska värdet"
- **Status**: Implementerat
- **Implementation**: `StockPriceUpdater.checkWatchCriteria()` skapar tydliga notiser
- **PRD krav**: ✅ Uppfyllt

#### ⚠️ Skyddsmekanism mot spam
- **PRD krav (6.5)**: "Varje alert får trigga max en gång per handelsdag eller markeras 'triggad' tills manuellt återaktiverad"
- **Status**: **DELVIS SAKNAS**
- **Vad som finns**: Notiser skickas
- **Vad som saknas**: 
  - ❌ Spårning av när alert senast triggade
  - ❌ Begränsning till en gång per handelsdag
  - ❌ Markering som "triggad" med manuell återaktivering
- **Behöver implementeras**: 
  - Fält i databasen: `lastTriggeredDate`, `isTriggered`
  - Logik i `StockPriceUpdater` för att kontrollera detta

### 6. Par-bevakningar (bibehållna)

#### ✅ Befintlig funktionalitet
- **PRD krav**: "Par-bevakningarna (befintlig funktionalitet) får inte gå sönder under fas 1"
- **Status**: ✅ Bibehållen
- **Implementation**: `StockPair` entity och `PricePair` watch type fungerar fortfarande
- **PRD krav**: ✅ Uppfyllt

## ❌ Saknas helt

### 1. Prisintervall-bevakning (WITHIN_RANGE)
**PRD krav (6.1, 6.3)**: `comparisonType: ≤, ≥, WITHIN_RANGE` och "pris inom [A, B]"

**Behöver implementeras**:
```kotlin
// I WatchType.kt
data class PriceRange(
    val minPrice: Double,
    val maxPrice: Double
) : WatchType()
```

**Utvärdering behövs**:
```kotlin
// I StockPriceUpdater eller evalueringsfunktion
when (watchType) {
    is WatchType.PriceRange -> {
        currentPrice >= watchType.minPrice && currentPrice <= watchType.maxPrice
    }
}
```

### 2. Dagsrörelse i procent-bevakning
**PRD krav (6.1, 6.3)**: `SingleDailyMove(symbol, percentThreshold, direction)`

**Behöver implementeras**:
1. **WatchType**:
```kotlin
data class DailyMove(
    val percentThreshold: Double,
    val direction: MoveDirection
) : WatchType()

enum class MoveDirection {
    UP,    // Alert when daily change ≥ +threshold
    DOWN,  // Alert when daily change ≤ -threshold
    BOTH   // Alert when |daily change| ≥ threshold
}
```

2. **Beräkning av dagsförändring**:
```kotlin
// I YahooFinanceService
suspend fun getDailyChangePercent(symbol: String): Double? {
    val currentPrice = getStockPrice(symbol) ?: return null
    val previousClose = getPreviousClose(symbol) ?: return null
    return ((currentPrice - previousClose) / previousClose) * 100
}

suspend fun getPreviousClose(symbol: String): Double? {
    val response = api.getStockPrice(symbol)
    return response.chart?.result?.firstOrNull()?.meta?.regularMarketPreviousClose
}
```

3. **Utvärdering**:
```kotlin
// I StockPriceUpdater
is WatchType.DailyMove -> {
    val dailyChange = YahooFinanceService.getDailyChangePercent(symbol) ?: return false
    when (watchType.direction) {
        MoveDirection.UP -> dailyChange >= watchType.percentThreshold
        MoveDirection.DOWN -> dailyChange <= -watchType.percentThreshold
        MoveDirection.BOTH -> abs(dailyChange) >= watchType.percentThreshold
    }
}
```

### 3. Enskild aktie detaljvy
**PRD krav (6.4)**: Fullständig detaljvy med alla krav

**Behöver implementeras**:
1. **Fragment/Activity**: `StockDetailFragment` eller `StockDetailActivity`
2. **Layout**: `fragment_stock_detail.xml` eller `activity_stock_detail.xml`
3. **ViewModel**: `StockDetailViewModel`
4. **UI-komponenter**:
   - Visning av: namn, ticker, pris, dagsförändring %, 52w high
   - Snabbval-knappar: "Skapa målpris-larm", "Skapa drawdown-larm", "Skapa dagsrörelselarm"
   - RecyclerView för befintliga bevakningar
5. **Navigation**: Integration med Navigation Component

### 4. Skyddsmekanism mot spam
**PRD krav (6.5)**: "Varje alert får trigga max en gång per handelsdag"

**Behöver implementeras**:
1. **Databas-fält**:
```kotlin
// I WatchItem eller StockWatchEntity
val lastTriggeredDate: String? = null  // Format: "YYYY-MM-DD"
val isTriggered: Boolean = false
```

2. **Logik i StockPriceUpdater**:
```kotlin
private fun shouldTriggerAlert(watch: WatchItem, today: String): Boolean {
    // Om redan triggad idag, skippa
    if (watch.lastTriggeredDate == today) return false
    // Om markerad som triggad, kräv manuell återaktivering
    if (watch.isTriggered) return false
    return true
}
```

3. **Uppdatering vid trigger**:
```kotlin
// När alert triggar, uppdatera databasen
watchItemDao.update(watch.copy(
    lastTriggeredDate = today,
    isTriggered = true
))
```

## ⚠️ Delvis implementerat / Kräver justering

### 1. Gemensam AlertRule-modell
**PRD krav (6.1)**: "Införa en gemensam modell för bevakningsregler"

**Problem**: Två parallella system:
- `WatchType` (nyare, i `app/src/main/java/com/stockflip/`)
- `WatchCriteria` (äldre, i root)

**Rekommendation**: 
- Beslut om vilket system som ska användas
- Migration-strategi om nödvändigt
- Eller enhetlig wrapper som stödjer båda

### 2. Gemensam evalueringsfunktion
**PRD krav (6.1)**: "Alla AlertRule ska kunna evalueras via en gemensam funktion, t.ex. evaluate(rule, marketData)"

**Status**: Delvis - finns i `StockPriceUpdater` men inte enhetlig

**Rekommendation**: 
- Skapa en `AlertEvaluator` klass med `evaluate(rule: AlertRule, marketData: MarketData): Boolean`
- Använd denna i `StockPriceUpdater`

### 3. Terminologi: 52w high vs ATH
**PRD krav**: Specifikt "52-veckors högsta"

**Status**: Implementationen använder faktiskt 52w high-data, men termen "ATH" används i kod

**Rekommendation**: 
- Döp om `ATHBased` till `Week52HighBased` för tydlighet
- Eller behåll namnet men dokumentera att det är 52w high

## Sammanfattning

### Implementeringsgrad: ~55%

**Fully implementerat**:
- ✅ Målpris-bevakning (delvis - saknar prisintervall)
- ✅ Drawdown-bevakning (använder 52w high-data korrekt)
- ✅ Dataintegration (Yahoo Finance)
- ✅ Notifieringssystem
- ✅ Par-bevakningar (bibehållna)
- ✅ Uppdateringspolicy

**Saknas helt**:
- ❌ Prisintervall-bevakning (WITHIN_RANGE)
- ❌ Dagsrörelse i procent-bevakning
- ❌ Enskild aktie detaljvy med alla krav
- ❌ Skyddsmekanism mot spam (en gång per handelsdag)
- ❌ Beräkning och visning av dagsförändring i %

**Kräver justering**:
- ⚠️ Gemensam AlertRule-modell (två parallella system)
- ⚠️ Gemensam evalueringsfunktion
- ⚠️ Terminologi (52w high vs ATH)

## Rekommenderade nästa steg (prioriterade)

### Prioritet 1: Kritiska saknade funktioner
1. **Dagsrörelse i procent-bevakning**
   - Implementera `WatchType.DailyMove`
   - Implementera `getDailyChangePercent()` i `YahooFinanceService`
   - Implementera utvärdering i `StockPriceUpdater`

2. **Prisintervall-bevakning**
   - Implementera `WatchType.PriceRange`
   - Implementera utvärdering

3. **Beräkning av dagsförändring**
   - Implementera `getDailyChangePercent()` och `getPreviousClose()` i `YahooFinanceService`

### Prioritet 2: UI-komponenter
4. **Enskild aktie detaljvy**
   - Skapa Fragment/Activity
   - Implementera alla krav från PRD 6.4
   - Snabbval för bevakningar
   - Lista av befintliga bevakningar

### Prioritet 3: Förbättringar
5. **Skyddsmekanism mot spam**
   - Lägg till fält i databasen
   - Implementera logik i `StockPriceUpdater`

6. **Gemensam evalueringsfunktion**
   - Skapa `AlertEvaluator` klass
   - Refaktorera `StockPriceUpdater` att använda denna

### Prioritet 4: Arkitektur
7. **Enhetlig datamodell**
   - Beslut om WatchType vs WatchCriteria
   - Migration-strategi om nödvändigt

8. **Terminologi**
   - Klargöra 52w high vs ATH
   - Eventuellt döpa om för tydlighet

