package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import eu.cloudnetservice.driver.document.DocumentFactory;
import eu.cloudnetservice.driver.document.StandardSerialisationStyle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SqlPanelDataBackend implements PanelDataBackend {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqlPanelDataBackend.class);

  private final PanelConfiguration configuration;
  private final String tableName;

  public SqlPanelDataBackend(PanelConfiguration configuration) {
    this.configuration = configuration;
    this.tableName = sanitizeTableName(configuration.panelSqlTable());
    this.initialize();
  }

  @Override
  public <T> T load(String storeKey, Path localPath, Class<T> type, T fallback) {
    try (var connection = this.connection()) {
      var payload = this.readPayload(connection, storeKey);
      if (payload == null && Files.exists(localPath)) {
        payload = Files.readString(localPath);
        this.writePayload(connection, storeKey, payload);
        LOGGER.info("Lokaler Panel-Speicher {} wurde nach SQL migriert.", localPath.getFileName());
      }
      if (payload == null || payload.isBlank()) {
        return fallback;
      }

      var data = DocumentFactory.json().parse(payload).toInstanceOf(type);
      return data == null ? fallback : data;
    } catch (Exception exception) {
      throw new IllegalStateException("SQL Panel-Speicher konnte nicht geladen werden: " + storeKey, exception);
    }
  }

  @Override
  public void save(String storeKey, Path localPath, Object data) {
    try (var connection = this.connection()) {
      var payload = DocumentFactory.json()
        .newDocument()
        .appendTree(data)
        .serializeToString(StandardSerialisationStyle.COMPACT);
      this.writePayload(connection, storeKey, payload);
    } catch (Exception exception) {
      throw new IllegalStateException("SQL Panel-Speicher konnte nicht geschrieben werden: " + storeKey, exception);
    }
  }

  private void initialize() {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver");
      try (var connection = this.connection();
           var statement = connection.createStatement()) {
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.tableName + " ("
          + "store_key VARCHAR(96) NOT NULL PRIMARY KEY,"
          + "payload MEDIUMTEXT NOT NULL,"
          + "updated_at VARCHAR(64) NOT NULL"
          + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
      }
    } catch (Exception exception) {
      throw new IllegalStateException("SQL Panel-Speicher konnte nicht initialisiert werden.", exception);
    }
  }

  private String readPayload(Connection connection, String storeKey) throws SQLException {
    try (var statement = connection.prepareStatement("SELECT payload FROM " + this.tableName + " WHERE store_key=?")) {
      statement.setString(1, storeKey);
      try (var resultSet = statement.executeQuery()) {
        return resultSet.next() ? resultSet.getString("payload") : null;
      }
    }
  }

  private void writePayload(Connection connection, String storeKey, String payload) throws SQLException {
    try (var statement = connection.prepareStatement(
      "INSERT INTO " + this.tableName + " (store_key, payload, updated_at) VALUES (?, ?, ?) "
        + "ON DUPLICATE KEY UPDATE payload=VALUES(payload), updated_at=VALUES(updated_at)")) {
      statement.setString(1, storeKey);
      statement.setString(2, payload);
      statement.setString(3, Instant.now().toString());
      statement.executeUpdate();
    }
  }

  private Connection connection() throws SQLException {
    return DriverManager.getConnection(
      this.configuration.panelSqlJdbcUrl(),
      this.configuration.panelSqlUsername(),
      this.configuration.panelSqlPassword());
  }

  private static String sanitizeTableName(String value) {
    var table = value == null || value.isBlank() ? "tccb_panel_data" : value.trim();
    if (!table.matches("[A-Za-z0-9_]+")) {
      throw new IllegalArgumentException("panelSqlTable darf nur Buchstaben, Zahlen und Unterstriche enthalten.");
    }
    return table;
  }
}
