package de.speed.ticketconsolecloudban.appeal;

import de.speed.ticketconsolecloudban.auth.SmtpMailService;
import de.speed.ticketconsolecloudban.ban.LiteBansDatabaseSyncService;
import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettings;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import de.speed.ticketconsolecloudban.store.BanAppealStore;
import de.speed.ticketconsolecloudban.store.BanStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BanAppealService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BanAppealService.class);
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final PanelConfiguration configuration;
  private final BanStore banStore;
  private final BanAppealStore appealStore;
  private final LiteBansDatabaseSyncService liteBansDatabaseSyncService;
  private final EvidenceStorage evidenceStorage;
  private final Path dataDirectory;
  private final SmtpMailService mailService;
  private final PanelSettingsStore settingsStore;

  public BanAppealService(
    PanelConfiguration configuration,
    BanStore banStore,
    BanAppealStore appealStore,
    LiteBansDatabaseSyncService liteBansDatabaseSyncService,
    EvidenceStorage evidenceStorage
  ) {
    this(configuration, banStore, appealStore, liteBansDatabaseSyncService, evidenceStorage, null);
  }

  public BanAppealService(
    PanelConfiguration configuration,
    BanStore banStore,
    BanAppealStore appealStore,
    LiteBansDatabaseSyncService liteBansDatabaseSyncService,
    EvidenceStorage evidenceStorage,
    PanelSettingsStore settingsStore
  ) {
    this(configuration, banStore, appealStore, liteBansDatabaseSyncService, evidenceStorage, settingsStore, null);
  }

  public BanAppealService(
    PanelConfiguration configuration,
    BanStore banStore,
    BanAppealStore appealStore,
    LiteBansDatabaseSyncService liteBansDatabaseSyncService,
    EvidenceStorage evidenceStorage,
    PanelSettingsStore settingsStore,
    Path dataDirectory
  ) {
    this.configuration = configuration;
    this.banStore = banStore;
    this.appealStore = appealStore;
    this.liteBansDatabaseSyncService = liteBansDatabaseSyncService;
    this.evidenceStorage = evidenceStorage;
    this.dataDirectory = dataDirectory;
    this.settingsStore = settingsStore;
    this.mailService = settingsStore == null
      ? new SmtpMailService(configuration)
      : new SmtpMailService(configuration, settingsStore);
  }

  public AppealSubmittedView submit(AppealMultipartForm form) {
    var publicBanId = required(form.field("banId"), "Random Ban-ID");
    var playerName = required(form.field("playerName"), "Spielername");
    var email = required(form.field("email"), "E-Mail");
    var reason = required(form.field("reason"), "Begründung");
    var videoLink = nullable(form.field("videoLink"));

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      throw new IllegalArgumentException("Bitte gib eine gültige E-Mail-Adresse an.");
    }

    this.liteBansDatabaseSyncService.syncIfStale("litebans-mysql-appeal", Duration.ofSeconds(30));

    var ban = this.banStore.findLiteBanByPublicIdAndTargetName(publicBanId, playerName)
      .orElseThrow(() -> new IllegalArgumentException("Random Ban-ID und Spielername passen nicht zusammen oder der Ban ist nicht aktiv."));

    this.appealStore.findByBanAndPlayer(publicBanId, playerName)
      .ifPresent(existing -> {
        throw new IllegalArgumentException("Für diese Random Ban-ID und diesen Spielernamen existiert bereits ein Antrag.");
      });

    var files = form.files().stream()
      .filter(file -> "evidence".equals(file.fieldName()))
      .toList();
    var maxFiles = this.maxFiles();
    var maxFileBytes = this.maxFileBytes();
    if (files.size() > maxFiles) {
      throw new IllegalArgumentException("Es duerfen maximal " + maxFiles + " Beweise hochgeladen werden.");
    }

    var attachmentDrafts = new ArrayList<BanAppealAttachment>();
    var appealId = java.util.UUID.randomUUID().toString();
    for (var file : files) {
      if (file.content().length > maxFileBytes) {
        throw new IllegalArgumentException("Die Datei " + file.fileName() + " ist größer als erlaubt.");
      }
      var stored = this.evidenceStorage().store(appealId, file);
      attachmentDrafts.add(new BanAppealAttachment(
        java.util.UUID.randomUUID().toString(),
        file.fileName(),
        file.contentType(),
        file.content().length,
        stored.storageType(),
        stored.storageReference(),
        Instant.now().toString()));
    }

    var appeal = this.appealStore.create(
      ban.publicId(),
      ban.id(),
      ban.targetName(),
      ban.targetUniqueId(),
      email,
      reason,
      videoLink,
      List.copyOf(attachmentDrafts));
    this.sendConfirmation(appeal);
    return new AppealSubmittedView(
      "Dein " + this.appealTitle() + " wurde eingereicht. Bitte prüfe deine E-Mails für den Statuslink.",
      this.statusUrl(appeal));
  }

  public AppealMetaView meta() {
    return new AppealMetaView(
      this.appealBrandName(),
      this.brandLogoUrl(),
      this.appealTitle(),
      this.appealStatusTitle());
  }

  public AppealStatusView status(String token) {
    var appeal = this.appealStore.findByToken(token)
      .orElseThrow(() -> new IllegalArgumentException("Statuslink ist ungültig."));
    return new AppealStatusView(
      appeal.id(),
      appeal.statusToken(),
      appeal.status(),
      this.settings().appealStatusLabel(appeal.status()),
      this.settings().appealStatusText(appeal.status()),
      appeal.publicBanId(),
      appeal.liteBanId(),
      appeal.playerName(),
      appeal.playerUniqueId(),
      appeal.reason(),
      appeal.videoLink(),
      appeal.createdAt(),
      appeal.updatedAt(),
      appeal.teamNote());
  }

  public long maxRequestBytes() {
    return (this.maxFileBytes() * Math.max(1, this.maxFiles())) + 512_000L;
  }

  private void sendConfirmation(BanAppealEntry appeal) {
    var statusUrl = this.statusUrl(appeal);
    var appealTitle = this.appealTitle();
    var statusLabel = this.settings().appealStatusLabel(appeal.status());
    var statusText = this.settings().appealStatusText(appeal.status());
    var text = "Hallo " + appeal.playerName() + ",\r\n\r\n"
      + "dein " + appealTitle + " für Ban-ID " + appeal.publicBanId() + " wurde eingereicht.\r\n"
      + "Status: " + statusLabel + "\r\n"
      + statusText + "\r\n"
      + "Status: " + statusUrl + "\r\n\r\n"
      + "Bitte bewahre diesen Link auf.";
    var body = "<p style=\"margin:0 0 14px;color:#d7e2ea;line-height:1.55;\">Hallo <strong>"
      + escapeHtml(appeal.playerName())
      + "</strong>, dein "
      + escapeHtml(appealTitle)
      + " für die Random Ban-ID <strong>"
      + escapeHtml(appeal.publicBanId())
      + "</strong> wurde erfolgreich eingereicht.</p>"
      + "<div style=\"margin:18px 0;padding:16px;border-radius:18px;background:rgba(255,255,255,.045);border:1px solid rgba(244,188,70,.2);\">"
      + "<p style=\"margin:0 0 8px;color:#f4bc46;font-weight:800;letter-spacing:.08em;text-transform:uppercase;\">"
      + escapeHtml(statusLabel)
      + "</p><p style=\"margin:0;color:#d7e2ea;line-height:1.55;\">"
      + escapeHtml(statusText)
      + "</p></div>"
      + "<p style=\"margin:0;color:#9eb0bc;line-height:1.55;\">Über den folgenden Link kannst du jederzeit den Status prüfen.</p>";
    var html = this.craftplayMailHtml(
      appealTitle,
      appealTitle + " eingegangen",
      body,
      statusUrl,
      "Status ansehen",
      "Falls du diesen Antrag nicht erstellt hast, kontaktiere bitte das Serverteam.");

    if (this.mailService.enabled()) {
      this.mailService.sendHtml(appeal.email(), appealTitle + " eingegangen", text, html);
    } else {
      LOGGER.warn("Ban appeal confirmation for {} not mailed because SMTP is disabled. Status URL: {}", appeal.email(), statusUrl);
    }
  }

  private String statusUrl(BanAppealEntry appeal) {
    return this.appealPublicBaseUrl() + "/status?token=" + appeal.statusToken();
  }

  private PanelSettings settings() {
    return this.settingsStore == null
      ? PanelSettings.fromConfiguration(this.configuration)
      : this.settingsStore.current();
  }

  private EvidenceStorage evidenceStorage() {
    if (this.settingsStore == null || this.dataDirectory == null) {
      return this.evidenceStorage;
    }
    return EvidenceStorageFactory.create(
      AppealEvidenceConfiguration.from(this.configuration, this.settingsStore.current()),
      this.dataDirectory,
      this.settingsStore::updateOneDriveRefreshToken);
  }

  private int maxFiles() {
    return this.settings().appealMaxFiles();
  }

  private long maxFileBytes() {
    return this.settings().appealMaxFileBytes();
  }

  private String appealPublicBaseUrl() {
    var value = this.settings().appealPublicBaseUrl();
    return value == null || value.isBlank()
      ? this.configuration.appealPublicBaseUrl()
      : value.trim().replaceAll("/+$", "");
  }

  private String appealBrandName() {
    var value = this.settings().appealBrandName();
    if (value != null && !value.isBlank()) {
      return value.trim();
    }
    value = this.settings().brandName();
    return value == null || value.isBlank() ? this.configuration.brandName() : value.trim();
  }

  private String brandLogoUrl() {
    var value = this.settings().brandLogoUrl();
    return value == null ? "" : value.trim();
  }

  private String appealTitle() {
    var value = this.settings().appealTitle();
    return value == null || value.isBlank() ? PanelSettings.DEFAULT_APPEAL_TITLE : value.trim();
  }

  private String appealStatusTitle() {
    var value = this.settings().appealStatusTitle();
    return value == null || value.isBlank() ? PanelSettings.DEFAULT_APPEAL_STATUS_TITLE : value.trim();
  }

  private String craftplayMailHtml(String eyebrow, String title, String bodyHtml, String buttonUrl, String buttonLabel, String footer) {
    var logo = this.brandLogoUrl();
    var logoHtml = logo.isBlank()
      ? ""
      : "<img src=\"" + escapeHtml(logo) + "\" alt=\"\" style=\"width:46px;height:46px;object-fit:contain;border-radius:12px;margin-right:12px;vertical-align:middle;\">";
    var buttonHtml = buttonUrl == null || buttonUrl.isBlank()
      ? ""
      : "<p style=\"margin:24px 0 0;\"><a href=\"" + escapeHtml(buttonUrl) + "\" style=\"display:inline-block;background:linear-gradient(135deg,#f4bc46,#ff9f43);color:#1d1406;text-decoration:none;font-weight:800;padding:13px 18px;border-radius:14px;\">"
        + escapeHtml(buttonLabel)
        + "</a></p>";
    return """
      <html>
        <body style="margin:0;background:#07131d;color:#f5f0e7;font-family:Segoe UI,Arial,sans-serif;">
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#07131d;padding:30px;background-image:radial-gradient(circle at top left,rgba(244,188,70,.18),transparent 34%%),linear-gradient(180deg,#07131d,#081019);">
            <tr>
              <td align="center">
                <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;background:#0f1e2b;border:1px solid rgba(244,188,70,.35);border-radius:24px;overflow:hidden;box-shadow:0 24px 80px rgba(0,0,0,.35);">
                  <tr>
                    <td style="height:5px;background:linear-gradient(90deg,#f4bc46,#ff9f43,#46c4a6);"></td>
                  </tr>
                  <tr>
                    <td style="padding:30px;">
                      <div style="margin:0 0 18px;">%s<span style="font-size:19px;font-weight:900;vertical-align:middle;">%s</span></div>
                      <p style="margin:0 0 10px;color:#f4bc46;letter-spacing:.18em;text-transform:uppercase;font-size:12px;font-weight:800;">%s</p>
                      <h1 style="margin:0 0 16px;font-size:30px;line-height:1.05;color:#f5f0e7;">%s</h1>
                      %s
                      %s
                      <p style="margin:26px 0 0;color:#9eb0bc;font-size:13px;">%s</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
      </html>
      """.formatted(
        logoHtml,
        escapeHtml(this.appealBrandName()),
        escapeHtml(eyebrow),
        escapeHtml(title),
        bodyHtml,
        buttonHtml,
        escapeHtml(footer));
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " ist erforderlich.");
    }
    return value.trim();
  }

  private static String nullable(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String escapeHtml(String value) {
    return String.valueOf(value)
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }

  public record AppealSubmittedView(
    String message,
    String statusUrl
  ) {
  }

  public record AppealMetaView(
    String brandName,
    String brandLogoUrl,
    String appealTitle,
    String appealStatusTitle
  ) {
  }

  public record AppealStatusView(
    String id,
    String statusToken,
    String status,
    String statusLabel,
    String statusText,
    String publicBanId,
    String liteBanId,
    String playerName,
    String playerUniqueId,
    String reason,
    String videoLink,
    String createdAt,
    String updatedAt,
    String teamNote
  ) {
  }
}
