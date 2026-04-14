package de.speed.ticketconsolecloudban.settings;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.store.LocalPanelDataBackend;
import de.speed.ticketconsolecloudban.store.PanelDataBackend;
import eu.cloudnetservice.driver.document.Document;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
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
      ticketCategories(request, existing.ticketCategories()),
      text(request, "cloudNetScreenName", existing.cloudNetScreenName()),
      textOrDefaultWhenBlank(request, "appealStatusOpenLabel", existing.appealStatusOpenLabel()),
      textOrDefaultWhenBlank(request, "appealStatusInReviewLabel", existing.appealStatusInReviewLabel()),
      textOrDefaultWhenBlank(request, "appealStatusAcceptedLabel", existing.appealStatusAcceptedLabel()),
      textOrDefaultWhenBlank(request, "appealStatusRejectedLabel", existing.appealStatusRejectedLabel()),
      textOrDefaultWhenBlank(request, "appealStatusClosedLabel", existing.appealStatusClosedLabel()),
      textOrDefaultWhenBlank(request, "appealStatusOpenText", existing.appealStatusOpenText()),
      textOrDefaultWhenBlank(request, "appealStatusInReviewText", existing.appealStatusInReviewText()),
      textOrDefaultWhenBlank(request, "appealStatusAcceptedText", existing.appealStatusAcceptedText()),
      textOrDefaultWhenBlank(request, "appealStatusRejectedText", existing.appealStatusRejectedText()),
      textOrDefaultWhenBlank(request, "appealStatusClosedText", existing.appealStatusClosedText()),
      trimTrailingSlash(text(request, "appealPublicBaseUrl", existing.appealPublicBaseUrl())),
      integer(request, "appealMaxFiles", existing.appealMaxFiles(), 1, 10),
      longNumber(request, "appealMaxFileBytes", existing.appealMaxFileBytes(), 1L, 100L * 1024L * 1024L),
      normalizeEvidenceStorage(text(request, "appealEvidenceStorage", existing.appealEvidenceStorage())),
      textOrDefaultWhenBlank(request, "appealEvidenceLocalDirectory", existing.appealEvidenceLocalDirectory()),
      text(request, "appealEvidenceSftpHost", existing.appealEvidenceSftpHost()),
      integer(request, "appealEvidenceSftpPort", existing.appealEvidenceSftpPort(), 1, 65535),
      text(request, "appealEvidenceSftpUsername", existing.appealEvidenceSftpUsername()),
      password(request, "appealEvidenceSftpPassword", existing.appealEvidenceSftpPassword()),
      text(request, "appealEvidenceSftpPrivateKeyPath", existing.appealEvidenceSftpPrivateKeyPath()),
      textOrDefaultWhenBlank(request, "appealEvidenceSftpRemoteDirectory", existing.appealEvidenceSftpRemoteDirectory()),
      text(request, "appealEvidenceOneDriveUploadUrlTemplate", existing.appealEvidenceOneDriveUploadUrlTemplate()),
      password(request, "appealEvidenceOneDriveBearerToken", existing.appealEvidenceOneDriveBearerToken()),
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

  private PanelSettings withDefaults(PanelSettings source) {
    var defaults = PanelSettings.fromConfiguration(this.baseConfiguration);
    return new PanelSettings(
      defaultIfBlank(source.brandName(), defaults.brandName()),
      source.brandLogoUrl() == null ? "" : source.brandLogoUrl(),
      sanitizeTicketCategories(source.ticketCategories(), defaults.ticketCategories()),
      source.cloudNetScreenName() == null ? "" : source.cloudNetScreenName(),
      defaultIfBlank(source.appealStatusOpenLabel(), defaults.appealStatusOpenLabel()),
      defaultIfBlank(source.appealStatusInReviewLabel(), defaults.appealStatusInReviewLabel()),
      defaultIfBlank(source.appealStatusAcceptedLabel(), defaults.appealStatusAcceptedLabel()),
      defaultIfBlank(source.appealStatusRejectedLabel(), defaults.appealStatusRejectedLabel()),
      defaultIfBlank(source.appealStatusClosedLabel(), defaults.appealStatusClosedLabel()),
      defaultIfBlank(source.appealStatusOpenText(), defaults.appealStatusOpenText()),
      defaultIfBlank(source.appealStatusInReviewText(), defaults.appealStatusInReviewText()),
      defaultIfBlank(source.appealStatusAcceptedText(), defaults.appealStatusAcceptedText()),
      defaultIfBlank(source.appealStatusRejectedText(), defaults.appealStatusRejectedText()),
      defaultIfBlank(source.appealStatusClosedText(), defaults.appealStatusClosedText()),
      defaultIfBlank(source.appealPublicBaseUrl(), defaults.appealPublicBaseUrl()).replaceAll("/+$", ""),
      source.appealMaxFiles() <= 0 ? defaults.appealMaxFiles() : Math.min(source.appealMaxFiles(), 10),
      source.appealMaxFileBytes() <= 0 ? defaults.appealMaxFileBytes() : Math.min(source.appealMaxFileBytes(), 100L * 1024L * 1024L),
      normalizeEvidenceStorage(defaultIfBlank(source.appealEvidenceStorage(), defaults.appealEvidenceStorage())),
      defaultIfBlank(source.appealEvidenceLocalDirectory(), defaults.appealEvidenceLocalDirectory()),
      source.appealEvidenceSftpHost() == null ? "" : source.appealEvidenceSftpHost(),
      source.appealEvidenceSftpPort() <= 0 ? defaults.appealEvidenceSftpPort() : source.appealEvidenceSftpPort(),
      source.appealEvidenceSftpUsername() == null ? "" : source.appealEvidenceSftpUsername(),
      source.appealEvidenceSftpPassword() == null ? "" : source.appealEvidenceSftpPassword(),
      source.appealEvidenceSftpPrivateKeyPath() == null ? "" : source.appealEvidenceSftpPrivateKeyPath(),
      defaultIfBlank(source.appealEvidenceSftpRemoteDirectory(), defaults.appealEvidenceSftpRemoteDirectory()),
      source.appealEvidenceOneDriveUploadUrlTemplate() == null ? "" : source.appealEvidenceOneDriveUploadUrlTemplate(),
      source.appealEvidenceOneDriveBearerToken() == null ? "" : source.appealEvidenceOneDriveBearerToken(),
      source.smtpEnabled(),
      defaultIfBlank(source.smtpHost(), defaults.smtpHost()),
      source.smtpPort() <= 0 ? defaults.smtpPort() : source.smtpPort(),
      source.smtpUsername() == null ? "" : source.smtpUsername(),
      source.smtpPassword() == null ? "" : source.smtpPassword(),
      defaultIfBlank(source.smtpFrom(), defaults.smtpFrom()),
      source.smtpStartTls(),
      source.smtpSsl(),
      source.liteBansDatabaseEnabled(),
      defaultIfBlank(source.liteBansJdbcUrl(), defaults.liteBansJdbcUrl()),
      source.liteBansDatabaseUsername() == null ? "" : source.liteBansDatabaseUsername(),
      source.liteBansDatabasePassword() == null ? "" : source.liteBansDatabasePassword(),
      defaultIfBlank(source.liteBansTablePrefix(), defaults.liteBansTablePrefix()),
      source.liteBansDatabaseMaxRows() <= 0 ? defaults.liteBansDatabaseMaxRows() : source.liteBansDatabaseMaxRows(),
      defaultIfBlank(source.liteBansBridgeBaseUrl(), defaults.liteBansBridgeBaseUrl()),
      source.liteBansBridgeSecret() == null ? "" : source.liteBansBridgeSecret(),
      source.liteBansBridgeConnectTimeoutMillis() <= 0 ? defaults.liteBansBridgeConnectTimeoutMillis() : source.liteBansBridgeConnectTimeoutMillis(),
      source.liteBansBridgeReadTimeoutMillis() <= 0 ? defaults.liteBansBridgeReadTimeoutMillis() : source.liteBansBridgeReadTimeoutMillis());
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

  private static long longNumber(Document request, String key, long fallback, long min, long max) {
    if (!request.contains(key)) {
      return fallback;
    }
    var value = request.getLong(key, fallback);
    return Math.max(min, Math.min(max, value));
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

  private static List<String> ticketCategories(Document request, List<String> fallback) {
    if (!request.contains("ticketCategories")) {
      return sanitizeTicketCategories(fallback, PanelSettings.DEFAULT_TICKET_CATEGORIES);
    }
    var values = request.readObject("ticketCategories", String[].class, new String[0]);
    return sanitizeTicketCategories(List.of(values), fallback);
  }

  private static List<String> sanitizeTicketCategories(List<String> values, List<String> fallback) {
    var categories = new LinkedHashSet<String>();
    if (values != null) {
      for (var value : values) {
        var normalized = normalizeTicketCategory(value);
        if (normalized != null) {
          categories.add(normalized);
        }
      }
    }
    if (categories.isEmpty() && fallback != null) {
      for (var value : fallback) {
        var normalized = normalizeTicketCategory(value);
        if (normalized != null) {
          categories.add(normalized);
        }
      }
    }
    if (categories.isEmpty()) {
      categories.addAll(PanelSettings.DEFAULT_TICKET_CATEGORIES);
    }
    return List.copyOf(categories);
  }

  private static String normalizeTicketCategory(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    var normalized = value.trim()
      .replace(' ', '_')
      .replace('-', '_')
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9_]", "");
    return normalized.isBlank() ? null : normalized;
  }

  private static String safeTablePrefix(String value) {
    var prefix = value == null ? "" : value.trim();
    return prefix.matches("[A-Za-z0-9_]*") ? prefix : "litebans_";
  }

  private static String trimTrailingSlash(String value) {
    return value == null ? "" : value.trim().replaceAll("/+$", "");
  }

  private static String normalizeEvidenceStorage(String value) {
    var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "SFTP", "ONEDRIVE" -> normalized;
      default -> "LOCAL";
    };
  }
}
