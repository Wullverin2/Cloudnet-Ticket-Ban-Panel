package de.speed.ticketconsolecloudban.ban;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PunishmentIdBridgeClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(PunishmentIdBridgeClient.class);
  private static final Pattern SUCCESS_PATTERN = Pattern.compile("\"success\"\\s*:\\s*true");
  private static final Pattern VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*\"([^\"]+)\"");

  private final PanelConfiguration configuration;
  private final PanelSettingsStore settingsStore;
  private final HttpClient httpClient;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  public PunishmentIdBridgeClient(PanelConfiguration configuration) {
    this.configuration = configuration;
    this.settingsStore = null;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(configuration.liteBansBridgeConnectTimeoutMillis()))
      .build();
  }

  public PunishmentIdBridgeClient(PanelConfiguration configuration, PanelSettingsStore settingsStore) {
    this.configuration = configuration;
    this.settingsStore = settingsStore;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(settingsStore.current().liteBansBridgeConnectTimeoutMillis()))
      .build();
  }

  public boolean configured() {
    return this.bridgeBaseUrl() != null
      && !this.bridgeBaseUrl().isBlank()
      && this.bridgeSecret() != null
      && !this.bridgeSecret().isBlank();
  }

  public Optional<String> fromDatabaseId(String databaseId, String playerUuid, String serverScope, String serverOrigin) {
    if (!this.configured() || databaseId == null || databaseId.isBlank()) {
      return Optional.empty();
    }

    var cacheKey = databaseId.trim() + "|" + nullSafe(playerUuid) + "|" + nullSafe(serverScope) + "|" + nullSafe(serverOrigin);
    var cached = this.cache.get(cacheKey);
    if (cached != null && !cached.isBlank()) {
      return Optional.of(cached);
    }

    try {
      var uri = URI.create(this.bridgeBaseUrl()
        + "/api/punishment-id/from-db?dbId=" + encode(databaseId)
        + "&playerUuid=" + encode(playerUuid)
        + "&serverScope=" + encode(serverScope)
        + "&serverOrigin=" + encode(serverOrigin));
      var request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(this.bridgeReadTimeoutMillis()))
        .header("Accept", "application/json")
        .header("X-Bridge-Secret", this.bridgeSecret())
        .GET()
        .build();
      var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        LOGGER.warn("LiteBans Punishment-ID-Bridge antwortete mit HTTP {} fuer DB-ID {}", response.statusCode(), databaseId);
        return Optional.empty();
      }

      var body = response.body() == null ? "" : response.body();
      if (!SUCCESS_PATTERN.matcher(body).find()) {
        return Optional.empty();
      }

      var matcher = VALUE_PATTERN.matcher(body);
      if (matcher.find()) {
        var value = matcher.group(1).trim();
        if (!isResolvedPublicId(value, databaseId)) {
          LOGGER.warn("LiteBans Punishment-ID-Bridge lieferte nur die interne DB-ID {}. Random-ID wird nicht ueberschrieben.", databaseId);
          return Optional.empty();
        }
        this.cache.put(cacheKey, value);
        return Optional.of(value);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      LOGGER.warn("LiteBans Punishment-ID-Bridge Anfrage wurde unterbrochen.");
    } catch (Exception exception) {
      LOGGER.warn("LiteBans Punishment-ID-Bridge ist nicht erreichbar: {}", exception.getMessage());
    }

    return Optional.empty();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value.trim();
  }

  private static boolean isResolvedPublicId(String value, String databaseId) {
    if (value == null || value.isBlank()) {
      return false;
    }
    var trimmed = value.trim();
    if (databaseId != null && trimmed.equalsIgnoreCase(databaseId.trim())) {
      return false;
    }
    return !trimmed.matches("\\d+");
  }

  private String bridgeBaseUrl() {
    return this.settingsStore == null
      ? this.configuration.liteBansBridgeBaseUrl()
      : this.settingsStore.current().liteBansBridgeBaseUrl();
  }

  private String bridgeSecret() {
    return this.settingsStore == null
      ? this.configuration.effectiveLiteBansBridgeSecret()
      : this.settingsStore.effectiveLiteBansBridgeSecret();
  }

  private int bridgeReadTimeoutMillis() {
    return this.settingsStore == null
      ? this.configuration.liteBansBridgeReadTimeoutMillis()
      : this.settingsStore.current().liteBansBridgeReadTimeoutMillis();
  }
}
