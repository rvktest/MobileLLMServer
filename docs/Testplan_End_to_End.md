# Stappenplan om MobileLLMServer echt te testen

## Doel van dit testplan

Dit plan brengt je van broncode naar een werkende Android-installatie en daarna naar een bruikbare lokale API-test vanaf je laptop.

Het plan is bewust praktisch opgezet:

1. app bouwen;
2. app installeren op Android;
3. model laden;
4. server starten;
5. API testen via Postman;
6. VS Code koppelen aan een ondersteunde OpenAI-compatibele client;
7. een echte dev-taak laten uitvoeren.

## Belangrijke realiteitscheck vooraf

### 1. GitHub Copilot zelf is hier waarschijnlijk niet de juiste target

De officiele GitHub Copilot-extensie in VS Code gebruikt geen simpele configureerbare OpenAI-base-URL zoals generieke OpenAI-clients dat doen. Verwacht dus niet dat je de officiele Copilot-plugin direct op deze lokale telefoonserver kunt richten.

Voor dit project zijn realistischer:

- Continue
- Cline
- Roo Code of vergelijkbare OpenAI-compatibele tools
- eigen scripts of apps die OpenAI-achtige endpoints aanroepen

### 2. De huidige app ondersteunt nog een smalle happy path

De huidige server is geschikt voor eerste tests, maar nog niet voor elke OpenAI-client. Test dus eerst bewust met simpele requests.

## Deel 1 - Android build voorbereiden

## Stap 1 - Controleer lokale tools

Benodigd:

- Android Studio
- Android SDK voor API 35 of compatibele setup
- JDK 17 of JDK 21
- ADB
- een Android-telefoon met Android 12+ voor deze appbasis

Let op:

- een nieuwere JDK is niet automatisch beter voor deze stack;
- in deze repository is Java 26 geen veilige keuze gebleken voor Gradle-validatie.

## Stap 2 - Configureer Hugging Face placeholders als je modeldownloads wilt gebruiken

Volgens de huidige repo moet je voor downloadfunctionaliteit eigen Hugging Face app-gegevens invullen.

Controleer en wijzig:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt`
- `Android/src/app/build.gradle.kts`

Vervang in elk geval de placeholderwaarden voor:

- client ID
- redirect URI
- redirect scheme

Als je geen downloadflow wilt testen en een lokaal model handmatig importeert, kun je deze stap mogelijk tijdelijk omzeilen voor de eerste servertest.

## Stap 3 - Open het Android-project

Open in Android Studio deze map:

- `Android/src`

Niet de repository-root, maar de Android Gradle root.

## Stap 4 - Bouw een debug APK

Gebruik in Android Studio een normale debug build, of vanaf terminal in de Android-root:

```powershell
./gradlew assembleDebug
```

Op Windows kan dat ook zijn:

```powershell
.\gradlew.bat assembleDebug
```

Verwachte outputlocatie is typisch onder:

- `Android/src/app/build/outputs/apk/debug/`

## Deel 2 - App installeren op Android

## Stap 5 - Zet USB debugging aan

Op je Android-telefoon:

- activeer Developer Options;
- zet USB debugging aan;
- verbind telefoon met je laptop.

## Stap 6 - Controleer ADB-verbinding

```powershell
adb devices
```

Je telefoon moet als device zichtbaar zijn.

## Stap 7 - Installeer de APK

```powershell
adb install -r Android/src/app/build/outputs/apk/debug/app-debug.apk
```

Als de bestandsnaam anders is, pak de exacte debug-APK uit de outputmap.

## Deel 3 - Model klaarzetten in de app

Je hebt nu twee praktische routes.

## Route A - Model downloaden via de app

Gebruik deze route als je Hugging Face config klopt en de appdownloadflow werkt.

Stappen:

1. Open de app.
2. Accepteer eventuele terms/dialogs.
3. Open modelbeheer.
4. Download een ondersteund model.
5. Wacht tot download en initialisatie klaar zijn.
6. Selecteer het model actief in de app.

## Route B - Model lokaal importeren

Gebruik deze route als je sneller serverfunctionaliteit wilt testen zonder volledige online downloadflow.

Belangrijk:

- alleen MediaPipe-compatibele `.task`, `.litertlm` of `.bin` files zijn relevant;
- de huidige branch accepteert deze extensies in validatie;
- Hugging Face URL import is in de huidige UI nog niet echt beschikbaar.

Stappen:

1. Zorg dat je een compatibel modelbestand hebt.
2. Gebruik de importflow in de app voor lokaal bestand.
3. Vul de relevante LLM-config in bij import.
4. Selecteer het geimporteerde model.

## Stap 8 - Verifieer dat het model echt actief is

De server gebruikt in de huidige branch het actieve geinitialiseerde model uit de app. Je moet dus niet alleen een model hebben gedownload of geimporteerd, maar ook zorgen dat dit model daadwerkelijk geselecteerd en klaar is.

De huidige implementatie bewaart ook de laatst gekozen modelnaam, zodat die selectie na opnieuw laden van modellen teruggezet kan worden.

## Deel 4 - Server starten en netwerk controleren

## Stap 9 - Start de server in de app

Gebruik de startknop op het homescreen bij Server Status.

Controleer:

- status springt naar RUNNING;
- er verschijnt een foreground notification;
- de app toont een Local API URL.

De huidige implementatie bewaart daarnaast of de server actief was, zodat een herstelflow in volgende sessies mogelijk is.

## Stap 10 - Controleer telefoon-IP

Gebruik het IP dat de app toont. Als dat onlogisch lijkt, controleer op de telefoon handmatig het wifi-IP.

Let op:

- telefoon en laptop moeten op hetzelfde lokale netwerk zitten;
- VPN kan roet in het eten gooien;
- mobiele data helpt hier meestal niet.

## Deel 5 - API testen met Postman

Gebruik als base URL:

```text
http://<phone-ip>:8080/v1
```

## Stap 11 - Test `GET /v1/models`

Test eerst de nieuwe serverstatus-endpoints voordat je naar `v1`-endpoints gaat.

## Stap 11a - Test `GET /healthz`

Request:

- Method: `GET`
- URL: `http://<phone-ip>:8080/healthz`

Verwacht:

- HTTP 200
- JSON dat aangeeft dat de server live is

## Stap 11b - Test `GET /readyz`

Request:

- Method: `GET`
- URL: `http://<phone-ip>:8080/readyz`

Verwacht:

- HTTP 200 als de server echt klaar is voor inference
- HTTP 503 als er nog geen model geselecteerd is, het model nog initialiseert, of de server niet klaar is

Mogelijke statuswaarden in de body:

- `ready`
- `busy`
- `model_initializing`
- `no_model_selected`
- `server_stopped`

## Stap 11c - Test `GET /v1/models`

Request:

- Method: `GET`
- URL: `http://<phone-ip>:8080/v1/models`

Verwacht:

- HTTP 200
- JSON met `data` lijst

Als dit niet werkt:

- check of server echt draait;
- check of telefoon en laptop hetzelfde LAN gebruiken;
- check firewall/netwerkisolatie;
- check of de app nog op de voorgrond-notification draait.

## Stap 12 - Test `POST /v1/chat/completions`

Headers:

- `Content-Type: application/json`

Body:

```json
{
  "model": "<exact-model-id-uit-v1-models>",
  "stream": false,
  "messages": [
    {
      "role": "user",
      "content": "Schrijf een korte samenvatting van wat MobileLLMServer probeert te doen."
    }
  ]
}
```

Verwacht:

- HTTP 200
- JSON met `choices[0].message.content`

Let op:

- in de huidige branch werkt dit alleen goed voor het actieve model;
- als het model nog niet geinitialiseerd is, krijg je fouten of timeouts;
- message history wordt nog beperkt verwerkt.

## Stap 13 - Test streaming

Gebruik dezelfde endpoint maar met:

```json
"stream": true
```

Niet elke Postman-setup toont SSE ideaal. Als Postman onhandig is, test streaming ook eens met een simpele client of curl-achtige tool.

## Deel 6 - VS Code koppelen

## Stap 14 - Kies een client die echt met OpenAI-achtige endpoints werkt

Aanbevolen eerste keuze:

- Continue
- Cline

Niet eerste keuze voor deze fork:

- officiele GitHub Copilot-extensie

## Stap 15 - Configureer de base URL

Gebruik:

```text
http://<phone-ip>:8080/v1
```

Als de client een API key vereist maar jouw server nog geen auth heeft, gebruik dan alleen wat de client toestaat voor keyless custom endpoints. Sommige clients laten een dummy key toe.

## Stap 16 - Kies een simpele eerste taak

Test niet meteen complexe agentic workflows. Begin met een simpele codegerelateerde prompt, bijvoorbeeld:

```text
Lees deze functie en geef drie concrete verbeterpunten voor foutafhandeling en testbaarheid.
```

Of:

```text
Schrijf een korte unit test voor deze parserfunctie.
```

Doel van deze stap:

- verifiëren dat de editorclient requests kan sturen;
- verifiëren dat responses stabiel terugkomen;
- zien of latency en kwaliteit bruikbaar zijn.

## Stap 17 - Test daarna een echte dev-taak

Bijvoorbeeld:

- laat een kleine refactor voorstellen;
- laat een test genereren;
- laat een README-sectie herschrijven;
- laat een bugfix-plan uitwerken.

Beperk eerst de scope. Een telefoon-hosted model zal vaak minder sterk en trager zijn dan cloudmodellen, dus test eerst korte taken met weinig context.

## Deel 7 - Praktische debug-checklist

Als iets niet werkt, loop deze lijst af.

### Build faalt

- check Hugging Face placeholders;
- check Android SDK en gebruik een ondersteunde JDK, bij voorkeur 17 of 21;
- vermijd Java 26 voor deze stack tenzij je expliciet hebt gevalideerd dat het werkt;
- open de juiste Gradle-root (`Android/src`).

### App installeert niet

- check `adb devices`;
- check of een oudere package botst;
- installeer met `adb install -r`.

### Server lijkt te draaien maar laptop kan niet verbinden

- check of laptop en telefoon op hetzelfde wifi-netwerk zitten;
- check of getoonde IP correct is;
- check VPN;
- test eerst `GET /v1/models`.

### `/v1/models` werkt maar chat niet

- check of een model echt geselecteerd en geinitialiseerd is;
- gebruik exact de model-id uit de response;
- houd eerste prompt simpel en tekst-only.

### VS Code client werkt niet

- verifieer eerst dat Postman werkt;
- controleer of de client echt OpenAI-compatibele custom endpoints ondersteunt;
- probeer een dummy API key als de client dat vereist;
- begin met non-streaming als streaming lastig doet.

## Aanbevolen concrete acceptatiecriteria

Ik zou deze branch pas als "goed testbaar" beschouwen als jij dit allemaal succesvol kunt afvinken:

1. Debug APK bouwen zonder handmatig zoeken naar verborgen buildstappen.
2. APK via ADB installeren.
3. Ten minste een model downloaden of importeren.
4. Server starten en een bruikbare lokale URL zien.
5. Met Postman `GET /healthz` succesvol uitvoeren.
6. Met Postman `GET /readyz` succesvol uitvoeren.
7. Met Postman `GET /v1/models` succesvol uitvoeren.
8. Met Postman `POST /v1/chat/completions` succesvol uitvoeren.
9. In VS Code een ondersteunde client verbinden.
10. Vanuit VS Code minstens een kleine developmenttaak uitvoeren.

Pas als dat pad stabiel loopt, is de volgende investeringsfase zinvol.