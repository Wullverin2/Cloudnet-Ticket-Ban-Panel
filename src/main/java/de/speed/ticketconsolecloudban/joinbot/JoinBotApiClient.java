package de.speed.ticketconsolecloudban.joinbot;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public final class JoinBotApiClient {

  private final PanelConfiguration configuration;
  private final HttpClient httpClient;

  public JoinBotApiClient(PanelConfiguration configuration) {
    this.configuration = configuration;
    this.httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofMillis(configuration.joinBotConnectTimeoutMillis()))
      .build();
  }

  public ProxyResponse proxy(String method, List<String> joinBotSegments, String rawQuery, byte[] body) {
    if (!this.configuration.joinBotEnabled()) {
      return ProxyResponse.json(503, "{\"error\":\"JoinBot-Anbindung ist im Panel nicht aktiviert.\"}");
    }

    var apiKey = this.configuration.effectiveJoinBotApiKey();
    if (apiKey.isBlank()) {
      return ProxyResponse.json(503, "{\"error\":\"JoinBot API-Key ist im Panel nicht konfiguriert.\"}");
    }

    try {
      var requestBuilder = HttpRequest.newBuilder()
        .uri(this.targetUri(joinBotSegments, rawQuery))
        .version(HttpClient.Version.HTTP_1_1)
        .timeout(Duration.ofMillis(this.configuration.joinBotReadTimeoutMillis()))
        .header("X-JoinBot-Key", apiKey)
        .header("Accept", "application/json");

      if (body != null && body.length > 0 && allowsBody(method)) {
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.method(method.toUpperCase(), HttpRequest.BodyPublishers.ofByteArray(body));
      } else {
        requestBuilder.method(method.toUpperCase(), HttpRequest.BodyPublishers.noBody());
      }

      var response = this.httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      var contentType = response.headers().firstValue("Content-Type").orElse("application/json; charset=utf-8");
      return new ProxyResponse(response.statusCode(), contentType, response.body());
    } catch (IOException exception) {
      return ProxyResponse.json(502, "{\"error\":\"JoinBot-Service ist nicht erreichbar.\"}");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return ProxyResponse.json(502, "{\"error\":\"JoinBot-Anfrage wurde unterbrochen.\"}");
    } catch (IllegalArgumentException exception) {
      return ProxyResponse.json(400, "{\"error\":\"Ungueltiger JoinBot-Endpunkt.\"}");
    }
  }

  private URI targetUri(List<String> joinBotSegments, String rawQuery) {
    var base = this.configuration.joinBotBaseUrl().replaceAll("/+$", "");
    var path = new StringBuilder(base);
    for (var segment : joinBotSegments) {
      if (segment == null || segment.isBlank()) {
        continue;
      }
      path.append('/').append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
    }
    if (rawQuery != null && !rawQuery.isBlank()) {
      path.append('?').append(rawQuery);
    }
    return URI.create(path.toString());
  }

  private static boolean allowsBody(String method) {
    return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
  }

  public record ProxyResponse(
    int statusCode,
    String contentType,
    String body
  ) {

    static ProxyResponse json(int statusCode, String body) {
      return new ProxyResponse(statusCode, "application/json; charset=utf-8", body);
    }
  }
}
