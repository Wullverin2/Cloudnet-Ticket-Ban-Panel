package de.speed.ticketconsolecloudban.appeal;

import de.speed.ticketconsolecloudban.auth.SmtpMailService;
import de.speed.ticketconsolecloudban.ban.LiteBansDatabaseSyncService;
import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import de.speed.ticketconsolecloudban.store.BanAppealStore;
import de.speed.ticketconsolecloudban.store.BanStore;
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
  private final SmtpMailService mailService;

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
    this.configuration = configuration;
    this.banStore = banStore;
    this.appealStore = appealStore;
    this.liteBansDatabaseSyncService = liteBansDatabaseSyncService;
    this.evidenceStorage = evidenceStorage;
    this.mailService = settingsStore == null
      ? new SmtpMailService(configuration)
      : new SmtpMailService(configuration, settingsStore);
  }

  public AppealSubmittedView submit(AppealMultipartForm form) {
    var publicBanId = required(form.field("banId"), "Random Ban-ID");
    var playerName = required(form.field("playerName"), "Spielername");
    var email = required(form.field("email"), "E-Mail");
    var reason = required(form.field("reason"), "Begruendung");
    var videoLink = nullable(form.field("videoLink"));

    if (!EMAIL_PATTERN.matcher(email).matches()) {
      throw new IllegalArgumentException("Bitte gib eine gueltige E-Mail-Adresse an.");
    }

    this.liteBansDatabaseSyncService.syncIfStale("litebans-mysql-appeal", Duration.ofSeconds(30));

    var ban = this.banStore.findLiteBanByPublicIdAndTargetName(publicBanId, playerName)
      .orElseThrow(() -> new IllegalArgumentException("Random Ban-ID und Spielername passen nicht zusammen oder der Ban ist nicht aktiv."));

    this.appealStore.findByBanAndPlayer(publicBanId, playerName)
      .ifPresent(existing -> {
        throw new IllegalArgumentException("Fuer diese Random Ban-ID und diesen Spielernamen existiert bereits ein Antrag.");
      });

    var files = form.files().stream()
      .filter(file -> "evidence".equals(file.fieldName()))
      .toList();
    if (files.size() > this.configuration.appealMaxFiles()) {
      throw new IllegalArgumentException("Es duerfen maximal " + this.configuration.appealMaxFiles() + " Beweise hochgeladen werden.");
    }

    var attachmentDrafts = new ArrayList<BanAppealAttachment>();
    var appealId = java.util.UUID.randomUUID().toString();
    for (var file : files) {
      if (file.content().length > this.configuration.appealMaxFileBytes()) {
        throw new IllegalArgumentException("Die Datei " + file.fileName() + " ist groesser als erlaubt.");
      }
      var stored = this.evidenceStorage.store(appealId, file);
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
      "Dein Entbannungsantrag wurde eingereicht. Bitte pruefe deine E-Mails fuer den Statuslink.",
      this.statusUrl(appeal));
  }

  public BanAppealEntry status(String token) {
    return this.appealStore.findByToken(token)
      .orElseThrow(() -> new IllegalArgumentException("Statuslink ist ungueltig."));
  }

  private void sendConfirmation(BanAppealEntry appeal) {
    var statusUrl = this.statusUrl(appeal);
    var text = "Hallo " + appeal.playerName() + ",\r\n\r\n"
      + "dein Entbannungsantrag fuer Ban-ID " + appeal.publicBanId() + " wurde eingereicht.\r\n"
      + "Status: " + statusUrl + "\r\n\r\n"
      + "Bitte bewahre diesen Link auf.";
    var html = """
      <html>
        <body style="margin:0;background:#07131d;color:#f5f0e7;font-family:Segoe UI,Arial,sans-serif;">
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#07131d;padding:28px;">
            <tr>
              <td align="center">
                <table role="presentation" width="620" cellspacing="0" cellpadding="0" style="max-width:620px;background:#0f1e2b;border:1px solid rgba(244,188,70,.35);border-radius:22px;overflow:hidden;">
                  <tr>
                    <td style="padding:28px;">
                      <p style="margin:0 0 10px;color:#f4bc46;letter-spacing:.18em;text-transform:uppercase;font-size:12px;">Entbannungsantrag</p>
                      <h1 style="margin:0 0 16px;font-size:28px;color:#f5f0e7;">Antrag eingegangen</h1>
                      <p style="margin:0 0 14px;color:#d7e2ea;">Hallo <strong>%s</strong>, dein Antrag fuer die Random Ban-ID <strong>%s</strong> wurde erfolgreich eingereicht.</p>
                      <p style="margin:0 0 22px;color:#9eb0bc;">Ueber den folgenden Link kannst du jederzeit den Status pruefen.</p>
                      <a href="%s" style="display:inline-block;background:#f4bc46;color:#1d1406;text-decoration:none;font-weight:700;padding:13px 18px;border-radius:14px;">Status ansehen</a>
                      <p style="margin:24px 0 0;color:#9eb0bc;font-size:13px;">Falls du diesen Antrag nicht erstellt hast, kontaktiere bitte das Serverteam.</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
      </html>
      """.formatted(escapeHtml(appeal.playerName()), escapeHtml(appeal.publicBanId()), escapeHtml(statusUrl));

    if (this.mailService.enabled()) {
      this.mailService.sendHtml(appeal.email(), "Entbannungsantrag eingegangen", text, html);
    } else {
      LOGGER.warn("Ban appeal confirmation for {} not mailed because SMTP is disabled. Status URL: {}", appeal.email(), statusUrl);
    }
  }

  private String statusUrl(BanAppealEntry appeal) {
    return this.configuration.appealPublicBaseUrl() + "/status?token=" + appeal.statusToken();
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
}
