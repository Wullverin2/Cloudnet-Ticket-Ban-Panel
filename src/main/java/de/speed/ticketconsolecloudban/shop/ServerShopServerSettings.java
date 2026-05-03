package de.speed.ticketconsolecloudban.shop;

import java.util.Locale;

public record ServerShopServerSettings(
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

  public static final String DEFAULT_ID = "survival";
  public static final String DEFAULT_NAME = "Survival";
  public static final String DEFAULT_HOST = "127.0.0.1";
  public static final int DEFAULT_PORT = 8096;
  public static final String DEFAULT_BASE_PATH = "/api/craftplayshop/v1";
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
  public static final int DEFAULT_READ_TIMEOUT_MILLIS = 5000;

  public static ServerShopServerSettings defaultServer() {
    return new ServerShopServerSettings(
      DEFAULT_ID,
      DEFAULT_NAME,
      DEFAULT_HOST,
      DEFAULT_PORT,
      false,
      DEFAULT_BASE_PATH,
      "",
      DEFAULT_CONNECT_TIMEOUT_MILLIS,
      DEFAULT_READ_TIMEOUT_MILLIS);
  }

  public ServerShopServerSettings normalize(int index) {
    var normalizedId = normalizeId(firstNonBlank(this.id, this.name, "shop-server-" + (index + 1)));
    var normalizedName = firstNonBlank(this.name, this.id, "Shop-Server " + (index + 1)).trim();
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
    return new ServerShopServerSettings(
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

  public ServerShopServerSettings withToken(String newToken) {
    return new ServerShopServerSettings(
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

  public ServerShopServerSettings withId(String newId) {
    return new ServerShopServerSettings(
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

  public ServerShopServerView toView() {
    return new ServerShopServerView(
      this.id,
      this.name,
      this.host,
      this.port,
      this.enabled,
      this.basePath,
      this.baseUrl(),
      this.connectTimeoutMillis,
      this.readTimeoutMillis,
      this.token != null && !this.token.isBlank());
  }

  private static String normalizeHost(String value) {
    var normalized = firstNonBlank(value, DEFAULT_HOST).trim();
    normalized = normalized.replaceFirst("(?i)^https?://", "");
    var slashIndex = normalized.indexOf('/');
    if (slashIndex >= 0) {
      normalized = normalized.substring(0, slashIndex);
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
