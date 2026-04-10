package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.ban.BanStoreData;
import de.speed.ticketconsolecloudban.ban.CloudBanEntry;
import eu.cloudnetservice.driver.document.DocumentFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class BanStore {

  private final Path storagePath;
  private final List<CloudBanEntry> bans = new ArrayList<>();

  public BanStore(Path dataDirectory) {
    this.storagePath = dataDirectory.resolve("bans.json");
    this.load();
  }

  public synchronized List<CloudBanEntry> list() {
    return this.bans.stream()
      .sorted(Comparator.comparing(CloudBanEntry::createdAt).reversed())
      .toList();
  }

  public synchronized CloudBanEntry create(
    String targetName,
    String targetUniqueId,
    String targetAddress,
    String reason,
    String issuedBy,
    String expiresAt
  ) {
    var entry = new CloudBanEntry(
      UUID.randomUUID().toString(),
      targetName,
      targetUniqueId,
      targetAddress,
      reason,
      issuedBy,
      Instant.now().toString(),
      expiresAt,
      true,
      null,
      null);
    this.bans.add(entry);
    this.save();
    return entry;
  }

  public synchronized CloudBanEntry deactivate(String id, String removedBy) {
    var existing = this.require(id);
    var updated = new CloudBanEntry(
      existing.id(),
      existing.targetName(),
      existing.targetUniqueId(),
      existing.targetAddress(),
      existing.reason(),
      existing.issuedBy(),
      existing.createdAt(),
      existing.expiresAt(),
      false,
      removedBy,
      Instant.now().toString());
    this.replace(updated);
    return updated;
  }

  public synchronized CloudBanEntry get(String id) {
    return this.require(id);
  }

  private CloudBanEntry require(String id) {
    return this.bans.stream()
      .filter(entry -> entry.id().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Der Ban wurde nicht gefunden."));
  }

  private void replace(CloudBanEntry updated) {
    for (int index = 0; index < this.bans.size(); index++) {
      if (this.bans.get(index).id().equals(updated.id())) {
        this.bans.set(index, updated);
        this.save();
        return;
      }
    }
    throw new IllegalArgumentException("Der Ban wurde nicht gefunden.");
  }

  private void load() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      if (Files.notExists(this.storagePath)) {
        this.save();
        return;
      }

      var document = DocumentFactory.json().parse(this.storagePath);
      var data = document.toInstanceOf(BanStoreData.class);
      if (data != null && data.bans() != null) {
        this.bans.clear();
        this.bans.addAll(data.bans());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Bans konnten nicht geladen werden.", exception);
    }
  }

  private void save() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      DocumentFactory.json()
        .newDocument()
        .appendTree(new BanStoreData(List.copyOf(this.bans)))
        .writeTo(this.storagePath);
    } catch (Exception exception) {
      throw new IllegalStateException("Bans konnten nicht gespeichert werden.", exception);
    }
  }
}
