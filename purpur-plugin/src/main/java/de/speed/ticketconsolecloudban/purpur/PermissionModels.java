package de.speed.ticketconsolecloudban.purpur;

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
