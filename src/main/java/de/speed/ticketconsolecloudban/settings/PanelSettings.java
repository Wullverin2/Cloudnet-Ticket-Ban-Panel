package de.speed.ticketconsolecloudban.settings;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;

public record PanelSettings(
  String brandName,
  String brandLogoUrl,
  String cloudNetScreenName,
  String cloudNetRestBaseUrl,
  String cloudNetRestUsername,
  String cloudNetRestPassword,
  String cloudNetRestThreshold,
  boolean smtpEnabled,
  String smtpHost,
  int smtpPort,
  String smtpUsername,
  String smtpPassword,
  String smtpFrom,
  boolean smtpStartTls,
  boolean smtpSsl
) {

  public static PanelSettings fromConfiguration(PanelConfiguration configuration) {
    return new PanelSettings(
      configuration.brandName(),
      configuration.brandLogoUrl(),
      "",
      "",
      "",
      "",
      "INFO",
      configuration.smtpEnabled(),
      configuration.smtpHost(),
      configuration.smtpPort(),
      configuration.smtpUsername(),
      configuration.smtpPassword(),
      configuration.smtpFrom(),
      configuration.smtpStartTls(),
      configuration.smtpSsl());
  }
}
