package de.speed.ticketconsolecloudban.auth;

import java.util.List;

public record PanelGroup(
  String name,
  List<String> permissions,
  boolean system,
  String createdAt,
  String updatedAt
) {
}
