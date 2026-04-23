# Analyse huidige staat van MobileLLMServer

## Samenvatting

De huidige branch is een geloofwaardige eerste verticale slice van het idee: een Android-app die een lokaal draaiend mobiel LLM-model via een OpenAI-achtig LAN-endpoint beschikbaar maakt. De branch laat zien dat de kernrichting klopt:

- de app kan een foreground server-service starten;
- er is een OpenAI-achtig API-oppervlak aanwezig;
- bestaande modelselectie en modelimport uit de Gallery-basis worden hergebruikt;
- de UI laat serverstatus en lokale URL zien.

Tegelijk is dit nog duidelijk een eerste opzet en nog geen stabiele "mobile LLM appliance" voor dagelijks gebruik vanaf laptop, Postman of VS Code. De basis is er, maar de branch mist nog lifecycle-hardening, API-volledigheid, beveiliging, testbaarheid en client-documentatie.

## Wat is goed gedaan

### 1. De fork heeft een scherpe productrichting

De positionering in de README is direct en bruikbaar: "run local LLMs on your phone and serve them to your IDE". Dat is veel sterker dan een generieke demo-positionering en maakt meteen duidelijk waarom deze fork bestaat.

### 2. Er is gekozen voor een pragmatische uitbreiding op de bestaande app

In plaats van een compleet nieuwe app te bouwen, breidt deze branch de bestaande AI Edge Gallery uit met een extra serverlaag. Dat is verstandig:

- bestaand modelbeheer blijft bruikbaar;
- bestaande runtimes blijven bruikbaar;
- bestaande import/download-paden blijven bruikbaar;
- de fork blijft dicht genoeg bij upstream om later nog dingen terug te kunnen trekken of opnieuw te mergen.

Voor een eerste branch is dat de juiste strategische keuze.

### 3. De server zit op een logisch Android-pad

De combinatie van een foreground service en een Ktor-server is functioneel logisch voor dit doel.

Relevante signalen in de code:

- er is een aparte service voor server lifecycle;
- de server draait foreground zodat Android hem minder snel wegdrukt;
- er worden wake lock en wifi lock gepakt om langere sessies mogelijk te maken;
- de home UI kan start/stop aansturen.

Dat laat zien dat er niet alleen aan de API is gedacht, maar ook aan het feit dat dit op een telefoon moet blijven draaien.

### 4. Er is al een bruikbaar minimum aan OpenAI-compatibiliteit

De branch implementeert al twee endpoints:

- `GET /v1/models`
- `POST /v1/chat/completions`

Daarnaast is er ook streaming-ondersteuning via SSE chunks. Dat is goed, want veel clients verwachten minstens basisstreaming.

### 5. De UI-koppeling is klein en duidelijk

De serverbediening is compact toegevoegd aan de home screen in plaats van direct een grote nieuwe settings- of admin-sectie te introduceren. Voor een eerste branch is dat goed: weinig UI-oppervlak, wel bruikbaarheid.

### 6. Import van lokale modellen is slim meegenomen

Dat de app ook geimporteerde modellen via `/v1/models` exposeert, en `.bin` nu in de validatie meeneemt, is belangrijk. Voor een project als dit wil je niet volledig afhankelijk zijn van alleen vooraf ingebakken allowlists.

## Wat minder goed is gedaan

### 1. De API-claims zijn groter dan de huidige functionele dekking

De branch noemt een OpenAI-compatibele API, maar de huidige implementatie is eerder "OpenAI-achtig voor een smalle happy path" dan echt breed compatibel.

Concreet:

- alleen de laatste `user` message wordt gebruikt;
- `system` en `assistant` history worden niet meegenomen;
- `max_tokens` en `temperature` worden wel geparsed maar niet zichtbaar toegepast in de serverlaag;
- multimodale request-inhoud via API is niet uitgewerkt;
- modelkeuze per request werkt feitelijk niet echt, omdat alleen het actieve geinitialiseerde model gebruikt kan worden.

Dat is op zich acceptabel voor een eerste versie, maar de documentatie moet hier strakker over zijn dan nu.

### 2. Modelrouting en `/v1/models` zijn nog niet in lijn met elkaar

`/v1/models` geeft een lijst van modellen terug, maar `POST /v1/chat/completions` accepteert in de praktijk alleen het op dat moment actieve model in memory. Daardoor ontstaat een contract-mismatch:

- de client ziet meerdere modellen;
- de server kan er feitelijk maar een tegelijk gebruiken;
- modelwisseling gebeurt niet via de API maar via de app-UI.

Voor een developer-tooling use case is dit een belangrijk gat.

### 3. Serverstatus en actief model leven alleen in runtime-geheugen

De runtime state zit in een singleton in memory. Dat betekent dat gedrag rond app-restart, process death en service-herstart nog niet robuust is.

Risico's:

- serverstatus kan na lifecycle-events niet meer overeenkomen met de werkelijkheid;
- actief model is niet duurzaam vastgelegd;
- clients weten niet of de server wel "ready" is of alleen "running".

### 4. Concurrency en request-serialisatie zijn nog onduidelijk

De modelstructuur bevat zelf al een TODO richting queueing/cleanupbeheer. Dat is hier relevant, want deze app wil van lokale demo naar netwerkserver gaan.

Op dit moment is niet duidelijk of meerdere clients of meerdere requests veilig tegelijk kunnen:

- hetzelfde modelinstance-object wordt gedeeld;
- er is geen expliciete request queue;
- er is geen concurrency policy naar buiten toe;
- er is geen backpressure of "busy"-respons.

Voor LAN-gebruik vanuit editor tooling is dat een kernpunt.

### 5. Security is bewust minimaal, maar nog te open

De README noemt trusted LAN, maar de implementatie laat op dit moment heel veel toe:

- geen API key of token;
- brede CORS (`anyHost()`);
- vaste poort;
- geen client-auth;
- geen expliciete netwerk-scope of allowlist.

Voor een eerste demo is dat verdedigbaar. Voor echt gebruik op een thuis- of kantoor-LAN is dit onvoldoende.

### 6. Documentatie is nog niet in sync met de fork-doelstelling

Voorbeelden:

- `DEVELOPMENT.md` is nog vooral gericht op Hugging Face OAuth voor modeldownload;
- de README noemt een Android project-locatie die niet past bij een normale lokale checkout;
- de branch noemt een voorbereid pad voor Hugging Face URL import, terwijl de UI zelf nog meldt dat deze import "not available yet" is.

Dat maakt testen en onboarden onnodig lastig.

### 7. Er zijn geen zichtbare tests voor de nieuwe serverlaag

Er zijn in de huidige repo geen testbestanden zichtbaar voor de toegevoegde serverfunctionaliteit. Voor een branch die gedrag op netwerklaag toevoegt is dat een serieus gemis.

## Wat vooral nog mist

### Productmatig ontbreekt

- een heldere productdefinitie van wat "OpenAI-compatible" hier precies betekent;
- een duidelijke afbakening voor welke clients wel en niet ondersteund worden;
- een expliciete uitspraak over GitHub Copilot versus andere VS Code-clients.

Belangrijk: de officiele GitHub Copilot-extensie is niet simpelweg een OpenAI-client die je naar een lokale URL wijst. Voor deze fork zijn tools zoals Continue, Cline of andere OpenAI-compatibele clients realistischer dan "VS Code met Copilot" in letterlijke zin.

### Technisch ontbreekt

- modelkeuze via API;
- readiness/health endpoints;
- queueing of single-flight request policy;
- auth voor LAN-gebruik;
- serverconfiguratie in de UI;
- logging en observability;
- foutafhandeling voor lifecycle- en netwerkcases;
- testdekking op API-niveau.

### Operationeel ontbreekt

- een echt end-to-end testpad;
- een Postman-collectie of voorbeeldrequests;
- documentatie voor VS Code-clients die echt met een OpenAI-compatibele endpoint kunnen praten;
- releasepad voor APK/AAB en versiebeheer van de fork-specifieke features.

## Eindoordeel

Als eerste branch is dit goed werk. De branch bewijst dat de hoofdgedachte levensvatbaar is en dat de fork meer is dan alleen een rename van de originele app. De belangrijke stap van "lokale mobiele inference" naar "mobiele LAN-host voor ontwikkeltools" is echt gezet.

Maar de branch is nog geen afgerond product en nog niet sterk genoeg als betrouwbare lokale API-server voor dagelijks ontwikkelwerk. De grootste ontbrekende laag zit niet in nog meer UI, maar in server-hardening, API-contract, testbaarheid, client-compatibiliteit en documentatie.

Kort:

- als prototype: goed;
- als eerste demo voor intern gebruik: bruikbaar;
- als stabiele developer tool: nog niet af.