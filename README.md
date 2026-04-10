# TicketConsoleCloudBan

Ein CloudNet-v4-Modul mit eingebautem Webpanel fuer:

- Task erstellen, bearbeiten und loeschen
- Services aus Tasks erstellen
- Services starten, stoppen, neustarten und loeschen
- Konsolen-Logs pro Service ansehen und Befehle senden
- Cluster-Nodes im Panel anzeigen
- Tickets erstellen, kommentieren, zuweisen und abschliessen
- Zentrale Cloud-Bans anlegen und deaktivieren
- Panel-Login mit Benutzern, Gruppen und Rechteverwaltung

Die UI ist direkt im Modul enthalten und wird ueber einen kleinen HTTP-Server ausgeliefert.

## Architektur

Das Modul ist fuer ein CloudNet-Cluster gedacht, in dem Velocity als Proxy und Purpur als Spielserver laufen. Du installierst das Modul auf einer CloudNet-Node, die den Cluster voll sehen kann. Ueber die CloudNet-v4-APIs werden dann clusterweit Tasks und Services verwaltet.

Das Panel ist bewusst als MVP gebaut:

- CloudNet Task-CRUD ist vorhanden
- Service-Management ist vorhanden
- Service-Konsole ist vorhanden
- Node-Uebersicht ist vorhanden
- Ticket-System ist vorhanden und speichert den Unterserver/Service, auf dem ein Ticket erstellt wurde
- Cloud-Ban-Verwaltung ist vorhanden
- Panel-Login mit Gruppenrechten ist vorhanden
- Live-Konsole laeuft aktuell per Polling auf dem Log-Cache

Noch nicht enthalten:

- LuckPerms/LiteBans-Anbindung
- Rootserver-SSH oder echte Root-Console
- Aktive Ban-Durchsetzung direkt auf Velocity/Purpur

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
  "apiTokens": [
    "dein-generiertes-token"
  ]
}
```

Das Panel nutzt einen eigenen Login. Der alte API-Token-Zugang bleibt fuer externe Tools oder ein spaeteres Velocity-/Purpur-Companion-Plugin erhalten und hat Vollzugriff.

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
- `POST /api/tickets` mit optional `sourceServer`/`serviceName`
- `POST /api/tickets/{id}/status`
- `POST /api/tickets/{id}/assign`
- `POST /api/tickets/{id}/comments`
- `GET /api/bans`
- `POST /api/bans`
- `POST /api/bans/{id}/deactivate`
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

1. Velocity-Plugin fuer aktive Ban-Pruefung beim Join
2. Velocity-/Purpur-Plugin fuer Ingame-Tickets mit automatischem Unterserver
3. LuckPerms- und LiteBans-Anbindung fuer echtes Netzwerk-Management
