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

public final class OneDriveEvidenceStorage implements EvidenceStorage {

  private final PanelConfiguration configuration;
  private final HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

  public OneDriveEvidenceStorage(PanelConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public StoredEvidence store(String appealId, AppealMultipartForm.UploadFile file) {
    if (this.configuration.appealEvidenceOneDriveUploadUrlTemplate().isBlank()
      || this.configuration.appealEvidenceOneDriveBearerToken().isBlank()) {
      throw new IllegalStateException("OneDrive-Speicher ist nicht vollstaendig konfiguriert.");
    }

    var storedName = safeName(appealId) + "/" + UUID.randomUUID() + "-" + safeName(file.fileName());
    var uploadUrl = this.configuration.appealEvidenceOneDriveUploadUrlTemplate()
      .replace("{filename}", URLEncoder.encode(storedName, StandardCharsets.UTF_8));

    try {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(uploadUrl))
        .timeout(Duration.ofSeconds(30))
        .header("Authorization", "Bearer " + this.configuration.appealEvidenceOneDriveBearerToken())
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
}
