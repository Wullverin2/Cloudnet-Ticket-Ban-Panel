package de.speed.ticketconsolecloudban.appeal;

import java.util.List;

public record BanAppealStoreData(
  List<BanAppealEntry> appeals
) {
}
