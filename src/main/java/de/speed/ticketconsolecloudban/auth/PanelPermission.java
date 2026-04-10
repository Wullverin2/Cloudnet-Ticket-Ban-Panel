package de.speed.ticketconsolecloudban.auth;

import java.util.List;

public final class PanelPermission {

  public static final String ALL = "*";
  public static final String CLOUDNET_VIEW = "cloudnet.view";
  public static final String CLOUDNET_MANAGE = "cloudnet.manage";
  public static final String CLOUDNET_CONSOLE = "cloudnet.console";
  public static final String CLOUDNET_COMMAND = "cloudnet.command";
  public static final String TICKETS_VIEW = "tickets.view";
  public static final String TICKETS_CREATE = "tickets.create";
  public static final String TICKETS_MANAGE = "tickets.manage";
  public static final String BANS_VIEW = "bans.view";
  public static final String BANS_MANAGE = "bans.manage";
  public static final String USERS_MANAGE = "users.manage";
  public static final String PROXY_PERMISSIONS_MANAGE = "permissions.proxy.manage";
  public static final String SERVER_PERMISSIONS_MANAGE = "permissions.server.manage";

  private static final List<PermissionView> CATALOG = List.of(
    new PermissionView(ALL, "System", "Alle Rechte", "Vollzugriff auf alle Panel-Funktionen."),
    new PermissionView(CLOUDNET_VIEW, "CloudNet", "CloudNet ansehen", "Uebersicht, Tasks, Services, Nodes und Logs anzeigen."),
    new PermissionView(CLOUDNET_MANAGE, "CloudNet", "CloudNet verwalten", "Tasks und Services erstellen, bearbeiten, starten, stoppen und loeschen."),
    new PermissionView(CLOUDNET_CONSOLE, "CloudNet", "Konsolen lesen", "Service-Konsolen und Log-Ausgaben anzeigen."),
    new PermissionView(CLOUDNET_COMMAND, "CloudNet", "Konsolenbefehle senden", "Befehle an einzelne Service-Konsolen senden."),
    new PermissionView(TICKETS_VIEW, "Tickets", "Tickets ansehen", "Tickets im Panel anzeigen."),
    new PermissionView(TICKETS_CREATE, "Tickets", "Tickets erstellen", "Tickets ueber das Panel oder externe Integrationen erstellen."),
    new PermissionView(TICKETS_MANAGE, "Tickets", "Tickets verwalten", "Tickets zuweisen, kommentieren und Status aendern."),
    new PermissionView(BANS_VIEW, "Bans", "Bans ansehen", "Cloud-Bans im Panel anzeigen."),
    new PermissionView(BANS_MANAGE, "Bans", "Bans verwalten", "Cloud-Bans erstellen und deaktivieren."),
    new PermissionView(USERS_MANAGE, "Rechte", "Benutzer und Gruppen", "Panel-Benutzer, Gruppen und Rechte verwalten."),
    new PermissionView(PROXY_PERMISSIONS_MANAGE, "Rechte", "Proxy-Rechte vorbereiten", "Reserviertes Recht fuer spaetere Proxy-/LuckPerms-Verwaltung."),
    new PermissionView(SERVER_PERMISSIONS_MANAGE, "Rechte", "Unterserver-Rechte vorbereiten", "Reserviertes Recht fuer spaetere Unterserver-/LuckPerms-Verwaltung."));

  private PanelPermission() {
  }

  public static List<PermissionView> catalog() {
    return CATALOG;
  }

  public record PermissionView(
    String id,
    String category,
    String label,
    String description
  ) {
  }
}
