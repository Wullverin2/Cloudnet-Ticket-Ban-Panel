package de.speed.ticketconsolecloudban.appeal;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettings;

public record AppealEvidenceConfiguration(
  String storage,
  String localDirectory,
  String sftpHost,
  int sftpPort,
  String sftpUsername,
  String sftpPassword,
  String sftpPrivateKeyPath,
  String sftpRemoteDirectory,
  String oneDriveUploadUrlTemplate,
  String oneDriveBearerToken
) {

  public AppealEvidenceConfiguration {
    storage = normalizeStorage(storage);
    localDirectory = defaultIfBlank(localDirectory, "appeal-evidence");
    sftpHost = blankIfNull(sftpHost);
    sftpPort = sftpPort > 0 && sftpPort <= 65535 ? sftpPort : 22;
    sftpUsername = blankIfNull(sftpUsername);
    sftpPassword = blankIfNull(sftpPassword);
    sftpPrivateKeyPath = blankIfNull(sftpPrivateKeyPath);
    sftpRemoteDirectory = defaultIfBlank(sftpRemoteDirectory, "/appeals");
    oneDriveUploadUrlTemplate = blankIfNull(oneDriveUploadUrlTemplate);
    oneDriveBearerToken = blankIfNull(oneDriveBearerToken);
  }

  public static AppealEvidenceConfiguration from(PanelConfiguration configuration) {
    return new AppealEvidenceConfiguration(
      configuration.appealEvidenceStorage(),
      configuration.appealEvidenceLocalDirectory(),
      configuration.appealEvidenceSftpHost(),
      configuration.appealEvidenceSftpPort(),
      configuration.appealEvidenceSftpUsername(),
      configuration.appealEvidenceSftpPassword(),
      configuration.appealEvidenceSftpPrivateKeyPath(),
      configuration.appealEvidenceSftpRemoteDirectory(),
      configuration.appealEvidenceOneDriveUploadUrlTemplate(),
      configuration.appealEvidenceOneDriveBearerToken());
  }

  public static AppealEvidenceConfiguration from(PanelConfiguration configuration, PanelSettings settings) {
    if (settings == null) {
      return from(configuration);
    }
    return new AppealEvidenceConfiguration(
      settings.appealEvidenceStorage(),
      settings.appealEvidenceLocalDirectory(),
      settings.appealEvidenceSftpHost(),
      settings.appealEvidenceSftpPort(),
      settings.appealEvidenceSftpUsername(),
      settings.appealEvidenceSftpPassword(),
      settings.appealEvidenceSftpPrivateKeyPath(),
      settings.appealEvidenceSftpRemoteDirectory(),
      settings.appealEvidenceOneDriveUploadUrlTemplate(),
      settings.appealEvidenceOneDriveBearerToken());
  }

  private static String normalizeStorage(String value) {
    var normalized = defaultIfBlank(value, "LOCAL").trim().toUpperCase();
    return switch (normalized) {
      case "SFTP", "ONEDRIVE" -> normalized;
      default -> "LOCAL";
    };
  }

  private static String defaultIfBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String blankIfNull(String value) {
    return value == null ? "" : value.trim();
  }
}
