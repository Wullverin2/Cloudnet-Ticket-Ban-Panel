package de.speed.ticketconsolecloudban.ban;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettings;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import de.speed.ticketconsolecloudban.store.BanStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LiteBansDatabaseSyncService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LiteBansDatabaseSyncService.class);

  private final PanelConfiguration configuration;
  private final PanelSettingsStore settingsStore;
  private final BanStore banStore;
  private final PunishmentIdBridgeClient bridgeClient;
  private volatile long lastSyncMillis;

  public LiteBansDatabaseSyncService(PanelConfiguration configuration, BanStore banStore) {
    this(configuration, banStore, null);
  }

  public LiteBansDatabaseSyncService(PanelConfiguration configuration, BanStore banStore, PanelSettingsStore settingsStore) {
    this.configuration = configuration;
    this.settingsStore = settingsStore;
    this.banStore = banStore;
    this.bridgeClient = settingsStore == null
      ? new PunishmentIdBridgeClient(configuration)
      : new PunishmentIdBridgeClient(configuration, settingsStore);
  }

  public boolean enabled() {
    return this.settings().liteBansDatabaseEnabled();
  }

  public void syncNow(String actor) {
    if (!this.enabled()) {
      return;
    }

    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      var entries = this.loadFromDatabase();
      this.banStore.syncLiteBans(entries, actor);
      this.lastSyncMillis = System.currentTimeMillis();
      LOGGER.info("LiteBans MySQL Sync abgeschlossen: {} Bans gelesen.", entries.size());
    } catch (Exception exception) {
      LOGGER.warn("LiteBans MySQL Sync fehlgeschlagen: {}", exception.getMessage());
    }
  }

  public void syncIfStale(String actor, Duration maxAge) {
    if (!this.enabled()) {
      return;
    }

    var ageMillis = System.currentTimeMillis() - this.lastSyncMillis;
    if (this.lastSyncMillis == 0 || ageMillis > maxAge.toMillis()) {
      this.syncNow(actor);
    }
  }

  private List<LiteBanEntry> loadFromDatabase() throws SQLException {
    var settings = this.settings();
    try (var connection = DriverManager.getConnection(
      settings.liteBansJdbcUrl(),
      settings.liteBansDatabaseUsername(),
      settings.liteBansDatabasePassword())) {
      var historyNames = this.loadLatestHistoryNames(connection);
      var bansTable = this.tableName("bans");
      var query = "SELECT * FROM " + bansTable + " ORDER BY time DESC LIMIT ?";

      try (var statement = connection.prepareStatement(query)) {
        statement.setInt(1, settings.liteBansDatabaseMaxRows());
        try (var resultSet = statement.executeQuery()) {
          var entries = new ArrayList<LiteBanEntry>();
          while (resultSet.next()) {
            entries.add(this.mapBan(resultSet, historyNames));
          }
          return List.copyOf(entries);
        }
      }
    }
  }

  private Map<String, String> loadLatestHistoryNames(Connection connection) {
    var historyTable = this.tableName("history");
    var query = "SELECT h1.uuid, h1.name FROM " + historyTable + " h1 "
      + "INNER JOIN (SELECT uuid, MAX(date) AS max_date FROM " + historyTable + " GROUP BY uuid) h2 "
      + "ON h1.uuid = h2.uuid AND h1.date = h2.max_date";
    var names = new HashMap<String, String>();

    try (PreparedStatement statement = connection.prepareStatement(query);
         ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        var uuid = normalizeUuid(safeString(resultSet, "uuid"));
        var name = safeString(resultSet, "name");
        if (uuid != null && name != null) {
          names.put(uuid, name);
        }
      }
    } catch (SQLException exception) {
      LOGGER.warn("LiteBans History-Namen konnten nicht geladen werden: {}", exception.getMessage());
    }

    return names;
  }

  private LiteBanEntry mapBan(ResultSet resultSet, Map<String, String> historyNames) throws SQLException {
    var databaseId = safeString(resultSet, "id");
    var playerUuid = normalizeUuid(safeString(resultSet, "uuid"));
    var serverScope = safeString(resultSet, "server_scope");
    var serverOrigin = safeString(resultSet, "server_origin");
    var publicId = this.bridgeClient.fromDatabaseId(databaseId, playerUuid, serverScope, serverOrigin)
      .orElse(databaseId);
    var until = safeLong(resultSet, "until");
    var active = safeBoolean(resultSet, "active", true);
    var targetName = firstNonBlank(safeString(resultSet, "name"), historyNames.get(playerUuid), playerUuid);

    return new LiteBanEntry(
      databaseId,
      publicId,
      targetName,
      playerUuid,
      safeString(resultSet, "ip"),
      stripMinecraftColorCodes(safeString(resultSet, "reason")),
      firstNonBlank(safeString(resultSet, "banned_by_name"), safeString(resultSet, "banned_by_uuid")),
      serverScope,
      epochMillis(safeLong(resultSet, "time")),
      until <= 0 ? null : epochMillis(until),
      active,
      firstNonBlank(safeString(resultSet, "removed_by_name"), safeString(resultSet, "removed_by_uuid")),
      epochMillis(safeLong(resultSet, "removed_by_date")),
      Instant.now().toString());
  }

  private String tableName(String suffix) {
    return this.settings().liteBansTablePrefix() + suffix;
  }

  private PanelSettings settings() {
    return this.settingsStore == null
      ? PanelSettings.fromConfiguration(this.configuration)
      : this.settingsStore.current();
  }

  private static String safeString(ResultSet resultSet, String column) {
    if (column == null || column.isBlank()) {
      return null;
    }
    try {
      var value = resultSet.getString(column);
      return value == null || value.isBlank() ? null : value;
    } catch (SQLException exception) {
      return null;
    }
  }

  private static long safeLong(ResultSet resultSet, String column) {
    try {
      return resultSet.getLong(column);
    } catch (SQLException exception) {
      return 0L;
    }
  }

  private static boolean safeBoolean(ResultSet resultSet, String column, boolean fallback) {
    try {
      return resultSet.getBoolean(column);
    } catch (SQLException exception) {
      return fallback;
    }
  }

  private static String epochMillis(long value) {
    return value <= 0 ? null : Instant.ofEpochMilli(value).toString();
  }

  private static String normalizeUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    var stripped = value.replace("-", "");
    if (stripped.length() != 32) {
      return value;
    }

    try {
      var uuid = UUID.fromString(stripped.substring(0, 8) + "-"
        + stripped.substring(8, 12) + "-"
        + stripped.substring(12, 16) + "-"
        + stripped.substring(16, 20) + "-"
        + stripped.substring(20));
      return uuid.toString();
    } catch (IllegalArgumentException exception) {
      return value;
    }
  }

  private static String stripMinecraftColorCodes(String text) {
    return text == null ? null : text.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
  }

  private static String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
