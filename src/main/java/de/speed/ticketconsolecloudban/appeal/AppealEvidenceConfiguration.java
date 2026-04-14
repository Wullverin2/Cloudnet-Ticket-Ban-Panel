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
  String oneDriveBearerToken,
  String oneDriveTenant,
  String oneDriveClientId,
  String oneDriveFolderPath,
  String oneDriveRefreshToken
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
    oneDriveTenant = OneDriveOAuthClient.normalizeTenant(oneDriveTenant);
    oneDriveClientId = blankIfNull(oneDriveClientId);
    oneDriveFolderPath = OneDriveOAuthClient.normalizeFolderPath(oneDriveFolderPath);
    oneDriveRefreshToken = blankIfNull(oneDriveRefreshToken);
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
      configuration.appealEvidenceOneDriveBearerToken(),
      OneDriveOAuthClient.DEFAULT_TENANT,
      "",
      OneDriveOAuthClient.DEFAULT_FOLDER_PATH,
      "");
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
      settings.appealEvidenceOneDriveBearerToken(),
      settings.appealEvidenceOneDriveTenant(),
      settings.appealEvidenceOneDriveClientId(),
      settings.appealEvidenceOneDriveFolderPath(),
      settings.appealEvidenceOneDriveRefreshToken());
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
