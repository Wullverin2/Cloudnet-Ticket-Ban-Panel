package de.speed.ticketconsolecloudban.purpur;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public record PurpurPluginConfig(
  String panelUrl,
  String panelApiToken,
  String serverId,
  boolean syncEnabled,
  int syncIntervalSeconds,
  String reloadPermission
) {

  public static PurpurPluginConfig load(JavaPlugin plugin) {
    var config = plugin.getConfig();
    return new PurpurPluginConfig(
      text(config.getString("panel.url"), "http://127.0.0.1:8088"),
      text(config.getString("panel.api-token"), "CHANGE_ME"),
      serverId(text(config.getString("server.id"), "CHANGE_ME")),
      config.getBoolean("sync.enabled", true),
      clamp(config.getInt("sync.interval-seconds", 60), 15, 3600),
      text(config.getString("permissions.reload"), "tccb.purpur.reload"));
  }

  public boolean hasPanelToken() {
    return this.panelApiToken != null
      && !this.panelApiToken.isBlank()
      && !"CHANGE_ME".equalsIgnoreCase(this.panelApiToken.trim());
  }

  private static String serverId(String configured) {
    if (configured != null && !configured.isBlank() && !"CHANGE_ME".equalsIgnoreCase(configured)) {
      return configured.trim();
    }

    var cloudNetName = System.getenv("CLOUDNET_SERVICE_NAME");
    if (cloudNetName != null && !cloudNetName.isBlank()) {
      return cloudNetName.trim();
    }

    var serverName = Bukkit.getServer().getName();
    return serverName == null || serverName.isBlank() ? "purpur" : serverName.trim();
  }

  private static String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
