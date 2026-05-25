# TicketConsoleCloudBan

Ausgeduennte CloudNet-v4-Modulversion mit eingebautem Webpanel fuer zentrale CloudNet-Verwaltung.

## Funktionen

- Tasks erstellen, bearbeiten und loeschen
- Services aus Tasks erstellen
- Services starten, stoppen, neustarten und loeschen
- Service-Konsolen ansehen und Befehle an Services senden
- CloudNet-Live-Konsole per REST oder Screen-Fallback
- Cluster-Nodes im Panel anzeigen
- Panel-Login mit Benutzern, Gruppen und Panel-Rechten
- Persistente Panel-Sessions
- Optionale 2-Faktor-Authentifikation per E-Mail-Code oder Authenticator-App
- Passwort-vergessen-Flow mit Reset-Token und optionalem SMTP-Mailversand
- Panel-Auftritt, CloudNet-Konsole und SMTP im Einstellungstab pflegen
- Optionaler SQL-Speicher fuer Panel-Daten mit lokalem JSON-Fallback

## Build

```bash
mvn -DskipTests package
```

Artefakt:

```text
target/TicketConsoleCloudBan.jar
```

## Installation in CloudNet

1. Modul bauen.
2. `target/TicketConsoleCloudBan.jar` in den `modules/`-Ordner der CloudNet-Node kopieren.
3. Node neu starten oder Modul neu laden.
4. Beim ersten Start erstellt das Modul seine Konfiguration und einen Admin-Login.
5. Admin-Benutzer und einmaliges Passwort stehen im CloudNet-Log.
6. Panel im Browser oeffnen:

```text
http://DEINE-NODE-IP:8088
```

## Konfiguration

Die Modul-Konfiguration wird beim ersten Start erzeugt. Beispiel:

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
  "apiTokens": [
    "dein-generiertes-token"
  ]
}
```

Der alte API-Token-Zugang bleibt fuer externe Tools erhalten und hat Vollzugriff. Normale Panel-Logins verwenden gehashte Session-Tokens im Panel-Speicher.

## Panel-Speicher

Bei `panelStorageBackend=SQL` werden Panel-Benutzer, Gruppen, Sessions, Reset-Tokens und Einstellungen in `panelSqlTable` gespeichert. Wenn SQL nicht erreichbar ist, faellt das Modul auf lokalen JSON-Speicher zurueck und schreibt eine Warnung ins CloudNet-Log.

Empfohlene MySQL-Vorbereitung:

```sql
CREATE DATABASE tccb_panel CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tccb_panel'@'%' IDENTIFIED BY 'DEIN_PASSWORT';
GRANT ALL PRIVILEGES ON tccb_panel.* TO 'tccb_panel'@'%';
FLUSH PRIVILEGES;
```

## Rechte

Im Panel gibt es die Unterseite `Rechte`. Dort kannst du Gruppen erstellen, Panel-Rechte vergeben und Benutzer diesen Gruppen zuordnen.

Verfuegbare Rechte:

- `cloudnet.view`
- `cloudnet.manage`
- `cloudnet.console`
- `cloudnet.command`
- `users.manage`
- `settings.manage`
- `*` fuer Vollzugriff

## API

- `GET /api/meta`
- `POST /api/auth/login`
- `GET /api/auth/session`
- `POST /api/auth/logout`
- `POST /api/auth/password-reset/request`
- `POST /api/auth/password-reset/complete`
- `PUT /api/auth/profile`
- `POST /api/auth/password`
- `POST /api/auth/2fa/setup`
- `POST /api/auth/2fa/verify`
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
- `GET /api/cloudnet/console?limit=250`
- `POST /api/cloudnet/command`
- `GET /api/nodes`
- `GET /api/security/permissions`
- `GET /api/security/users`
- `POST /api/security/users`
- `PUT /api/security/users/{username}`
- `DELETE /api/security/users/{username}`
- `GET /api/security/groups`
- `POST /api/security/groups`
- `PUT /api/security/groups/{name}`
- `DELETE /api/security/groups/{name}`
- `GET /api/settings`
- `PUT /api/settings`
- `POST /api/settings/test-mail`
