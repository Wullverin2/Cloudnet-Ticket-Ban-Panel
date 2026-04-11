package de.speed.ticketconsolecloudban.velocity;

public record PanelTicket(
  String id,
  String creatorName,
  String category,
  String priority,
  String status,
  String subject,
  String sourceServer,
  String createdAt,
  String updatedAt
) {
}

record LiteBanSnapshot(
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

record PanelBanAction(
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
  String status
) {
}

record PermissionSubjectSnapshot(
  String type,
  String id,
  String name,
  java.util.List<String> permissions,
  java.util.List<String> parents,
  String source,
  String lastSyncedAt
) {
}

record PanelPermissionAction(
  String id,
  String action,
  String subjectType,
  String subjectId,
  String permission,
  String parent,
  String actor,
  String status
) {
}
