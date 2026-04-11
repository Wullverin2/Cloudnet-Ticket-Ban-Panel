package de.speed.ticketconsolecloudban.auth;

public record PasswordResetToken(
  String tokenHash,
  String username,
  String email,
  String createdAt,
  String expiresAt,
  String usedAt
) {
}
