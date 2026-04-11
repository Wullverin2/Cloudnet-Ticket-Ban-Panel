package de.speed.ticketconsolecloudban.config;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public record PanelConfiguration(
  String bindHost,
  int bindPort,
  int consoleLineLimit,
  String brandName,
  List<String> apiTokens,
  String publicBaseUrl,
  boolean smtpEnabled,
  String smtpHost,
  int smtpPort,
  String smtpUsername,
  String smtpPassword,
  String smtpFrom,
  boolean smtpStartTls,
  boolean smtpSsl,
  int passwordResetTokenMinutes,
  boolean appealEnabled,
  String appealBindHost,
  int appealBindPort,
  String appealPublicBaseUrl,
  int appealMaxFiles,
  long appealMaxFileBytes,
  String appealEvidenceStorage,
  String appealEvidenceLocalDirectory,
  String appealEvidenceSftpHost,
  int appealEvidenceSftpPort,
  String appealEvidenceSftpUsername,
  String appealEvidenceSftpPassword,
  String appealEvidenceSftpPrivateKeyPath,
  String appealEvidenceSftpRemoteDirectory,
  String appealEvidenceOneDriveUploadUrlTemplate,
  String appealEvidenceOneDriveBearerToken,
  boolean liteBansDatabaseEnabled,
  String liteBansJdbcUrl,
  String liteBansDatabaseUsername,
  String liteBansDatabasePassword,
  String liteBansTablePrefix,
  int liteBansDatabaseMaxRows,
  String liteBansBridgeBaseUrl,
  String liteBansBridgeSecret,
  int liteBansBridgeConnectTimeoutMillis,
  int liteBansBridgeReadTimeoutMillis
) {

  private static final SecureRandom RANDOM = new SecureRandom();

  public static PanelConfiguration createDefault() {
    return new PanelConfiguration(
      "0.0.0.0",
      8088,
      250,
      "Network Control",
      List.of(generateToken()),
      "http://127.0.0.1:8088",
      false,
      "127.0.0.1",
      587,
      "",
      "",
      "panel@example.com",
      true,
      false,
      30,
      true,
      "0.0.0.0",
      8090,
      "http://127.0.0.1:8090",
      3,
      10L * 1024L * 1024L,
      "LOCAL",
      "appeal-evidence",
      "",
      22,
      "",
      "",
      "",
      "/appeals",
      "",
      "",
      false,
      "jdbc:mysql://127.0.0.1:3306/litebans?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
      "litebans",
      "",
      "litebans_",
      1000,
      "",
      "",
      2500,
      5000);
  }

  public PanelConfiguration normalize() {
    var normalizedHost = this.bindHost == null || this.bindHost.isBlank() ? "0.0.0.0" : this.bindHost.trim();
    var normalizedPort = this.bindPort > 0 && this.bindPort <= 0xFFFF ? this.bindPort : 8088;
    var normalizedLimit = clamp(this.consoleLineLimit, 50, 1000);
    var normalizedBrand = this.brandName == null || this.brandName.isBlank() ? "Network Control" : this.brandName.trim();
    var normalizedBaseUrl = this.publicBaseUrl == null || this.publicBaseUrl.isBlank()
      ? "http://127.0.0.1:" + normalizedPort
      : this.publicBaseUrl.trim().replaceAll("/+$", "");
    var normalizedSmtpHost = this.smtpHost == null || this.smtpHost.isBlank() ? "127.0.0.1" : this.smtpHost.trim();
    var normalizedSmtpPort = this.smtpPort > 0 && this.smtpPort <= 0xFFFF ? this.smtpPort : 587;
    var normalizedSmtpFrom = this.smtpFrom == null || this.smtpFrom.isBlank() ? "panel@example.com" : this.smtpFrom.trim();
    var normalizedResetMinutes = clamp(this.passwordResetTokenMinutes, 5, 240);
    var normalizedAppealHost = this.appealBindHost == null || this.appealBindHost.isBlank() ? "0.0.0.0" : this.appealBindHost.trim();
    var normalizedAppealPort = this.appealBindPort > 0 && this.appealBindPort <= 0xFFFF ? this.appealBindPort : 8090;
    var normalizedAppealBaseUrl = this.appealPublicBaseUrl == null || this.appealPublicBaseUrl.isBlank()
      ? "http://127.0.0.1:" + normalizedAppealPort
      : this.appealPublicBaseUrl.trim().replaceAll("/+$", "");
    var normalizedMaxFiles = clamp(this.appealMaxFiles, 0, 10);
    var normalizedMaxFileBytes = this.appealMaxFileBytes <= 0 ? 10L * 1024L * 1024L : Math.min(this.appealMaxFileBytes, 100L * 1024L * 1024L);
    var normalizedEvidenceStorage = this.appealEvidenceStorage == null || this.appealEvidenceStorage.isBlank()
      ? "LOCAL"
      : this.appealEvidenceStorage.trim().toUpperCase();
    var normalizedLocalDirectory = this.appealEvidenceLocalDirectory == null || this.appealEvidenceLocalDirectory.isBlank()
      ? "appeal-evidence"
      : this.appealEvidenceLocalDirectory.trim();
    var normalizedSftpPort = this.appealEvidenceSftpPort > 0 && this.appealEvidenceSftpPort <= 0xFFFF ? this.appealEvidenceSftpPort : 22;
    var normalizedLiteBansJdbcUrl = this.liteBansJdbcUrl == null || this.liteBansJdbcUrl.isBlank()
      ? "jdbc:mysql://127.0.0.1:3306/litebans?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
      : this.liteBansJdbcUrl.trim();
    var normalizedLiteBansTablePrefix = this.liteBansTablePrefix == null ? "litebans_" : this.liteBansTablePrefix.trim();
    if (!normalizedLiteBansTablePrefix.matches("[A-Za-z0-9_]*")) {
      normalizedLiteBansTablePrefix = "litebans_";
    }
    var normalizedLiteBansMaxRows = this.liteBansDatabaseMaxRows <= 0 ? 1000 : clamp(this.liteBansDatabaseMaxRows, 50, 10_000);
    var normalizedLiteBansBridgeBaseUrl = this.liteBansBridgeBaseUrl == null || this.liteBansBridgeBaseUrl.isBlank()
      ? ""
      : this.liteBansBridgeBaseUrl.trim().replaceAll("/+$", "");
    var normalizedLiteBansBridgeConnectTimeout = this.liteBansBridgeConnectTimeoutMillis <= 0
      ? 2500
      : clamp(this.liteBansBridgeConnectTimeoutMillis, 500, 30_000);
    var normalizedLiteBansBridgeReadTimeout = this.liteBansBridgeReadTimeoutMillis <= 0
      ? 5000
      : clamp(this.liteBansBridgeReadTimeoutMillis, 500, 30_000);

    var normalizedTokens = new ArrayList<String>();
    if (this.apiTokens != null) {
      for (var token : this.apiTokens) {
        if (token != null && !token.isBlank()) {
          normalizedTokens.add(token.trim());
        }
      }
    }

    if (normalizedTokens.isEmpty()) {
      normalizedTokens.add(generateToken());
    }

    return new PanelConfiguration(
      normalizedHost,
      normalizedPort,
      normalizedLimit,
      normalizedBrand,
      List.copyOf(normalizedTokens),
      normalizedBaseUrl,
      this.smtpEnabled,
      normalizedSmtpHost,
      normalizedSmtpPort,
      this.smtpUsername == null ? "" : this.smtpUsername.trim(),
      this.smtpPassword == null ? "" : this.smtpPassword,
      normalizedSmtpFrom,
      this.smtpStartTls,
      this.smtpSsl,
      normalizedResetMinutes,
      this.appealEnabled,
      normalizedAppealHost,
      normalizedAppealPort,
      normalizedAppealBaseUrl,
      normalizedMaxFiles,
      normalizedMaxFileBytes,
      normalizedEvidenceStorage,
      normalizedLocalDirectory,
      this.appealEvidenceSftpHost == null ? "" : this.appealEvidenceSftpHost.trim(),
      normalizedSftpPort,
      this.appealEvidenceSftpUsername == null ? "" : this.appealEvidenceSftpUsername.trim(),
      this.appealEvidenceSftpPassword == null ? "" : this.appealEvidenceSftpPassword,
      this.appealEvidenceSftpPrivateKeyPath == null ? "" : this.appealEvidenceSftpPrivateKeyPath.trim(),
      this.appealEvidenceSftpRemoteDirectory == null || this.appealEvidenceSftpRemoteDirectory.isBlank()
        ? "/appeals"
        : this.appealEvidenceSftpRemoteDirectory.trim(),
      this.appealEvidenceOneDriveUploadUrlTemplate == null ? "" : this.appealEvidenceOneDriveUploadUrlTemplate.trim(),
      this.appealEvidenceOneDriveBearerToken == null ? "" : this.appealEvidenceOneDriveBearerToken,
      this.liteBansDatabaseEnabled,
      normalizedLiteBansJdbcUrl,
      this.liteBansDatabaseUsername == null ? "" : this.liteBansDatabaseUsername.trim(),
      this.liteBansDatabasePassword == null ? "" : this.liteBansDatabasePassword,
      normalizedLiteBansTablePrefix,
      normalizedLiteBansMaxRows,
      normalizedLiteBansBridgeBaseUrl,
      this.liteBansBridgeSecret == null ? "" : this.liteBansBridgeSecret,
      normalizedLiteBansBridgeConnectTimeout,
      normalizedLiteBansBridgeReadTimeout);
  }

  public String effectiveLiteBansBridgeSecret() {
    if (this.liteBansBridgeSecret != null && !this.liteBansBridgeSecret.isBlank()) {
      return this.liteBansBridgeSecret.trim();
    }

    return this.apiTokens == null || this.apiTokens.isEmpty() ? "" : this.apiTokens.get(0);
  }

  public boolean acceptsToken(String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return false;
    }

    for (var token : this.apiTokens) {
      if (token.equals(candidate)) {
        return true;
      }
    }

    return false;
  }

  public static String generateToken() {
    var bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
