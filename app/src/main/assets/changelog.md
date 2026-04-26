# Ändringslogg

## 1.1.0

- Ny huvudnavigation med `Översikt`, `Par` och `Mina case`
- `Översikt` byggd som läsläge med sammanfattningskort och sektioner för `Nytt och triggade`, `Nära att triggas`, `Aktiva case` och `Inaktiva`
- `Mina case` samlar nu tillägg av aktier, bevakningshantering, filter och masshantering på ett ställe
- Sorteringen i `Översikt` prioriterar nu utlösta, nära trigger och aktiva case bättre
- `Nära att triggas` deduperas nu per aktie och bevakningstyp och använder skarpare gränser
- Aktiedetaljen har förenklats med tydligare fokus på nivåer och bevakningar
- Skapa-dialogerna har fått mer balanserade snabbval med mindre standardnivåer först
- Notiser öppnar nu relevanta detaljvyer med tydligare triggerkontext
- Pair-detaljen och pair-graferna har hårdnats för att undvika krascher vid ofullständig data
- Versionsraden öppnar nu den här ändringsloggen

## Att uppdatera framåt

- Lägg till en ny sektion överst i den här filen vid varje appuppdatering
- Uppdatera även `docs/CHANGELOG.md` så att repo och app visar samma historik
