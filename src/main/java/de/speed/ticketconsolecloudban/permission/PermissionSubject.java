package de.speed.ticketconsolecloudban.permission;

import java.util.List;

public record PermissionSubject(
  String serverId,
  String type,
  String id,
  String name,
  List<String> permissions,
  List<String> parents,
  String source,
  String lastSyncedAt
) {
}
