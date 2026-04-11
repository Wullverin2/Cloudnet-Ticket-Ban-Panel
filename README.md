# TicketConsoleCloudBan

Ein CloudNet-v4-Modul mit eingebautem Webpanel fuer:

- Task erstellen, bearbeiten und loeschen
- Services aus Tasks erstellen
- Services starten, stoppen, neustarten und löschen
- Konsolen-Logs pro Service ansehen und Befehle senden
- Cluster-Nodes im Panel anzeigen
- Tickets erstellen, kommentieren, zuweisen und abschliessen
- Zentrale Cloud-Bans anlegen und deaktivieren
- Panel-Login mit Benutzern, Gruppen und Rechteverwaltung
- Benutzerprofil mit E-Mail fuer Passwort-vergessen-Prozesse und optionalem Minecraft-Account
- Auditlogs fuer Tickets und Bans
- LiteBans-Unterseite mit synchronisierten LiteBans-Bans
- Passwort-vergessen-Flow mit Reset-Token und optionalem SMTP-Mailversand
- LuckPerms-Unterseite mit Subject-Sync, Aktionsqueue und Auditlog

Die UI ist direkt im Modul enthalten und wird ueber einen kleinen HTTP-Server ausgeliefert.

Zusaetzlich enthaelt das Repository ein Velocity-Companion-Plugin fuer Ingame-Tickets, LiteBans-Pruefung, LiteBans-Sync und Proxy-LuckPerms sowie ein Purpur/Paper-Companion-Plugin fuer lokale Unterserver-LuckPerms-Datenbanken.

## Architektur

Das Modul ist für ein CloudNet-Cluster gedacht, in dem Velocity als Proxy und Purpur als Spielserver laufen. Du installierst das Modul auf einer CloudNet-Node, die den Cluster voll sehen kann. Ueber die CloudNet-v4-APIs werden dann clusterweit Tasks und Services verwaltet.

Das Panel ist bewusst als MVP gebaut:

- CloudNet Task-CRUD ist vorhanden
- Service-Management ist vorhanden
- Service-Konsole ist vorhanden
- Node-Übersicht ist vorhanden
- Ticket-System ist vorhanden und speichert den Unterserver/Service, auf dem ein Ticket erstellt wurde
- Cloud-Ban-Verwaltung ist vorhanden
- Panel-Login mit Gruppenrechten ist vorhanden
- LiteBans-Bans koennen ueber das Velocity-Plugin ins Panel synchronisiert werden
- LiteBans-Unban und -Verlaengerung laufen ueber eine Panel-Aktionsqueue, die Velocity abarbeitet
- LuckPerms-Gruppen und geladene Spieler koennen pro Proxy und pro Purpur-Unterserver ins Panel synchronisiert werden
- LuckPerms-Permissions und Parent-Gruppen koennen im Panel als Queue-Aktion erstellt werden
- Unterserver mit eigener LuckPerms-Datenbank werden ueber das Purpur-Companion-Plugin gezielt per `server.id` angesteuert
- Passwort-Reset laeuft mit gehashten Einmal-Tokens und optionaler SMTP-Mail
- Live-Konsole laeuft aktuell per Polling auf dem Log-Cache

Noch nicht enthalten:

- Rootserver-SSH oder echte Root-Console
- Eigene Purpur-Ban-Durchsetzung ohne LiteBans
- Vollstaendiger LuckPerms-Webeditor mit allen Metadaten/Expiry/Context-Kombinationen

Die Struktur ist aber so angelegt, dass diese Bausteine spaeter sauber angebunden werden koennen.

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
3. Starte die Node neu oder lade das Modul ueber CloudNet neu.
4. Nach dem ersten Start erstellt das Modul seine `config.json` und `panel-users.json` im Modul-Datenordner.
5. Das generierte API-Token wird beim Start ins Log geschrieben und ist fuer Integrationen gedacht.
6. Beim ersten Start wird ein Panel-Admin erstellt. Benutzer `admin` und Passwort stehen einmalig im CloudNet-Log.
7. Oeffne dann im Browser:

```text
http://DEINE-NODE-IP:8088
```

## Konfiguration

Die Modul-Konfiguration wird automatisch erstellt und sieht sinngemaess so aus:

```json
{
  "bindHost": "0.0.0.0",
  "bindPort": 8088,
  "consoleLineLimit": 250,
  "brandName": "Network Control",
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
  "apiTokens": [
    "dein-generiertes-token"
  ]
}
```

Das Panel nutzt einen eigenen Login. Der alte API-Token-Zugang bleibt fuer externe Tools oder ein spaeteres Velocity-/Purpur-Companion-Plugin erhalten und hat Vollzugriff.

Wenn `smtpEnabled=false` ist, werden Passwort-Reset-Links nicht per Mail versendet, sondern sicherheitshalber im CloudNet-Log ausgegeben. Fuer produktiven Betrieb solltest du `publicBaseUrl` auf deine Panel-Domain setzen und SMTP aktivieren.

## Velocity-Plugin

Das Velocity-Plugin wird auf dem Velocity-Proxy installiert. Es nutzt das Panel per API fuer Tickets und nutzt LiteBans direkt auf dem Proxy fuer Ban-Pruefung und Ban-Befehle.

Installation:

1. Kopiere `velocity-plugin/target/TicketConsoleCloudBan-Velocity.jar` in den `plugins/`-Ordner deines Velocity-Proxys.
2. Starte Velocity einmal, damit `plugins/ticketconsolecloudban-velocity/config.properties` erstellt wird.
3. Trage dort `panel.url` und `panel.api-token` ein. Der API-Token steht in der CloudNet-Modul-Konfiguration oder im CloudNet-Log.
4. Starte Velocity neu oder nutze `/tccbvelocity reload`.

Wichtige Config-Werte:

```properties
panel.url=http://127.0.0.1:8088
panel.api-token=CHANGE_ME
litebans.enabled=true
litebans.join-check=true
litebans.sync-enabled=true
litebans.sync-interval-seconds=60
litebans.server-scope=*
litebans.public-id-column=id
litebans.ban-command=ban {player} {duration} {reason}
litebans.unban-command=unban {player} {reason}
litebans.extend-command=ban {player} {duration} {reason}
luckperms.sync-enabled=true
luckperms.server-id=proxy
luckperms.sync-interval-seconds=60
```

Hinweis zu LiteBans-IDs: LiteBans speichert intern eine numerische `id`. Wenn dein Server in Nachrichten eine zufaellige Buchstaben-/Zahlen-ID nutzt, kannst du spaeter `litebans.public-id-column` auf eine passende Datenbankspalte setzen. Falls diese Spalte nicht existiert, nutzt das Panel automatisch die interne `id`. Die Befehls-Templates koennen `{id}`, `{banId}`, `{player}`, `{uuid}`, `{ip}`, `{duration}`, `{reason}` und `{actor}` verwenden.

Ingame-Befehle:

- `/ticket <nachricht>` erstellt ein Ticket mit Spielername, UUID und aktuellem Unterserver.
- `/tickets` zeigt eigene Tickets.
- `/teamtickets` zeigt offene Tickets fuer Teamler.
- `/ticketclose <id>` schliesst ein Ticket.
- `/ticketcomment <id> <nachricht>` kommentiert ein Ticket.
- `/cloudban <spieler> <dauer> <grund>` fuehrt den konfigurierten LiteBans-Befehl aus.
- `/cloudunban <spieler> [grund]` fuehrt den konfigurierten LiteBans-Unban aus.
- `/baninfo <spieler>` prueft aktive LiteBans-Bans fuer online Spieler.
- `/tccbvelocity reload` laedt die Velocity-Config neu.

LuckPerms-Rechte:

- `tccb.ticket.create`
- `tccb.ticket.own`
- `tccb.ticket.team`
- `tccb.ticket.manage`
- `tccb.ban.manage`
- `tccb.reload`

Wenn LuckPerms auf Velocity installiert ist, werden diese Rechte ueber LuckPerms ausgewertet. Ohne LuckPerms nutzt das Plugin Velocitys normales `hasPermission`.

LuckPerms-Panel-Bridge fuer Velocity:

- Das Velocity-Plugin synchronisiert geladene LuckPerms-Gruppen und geladene User als Server `proxy` ins Panel.
- Im Panel unter `LuckPerms` kannst du Permissions hinzufuegen/entfernen und Parent-Gruppen zuweisen/entfernen.
- Die Aenderungen werden als Queue-Aktion gespeichert und vom Velocity-Plugin verarbeitet, wenn `serverId=proxy` ist.
- Das Panel schreibt ein Auditlog fuer angeforderte und abgeschlossene Permission-Aktionen.
- Fuer Unterserver mit eigenen LuckPerms-Datenbanken brauchst du das Purpur/Paper-Plugin auf jedem Unterserver.

## Purpur/Paper-Plugin

Das Purpur/Paper-Plugin wird auf jedem Minecraft-Unterserver installiert, der eine eigene LuckPerms-Datenbank nutzt. Es verbindet sich mit dem Panel, synchronisiert die lokale LuckPerms-Instanz und verarbeitet nur Aktionen fuer seine eigene `server.id`.

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

Verfuegbare Rechte:

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
- `*` fuer Vollzugriff

## Einsatz im Cluster

Fuer dein Setup mit mehreren Rootservern gilt:

- Installiere das Modul auf einer CloudNet-Node, die sauber mit dem Cluster verbunden ist.
- Wenn du ein einzelnes zentrales Panel willst, sollte genau diese Node der Einstiegspunkt fuer das Webpanel sein.
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
- `GET /api/tickets/audit`
- `POST /api/tickets` mit optional `sourceServer`/`serviceName`
- `POST /api/tickets/{id}/status`
- `POST /api/tickets/{id}/assign`
- `POST /api/tickets/{id}/comments`
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
- `GET /api/permissions/subjects`
- `POST /api/permissions/sync`
- `GET /api/permissions/actions?serverId=<server>`
- `POST /api/permissions/actions`
- `POST /api/permissions/actions/{id}/complete`
- `GET /api/permissions/audit`
- `PUT /api/auth/profile`
- `POST /api/auth/password`
- `GET /api/security/users`
- `POST /api/security/users`
- `PUT /api/security/users/{username}`
- `DELETE /api/security/users/{username}`
- `GET /api/security/groups`
- `POST /api/security/groups`
- `PUT /api/security/groups/{name}`
- `DELETE /api/security/groups/{name}`

## Sinnvolle naechste Schritte

Wenn du daraus noch naeher an ein komplettes "NetworkManager"-System willst, wuerde ich als naechstes diese drei Erweiterungen bauen:

1. LuckPerms-Expiry, Meta-Nodes und Context-Kombinationen im Editor
2. Rootserver-Agent oder SSH-Anbindung fuer echte Rootserver-Konsole
3. Serverlokale Purpur-Befehle und Statusdaten ueber das Purpur-Companion-Plugin
