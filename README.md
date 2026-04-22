# TicketConsoleCloudBan

Ein CloudNet-v4-Modul mit eingebautem Webpanel für:

- Task erstellen, bearbeiten und löschen
- Services aus Tasks erstellen
- Services starten, stoppen, neustarten und löschen
- Konsolen-Logs pro Service ansehen und Befehle senden
- Cluster-Nodes im Panel anzeigen
- Tickets erstellen, kommentieren, zuweisen, abschließen und archivieren
- Zentrale Cloud-Bans anlegen und deaktivieren
- Panel-Login mit Benutzern, Gruppen und Rechteverwaltung
- Optionale 2-Faktor-Authentifikation pro Panel-Benutzer per E-Mail-Code oder Google Authenticator App
- Benutzerprofil mit E-Mail für Passwort-vergessen-Prozesse und optionalem Minecraft-Account
- Auditlogs und Archivansichten für Tickets und Entbannungsanträge
- LiteBans-Unterseite mit synchronisierten LiteBans-Bans
- Öffentliches Entbannungsformular auf separatem Port mit Statusseite und HTML-Mail
- Passwort-vergessen-Flow mit Reset-Token und optionalem SMTP-Mailversand
- LuckPerms-Unterseite mit Subject-Sync, Aktionsqueue und Auditlog
- Panel-Teleport-Button für Ticket-Ersteller über die Velocity-Aktionsqueue
- CraftplayQuests-Tab als Browser-Gegenstelle zur Plugin-Web-API

Die UI ist direkt im Modul enthalten und wird über einen kleinen HTTP-Server ausgeliefert.

Zusätzlich enthält das Repository ein Velocity-Companion-Plugin für Ingame-Tickets, LiteBans-Prüfung, LiteBans-Sync und Proxy-LuckPerms sowie ein Purpur/Paper-Companion-Plugin für lokale Unterserver-LuckPerms-Datenbanken.

## Architektur

Das Modul ist für ein CloudNet-Cluster gedacht, in dem Velocity als Proxy und Purpur als Spielserver laufen. Du installierst das Modul auf einer CloudNet-Node, die den Cluster voll sehen kann. Über die CloudNet-v4-APIs werden dann clusterweit Tasks und Services verwaltet.

Das Panel ist bewusst als MVP gebaut:

- CloudNet Task-CRUD ist vorhanden
- Der CloudNet-Tab zeigt die CloudNet-Kennzahlen nur dort, bietet eine Task-Auswahl per Dropdown und öffnet separate Unterseiten zum Bearbeiten oder Anlegen von Tasks
- Service-Management ist vorhanden
- Service-Konsole ist vorhanden
- Node-Übersicht ist vorhanden
- Ticket-System ist vorhanden und speichert den Unterserver/Service, auf dem ein Ticket erstellt wurde
- Geschlossene Tickets werden im Ticket-Archiv angezeigt
- Cloud-Ban-Verwaltung ist vorhanden
- Panel-Login mit Gruppenrechten ist vorhanden
- Benutzer können 2FA im eigenen Profil aktivieren und zwischen E-Mail-Code oder Google Authenticator App wählen
- LiteBans-Bans können über das Velocity-Plugin ins Panel synchronisiert werden
- LiteBans-Unban und -Verlängerung laufen über eine Panel-Aktionsqueue, die Velocity abarbeitet
- Entbannungsanträge prüfen die Random-LiteBans-ID gegen den Spielernamen und erlauben nur einen Antrag je Ban-ID/Spieler
- Angenommene, abgelehnte und geschlossene Entbannungsanträge werden im Ban-Archiv angezeigt
- Beweise können lokal, per SFTP oder per OneDrive OAuth gespeichert werden; eine manuelle OneDrive-Upload-URL bleibt als Fallback möglich
- LuckPerms-Gruppen und geladene Spieler können pro Proxy und pro Purpur-Unterserver ins Panel synchronisiert werden
- LuckPerms-Permissions und Parent-Gruppen können im Panel als Queue-Aktion erstellt werden
- Unterserver mit eigener LuckPerms-Datenbank werden über das Purpur-Companion-Plugin gezielt per `server.id` angesteuert
- Das Panel kann Teamler per Teleport-Queue zum Ticket-Ersteller schicken, sofern der Teamler online ist und Velocity den Teleport-Befehl ausführen kann
- CraftplayQuests kann als eigener Tab eingebunden werden und liest Quests, Kategorien, Details sowie Roh-YAML über einen geschützten Panel-Proxy
- Passwort-Reset läuft mit gehashten Einmal-Tokens und optionaler SMTP-Mail
- Live-Konsole läuft aktuell per Polling auf dem Log-Cache

Noch nicht enthalten:

- Rootserver-SSH oder echte Root-Console
- Eigene Purpur-Ban-Durchsetzung ohne LiteBans
- Vollständiger LuckPerms-Webeditor mit allen Metadaten/Expiry/Context-Kombinationen
- Automatisches Unban nach angenommenem Entbannungsantrag

Die Struktur ist aber so angelegt, dass diese Bausteine später sauber angebunden werden können.

## Build

Mit Maven:

```bash
mvn -DskipTests package
```

Artefakt:

```text
target/TicketConsoleCloudBan.jar
```

Velocity-Plugin:

```bash
cd velocity-plugin
mvn -DskipTests package
```

Artefakt:

```text
velocity-plugin/target/TicketConsoleCloudBan-Velocity.jar
```

Purpur/Paper-Plugin:

```bash
cd purpur-plugin
mvn -DskipTests package
```

Artefakt:

```text
purpur-plugin/target/TicketConsoleCloudBan-Purpur.jar
```

Optional liegt auch ein `build.gradle.kts` bei, falls du lieber mit Gradle arbeitest.

## Installation in CloudNet

1. Baue das Modul.
2. Kopiere `target/TicketConsoleCloudBan.jar` in den `modules/`-Ordner der CloudNet-Node.
3. Starte die Node neu oder lade das Modul über CloudNet neu.
4. Nach dem ersten Start erstellt das Modul seine `config.json` und `panel-users.json` im Modul-Datenordner.
5. Das generierte API-Token wird beim Start ins Log geschrieben und ist für Integrationen gedacht.
6. Beim ersten Start wird ein Panel-Admin erstellt. Benutzer `admin` und Passwort stehen einmalig im CloudNet-Log.
7. Öffne dann im Browser:

```text
http://DEINE-NODE-IP:8088
```

## Konfiguration

Die Modul-Konfiguration wird automatisch erstellt und sieht sinngemäß so aus:

```json
{
  "bindHost": "0.0.0.0",
  "bindPort": 8088,
  "consoleLineLimit": 250,
  "brandName": "Network Control",
  "brandLogoUrl": "",
  "panelStorageBackend": "SQL",
  "panelSqlJdbcUrl": "jdbc:mysql://127.0.0.1:3306/tccb_panel?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
  "panelSqlUsername": "tccb_panel",
  "panelSqlPassword": "",
  "panelSqlTable": "tccb_panel_data",
  "publicBaseUrl": "http://127.0.0.1:8088",
  "smtpEnabled": false,
  "smtpHost": "127.0.0.1",
  "smtpPort": 587,
  "smtpUsername": "",
  "smtpPassword": "",
  "smtpFrom": "panel@example.com",
  "smtpStartTls": true,
  "smtpSsl": false,
  "passwordResetTokenMinutes": 30,
  "appealEnabled": true,
  "appealBindHost": "0.0.0.0",
  "appealBindPort": 8090,
  "appealPublicBaseUrl": "http://127.0.0.1:8090",
  "appealMaxFiles": 3,
  "appealMaxFileBytes": 10485760,
  "appealEvidenceStorage": "LOCAL",
  "appealEvidenceLocalDirectory": "appeal-evidence",
  "appealEvidenceSftpHost": "",
  "appealEvidenceSftpPort": 22,
  "appealEvidenceSftpUsername": "",
  "appealEvidenceSftpPassword": "",
  "appealEvidenceSftpPrivateKeyPath": "",
  "appealEvidenceSftpRemoteDirectory": "/appeals",
  "appealEvidenceOneDriveUploadUrlTemplate": "",
  "appealEvidenceOneDriveBearerToken": "",
  "questEditorEnabled": false,
  "questEditorBaseUrl": "http://127.0.0.1:8095/api/craftplayquests/v1",
  "questEditorToken": "",
  "questEditorConnectTimeoutMillis": 3000,
  "questEditorReadTimeoutMillis": 5000,
  "apiTokens": [
    "dein-generiertes-token"
  ]
}
```

Das Panel nutzt einen eigenen Login. Der alte API-Token-Zugang bleibt für externe Tools oder ein späteres Velocity-/Purpur-Companion-Plugin erhalten und hat Vollzugriff.

### CraftplayQuests Browser-Editor

Der Quest-Tab im Panel nutzt die Web-API von CraftplayQuests und reicht Anfragen serverseitig weiter. Dadurch liegt der CraftplayQuests-Token nicht im Browser. Das Panel kann mehrere Questserver verwalten und im Questeditor per Serverauswahl umschalten.

Aktiviere im CloudNet-Modul in `config.json`:

```json
{
  "questEditorEnabled": true,
  "questEditorBaseUrl": "http://127.0.0.1:8095/api/craftplayquests/v1",
  "questEditorToken": "DERSELBE_TOKEN_WIE_IM_PLUGIN",
  "questEditorConnectTimeoutMillis": 3000,
  "questEditorReadTimeoutMillis": 5000
}
```

Die Werte aus `config.json` dienen als erste Standardverbindung. Weitere Questserver werden im Panel unter **Einstellungen -> CraftplayQuests Server** gepflegt. Dort hinterlegst du pro Minecraft-Server:

- Name im Panel
- IP oder Host
- API-Port
- API-Pfad, standardmäßig `/api/craftplayquests/v1`
- Token aus `web-editor.token`
- Timeouts

Im CraftplayQuests Plugin muss die `web-editor`-API ebenfalls aktiv sein und denselben Token verwenden. Zusätzlich kann jeder Plugin-Server in `config.yml` unter `server.name` einen eigenen Anzeigenamen setzen; dieser Name erscheint nach erfolgreicher Verbindung im Serverauswahlmenü des Quest-Tabs. Der Panel-Tab braucht das Recht `quests.editor.view`; Admins mit `*` haben es automatisch.

Panel-Daten wie Tickets, Entbannungsanträge, Teampanel-Benutzer, Panelgruppen, Gruppenrechte, Ban-/LiteBans-Snapshots, Aktionsqueues und LuckPerms-Bridge-Daten werden bei `panelStorageBackend=SQL` in der Tabelle `panelSqlTable` gespeichert. `SQL` ist die Standard-Speicherart. Beim Wechsel von `LOCAL` auf `SQL` importiert das Modul vorhandene lokale JSON-Dateien automatisch in die MySQL-Tabelle.

Für MySQL-Speicherung im Panel müssen in der CloudNet-Modul-`config.json` diese Werte gesetzt sein:

```json
{
  "panelStorageBackend": "SQL",
  "panelSqlJdbcUrl": "jdbc:mysql://MYSQL-HOST:3306/tccb_panel?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
  "panelSqlUsername": "tccb_panel",
  "panelSqlPassword": "DEIN_PASSWORT",
  "panelSqlTable": "tccb_panel_data"
}
```

Empfohlene MySQL-Vorbereitung:

```sql
CREATE DATABASE tccb_panel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tccb_panel'@'%' IDENTIFIED BY 'DEIN_PASSWORT';
GRANT ALL PRIVILEGES ON tccb_panel.* TO 'tccb_panel'@'%';
FLUSH PRIVILEGES;
```

Migration von lokalem Speicher nach MySQL:

1. CloudNet-Modul stoppen oder CloudNet-Node stoppen.
2. Modul-Datenordner sichern, besonders `tickets.json`, `ban-appeals.json`, `panel-users.json`, `panel-settings.json`, `permissions.json`, `bans.json` und `player-actions.json`.
3. In der Modul-`config.json` `panelStorageBackend` auf `SQL` setzen und JDBC-URL, Benutzer, Passwort und Tabelle eintragen.
4. Modul/Node starten. Die SQL-Tabelle wird automatisch erstellt und lokale JSON-Dateien werden beim ersten Laden importiert.
5. Im Log sollte pro Store eine Meldung wie `Lokaler Panel-Speicher tickets.json wurde nach SQL migriert.` erscheinen.
6. Wenn in `panelSqlTable` bereits Daten für denselben `store_key` liegen, werden lokale JSON-Dateien nicht darüber geschrieben. Für einen erneuten Import die SQL-Zeilen oder die Tabelle vorher leeren.

Wenn die SQL-Verbindung fehlschlägt, nutzt das Modul als Sicherheitsfallback weiter lokalen JSON-Speicher und schreibt eine Warnung ins Log. Für produktiven Betrieb solltest du nach dem Start prüfen, dass keine Fallback-Warnung geloggt wurde.

Wenn im CloudNet-Log `SQL Panel-Speicher ist nicht verfügbar, nutze lokalen JSON-Speicher als Fallback` steht, ist das Panel nicht in MySQL, sondern wieder in JSON gestartet. Prüfe dann:

- Kopiere wirklich `target/TicketConsoleCloudBan.jar` in CloudNet, nicht `target/original-TicketConsoleCloudBan.jar`. Nur das normale `TicketConsoleCloudBan.jar` enthält den MySQL-Treiber.
- Die Datenbank aus `panelSqlJdbcUrl` muss existieren, z.B. `tccb_panel`.
- `panelSqlUsername` und `panelSqlPassword` müssen für genau diesen Host erlaubt sein. Bei mehreren Rootservern ist `'tccb_panel'@'%'` oder die konkrete Rootserver-IP nötig, nicht nur `'tccb_panel'@'localhost'`.
- MySQL muss von der CloudNet-Node erreichbar sein. Firewall, Docker/Rootserver-Bind-IP und MySQL `bind-address` prüfen.
- Bei MySQL 8/9 sollte die JDBC-URL `allowPublicKeyRetrieval=true` enthalten, wenn kein SSL genutzt wird.
- Nach Änderungen Modul/Node neu starten, weil die Panel-Speicherart nur beim Modulstart geladen wird.

Ab neueren Builds schreibt das Modul die echte Ursache mit Stacktrace ins CloudNet-Log, z.B. `Access denied`, `Unknown database`, `Communications link failure` oder `ClassNotFoundException`.

Mailserver-, LiteBans-Datenbank- und Beweis-Speicherwerte können im Webpanel unter `Einstellungen` geändert werden. Dort gibt es auch eine Testmail-Funktion. Die Panel-Speicherart selbst wird beim Modulstart geladen und bleibt deshalb in der Modul-Konfiguration.

Für OneDrive ist die empfohlene Lösung die Microsoft-Anmeldung per Gerätecode. Lege dafür in Microsoft Entra eine App-Registrierung als Public Client an, trage die Client-ID im Panel ein und klicke unter `Einstellungen` auf `OneDrive verbinden`. Das Panel speichert danach nur den Refresh-Token und erneuert Access Tokens automatisch. Ein manuell eingetragener Bearer Token ist nur noch als Fallback gedacht, weil solche Tokens kurzlebig sind.

Panel-Titel, Logo-URL sowie Statusnamen und Statustexte für Entbannungsanträge können ebenfalls im Einstellungstab geändert werden. Das Logo wird als externe URL hinterlegt und direkt im Panel, auf der Entbannungsseite und in HTML-Mails verwendet.

Wenn `smtpEnabled=false` ist, werden Passwort-Reset-Links nicht per Mail versendet, sondern sicherheitshalber im CloudNet-Log ausgegeben. Für produktiven Betrieb solltest du `publicBaseUrl` auf deine Panel-Domain setzen und SMTP aktivieren. E-Mail-2FA nutzt dieselben SMTP-Einstellungen und sollte nur aktiviert werden, wenn der Mailversand funktioniert.

## Entbannungsformular

Das Entbannungsformular läuft bewusst auf einem eigenen HTTP-Port, damit du es getrennt vom Admin-Panel per Reverse Proxy auf eine eigene Subdomain legen kannst, zum Beispiel:

```text
https://unban.deinserver.de -> http://DEINE-NODE-IP:8090
```

Spieler geben dort ein:

- Random Ban-ID aus der Ban-Nachricht
- Spielername
- E-Mail-Adresse
- Begründung
- optionalen Video-Link
- optionale Beweisdateien

Das Formular akzeptiert einen Antrag nur, wenn die Random Ban-ID (`publicId`) und der Spielername zu einem aktiven LiteBans-Ban passen. Numerische LiteBans-Datenbank-IDs werden dabei bewusst nicht als Random-ID akzeptiert. Pro Random Ban-ID und Spielername ist maximal ein Antrag möglich. Nach dem Absenden bekommt der Spieler eine HTML-Bestätigungsmail mit einem persönlichen Statuslink.

Evidence-Speicher:

- `LOCAL` speichert Dateien im Modul-Datenordner unter `appeal-evidence`.
- `SFTP` speichert Dateien auf einem externen SFTP-Server. Dafür müssen Host, Benutzer und Passwort oder Private-Key-Pfad gesetzt sein.
- `ONEDRIVE` nutzt bevorzugt die Microsoft-Anmeldung per Gerätecode. Im Panel müssen Tenant, Client-ID und Zielordner gesetzt sein; danach erledigt `OneDrive verbinden` die Anmeldung.
- Optional kann weiterhin eine Microsoft-Graph-Upload-URL-Vorlage mit `{filename}` und ein Bearer Token als Fallback gesetzt werden.

Beispiel OneDrive-URL-Vorlage:

```text
https://graph.microsoft.com/v1.0/me/drive/root:/BanAppeals/{filename}:/content
```

### LiteBans MySQL + Random Punishment-ID Bridge

Das Panel kann LiteBans-Bans direkt aus der LiteBans-MySQL-Datenbank lesen. Die zufällige Buchstaben-/Zahlen-ID, die LiteBans Spielern in der Ban-Nachricht zeigt, liegt nicht verlässlich als eigene DB-Spalte vor. Deshalb fragt das Panel für diese Anzeige-ID die Velocity-Bridge, weil dort LiteBans geladen ist und die ID über LiteBans `RandomID`/API aus der internen Datenbank-ID aufgelöst werden kann.

Wichtige Panel-Config:

```json
{
  "liteBansDatabaseEnabled": true,
  "liteBansJdbcUrl": "jdbc:mysql://127.0.0.1:3306/litebans?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
  "liteBansDatabaseUsername": "litebans",
  "liteBansDatabasePassword": "DEIN_PASSWORT",
  "liteBansTablePrefix": "litebans_",
  "liteBansDatabaseMaxRows": 1000,
  "liteBansBridgeBaseUrl": "http://127.0.0.1:9095",
  "liteBansBridgeSecret": ""
}
```

Wenn `liteBansBridgeSecret` leer ist, nutzt das Panel automatisch den ersten `apiTokens`-Eintrag als Bridge-Secret. Trage denselben Token im Velocity-Plugin als `panel.api-token` ein oder setze explizit auf beiden Seiten dasselbe `litebans.bridge-secret`.

Wenn Panel und Velocity nicht auf demselben Rootserver laufen, muss `liteBansBridgeBaseUrl` auf die erreichbare Proxy-IP zeigen, z.B. `http://PROXY-IP:9095`. Öffne den Bridge-Port nur für die Panel-/CloudNet-IP.

## Velocity-Plugin

Das Velocity-Plugin wird auf dem Velocity-Proxy installiert. Es nutzt das Panel per API für Tickets und nutzt LiteBans direkt auf dem Proxy für Ban-Prüfung und Ban-Befehle.

Installation:

1. Kopiere `velocity-plugin/target/TicketConsoleCloudBan-Velocity.jar` in den `plugins/`-Ordner deines Velocity-Proxys.
2. Starte Velocity einmal, damit `plugins/ticketconsolecloudban-velocity/config.properties` erstellt wird.
3. Trage dort `panel.url` und `panel.api-token` ein. Der API-Token steht in der CloudNet-Modul-Konfiguration oder im CloudNet-Log.
4. Starte Velocity neu oder nutze `/tccbvelocity reload`.

Wichtige Config-Werte:

```properties
panel.url=http://127.0.0.1:8088
panel.api-token=CHANGE_ME
panel.action-interval-seconds=10
litebans.enabled=true
litebans.join-check=true
litebans.sync-enabled=true
litebans.sync-interval-seconds=60
litebans.server-scope=*
litebans.public-id-column=id
litebans.bridge-enabled=true
litebans.bridge-bind-host=127.0.0.1
litebans.bridge-port=9095
litebans.bridge-secret=
litebans.ban-command=ban {player} {duration} {reason}
litebans.unban-command=unban {player} {reason}
litebans.extend-command=ban {player} {duration} {reason}
teleport.enabled=true
teleport.command=tp {staff} {target}
luckperms.sync-enabled=true
luckperms.server-id=proxy
luckperms.sync-interval-seconds=60
```

Hinweis zu LiteBans-IDs: LiteBans speichert intern eine numerische `id`, zeigt Spielern aber je nach LiteBans-Version eine zufällige Punishment-ID an. Das Velocity-Plugin löst diese Random-ID beim Sync automatisch über LiteBans `RandomID`/API aus der Datenbank-ID auf und sendet sie als `publicId` ans Panel. Zusätzlich stellt es die geschützte Bridge-Route `GET /api/punishment-id/from-db` bereit, damit das Panel bei direktem MySQL-Lesen dieselbe Random-ID anzeigen kann. `litebans.public-id-column` ist nur noch ein Fallback, falls der API-Resolver auf einer LiteBans-Version nicht verfügbar ist. Die Befehls-Templates können `{id}` für die Random-ID, `{banId}` für die interne Datenbank-ID sowie `{player}`, `{uuid}`, `{ip}`, `{duration}`, `{reason}` und `{actor}` verwenden.

Ingame-Befehle:

- `/ticket create <grund> [Support|Bug|Melden|Sonstiges]` erstellt ein Ticket mit Spielername, UUID und aktuellem Unterserver.
- `/ticket <nachricht>` bleibt als Kurzform für ein Support-Ticket erhalten.
- `/ticket list` oder `/tickets` zeigt eigene Tickets.
- `/ticket view <id>` zeigt Status, Beschreibung und Antworten des eigenen Tickets.
- `/teamtickets` zeigt offene und in Bearbeitung befindliche Tickets für Teamler.
- `/teamticket list` zeigt offene und in Bearbeitung befindliche Tickets.
- `/teamticket view <id>` zeigt ein Ticket inklusive interner Kommentare.
- `/teamticket open <id>`, `/teamticket progress <id>` und `/teamticket close <id>` ändern den Ticketstatus.
- `/teamticket assign <id> <teamler>` weist ein Ticket zu.
- `/teamticket comment <id> <nachricht>` schreibt eine Antwort ins Ticket.
- `/ticketclose <id>` schliesst ein Ticket als alte Kurzform.
- `/ticketcomment <id> <nachricht>` kommentiert ein Ticket als alte Kurzform.
- `/cloudban <spieler> <dauer> <grund>` führt den konfigurierten LiteBans-Befehl aus.
- `/cloudunban <spieler> [grund]` führt den konfigurierten LiteBans-Unban aus.
- `/baninfo <spieler>` prüft aktive LiteBans-Bans für online Spieler.
- `/tccbvelocity reload` lädt die Velocity-Config neu.

Teleport aus dem Panel:

- Der Button im Ticket-Panel erstellt eine Spieler-Aktion in der Panel-Datenbank.
- Velocity holt diese Aktion alle `panel.action-interval-seconds` Sekunden ab.
- Velocity verbindet den Teamler auf den Server des Spielers und führt anschliessend `teleport.command` aus.
- Standard ist `tp {staff} {target}`. Platzhalter: `{staff}`, `{target}`, `{server}`, `{ticketId}`.
- Das Kommando muss auf deinem Proxy-Setup ausfuehrbar sein. Wenn dein `/tp` nur serverseitig existiert, brauchst du ein Proxy-kompatibles Teleport-Command oder einen Server-Command-Forwarder.

LuckPerms-Rechte:

- `tccb.ticket.create`
- `tccb.ticket.own`
- `tccb.ticket.team`
- `tccb.ticket.manage`
- `tccb.ban.manage`
- `tccb.reload`

Wenn LuckPerms auf Velocity installiert ist, werden diese Rechte über LuckPerms ausgewertet. Ohne LuckPerms nutzt das Plugin Velocitys normales `hasPermission`.

LuckPerms-Panel-Bridge für Velocity:

- Das Velocity-Plugin synchronisiert geladene LuckPerms-Gruppen und geladene User als Server `proxy` ins Panel.
- Im Panel unter `LuckPerms` kannst du Permissions hinzufügen/entfernen und Parent-Gruppen zuweisen/entfernen.
- Die Änderungen werden als Queue-Aktion gespeichert und vom Velocity-Plugin verarbeitet, wenn `serverId=proxy` ist.
- Das Panel schreibt ein Auditlog für angeforderte und abgeschlossene Permission-Aktionen.
- Für Unterserver mit eigenen LuckPerms-Datenbanken brauchst du das Purpur/Paper-Plugin auf jedem Unterserver.

## Purpur/Paper-Plugin

Das Purpur/Paper-Plugin wird auf jedem Minecraft-Unterserver installiert, der eine eigene LuckPerms-Datenbank nutzt. Es verbindet sich mit dem Panel, synchronisiert die lokale LuckPerms-Instanz und verarbeitet nur Aktionen für seine eigene `server.id`.

Installation:

1. Kopiere `purpur-plugin/target/TicketConsoleCloudBan-Purpur.jar` in den `plugins/`-Ordner jedes Purpur-Servers.
2. Starte den Server einmal, damit `plugins/TicketConsoleCloudBan-Purpur/config.yml` erstellt wird.
3. Trage `panel.url`, `panel.api-token` und eine eindeutige `server.id` ein, zum Beispiel `Lobby-1`, `Survival-1` oder `CityBuild`.
4. Starte den Server neu oder nutze `/tccbpurpur reload`.

Wichtige Config-Werte:

```yaml
panel:
  url: "http://127.0.0.1:8088"
  api-token: "CHANGE_ME"
server:
  id: "Lobby-1"
sync:
  enabled: true
  interval-seconds: 60
permissions:
  reload: "tccb.purpur.reload"
```

Wichtig: Bei getrennten LuckPerms-Datenbanken muss jeder Unterserver, dessen Rechte du im Panel bearbeiten willst, dieses Purpur/Paper-Plugin installiert haben. Velocity kann diese getrennten Datenbanken nicht direkt bearbeiten.

## Rechteverwaltung

Im Panel gibt es die Unterseite `Rechte`. Dort kannst du Gruppen erstellen, Rechte zuweisen und Panel-Benutzer diesen Gruppen zuordnen.

Verfügbare Rechte:

- `cloudnet.view`
- `cloudnet.manage`
- `cloudnet.console`
- `cloudnet.command`
- `tickets.view`
- `tickets.create`
- `tickets.manage`
- `bans.view`
- `bans.manage`
- `users.manage`
- `permissions.proxy.manage`
- `permissions.server.manage`
- `*` für Vollzugriff

## Einsatz im Cluster

Für dein Setup mit mehreren Rootservern gilt:

- Installiere das Modul auf einer CloudNet-Node, die sauber mit dem Cluster verbunden ist.
- Wenn du ein einzelnes zentrales Panel willst, sollte genau diese Node der Einstiegspunkt für das Webpanel sein.
- Packe idealerweise einen Reverse Proxy davor, z.B. Nginx.
- Schuetze den Port per Firewall oder Reverse-Proxy-Auth, auch wenn bereits API-Tokens verwendet werden.

## Aktuelle API-Funktionen

- `GET /api/meta`
- `POST /api/auth/login`
- `GET /api/auth/session`
- `POST /api/auth/logout`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/complete`
- `GET /api/overview`
- `GET /api/tasks`
- `POST /api/tasks`
- `PUT /api/tasks/{name}`
- `DELETE /api/tasks/{name}`
- `GET /api/services`
- `POST /api/services`
- `POST /api/services/{name}/start`
- `POST /api/services/{name}/stop`
- `POST /api/services/{name}/restart`
- `DELETE /api/services/{name}`
- `GET /api/services/{name}/console?limit=250`
- `POST /api/services/{name}/command`
- `GET /api/nodes`
- `GET /api/tickets`
- `GET /api/tickets?creatorUniqueId=<uuid>`
- `GET /api/tickets?creatorName=<name>`
- `GET /api/tickets?status=OPEN`
- `GET /api/tickets/{id}`
- `GET /api/tickets/audit`
- `POST /api/tickets` mit optional `sourceServer`/`serviceName`
- `POST /api/tickets/{id}/status`
- `POST /api/tickets/{id}/assign`
- `POST /api/tickets/{id}/comments`
- `GET /api/player-actions`
- `POST /api/player-actions/teleport`
- `POST /api/player-actions/{id}/complete`
- `GET /api/bans`
- `POST /api/bans`
- `POST /api/bans/{id}/deactivate`
- `GET /api/bans/litebans`
- `POST /api/bans/litebans-sync`
- `POST /api/bans/litebans/{id}/unban`
- `POST /api/bans/litebans/{id}/extend`
- `GET /api/bans/actions`
- `POST /api/bans/actions/{id}/complete`
- `GET /api/bans/audit`
- `GET /api/ban-appeals`
- `POST /api/ban-appeals/{id}/status`
- `POST http://APPEAL-HOST:APPEAL-PORT/api/appeals`
- `GET http://APPEAL-HOST:APPEAL-PORT/api/appeals/meta`
- `GET http://APPEAL-HOST:APPEAL-PORT/api/appeals/status?token=<token>`
- `GET /api/permissions/subjects`
- `POST /api/permissions/sync`
- `GET /api/permissions/actions?serverId=<server>`
- `POST /api/permissions/actions`
- `POST /api/permissions/actions/{id}/complete`
- `GET /api/permissions/audit`
- `PUT /api/auth/profile`
- `POST /api/auth/password`
- `POST /api/auth/2fa/setup`
- `POST /api/auth/2fa/verify`
- `GET /api/quest-editor/config`
- `GET /api/quest-editor/servers`
- `GET /api/quest-editor/{serverId}/status`
- `GET /api/quest-editor/{serverId}/schema`
- `GET /api/quest-editor/{serverId}/categories`
- `GET /api/quest-editor/{serverId}/quests`
- `GET /api/quest-editor/{serverId}/quests/{id}`
- `GET /api/quest-editor/{serverId}/raw/quests/{id}`
- `GET /api/security/users`
- `POST /api/security/users`
- `PUT /api/security/users/{username}`
- `DELETE /api/security/users/{username}`
- `GET /api/security/groups`
- `POST /api/security/groups`
- `PUT /api/security/groups/{name}`
- `DELETE /api/security/groups/{name}`

## Sinnvolle nächste Schritte

Wenn du daraus noch näher an ein komplettes "NetworkManager"-System willst, wuerde ich als nächstes diese drei Erweiterungen bauen:

1. LuckPerms-Expiry, Meta-Nodes und Context-Kombinationen im Editor
2. Rootserver-Agent oder SSH-Anbindung für echte Rootserver-Konsole
3. Serverlokale Purpur-Befehle und Statusdaten über das Purpur-Companion-Plugin
