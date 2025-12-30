# Felsökning: Nyckeltal laddar inte

## ✅ Vad som är fixat

1. **Förbättrad loggning** - Mer detaljerad loggning för att se vad som händer
2. **Flera fältnamn** - Försöker flera varianter av fältnamn från Finnhub API
3. **Bättre API-nyckel-hantering** - Loggar om API-nyckeln är korrekt laddad

## 🔍 Steg för att felsöka

### 1. Verifiera API-nyckel

Kolla i `local.properties` att API-nyckeln är korrekt:
```
FINNHUB_API_KEY=d59sf8hr01qu56multf0d59sf8hr01qu56multfg
```

### 2. Rebuild projektet

Efter att ha ändrat `local.properties` måste du rebuilda:
```bash
./gradlew clean assembleDebug
```

Eller i Android Studio:
- **Build → Clean Project**
- **Build → Rebuild Project**

### 3. Kolla loggen

När du kör appen, sök efter dessa taggar i logcat:

**FinnhubService:**
- `API key loaded from BuildConfig` - Bekräftar att API-nyckeln laddades
- `Fetching PE_RATIO from Finnhub` - Visar att förfrågan görs
- `Finnhub response received` - Visar att svar mottogs
- `Successfully extracted` - Visar att värdet hittades
- `Available keys in metric:` - Visar vilka fält som finns i svaret

**YahooFinanceService:**
- `Using Finnhub directly for key metrics` - Bekräftar att Finnhub används
- `Finnhub returned` - Visar resultatet

**MainViewModel:**
- `Fetching key metric` - Visar att förfrågan görs
- `getKeyMetric returned` - Visar resultatet
- `Got metric value` - Visar att värdet sparades

### 4. Vanliga problem och lösningar

#### Problem: "API key not configured"
**Lösning:**
1. Verifiera att `FINNHUB_API_KEY` finns i `local.properties`
2. Rebuild projektet
3. Kolla loggen för "API key loaded from BuildConfig"

#### Problem: "Finnhub API Error: 401"
**Lösning:**
- API-nyckeln är ogiltig eller har gått ut
- Hämta ny API-nyckel från https://finnhub.io/
- Uppdatera `local.properties` och rebuild

#### Problem: "No 'metric' object in Finnhub response"
**Lösning:**
- Aktien finns kanske inte i Finnhub
- Kolla "Response preview" i loggen för att se vad API:et returnerar
- Prova en annan aktie som definitivt finns (t.ex. VOLV-B.ST, EVO.ST)

#### Problem: "Could not find PE_RATIO in Finnhub response"
**Lösning:**
- Aktien har kanske inte P/E-tal (t.ex. om den är förlustgivande)
- Kolla "Available keys in metric:" i loggen för att se vilka fält som finns
- Prova en annan aktie eller ett annat nyckeltal

#### Problem: "All symbol variants failed"
**Lösning:**
- Symbolformatet kanske inte stämmer
- Kolla loggen för vilka varianter som testas
- Prova att lägga till aktien med olika symbolformat

### 5. Testa direkt med Finnhub API

Du kan testa API:et direkt med curl:

```bash
curl "https://finnhub.io/api/v1/stock/metric?symbol=VOLV-B.ST&metric=all&token=d59sf8hr01qu56multf0d59sf8hr01qu56multfg"
```

Detta bör returnera JSON med metric-objektet.

### 6. Kolla API-begränsningar

Finnhub gratis tier har:
- 60 calls/minut
- Om du överskrider detta får du 429-fel

Kolla loggen för "429 Too Many Requests".

## 📝 Exempel på korrekt loggning

När allt fungerar bör du se:

```
FinnhubService: API key loaded from BuildConfig, length: 43, first 5 chars: d59sf...
YahooFinanceService: Fetching key metric PE_RATIO for symbol: VOLV-B.ST
YahooFinanceService: Using Finnhub directly for key metrics
FinnhubService: Fetching PE_RATIO from Finnhub for symbol: VOLV-B.ST (trying variants: [VOLV-B.ST, VOLV-B])
FinnhubService: Calling Finnhub API: https://finnhub.io/api/v1/stock/metric?symbol=VOLV-B.ST&metric=all&token=***
FinnhubService: Finnhub response received for VOLV-B.ST (variant: VOLV-B.ST), length: 1234
FinnhubService: Successfully extracted PE_RATIO from Finnhub for VOLV-B.ST (variant: VOLV-B.ST): 15.5
YahooFinanceService: Finnhub returned PE_RATIO for VOLV-B.ST: 15.5
MainViewModel: Got metric value for VOLV-B.ST: 15.5
```

## 🐛 Om problemet kvarstår

1. **Kopiera hela loggen** från när du försöker ladda nyckeltal
2. **Kolla specifikt efter:**
   - Finns "API key loaded"?
   - Finns "Fetching ... from Finnhub"?
   - Finns "Finnhub response received"?
   - Vad står det i "Available keys in metric:"?
   - Finns några felmeddelanden?

3. **Testa med en känd aktie:**
   - VOLV-B.ST (Volvo)
   - EVO.ST (Evolution Gaming)
   - ASSA-B.ST (Assa Abloy)

4. **Verifiera API-nyckel:**
   - Gå till https://finnhub.io/dashboard
   - Kolla att API-nyckeln är aktiv
   - Testa med curl (se ovan)

