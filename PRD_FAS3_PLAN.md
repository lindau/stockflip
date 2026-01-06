# PRD Fas 3 - Implementeringsplan
## Regelmotor / Kombinerade larm

**Status**: ✅ Fullt implementerat  
**Startdatum**: 2024  
**Slutdatum**: 2024  
**Mål**: Implementera regelmotor för att kombinera flera alert-villkor med AND/OR/NOT

---

## Översikt

Fas 3 fokuserar på att möjliggöra kombinerade larm där flera villkor kan kombineras med logiska operatorer (AND, OR, NOT). Detta ger användaren möjlighet att skapa mer avancerade bevakningar som "pris 20% under 52w high OCH P/E < 20".

---

## Nuvarande status

### ✅ Redan implementerat (från Fas 1-2)
- `AlertRule` sealed class med alla typer av regler
- `AlertEvaluator.evaluate()` för att utvärdera enskilda regler
- `MarketSnapshot` för marknadsdata
- UI för att skapa enskilda bevakningar

### ❌ Saknas (Fas 3 krav)
- `AlertExpression` sealed class för att uttrycka kombinationer
- `evaluateExpression()` funktion för att utvärdera uttryck
- Datamodell för att spara kombinerade larm
- UI för att skapa kombinerade larm (kan göras senare)

---

## Implementeringsplan

### Fas 3.1: AlertExpression datamodell (Prioritet 1)

#### 1.1 Skapa AlertExpression sealed class
**Fil**: `app/src/main/java/com/stockflip/AlertExpression.kt`

```kotlin
sealed class AlertExpression {
    data class Single(val rule: AlertRule) : AlertExpression()
    data class And(val left: AlertExpression, val right: AlertExpression) : AlertExpression()
    data class Or(val left: AlertExpression, val right: AlertExpression) : AlertExpression()
    data class Not(val inner: AlertExpression) : AlertExpression()
}
```

#### 1.2 Skapa ExpressionEvaluator
**Fil**: `app/src/main/java/com/stockflip/ExpressionEvaluator.kt`

**Funktioner**:
- `evaluateExpression(expr: AlertExpression, snapshots: Map<String, MarketSnapshot>): Boolean`
- Hantera Single, And, Or, Not
- Samla alla symboler från uttrycket
- Använd AlertEvaluator för Single-regler

---

### Fas 3.2: Integration med WatchItem (Prioritet 1)

#### 2.1 Utöka WatchType
**Fil**: `app/src/main/java/com/stockflip/WatchType.kt`

**Lägg till**:
```kotlin
data class Combined(
    val expression: AlertExpression
) : WatchType()
```

#### 2.2 Uppdatera WatchTypeConverter
**Fil**: `app/src/main/java/com/stockflip/WatchTypeConverter.kt`

- Lägg till serialisering/deserialisering för Combined
- Konvertera AlertExpression till/från String

#### 2.3 Uppdatera AlertRuleConverter
**Fil**: `app/src/main/java/com/stockflip/AlertRuleConverter.kt`

- Hantera Combined WatchType
- Konvertera till AlertExpression för evaluering

---

### Fas 3.3: Evaluering & integration (Prioritet 1)

#### 3.1 Implementera ExpressionEvaluator
**Fil**: `app/src/main/java/com/stockflip/ExpressionEvaluator.kt`

**Funktionalitet**:
- Extrahera alla symboler från AlertExpression
- Hämta MarketSnapshot för varje symbol
- Utvärdera uttrycket rekursivt
- Använd AlertEvaluator för Single-regler

#### 3.2 Integrera med StockPriceUpdater
**Fil**: `app/src/main/java/com/stockflip/StockPriceUpdateWorker.kt`

- Utvärdera Combined WatchItems
- Använd ExpressionEvaluator för kombinerade larm

---

### Fas 3.4: UI för att skapa kombinerade larm (Prioritet 2)

#### 4.1 Skapa dialog för kombinerade larm
**Fil**: `app/src/main/res/layout/dialog_add_combined_alert.xml`

**UI-komponenter**:
- Lista med villkor
- Knapp för att lägga till villkor
- Välj kombinationsoperator (AND/OR)
- Förhandsvisning av uttrycket

#### 4.2 Implementera builder-pattern för uttryck
**Fil**: `app/src/main/java/com/stockflip/ui/builders/ExpressionBuilder.kt`

- Helper-klass för att bygga AlertExpression från UI
- Validering av uttryck

#### 4.3 Uppdatera MainActivity
**Fil**: `app/src/main/java/com/stockflip/MainActivity.kt`

- Lägg till "Skapa kombinerat larm" i meny
- Implementera dialog för att skapa kombinerade larm

---

### Fas 3.5: UI för att visa kombinerade larm (Prioritet 2)

#### 5.1 Skapa CombinedAlertCard
**Fil**: `app/src/main/java/com/stockflip/ui/components/cards/CombinedAlertCard.kt`

- Visa kombinerat larm i listan
- Visa alla villkor och operatorer
- Visa status (triggad/ej triggad)

#### 5.2 Uppdatera WatchItemAdapter
**Fil**: `app/src/main/java/com/stockflip/WatchItemAdapter.kt`

- Lägg till bindCombined() metod
- Visa kombinerade larm i XML-layout

---

## Tekniska överväganden

### Datamodell
- AlertExpression är rekursiv (kan innehålla andra AlertExpression)
- Behöver serialisering för att spara i Room
- Kan använda JSON eller custom string-format

### Evaluering
- Rekursiv evaluering av uttryck
- Samla alla symboler först, hämta data, sedan evaluera
- Caching av MarketSnapshot för prestanda

### UI-komplexitet
- UI kan vara komplex för att bygga uttryck
- Kan börja med enkel version (2-3 villkor)
- Utöka senare med mer avancerad builder

---

## Tidsestimering

### Fas 3.1: AlertExpression datamodell
- **Tid**: 2-3 timmar
- **Komplexitet**: Låg-Medel

### Fas 3.2: Integration med WatchItem
- **Tid**: 3-4 timmar
- **Komplexitet**: Medel

### Fas 3.3: Evaluering & integration
- **Tid**: 4-6 timmar
- **Komplexitet**: Medel-Hög

### Fas 3.4: UI för att skapa kombinerade larm
- **Tid**: 8-12 timmar
- **Komplexitet**: Hög

### Fas 3.5: UI för att visa kombinerade larm
- **Tid**: 4-6 timmar
- **Komplexitet**: Medel

**Total estimering**: 21-31 timmar

---

## Prioritering

### Måste ha (MVP)
1. ✅ AlertExpression datamodell (3.1)
2. ✅ ExpressionEvaluator (3.3)
3. ✅ Integration med WatchItem (3.2)
4. ✅ Evaluering i StockPriceUpdater (3.3)

### Bör ha
5. UI för att skapa kombinerade larm (3.4)
6. UI för att visa kombinerade larm (3.5)

---

## Nästa steg

1. **Börja med Fas 3.1**: Skapa AlertExpression datamodell
2. **Fortsätt med Fas 3.2**: Integrera med WatchItem
3. **Implementera Fas 3.3**: ExpressionEvaluator och integration
4. **UI senare**: Kan implementeras efter att kärnfunktionaliteten fungerar

---

## Anteckningar

- **Serialisering**: AlertExpression behöver serialiseras för att sparas i Room. Överväg JSON eller custom format.
- **UI-komplexitet**: UI för att bygga uttryck kan vara komplex. Börja enkelt och utöka.
- **Prestanda**: Rekursiv evaluering kan vara kostsam. Överväg caching och optimering.
- **Testning**: Skriv unit tests för ExpressionEvaluator för att säkerställa korrekt evaluering.

