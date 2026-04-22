package de.speed.ticketconsolecloudban.quest;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class CraftplayQuestEditorClient {

  private final PanelConfiguration configuration;
  private final PanelSettingsStore settingsStore;

  public CraftplayQuestEditorClient(PanelConfiguration configuration, PanelSettingsStore settingsStore) {
    this.configuration = configuration;
    this.settingsStore = settingsStore;
  }

  public boolean enabled() {
    return this.servers().stream().anyMatch(QuestEditorServerView::enabled);
  }

  public QuestEditorConfigView configView() {
    var servers = this.servers();
    return new QuestEditorConfigView(
      servers.stream().anyMatch(QuestEditorServerView::enabled),
      servers);
  }

  public List<QuestEditorServerView> servers() {
    return this.normalizedServers().stream()
      .map(QuestEditorServerSettings::toView)
      .toList();
  }

  public ProxyResponse get(String serverId, String remotePath) {
    var server = this.resolveServer(serverId);
    if (server.isEmpty()) {
      return new ProxyResponse(
        404,
        "{\"error\":\"Quest-Server wurde im Panel nicht gefunden.\"}");
    }
    if (!server.get().enabled()) {
      return new ProxyResponse(
        503,
        "{\"error\":\"Quest-Server ist im Panel deaktiviert.\",\"enabled\":false}");
    }
    return this.get(server.get(), remotePath);
  }

  public ProxyResponse getFirstEnabled(String remotePath) {
    var server = this.normalizedServers().stream()
      .filter(QuestEditorServerSettings::enabled)
      .findFirst();
    if (server.isEmpty()) {
      return new ProxyResponse(
        503,
        "{\"error\":\"Kein aktiver Quest-Server im Panel hinterlegt.\",\"enabled\":false}");
    }
    return this.get(server.get(), remotePath);
  }

  public Optional<QuestEditorServerSettings> resolveServer(String serverId) {
    var normalizedId = serverId == null ? "" : serverId.trim();
    return this.normalizedServers().stream()
      .filter(server -> server.id().equalsIgnoreCase(normalizedId))
      .findFirst();
  }

  public static String pathSegment(String value) {
    return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
  }

  private ProxyResponse get(QuestEditorServerSettings server, String remotePath) {
    var requestBuilder = HttpRequest.newBuilder(this.uri(server, remotePath))
      .GET()
      .timeout(Duration.ofMillis(server.readTimeoutMillis()))
      .header("Accept", "application/json");

    var token = server.token();
    if (token != null && !token.isBlank()) {
      requestBuilder.header("Authorization", "Bearer " + token.trim());
      requestBuilder.header("X-Craftplay-Token", token.trim());
    }

    try {
      var client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(server.connectTimeoutMillis()))
        .build();
      var response = client.send(
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

  private URI uri(QuestEditorServerSettings server, String remotePath) {
    var normalizedBase = server.baseUrl().endsWith("/")
      ? server.baseUrl().substring(0, server.baseUrl().length() - 1)
      : server.baseUrl();
    var normalizedPath = remotePath.startsWith("/") ? remotePath : "/" + remotePath;
    return URI.create(normalizedBase + normalizedPath);
  }

  private List<QuestEditorServerSettings> normalizedServers() {
    var settings = this.settingsStore.current();
    var servers = settings.questEditorServers();
    if (servers == null || servers.isEmpty()) {
      return List.of(QuestEditorServerSettings.fromConfiguration(this.configuration));
    }
    return servers.stream()
      .filter(server -> server != null)
      .map(server -> server.normalize(0))
      .toList();
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
    List<QuestEditorServerView> servers
  ) {
  }
}
