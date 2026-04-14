package de.speed.ticketconsolecloudban.appeal;

import eu.cloudnetservice.driver.document.Document;
import eu.cloudnetservice.driver.document.DocumentFactory;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class OneDriveOAuthClient {

  public static final String DEFAULT_TENANT = "common";
  public static final String DEFAULT_FOLDER_PATH = "Entbannungsantraege";
  private static final String SCOPE = "offline_access Files.ReadWrite";
  private static final String DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

  private final HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

  public DeviceCodeView startDeviceCode(String tenant, String clientId) {
    var response = this.postForm(
      endpoint(tenant, "devicecode"),
      Map.of(
        "client_id", required(clientId, "OneDrive Client ID"),
        "scope", SCOPE));
    return new DeviceCodeView(
      "PENDING",
      response.getString("device_code"),
      response.getString("user_code"),
      firstNonBlank(response.getString("verification_uri"), response.getString("verification_url")),
      response.getString("verification_uri_complete"),
      response.getString("message"),
      Math.max(60, response.getInt("expires_in", 900)),
      Math.max(5, response.getInt("interval", 5)));
  }

  public DeviceCodeTokenResult completeDeviceCode(String tenant, String clientId, String deviceCode) {
    var response = this.postForm(
      endpoint(tenant, "token"),
      Map.of(
        "grant_type", DEVICE_CODE_GRANT,
        "client_id", required(clientId, "OneDrive Client ID"),
        "device_code", required(deviceCode, "OneDrive Device Code")));
    var error = response.getString("error");
    if (error != null && !error.isBlank()) {
      if ("authorization_pending".equalsIgnoreCase(error) || "slow_down".equalsIgnoreCase(error)) {
        return new DeviceCodeTokenResult(
          "PENDING",
          "Microsoft-Anmeldung ist noch nicht abgeschlossen.",
          "",
          "",
          "slow_down".equalsIgnoreCase(error) ? 10 : 5);
      }
      throw new IllegalArgumentException("OneDrive-Anmeldung fehlgeschlagen: " + firstNonBlank(response.getString("error_description"), error));
    }
    return new DeviceCodeTokenResult(
      "CONNECTED",
      "OneDrive wurde erfolgreich verbunden.",
      response.getString("access_token"),
      response.getString("refresh_token"),
      0);
  }

  public AccessTokenView refreshAccessToken(String tenant, String clientId, String refreshToken) {
    var response = this.postForm(
      endpoint(tenant, "token"),
      Map.of(
        "grant_type", "refresh_token",
        "client_id", required(clientId, "OneDrive Client ID"),
        "refresh_token", required(refreshToken, "OneDrive Refresh Token"),
        "scope", SCOPE));
    var error = response.getString("error");
    if (error != null && !error.isBlank()) {
      throw new IllegalArgumentException("OneDrive Token konnte nicht erneuert werden: " + firstNonBlank(response.getString("error_description"), error));
    }
    return new AccessTokenView(
      required(response.getString("access_token"), "OneDrive Access Token"),
      firstNonBlank(response.getString("refresh_token"), refreshToken));
  }

  public static String graphContentUrl(String folderPath, String storedName) {
    return "https://graph.microsoft.com/v1.0/me/drive/root:/"
      + encodePath(joinPath(folderPath, storedName))
      + ":/content";
  }

  public static String normalizeTenant(String value) {
    return value == null || value.isBlank() ? DEFAULT_TENANT : value.trim();
  }

  public static String normalizeFolderPath(String value) {
    var normalized = value == null || value.isBlank() ? DEFAULT_FOLDER_PATH : value.trim();
    return normalized.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
  }

  private Document postForm(String uri, Map<String, String> values) {
    try {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(uri))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(form(values)))
        .build();
      var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      var body = response.body() == null || response.body().isBlank()
        ? DocumentFactory.json().newDocument()
        : DocumentFactory.json().parse(response.body());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return body;
      }
      if (body.contains("error")) {
        return body;
      }
      throw new IllegalArgumentException("OneDrive HTTP " + response.statusCode() + ": " + response.body());
    } catch (IOException exception) {
      throw new IllegalArgumentException("OneDrive ist nicht erreichbar: " + exception.getMessage(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("OneDrive-Anfrage wurde unterbrochen.", exception);
    }
  }

  private static String endpoint(String tenant, String action) {
    return "https://login.microsoftonline.com/"
      + encodePathSegment(normalizeTenant(tenant))
      + "/oauth2/v2.0/"
      + action;
  }

  private static String form(Map<String, String> values) {
    var ordered = new LinkedHashMap<>(values);
    return ordered.entrySet().stream()
      .map(entry -> encodeForm(entry.getKey()) + "=" + encodeForm(entry.getValue()))
      .collect(Collectors.joining("&"));
  }

  private static String joinPath(String first, String second) {
    var folder = normalizeFolderPath(first);
    var file = second == null ? "" : second.replace('\\', '/').replaceAll("^/+", "");
    return folder.isBlank() ? file : folder + "/" + file;
  }

  private static String encodePath(String path) {
    return java.util.Arrays.stream(path.split("/"))
      .filter(part -> !part.isBlank())
      .map(OneDriveOAuthClient::encodePathSegment)
      .collect(Collectors.joining("/"));
  }

  private static String encodePathSegment(String value) {
    return encodeForm(value).replace("+", "%20");
  }

  private static String encodeForm(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " ist erforderlich.");
    }
    return value.trim();
  }

  private static String firstNonBlank(String first, String second) {
    return first == null || first.isBlank() ? second == null ? "" : second : first;
  }

  public record DeviceCodeView(
    String status,
    String deviceCode,
    String userCode,
    String verificationUri,
    String verificationUriComplete,
    String message,
    int expiresIn,
    int interval
  ) {
  }

  public record DeviceCodeTokenResult(
    String status,
    String message,
    String accessToken,
    String refreshToken,
    int interval
  ) {
  }

  public record AccessTokenView(
    String accessToken,
    String refreshToken
  ) {
  }
}
