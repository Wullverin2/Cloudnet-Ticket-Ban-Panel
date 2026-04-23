package de.speed.ticketconsolecloudban.service;

import de.speed.ticketconsolecloudban.settings.PanelSettings;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import eu.cloudnetservice.driver.document.DocumentFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class CloudNetRestConsoleClient implements AutoCloseable {

  private static final Set<String> REQUIRED_SCOPES = Set.of(
    "cloudnet_rest:node_read",
    "cloudnet_rest:node_log_lines",
    "cloudnet_rest:node_live_console");

  private final PanelSettingsStore settingsStore;
  private final HttpClient httpClient;
  private final int maxBufferedLines;
  private final ArrayDeque<String> bufferedLines = new ArrayDeque<>();
  private final StringBuilder partialTextFrame = new StringBuilder();

  private RestConfiguration activeConfiguration;
  private WebSocket webSocket;
  private String accessToken;
  private Instant accessTokenExpiresAt = Instant.EPOCH;
  private boolean webSocketConnecting;
  private String source;
  private String lastError;

  public CloudNetRestConsoleClient(PanelSettingsStore settingsStore, int maxBufferedLines) {
    this.settingsStore = settingsStore;
    this.maxBufferedLines = Math.max(250, maxBufferedLines);
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  }

  public synchronized ConsoleResult console(int requestedLimit) {
    var settings = this.settingsStore.current();
    if (!hasAnyRestConfiguration(settings)) {
      return new ConsoleResult(false, List.of(), null);
    }

    var configuration = currentConfiguration(settings);
    if (configuration == null) {
      return new ConsoleResult(
        true,
        List.of("CloudNet REST ist unvollständig konfiguriert. Bitte Basis-URL, Benutzer und Passwort eintragen."),
        "cloudnet-rest");
    }

    try {
      this.ensureConnected(configuration);
    } catch (Exception exception) {
      this.lastError = "CloudNet REST ist nicht erreichbar: " + this.exceptionMessage(exception);
    }

    var lines = this.latestLines(requestedLimit);
    if (!lines.isEmpty()) {
      return new ConsoleResult(true, lines, this.source);
    }
    if (this.lastError != null && !this.lastError.isBlank()) {
      return new ConsoleResult(true, List.of(this.lastError), this.sourceOrDefault(configuration));
    }
    return new ConsoleResult(
      true,
      List.of("CloudNet REST Live-Konsole wird verbunden ..."),
      this.sourceOrDefault(configuration));
  }

  @Override
  public synchronized void close() {
    if (this.webSocket != null) {
      try {
        this.webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Panel stopped");
      } catch (Exception ignored) {
        this.webSocket.abort();
      }
    }
    this.resetState();
  }

  private synchronized void ensureConnected(RestConfiguration configuration) throws IOException, InterruptedException {
    if (!configuration.equals(this.activeConfiguration)) {
      this.resetState();
      this.activeConfiguration = configuration;
      this.source = this.sourceOrDefault(configuration);
    }

    if (this.accessToken == null || Instant.now().isAfter(this.accessTokenExpiresAt.minusSeconds(30))) {
      this.authenticate(configuration);
    }

    if (this.bufferedLines.isEmpty()) {
      this.bootstrapLogLines(configuration);
    }

    if (this.webSocket == null && !this.webSocketConnecting) {
      this.connectWebSocket(configuration);
    }
  }

  private synchronized void authenticate(RestConfiguration configuration) throws IOException, InterruptedException {
    var payload = "{\"scopes\":[" + REQUIRED_SCOPES.stream()
      .map(scope -> "\"" + scope + "\"")
      .reduce((left, right) -> left + "," + right)
      .orElse("") + "]}";
    var request = HttpRequest.newBuilder(configuration.authUri())
      .header("Authorization", this.basicAuthorization(configuration))
      .header("Content-Type", "application/json")
      .timeout(Duration.ofSeconds(10))
      .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
      .build();
    var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("HTTP " + response.statusCode() + ": " + this.responseErrorMessage(response.body()));
    }

    var auth = DocumentFactory.json().parse(response.body()).toInstanceOf(JwtAuthResponse.class);
    if (auth == null || auth.accessToken() == null || auth.accessToken().token() == null || auth.accessToken().token().isBlank()) {
      throw new IllegalStateException("CloudNet REST /auth hat kein Access-Token geliefert.");
    }

    this.accessToken = auth.accessToken().token();
    this.accessTokenExpiresAt = Instant.now().plusMillis(Math.max(auth.accessToken().expiresIn(), 60_000L));
    this.lastError = null;
  }

  private synchronized void bootstrapLogLines(RestConfiguration configuration) throws IOException, InterruptedException {
    var request = HttpRequest.newBuilder(configuration.logLinesUri())
      .header("Authorization", "Bearer " + this.accessToken)
      .header("Accept", "application/json")
      .timeout(Duration.ofSeconds(10))
      .GET()
      .build();
    var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() == 401 || response.statusCode() == 403) {
      this.accessToken = null;
      this.accessTokenExpiresAt = Instant.EPOCH;
      throw new IllegalStateException("CloudNet REST lehnt den Zugriff auf /node/logLines ab.");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("HTTP " + response.statusCode() + ": " + this.responseErrorMessage(response.body()));
    }

    var lines = DocumentFactory.json().parse(response.body()).toInstanceOf(LogLinesResponse.class);
    this.bufferedLines.clear();
    for (var line : lines == null || lines.lines() == null ? List.<String>of() : lines.lines()) {
      this.pushLine(line);
    }
    this.lastError = null;
  }

  private synchronized void connectWebSocket(RestConfiguration configuration) {
    this.webSocketConnecting = true;
    this.httpClient.newWebSocketBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .header("Authorization", "Bearer " + this.accessToken)
      .buildAsync(configuration.liveConsoleUri(), new LiveConsoleListener(configuration))
      .whenComplete((socket, throwable) -> {
        synchronized (CloudNetRestConsoleClient.this) {
          CloudNetRestConsoleClient.this.webSocketConnecting = false;
          if (throwable != null) {
            CloudNetRestConsoleClient.this.webSocket = null;
            CloudNetRestConsoleClient.this.accessToken = null;
            CloudNetRestConsoleClient.this.accessTokenExpiresAt = Instant.EPOCH;
            CloudNetRestConsoleClient.this.lastError = "CloudNet Live-Konsole konnte nicht verbunden werden: "
              + CloudNetRestConsoleClient.this.exceptionMessage(throwable);
            return;
          }
          CloudNetRestConsoleClient.this.webSocket = socket;
          CloudNetRestConsoleClient.this.source = CloudNetRestConsoleClient.this.sourceOrDefault(configuration);
          CloudNetRestConsoleClient.this.lastError = null;
        }
      });
  }

  private synchronized void appendConsoleText(String text) {
    if (text == null || text.isBlank()) {
      return;
    }

    var normalized = text.replace("\r\n", "\n").replace('\r', '\n');
    for (var line : normalized.split("\n")) {
      this.pushLine(line);
    }
    this.lastError = null;
  }

  private synchronized void pushLine(String line) {
    if (line == null) {
      return;
    }
    this.bufferedLines.addLast(line);
    while (this.bufferedLines.size() > this.maxBufferedLines) {
      this.bufferedLines.removeFirst();
    }
  }

  private synchronized List<String> latestLines(int requestedLimit) {
    var effectiveLimit = Math.max(25, Math.min(this.maxBufferedLines, requestedLimit));
    if (this.bufferedLines.isEmpty()) {
      return List.of();
    }

    var skip = Math.max(0, this.bufferedLines.size() - effectiveLimit);
    var result = new ArrayList<String>(effectiveLimit);
    int index = 0;
    for (var line : this.bufferedLines) {
      if (index++ >= skip) {
        result.add(line);
      }
    }
    return List.copyOf(result);
  }

  private synchronized void handleSocketClosed(RestConfiguration configuration, String reason) {
    this.webSocket = null;
    this.accessToken = null;
    this.accessTokenExpiresAt = Instant.EPOCH;
    this.lastError = reason;
    this.source = this.sourceOrDefault(configuration);
  }

  private synchronized void resetState() {
    if (this.webSocket != null) {
      try {
        this.webSocket.abort();
      } catch (Exception ignored) {
      }
    }
    this.webSocket = null;
    this.accessToken = null;
    this.accessTokenExpiresAt = Instant.EPOCH;
    this.webSocketConnecting = false;
    this.partialTextFrame.setLength(0);
    this.bufferedLines.clear();
    this.lastError = null;
    this.source = null;
  }

  private String basicAuthorization(RestConfiguration configuration) {
    var value = configuration.username() + ":" + configuration.password();
    return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private String sourceOrDefault(RestConfiguration configuration) {
    return "cloudnet-rest:" + configuration.apiBaseUrl();
  }

  private static boolean hasAnyRestConfiguration(PanelSettings settings) {
    return hasText(settings.cloudNetRestBaseUrl())
      || hasText(settings.cloudNetRestUsername())
      || hasText(settings.cloudNetRestPassword());
  }

  private static RestConfiguration currentConfiguration(PanelSettings settings) {
    var baseUrl = normalizeBaseUrl(settings.cloudNetRestBaseUrl());
    var username = text(settings.cloudNetRestUsername());
    var password = text(settings.cloudNetRestPassword());
    if (baseUrl == null || username == null || password == null) {
      return null;
    }
    return new RestConfiguration(baseUrl, username, password, normalizeThreshold(settings.cloudNetRestThreshold()));
  }

  private static String normalizeBaseUrl(String value) {
    if (!hasText(value)) {
      return null;
    }
    var base = value.trim().replaceAll("/+$", "");
    if (base.endsWith("/api/v3")) {
      return base;
    }
    if (base.endsWith("/api")) {
      return base + "/v3";
    }
    return base + "/api/v3";
  }

  private static String normalizeThreshold(String value) {
    var normalized = text(value);
    if (normalized == null) {
      return "INFO";
    }
    return switch (normalized.toUpperCase()) {
      case "ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF" -> normalized.toUpperCase();
      default -> "INFO";
    };
  }

  private static String text(String value) {
    return hasText(value) ? value.trim() : null;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String responseErrorMessage(String body) {
    try {
      var document = DocumentFactory.json().parse(body == null || body.isBlank() ? "{}" : body);
      var detail = document.getString("detail");
      if (hasText(detail)) {
        return detail.trim();
      }
      var error = document.getString("error");
      if (hasText(error)) {
        return error.trim();
      }
    } catch (Exception ignored) {
    }
    return body == null || body.isBlank() ? "Leere Antwort" : body;
  }

  private String exceptionMessage(Throwable throwable) {
    var cause = throwable;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage() == null || cause.getMessage().isBlank()
      ? cause.getClass().getSimpleName()
      : cause.getMessage();
  }

  public record ConsoleResult(
    boolean handled,
    List<String> lines,
    String source
  ) {
  }

  private record RestConfiguration(
    String apiBaseUrl,
    String username,
    String password,
    String threshold
  ) {

    private URI authUri() {
      return URI.create(this.apiBaseUrl + "/auth");
    }

    private URI logLinesUri() {
      return URI.create(this.apiBaseUrl + "/node/logLines?format=raw");
    }

    private URI liveConsoleUri() {
      var apiUri = URI.create(this.apiBaseUrl);
      var scheme = "https".equalsIgnoreCase(apiUri.getScheme()) ? "wss" : "ws";
      var query = "threshold=" + URLEncoder.encode(this.threshold, StandardCharsets.UTF_8);
      return URI.create(scheme + "://" + apiUri.getAuthority() + apiUri.getPath() + "/node/liveConsole?" + query);
    }
  }

  private record JwtAuthResponse(
    Set<String> scopes,
    long creationTime,
    JwtToken accessToken,
    JwtToken refreshToken
  ) {
  }

  private record JwtToken(
    String token,
    String tokenType,
    long expiresIn
  ) {
  }

  private record LogLinesResponse(
    List<String> lines
  ) {
  }

  private final class LiveConsoleListener implements WebSocket.Listener {

    private final RestConfiguration configuration;

    private LiveConsoleListener(RestConfiguration configuration) {
      this.configuration = configuration;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      synchronized (CloudNetRestConsoleClient.this) {
        CloudNetRestConsoleClient.this.webSocket = webSocket;
        CloudNetRestConsoleClient.this.source = CloudNetRestConsoleClient.this.sourceOrDefault(this.configuration);
        CloudNetRestConsoleClient.this.lastError = null;
      }
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      synchronized (CloudNetRestConsoleClient.this) {
        CloudNetRestConsoleClient.this.partialTextFrame.append(data);
        if (last) {
          CloudNetRestConsoleClient.this.appendConsoleText(CloudNetRestConsoleClient.this.partialTextFrame.toString());
          CloudNetRestConsoleClient.this.partialTextFrame.setLength(0);
        }
      }
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      synchronized (CloudNetRestConsoleClient.this) {
        CloudNetRestConsoleClient.this.handleSocketClosed(
          this.configuration,
          "CloudNet Live-Konsole wurde getrennt: " + statusCode + " " + (reason == null ? "" : reason));
      }
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      synchronized (CloudNetRestConsoleClient.this) {
        CloudNetRestConsoleClient.this.handleSocketClosed(
          this.configuration,
          "CloudNet Live-Konsole Fehler: " + CloudNetRestConsoleClient.this.exceptionMessage(error));
      }
    }
  }
}
