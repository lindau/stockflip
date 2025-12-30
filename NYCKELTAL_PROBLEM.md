# Problem med Nyckeltal-hämtning

## Problembeskrivning

Yahoo Finance API:et returnerar **401 Unauthorized** för alla försök att hämta nyckeltal (P/E, P/S, Dividend Yield) via `quoteSummary`-endpointen.

### Vad som fungerar:
- ✅ ATH (All-Time High) - fungerar via chart API
- ✅ Aktiepriser - fungerar via chart API
- ✅ Aktiesökning - fungerar

### Vad som INTE fungerar:
- ❌ P/E-tal - 401-fel
- ❌ P/S-tal - 401-fel
- ❌ Utdelningsprocent - 401-fel

## Orsak

Yahoo Finance har ändrat sina API:er och `quoteSummary`-endpointen kräver nu autentisering (cookies/session), medan `chart`-endpointen fortfarande fungerar utan autentisering.

## Lösningar

### Alternativ 1: Använda alternativ datakälla (Rekommenderat)

#### Option A: Finnhub API (Gratis tier)
- **URL:** https://finnhub.io/
- **Gratis tier:** 60 API calls/minut
- **Stöd för:** P/E, P/S, Dividend Yield
- **Kräver:** API-nyckel (gratis registrering)

#### Option B: Alpha Vantage API
- **URL:** https://www.alphavantage.co/
- **Gratis tier:** 5 API calls/minut, 500 calls/dag
- **Stöd för:** Fundamental data inkl. P/E, P/S
- **Kräver:** API-nyckel (gratis registrering)

#### Option C: Polygon.io
- **URL:** https://polygon.io/
- **Gratis tier:** Begränsad
- **Stöd för:** Fundamental data
- **Kräver:** API-nyckel

### Alternativ 2: Implementera Cookie-hantering

Implementera session-hantering för Yahoo Finance:
1. Hämta cookies från Yahoo Finance webbsida
2. Använd cookies i API-anrop
3. Uppdatera cookies regelbundet

**Nackdelar:**
- Komplicerat att implementera
- Kan brytas vid ändringar i Yahoo Finance
- Kräver web scraping

### Alternativ 3: Temporär lösning (Nuvarande)

Visa "Ej tillgängligt" när nyckeltal inte kan hämtas.

**Fördelar:**
- Ingen extra kod
- Tydligt för användaren

**Nackdelar:**
- Funktionen fungerar inte

## Rekommendation

**Använd Finnhub API** eftersom:
1. Gratis tier är generös (60 calls/minut)
2. Enkel att implementera
3. Stabilt API
4. Bra dokumentation
5. Stöd för svenska aktier

## Implementering av Finnhub

### Steg 1: Registrera konto
1. Gå till https://finnhub.io/
2. Skapa gratis konto
3. Hämta API-nyckel

### Steg 2: Lägg till API-nyckel i appen
Lägg till i `local.properties`:
```
FINNHUB_API_KEY=din_api_nyckel_här
```

### Steg 3: Implementera FinnhubService
Skapa en ny service som hämtar nyckeltal från Finnhub API.

### Steg 4: Uppdatera YahooFinanceService
Lägg till fallback till Finnhub när Yahoo Finance misslyckas.

## Status

**Nuvarande status:** Nyckeltal fungerar INTE p.g.a. 401-fel från Yahoo Finance.

**Nästa steg:** Implementera alternativ datakälla (rekommenderat: Finnhub).

