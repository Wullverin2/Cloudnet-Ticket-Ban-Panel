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

    var subject = "Network Control Passwort zuruecksetzen";
    var body = "Hallo,\r\n\r\n"
      + "fuer dein Network Control Panel wurde ein Passwort-Reset angefordert.\r\n"
      + "Oeffne diesen Link, um ein neues Passwort zu setzen:\r\n\r\n"
      + resetUrl + "\r\n\r\n"
      + "Gueltig bis: " + expiresAt + "\r\n"
      + "Wenn du das nicht warst, kannst du diese Mail ignorieren.\r\n";

    try {
      this.send(recipient, subject, body, null);
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
