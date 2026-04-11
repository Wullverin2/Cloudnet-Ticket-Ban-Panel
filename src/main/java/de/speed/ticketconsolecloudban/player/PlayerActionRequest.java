package de.speed.ticketconsolecloudban.player;

public record PlayerActionRequest(
  String id,
  String type,
  String status,
  String staffName,
  String targetName,
  String targetUniqueId,
  String targetServer,
  String ticketId,
  String actor,
  String createdAt,
  String completedAt,
  String message
) {
}
