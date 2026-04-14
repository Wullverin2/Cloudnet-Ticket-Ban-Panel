package de.speed.ticketconsolecloudban.appeal;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

public final class OneDriveEvidenceStorage implements EvidenceStorage {

  private final AppealEvidenceConfiguration configuration;
  private final Consumer<String> refreshTokenUpdater;
  private final OneDriveOAuthClient oauthClient = new OneDriveOAuthClient();
  private final HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

  public OneDriveEvidenceStorage(PanelConfiguration configuration) {
    this(AppealEvidenceConfiguration.from(configuration));
  }

  public OneDriveEvidenceStorage(AppealEvidenceConfiguration configuration) {
    this(configuration, token -> {
    });
  }

  public OneDriveEvidenceStorage(AppealEvidenceConfiguration configuration, Consumer<String> refreshTokenUpdater) {
    this.configuration = configuration;
    this.refreshTokenUpdater = refreshTokenUpdater;
  }

  @Override
  public StoredEvidence store(String appealId, AppealMultipartForm.UploadFile file) {
    var bearerToken = this.bearerToken();
    if (bearerToken.isBlank()) {
      throw new IllegalStateException("OneDrive-Speicher ist nicht vollstaendig konfiguriert.");
    }

    var storedName = safeName(appealId) + "/" + UUID.randomUUID() + "-" + safeName(file.fileName());
    var uploadUrl = this.uploadUrl(storedName);

    try {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(uploadUrl))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + bearerToken)
        .header("Content-Type", file.contentType() == null || file.contentType().isBlank() ? "application/octet-stream" : file.contentType())
        .PUT(HttpRequest.BodyPublishers.ofByteArray(file.content()))
        .build();
      var response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("OneDrive HTTP " + response.statusCode() + ": " + response.body());
      }
      return new StoredEvidence("ONEDRIVE", storedName);
    } catch (Exception exception) {
      throw new IllegalStateException("Beweisdatei konnte in OneDrive nicht gespeichert werden: " + exception.getMessage(), exception);
    }
  }

  private static String safeName(String value) {
    var safe = value == null || value.isBlank() ? "file" : value.trim();
    return safe.replaceAll("[^A-Za-z0-9._/-]", "_");
  }

  private String bearerToken() {
    if (!this.configuration.oneDriveRefreshToken().isBlank()) {
      var token = this.oauthClient.refreshAccessToken(
        this.configuration.oneDriveTenant(),
        this.configuration.oneDriveClientId(),
        this.configuration.oneDriveRefreshToken());
      if (!token.refreshToken().isBlank() && !token.refreshToken().equals(this.configuration.oneDriveRefreshToken())) {
        this.refreshTokenUpdater.accept(token.refreshToken());
      }
      return token.accessToken();
    }
    return this.configuration.oneDriveBearerToken();
  }

  private String uploadUrl(String storedName) {
    if (!this.configuration.oneDriveUploadUrlTemplate().isBlank()) {
      return this.configuration.oneDriveUploadUrlTemplate()
        .replace("{filename}", URLEncoder.encode(storedName, StandardCharsets.UTF_8));
    }
    return OneDriveOAuthClient.graphContentUrl(this.configuration.oneDriveFolderPath(), storedName);
  }
}
