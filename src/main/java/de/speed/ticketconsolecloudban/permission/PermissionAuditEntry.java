package de.speed.ticketconsolecloudban.permission;

public record PermissionAuditEntry(
  String id,
  String serverId,
  String action,
  String subjectType,
  String subjectId,
  String actor,
  String message,
  String createdAt
) {
}
