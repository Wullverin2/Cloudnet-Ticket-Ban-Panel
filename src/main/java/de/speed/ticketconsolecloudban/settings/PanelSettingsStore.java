package de.speed.ticketconsolecloudban.settings;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.store.LocalPanelDataBackend;
import de.speed.ticketconsolecloudban.store.PanelDataBackend;
import eu.cloudnetservice.driver.document.Document;
import java.nio.file.Path;
import java.util.Locale;

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
    this.settings = this.withDefaults(this.settings);
    this.save();
  }

  public synchronized PanelSettings current() {
    return this.settings;
  }

  public synchronized PanelSettings update(Document request) {
    var existing = this.settings;
    this.settings = new PanelSettings(
      textOrDefaultWhenBlank(request, "brandName", existing.brandName()),
      text(request, "brandLogoUrl", existing.brandLogoUrl()),
      text(request, "cloudNetScreenName", existing.cloudNetScreenName()),
      normalizeCloudNetRestBaseUrl(text(request, "cloudNetRestBaseUrl", existing.cloudNetRestBaseUrl())),
      text(request, "cloudNetRestUsername", existing.cloudNetRestUsername()),
      password(request, "cloudNetRestPassword", existing.cloudNetRestPassword()),
      normalizeCloudNetRestThreshold(text(request, "cloudNetRestThreshold", existing.cloudNetRestThreshold())),
      bool(request, "smtpEnabled", existing.smtpEnabled()),
      text(request, "smtpHost", existing.smtpHost()),
      integer(request, "smtpPort", existing.smtpPort(), 1, 65535),
      text(request, "smtpUsername", existing.smtpUsername()),
      password(request, "smtpPassword", existing.smtpPassword()),
      text(request, "smtpFrom", existing.smtpFrom()),
      bool(request, "smtpStartTls", existing.smtpStartTls()),
      bool(request, "smtpSsl", existing.smtpSsl()));
    this.save();
    return this.settings;
  }

  private void save() {
    this.backend.save("panel-settings", this.storagePath, this.settings);
  }

  private PanelSettings withDefaults(PanelSettings source) {
    var defaults = PanelSettings.fromConfiguration(this.baseConfiguration);
    return new PanelSettings(
      defaultIfBlank(source.brandName(), defaults.brandName()),
      source.brandLogoUrl() == null ? "" : source.brandLogoUrl(),
      source.cloudNetScreenName() == null ? "" : source.cloudNetScreenName(),
      normalizeCloudNetRestBaseUrl(source.cloudNetRestBaseUrl()),
      source.cloudNetRestUsername() == null ? "" : source.cloudNetRestUsername(),
      source.cloudNetRestPassword() == null ? "" : source.cloudNetRestPassword(),
      normalizeCloudNetRestThreshold(defaultIfBlank(source.cloudNetRestThreshold(), defaults.cloudNetRestThreshold())),
      source.smtpEnabled(),
      defaultIfBlank(source.smtpHost(), defaults.smtpHost()),
      source.smtpPort() <= 0 ? defaults.smtpPort() : source.smtpPort(),
      source.smtpUsername() == null ? "" : source.smtpUsername(),
      source.smtpPassword() == null ? "" : source.smtpPassword(),
      defaultIfBlank(source.smtpFrom(), defaults.smtpFrom()),
      source.smtpStartTls(),
      source.smtpSsl());
  }

  private static String defaultIfBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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

  private static String textOrDefaultWhenBlank(Document request, String key, String fallback) {
    var value = text(request, key, fallback);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String password(Document request, String key, String fallback) {
    if (!request.containsNonNull(key)) {
      return fallback == null ? "" : fallback;
    }
    var value = request.getString(key);
    return value == null || value.isBlank() ? fallback == null ? "" : fallback : value;
  }

  private static String normalizeCloudNetRestBaseUrl(String value) {
    return value == null ? "" : value.trim().replaceAll("/+$", "");
  }

  private static String normalizeCloudNetRestThreshold(String value) {
    var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF" -> normalized;
      default -> "INFO";
    };
  }
}
