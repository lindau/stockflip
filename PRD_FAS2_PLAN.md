# PRD Fas 2 - Implementeringsplan
## Fundamenta & Nyckeltal (inkl. historik)

**Status**: Planering  
**Startdatum**: TBD  
**Mål**: Implementera historikvisning och presets för nyckeltal-bevakningar

---

## Översikt

Fas 2 fokuserar på att förbättra nyckeltal-bevakningar med historisk kontext. Användaren ska kunna se hur P/E, P/S och direktavkastning har sett ut historiskt (1/3/5 år) när de skapar larm, och få förslag på rimliga tröskelvärden baserat på historiken.

---

## Nuvarande status

### ✅ Redan implementerat (från Fas 1)
- `WatchType.KeyMetrics` med stöd för P/E, P/S, Dividend Yield
- `AlertRule.SingleKeyMetric` för evaluering
- `AlertEvaluator.evaluateSingleKeyMetric()` - evaluering fungerar
- UI för att skapa/redigera KeyMetrics-bevakningar
- `MetricAlertCard` för att visa KeyMetrics i listan
- Datahämtning av nuvarande nyckeltal (Yahoo Finance / Finnhub)

### ❌ Saknas (Fas 2 krav)
- Historisk data för nyckeltal (1/3/5 år)
- Datamodell för `MetricHistorySummary`
- API-integration för historisk data
- Lagring av historisk data i Room
- UI för historikvisning vid larm-skapande
- Presets baserat på historik
- Beräkning av min/max/snitt för historiska perioder

---

## Implementeringsplan

### Fas 2.1: Datamodell & lagring (Prioritet 1)

#### 1.1 Skapa MetricHistorySummary data class
**Fil**: `app/src/main/java/com/stockflip/MetricHistorySummary.kt`

```kotlin
data class MetricHistorySummary(
    val metricType: WatchType.MetricType,
    val symbol: String,
    val oneYear: PeriodSummary,
    val threeYear: PeriodSummary,
    val fiveYear: PeriodSummary,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PeriodSummary(
    val min: Double,
    val max: Double,
    val average: Double,
    val median: Double? = null // Optional för framtida utökning
)
```

#### 1.2 Skapa MetricHistoryEntity för Room
**Fil**: `app/src/main/java/com/stockflip/MetricHistoryEntity.kt`

```kotlin
@Entity(tableName = "metric_history")
data class MetricHistoryEntity(
    @PrimaryKey val id: String, // "SYMBOL_METRICTYPE"
    val symbol: String,
    val metricType: String, // WatchType.MetricType.name
    val date: Long, // Timestamp
    val value: Double
)
```

#### 1.3 Skapa MetricHistoryDao
**Fil**: `app/src/main/java/com/stockflip/MetricHistoryDao.kt`

**Metoder behövs**:
- `insertMetricHistory(history: MetricHistoryEntity)`
- `getMetricHistory(symbol: String, metricType: WatchType.MetricType, startDate: Long, endDate: Long): List<MetricHistoryEntity>`
- `getLatestHistory(symbol: String, metricType: WatchType.MetricType, days: Int): List<MetricHistoryEntity>`
- `deleteOldHistory(olderThan: Long)` - för cleanup

#### 1.4 Uppdatera StockPairDatabase
**Fil**: `app/src/main/java/com/stockflip/StockPairDatabase.kt`

- Lägg till `MetricHistoryEntity` i `@Database`
- Lägg till `metricHistoryDao()` i database-klassen

#### 1.5 Skapa MetricHistoryRepository
**Fil**: `app/src/main/java/com/stockflip/repository/MetricHistoryRepository.kt`

**Funktioner**:
- `getMetricHistorySummary(symbol: String, metricType: WatchType.MetricType): MetricHistorySummary?`
- `calculatePeriodSummary(history: List<MetricHistoryEntity>, periodDays: Int): PeriodSummary`
- `saveMetricHistory(symbol: String, metricType: WatchType.MetricType, value: Double)`

---

### Fas 2.2: API-integration för historisk data (Prioritet 1)

#### 2.1 Utvärdera datakällor
**Alternativ**:
- **Finnhub**: Har historisk fundamentals data (kräver premium för längre historik?)
- **Yahoo Finance**: Begränsad historisk fundamentals data
- **Alpha Vantage**: Har fundamentals men begränsad gratis tier
- **FMP (Financial Modeling Prep)**: Bra fundamentals API med historik

**Rekommendation**: Börja med Finnhub (redan integrerad), utvärdera om premium behövs.

#### 2.2 Utöka FinnhubService eller skapa ny service
**Fil**: `app/src/main/java/com/stockflip/FinnhubService.kt` eller ny fil

**Metoder behövs**:
- `getMetricHistory(symbol: String, metricType: WatchType.MetricType, years: Int): List<MetricHistoryData>`
- `fetchHistoricalMetrics(symbol: String, metricType: WatchType.MetricType): List<MetricHistoryData>`

**Data struktur**:
```kotlin
data class MetricHistoryData(
    val date: Long,
    val value: Double
)
```

#### 2.3 Implementera historik-hämtning
- Hämta kvartalsvisa/årliga värden för P/E, P/S, Dividend Yield
- Konvertera till dagliga snapshots (interpolera om nödvändigt)
- Spara i Room via MetricHistoryRepository

---

### Fas 2.3: Beräkning & sammanfattning (Prioritet 1)

#### 3.1 Implementera PeriodSummary-beräkning
**Fil**: `app/src/main/java/com/stockflip/repository/MetricHistoryRepository.kt`

**Funktioner**:
```kotlin
private fun calculatePeriodSummary(
    history: List<MetricHistoryEntity>,
    periodDays: Int
): PeriodSummary {
    val filtered = history.filter { 
        it.date >= (System.currentTimeMillis() - periodDays * 24 * 60 * 60 * 1000L)
    }
    
    if (filtered.isEmpty()) return PeriodSummary(0.0, 0.0, 0.0)
    
    val values = filtered.map { it.value }
    return PeriodSummary(
        min = values.minOrNull() ?: 0.0,
        max = values.maxOrNull() ?: 0.0,
        average = values.average(),
        median = values.sorted().let { 
            if (it.size % 2 == 0) {
                (it[it.size / 2 - 1] + it[it.size / 2]) / 2.0
            } else {
                it[it.size / 2]
            }
        }
    )
}
```

#### 3.2 Implementera MetricHistorySummary-generering
- Kombinera 1/3/5 års perioder
- Hantera saknad data gracefully
- Cache-resultat för prestanda

---

### Fas 2.4: UI för historikvisning (Prioritet 2)

#### 4.1 Uppdatera dialog för KeyMetrics
**Fil**: `app/src/main/res/layout/dialog_add_key_metrics.xml`

**Lägg till**:
- Sektion för historikvisning (1/3/5 år)
- Visning av nuvarande värde
- Visning av min/max/snitt för varje period
- Preset-knappar

**Layout-struktur**:
```xml
<!-- Nuvarande värde -->
<TextView>Nuvarande P/E: 22.5</TextView>

<!-- Historik -->
<TextView>1 år: snitt 22.7 (min 18.2 / max 27.9)</TextView>
<TextView>3 år: snitt 21.4 (min 15.0 / max 30.1)</TextView>
<TextView>5 år: snitt 19.8 (min 12.3 / max 34.2)</TextView>

<!-- Presets -->
<Button>Sätt larm under 5-årssnittet</Button>
<Button>Sätt larm under 3-årsmin</Button>
<Button>Sätt larm vid 1-årssnitt − 20%</Button>
```

#### 4.2 Uppdatera showAddKeyMetricsDialog() i MainActivity
**Fil**: `app/src/main/java/com/stockflip/MainActivity.kt`

**Funktionalitet**:
- Hämta MetricHistorySummary när dialog öppnas
- Visa historik i dialog
- Implementera preset-knappar
- Uppdatera targetValue baserat på preset

#### 4.3 Uppdatera showEditKeyMetricsDialog()
- Samma funktionalitet som add-dialog
- Visa historik även vid redigering

#### 4.4 Skapa Compose-komponent för historikvisning (valfritt)
**Fil**: `app/src/main/java/com/stockflip/ui/components/MetricHistoryView.kt`

För framtida Compose-migration.

---

### Fas 2.5: Presets & smarta förslag (Prioritet 2)

#### 5.1 Implementera preset-logik
**Fil**: `app/src/main/java/com/stockflip/ui/presets/MetricPresets.kt`

**Presets**:
```kotlin
object MetricPresets {
    fun getPresetValue(
        presetType: PresetType,
        history: MetricHistorySummary
    ): Double? {
        return when (presetType) {
            PresetType.BELOW_5_YEAR_AVG -> history.fiveYear.average * 0.95
            PresetType.BELOW_3_YEAR_MIN -> history.threeYear.min
            PresetType.ONE_YEAR_AVG_MINUS_20 -> history.oneYear.average * 0.80
            PresetType.BELOW_1_YEAR_MIN -> history.oneYear.min
            // ... fler presets
        }
    }
}

enum class PresetType {
    BELOW_5_YEAR_AVG,
    BELOW_3_YEAR_MIN,
    ONE_YEAR_AVG_MINUS_20,
    BELOW_1_YEAR_MIN,
    ABOVE_5_YEAR_MAX,
    // ...
}
```

#### 5.2 Integrera presets i UI
- Knappar i dialog som anropar preset-logik
- Uppdatera targetValue-input automatiskt
- Visa förklaring av preset (t.ex. "5-årssnittet = 19.8")

---

### Fas 2.6: Uppdateringspolicy (Prioritet 3)

#### 6.1 Implementera cache-policy
**Fil**: `app/src/main/java/com/stockflip/repository/MetricHistoryRepository.kt`

**Logik**:
- Uppdatera historik max 1 gång per dag per aktie
- Kontrollera `lastUpdated` i MetricHistorySummary
- Uppdatera vid öppning av detaljvy om cache är äldre än X timmar (t.ex. 24h)

#### 6.2 Lägg till WorkManager-jobb för nattlig uppdatering (valfritt)
**Fil**: `app/src/main/java/com/stockflip/workers/MetricHistoryUpdateWorker.kt`

- Kör nattligt (t.ex. 02:00)
- Uppdatera historik för alla aktier med KeyMetrics-bevakningar
- Begränsa till WiFi + laddning för batterioptimering

#### 6.3 Uppdatera StockPriceUpdater
- Inkludera historik-uppdatering i befintlig uppdateringscykel
- Eller separera till egen uppdateringscykel (längre intervall)

---

### Fas 2.7: UI-förbättringar (Prioritet 3)

#### 7.1 Förbättra MetricAlertCard
**Fil**: `app/src/main/java/com/stockflip/ui/components/cards/MetricAlertCard.kt`

**Möjliga förbättringar**:
- Visa "värde vid skapande" om det finns
- Visa trend-indikator (upp/ned jämfört med skapande)
- Färgkodning baserat på historisk kontext

#### 7.2 Lägg till historik-visning i StockDetailFragment
**Fil**: `app/src/main/java/com/stockflip/StockDetailFragment.kt`

- Visa historik för nyckeltal i detaljvyn
- Grafisk representation (valfritt, kan vara Fas 3)

---

## Tekniska överväganden

### Datakällor
1. **Finnhub** (nuvarande)
   - ✅ Redan integrerad
   - ❓ Kräver premium för längre historik?
   - ❓ Begränsad API-rate för gratis tier

2. **Yahoo Finance**
   - ✅ Gratis
   - ❌ Begränsad historisk fundamentals data
   - ❌ Ostrukturerad data

3. **Alpha Vantage**
   - ✅ Bra fundamentals API
   - ❌ Begränsad gratis tier (5 calls/min)
   - ✅ Har historisk data

4. **FMP (Financial Modeling Prep)**
   - ✅ Bra fundamentals med historik
   - ❌ Kräver betalning för historik

**Rekommendation**: Börja med Finnhub, utvärdera om premium behövs. Om inte, överväg Alpha Vantage som backup.

### Data-lagring strategi
- **Kvartalsvisa värden**: Spara kvartalsvisa fundamentals (P/E, P/S, yield)
- **Dagliga snapshots**: Interpolera eller använd senaste kvartalsvärde för dagliga snapshots
- **Cleanup**: Ta bort data äldre än 5 år automatiskt

### Prestanda
- **Lazy loading**: Ladda historik endast när dialog öppnas
- **Caching**: Cache MetricHistorySummary i minnet
- **Background updates**: Uppdatera historik i bakgrunden

---

## Testning

### Unit tests
- `MetricHistoryRepositoryTest.kt`
  - Testa PeriodSummary-beräkning
  - Testa MetricHistorySummary-generering
  - Testa cache-logik

- `MetricPresetsTest.kt`
  - Testa alla preset-typer
  - Testa edge cases (saknad data, etc.)

### Integration tests
- `MetricHistoryIntegrationTest.kt`
  - Testa API-integration
  - Testa Room-lagring
  - Testa end-to-end flow

---

## Tidsestimering

### Fas 2.1: Datamodell & lagring
- **Tid**: 4-6 timmar
- **Komplexitet**: Medel

### Fas 2.2: API-integration
- **Tid**: 6-8 timmar
- **Komplexitet**: Medel-Hög
- **Risk**: API-begränsningar kan kräva premium eller backup-API

### Fas 2.3: Beräkning & sammanfattning
- **Tid**: 3-4 timmar
- **Komplexitet**: Låg-Medel

### Fas 2.4: UI för historikvisning
- **Tid**: 6-8 timmar
- **Komplexitet**: Medel

### Fas 2.5: Presets & smarta förslag
- **Tid**: 4-6 timmar
- **Komplexitet**: Medel

### Fas 2.6: Uppdateringspolicy
- **Tid**: 3-4 timmar
- **Komplexitet**: Låg-Medel

### Fas 2.7: UI-förbättringar
- **Tid**: 4-6 timmar
- **Komplexitet**: Medel

**Total estimering**: 30-42 timmar

---

## Prioritering

### Måste ha (MVP)
1. ✅ Datamodell & lagring (2.1)
2. ✅ API-integration för historisk data (2.2)
3. ✅ Beräkning & sammanfattning (2.3)
4. ✅ UI för historikvisning (2.4)
5. ✅ Presets (2.5)

### Bör ha
6. Uppdateringspolicy (2.6)

### Nice to have
7. UI-förbättringar (2.7)

---

## Nästa steg

1. **Börja med Fas 2.1**: Skapa datamodell och Room-entities
2. **Utvärdera API**: Testa Finnhub för historisk data, bestäm om premium behövs
3. **Implementera stegvis**: Börja med P/E, expandera till P/S och Dividend Yield
4. **Testa tidigt**: Verifiera att historik-data hämtas korrekt innan UI-implementation

---

## Anteckningar

- **EPS**: PRD nämner EPS men det är inte implementerat i WatchType.MetricType ännu. Överväg att lägga till.
- **P/B**: PRD nämner P/B som "ev." - kan skippas i första iterationen.
- **Median**: PRD nämner median som "ev." - kan skippas initialt, lägg till senare om behövs.
- **Grafisk representation**: Kan vara nice-to-have för Fas 3 eller senare.

---

## Risker & begränsningar

1. **API-begränsningar**: Gratis APIs kan ha begränsad historik eller rate limits
2. **Datakvalitet**: Historisk data kan vara inkonsekvent mellan olika källor
3. **Prestanda**: Beräkning av 5 års historik kan vara långsam första gången
4. **Storage**: 5 års historik för många aktier kan ta mycket plats

**Mitigering**:
- Implementera effektiv caching
- Begränsa antal aktier med historik
- Cleanup av gammal data
- Lazy loading av historik

