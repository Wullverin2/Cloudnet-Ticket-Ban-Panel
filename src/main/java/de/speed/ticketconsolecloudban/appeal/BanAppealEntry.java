package de.speed.ticketconsolecloudban.appeal;

import java.util.List;

public record BanAppealEntry(
  String id,
  String statusToken,
  String status,
  String publicBanId,
  String liteBanId,
  String playerName,
  String playerUniqueId,
  String email,
  String reason,
  String videoLink,
  List<BanAppealAttachment> attachments,
  String createdAt,
  String updatedAt,
  String teamNote,
  String updatedBy
) {
}
