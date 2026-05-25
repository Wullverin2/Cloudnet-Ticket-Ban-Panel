package de.speed.ticketconsolecloudban.auth;

public record PanelSession(
  String tokenHash,
  String username,
  String createdAt,
  String lastSeenAt,
  String expiresAt
) {

  public PanelSession withLastSeenAt(String value) {
    return new PanelSession(
      this.tokenHash,
      this.username,
      this.createdAt,
      value,
      this.expiresAt);
  }
}
