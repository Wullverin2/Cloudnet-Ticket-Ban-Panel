package de.speed.ticketconsolecloudban.player;

import java.util.List;

public record PlayerActionStoreData(
  List<PlayerActionRequest> actionRequests
) {
}
