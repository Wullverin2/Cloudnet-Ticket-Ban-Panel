package de.speed.ticketconsolecloudban.auth;

import java.util.List;

public record PanelPrincipal(
  String username,
  String displayName,
  List<String> permissions,
  boolean apiToken,
  String sessionToken
) {

  public boolean hasPermission(String permission) {
    return this.permissions.contains(PanelPermission.ALL) || this.permissions.contains(permission);
  }
}
