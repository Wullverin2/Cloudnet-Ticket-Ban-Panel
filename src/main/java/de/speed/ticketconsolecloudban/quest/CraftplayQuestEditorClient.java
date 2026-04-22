package de.speed.ticketconsolecloudban.quest;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class CraftplayQuestEditorClient {

  private final PanelConfiguration configuration;
  private final HttpClient httpClient;

  public CraftplayQuestEditorClient(PanelConfiguration configuration) {
    this.configuration = configuration;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(configuration.questEditorConnectTimeoutMillis()))
      .build();
  }

  public boolean enabled() {
    return this.configuration.questEditorEnabled();
  }

  public QuestEditorConfigView configView() {
    return new QuestEditorConfigView(
      this.configuration.questEditorEnabled(),
      this.configuration.questEditorBaseUrl(),
      this.configuration.questEditorConnectTimeoutMillis(),
      this.configuration.questEditorReadTimeoutMillis(),
      this.configuration.questEditorToken() != null && !this.configuration.questEditorToken().isBlank());
  }

  public ProxyResponse get(String remotePath) {
    if (!this.enabled()) {
      return new ProxyResponse(
        503,
        "{\"error\":\"Quest-Editor-API ist im Panel deaktiviert.\",\"enabled\":false}");
    }

    var requestBuilder = HttpRequest.newBuilder(this.uri(remotePath))
      .GET()
      .timeout(Duration.ofMillis(this.configuration.questEditorReadTimeoutMillis()))
      .header("Accept", "application/json");

    var token = this.configuration.questEditorToken();
    if (token != null && !token.isBlank()) {
      requestBuilder.header("Authorization", "Bearer " + token.trim());
      requestBuilder.header("X-Craftplay-Token", token.trim());
    }

    try {
      var response = this.httpClient.send(
        requestBuilder.build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      var body = response.body() == null || response.body().isBlank()
        ? "{}"
        : response.body();
      return new ProxyResponse(response.statusCode(), body);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new ProxyResponse(502, "{\"error\":\"Quest-Editor-Anfrage wurde unterbrochen.\"}");
    } catch (IOException | IllegalArgumentException exception) {
      return new ProxyResponse(
        502,
        "{\"error\":\"CraftplayQuests-API nicht erreichbar: " + jsonEscape(exception.getMessage()) + "\"}");
    }
  }

  public static String pathSegment(String value) {
    return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
  }

  private URI uri(String remotePath) {
    var baseUrl = this.configuration.questEditorBaseUrl();
    var normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    var normalizedPath = remotePath.startsWith("/") ? remotePath : "/" + remotePath;
    return URI.create(normalizedBase + normalizedPath);
  }

  private static String jsonEscape(String value) {
    if (value == null || value.isBlank()) {
      return "Unbekannter Fehler";
    }
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\r", "\\r")
      .replace("\n", "\\n");
  }

  public record ProxyResponse(
    int statusCode,
    String body
  ) {
  }

  public record QuestEditorConfigView(
    boolean enabled,
    String baseUrl,
    int connectTimeoutMillis,
    int readTimeoutMillis,
    boolean tokenConfigured
  ) {
  }
}
