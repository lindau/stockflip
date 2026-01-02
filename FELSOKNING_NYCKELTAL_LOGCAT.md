# Felsökning: Nyckeltal laddas inte

## Logcat-sökord att använda

### 1. Filtrera på relevanta taggar
```bash
adb logcat | grep -E "MainViewModel|YahooFinanceService|FinnhubService|KeyMetrics"
```

### 2. Specifika sökord att leta efter

#### A. När nyckeltal ska hämtas (MainViewModel)
- **Sök efter**: `"Fetching key metric"`
- **Exempel**: `"Fetching key metric PE_RATIO for VOLV-B.ST"`
- **Vad det betyder**: Appen försöker hämta nyckeltal för en aktie

#### B. Yahoo Finance försök (YahooFinanceService)
- **Sök efter**: `"Fetching key metric.*from Yahoo Finance"`
- **Exempel**: `"Fetching key metric PE_RATIO for symbol: VOLV-B.ST from Yahoo Finance"`
- **Vad det betyder**: Försöker hämta från Yahoo Finance först

#### C. Yahoo Finance fel
- **Sök efter**: `"Error fetching key metric.*from Yahoo"` eller `"Yahoo Finance API Error"`
- **Exempel**: 
  - `"Error fetching key metric PE_RATIO for VOLV-B.ST from Yahoo: ..."`
  - `"Yahoo Finance API Error: 401 - Unauthorized"`
- **Vad det betyder**: Yahoo Finance misslyckades, appen faller tillbaka till Finnhub

#### D. Finnhub fallback
- **Sök efter**: `"Falling back to Finnhub"`
- **Exempel**: `"Falling back to Finnhub for key metric PE_RATIO for symbol: VOLV-B.ST"`
- **Vad det betyder**: Appen använder Finnhub som backup

#### E. Finnhub resultat
- **Sök efter**: `"Finnhub returned"` eller `"Error fetching key metric.*from Finnhub"`
- **Exempel**:
  - `"Finnhub returned null for PE_RATIO for VOLV-B.ST"` (null = inget värde hittades)
  - `"Error fetching key metric PE_RATIO for VOLV-B.ST from Finnhub: ..."` (fel)
- **Vad det betyder**: Finnhub-svaret (lyckat eller misslyckat)

#### F. Slutresultat (MainViewModel)
- **Sök efter**: `"getKeyMetric returned"` eller `"Got metric value"` eller `"Could not get metric value"`
- **Exempel**:
  - `"getKeyMetric returned: 15.5 for VOLV-B.ST"` (lyckat)
  - `"Got metric value for VOLV-B.ST: 15.5"` (uppdaterat i databas)
  - `"Could not get metric value for VOLV-B.ST (returned null), keeping existing value: 0.0"` (misslyckat)
- **Vad det betyder**: Slutresultatet av hämtningen

#### G. Undantag och fel
- **Sök efter**: `"Exception while fetching key metric"` eller `"Ticker is null"`
- **Exempel**:
  - `"Exception while fetching key metric for VOLV-B.ST: ..."`
  - `"Ticker is null for KeyMetrics watch item 123"`
- **Vad det betyder**: Kritiska fel som stoppar hämtningen

## Vanliga problem och lösningar

### Problem 1: Finnhub API-nyckel saknas
**Symptom i logcat:**
```
Falling back to Finnhub for key metric PE_RATIO for symbol: VOLV-B.ST
Finnhub returned null for PE_RATIO for VOLV-B.ST
```
**Lösning**: Lägg till `FINNHUB_API_KEY=din_nyckel_här` i `local.properties`

### Problem 2: Yahoo Finance kräver autentisering
**Symptom i logcat:**
```
Yahoo Finance API Error: 401 - Unauthorized
Falling back to Finnhub...
```
**Lösning**: Detta är normalt - appen använder automatiskt Finnhub som backup

### Problem 3: Symbol hittas inte
**Symptom i logcat:**
```
Error fetching key metric PE_RATIO for INVALID.SYMBOL from Yahoo: ...
Falling back to Finnhub...
Finnhub returned null for PE_RATIO for INVALID.SYMBOL
```
**Lösning**: Kontrollera att symbolen är korrekt (t.ex. "VOLV-B.ST" för Volvo)

### Problem 4: Ticker är null
**Symptom i logcat:**
```
Ticker is null for KeyMetrics watch item 123
```
**Lösning**: Detta är ett databasproblem - watch item saknar ticker. Ta bort och skapa om bevakningen.

### Problem 5: Rate limiting
**Symptom i logcat:**
```
Fetching key metric PE_RATIO for VOLV-B.ST
[1 sekund delay]
Fetching key metric PS_RATIO for EVO.ST
```
**Lösning**: Appen har redan 1 sekunds delay mellan requests. Om du ser många fel kan det vara rate limiting från API:erna.

## Hur man kör logcat i Android Studio

### Metod 1: Använd Android Studio Terminal (Rekommenderat)

1. **Öppna Terminal-fönstret:**
   - Gå till: **View → Tool Windows → Terminal** (eller tryck `Alt+F12` på Windows/Linux, `Option+F12` på Mac)
   - Terminal-fönstret öppnas längst ner i Android Studio

2. **Kör logcat-kommandot:**
   - Kopiera och klistra in ett av kommandona nedan i terminalen
   - Tryck Enter

3. **För att se alla loggar från appen:**
```bash
adb logcat -c && adb logcat | grep -E "YahooFinanceService|MainViewModel"
```

4. **För att se endast fel och varningar:**
```bash
adb logcat *:E | grep -E "YahooFinanceService|MainViewModel"
```

5. **För att se allt relaterat till nyckeltal:**
```bash
adb logcat -s MainViewModel:D YahooFinanceService:D FinnhubService:D *:E | grep -i "key\|metric\|nyckel\|finnhub"
```

### Metod 2: Använd Logcat-fönstret (Visuellt)

1. **Öppna Logcat-fönstret:**
   - Gå till: **View → Tool Windows → Logcat** (eller tryck `Alt+6`)
   - Logcat-fönstret öppnas längst ner i Android Studio

2. **Filtrera loggar:**
   - I filterfältet (överst i Logcat-fönstret), skriv: `YahooFinanceService` eller `MainViewModel`
   - Du kan också välja log level (Verbose, Debug, Info, Warn, Error) från dropdown-menyn

3. **Rensa loggar:**
   - Klicka på "Clear" (rensa-ikonen) för att rensa gamla loggar
   - Starta om appen för att se nya loggar

### Metod 3: Använd extern terminal/kommandorad

Om du föredrar att använda en extern terminal (t.ex. Terminal på Mac eller Command Prompt på Windows):

**På Mac (om adb inte är i PATH):**

1. Öppna terminal/kommandorad
2. Navigera till projektmappen (valfritt)
3. Kör kommandot med fullständig sökväg:
```bash
~/Library/Android/sdk/platform-tools/adb logcat -c && ~/Library/Android/sdk/platform-tools/adb logcat | grep -E "YahooFinanceService|MainViewModel"
```

**Eller lägg till adb i PATH (för denna session):**
```bash
export PATH=$PATH:~/Library/Android/sdk/platform-tools
adb logcat -c && adb logcat | grep -E "YahooFinanceService|MainViewModel"
```

**På Windows:**
```bash
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe logcat -c && %LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe logcat | findstr "YahooFinanceService MainViewModel"
```

**OBS:** Om du får "command not found", använd Metod 2 (Logcat-fönstret) istället - det är enklare!

## Komplett logcat-kommando

För att se allt relaterat till nyckeltal:
```bash
adb logcat -s MainViewModel:D YahooFinanceService:D FinnhubService:D *:E | grep -i "key\|metric\|nyckel\|finnhub"
```

Eller för att se allt från appen:
```bash
adb logcat | grep -E "com.stockflip|MainViewModel|YahooFinanceService|FinnhubService"
```

## Steg-för-steg felsökning

1. **Kontrollera att nyckeltal-bevakning skapas:**
   - Leta efter: `"Nyckeltalsbevakning tillagd"` eller `"Nyckeltalsbevakning uppdaterad"`

2. **Kontrollera att refresh körs:**
   - Leta efter: `"Refreshing watch items"` eller `"Found X watch items to refresh"`

3. **Kontrollera att KeyMetrics items hittas:**
   - Leta efter: `"Fetching key metric"` - om detta saknas, finns inga KeyMetrics items

4. **Kontrollera API-anrop:**
   - Leta efter: `"Fetching key metric.*from Yahoo Finance"` eller `"Falling back to Finnhub"`

5. **Kontrollera resultat:**
   - Leta efter: `"getKeyMetric returned"` - om detta är null, misslyckades hämtningen

6. **Kontrollera databasuppdatering:**
   - Leta efter: `"Updated database with new metric value"` - om detta saknas, uppdaterades inte databasen

## Debug-läge

För mer detaljerad logging, sätt log level till DEBUG:
```bash
adb logcat *:D | grep -E "MainViewModel|YahooFinanceService|FinnhubService"
```

