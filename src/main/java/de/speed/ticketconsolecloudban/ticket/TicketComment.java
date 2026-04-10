package de.speed.ticketconsolecloudban.ticket;

public record TicketComment(
  String author,
  String message,
  boolean internal,
  String createdAt
) {
}

