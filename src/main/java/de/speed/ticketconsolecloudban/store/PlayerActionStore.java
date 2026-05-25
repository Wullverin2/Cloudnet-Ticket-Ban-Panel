package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.player.PlayerActionRequest;
import de.speed.ticketconsolecloudban.player.PlayerActionStoreData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PlayerActionStore {

  private final Path storagePath;
  private final PanelDataBackend backend;
  private final List<PlayerActionRequest> actionRequests = new ArrayList<>();

  public PlayerActionStore(Path dataDirectory) {
    this(dataDirectory, new LocalPanelDataBackend());
  }

  public PlayerActionStore(Path dataDirectory, PanelDataBackend backend) {
    this.storagePath = dataDirectory.resolve("player-actions.json");
    this.backend = backend;
    this.load();
  }

  public synchronized List<PlayerActionRequest> pendingActionRequests() {
    return this.actionRequests.stream()
      .filter(request -> "PENDING".equals(request.status()))
      .sorted(Comparator.comparing(PlayerActionRequest::createdAt))
      .toList();
  }

  public synchronized PlayerActionRequest requestTeleport(
    String staffName,
    String targetName,
    String targetUniqueId,
    String targetServer,
    String ticketId,
    String actor
  ) {
    var request = new PlayerActionRequest(
      UUID.randomUUID().toString(),
      "TELEPORT_TO_PLAYER",
      "PENDING",
      require(staffName, "Teamler"),
      require(targetName, "Spieler"),
      blankToNull(targetUniqueId),
      blankToNull(targetServer),
      blankToNull(ticketId),
      blankDefault(actor, staffName),
      Instant.now().toString(),
      null,
      null);
    this.actionRequests.add(request);
    this.save();
    return request;
  }

  public synchronized PlayerActionRequest completeActionRequest(String id, boolean success, String message) {
    var existing = this.actionRequests.stream()
      .filter(request -> request.id().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Spieler-Aktion wurde nicht gefunden."));
    var updated = new PlayerActionRequest(
      existing.id(),
      existing.type(),
      success ? "COMPLETED" : "FAILED",
      existing.staffName(),
      existing.targetName(),
      existing.targetUniqueId(),
      existing.targetServer(),
      existing.ticketId(),
      existing.actor(),
      existing.createdAt(),
      Instant.now().toString(),
      message);

    for (int index = 0; index < this.actionRequests.size(); index++) {
      if (this.actionRequests.get(index).id().equals(updated.id())) {
        this.actionRequests.set(index, updated);
        this.save();
        return updated;
      }
    }
    throw new IllegalArgumentException("Spieler-Aktion wurde nicht gefunden.");
  }

  private void load() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      var data = this.backend.load("player-actions", this.storagePath, PlayerActionStoreData.class, null);
      if (data == null) {
        this.save();
        return;
      }
      if (data.actionRequests() != null) {
        this.actionRequests.clear();
        this.actionRequests.addAll(data.actionRequests());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Spieler-Aktionen konnten nicht geladen werden.", exception);
    }
  }

  private void save() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      this.backend.save(
        "player-actions",
        this.storagePath,
        new PlayerActionStoreData(List.copyOf(this.actionRequests)));
    } catch (Exception exception) {
      throw new IllegalStateException("Spieler-Aktionen konnten nicht gespeichert werden.", exception);
    }
  }

  private static String require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " ist erforderlich.");
    }
    return value.trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String blankDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
