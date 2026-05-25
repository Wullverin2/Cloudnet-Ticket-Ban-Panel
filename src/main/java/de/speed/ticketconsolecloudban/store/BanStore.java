package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.ban.BanActionRequest;
import de.speed.ticketconsolecloudban.ban.BanAuditEntry;
import de.speed.ticketconsolecloudban.ban.BanStoreData;
import de.speed.ticketconsolecloudban.ban.CloudBanEntry;
import de.speed.ticketconsolecloudban.ban.LiteBanEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BanStore {

  private final Path storagePath;
  private final PanelDataBackend backend;
  private final List<CloudBanEntry> bans = new ArrayList<>();
  private final List<LiteBanEntry> liteBans = new ArrayList<>();
  private final List<BanActionRequest> actionRequests = new ArrayList<>();
  private final List<BanAuditEntry> auditLog = new ArrayList<>();

  public BanStore(Path dataDirectory) {
    this(dataDirectory, new LocalPanelDataBackend());
  }

  public BanStore(Path dataDirectory, PanelDataBackend backend) {
    this.storagePath = dataDirectory.resolve("bans.json");
    this.backend = backend;
    this.load();
  }

  public synchronized List<CloudBanEntry> list() {
    return this.bans.stream()
      .sorted(Comparator.comparing(CloudBanEntry::createdAt).reversed())
      .toList();
  }

  public synchronized List<LiteBanEntry> listLiteBans() {
    return this.liteBans.stream()
      .sorted(Comparator.comparing(LiteBanEntry::lastSyncedAt, Comparator.nullsLast(String::compareTo)).reversed())
      .toList();
  }

  public synchronized Optional<LiteBanEntry> findLiteBanByPublicIdAndTargetName(String publicId, String targetName) {
    var normalizedId = normalize(publicId);
    var normalizedName = normalize(targetName);
    return this.liteBans.stream()
      .filter(LiteBanEntry::active)
      .filter(entry -> isResolvedPublicId(entry.publicId(), entry.id()))
      .filter(entry -> normalize(entry.publicId()).equals(normalizedId))
      .filter(entry -> normalize(entry.targetName()).equals(normalizedName))
      .findFirst();
  }

  public synchronized List<BanAuditEntry> auditLog() {
    return this.auditLog.stream()
      .sorted(Comparator.comparing(BanAuditEntry::createdAt).reversed())
      .toList();
  }

  public synchronized List<BanActionRequest> pendingActionRequests() {
    return this.actionRequests.stream()
      .filter(request -> "PENDING".equals(request.status()))
      .sorted(Comparator.comparing(BanActionRequest::createdAt))
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
    this.audit("panel", entry.id(), entry.id(), "CREATE", issuedBy, "Panel-Ban erstellt: " + reason);
    this.save();
    return entry;
  }

  public synchronized CloudBanEntry deactivate(String id, String removedBy, String note) {
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
    this.audit(
      "panel",
      id,
      id,
      "UNBAN",
      removedBy,
      note == null || note.isBlank() ? "Panel-Ban deaktiviert" : "Panel-Ban deaktiviert: " + note.trim());
    this.replace(updated);
    return updated;
  }

  public synchronized CloudBanEntry get(String id) {
    return this.require(id);
  }

  public synchronized List<LiteBanEntry> syncLiteBans(List<LiteBanEntry> incoming, String actor) {
    var now = Instant.now().toString();
    var byId = new LinkedHashMap<String, LiteBanEntry>();
    for (var existing : this.liteBans) {
      byId.put(existing.id(), existing);
    }

    for (var entry : incoming) {
      if (entry == null || entry.id() == null || entry.id().isBlank()) {
        continue;
      }
      var previous = byId.get(entry.id());
      var synced = new LiteBanEntry(
        entry.id(),
        this.effectivePublicId(entry, previous),
        entry.targetName(),
        entry.targetUniqueId(),
        entry.targetAddress(),
        entry.reason(),
        entry.issuedBy(),
        entry.serverScope(),
        entry.createdAt(),
        entry.expiresAt(),
        entry.active(),
        entry.removedBy(),
        entry.removedAt(),
        now);
      previous = byId.put(synced.id(), synced);
      if (previous == null) {
        this.audit("litebans", synced.id(), synced.publicId(), "SYNC_CREATE", actor, "LiteBans-Ban synchronisiert");
      } else if (previous.active() && !synced.active()) {
        this.audit("litebans", synced.id(), synced.publicId(), "SYNC_UNBAN", actor, "LiteBans-Ban ist nicht mehr aktiv");
      }
    }

    this.liteBans.clear();
    this.liteBans.addAll(byId.values());
    this.save();
    return this.listLiteBans();
  }

  private String effectivePublicId(LiteBanEntry incoming, LiteBanEntry previous) {
    var incomingPublicId = isResolvedPublicId(incoming.publicId(), incoming.id()) ? incoming.publicId().trim() : null;
    if (previous == null || previous.publicId() == null || previous.publicId().isBlank()) {
      return incomingPublicId;
    }

    // MySQL can only provide LiteBans' numeric DB id. If the Velocity bridge is
    // temporarily unavailable, keep the last resolved random punishment id.
    if (incomingPublicId == null && isResolvedPublicId(previous.publicId(), previous.id())) {
      return previous.publicId();
    }
    return incomingPublicId;
  }

  public synchronized BanActionRequest requestLiteBanUnban(String banId, String actor, String reason) {
    var ban = this.requireLiteBan(banId);
    return this.createActionRequest(
      "litebans",
      "UNBAN",
      ban,
      null,
      actor,
      reason == null || reason.isBlank() ? "Unban via Panel" : reason);
  }

  public synchronized BanActionRequest requestLiteBanExtend(String banId, String actor, String duration, String reason) {
    if (duration == null || duration.isBlank()) {
      throw new IllegalArgumentException("Dauer ist erforderlich.");
    }
    var ban = this.requireLiteBan(banId);
    return this.createActionRequest(
      "litebans",
      "EXTEND",
      ban,
      duration,
      actor,
      reason == null || reason.isBlank() ? "Ban via Panel verlaengert" : reason);
  }

  public synchronized BanActionRequest completeActionRequest(String id, boolean success, String message) {
    var existing = this.requireActionRequest(id);
    var updated = new BanActionRequest(
      existing.id(),
      existing.source(),
      existing.action(),
      existing.banId(),
      existing.publicId(),
      existing.targetName(),
      existing.targetUniqueId(),
      existing.targetAddress(),
      existing.duration(),
      existing.reason(),
      existing.actor(),
      success ? "COMPLETED" : "FAILED",
      existing.createdAt(),
      Instant.now().toString(),
      message);

    this.replaceActionRequest(updated);
    this.audit(existing.source(), existing.banId(), existing.publicId(), existing.action() + "_" + updated.status(), existing.actor(), message);
    return updated;
  }

  private BanActionRequest createActionRequest(String source, String action, LiteBanEntry ban, String duration, String actor, String reason) {
    var request = new BanActionRequest(
      UUID.randomUUID().toString(),
      source,
      action,
      ban.id(),
      blankDefault(ban.publicId(), ban.id()),
      ban.targetName(),
      ban.targetUniqueId(),
      ban.targetAddress(),
      duration,
      reason,
      actor,
      "PENDING",
      Instant.now().toString(),
      null,
      null);
    this.actionRequests.add(request);
    this.audit(source, ban.id(), request.publicId(), action + "_REQUESTED", actor, reason);
    this.save();
    return request;
  }

  private CloudBanEntry require(String id) {
    return this.bans.stream()
      .filter(entry -> entry.id().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Der Ban wurde nicht gefunden."));
  }

  private LiteBanEntry requireLiteBan(String id) {
    return this.liteBans.stream()
      .filter(entry -> entry.id().equals(id) || (entry.publicId() != null && entry.publicId().equalsIgnoreCase(id)))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Der LiteBans-Ban wurde nicht gefunden."));
  }

  private BanActionRequest requireActionRequest(String id) {
    return this.actionRequests.stream()
      .filter(entry -> entry.id().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Die Ban-Aktion wurde nicht gefunden."));
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

  private void replaceActionRequest(BanActionRequest updated) {
    for (int index = 0; index < this.actionRequests.size(); index++) {
      if (this.actionRequests.get(index).id().equals(updated.id())) {
        this.actionRequests.set(index, updated);
        this.save();
        return;
      }
    }
    throw new IllegalArgumentException("Die Ban-Aktion wurde nicht gefunden.");
  }

  private void audit(String source, String banId, String publicId, String action, String actor, String message) {
    this.auditLog.add(new BanAuditEntry(
      UUID.randomUUID().toString(),
      source,
      banId,
      publicId,
      action,
      actor,
      message,
      Instant.now().toString()));
  }

  private void load() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      var data = this.backend.load("bans", this.storagePath, BanStoreData.class, null);
      if (data == null) {
        this.save();
        return;
      }

      if (data != null && data.bans() != null) {
        this.bans.clear();
        this.bans.addAll(data.bans());
      }
      if (data != null && data.liteBans() != null) {
        this.liteBans.clear();
        this.liteBans.addAll(data.liteBans());
      }
      if (data != null && data.actionRequests() != null) {
        this.actionRequests.clear();
        this.actionRequests.addAll(data.actionRequests());
      }
      if (data != null && data.auditLog() != null) {
        this.auditLog.clear();
        this.auditLog.addAll(data.auditLog());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Bans konnten nicht geladen werden.", exception);
    }
  }

  private void save() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      this.backend.save(
        "bans",
        this.storagePath,
        new BanStoreData(
          List.copyOf(this.bans),
          List.copyOf(this.liteBans),
          List.copyOf(this.actionRequests),
          List.copyOf(this.auditLog)));
    } catch (Exception exception) {
      throw new IllegalStateException("Bans konnten nicht gespeichert werden.", exception);
    }
  }

  private static String blankDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private static boolean isResolvedPublicId(String publicId, String databaseId) {
    if (publicId == null || publicId.isBlank()) {
      return false;
    }
    var value = publicId.trim();
    if (databaseId != null && value.equalsIgnoreCase(databaseId.trim())) {
      return false;
    }
    return !value.matches("\\d+");
  }
}
