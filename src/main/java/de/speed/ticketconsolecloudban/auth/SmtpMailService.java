package de.speed.ticketconsolecloudban.auth;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
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

  public SmtpMailService(PanelConfiguration configuration) {
    this.configuration = configuration;
  }

  public boolean enabled() {
    return this.configuration.smtpEnabled();
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
      this.send(recipient, subject, body);
    } catch (IOException exception) {
      throw new IllegalStateException("Reset-Mail konnte nicht gesendet werden: " + exception.getMessage(), exception);
    }
  }

  private void send(String recipient, String subject, String body) throws IOException {
    try (var socket = this.openSocket()) {
      var session = new Session(socket);
      session.expect(220);
      session.command("EHLO panel.local", 250);

      if (this.configuration.smtpStartTls() && !this.configuration.smtpSsl()) {
        session.command("STARTTLS", 220);
        session = new Session(((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(
          socket,
          this.configuration.smtpHost(),
          this.configuration.smtpPort(),
          true));
        session.command("EHLO panel.local", 250);
      }

      if (this.configuration.smtpUsername() != null && !this.configuration.smtpUsername().isBlank()) {
        session.command("AUTH LOGIN", 334);
        session.command(Base64.getEncoder().encodeToString(this.configuration.smtpUsername().getBytes(StandardCharsets.UTF_8)), 334);
        session.command(Base64.getEncoder().encodeToString(this.configuration.smtpPassword().getBytes(StandardCharsets.UTF_8)), 235);
      }

      session.command("MAIL FROM:<" + this.configuration.smtpFrom() + ">", 250);
      session.command("RCPT TO:<" + recipient + ">", 250);
      session.command("DATA", 354);
      session.write("From: " + this.configuration.smtpFrom() + "\r\n"
        + "To: " + recipient + "\r\n"
        + "Subject: " + subject + "\r\n"
        + "Content-Type: text/plain; charset=UTF-8\r\n"
        + "\r\n"
        + body.replace("\r\n.", "\r\n..")
        + "\r\n.");
      session.expect(250);
      session.command("QUIT", 221);
    }
  }

  private Socket openSocket() throws IOException {
    if (this.configuration.smtpSsl()) {
      return SSLSocketFactory.getDefault().createSocket(this.configuration.smtpHost(), this.configuration.smtpPort());
    }
    return new Socket(this.configuration.smtpHost(), this.configuration.smtpPort());
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
