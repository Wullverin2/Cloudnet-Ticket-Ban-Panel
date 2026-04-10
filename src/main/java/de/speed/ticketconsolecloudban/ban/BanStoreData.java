package de.speed.ticketconsolecloudban.ban;

import java.util.List;

public record BanStoreData(
  List<CloudBanEntry> bans,
  List<LiteBanEntry> liteBans,
  List<BanActionRequest> actionRequests,
  List<BanAuditEntry> auditLog
) {
}
