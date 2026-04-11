package de.speed.ticketconsolecloudban.velocity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record VelocityPluginConfig(
  String panelUrl,
  String panelApiToken,
  String ticketDefaultCategory,
  String ticketDefaultPriority,
  int ticketListLimit,
  boolean liteBansEnabled,
  boolean liteBansJoinCheck,
  boolean liteBansSyncEnabled,
  int liteBansSyncIntervalSeconds,
  String liteBansServerScope,
  String liteBansPublicIdColumn,
  String liteBansBanCommand,
  String liteBansUnbanCommand,
  String liteBansExtendCommand,
  String permissionTicketCreate,
  String permissionTicketListOwn,
  String permissionTicketTeam,
  String permissionTicketManage,
  String permissionBanManage,
  String permissionReload,
  String luckPermsServerId,
  boolean luckPermsSyncEnabled,
  int luckPermsSyncIntervalSeconds
) {

  public static VelocityPluginConfig load(Path dataDirectory) {
    var path = dataDirectory.resolve("config.properties");
    var properties = new Properties();

    try {
      Files.createDirectories(dataDirectory);
      if (Files.notExists(path)) {
        try (InputStream defaults = VelocityPluginConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
          if (defaults != null) {
            properties.load(defaults);
          }
        }
        try (OutputStream output = Files.newOutputStream(path)) {
          properties.store(output, "TicketConsoleCloudBan Velocity Plugin");
        }
      } else {
        try (InputStream input = Files.newInputStream(path)) {
          properties.load(input);
        }
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Velocity config konnte nicht geladen werden.", exception);
    }

    return new VelocityPluginConfig(
      text(properties, "panel.url", "http://127.0.0.1:8088"),
      text(properties, "panel.api-token", "CHANGE_ME"),
      text(properties, "ticket.default-category", "SUPPORT"),
      text(properties, "ticket.default-priority", "NORMAL"),
      integer(properties, "ticket.list-limit", 8, 1, 25),
      bool(properties, "litebans.enabled", true),
      bool(properties, "litebans.join-check", true),
      bool(properties, "litebans.sync-enabled", true),
      integer(properties, "litebans.sync-interval-seconds", 60, 15, 3600),
      text(properties, "litebans.server-scope", "*"),
      text(properties, "litebans.public-id-column", "id"),
      text(properties, "litebans.ban-command", "ban {player} {duration} {reason}"),
      text(properties, "litebans.unban-command", "unban {player} {reason}"),
      text(properties, "litebans.extend-command", "ban {player} {duration} {reason}"),
      text(properties, "permissions.ticket-create", "tccb.ticket.create"),
      text(properties, "permissions.ticket-list-own", "tccb.ticket.own"),
      text(properties, "permissions.ticket-team", "tccb.ticket.team"),
      text(properties, "permissions.ticket-manage", "tccb.ticket.manage"),
      text(properties, "permissions.ban-manage", "tccb.ban.manage"),
      text(properties, "permissions.reload", "tccb.reload"),
      text(properties, "luckperms.server-id", "proxy"),
      bool(properties, "luckperms.sync-enabled", true),
      integer(properties, "luckperms.sync-interval-seconds", 60, 15, 3600));
  }

  public boolean hasPanelToken() {
    return this.panelApiToken != null
      && !this.panelApiToken.isBlank()
      && !"CHANGE_ME".equalsIgnoreCase(this.panelApiToken.trim());
  }

  private static String text(Properties properties, String key, String fallback) {
    var value = properties.getProperty(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static boolean bool(Properties properties, String key, boolean fallback) {
    var value = properties.getProperty(key);
    return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
  }

  private static int integer(Properties properties, String key, int fallback, int min, int max) {
    try {
      var value = Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
      return Math.max(min, Math.min(max, value));
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }
}
