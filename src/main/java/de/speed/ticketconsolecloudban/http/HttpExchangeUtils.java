package de.speed.ticketconsolecloudban.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import eu.cloudnetservice.driver.document.Document;
import eu.cloudnetservice.driver.document.DocumentFactory;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class HttpExchangeUtils {

  private HttpExchangeUtils() {
  }

  public static Document readJson(HttpExchange exchange) throws IOException {
    var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (body.isBlank()) {
      return DocumentFactory.json().newDocument();
    }
    return DocumentFactory.json().parse(body);
  }

  public static void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
    var bytes = JsonBodyWriter.toJson(payload).getBytes(StandardCharsets.UTF_8);

    var headers = exchange.getResponseHeaders();
    headers.set("Content-Type", "application/json; charset=utf-8");
    headers.set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  public static void writeText(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(statusCode, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  public static void writeBinary(HttpExchange exchange, int statusCode, byte[] body, String contentType) throws IOException {
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(statusCode, body.length);
    try (var output = exchange.getResponseBody()) {
      output.write(body);
    }
  }

  public static void sendNoContent(HttpExchange exchange) throws IOException {
    exchange.sendResponseHeaders(204, -1);
    exchange.close();
  }

  public static List<String> pathSegments(HttpExchange exchange) {
    var path = exchange.getRequestURI().getPath();
    var segments = new ArrayList<String>();
    for (var segment : path.split("/")) {
      if (!segment.isBlank()) {
        segments.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
      }
    }
    return segments;
  }

  public static boolean matchesMethod(HttpExchange exchange, String method) {
    return exchange.getRequestMethod().equalsIgnoreCase(method);
  }

  public static String bearerToken(HttpExchange exchange) {
    var authorization = exchange.getRequestHeaders().getFirst("Authorization");
    if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authorization.substring(7).trim();
    }

    var headerToken = exchange.getRequestHeaders().getFirst("X-Api-Token");
    if (headerToken != null && !headerToken.isBlank()) {
      return headerToken.trim();
    }

    return null;
  }

  public static void allowCommonHeaders(Headers headers) {
    headers.set("Access-Control-Allow-Origin", "*");
    headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Api-Token");
    headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  }

  public record ApiError(String error) {
  }
}
