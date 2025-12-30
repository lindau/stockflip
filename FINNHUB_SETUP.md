# Finnhub API Setup

## Steg 1: Skapa Finnhub-konto och hämta API-nyckel

1. Gå till https://finnhub.io/
2. Klicka på "Sign Up" eller "Get Free API Key"
3. Skapa ett konto (gratis)
4. Efter registrering, gå till din dashboard
5. Kopiera din API-nyckel

## Steg 2: Lägg till API-nyckel i projektet

1. Öppna filen `local.properties` i projektets rotkatalog
2. Lägg till följande rad:
   ```
   FINNHUB_API_KEY=din_api_nyckel_här
   ```
3. Ersätt `din_api_nyckel_här` med din faktiska API-nyckel från Finnhub

**Exempel:**
```
FINNHUB_API_KEY=c1234567890abcdefghijklmnopqrstuvwxyz
```

## Steg 3: Rebuild projektet

Efter att du lagt till API-nyckeln i `local.properties`:

1. I Android Studio: **File → Invalidate Caches / Restart**
2. Eller kör: `./gradlew clean build`

## Verifiering

När API-nyckeln är konfigurerad kommer systemet automatiskt att:
1. Först försöka hämta nyckeltal från Yahoo Finance
2. Om Yahoo Finance ger 401-fel, använda Finnhub som fallback
3. Logga meddelanden som visar vilken källa som används

## Loggmeddelanden att leta efter

### När Finnhub används:
```
YahooFinanceService: Failed to get response from all URLs, trying Finnhub as fallback
FinnhubService: Fetching PE_RATIO from Finnhub for symbol: VOLV-B.ST
FinnhubService: Successfully extracted PE_RATIO from Finnhub for VOLV-B.ST: 12.5
```

### Om API-nyckel saknas:
```
FinnhubService: Finnhub API key not configured. Please set FINNHUB_API_KEY in local.properties
```

## Finnhub API Limits (Gratis tier)

- **60 API calls per minut**
- **Unlimited calls per månad** (med vissa begränsningar)
- **Stöd för:** P/E, P/S, Dividend Yield och många fler nyckeltal

## Felsökning

### Problem: API-nyckel fungerar inte
- Kontrollera att API-nyckeln är korrekt i `local.properties`
- Kontrollera att du har rebuildat projektet efter att lagt till nyckeln
- Kontrollera loggen för felmeddelanden från Finnhub API

### Problem: Finnhub returnerar inga data
- Vissa aktier kanske inte har alla nyckeltal tillgängliga
- Kontrollera loggen för vilka keys som finns i svaret
- Vissa svenska aktier kan behöva annat symbolformat

## Ytterligare information

- Finnhub dokumentation: https://finnhub.io/docs/api
- API endpoint för nyckeltal: `/stock/metric`
- Support: https://finnhub.io/support

