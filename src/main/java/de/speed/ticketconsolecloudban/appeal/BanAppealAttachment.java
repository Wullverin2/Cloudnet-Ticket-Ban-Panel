package de.speed.ticketconsolecloudban.appeal;

public record BanAppealAttachment(
  String id,
  String fileName,
  String contentType,
  long size,
  String storageType,
  String storageReference,
  String createdAt
) {
}
