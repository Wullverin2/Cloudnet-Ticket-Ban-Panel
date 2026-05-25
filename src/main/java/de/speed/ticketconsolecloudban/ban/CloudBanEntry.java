package de.speed.ticketconsolecloudban.ban;

public record CloudBanEntry(
  String id,
  String targetName,
  String targetUniqueId,
  String targetAddress,
  String reason,
  String issuedBy,
  String createdAt,
  String expiresAt,
  boolean active,
  String removedBy,
  String removedAt
) {
}

