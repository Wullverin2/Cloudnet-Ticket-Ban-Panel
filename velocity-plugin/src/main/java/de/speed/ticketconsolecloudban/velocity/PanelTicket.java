package de.speed.ticketconsolecloudban.velocity;

public record PanelTicket(
  String id,
  String creatorName,
  String creatorUniqueId,
  String category,
  String priority,
  String status,
  String subject,
  String content,
  String assignedTo,
  String sourceServer,
  String createdAt,
  String updatedAt,
  java.util.List<PanelTicketComment> comments
) {
}

record PanelTicketComment(
  String author,
  String message,
  boolean internal,
  String createdAt
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
  String serverId,
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
  String serverId,
  String action,
  String subjectType,
  String subjectId,
  String permission,
  String parent,
  Boolean value,
  String actor,
  String status
) {
}

record PanelPlayerAction(
  String id,
  String type,
  String status,
  String staffName,
  String targetName,
  String targetUniqueId,
  String targetServer,
  String ticketId,
  String actor
) {
}
