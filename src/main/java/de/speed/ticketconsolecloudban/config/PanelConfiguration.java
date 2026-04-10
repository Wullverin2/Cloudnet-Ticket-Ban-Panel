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
  List<String> apiTokens
) {

  private static final SecureRandom RANDOM = new SecureRandom();

  public static PanelConfiguration createDefault() {
    return new PanelConfiguration(
      "0.0.0.0",
      8088,
      250,
      "Network Control",
      List.of(generateToken()));
  }

  public PanelConfiguration normalize() {
    var normalizedHost = this.bindHost == null || this.bindHost.isBlank() ? "0.0.0.0" : this.bindHost.trim();
    var normalizedPort = this.bindPort > 0 && this.bindPort <= 0xFFFF ? this.bindPort : 8088;
    var normalizedLimit = clamp(this.consoleLineLimit, 50, 1000);
    var normalizedBrand = this.brandName == null || this.brandName.isBlank() ? "Network Control" : this.brandName.trim();

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
      List.copyOf(normalizedTokens));
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
