package de.speed.ticketconsolecloudban.settings;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.util.List;

public record PanelSettings(
  String brandName,
  String brandLogoUrl,
  List<String> ticketCategories,
  String appealStatusOpenLabel,
  String appealStatusInReviewLabel,
  String appealStatusAcceptedLabel,
  String appealStatusRejectedLabel,
  String appealStatusClosedLabel,
  String appealStatusOpenText,
  String appealStatusInReviewText,
  String appealStatusAcceptedText,
  String appealStatusRejectedText,
  String appealStatusClosedText,
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

  public static final List<String> DEFAULT_TICKET_CATEGORIES = List.of("SUPPORT", "BUG", "MELDEN", "SONSTIGES");

  public static final String DEFAULT_APPEAL_STATUS_OPEN_LABEL = "Offen";
  public static final String DEFAULT_APPEAL_STATUS_IN_REVIEW_LABEL = "In Prüfung";
  public static final String DEFAULT_APPEAL_STATUS_ACCEPTED_LABEL = "Angenommen";
  public static final String DEFAULT_APPEAL_STATUS_REJECTED_LABEL = "Abgelehnt";
  public static final String DEFAULT_APPEAL_STATUS_CLOSED_LABEL = "Geschlossen";

  public static final String DEFAULT_APPEAL_STATUS_OPEN_TEXT =
    "Dein Entbannungsantrag ist eingegangen und wartet auf die Bearbeitung durch unser Team.";
  public static final String DEFAULT_APPEAL_STATUS_IN_REVIEW_TEXT =
    "Dein Entbannungsantrag wird aktuell vom Team geprüft. Bitte habe noch etwas Geduld.";
  public static final String DEFAULT_APPEAL_STATUS_ACCEPTED_TEXT =
    "Dein Entbannungsantrag wurde angenommen. Bitte prüfe, ob dein Ban bereits aufgehoben wurde.";
  public static final String DEFAULT_APPEAL_STATUS_REJECTED_TEXT =
    "Dein Entbannungsantrag wurde abgelehnt. Die Entscheidung und Hinweise des Teams findest du in der Team-Notiz.";
  public static final String DEFAULT_APPEAL_STATUS_CLOSED_TEXT =
    "Dein Entbannungsantrag wurde geschlossen. Wenn du Fragen hast, melde dich bitte beim Serverteam.";

  public static PanelSettings fromConfiguration(PanelConfiguration configuration) {
    return new PanelSettings(
      configuration.brandName(),
      configuration.brandLogoUrl(),
      DEFAULT_TICKET_CATEGORIES,
      DEFAULT_APPEAL_STATUS_OPEN_LABEL,
      DEFAULT_APPEAL_STATUS_IN_REVIEW_LABEL,
      DEFAULT_APPEAL_STATUS_ACCEPTED_LABEL,
      DEFAULT_APPEAL_STATUS_REJECTED_LABEL,
      DEFAULT_APPEAL_STATUS_CLOSED_LABEL,
      DEFAULT_APPEAL_STATUS_OPEN_TEXT,
      DEFAULT_APPEAL_STATUS_IN_REVIEW_TEXT,
      DEFAULT_APPEAL_STATUS_ACCEPTED_TEXT,
      DEFAULT_APPEAL_STATUS_REJECTED_TEXT,
      DEFAULT_APPEAL_STATUS_CLOSED_TEXT,
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

  public String appealStatusLabel(String status) {
    return switch (status == null ? "" : status.trim().toUpperCase()) {
      case "IN_REVIEW" -> this.appealStatusInReviewLabel();
      case "ACCEPTED" -> this.appealStatusAcceptedLabel();
      case "REJECTED" -> this.appealStatusRejectedLabel();
      case "CLOSED" -> this.appealStatusClosedLabel();
      default -> this.appealStatusOpenLabel();
    };
  }

  public String appealStatusText(String status) {
    return switch (status == null ? "" : status.trim().toUpperCase()) {
      case "IN_REVIEW" -> this.appealStatusInReviewText();
      case "ACCEPTED" -> this.appealStatusAcceptedText();
      case "REJECTED" -> this.appealStatusRejectedText();
      case "CLOSED" -> this.appealStatusClosedText();
      default -> this.appealStatusOpenText();
    };
  }
}
