package de.speed.ticketconsolecloudban.auth;

public enum TwoFactorMethod {
  NONE,
  EMAIL,
  TOTP;

  public static TwoFactorMethod parse(String value) {
    if (value == null || value.isBlank()) {
      return NONE;
    }

    try {
      return TwoFactorMethod.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unbekannte 2FA-Methode.");
    }
  }

  public boolean enabled() {
    return this != NONE;
  }
}
