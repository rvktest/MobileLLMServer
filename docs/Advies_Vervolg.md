# Advies voor wat hierna moet gebeuren

## Prioriteit in een zin

Werk nu niet eerst aan meer features, maar aan betrouwbaarheid van de serverlaag, een scherper API-contract en een reproduceerbaar testpad. Dat levert veel meer op dan extra UI of extra modelimport-opties.

## Advies per fase

## Fase 1 - Maak de huidige slice betrouwbaar

Dit is de hoogste prioriteit. Zonder deze laag blijft alles wat je hierna bouwt fragiel.

### 1. Definieer het servercontract expliciet

Leg in documentatie en code vast:

- welke endpoints ondersteund worden;
- welke requestvelden daadwerkelijk gebruikt worden;
- welke velden genegeerd worden;
- of maar een model tegelijk actief kan zijn;
- hoe streaming werkt;
- welke foutcodes clients kunnen verwachten.

Waarom dit eerst moet:

- tooling-integraties hangen van contractduidelijkheid af;
- zonder contract ga je later backward compatibility kapotmaken;
- de huidige README verkoopt meer compatibiliteit dan de code nu levert.

### 2. Voeg health- en readiness-endpoints toe

Minimaal:

- `GET /healthz`
- `GET /readyz`
- eventueel `GET /v1/server/status`

Deze endpoints moeten onderscheid maken tussen:

- app draait;
- server draait;
- model is geselecteerd;
- model is geinitialiseerd;
- model is bezig;
- server is klaar om requests te accepteren.

### 3. Kies en implementeer een concurrency policy

Je moet nu expliciet beslissen welk gedrag je wilt bij meerdere requests:

- slechts een request tegelijk, rest krijgt `409` of `429`;
- queue met een beperkte diepte;
- meerdere requests tegelijk als de runtime dat echt aan kan.

Mijn advies voor de eerstvolgende stap:

- begin met exact een actieve inference tegelijk;
- geef een duidelijke foutmelding bij parallelle requests;
- log en documenteer dit.

Dat is simpeler, voorspelbaar en past beter bij telefoons als host.

### 4. Maak modelstatus expliciet en persistent

Sla vast of:

- de server aan staat;
- welk model gekozen is;
- of auto-start gewenst is;
- of het model nog warm is of opnieuw geinitialiseerd moet worden.

Nu zit te veel state alleen in memory. Voor een mobiele server is dat te fragiel.

## Fase 2 - Maak het bruikbaar vanaf externe clients

### 5. Maak modelselectie via API mogelijk

Je hebt hier twee opties:

- eenvoudig: een endpoint om het actieve model te zetten;
- beter: request-level modelselectie, waarbij de server zelf zonodig het juiste model activeert.

Mijn advies:

- begin met een expliciet admin-endpoint om het actieve model te wisselen;
- documenteer dat slechts een model tegelijk warm kan zijn;
- voeg later pas automatische wissel op requestniveau toe.

### 6. Voeg minimale authenticatie toe

Voor lokaal netwerkgebruik is dit een must zodra je buiten pure demo gaat.

Pragmatische aanpak:

- optionele bearer token of API key;
- token zichtbaar/kopieerbaar in de app;
- mogelijkheid om token te regenereren;
- toggle om auth uit te zetten voor pure offline demo.

### 7. Maak netwerkconfiguratie instelbaar

Minimaal instelbaar:

- poort;
- auth aan/uit;
- eventueel alleen luisteren op wifi/LAN-context;
- CORS-beleid.

Hardcoded `8080` is prima voor nu, maar geen eindstaat.

### 8. Ondersteun client-discovery en gebruiksgemak

Denk aan:

- copy button voor API base URL;
- QR-code met base URL;
- overzicht van telefoon-IP en poort;
- melding als telefoon van IP verandert;
- duidelijke foutmelding als client op ander netwerk zit.

## Fase 3 - Maak het echt een developer tool

### 9. Breid de OpenAI-compatibiliteit gericht uit

Ga niet blind elk endpoint nabouwen. Kies wat je doelgroep nodig heeft.

Waarschijnlijk relevant:

- consistente `chat/completions` support;
- betere verwerking van message history;
- duidelijke streaming-semantiek;
- modelmetadata in `/v1/models`;
- mogelijk later embeddings of responses API, alleen als concrete clients dat vragen.

### 10. Voeg observability toe

Minimaal:

- request logging;
- duur per request;
- modelnaam;
- statuscode;
- fouten met oorzaakcategorie.

Idealiter ook:

- eenvoudige diagnostiek in de UI;
- knop om serverlogs te exporteren;
- counters voor requests, errors en average latency.

### 11. Voeg tests toe op drie niveaus

Minimaal nodig:

- unit tests voor request parsing en foutafhandeling;
- integratietests voor Ktor-routing;
- een handmatig of geautomatiseerd Android smoke testpad.

Waar tests specifiek op moeten zitten:

- `/v1/models` gedrag;
- lege of ongeldige requests;
- stream versus non-stream responses;
- request op niet-actief model;
- gedrag wanneer model nog niet klaar is;
- gedrag bij parallelle requests.

## Fase 4 - Maak de fork productmatig helder

### 12. Schrijf een echte fork-roadmap

Leg vast dat MobileLLMServer niet "AI Edge Gallery met een extra knop" is, maar:

- een mobiele lokale LLM host;
- gericht op LAN-gebruik;
- bedoeld voor dev/test en lokale agents;
- geen publieke internetservice;
- geen volledige vervanging van cloud-hosting.

### 13. Corrigeer de VS Code-propositie

Wees hier precies:

- noem OpenAI-compatibele VS Code-clients die wel werken;
- noem GitHub Copilot niet alsof je simpelweg een base URL kunt invullen;
- geef voorbeeldconfiguraties voor de clients die je echt ondersteunt.

### 14. Maak de import-story af

Nu is er al UI voor Hugging Face URL import, maar de feature is nog niet af. Kies een van deze richtingen:

- haal het tijdelijke pad uit de UI tot het echt werkt;
- of maak het af in de eerstvolgende fase.

Half-aanwezige flows zorgen voor twijfel over de rest van het product.

## Concreet geprioriteerde backlog

Als ik dit zou omzetten naar de eerstvolgende werkblokken, zou ik exact deze volgorde aanhouden:

1. Documenteer het echte API-contract en supported clients.
2. Voeg `healthz` en `readyz` toe.
3. Implementeer single-request concurrency policy met heldere foutrespons.
4. Maak actieve modelstatus duurzaam en zichtbaar.
5. Voeg optionele API key auth toe.
6. Maak poort en basis serverinstellingen configureerbaar.
7. Voeg Ktor route tests toe.
8. Voeg Postman-voorbeelden en VS Code-configs toe.
9. Werk modelselectie via API uit.
10. Maak Hugging Face URL import af of haal het tijdelijk weg.

## Wat ik expliciet nog niet eerst zou doen

Nog niet prioriteren:

- uitgebreide nieuwe UI-schermen;
- meerdere nieuwe endpoints zonder concrete clientbehoefte;
- internet-exposure buiten LAN;
- iOS-pariteit;
- grote refactor weg van de bestaande Gallery-architectuur.

Dat zijn pas verstandige investeringen nadat de basisserver betrouwbaar is.