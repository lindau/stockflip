# Product Requirements Document (PRD)
## StockFlip - Aktieövervakningsapp

**Version:** 1.0  
**Datum:** 2024  
**Status:** Implementerad

---

## 1. Översikt

### 1.1 Produktbeskrivning
StockFlip är en Android-applikation som gör det möjligt för användare att övervaka aktier och få notifikationer när specifika tröskelvärden nås. Appen stödjer flera typer av bevakningar och uppdaterar priser automatiskt i bakgrunden.

### 1.2 Målgrupp
- Privata investerare som vill övervaka specifika aktier
- Användare som vill jämföra två aktiers priser
- Användare som vill få notifikationer vid prisförändringar

### 1.3 Affärsmål
- Ge användare en enkel och effektiv lösning för aktieövervakning
- Minska behovet av manuell kontroll av aktiekurser
- Öka användarens medvetenhet om viktiga prisförändringar

---

## 2. Funktionella Krav

### 2.1 Aktiepar (Stock Pairs)
**Beskrivning:** Användare kan skapa par av två aktier för att jämföra deras priser.

**Funktioner:**
- Skapa nytt aktiepar genom att ange:
  - Ticker/bolagsnamn för aktie 1
  - Ticker/bolagsnamn för aktie 2
  - Prisskillnad (tröskelvärde)
  - Notifikation när priser är lika (valfritt)
- Visa lista över alla aktiepar med:
  - Nuvarande priser för båda aktierna
  - Prisskillnad
  - Senaste uppdateringstid
- Ta bort aktiepar (swipe to delete)
- Automatisk prisuppdatering varje minut

**Acceptanskriterier:**
- Användare kan lägga till minst ett aktiepar
- Prisskillnaden beräknas korrekt
- Notifikation skickas när tröskelvärde nås
- Priser uppdateras automatiskt i bakgrunden

### 2.2 Prisbevakning (Price Target Watch)
**Beskrivning:** Användare kan övervaka en enskild aktie för prisfall.

**Funktioner:**
- Skapa ny bevakning genom att ange:
  - Ticker/bolagsnamn
  - Drop-värde (absolut eller procentuellt)
  - Notifikation vid tröskelvärde (valfritt)
- Visa lista över alla bevakningar med:
  - Nuvarande pris
  - Drop-värde och typ (absolut/procent)
  - Status (tröskelvärde nått eller ej)
- Ta bort bevakningar (swipe to delete)
- Automatisk prisuppdatering varje minut

**Acceptanskriterier:**
- Användare kan lägga till minst en bevakning
- Drop-värde kan anges som absolut värde eller procent
- Notifikation skickas när drop-värde nås
- Priser uppdateras automatiskt i bakgrunden

### 2.3 Nyckeltalsbevakning (Key Metrics Watch)
**Beskrivning:** Användare kan övervaka specifika nyckeltal för en aktie.

**Funktioner:**
- Skapa ny bevakning genom att ange:
  - Ticker/bolagsnamn
  - Typ av nyckeltal (t.ex. utdelningsavkastning)
  - Tröskelvärde
- Visa lista över alla nyckeltalsbevakningar med:
  - Nuvarande värde
  - Tröskelvärde
  - Status
- Ta bort bevakningar (swipe to delete)
- Automatisk uppdatering varje minut

**Acceptanskriterier:**
- Användare kan lägga till minst en nyckeltalsbevakning
- Stöd för utdelningsavkastning (dividend yield)
- Notifikation skickas när tröskelvärde nås
- Värden uppdateras automatiskt i bakgrunden

### 2.4 Aktiesökning
**Beskrivning:** Användare kan söka efter aktier när de lägger till nya bevakningar.

**Funktioner:**
- Autocomplete-sökning med förslag
- Visa bolagsnamn och ticker
- Validering av ticker-symboler

**Acceptanskriterier:**
- Sökning fungerar med partiella matchningar
- Förslag visas inom 2 sekunder
- Endast giltiga ticker-symboler kan väljas

### 2.5 Notifikationer
**Beskrivning:** Användare får notifikationer när tröskelvärden nås.

**Funktioner:**
- Notifikation när aktiepars priser är lika (om aktiverat)
- Notifikation när drop-värde nås för prisbevakning
- Notifikation när tröskelvärde nås för nyckeltalsbevakning
- Notifikationer med hög prioritet (vibration, ljud)

**Acceptanskriterier:**
- Notifikationer skickas omedelbart när tröskelvärde nås
- Notifikationer innehåller relevant information
- Användare kan hantera notifikationsbehörigheter

### 2.6 Automatiska Uppdateringar
**Beskrivning:** Priser och värden uppdateras automatiskt i bakgrunden.

**Funktioner:**
- Periodiska uppdateringar varje minut
- Omedelbar uppdatering när ny bevakning läggs till
- Uppdateringar även när appen är stängd
- Hantering av batterioptimeringar

**Acceptanskriterier:**
- Uppdateringar sker varje minut när nätverk är tillgängligt
- Appen fortsätter uppdatera även i bakgrunden
- Batterioptimeringar hanteras korrekt

---

## 3. Icke-funktionella Krav

### 3.1 Prestanda
- Appen ska starta inom 2 sekunder
- Prisuppdateringar ska slutföras inom 5 sekunder
- UI ska vara responsiv utan lagg

### 3.2 Tillgänglighet
- Stöd för svenska språket
- Material Design 3-komponenter
- Stöd för mörkt tema (DayNight)

### 3.3 Säkerhet
- Lokal datalagring (ingen synkronisering)
- Säker API-kommunikation (HTTPS)
- Inga känsliga användardata lagras

### 3.4 Kompatibilitet
- Minsta Android-version: API 24 (Android 7.0)
- Stöd för både telefoner och surfplattor
- Stöd för både porträtt- och landskapsläge

### 3.5 Tillförlitlighet
- Hantering av nätverksfel
- Retry-logik för API-anrop
- Graceful degradation vid API-fel

---

## 4. Tekniska Krav

### 4.1 Arkitektur
- **Pattern:** MVVM (Model-View-ViewModel)
- **Clean Architecture:** Ja
- **Repository Pattern:** Ja
- **Single Activity Architecture:** Ja

### 4.2 Teknologier
- **Språk:** Kotlin
- **UI Framework:** XML Layouts med ViewBinding
- **Databas:** Room Database
- **Background Tasks:** WorkManager
- **Networking:** Retrofit + OkHttp
- **API:** Yahoo Finance API
- **Dependency Injection:** Manual (ViewModelProvider.Factory)

### 4.3 Databasstruktur
- **StockPair Entity:** Aktiepar med prisskillnad
- **WatchItem Entity:** Generisk bevakning (PricePair, PriceTarget, KeyMetrics)
- **Type Converters:** För WatchType enum

### 4.4 API-integration
- **Primär API:** Yahoo Finance (query1.finance.yahoo.com)
- **Endpoints:**
  - `/v8/finance/chart/{symbol}` - Hämta aktiepris
  - `/v1/finance/search` - Sök efter aktier
- **Retry-logik:** 3 försök med exponential backoff

### 4.5 Notifikationer
- **Channel:** "stock_price_alerts"
- **Priority:** HIGH
- **Features:** Vibration, ljud, badge

---

## 5. Användarflöden

### 5.1 Lägga till Aktiepar
1. Användare klickar på "Add Pair"-knapp
2. Dialog öppnas
3. Användare söker och väljer aktie 1
4. Användare söker och väljer aktie 2
5. Användare anger prisskillnad
6. Användare väljer om notifikation ska skickas när priser är lika
7. Användare klickar "Save"
8. Aktiepar läggs till och priser uppdateras omedelbart

### 5.2 Lägga till Prisbevakning
1. Användare klickar på "Add Watch"-knapp
2. Dialog öppnas
3. Användare söker och väljer aktie
4. Användare anger drop-värde
5. Användare väljer om värdet är absolut eller procentuellt
6. Användare väljer om notifikation ska skickas
7. Användare klickar "Save"
8. Bevakning läggs till och pris uppdateras omedelbart

### 5.3 Ta bort Bevakning
1. Användare sveper vänster eller höger på en bevakning i listan
2. Bevakning tas bort från databasen
3. Listan uppdateras automatiskt

---

## 6. UI/UX Krav

### 6.1 Design System
- **Material Design 3:** Ja
- **Tema:** DayNight (stöd för mörkt/ljust tema)
- **Färger:** Lila primärfärg, teal sekundärfärg

### 6.2 Layout-komponenter
- **ConstraintLayout:** För komplexa layouter
- **LinearLayout:** För enkla vertikala/horizontella arrangemang
- **FrameLayout:** För överlappande layouter

### 6.3 Material-komponenter
- MaterialButton
- MaterialCheckBox
- TextInputLayout med TextInputEditText
- MaterialCardView
- MaterialAlertDialogBuilder

### 6.4 Interaktioner
- Swipe to delete för alla bevakningar
- Pull to refresh (framtida funktion)
- Loading states med ProgressBar

---

## 7. Testning

### 7.1 Unit Tests
- UseCase-tester
- ViewModel-tester
- Repository-tester
- Test doubles för dependencies

### 7.2 UI Tests
- Espresso-tester för XML-layouter
- Testa användarflöden
- Testa edge cases

### 7.3 Integration Tests
- API-modultester
- Databastester
- WorkManager-tester

---

## 8. Framtida Funktioner (Backlog)

### 8.1 Prioriterade
- Redigera befintliga bevakningar
- Historik över prisförändringar
- Grafer för prisutveckling
- Export av bevakningar

### 8.2 Önskvärda
- Widget för hemsskärmen
- Fler API-källor (Nordnet, Google Finance)
- Portföljhantering
- Push-notifikationer via Firebase

### 8.3 Långsiktiga
- Användarkonton och synkronisering
- Delning av bevakningar
- Sociala funktioner
- Premium-funktioner

---

## 9. Kända Begränsningar

### 9.1 API-begränsningar
- Yahoo Finance API har rate limits
- Vissa ticker-symboler kanske inte stöds
- API-respons kan variera i kvalitet

### 9.2 Batteri
- Periodiska uppdateringar kan påverka batteritid
- Användare behöver bevilja batterioptimeringar

### 9.3 Nätverk
- Kräver internetanslutning för uppdateringar
- Ingen offline-funktionalitet för nya priser

---

## 10. Definition of Done

En funktion anses klar när:
- ✅ Koden följer projektets kodstandarder
- ✅ Unit-tester är skrivna och passerar
- ✅ UI-tester är skrivna och passerar (om tillämpligt)
- ✅ Kodgranskning är genomförd
- ✅ Funktionen är testad manuellt
- ✅ Dokumentation är uppdaterad
- ✅ Inga kända buggar finns

---

## 11. Referenser

### 11.1 Externa API:er
- Yahoo Finance API: https://query1.finance.yahoo.com/

### 11.2 Dokumentation
- Android Developer Documentation
- Material Design 3 Guidelines
- Room Database Documentation
- WorkManager Documentation

---

## 12. Ändringshistorik

| Version | Datum | Författare | Beskrivning |
|---------|-------|------------|-------------|
| 1.0 | 2024 | - | Initial PRD baserad på implementerad kod |

---

**Dokumentstatus:** ✅ Implementerad  
**Senaste uppdatering:** 2024