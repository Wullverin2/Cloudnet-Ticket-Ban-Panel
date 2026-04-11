package de.speed.ticketconsolecloudban.permission;

import java.util.List;

public record PermissionBridgeStoreData(
  List<PermissionSubject> subjects,
  List<PermissionActionRequest> actionRequests,
  List<PermissionAuditEntry> auditLog
) {
}
