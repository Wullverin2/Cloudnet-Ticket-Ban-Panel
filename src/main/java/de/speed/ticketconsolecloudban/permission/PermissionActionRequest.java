package de.speed.ticketconsolecloudban.permission;

public record PermissionActionRequest(
  String id,
  String serverId,
  String action,
  String subjectType,
  String subjectId,
  String permission,
  String parent,
  Boolean value,
  String actor,
  String status,
  String createdAt,
  String completedAt,
  String message
) {
}
