package de.speed.ticketconsolecloudban.velocity;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;

public final class LiteBansBridge {

  private final Logger logger;
  private Object database;
  private Method getBanMethod;
  private Method prepareStatementMethod;
  private Method getPlayerNameMethod;

  public LiteBansBridge(Logger logger) {
    this.logger = logger;
  }

  public boolean available() {
    try {
      this.database();
      return true;
    } catch (Throwable throwable) {
      return false;
    }
  }

  public LiteBanResult activeBan(UUID uniqueId, String address, String serverScope) {
    try {
      var entry = this.getBanMethod().invoke(this.database(), uniqueId, address, normalizeScope(serverScope));
      return entry == null ? null : this.toResult(entry);
    } catch (Throwable throwable) {
      this.logger.warn("LiteBans Ban-Pruefung fehlgeschlagen: {}", throwable.getMessage());
      return null;
    }
  }

  public List<LiteBanSnapshot> activeBans(String serverScope, String publicIdColumn) {
    var bans = new ArrayList<LiteBanSnapshot>();
    try {
      var statement = this.prepareStatement("SELECT * FROM {bans} WHERE active=1");
      try (statement; ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          bans.add(this.toSnapshot(resultSet, publicIdColumn));
        }
      }
    } catch (Throwable throwable) {
      this.logger.warn("LiteBans Sync fehlgeschlagen: {}", throwable.getMessage());
    }
    return List.copyOf(bans);
  }

  public String describe(LiteBanResult ban) {
    if (ban == null) {
      return "Kein aktiver LiteBans-Ban gefunden.";
    }

    return "Aktiver Ban: " + ban.reason() + " | Verbleibend: " + ban.remaining();
  }

  private LiteBanResult toResult(Object entry) throws ReflectiveOperationException {
    var entryClass = entry.getClass();
    var reason = String.valueOf(entryClass.getMethod("getReason").invoke(entry));
    var permanent = Boolean.TRUE.equals(entryClass.getMethod("isPermanent").invoke(entry));
    var remaining = permanent
      ? "permanent"
      : String.valueOf(entryClass.getMethod("getRemainingDurationString", long.class).invoke(entry, System.currentTimeMillis()));
    return new LiteBanResult(reason == null || reason.isBlank() || "null".equals(reason) ? "Kein Grund angegeben" : reason, remaining);
  }

  private Object database() throws ReflectiveOperationException {
    if (this.database == null) {
      var databaseClass = Class.forName("litebans.api.Database");
      this.database = databaseClass.getMethod("get").invoke(null);
    }
    return this.database;
  }

  private java.sql.PreparedStatement prepareStatement(String sql) throws ReflectiveOperationException {
    if (this.prepareStatementMethod == null) {
      this.prepareStatementMethod = this.database().getClass().getMethod("prepareStatement", String.class);
    }
    return (java.sql.PreparedStatement) this.prepareStatementMethod.invoke(this.database(), sql);
  }

  private String playerName(UUID uuid) {
    if (uuid == null) {
      return null;
    }
    try {
      if (this.getPlayerNameMethod == null) {
        this.getPlayerNameMethod = this.database().getClass().getMethod("getPlayerName", UUID.class);
      }
      var name = this.getPlayerNameMethod.invoke(this.database(), uuid);
      return name == null ? null : String.valueOf(name);
    } catch (Throwable throwable) {
      return null;
    }
  }

  private Method getBanMethod() throws ReflectiveOperationException {
    if (this.getBanMethod == null) {
      this.getBanMethod = this.database().getClass().getMethod("getBan", UUID.class, String.class, String.class);
    }
    return this.getBanMethod;
  }

  private static String normalizeScope(String serverScope) {
    return serverScope == null || serverScope.isBlank() || "*".equals(serverScope.trim())
      ? "__ALL__"
      : serverScope.trim();
  }

  private LiteBanSnapshot toSnapshot(ResultSet resultSet, String publicIdColumn) throws SQLException {
    var id = safeString(resultSet, "id");
    var publicId = safeString(resultSet, publicIdColumn);
    var uuid = normalizeUuid(safeString(resultSet, "uuid"));
    var uniqueId = parseUuid(uuid);
    var targetName = firstNonBlank(safeString(resultSet, "name"), this.playerName(uniqueId));
    var until = safeLong(resultSet, "until");

    return new LiteBanSnapshot(
      id,
      firstNonBlank(publicId, id),
      targetName,
      uuid,
      safeString(resultSet, "ip"),
      safeString(resultSet, "reason"),
      firstNonBlank(safeString(resultSet, "banned_by_name"), safeString(resultSet, "banned_by_uuid")),
      safeString(resultSet, "server_scope"),
      epochMillis(safeLong(resultSet, "time")),
      until <= 0 ? null : epochMillis(until),
      safeBoolean(resultSet, "active"),
      firstNonBlank(safeString(resultSet, "removed_by_name"), safeString(resultSet, "removed_by_uuid")),
      epochMillis(safeLong(resultSet, "removed_by_date")),
      Instant.now().toString());
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

  private static boolean safeBoolean(ResultSet resultSet, String column) {
    try {
      return resultSet.getBoolean(column);
    } catch (SQLException exception) {
      return true;
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
    return stripped.substring(0, 8) + "-"
      + stripped.substring(8, 12) + "-"
      + stripped.substring(12, 16) + "-"
      + stripped.substring(16, 20) + "-"
      + stripped.substring(20);
  }

  private static UUID parseUuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private static String firstNonBlank(String first, String second) {
    return first == null || first.isBlank() ? second : first;
  }

  public record LiteBanResult(String reason, String remaining) {
  }
}
