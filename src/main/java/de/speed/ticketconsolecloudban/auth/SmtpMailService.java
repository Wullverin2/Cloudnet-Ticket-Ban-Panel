package de.speed.ticketconsolecloudban.auth;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettings;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocketFactory;

public final class SmtpMailService {

  private final PanelConfiguration configuration;
  private final PanelSettingsStore settingsStore;

  public SmtpMailService(PanelConfiguration configuration) {
    this.configuration = configuration;
    this.settingsStore = null;
  }

  public SmtpMailService(PanelConfiguration configuration, PanelSettingsStore settingsStore) {
    this.configuration = configuration;
    this.settingsStore = settingsStore;
  }

  public boolean enabled() {
    return this.settings().smtpEnabled();
  }

  public void sendPasswordReset(String recipient, String resetUrl, String expiresAt) {
    if (!this.enabled()) {
      return;
    }

    var subject = this.brandName() + " Passwort zuruecksetzen";
    var body = "Hallo,\r\n\r\n"
      + "fuer dein " + this.brandName() + " Panel wurde ein Passwort-Reset angefordert.\r\n"
      + "Oeffne diesen Link, um ein neues Passwort zu setzen:\r\n\r\n"
      + resetUrl + "\r\n\r\n"
      + "Gueltig bis: " + expiresAt + "\r\n"
      + "Wenn du das nicht warst, kannst du diese Mail ignorieren.\r\n";
    var html = this.mailHtml(
      "Panel Sicherheit",
      "Passwort zuruecksetzen",
      "<p style=\"margin:0 0 14px;color:#d7e2ea;line-height:1.55;\">Fuer dein <strong>"
        + escapeHtml(this.brandName())
        + "</strong> Panel wurde ein Passwort-Reset angefordert.</p>"
        + "<p style=\"margin:0;color:#9eb0bc;line-height:1.55;\">Der Link ist gueltig bis <strong style=\"color:#f5f0e7;\">"
        + escapeHtml(expiresAt)
        + "</strong>. Wenn du das nicht warst, kannst du diese Mail ignorieren.</p>",
      resetUrl,
      "Neues Passwort setzen",
      "Craftplay Panel");

    try {
      this.send(recipient, subject, body, html);
    } catch (IOException exception) {
      throw new IllegalStateException("Reset-Mail konnte nicht gesendet werden: " + exception.getMessage(), exception);
    }
  }

  public void sendHtml(String recipient, String subject, String textBody, String htmlBody) {
    if (!this.enabled()) {
      return;
    }

    try {
      this.send(recipient, subject, textBody, htmlBody);
    } catch (IOException exception) {
      throw new IllegalStateException("Mail konnte nicht gesendet werden: " + exception.getMessage(), exception);
    }
  }

  private void send(String recipient, String subject, String body, String htmlBody) throws IOException {
    try (var socket = this.openSocket()) {
      var session = new Session(socket);
      session.expect(220);
      session.command("EHLO panel.local", 250);

      var settings = this.settings();

      if (settings.smtpStartTls() && !settings.smtpSsl()) {
        session.command("STARTTLS", 220);
        session = new Session(((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(
          socket,
          settings.smtpHost(),
          settings.smtpPort(),
          true));
        session.command("EHLO panel.local", 250);
      }

      if (settings.smtpUsername() != null && !settings.smtpUsername().isBlank()) {
        session.command("AUTH LOGIN", 334);
        session.command(Base64.getEncoder().encodeToString(settings.smtpUsername().getBytes(StandardCharsets.UTF_8)), 334);
        session.command(Base64.getEncoder().encodeToString(settings.smtpPassword().getBytes(StandardCharsets.UTF_8)), 235);
      }

      session.command("MAIL FROM:<" + settings.smtpFrom() + ">", 250);
      session.command("RCPT TO:<" + recipient + ">", 250);
      session.command("DATA", 354);
      session.write("From: " + settings.smtpFrom() + "\r\n"
        + "To: " + recipient + "\r\n"
        + "Subject: " + subject + "\r\n"
        + messageContent(body, htmlBody)
        + "\r\n"
        + "\r\n.");
      session.expect(250);
      session.command("QUIT", 221);
    }
  }

  private static String messageContent(String body, String htmlBody) {
    if (htmlBody == null || htmlBody.isBlank()) {
      return "Content-Type: text/plain; charset=UTF-8\r\n"
        + "\r\n"
        + body.replace("\r\n.", "\r\n..");
    }

    var boundary = "tccb-" + Long.toHexString(System.nanoTime());
    return "MIME-Version: 1.0\r\n"
      + "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"\r\n"
      + "\r\n"
      + "--" + boundary + "\r\n"
      + "Content-Type: text/plain; charset=UTF-8\r\n"
      + "\r\n"
      + body.replace("\r\n.", "\r\n..") + "\r\n"
      + "--" + boundary + "\r\n"
      + "Content-Type: text/html; charset=UTF-8\r\n"
      + "\r\n"
      + htmlBody.replace("\r\n.", "\r\n..") + "\r\n"
      + "--" + boundary + "--";
  }

  private Socket openSocket() throws IOException {
    var settings = this.settings();
    if (settings.smtpSsl()) {
      return SSLSocketFactory.getDefault().createSocket(settings.smtpHost(), settings.smtpPort());
    }
    return new Socket(settings.smtpHost(), settings.smtpPort());
  }

  private PanelSettings settings() {
    return this.settingsStore == null
      ? PanelSettings.fromConfiguration(this.configuration)
      : this.settingsStore.current();
  }

  private String brandName() {
    var value = this.settings().brandName();
    return value == null || value.isBlank() ? this.configuration.brandName() : value.trim();
  }

  private String brandLogoUrl() {
    var value = this.settings().brandLogoUrl();
    return value == null ? "" : value.trim();
  }

  private String mailHtml(String eyebrow, String title, String bodyHtml, String buttonUrl, String buttonLabel, String footer) {
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
        escapeHtml(this.brandName()),
        escapeHtml(eyebrow),
        escapeHtml(title),
        bodyHtml,
        buttonHtml,
        escapeHtml(footer));
  }

  private static String escapeHtml(String value) {
    return String.valueOf(value)
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }

  private static final class Session {

    private final BufferedReader reader;
    private final BufferedWriter writer;

    private Session(Socket socket) throws IOException {
      this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private void command(String command, int expectedCode) throws IOException {
      this.write(command);
      this.expect(expectedCode);
    }

    private void write(String command) throws IOException {
      this.writer.write(command);
      this.writer.write("\r\n");
      this.writer.flush();
    }

    private void expect(int expectedCode) throws IOException {
      String line;
      String last = null;
      do {
        line = this.reader.readLine();
        if (line == null) {
          throw new IOException("SMTP Verbindung wurde geschlossen.");
        }
        last = line;
      } while (line.length() > 3 && line.charAt(3) == '-');

      if (last.length() < 3 || Integer.parseInt(last.substring(0, 3)) != expectedCode) {
        throw new IOException("SMTP erwartete " + expectedCode + ", bekam: " + last);
      }
    }
  }
}
