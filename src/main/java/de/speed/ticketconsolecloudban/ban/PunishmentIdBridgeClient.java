package de.speed.ticketconsolecloudban.ban;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
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
  private final HttpClient httpClient;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  public PunishmentIdBridgeClient(PanelConfiguration configuration) {
    this.configuration = configuration;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(configuration.liteBansBridgeConnectTimeoutMillis()))
      .build();
  }

  public boolean configured() {
    return this.configuration.liteBansBridgeBaseUrl() != null
      && !this.configuration.liteBansBridgeBaseUrl().isBlank()
      && this.configuration.effectiveLiteBansBridgeSecret() != null
      && !this.configuration.effectiveLiteBansBridgeSecret().isBlank();
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
      var uri = URI.create(this.configuration.liteBansBridgeBaseUrl()
        + "/api/punishment-id/from-db?dbId=" + encode(databaseId)
        + "&playerUuid=" + encode(playerUuid)
        + "&serverScope=" + encode(serverScope)
        + "&serverOrigin=" + encode(serverOrigin));
      var request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(this.configuration.liteBansBridgeReadTimeoutMillis()))
        .header("Accept", "application/json")
        .header("X-Bridge-Secret", this.configuration.effectiveLiteBansBridgeSecret())
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
        var value = matcher.group(1);
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
}
