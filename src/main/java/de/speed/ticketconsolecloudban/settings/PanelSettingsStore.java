package de.speed.ticketconsolecloudban.settings;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.store.LocalPanelDataBackend;
import de.speed.ticketconsolecloudban.store.PanelDataBackend;
import eu.cloudnetservice.driver.document.Document;
import java.nio.file.Path;

public final class PanelSettingsStore {

  private final PanelConfiguration baseConfiguration;
  private final PanelDataBackend backend;
  private final Path storagePath;
  private PanelSettings settings;

  public PanelSettingsStore(Path dataDirectory, PanelConfiguration baseConfiguration) {
    this(dataDirectory, baseConfiguration, new LocalPanelDataBackend());
  }

  public PanelSettingsStore(Path dataDirectory, PanelConfiguration baseConfiguration, PanelDataBackend backend) {
    this.baseConfiguration = baseConfiguration;
    this.backend = backend;
    this.storagePath = dataDirectory.resolve("panel-settings.json");
    this.settings = this.backend.load(
      "panel-settings",
      this.storagePath,
      PanelSettings.class,
      PanelSettings.fromConfiguration(baseConfiguration));
    this.save();
  }

  public synchronized PanelSettings current() {
    return this.settings;
  }

  public synchronized PanelSettings update(Document request) {
    var existing = this.settings;
    this.settings = new PanelSettings(
      bool(request, "smtpEnabled", existing.smtpEnabled()),
      text(request, "smtpHost", existing.smtpHost()),
      integer(request, "smtpPort", existing.smtpPort(), 1, 65535),
      text(request, "smtpUsername", existing.smtpUsername()),
      password(request, "smtpPassword", existing.smtpPassword()),
      text(request, "smtpFrom", existing.smtpFrom()),
      bool(request, "smtpStartTls", existing.smtpStartTls()),
      bool(request, "smtpSsl", existing.smtpSsl()),
      bool(request, "liteBansDatabaseEnabled", existing.liteBansDatabaseEnabled()),
      text(request, "liteBansJdbcUrl", existing.liteBansJdbcUrl()),
      text(request, "liteBansDatabaseUsername", existing.liteBansDatabaseUsername()),
      password(request, "liteBansDatabasePassword", existing.liteBansDatabasePassword()),
      safeTablePrefix(text(request, "liteBansTablePrefix", existing.liteBansTablePrefix())),
      integer(request, "liteBansDatabaseMaxRows", existing.liteBansDatabaseMaxRows(), 50, 10_000),
      trimTrailingSlash(text(request, "liteBansBridgeBaseUrl", existing.liteBansBridgeBaseUrl())),
      password(request, "liteBansBridgeSecret", existing.liteBansBridgeSecret()),
      integer(request, "liteBansBridgeConnectTimeoutMillis", existing.liteBansBridgeConnectTimeoutMillis(), 500, 30_000),
      integer(request, "liteBansBridgeReadTimeoutMillis", existing.liteBansBridgeReadTimeoutMillis(), 500, 30_000));
    this.save();
    return this.settings;
  }

  public String effectiveLiteBansBridgeSecret() {
    var settings = this.current();
    if (settings.liteBansBridgeSecret() != null && !settings.liteBansBridgeSecret().isBlank()) {
      return settings.liteBansBridgeSecret().trim();
    }
    return this.baseConfiguration.effectiveLiteBansBridgeSecret();
  }

  private void save() {
    this.backend.save("panel-settings", this.storagePath, this.settings);
  }

  private static boolean bool(Document request, String key, boolean fallback) {
    return request.contains(key) ? request.getBoolean(key) : fallback;
  }

  private static int integer(Document request, String key, int fallback, int min, int max) {
    if (!request.contains(key)) {
      return fallback;
    }
    return Math.max(min, Math.min(max, request.getInt(key, fallback)));
  }

  private static String text(Document request, String key, String fallback) {
    if (!request.containsNonNull(key)) {
      return fallback == null ? "" : fallback;
    }
    var value = request.getString(key);
    return value == null ? "" : value.trim();
  }

  private static String password(Document request, String key, String fallback) {
    if (!request.containsNonNull(key)) {
      return fallback == null ? "" : fallback;
    }
    var value = request.getString(key);
    return value == null || value.isBlank() ? fallback == null ? "" : fallback : value;
  }

  private static String safeTablePrefix(String value) {
    var prefix = value == null ? "" : value.trim();
    return prefix.matches("[A-Za-z0-9_]*") ? prefix : "litebans_";
  }

  private static String trimTrailingSlash(String value) {
    return value == null ? "" : value.trim().replaceAll("/+$", "");
  }
}
