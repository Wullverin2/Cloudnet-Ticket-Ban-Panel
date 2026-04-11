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
  int passwordResetTokenMinutes
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
      30);
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
      normalizedResetMinutes);
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
