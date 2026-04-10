package de.speed.ticketconsolecloudban.auth;

import java.util.List;

public record PanelUser(
  String username,
  String displayName,
  String email,
  String minecraftName,
  String minecraftUniqueId,
  String passwordHash,
  String passwordSalt,
  int passwordIterations,
  List<String> groups,
  boolean enabled,
  String createdAt,
  String updatedAt,
  String lastLoginAt
) {
}
