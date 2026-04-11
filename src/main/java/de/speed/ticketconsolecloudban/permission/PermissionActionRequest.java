package de.speed.ticketconsolecloudban.permission;

public record PermissionActionRequest(
  String id,
  String action,
  String subjectType,
  String subjectId,
  String permission,
  String parent,
  String actor,
  String status,
  String createdAt,
  String completedAt,
  String message
) {
}
