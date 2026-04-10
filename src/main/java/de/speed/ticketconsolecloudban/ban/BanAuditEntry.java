package de.speed.ticketconsolecloudban.ban;

public record BanAuditEntry(
  String id,
  String source,
  String banId,
  String publicId,
  String action,
  String actor,
  String message,
  String createdAt
) {
}
