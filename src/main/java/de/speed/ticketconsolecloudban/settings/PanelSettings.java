package de.speed.ticketconsolecloudban.settings;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;

public record PanelSettings(
  String brandName,
  String brandLogoUrl,
  boolean smtpEnabled,
  String smtpHost,
  int smtpPort,
  String smtpUsername,
  String smtpPassword,
  String smtpFrom,
  boolean smtpStartTls,
  boolean smtpSsl,
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

  public static PanelSettings fromConfiguration(PanelConfiguration configuration) {
    return new PanelSettings(
      configuration.brandName(),
      configuration.brandLogoUrl(),
      configuration.smtpEnabled(),
      configuration.smtpHost(),
      configuration.smtpPort(),
      configuration.smtpUsername(),
      configuration.smtpPassword(),
      configuration.smtpFrom(),
      configuration.smtpStartTls(),
      configuration.smtpSsl(),
      configuration.liteBansDatabaseEnabled(),
      configuration.liteBansJdbcUrl(),
      configuration.liteBansDatabaseUsername(),
      configuration.liteBansDatabasePassword(),
      configuration.liteBansTablePrefix(),
      configuration.liteBansDatabaseMaxRows(),
      configuration.liteBansBridgeBaseUrl(),
      configuration.liteBansBridgeSecret(),
      configuration.liteBansBridgeConnectTimeoutMillis(),
      configuration.liteBansBridgeReadTimeoutMillis());
  }
}
