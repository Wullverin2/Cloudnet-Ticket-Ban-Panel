package de.speed.ticketconsolecloudban.ban;

public record BanActionRequest(
  String id,
  String source,
  String action,
  String banId,
  String publicId,
  String targetName,
  String targetUniqueId,
  String targetAddress,
  String duration,
  String reason,
  String actor,
  String status,
  String createdAt,
  String completedAt,
  String message
) {
}
