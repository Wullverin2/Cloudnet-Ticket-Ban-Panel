package de.speed.ticketconsolecloudban.ticket;

import java.util.List;

public record TicketEntry(
  String id,
  String creatorName,
  String creatorUniqueId,
  String category,
  String priority,
  String status,
  String subject,
  String content,
  String assignedTo,
  String serviceName,
  String createdAt,
  String updatedAt,
  List<TicketComment> comments
) {
}

