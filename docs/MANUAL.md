# StockFlip — Användarhandbok

StockFlip låter dig bevaka aktier och kryptovalutor och få notiser när dina egna villkor uppfylls. Den här guiden förklarar hur du skapar och hanterar bevakningar.

## Innehållsförteckning

- [Ordlista](#ordlista)
- [Hur appen fungerar i bakgrunden](#hur-appen-fungerar-i-bakgrunden)
- [Navigering i appen](#navigering-i-appen)
- [Bevakningstyperna](#bevakningstyperna)
  - [1. Prismål](#1-prismal-prisbevakning)
  - [2. Dagsrörelse](#2-dagsrorelse)
  - [3. 52-veckorshögsta](#3-52-veckorshogsta-drawdown)
  - [4. Nyckeltal](#4-nyckeltal)
  - [5. Aktiepar](#5-aktiepar)
  - [6. Prisintervall](#6-prisintervall)
  - [7. Kombinerat larm](#7-kombinerat-larm)
- [Vanliga flöden](#vanliga-floden)
- [Hantera dina bevakningar](#hantera-dina-bevakningar)
- [Notiser](#notiser)
- [Tips och vanliga frågor](#tips-och-vanliga-fragor)

---

## Ordlista

| Term | Förklaring |
|------|------------|
| **Bevakning** | En regel du sätter upp för en aktie eller ett aktiepar. Appen kontrollerar villkoret löpande och skickar en notis när det uppfylls. |
| **Utlösning** (triggad) | Bevakningens villkor har uppfyllts. Appen skickade en notis och märkte bevakningen som triggad. |
| **Engångslarm** | En bevakning som inaktiveras automatiskt när den utlöses. Måste återaktiveras manuellt för att kunna utlösas igen. |
| **Återkommande larm** | En bevakning som kan utlösas igen nästa handelsdag utan att du behöver göra något. |
| **Ny-märke** | En "Ny"-etikett som visas på bevakningskort för utlösningar du ännu inte har sett. Försvinner när du öppnar detaljvyn för den berörda aktien eller paret. |
| **Dagsrörelse** | Hur mycket aktiens pris förändrats i procent sedan föregående stängningskurs. |
| **Drawdown** | Hur mycket aktiens pris har fallit från sin 52-veckorshögsta kurs, antingen i procent eller i kronor. |
| **52-veckorshögsta** | Det högsta priset aktien handlades på under de senaste 52 veckorna. |
| **Nyckeltal** | Finansiella mått som P/E-tal, P/S-tal och direktavkastning. |
| **P/E-tal** | Aktiekursen delat med vinst per aktie. Högt P/E = dyr värdering; lågt P/E = billig värdering. |
| **P/S-tal** | Aktiekursen delat med omsättning per aktie. |
| **Direktavkastning** | Utdelningen per aktie delat med aktiekursen, i procent. |
| **Aktiepar** | En bevakning som jämför prisskillnaden mellan två aktier. |
| **Kombinerat larm** | En bevakning som kombinerar flera villkor med logiska operatorer (OCH/ELLER/INTE). |

---

## Hur appen fungerar i bakgrunden

StockFlip hämtar aktuella kurser automatiskt:

- **Under börsens öppetider:** en gång per minut
- **Utanför börsens öppetider:** var 60:e minut

Notiser skickas direkt när ett villkor uppfylls. Trycker du på notisen öppnas appen och du hamnar direkt på aktiedetaljvyn för den berörda aktien.

### Börser och öppettider (lokal tid)

| Börs | Öppettider |
|------|------------|
| Stockholm (OMX) | 09:00–17:30 |
| NASDAQ / NYSE (USA) | 09:30–16:00 Eastern |
| London (LSE) | 08:00–16:30 |
| Frankfurt (XETRA) | 09:00–17:30 |
| Tokyo (TSE) | 09:00–15:00 |
| Oslo (OSE) | 09:00–16:25 |
| Krypto | Alltid öppet |

---

## Navigering i appen

Appen har tre flikar längst ned:

- **Aktier** — alla dina bevakningar, grupperade per bolag
- **Par** — enbart aktiepar-bevakningar
- **Bevakningar** — alla bevakningar samlade i en lista

I nuvarande appversion skapas nya bevakningar främst så här:

- **Aktier:** tryck på `+`, sök fram en aktie eller krypto och öppna detaljvyn. Där kan du skapa prismål, dagsrörelse, drawdown och nyckeltal.
- **Par:** gå till fliken **Par** och tryck på `+` för att skapa ett aktiepar.
- **Bevakningar:** används för att överblicka, redigera, aktivera/inaktivera och ta bort befintliga bevakningar.

---

## Bevakningstyperna

### 1. Prismål (Prisbevakning)

**Vad det gör:** Skickar en notis när aktiens pris når ett målpris du sätter.

**Typ:** Engångslarm — inaktiveras automatiskt när det utlöses.

**Riktning:** Bestäms automatiskt när du sparar bevakningen.
- Om nuvarande pris är *högre* än målpriset → väntar på att priset ska falla **under** målet.
- Om nuvarande pris är *lägre* än målpriset → väntar på att priset ska stiga **över** målet.

**Skapa en prismålsbevakning:**
1. Sök upp aktien eller tryck på en befintlig bevakningskort för att öppna aktiedetaljvyn.
2. Tryck på **Prisbevakning** i snabbåtgärdspanelen.
3. Ange målpriset.
4. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Bevakningen märks som "Triggad" med datum.
- Bevakningen inaktiveras — du måste trycka **Återaktivera** för att sätta upp larmet igen.

---

### 2. Dagsrörelse

**Vad det gör:** Skickar en notis när aktien rör sig mer än ett angivet antal procent under handelsdagen.

**Typ:** Återkommande — kan utlösas igen nästa handelsdag.

**Riktning:**
- **Upp** — utlöses om daglig förändring ≥ +X %
- **Ned** — utlöses om daglig förändring ≤ −X %
- **Båda håll** — utlöses om |daglig förändring| ≥ X %

**Skapa en dagsrörelsebevakning:**
1. Öppna aktiedetaljvyn.
2. Tryck på **Dagsrörelse**.
3. Ange tröskelprocent (t.ex. 5).
4. Välj riktning: Upp / Ned / Båda håll.
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Bevakningen visas som triggad med datum.
- Återställs automatiskt nästa dag.

---

### 3. 52-veckorshögsta (Drawdown)

**Vad det gör:** Skickar en notis när aktien har fallit ett visst belopp eller en viss procent från sin 52-veckorshögsta kurs.

**Typ:** Engångslarm — inaktiveras automatiskt när det utlöses.

**Välj mättyp:**
- **Procent** — t.ex. "varna om aktien fallit 15 % från toppen"
- **Kronor** — t.ex. "varna om aktien fallit 50 kr från toppen"

**Skapa en drawdown-bevakning:**
1. Öppna aktiedetaljvyn (visar aktuell drawdown i headern).
2. Tryck på **52-veckorshögsta**.
3. Välj Procent eller Kronor.
4. Ange hur mycket nedgång som ska trigga larmet.
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Bevakningen inaktiveras — tryck **Återaktivera** för att sätta upp det igen.

---

### 4. Nyckeltal

**Vad det gör:** Skickar en notis när ett finansiellt nyckeltal (P/E, P/S eller direktavkastning) når ett målvärde.

**Typ:** Återkommande — kan utlösas igen nästa handelsdag.

**Tillgängliga nyckeltal:**
- **P/E-tal** — värderingsmått baserat på vinst
- **P/S-tal** — värderingsmått baserat på omsättning
- **Direktavkastning** — utdelning i procent av kursen

**Riktning:** Bestäms automatiskt när du sparar bevakningen (samma logik som Prismål).

**Skapa en nyckeltalbevakning:**
1. Öppna aktiedetaljvyn.
2. Tryck på **Nyckeltal**.
3. Välj vilket nyckeltal (P/E, P/S, Direktavkastning).
4. Ange målvärdet.
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Återställs automatiskt nästa dag.

---

### 5. Aktiepar

**Vad det gör:** Bevakar prisskillnaden mellan två aktier och skickar en notis när skillnaden når ett visst värde, eller när priserna är lika.

**Typ:** Återkommande — kan utlösas igen nästa handelsdag.

**Inställningar:**
- **Prisskillnad (valfritt)** — utlöses när |pris1 − pris2| når gränsen
- **Notis när lika** — utlöses när priserna är praktiskt taget identiska (skiljer sig med mindre än 0,01)

**Skapa en aktiepar-bevakning:**
1. Tryck på **+**-knappen i Aktier- eller Par-fliken.
2. Sök upp och välj den första aktien.
3. Sök upp och välj den andra aktien.
4. Ange prisskillnad om önskat, och/eller aktivera "Notis när lika".
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Återställs automatiskt nästa dag.

---

### 6. Prisintervall

**Vad det gör:** Bevakar om priset ligger inom ett angivet intervall mellan ett min- och maxpris.

**Typ:** Återkommande.

**Viktigt i nuvarande version:** Prisintervall finns fortfarande som bevakningstyp och kan redigeras om du redan har en sådan bevakning, men det finns ingen tydlig skapa-knapp för den i dagens huvudflöde.

**Redigera en befintlig prisintervall-bevakning:**
1. Öppna bevakningen från listan.
2. Justera min- och maxpris.
3. Tryck **Uppdatera**.

---

### 7. Kombinerat larm

**Vad det gör:** Låter dig kombinera flera villkor med logiska operatorer för att skapa avancerade bevakningsregler.

**Typ:** Återkommande — kan utlösas igen nästa handelsdag.

**Operatorer:**
- **OCH** — båda villkoren måste uppfyllas
- **ELLER** — minst ett villkor måste uppfyllas
- **INTE** — villkoret får *inte* vara uppfyllt

**Exempel:**
- "Pris under 100 kr OCH P/E under 15" — köpsignal baserad på både pris och värdering
- "Dagsrörelse ≥ 5 % ELLER Drawdown ≥ 10 %" — varning vid antingen stor rörelse eller stort fall

**Viktigt i nuvarande version:** Kombinerade larm stöds fortfarande av appen och kan redigeras om de redan finns, men det finns ingen synlig skapa-väg för dem i dagens huvudflöde.

---

## Vanliga flöden

### Bevaka en köpkurs

Situation: Du vill köpa Volvo B om den faller till 220 kr (nuvarande pris: 260 kr).

1. Sök efter "VOLV-B" och öppna aktiedetaljvyn.
2. Tryck **Prisbevakning**.
3. Ange `220` som målpris.
4. Tryck **Spara**.
5. Appen sätter automatiskt riktningen till "under 220 kr" och skickar en notis om priset faller till 220 kr eller lägre.
6. När notisen kommit: tryck på den för att gå direkt till aktiedetaljvyn och se situationen.

---

### Bevaka en stor daglig rörelse

Situation: Du vill veta om Ericsson rör sig mer än 4 % en dag, oavsett håll.

1. Öppna aktiedetaljvyn för ERIC-B.
2. Tryck **Dagsrörelse**.
3. Ange `4` %.
4. Välj **Båda håll**.
5. Tryck **Spara**.

---

### Bevaka fundamental värdering (P/E)

Situation: Du vill veta om Investor AB:s P/E-tal stiger över 25 (tecken på högt pris).

1. Öppna aktiedetaljvyn för INVE-B.
2. Tryck **Nyckeltal**.
3. Välj **P/E-tal**.
4. Ange `25`.
5. Tryck **Spara**.
6. Appen sätter riktningen till "P/E ≥ 25" automatiskt (om nuvarande P/E är under 25).

---

### Jämföra två aktiers priser

Situation: Du äger Handelsbanken och SEB och vill veta om priskurvan jämnas ut (skillnad under 5 kr).

1. Tryck **+** i Par-fliken.
2. Välj **SHB-A** som aktie 1.
3. Välj **SEB-A** som aktie 2.
4. Ange `5` som prisskillnadsgräns.
5. Tryck **Spara**.

---

### Hantera äldre bevakningstyper

Om du redan har äldre bevakningar av typen **Prisintervall** eller **Kombinerat larm** kvar i databasen kan du fortfarande:

1. Öppna dem från listan.
2. Redigera deras värden.
3. Aktivera/inaktivera eller ta bort dem som vanligt.

---

## Hantera dina bevakningar

### Aktivera och inaktivera

Tryck på **reglaget** på bevakningskortet för att stänga av eller slå på en bevakning utan att ta bort den. En inaktiv bevakning kontrolleras inte och skickar inga notiser.

### Återaktivera ett engångslarm

Engångslarm (Prismål och 52-veckorshögsta) inaktiveras automatiskt efter utlösning.

- Hitta bevakningen i listan — den visar "Triggad [datum]".
- Tryck på kortet för att öppna redigeringsdialogen.
- Tryck **Återaktivera**.

Bevakningen är nu aktiv igen med samma inställningar.

### Redigera en bevakning

1. Tryck på bevakningskortet.
2. Ändra de önskade värdena i dialogen.
3. Tryck **Uppdatera**.

### Ta bort en bevakning

**Alternativ 1 — Swipe:** Svep kortet åt vänster. En bekräftelsedialog visas med möjlighet att ångra.

**Alternativ 2 — Via dialog:** Tryck på kortet → tryck **Ta bort** längst ned i dialogen.

### Förstå "Ny"-märket och triggad-badge

- **Triggad-badge (amber/gul):** Visar datumet då bevakningen utlöstes. Visas alltid så länge bevakningen är i triggat läge.
- **Ny-märke (lila/primärfärg):** Visar att det är en utlösning du *inte sett* sedan den inträffade. Försvinner automatiskt när du öppnar aktiedetaljvyn för den berörda aktien.

---

## Notiser

### Aktivera notisbehörighet

Appen ber om notisbehörighet första gången du startar den. Om du avböjde kan du aktivera det igen via:

**Android-inställningar → Appar → StockFlip → Notiser → Tillåt**

Utan notisbehörighet kan appen inte meddela dig när en bevakning utlöses — du behöver då öppna appen manuellt för att se om något triggats.

### Vad händer när en notis skickas

- Notisen visas med titeln och en kort beskrivning av vad som triggades.
- Trycker du på notisen öppnas StockFlip direkt på detaljvyn för den berörda aktien, eller pardetaljen för ett aktiepar.
- Bevakningen är märkt som triggad med datum när du väl öppnar appen.

### Engångslarm vs återkommande larm

| | Engångslarm | Återkommande larm |
|---|---|---|
| **Typ** | Prismål, 52-veckorshögsta | Dagsrörelse, Nyckeltal, Aktiepar, Prisintervall, Kombinerat |
| **Inaktiveras efter utlösning** | Ja | Nej |
| **Återaktivering** | Manuell | Automatisk (nästa dag) |
| **Kan utlösas igen samma dag** | Nej | Nej (max en gång per handelsdag) |

---

## Tips och vanliga frågor

**Varför fick jag ingen notis?**
- Kontrollera att notisbehörighet är aktiverat (se ovan).
- Kontrollera att bevakningen är aktiv (reglaget på).
- Engångslarm utlöses inte om de redan är triggade — tryck Återaktivera.
- Om marknaden är stängd kontrollerar appen bara var 60:e minut.

**Varför inaktiverades min bevakning automatiskt?**
- Prismål och 52-veckorshögsta inaktiveras automatiskt när de utlöses. Det är avsiktligt för att undvika upprepade notiser för samma händelse.

**Kan jag ha flera bevakningar på samma aktie?**
- Ja, du kan ha hur många bevakningar du vill på samma aktie, av olika eller samma typ.

**Hur hämtas nyckeltal (P/E etc.)?**
- Via Finnhub (extern datatjänst). Nyckeltal uppdateras med lägre frekvens än kurser och kan ibland saknas för ovanliga aktier.

**Vilka aktier kan jag bevaka?**
- Alla aktier som finns på Yahoo Finance: svenska (OMX), amerikanska (NASDAQ/NYSE), krypto och mer. Svenska aktier söks med tickersuffix `.ST` (t.ex. `VOLV-B.ST`).
