package de.speed.ticketconsolecloudban.ticket;

public record TicketAuditEntry(
  String id,
  String ticketId,
  String action,
  String actor,
  String message,
  String createdAt
) {
}
