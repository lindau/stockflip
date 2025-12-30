# Felsökning av Nyckeltal-hämtning

## Loggmeddelanden att leta efter

### 1. Start av hämtning
**Sök efter:**
```
MainViewModel: Fetching key metric PE_RATIO for [TICKER]
```
eller
```
YahooFinanceService: Fetching key metric PE_RATIO for symbol: [TICKER]
```

**Vad det betyder:** Hämtningen startar för den angivna tickern och nyckeltalstypen.

---

### 2. URL-försök
**Sök efter:**
```
YahooFinanceService: Trying URL: https://query1.finance.yahoo.com/v10/finance/quoteSummary/...
```
eller
```
YahooFinanceService: Trying URL: https://query2.finance.yahoo.com/v10/finance/quoteSummary/...
```

**Vad det betyder:** Systemet försöker hämta data från Yahoo Finance API.

**Möjliga problem:**
- Om du ser detta men inget mer = timeout eller nätverksfel
- Om du ser "API Error from [URL]: 401" = autentiseringsfel
- Om du ser "API Error from [URL]: 404" = tickern finns inte

---

### 3. API-fel (401 Unauthorized)
**Sök efter:**
```
YahooFinanceService: API Error from [URL]: 401 - 
```

**Vad det betyder:** Yahoo Finance API:et kräver autentisering för quoteSummary-endpointen.

**Lösning:** Detta är ett känt problem. Endpointen kräver cookies eller session-autentisering.

---

### 4. API-fel (andra felkoder)
**Sök efter:**
```
YahooFinanceService: API Error from [URL]: [KOD] - [MEDDELANDE]
```

**Vanliga felkoder:**
- **401** = Unauthorized (autentiseringsfel)
- **404** = Not Found (tickern finns inte)
- **429** = Too Many Requests (för många förfrågningar)
- **500** = Internal Server Error (serverfel)

---

### 5. Lyckat svar
**Sök efter:**
```
YahooFinanceService: Successfully got response from [URL]
YahooFinanceService: Response body length for [TICKER]: [SIZE]
```

**Vad det betyder:** API:et returnerade data. Om du ser detta men inget värde = problem med JSON-parsning.

---

### 6. JSON-parsningsproblem
**Sök efter:**
```
YahooFinanceService: No quoteSummary in response
```
eller
```
YahooFinanceService: No result in quoteSummary
```

**Vad det betyder:** API:et returnerade data, men JSON-strukturen är inte som förväntat.

**Lösning:** Kolla "Response body length" för att se om det finns data. Om längden är > 0 men quoteSummary saknas, kan det vara ett felmeddelande från API:et.

---

### 7. Värde hittades inte
**Sök efter:**
```
YahooFinanceService: Could not find PE_RATIO in response for [TICKER]. summaryDetail keys: [...], defaultKeyStatistics keys: [...]
```

**Vad det betyder:** API:et returnerade data, men det specifika nyckeltalet finns inte i svaret.

**Vad att göra:**
- Kolla vilka keys som finns i summaryDetail och defaultKeyStatistics
- Det kan vara att nyckeltalet heter något annat i API:et
- Vissa aktier har inte alla nyckeltal tillgängliga

---

### 8. Värde hittades
**Sök efter:**
```
YahooFinanceService: Successfully extracted PE_RATIO for [TICKER]: [VÄRDE]
```

**Vad det betyder:** Nyckeltalet hittades och extraherades korrekt.

---

### 9. Returnerade null
**Sök efter:**
```
MainViewModel: getKeyMetric returned: null for [TICKER]
MainViewModel: Could not get metric value for [TICKER] (returned null), keeping existing value: 0.0
```

**Vad det betyder:** Hämtningen misslyckades eller värdet finns inte för denna aktie.

---

### 10. Exception
**Sök efter:**
```
MainViewModel: Exception while fetching key metric for [TICKER]: [FELMEDDELANDE]
YahooFinanceService: Error fetching key metric PE_RATIO for [TICKER]: [FELMEDDELANDE]
```

**Vad det betyder:** Ett undantag uppstod under hämtningen.

**Vad att göra:** Kolla stack trace för att se var felet uppstod.

---

## Vanliga problem och lösningar

### Problem 1: 401 Unauthorized
**Symptom:** Alla försök ger 401-fel

**Orsak:** quoteSummary-endpointen kräver autentisering

**Möjliga lösningar:**
1. Använd en annan datakälla för nyckeltal
2. Implementera cookie-hantering
3. Använd en scraping-lösning

### Problem 2: Värde finns inte i JSON
**Symptom:** "Could not find PE_RATIO in response" med lista över tillgängliga keys

**Orsak:** JSON-strukturen är annorlunda än förväntat, eller nyckeltalet finns inte för denna aktie

**Lösning:** Kolla vilka keys som faktiskt finns och uppdatera koden för att söka efter rätt fält

### Problem 3: Timeout
**Symptom:** Inga loggmeddelanden efter "Trying URL"

**Orsak:** Nätverksfördröjning eller API:et svarar inte

**Lösning:** Öka timeout-värdet eller försök igen senare

---

## Exempel på en lyckad hämtning

```
MainViewModel: Fetching key metric PE_RATIO for VOLV-B.ST
YahooFinanceService: Fetching key metric PE_RATIO for symbol: VOLV-B.ST
YahooFinanceService: Trying URL: https://query1.finance.yahoo.com/v10/finance/quoteSummary/VOLV-B.ST?modules=summaryDetail,defaultKeyStatistics
YahooFinanceService: Successfully got response from https://query1.finance.yahoo.com/v10/finance/quoteSummary/VOLV-B.ST?modules=summaryDetail,defaultKeyStatistics
YahooFinanceService: Response body length for VOLV-B.ST: 1234
YahooFinanceService: Successfully extracted PE_RATIO for VOLV-B.ST: 12.5
MainViewModel: getKeyMetric returned: 12.5 for VOLV-B.ST
MainViewModel: Got metric value for VOLV-B.ST: 12.5
MainViewModel: Updated database with new metric value for VOLV-B.ST: 12.5
```

---

## Exempel på misslyckad hämtning (401)

```
MainViewModel: Fetching key metric PE_RATIO for VOLV-B.ST
YahooFinanceService: Fetching key metric PE_RATIO for symbol: VOLV-B.ST
YahooFinanceService: Trying URL: https://query1.finance.yahoo.com/v10/finance/quoteSummary/VOLV-B.ST?modules=summaryDetail,defaultKeyStatistics
YahooFinanceService: API Error from https://query1.finance.yahoo.com/v10/finance/quoteSummary/VOLV-B.ST?modules=summaryDetail,defaultKeyStatistics: 401 - 
YahooFinanceService: Trying URL: https://query2.finance.yahoo.com/v10/finance/quoteSummary/VOLV-B.ST?modules=summaryDetail,defaultKeyStatistics
YahooFinanceService: API Error from https://query2.finance.yahoo.com/v10/finance/quoteSummary/VOLV-B.ST?modules=summaryDetail,defaultKeyStatistics: 401 - 
YahooFinanceService: Failed to get response from all URLs
MainViewModel: getKeyMetric returned: null for VOLV-B.ST
MainViewModel: Could not get metric value for VOLV-B.ST (returned null), keeping existing value: 0.0
```

---

## Hur du filtrerar loggen

### Android Studio Logcat
1. Öppna Logcat
2. I filter-fältet, skriv: `YahooFinanceService|MainViewModel`
3. Välj nivå: `Debug` eller `All`

### ADB (kommandorad)
```bash
adb logcat | grep -E "YahooFinanceService|MainViewModel"
```

### Specifik ticker
```bash
adb logcat | grep -E "YahooFinanceService|MainViewModel" | grep "VOLV-B.ST"
```

---

## Nästa steg

Om du ser 401-fel kontinuerligt:
1. Detta är ett känt problem med Yahoo Finance API:et
2. quoteSummary-endpointen kräver autentisering
3. Vi behöver antingen:
   - Hitta en annan datakälla
   - Implementera cookie/session-hantering
   - Använda en scraping-lösning

Om du ser att värden inte hittas i JSON:
1. Kolla vilka keys som faktiskt finns i loggen
2. Uppdatera koden för att söka efter rätt fältnamn
3. Vissa aktier har inte alla nyckeltal tillgängliga

