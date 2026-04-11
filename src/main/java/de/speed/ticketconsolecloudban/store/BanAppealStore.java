package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.appeal.BanAppealAttachment;
import de.speed.ticketconsolecloudban.appeal.BanAppealEntry;
import de.speed.ticketconsolecloudban.appeal.BanAppealStoreData;
import eu.cloudnetservice.driver.document.DocumentFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BanAppealStore {

  private final Path storagePath;
  private final PanelDataBackend backend;
  private final List<BanAppealEntry> appeals = new ArrayList<>();

  public BanAppealStore(Path dataDirectory) {
    this(dataDirectory, new LocalPanelDataBackend());
  }

  public BanAppealStore(Path dataDirectory, PanelDataBackend backend) {
    this.storagePath = dataDirectory.resolve("ban-appeals.json");
    this.backend = backend;
    this.load();
  }

  public synchronized List<BanAppealEntry> list() {
    return this.appeals.stream()
      .sorted(Comparator.comparing(BanAppealEntry::createdAt).reversed())
      .toList();
  }

  public synchronized Optional<BanAppealEntry> findByToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    return this.appeals.stream()
      .filter(appeal -> appeal.statusToken().equals(token))
      .findFirst();
  }

  public synchronized Optional<BanAppealEntry> findByBanAndPlayer(String publicBanId, String playerName) {
    var normalizedBanId = normalize(publicBanId);
    var normalizedPlayer = normalize(playerName);
    return this.appeals.stream()
      .filter(appeal -> normalize(appeal.publicBanId()).equals(normalizedBanId))
      .filter(appeal -> normalize(appeal.playerName()).equals(normalizedPlayer))
      .findFirst();
  }

  public synchronized BanAppealEntry create(
    String publicBanId,
    String liteBanId,
    String playerName,
    String playerUniqueId,
    String email,
    String reason,
    String videoLink,
    List<BanAppealAttachment> attachments
  ) {
    this.findByBanAndPlayer(publicBanId, playerName)
      .ifPresent(existing -> {
        throw new IllegalArgumentException("Für diese Ban-ID und diesen Spielernamen existiert bereits ein Entbannungsantrag.");
      });

    var now = Instant.now().toString();
    var appeal = new BanAppealEntry(
      UUID.randomUUID().toString(),
      UUID.randomUUID().toString().replace("-", ""),
      "OPEN",
      publicBanId,
      liteBanId,
      playerName,
      playerUniqueId,
      email,
      reason,
      videoLink,
      attachments == null ? List.of() : List.copyOf(attachments),
      now,
      now,
      null);
    this.appeals.add(appeal);
    this.save();
    return appeal;
  }

  public synchronized BanAppealEntry updateStatus(String id, String status, String teamNote) {
    var existing = this.appeals.stream()
      .filter(appeal -> appeal.id().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Der Entbannungsantrag wurde nicht gefunden."));
    var updated = new BanAppealEntry(
      existing.id(),
      existing.statusToken(),
      status == null || status.isBlank() ? existing.status() : status.trim().toUpperCase(),
      existing.publicBanId(),
      existing.liteBanId(),
      existing.playerName(),
      existing.playerUniqueId(),
      existing.email(),
      existing.reason(),
      existing.videoLink(),
      existing.attachments(),
      existing.createdAt(),
      Instant.now().toString(),
      teamNote == null || teamNote.isBlank() ? existing.teamNote() : teamNote.trim());
    this.replace(updated);
    return updated;
  }

  private void replace(BanAppealEntry updated) {
    for (int index = 0; index < this.appeals.size(); index++) {
      if (this.appeals.get(index).id().equals(updated.id())) {
        this.appeals.set(index, updated);
        this.save();
        return;
      }
    }
    throw new IllegalArgumentException("Der Entbannungsantrag wurde nicht gefunden.");
  }

  private void load() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      var data = this.backend.load("ban-appeals", this.storagePath, BanAppealStoreData.class, null);
      if (data == null) {
        this.save();
        return;
      }
      this.appeals.clear();
      if (data != null && data.appeals() != null) {
        this.appeals.addAll(data.appeals());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Entbannungsanträge konnten nicht geladen werden.", exception);
    }
  }

  private void save() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      this.backend.save("ban-appeals", this.storagePath, new BanAppealStoreData(List.copyOf(this.appeals)));
    } catch (Exception exception) {
      throw new IllegalStateException("Entbannungsanträge konnten nicht gespeichert werden.", exception);
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }
}
