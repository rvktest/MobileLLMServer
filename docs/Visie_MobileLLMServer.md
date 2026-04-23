# Visie op wat MobileLLMServer uiteindelijk moet worden

## Kernvisie

MobileLLMServer moet geen "demo-app die toevallig ook een API heeft" blijven. Het moet uitgroeien tot een compacte, betrouwbare lokale AI-host die op een telefoon draait en zich op het netwerk gedraagt als een kleine inference appliance voor ontwikkelaars.

De telefoon is dan niet alleen een device waarop je een model kunt testen, maar een draagbare lokale modelserver die:

- volledig on-device inference doet;
- modellen lokaal beheert;
- op trusted LAN bereikbaar is;
- door standaard developer tools aangesproken kan worden;
- voorspelbaar en reproduceerbaar werkt.

## Gewenste eindstaat

In de eindstaat doet de app vijf dingen goed.

### 1. Lokaal model hosten

De app kan een of meerdere ondersteunde on-device modellen beheren, initialiseren, warm houden en veilig vrijgeven. Daarbij hoort:

- duidelijk modelbeheer;
- heldere status per model;
- inzicht in geheugen- en performance-impact;
- snelle herstart na app-heropening.

### 2. Gestandaardiseerde lokale API aanbieden

De app biedt een klein maar betrouwbaar API-oppervlak dat genoeg compatibiliteit heeft voor echte clients. Niet per se alles, maar wel stabiel, duidelijk en goed gedocumenteerd.

Doel:

- eenvoudige tools zoals Postman werken meteen;
- OpenAI-compatibele editors en agent-tools kunnen verbinden;
- ontwikkelaars kunnen lokale prompts, regressies en flows testen zonder cloudmodel.

### 3. Ontwikkeltools echt ondersteunen

MobileLLMServer moet eindigen als een praktische tool voor:

- promptontwikkeling;
- evaluatie van modelgedrag;
- lokale agent- en automation-tests;
- experimenten met editor-integraties;
- privacygevoelige of offline demo's.

Het product moet daarom niet alleen "een endpoint hebben", maar ook:

- goede foutmeldingen geven;
- readiness kunnen rapporteren;
- stabiele streaming leveren;
- bruikbare documentatie voor clients aanbieden.

### 4. Veilig genoeg zijn voor lokaal netwerkgebruik

Niet internet-first, maar wel verantwoord op LAN.

Dat betekent:

- standaard duidelijke waarschuwing dat dit alleen voor trusted LAN is;
- optionele auth;
- inzicht in wie kan verbinden;
- geen onnodig open configuratie.

### 5. Mobiel en zuinig genoeg blijven

De app moet server kunnen zijn zonder de aard van een telefoon te negeren.

Dus:

- duidelijke energiekosten;
- beheer van wake/wifi gedrag;
- goed gedrag bij scherm uit;
- nette recovery na netwerk- of lifecycle-wijzigingen.

## Niet de ambitie

MobileLLMServer hoeft waarschijnlijk niet te worden:

- een publieke internet-hostingdienst;
- een volledige vervanger van OpenAI of cloud inference;
- een generieke enterprise gateway;
- een product dat elk mogelijk OpenAI-endpoint 1-op-1 kloont.

De kracht zit juist in focus: on-device, lokaal, ontwikkelgericht, eenvoudig te deployen.

## Doelgroep

De meest logische primaire doelgroep is:

- developers die lokale LLM-integraties willen testen;
- mensen die een privacyvriendelijke lokale modelhost willen;
- teams die editor agents of API-clients tegen een goedkoop lokaal endpoint willen laten praten;
- demo- en workshopgebruik waarbij internetafhankelijkheid ongewenst is.

## Succescriteria

De app is pas echt geslaagd als een developer dit zonder frictie kan doen:

1. APK installeren op Android-telefoon.
2. Model downloaden of importeren.
3. Server starten.
4. Vanaf laptop de API bereiken.
5. In Postman succesvol requests sturen.
6. In VS Code een ondersteunde OpenAI-compatibele client configureren.
7. Een echte developmenttaak laten uitvoeren tegen het model op de telefoon.

Als dat pad betrouwbaar werkt, dan klopt de productrichting.

## Strategisch advies voor de positionering

Ik zou de app extern zo positioneren:

"Run local LLMs on your Android phone and expose them as a small trusted-LAN developer API for testing, editor tools and local agents."

Dat is concreet, eerlijk en sterk genoeg zonder te veel te beloven.