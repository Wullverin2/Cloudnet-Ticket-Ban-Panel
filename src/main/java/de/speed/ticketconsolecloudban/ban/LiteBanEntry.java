package de.speed.ticketconsolecloudban.ban;

public record LiteBanEntry(
  String id,
  String publicId,
  String targetName,
  String targetUniqueId,
  String targetAddress,
  String reason,
  String issuedBy,
  String serverScope,
  String createdAt,
  String expiresAt,
  boolean active,
  String removedBy,
  String removedAt,
  String lastSyncedAt
) {
}
