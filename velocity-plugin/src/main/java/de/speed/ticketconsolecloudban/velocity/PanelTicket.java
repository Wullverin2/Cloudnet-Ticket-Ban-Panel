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
