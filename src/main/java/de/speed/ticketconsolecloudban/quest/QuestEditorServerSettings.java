package de.speed.ticketconsolecloudban.quest;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.net.URI;
import java.util.Locale;

public record QuestEditorServerSettings(
  String id,
  String name,
  String host,
  int port,
  boolean enabled,
  String basePath,
  String token,
  int connectTimeoutMillis,
  int readTimeoutMillis
) {

  public static final String DEFAULT_ID = "default";
  public static final String DEFAULT_NAME = "Craftplay Server";
  public static final String DEFAULT_HOST = "127.0.0.1";
  public static final int DEFAULT_PORT = 8095;
  public static final String DEFAULT_BASE_PATH = "/api/craftplayquests/v1";
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;

  public static QuestEditorServerSettings fromConfiguration(PanelConfiguration configuration) {
    var baseUrl = configuration.questEditorBaseUrl();
    var host = DEFAULT_HOST;
    var port = DEFAULT_PORT;
    var basePath = DEFAULT_BASE_PATH;
    if (baseUrl != null && !baseUrl.isBlank()) {
      try {
        var uri = URI.create(baseUrl.trim());
        if (uri.getHost() != null && !uri.getHost().isBlank()) {
          host = uri.getHost();
        }
        if (uri.getPort() > 0) {
          port = uri.getPort();
        }
        if (uri.getPath() != null && !uri.getPath().isBlank()) {
          basePath = uri.getPath();
        }
      } catch (IllegalArgumentException ignored) {
        // The legacy value can be repaired in the panel settings UI.
      }
    }

    return new QuestEditorServerSettings(
      DEFAULT_ID,
      DEFAULT_NAME,
      host,
      port,
      configuration.questEditorEnabled(),
      basePath,
      configuration.questEditorToken(),
      configuration.questEditorConnectTimeoutMillis(),
      configuration.questEditorReadTimeoutMillis()).normalize(0);
  }

  public QuestEditorServerSettings normalize(int index) {
    var normalizedId = normalizeId(firstNonBlank(this.id, this.name, "server-" + (index + 1)));
    var normalizedName = firstNonBlank(this.name, this.id, "Quest-Server " + (index + 1)).trim();
    var normalizedHost = normalizeHost(this.host);
    var normalizedPort = this.port > 0 && this.port <= 0xFFFF ? this.port : DEFAULT_PORT;
    var normalizedPath = normalizeBasePath(this.basePath);
    var normalizedToken = this.token == null ? "" : this.token.trim();
    var normalizedConnectTimeout = clamp(
      this.connectTimeoutMillis <= 0 ? DEFAULT_CONNECT_TIMEOUT_MILLIS : this.connectTimeoutMillis,
      500,
      30_000);
    var normalizedReadTimeout = clamp(
      this.readTimeoutMillis <= 0 ? DEFAULT_READ_TIMEOUT_MILLIS : this.readTimeoutMillis,
      500,
      60_000);
    return new QuestEditorServerSettings(
      normalizedId,
      normalizedName,
      normalizedHost,
      normalizedPort,
      this.enabled,
      normalizedPath,
      normalizedToken,
      normalizedConnectTimeout,
      normalizedReadTimeout);
  }

  public QuestEditorServerSettings withToken(String newToken) {
    return new QuestEditorServerSettings(
      this.id,
      this.name,
      this.host,
      this.port,
      this.enabled,
      this.basePath,
      newToken == null ? "" : newToken,
      this.connectTimeoutMillis,
      this.readTimeoutMillis);
  }

  public QuestEditorServerSettings withId(String newId) {
    return new QuestEditorServerSettings(
      newId,
      this.name,
      this.host,
      this.port,
      this.enabled,
      this.basePath,
      this.token,
      this.connectTimeoutMillis,
      this.readTimeoutMillis);
  }

  public String baseUrl() {
    return "http://" + this.host + ":" + this.port + this.basePath;
  }

  public QuestEditorServerView toView() {
    return new QuestEditorServerView(
      this.id,
      this.name,
      this.host,
      this.port,
      this.enabled,
      this.basePath,
      this.baseUrl(),
      this.connectTimeoutMillis,
      this.readTimeoutMillis,
      this.token != null && !this.token.isBlank(),
      "");
  }

  private static String normalizeHost(String value) {
    var normalized = firstNonBlank(value, DEFAULT_HOST).trim();
    normalized = normalized.replaceFirst("(?i)^https?://", "");
    var slashIndex = normalized.indexOf('/');
    if (slashIndex >= 0) {
      normalized = normalized.substring(0, slashIndex);
    }
    if (normalized.startsWith("[") && normalized.contains("]")) {
      return normalized.substring(1, normalized.indexOf(']'));
    }
    var colonIndex = normalized.indexOf(':');
    if (colonIndex > 0) {
      normalized = normalized.substring(0, colonIndex);
    }
    return normalized.isBlank() ? DEFAULT_HOST : normalized;
  }

  private static String normalizeBasePath(String value) {
    var normalized = firstNonBlank(value, DEFAULT_BASE_PATH).trim();
    if (!normalized.startsWith("/")) {
      normalized = "/" + normalized;
    }
    normalized = normalized.replaceAll("/+$", "");
    return normalized.isBlank() ? DEFAULT_BASE_PATH : normalized;
  }

  private static String normalizeId(String value) {
    var normalized = firstNonBlank(value, DEFAULT_ID)
      .trim()
      .toLowerCase(Locale.ROOT)
      .replace(' ', '-')
      .replace('_', '-')
      .replaceAll("[^a-z0-9-]", "");
    return normalized.isBlank() ? DEFAULT_ID : normalized;
  }

  private static String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
