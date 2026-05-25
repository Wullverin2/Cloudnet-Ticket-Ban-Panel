package de.speed.ticketconsolecloudban.auth;

import java.util.List;

public final class PanelPermission {

  public static final String ALL = "*";
  public static final String CLOUDNET_VIEW = "cloudnet.view";
  public static final String CLOUDNET_MANAGE = "cloudnet.manage";
  public static final String CLOUDNET_CONSOLE = "cloudnet.console";
  public static final String CLOUDNET_COMMAND = "cloudnet.command";
  public static final String USERS_MANAGE = "users.manage";
  public static final String SETTINGS_MANAGE = "settings.manage";

  private static final List<PermissionView> CATALOG = List.of(
    new PermissionView(ALL, "System", "Alle Rechte", "Vollzugriff auf alle Panel-Funktionen."),
    new PermissionView(CLOUDNET_VIEW, "CloudNet", "CloudNet ansehen", "Übersicht, Tasks, Services, Nodes und Logs anzeigen."),
    new PermissionView(CLOUDNET_MANAGE, "CloudNet", "CloudNet verwalten", "Tasks und Services erstellen, bearbeiten, starten, stoppen und loeschen."),
    new PermissionView(CLOUDNET_CONSOLE, "CloudNet", "Konsolen lesen", "Service-Konsolen und Log-Ausgaben anzeigen."),
    new PermissionView(CLOUDNET_COMMAND, "CloudNet", "Konsolenbefehle senden", "Befehle an einzelne Service-Konsolen senden."),
    new PermissionView(USERS_MANAGE, "Rechte", "Benutzer und Gruppen", "Panel-Benutzer, Gruppen und Rechte verwalten."),
    new PermissionView(SETTINGS_MANAGE, "System", "Panel-Einstellungen", "Mailserver, CloudNet-Konsole und Panel-Speicher ansehen und bearbeiten."));

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
