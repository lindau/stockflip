# StockFlip — Användarhandbok

StockFlip låter dig bevaka aktier och kryptovalutor och få notiser när dina egna villkor uppfylls. Den här guiden förklarar hur du skapar och hanterar bevakningar.

## Innehållsförteckning

- [Ordlista](#ordlista)
- [Hur appen fungerar i bakgrunden](#hur-appen-fungerar-i-bakgrunden)
- [Navigering i appen](#navigering-i-appen)
- [Kursgrafen och indikatorer](#kursgrafen-och-indikatorer)
- [Aktiedetaljsidan: kollapsa och expandera](#aktiedetaljsidan-kollapsa-och-expandera)
- [Bevakningstyperna](#bevakningstyperna)
  - [1. Målpris](#1-malpris)
  - [2. Dagsrörelse](#2-dagsrorelse)
  - [3. Drawdown](#3-drawdown)
  - [4. Nyckeltal](#4-nyckeltal)
  - [5. Insideraffärer](#5-insideraffarer)
  - [6. Aktiepar](#6-aktiepar)
  - [7. Prisintervall](#7-prisintervall)
  - [8. Kombinerat larm](#8-kombinerat-larm)
  - [9. SMA-bevakning](#9-sma-bevakning)
  - [10. SMA-korsning](#10-sma-korsning)
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
| **Nyckeltal** | Finansiella mått som P/E-tal, P/S-tal, direktavkastning och vinst/aktie. |
| **P/E-tal** | Aktiekursen delat med vinst per aktie. Högt P/E = dyr värdering; lågt P/E = billig värdering. |
| **P/S-tal** | Aktiekursen delat med omsättning per aktie. |
| **Direktavkastning** | Utdelningen per aktie delat med aktiekursen, i procent. |
| **Vinst/aktie** | Bolagets vinst per aktie, ofta kallat EPS. |
| **P/B-tal** (Price/Book) | Börsvärdet delat med bolagets bokförda egna kapital (substansvärde). Under 1 betyder att bolaget värderas lägre än det egna kapitalet i balansräkningen. |
| **EV/EBITDA** | Företagsvärdet (börsvärde + nettoskuld) delat med rörelseresultat före avskrivningar. Ett värderingsmått som tar hänsyn till bolagets skulder och därför fungerar bättre än P/E när bolag är olika skuldsatta. |
| **Kursmål** | Analytikernas genomsnittliga bedömning av vad aktien bör kosta om ett år (tidshorisonten anges inte av Yahoo och varierar mellan analytiker). Visas tillsammans med lägsta och högsta kursmål och antalet analytiker bakom siffrorna. |
| **Analytikerrekommendation** | Sammanvägd rekommendation från analytikerna: Starkt köp, Köp, Behåll, Minska eller Sälj. Visas som en etikett på kortet Analytikernas kursmål. |
| **Skuldsättningsgrad** | Bolagets skulder i procent av det egna kapitalet. I appens statistikruta står den förkortad som "Skuldsättn.". |
| **Aktiepar** | En bevakning som jämför prisskillnaden mellan två aktier. |
| **Kombinerat larm** | En bevakning som kombinerar flera villkor med logiska operatorer (OCH/ELLER/INTE). |
| **SMA** (Simple Moving Average) | Glidande medelvärde: genomsnittligt stängningspris för aktien över ett antal senaste dagar, t.ex. SMA(50) för de senaste 50 dagarna. Jämnar ut kortsiktigt brus och används för att se den underliggande trenden. |
| **RSI** (Relative Strength Index) | Momentumindikator mellan 0 och 100 som mäter hur snabbt kursen stigit eller fallit på sistone (här RSI(14), Wilders utjämning). Över 70 tolkas ofta som överköpt, under 30 som översåld. |
| **Bollinger Bands** | Ett band runt kursen: ett glidande medelvärde (SMA 20) med en övre och undre linje två standardavvikelser ovanför och under. Ju mer kursen svänger, desto bredare band. Kursen nära övre kanten kan tyda på att den rört sig starkt uppåt, nära den undre på motsatsen. |
| **Golden cross** | När ett kortare SMA (t.ex. 50 dagar) stiger över ett längre SMA (t.ex. 200 dagar) — ofta tolkat som ett tecken på uppåtgående trend. |
| **Death cross** | När ett kortare SMA faller under ett längre SMA — ofta tolkat som ett tecken på nedåtgående trend. |

---

## Hur appen fungerar i bakgrunden

StockFlip hämtar aktuella kurser automatiskt:

- **Under börsens öppetider:** en gång per minut
- **Utanför börsens öppetider:** var 60:e minut
- **Nyckeltal** (P/E, P/S, utdelning, vinst/aktie) hämtas högst var 15:e minut, eftersom de sällan ändras under dagen

Notiser skickas direkt när ett villkor uppfylls. Trycker du på notisen öppnas appen och du hamnar direkt på aktiedetaljvyn för den berörda aktien.

Kurslarm skickar bara notiser medan aktiens marknad är öppen, samt upp till 30 minuter efter stängning. Det gör att du inte väcks av notiser nattetid som bygger på gårdagens stängningskurs. Kryptolarm kan utlösas dygnet runt. Ett återkommande larm vars villkor fortfarande är uppfyllt när nästa handelsdag öppnar notifierar då igen. Insiderbevakningar kontrolleras enligt sitt eget schema och påverkas inte av börsens öppettider.

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

Index följer sin hemmabörs öppettider, t.ex. `^OMXS30` Stockholm och `^GSPC` (S&P 500) USA.

---

## Navigering i appen

Appen har tre flikar längst ned:

- **Översikt** — ett läsläge som prioriterar vad som kräver din uppmärksamhet nu
- **Par** — enbart aktiepar-bevakningar
- **Bevakningar** — här lägger du till aktier och skapar, redigerar, pausar eller tar bort bevakningar

I nuvarande appversion fungerar flikarna så här:

- **Översikt:** aktiepar visas inte här utan i fliken **Par**. Översikten visar sektioner som `Nytt och triggade`, `Nära att triggas`, `Aktiva bevakningar` och `Inaktiva`. Härifrån läser du läget, men nya aktier läggs inte till här.
- **Par:** tryck på `+` för att skapa ett aktiepar.
- **Bevakningar:** tryck på `+` för att lägga till en ny aktie, ett index eller en kryptovaluta. Öppna sedan aktiens detaljsida och välj vilken typ av bevakning du vill skapa. Filtret du valt (t.ex. **Triggade**) ligger kvar när du byter flik och kommer tillbaka.

---

## Kursgrafen och indikatorer

Överst på aktiens detaljsida visas kursgrafen. Välj period under grafen (1D, 1V, 1M, 3M, 6M, 1Å, 5Å) och tryck och dra i grafen för att se kurs och datum för en enskild punkt.

Uppe till höger i grafen finns två knappar:

- **Kugghjul** — välj vilka indikatorer som ritas i grafen (se nedan).
- **Fullskärm** — visar grafen över hela skärmen i liggande läge. Kugghjulet finns även där.

**Indikatorer du kan välja:**

- **SMA (från bevakningar)** — de streckade SMA-linjerna för dina SMA-bevakningar. På som standard.
- **Bollinger Bands (20, 2σ)** — ett skuggat band runt kursen med streckade linjer för övre och undre gräns och en tunn mittlinje (SMA 20). Av som standard.
- **RSI (14)** — visas i en egen liten panel under grafen med hjälplinjer vid 30 och 70 och aktuellt värde. När du trycker i grafen visas RSI-värdet för samma dag. Av som standard.

**Bra att veta:**

- Bollinger Bands och RSI visas för perioderna **1M och längre**. För 1D och 1V (som visar kursen minut för minut) går de inte att slå på, eftersom de bygger på dagsstängningar.
- Ditt val gäller alla aktier och sparas tills du ändrar det.
- Indikatorerna är bara ett hjälpmedel i grafen — de skickar inga notiser. Bevakningar skapar du som vanligt under respektive bevakningstyp.

---

## Aktiedetaljsidan: kollapsa och expandera

Aktiedetaljsidan är uppdelad i sektioner så att den inte blir för lång:

- **Bevakningar** — dina nivåer, alla bevakningar för bolaget och den runda **+**-knappen nere till höger som alltid ligger kvar när du scrollar. Öppen som standard.
- **Mer om bolaget** — **Senaste insideraffärer**, **Poddomnämnanden** (om funktionen finns i din version) och **Anteckningar**. Insider, podd och anteckningar är hopfällda som standard.

Datum för nästa rapport visas som en liten rad under kursen i toppkortet, till exempel *Rapport om 12 dagar · 23 okt*.

Tryck på en rubrik (eller pilen till höger) för att fälla ut eller ihop sektionen. När en sektion är hopfälld visas en kort sammanfattning bredvid rubriken, till exempel *4 aktiva · 1 triggad* eller *3 köp · 1 sälj*. Ditt val sparas och gäller alla aktier. Sektioner utan innehåll, till exempel insideraffärer för en aktie utan sådana, visas inte alls.

Tryck på **+**-knappen för att välja bevakningstyp. Nyckeltal och insideraffärer visas bara för aktier som stöder dem. Längst ned finns genvägar till Avanza och Nordnet.

---

## Bevakningstyperna

### 1. Målpris

**Vad det gör:** Skickar en notis när aktiens pris når ett målpris du sätter.

**Typ:** Engångslarm — inaktiveras automatiskt när det utlöses.

**Riktning:** Bestäms automatiskt när du sparar bevakningen.
- Om nuvarande pris är *högre* än målpriset → väntar på att priset ska falla **under** målet.
- Om nuvarande pris är *lägre* än målpriset → väntar på att priset ska stiga **över** målet.

**Skapa en prismålsbevakning:**
1. Sök upp aktien eller tryck på en befintlig bevakningskort för att öppna aktiedetaljvyn.
2. Tryck på **+**-knappen nere till höger och välj **Målpris**.
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
2. Tryck på **+**-knappen nere till höger och välj **Dagsrörelse**.
3. Ange tröskelprocent (t.ex. 5).
4. Välj riktning: Upp / Ned / Båda håll.
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Bevakningen visas som triggad med datum.
- Återställs automatiskt nästa dag.

---

### 3. Drawdown

**Vad det gör:** Skickar en notis när aktien har fallit ett visst belopp eller en viss procent från sin 52-veckorshögsta kurs.

**Typ:** Engångslarm — inaktiveras automatiskt när det utlöses.

**Välj mättyp:**
- **Procent** — t.ex. "varna om aktien fallit 15 % från toppen"
- **Kronor** — t.ex. "varna om aktien fallit 50 kr från toppen"

**Skapa en drawdown-bevakning:**
1. Öppna aktiedetaljvyn (visar aktuell drawdown i headern).
2. Tryck på **+**-knappen nere till höger och välj **Drawdown**.
3. Välj Procent eller Kronor.
4. Ange hur mycket nedgång som ska trigga larmet.
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Bevakningen inaktiveras — tryck **Återaktivera** för att sätta upp det igen.

---

### 4. Nyckeltal

**Vad det gör:** Skickar en notis när ett finansiellt nyckeltal (P/E, P/S, direktavkastning eller vinst/aktie) når ett målvärde.

**Typ:** Återkommande — kan utlösas igen nästa handelsdag.

**Tillgängliga nyckeltal:**
- **P/E-tal** — värderingsmått baserat på vinst
- **P/S-tal** — värderingsmått baserat på omsättning
- **Direktavkastning** — utdelning i procent av kursen
- **Vinst/aktie** — vinst per aktie (EPS)

**Bara visning:** Aktiens detaljsida visar fler nyckeltal än de som går att bevaka — Börsvärde, ROE, P/B, EV/EBITDA och Skuldsättningsgrad visas i statistikrutorna men kan inte användas i en bevakning.

**Riktning:** Bestäms automatiskt när du sparar bevakningen (samma logik som Prismål).

**Skapa en nyckeltalbevakning:**
1. Öppna aktiedetaljvyn.
2. Tryck på **+**-knappen nere till höger och välj **Nyckeltal**.
3. Välj vilket nyckeltal (P/E, P/S, Direktavkastning, Vinst/aktie).
4. Ange målvärdet.
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Återställs automatiskt nästa dag.

---

### 5. Insideraffärer

**Vad det gör:** Skickar en notis när nya insideraffärer (både köp och försäljningar) rapporteras för en aktie där appen har stöd för insiderdata.

**Typ:** Återkommande — kontrolleras var 6:e timme och kan utlösas igen nästa handelsdag.

**Skapa en bevakning för insideraffärer:**
1. Öppna aktiedetaljvyn för en aktie med insiderstöd.
2. Tryck på **+**-knappen nere till höger och välj **Insideraffärer**.
3. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis med sammanfattning.
- Trycker du på notisen öppnas aktiedetaljvyn vid **Senaste insideraffärer**.
- Den aktuella transaktionen markeras och visas i ett detaljfönster med person, roll, datum, antal, pris och uppskattat värde.

---

### 6. Aktiepar

**Vad det gör:** Bevakar den absoluta prisskillnaden mellan två aktier och skickar en notis när skillnaden är minst ett visst värde, oavsett vilken aktie som ligger högst, eller när priserna är lika.

**Typ:** Återkommande — kan utlösas igen nästa handelsdag. Om spreaden byter sida samma dag, till exempel först B över A och senare A över B, kan den utlösas igen för den nya sidan.

**Inställningar:**
- **Prisskillnad (valfritt)** — utlöses när |pris1 − pris2| är större än eller lika med gränsen
- **Notis när lika** — utlöses när priserna är praktiskt taget identiska (skiljer sig med mindre än 0,01)

**Skapa en aktiepar-bevakning:**
1. Tryck på **+**-knappen i **Par**-fliken.
2. Sök upp och välj den första aktien.
3. Sök upp och välj den andra aktien.
4. Ange prisskillnad om önskat, och/eller aktivera "Notis när lika".
5. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Återställs automatiskt nästa dag.

---

### 7. Prisintervall

**Vad det gör:** Bevakar om priset ligger inom ett angivet intervall mellan ett min- och maxpris.

**Typ:** Återkommande.

**Viktigt i nuvarande version:** Prisintervall finns fortfarande som bevakningstyp och kan redigeras om du redan har en sådan bevakning, men det finns ingen tydlig skapa-knapp för den i dagens huvudflöde.

**Redigera en befintlig prisintervall-bevakning:**
1. Öppna bevakningen från listan.
2. Justera min- och maxpris.
3. Tryck **Uppdatera**.

---

### 8. Kombinerat larm

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

### 9. SMA-bevakning

**Hur SMA fungerar:** SMA (Simple Moving Average, glidande medelvärde) är genomsnittet av aktiens stängningskurs över ett visst antal senaste dagar. SMA(50) betyder alltså "genomsnittspriset de senaste 50 dagarna". Eftersom det jämnar ut dagliga upp- och nedgångar visar SMA-linjen den underliggande trenden tydligare än det råa priset. Ett kort SMA (t.ex. 20 dagar) följer priset tätt och reagerar snabbt; ett långt SMA (t.ex. 200 dagar) rör sig trögt och visar den mer långsiktiga trenden.

**Vad det gör:** Skickar en notis när aktiens pris passerar ett glidande medelvärde (SMA) över ett antal dagar du väljer, t.ex. "pris under SMA(50)".

**Typ:** Engångslarm — inaktiveras automatiskt när det utlöses.

**Riktning:** Bestäms automatiskt när du sparar bevakningen, precis som för Målpris.
- Om priset redan ligger *över* SMA:t → väntar på att priset ska falla **under** SMA:t.
- Om priset redan ligger *under* SMA:t → väntar på att priset ska stiga **över** SMA:t.

**Vanliga inställningar:**
- **SMA(50)** — pris som korsar sitt 50-dagarsmedelvärde tolkas ofta som en förändring i den medelfristiga trenden.
- **SMA(200)** — pris som korsar sitt 200-dagarsmedelvärde är ett klassiskt mått på om aktien är i en långsiktig upp- eller nedåtgående marknad ("bull"/"bear"). Pris över SMA(200) tolkas ofta som en köpsignal på lång sikt, pris under som en säljsignal.
- **SMA(20)** — kortare och känsligare, fångar upp trendskiften snabbare men ger fler falska signaler.

**Skapa en SMA-bevakning:**
1. Öppna aktiedetaljvyn.
2. Tryck på **+**-knappen nere till höger och välj **SMA-bevakning**.
3. Ange antal dagar för det glidande medelvärdet, eller välj en snabbknapp (20, 50, 100 eller 200 dagar).
4. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis.
- Bevakningen inaktiveras — tryck **Återaktivera** för att sätta upp den igen.

**I kursgrafen:** Så länge du har en aktiv SMA-bevakning (eller SMA-korsning, se nedan) på aktien ritas medelvärdets historiska utveckling som en streckad linje ovanpå kursgrafen, märkt med perioden (t.ex. "SMA50") — precis som på t.ex. Yahoo Finance rör sig linjen upp och ner i takt med kursen, inte en rak vågrät linje. Har du flera bevakningar med olika perioder visas en linje per period. Du kan slå av och på SMA-linjerna med kugghjulet ovanför grafen, se [Kursgrafen och indikatorer](#kursgrafen-och-indikatorer).

---

### 10. SMA-korsning

**Vad det gör:** Skickar en notis när ett kortare glidande medelvärde (SMA) korsar ett längre, t.ex. "SMA(50) under SMA(200)" (death cross) eller "SMA(50) över SMA(200)" (golden cross).

**Typ:** Engångslarm — inaktiveras automatiskt när det utlöses.

**Riktning:** Bestäms automatiskt när du sparar bevakningen, utifrån hur de två SMA:erna ligger till just nu.
- Ligger det korta SMA:t redan över det långa → väntar på nästa **death cross** (korta faller under långa).
- Ligger det korta SMA:t redan under det långa → väntar på nästa **golden cross** (korta stiger över långa).

**Vanliga inställningar för köp- och säljsignaler:**
- **50/200 dagar** — den mest kända kombinationen. Golden cross (SMA 50 stiger över SMA 200) tolkas traditionellt som en köpsignal och signalerar en ny långsiktig uppgångstrend. Death cross (SMA 50 faller under SMA 200) tolkas som en säljsignal och en ny nedgångstrend. Ger sällsynta men ofta mer tillförlitliga signaler.
- **20/50 dagar** — en snabbare kombination för kortare tidshorisont. Ger fler signaler och reagerar snabbare på trendskiften, men med fler falska utslag i sidledes marknader.
- **20/100 dagar** — en mellanväg mellan de två ovan.

Vill du bevaka båda hållen (både nästa golden cross och nästa death cross) på samma periodpar skapar du två separata SMA-korsningsbevakningar — appen väljer bara en riktning åt gången baserat på nuläget när du skapar bevakningen.

**Skapa en SMA-korsningsbevakning:**
1. Öppna aktiedetaljvyn.
2. Tryck på **+**-knappen nere till höger och välj **SMA-korsning**.
3. Ange antal dagar för det korta SMA:t (t.ex. 20 eller 50) och det långa SMA:t (t.ex. 100 eller 200), eller använd snabbknapparna.
4. Tryck **Spara**.

**Vad händer när den utlöses:**
- Du får en notis om golden cross eller death cross.
- Bevakningen inaktiveras — tryck **Återaktivera** för att sätta upp den igen.

---

## Vanliga flöden

### Bevaka en köpkurs

Situation: Du vill köpa Volvo B om den faller till 220 kr (nuvarande pris: 260 kr).

1. Gå till **Bevakningar** och tryck på `+`.
2. Sök efter "VOLV-B" och öppna aktiedetaljvyn.
3. Tryck på **+**-knappen nere till höger och välj **Målpris**.
4. Ange `220` som målpris.
5. Tryck **Spara**.
6. Appen sätter automatiskt riktningen till "under 220 kr" och skickar en notis om priset faller till 220 kr eller lägre.
7. När notisen kommit: tryck på den för att gå direkt till aktiedetaljvyn och se situationen.

---

### Bevaka en stor daglig rörelse

Situation: Du vill veta om Ericsson rör sig mer än 4 % en dag, oavsett håll.

1. Gå till **Bevakningar**, öppna aktiedetaljvyn för ERIC-B.
2. Tryck **Dagsrörelse**.
3. Ange `4` %.
4. Välj **Båda håll**.
5. Tryck **Spara**.

---

### Bevaka fundamental värdering (P/E)

Situation: Du vill veta om Investor AB:s P/E-tal stiger över 25 (tecken på högt pris).

1. Gå till **Bevakningar**, öppna aktiedetaljvyn för INVE-B.
2. Tryck **Nyckeltal**.
3. Välj **P/E-tal**.
4. Ange `25`.
5. Tryck **Spara**.
6. Appen sätter riktningen till "P/E ≥ 25" automatiskt (om nuvarande P/E är under 25).

---

### Jämföra två aktiers priser

Situation: Du äger Handelsbanken och SEB och vill veta när prisskillnaden blir minst 5 kr, oavsett vilken aktie som ligger högst.

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

Vill du snabbt stänga av eller slå på *alla* bevakningar för en aktie utan att öppna varje bevakning för sig, använd reglaget bredvid rubriken "Bevakningar" på aktiens detaljvy.

### Masshantering i Bevakningar

I fliken **Bevakningar** kan du långtrycka på en bevakning för att gå in i markeringsläge. Där kan du välja flera bevakningar samtidigt och:

- **Aktivera**
- **Pausa**
- **Ta bort**
- **Avsluta markeringsläge**

### Återaktivera en triggad bevakning

Engångslarm (Målpris och Drawdown) inaktiveras automatiskt efter utlösning och kräver alltid manuell återaktivering. Återkommande larm (Dagsrörelse, Nyckeltal, Aktiepar, Prisintervall, Kombinerat) återaktiveras normalt automatiskt nästa handelsdag, men du kan även återaktivera dem manuellt tidigare — till exempel för att slippa se "Triggad"-märket resten av dagen.

- Hitta bevakningen i listan — den visar "Triggad [datum]".
- Tryck **Återaktivera** direkt på kortet.

Bevakningen är nu aktiv igen. För målpris räknar appen om riktningen från aktuell kurs: om kursen ligger över målpriset bevakas nästa passage ned under nivån, och om kursen ligger under målpriset bevakas nästa passage upp över nivån.

Samma regel gäller alla bevakningstyper vid återaktivering: är den berörda börsen stängd, eller är villkoret fortfarande uppfyllt just nu (kursen ligger till exempel kvar över målpriset, aktien är kvar i drawdown, eller dagsrörelsen är fortfarande över tröskeln), behåller appen dagens trigger-spärr. Bevakningen visas som aktiv igen, men kan inte skicka en ny notis förrän nästa handelsdag — då utvärderas den på nytt.

Är börsen däremot öppen och villkoret inte längre uppfyllt när du återaktiverar, släpps spärren direkt — bevakningen kan då trigga igen redan vid nästa kurskontroll samma dag, i stället för att behöva vänta till börsstängning eller nästa dag.

När spärren behålls visas detta i listor som **Nästa handelsdag**, och återaktiveringsmeddelandet säger att bevakningen kan trigga först nästa handelsdag.

Om flera bevakningar är triggade samtidigt kan du återaktivera dem alla på en gång: välj filtret **Triggade** i **Bevakningar** och tryck **Återaktivera alla**.

### Redigera en bevakning

1. Tryck på bevakningskortet.
2. Ändra de önskade värdena i dialogen.
3. Tryck **Uppdatera**.

### Ta bort en bevakning

**Alternativ 1 — Swipe:** Svep kortet åt vänster. I **Bevakningar** pausas en aktiv bevakning (paus-ikon) och en pausad aktiveras igen (play-ikon) — inget tas bort. En utlöst bevakning aktiveras i stället med knappen **Återaktivera**. I fliken **Par** tas aktieparet bort direkt (röd papperskorg). I alla fallen visas ett meddelande längst ned med **Ångra**. Svep åt höger öppnar detaljvyn.

**Alternativ 2 — Via dialog:** Tryck på kortet → tryck **Ta bort** längst ned i dialogen.

### Förstå "Ny"-märket och triggad-badge

- **Triggad-badge (amber/gul):** Visar datumet då bevakningen utlöstes. Visas alltid så länge bevakningen är i triggat läge.
- **Ny-märke (lila/primärfärg):** Visar att det är en utlösning du *inte sett* sedan den inträffade. Försvinner automatiskt när du öppnar aktiedetaljvyn för den berörda aktien.

### Poddomnämnanden

Den här funktionen kräver att telefonen är ansluten till samma Tailscale-nätverk som podcast-pipeline-instansen, en fristående tjänst som analyserar poddavsnitt. Är instansen inte nåbar fungerar resten av appen som vanligt, bara utan poddfunktionerna nedan.

**Mikrofon-ikon på aktiekortet:** På aktiekortet i **Översikt → Aktier** kan en liten mikrofon-ikon visas till vänster om texten som anger antal bevakningar. Den betyder att bolaget nyligen nämnts i ett poddavsnitt.

**Sektionen "Poddomnämnanden" på aktiedetaljen:**
- En egen sektion längst ned på aktiens detaljvy visar mer detaljer, med en **på/av-växel** i sektionsrubriken (av som standard).
- När den är på listas omnämnandena med podd, datum, ett kort sammandrag/citat och eventuell rekommendation. Finns fler än ett omnämnande visas bara det senaste, med texten "Visar 1 av X omnämnanden · tryck för att visa X till" — tryck på rubriken eller texten för att fälla ut resten. Utfällda rader visar även eventuella risker.
- Finns inga omnämnanden ännu visas en **Synka nu**-knapp som kör en synk direkt i förgrunden och visar antingen träffar eller en tydlig felmeddelandetext, istället för att bara vänta på nästa bakgrundskörning.

---

## Notiser

### Aktivera notisbehörighet

Appen ber om notisbehörighet första gången du startar den. Om du avböjde kan du aktivera det igen via:

**Android-inställningar → Appar → StockFlip → Notiser → Tillåt**

Utan notisbehörighet kan appen inte meddela dig när en bevakning utlöses — du behöver då öppna appen manuellt för att se om något triggats.

### Vad händer när en notis skickas

- Notisen visar vad som faktiskt triggade, till exempel att ett målpris nåtts, att drawdown-nivån nåtts eller att ett nyckeltal passerat din nivå.
- Trycker du på notisen öppnas StockFlip direkt på detaljvyn för den berörda aktien, eller pardetaljen för ett aktiepar.
- Ett **kombinerat larm** som inte är knutet till en enskild aktie öppnar i stället **Bevakningar**-fliken och visar trigger-texten kort på skärmen.
- Du möts av en tydlig trigger-banner högst upp med varför du hamnade där och kan direkt **återaktivera** eller **ta bort** bevakningen.
- En trigger markeras som sedd först när du öppnar bevakningen eller detaljvyn, inte bara när listan visas.
- För **insideraffärer** öppnas aktiedetaljvyn vid sektionen **Senaste insideraffärer**. Den aktuella transaktionen markeras och visas även i ett detaljfönster som du stänger med **Stäng**.
- Notisen fungerar även om du trycker på den långt senare, till exempel dagen efter, och du kan trycka på samma notis flera gånger.

### Appuppdateringar

StockFlip distribueras inte via Play Store, så appen håller själv koll på om det finns en nyare version att hämta.

- **Sök efter uppdateringar** finns i menyn högst upp (bredvid Tema/Exportera/Importera/Hjälp/Version) och kontrollerar direkt om en nyare version finns.
- Kontrollen görs även tyst varje gång du startar appen, och därtill i bakgrunden ungefär en gång per dygn (kräver nätverksanslutning). Hittas ingen ny version, eller misslyckas kontrollen, händer inget synligt.
- Hittas en ny version vid appstart visas bekräftelsedialogen direkt. Hittas den i stället i bakgrunden får du en notis, och trycker du på den öppnas samma bekräftelsedialog.
- Dialogen har tre val:
  - **Hämta och installera** — laddar ner filen och startar installationen.
  - **Avbryt** — gör inget just nu; du kan söka igen senare.
  - **Hoppa över denna version** — den automatiska bakgrundskontrollen slutar notifiera om just den versionen (en manuell sökning visar den ändå).
- Om StockFlip inte redan har tillstånd att installera appar från denna källa tas du till en systeminställning för att tillåta det, innan installationen kan fortsätta.
- Själva installationen bekräftas en andra gång av Androids egen installationsskärm — StockFlip startar bara den, det är inte StockFlip som installerar.
- Uppdateringskontrollen pratar med det publika GitHub-repot `github.com/lindau/StockFlip` över krypterad anslutning (HTTPS); ingen inloggning behövs.

### Engångslarm vs återkommande larm

| | Engångslarm | Återkommande larm |
|---|---|---|
| **Typ** | Målpris, Drawdown, SMA-bevakning, SMA-korsning | Dagsrörelse, Nyckeltal, Insideraffärer, Aktiepar, Prisintervall, Kombinerat |
| **Inaktiveras efter utlösning** | Ja | Nej |
| **Återaktivering** | Manuell (krävs) | Automatisk (nästa dag), men kan även göras manuellt tidigare |
| **Kan utlösas igen samma dag** | Nej | Normalt nej. Aktiepar kan trigga igen om spreaden byter sida. Vid manuell återaktivering: ja, om villkoret inte längre är uppfyllt och börsen är öppen — se [Återaktivera en triggad bevakning](#ateraktivera-en-triggad-bevakning). |

---

## Tips och vanliga frågor

**Varför fick jag ingen notis?**
- Kontrollera att notisbehörighet är aktiverat (se ovan).
- Kontrollera att bevakningen är aktiv (reglaget på).
- Engångslarm utlöses inte om de redan är triggade — tryck Återaktivera.
- Om marknaden är stängd kontrollerar appen bara var 60:e minut.

**Varför inaktiverades min bevakning automatiskt?**
- Målpris, Drawdown, SMA-bevakning och SMA-korsning inaktiveras automatiskt när de utlöses. Det är avsiktligt för att undvika upprepade notiser för samma händelse.

**Vad betyder "Kunde inte uppdateras" på ett kort?**
- Appen lyckades inte hämta nya kurser för bevakningen, till exempel på grund av dålig uppkoppling. Kortet visar då de senast kända värdena och vilken tid de hämtades. Raden försvinner av sig själv när nästa uppdatering lyckas, eller när du drar nedåt för att uppdatera.

**Vad händer om en vy inte kan laddas?**
- Om **Översikt**, aktie- eller aktiepardetaljen, eller listorna i **Par** och **Bevakningar**, inte kan laddas första gången visas ett felmeddelande med knappen **Försök igen**. Kan grafen på aktiedetaljen inte hämtas står det i grafytan — tryck på den valda perioden (eller dra nedåt) för att försöka igen. Kan bevakningarna på aktiedetaljen inte läsas in visas det i avsnittet Bevakningar. Om vyn redan visar data och en uppdatering misslyckas ligger de senast kända värdena kvar, och ett kort meddelande visas längst ned.

**Varför stängs inte dialogen när jag trycker Skapa eller Uppdatera?**
- Om ett värde saknas eller är ogiltigt, om en likadan bevakning redan finns, eller om bevakningen inte kunde sparas, ligger dialogen kvar och felet visas direkt under fältet. Det du har skrivit finns kvar, så att du kan rätta och försöka igen. Tal kan skrivas med komma eller punkt, och med eller utan mellanslag mellan tusental (t.ex. `1 234,50`).

**Kan jag ha flera bevakningar på samma aktie?**
- Ja, du kan ha hur många bevakningar du vill på samma aktie, av olika eller samma typ.

**Hur hämtas nyckeltal (P/E etc.)?**
- Via Yahoo Finance. Nyckeltal kan ibland saknas för ovanliga aktier eftersom appen inte använder någon separat klientnyckelbaserad fallback.

**Vilka aktier kan jag bevaka?**
- Alla aktier som finns på Yahoo Finance: svenska (OMX), amerikanska (NASDAQ/NYSE), krypto och mer. Svenska aktier söks med tickersuffix `.ST` (t.ex. `VOLV-B.ST`).

**Kan jag bevaka index?**
- Ja. Sök på t.ex. "OMX", "S&P" eller "Nasdaq". Index har `^` framför symbolen (`^OMXS30`, `^GSPC`, `^IXIC`). Målpris, dagsrörelse, drawdown, aktiepar, SMA-bevakning, SMA-korsning och kombinerade larm fungerar som för aktier. Nyckeltal och insideraffärer finns inte för index, så de knapparna visas inte.

**Var kommer kursmålen ifrån?**
- Kortet "Analytikernas kursmål" på aktiens detaljsida hämtar snitt, lägsta och högsta kursmål, antal analytiker och rekommendation från Yahoo Finance. Uppsidan är skillnaden mellan snittkursmålet och aktuell kurs och visas bara när kursmålet anges i samma valuta som aktien handlas i.
- För mindre bolag baserar sig siffrorna ofta på bara en eller två analytiker (se antalet på kortet), och för många små svenska bolag saknas kursmål helt. Yahoo visar inte när kursmålen senast uppdaterades, så de kan vara gamla. Kursmål är analytikernas bedömningar, inte en garanti för hur kursen utvecklas.

**Varför saknas P/B, EV/EBITDA eller skuldsättningsgrad för en aktie?**
- Värdena hämtas från Yahoo Finance och finns inte för alla bolag. De saknas ofta för banker, försäkrings- och fastighetsbolag (särskilt EV/EBITDA och skuldsättningsgrad), samt för mindre bolag. Saknade värden visas som "-". Negativa värden är möjliga, till exempel negativt EV/EBITDA när bolaget går med förlust.

**Hur beräknas SMA?**
- SMA(N) är genomsnittet av aktiens senaste N dagsstängningar, hämtade från Yahoo Finance. Under pågående handelsdag används dagens senaste pris som den "senaste" punkten, precis som på de flesta handelsplattformar.

**Hur beräknas RSI och Bollinger Bands?**
- Båda beräknas i appen från aktiens dagsstängningar hämtade från Yahoo Finance. RSI(14) använder Wilders utjämning, och Bollinger Bands är SMA(20) ± 2 standardavvikelser. Värdena kan skilja sig något från andra tjänster eftersom RSI är känslig för hur många dagar tillbaka beräkningen startar.

**Varifrån kommer bolagsloggorna?**
- Bolagsloggor tillhandahålls av [Logo.dev](https://www.logo.dev), kryptologotyper av [CoinCap](https://coincap.io).
