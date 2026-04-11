package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.permission.PermissionActionRequest;
import de.speed.ticketconsolecloudban.permission.PermissionAuditEntry;
import de.speed.ticketconsolecloudban.permission.PermissionBridgeStoreData;
import de.speed.ticketconsolecloudban.permission.PermissionSubject;
import eu.cloudnetservice.driver.document.DocumentFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class PermissionBridgeStore {

  private final Path storagePath;
  private final PanelDataBackend backend;
  private final List<PermissionSubject> subjects = new ArrayList<>();
  private final List<PermissionActionRequest> actionRequests = new ArrayList<>();
  private final List<PermissionAuditEntry> auditLog = new ArrayList<>();

  public PermissionBridgeStore(Path dataDirectory) {
    this(dataDirectory, new LocalPanelDataBackend());
  }

  public PermissionBridgeStore(Path dataDirectory, PanelDataBackend backend) {
    this.storagePath = dataDirectory.resolve("permissions.json");
    this.backend = backend;
    this.load();
  }

  public synchronized List<PermissionSubject> listSubjects() {
    return this.subjects.stream()
      .sorted(Comparator.comparing((PermissionSubject subject) -> normalizedServer(subject.serverId()))
        .thenComparing(PermissionSubject::type)
        .thenComparing(PermissionSubject::name))
      .toList();
  }

  public synchronized List<PermissionAuditEntry> auditLog() {
    return this.auditLog.stream()
      .sorted(Comparator.comparing(PermissionAuditEntry::createdAt).reversed())
      .toList();
  }

  public synchronized List<PermissionActionRequest> pendingActionRequests() {
    return this.pendingActionRequests(null);
  }

  public synchronized List<PermissionActionRequest> pendingActionRequests(String serverId) {
    var normalizedServer = blankToNull(serverId);
    return this.actionRequests.stream()
      .filter(request -> "PENDING".equals(request.status()))
      .filter(request -> normalizedServer == null || normalizedServer(request.serverId()).equalsIgnoreCase(normalizedServer))
      .sorted(Comparator.comparing(PermissionActionRequest::createdAt))
      .toList();
  }

  public synchronized List<PermissionSubject> syncSubjects(List<PermissionSubject> incoming, String actor, String serverIdFallback) {
    var now = Instant.now().toString();
    var byKey = new LinkedHashMap<String, PermissionSubject>();
    for (var subject : this.subjects) {
      byKey.put(key(subject.serverId(), subject.type(), subject.id()), subject);
    }

    for (var subject : incoming) {
      if (subject == null || subject.type() == null || subject.id() == null) {
        continue;
      }
      var serverId = normalizedServer(subject.serverId() == null ? serverIdFallback : subject.serverId());
      var synced = new PermissionSubject(
        serverId,
        subject.type().toUpperCase(),
        subject.id(),
        subject.name() == null || subject.name().isBlank() ? subject.id() : subject.name(),
        subject.permissions() == null ? List.of() : subject.permissions(),
        subject.parents() == null ? List.of() : subject.parents(),
        subject.source() == null || subject.source().isBlank() ? "velocity" : subject.source(),
        now);
      var previous = byKey.put(key(synced.serverId(), synced.type(), synced.id()), synced);
      if (previous == null) {
        this.audit(synced.serverId(), "SYNC_CREATE", synced.type(), synced.id(), actor, "LuckPerms Subject synchronisiert");
      }
    }

    this.subjects.clear();
    this.subjects.addAll(byKey.values());
    this.save();
    return this.listSubjects();
  }

  public synchronized PermissionActionRequest requestAction(
    String serverId,
    String action,
    String subjectType,
    String subjectId,
    String permission,
    String parent,
    Boolean value,
    String actor
  ) {
    var normalizedServer = normalizedServer(serverId);
    var normalizedAction = require(action, "Aktion").toUpperCase();
    var normalizedType = require(subjectType, "Subject-Typ").toUpperCase();
    var normalizedSubject = require(subjectId, "Subject").trim();
    var request = new PermissionActionRequest(
      UUID.randomUUID().toString(),
      normalizedServer,
      normalizedAction,
      normalizedType,
      normalizedSubject,
      blankToNull(permission),
      blankToNull(parent),
      value,
      blankDefault(actor, "Panel"),
      "PENDING",
      Instant.now().toString(),
      null,
      null);
    this.actionRequests.add(request);
    this.audit(
      normalizedServer,
      normalizedAction + "_REQUESTED",
      normalizedType,
      normalizedSubject,
      request.actor(),
      describe(request.permission() == null ? request.parent() : request.permission(), request.value()));
    this.save();
    return request;
  }

  public synchronized PermissionActionRequest completeActionRequest(String id, boolean success, String message) {
    var existing = this.actionRequests.stream()
      .filter(request -> request.id().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Permission-Aktion wurde nicht gefunden."));
    var updated = new PermissionActionRequest(
      existing.id(),
      normalizedServer(existing.serverId()),
      existing.action(),
      existing.subjectType(),
      existing.subjectId(),
      existing.permission(),
      existing.parent(),
      existing.value(),
      existing.actor(),
      success ? "COMPLETED" : "FAILED",
      existing.createdAt(),
      Instant.now().toString(),
      message);

    for (int index = 0; index < this.actionRequests.size(); index++) {
      if (this.actionRequests.get(index).id().equals(updated.id())) {
        this.actionRequests.set(index, updated);
        break;
      }
    }
    this.audit(normalizedServer(existing.serverId()), existing.action() + "_" + updated.status(), existing.subjectType(), existing.subjectId(), existing.actor(), message);
    this.save();
    return updated;
  }

  private void audit(String serverId, String action, String subjectType, String subjectId, String actor, String message) {
    this.auditLog.add(new PermissionAuditEntry(
      UUID.randomUUID().toString(),
      normalizedServer(serverId),
      action,
      subjectType,
      subjectId,
      actor,
      message,
      Instant.now().toString()));
  }

  private void load() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      var data = this.backend.load("permissions", this.storagePath, PermissionBridgeStoreData.class, null);
      if (data == null) {
        this.save();
        return;
      }
      if (data != null && data.subjects() != null) {
        this.subjects.clear();
        this.subjects.addAll(data.subjects());
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
      throw new IllegalStateException("Permission-Bridge konnte nicht geladen werden.", exception);
    }
  }

  private void save() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      this.backend.save(
        "permissions",
        this.storagePath,
        new PermissionBridgeStoreData(
          List.copyOf(this.subjects),
          List.copyOf(this.actionRequests),
          List.copyOf(this.auditLog)));
    } catch (Exception exception) {
      throw new IllegalStateException("Permission-Bridge konnte nicht gespeichert werden.", exception);
    }
  }

  private static String key(String serverId, String type, String id) {
    return normalizedServer(serverId) + ":" + type.toUpperCase() + ":" + id;
  }

  private static String require(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " ist erforderlich.");
    }
    return value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String blankDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static String normalizedServer(String serverId) {
    return serverId == null || serverId.isBlank() ? "proxy" : serverId.trim();
  }

  private static String describe(String node, Boolean value) {
    if (value == null || value) {
      return node;
    }
    return node + " = false";
  }
}
