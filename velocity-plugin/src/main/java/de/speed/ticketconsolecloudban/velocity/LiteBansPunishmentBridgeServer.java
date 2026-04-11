package de.speed.ticketconsolecloudban.velocity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;

final class LiteBansPunishmentBridgeServer {

  private final Logger logger;
  private final VelocityPluginConfig config;
  private final LiteBansRandomIdResolver randomIdResolver;
  private HttpServer server;
  private ExecutorService executor;

  LiteBansPunishmentBridgeServer(Logger logger, VelocityPluginConfig config, LiteBansRandomIdResolver randomIdResolver) {
    this.logger = logger;
    this.config = config;
    this.randomIdResolver = randomIdResolver;
  }

  void start() {
    if (!this.config.liteBansBridgeEnabled()) {
      return;
    }
    if (this.config.effectiveLiteBansBridgeSecret() == null
      || this.config.effectiveLiteBansBridgeSecret().isBlank()
      || "CHANGE_ME".equalsIgnoreCase(this.config.effectiveLiteBansBridgeSecret())) {
      this.logger.warn("LiteBans Punishment-ID-Bridge wurde nicht gestartet: panel.api-token oder litebans.bridge-secret ist nicht gesetzt.");
      return;
    }

    try {
      this.server = HttpServer.create(new InetSocketAddress(this.config.liteBansBridgeBindHost(), this.config.liteBansBridgePort()), 0);
      this.executor = Executors.newVirtualThreadPerTaskExecutor();
      this.server.setExecutor(this.executor);
      this.server.createContext("/", this::handle);
      this.server.start();
      this.logger.info(
        "LiteBans Punishment-ID-Bridge gestartet auf http://{}:{}/",
        this.config.liteBansBridgeBindHost(),
        this.config.liteBansBridgePort());
    } catch (IOException exception) {
      this.logger.warn("LiteBans Punishment-ID-Bridge konnte nicht gestartet werden: {}", exception.getMessage());
    }
  }

  void stop() {
    if (this.server != null) {
      this.server.stop(0);
      this.server = null;
    }
    if (this.executor != null) {
      this.executor.shutdownNow();
      this.executor = null;
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    try {
      if ("/".equals(exchange.getRequestURI().getPath())) {
        this.writeJson(exchange, 200, "{\"success\":true,\"message\":\"bridge_online\"}");
        return;
      }

      if ("/api/punishment-id/from-db".equals(exchange.getRequestURI().getPath())) {
        this.handleFromDatabaseId(exchange);
        return;
      }

      this.writeJson(exchange, 404, "{\"success\":false,\"error\":\"not_found\"}");
    } catch (Exception exception) {
      this.logger.warn("LiteBans Punishment-ID-Bridge Fehler: {}", exception.getMessage());
      this.writeJson(exchange, 500, "{\"success\":false,\"error\":\"internal_error\"}");
    }
  }

  private void handleFromDatabaseId(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      this.writeJson(exchange, 405, "{\"success\":false,\"error\":\"method_not_allowed\"}");
      return;
    }
    if (!this.authorized(exchange)) {
      this.writeJson(exchange, 403, "{\"success\":false,\"error\":\"forbidden\"}");
      return;
    }

    var parameters = queryParameters(exchange.getRequestURI());
    var databaseIdRaw = parameters.get("dbId");
    if (databaseIdRaw == null || databaseIdRaw.isBlank()) {
      this.writeJson(exchange, 400, "{\"success\":false,\"error\":\"missing_dbId\"}");
      return;
    }

    try {
      var databaseId = Long.parseLong(databaseIdRaw.trim());
      var randomId = this.randomIdResolver.fromDatabaseId(
        databaseId,
        parameters.get("playerUuid"),
        parameters.get("serverScope"),
        parameters.get("serverOrigin"));
      if (randomId.isPresent()) {
        this.writeJson(exchange, 200, "{\"success\":true,\"value\":\"" + escapeJson(randomId.get()) + "\"}");
      } else {
        this.writeJson(exchange, 404, "{\"success\":false,\"error\":\"not_found\"}");
      }
    } catch (NumberFormatException exception) {
      this.writeJson(exchange, 400, "{\"success\":false,\"error\":\"invalid_dbId\"}");
    }
  }

  private boolean authorized(HttpExchange exchange) {
    var provided = exchange.getRequestHeaders().getFirst("X-Bridge-Secret");
    return provided != null && provided.equals(this.config.effectiveLiteBansBridgeSecret());
  }

  private static Map<String, String> queryParameters(URI uri) {
    var parameters = new LinkedHashMap<String, String>();
    var query = uri.getRawQuery();
    if (query == null || query.isBlank()) {
      return parameters;
    }

    for (var part : query.split("&")) {
      if (part.isBlank()) {
        continue;
      }
      var separator = part.indexOf('=');
      var key = separator >= 0 ? part.substring(0, separator) : part;
      var value = separator >= 0 ? part.substring(separator + 1) : "";
      parameters.put(
        URLDecoder.decode(key, StandardCharsets.UTF_8),
        URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return parameters;
  }

  private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
    var bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static String escapeJson(String value) {
    return value == null ? "" : value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\r", "\\r")
      .replace("\n", "\\n")
      .replace("\t", "\\t");
  }
}
