package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.ticket.TicketComment;
import de.speed.ticketconsolecloudban.ticket.TicketAuditEntry;
import de.speed.ticketconsolecloudban.ticket.TicketEntry;
import de.speed.ticketconsolecloudban.ticket.TicketStoreData;
import eu.cloudnetservice.driver.document.DocumentFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class TicketStore {

  private final Path storagePath;
  private final List<TicketEntry> tickets = new ArrayList<>();
  private final List<TicketAuditEntry> auditLog = new ArrayList<>();

  public TicketStore(Path dataDirectory) {
    this.storagePath = dataDirectory.resolve("tickets.json");
    this.load();
  }

  public synchronized List<TicketEntry> list() {
    return this.tickets.stream()
      .sorted(Comparator.comparing(TicketEntry::updatedAt).reversed())
      .toList();
  }

  public synchronized List<TicketAuditEntry> auditLog() {
    return this.auditLog.stream()
      .sorted(Comparator.comparing(TicketAuditEntry::createdAt).reversed())
      .toList();
  }

  public synchronized TicketEntry create(
    String creatorName,
    String creatorUniqueId,
    String category,
    String priority,
    String subject,
    String content,
    String serviceName
  ) {
    var now = Instant.now().toString();
    var ticket = new TicketEntry(
      UUID.randomUUID().toString(),
      creatorName,
      creatorUniqueId,
      category,
      priority,
      "OPEN",
      subject,
      content,
      null,
      serviceName,
      now,
      now,
      List.of());
    this.tickets.add(ticket);
    this.audit(ticket.id(), "CREATE", creatorName, "Ticket auf " + nullDash(serviceName) + " erstellt: " + subject);
    this.save();
    return ticket;
  }

  public synchronized TicketEntry updateStatus(String id, String status, String actor) {
    var ticket = this.require(id);
    var now = Instant.now().toString();
    var updated = new TicketEntry(
      ticket.id(),
      ticket.creatorName(),
      ticket.creatorUniqueId(),
      ticket.category(),
      ticket.priority(),
      status,
      ticket.subject(),
      ticket.content(),
      ticket.assignedTo(),
      ticket.serviceName(),
      ticket.createdAt(),
      now,
      appendComment(ticket.comments(), new TicketComment(actor, "Status geaendert zu " + status, true, now)));
    this.audit(ticket.id(), "STATUS", actor, "Status geaendert zu " + status);
    this.replace(updated);
    return updated;
  }

  public synchronized TicketEntry assign(String id, String assignedTo, String actor) {
    var ticket = this.require(id);
    var now = Instant.now().toString();
    var updated = new TicketEntry(
      ticket.id(),
      ticket.creatorName(),
      ticket.creatorUniqueId(),
      ticket.category(),
      ticket.priority(),
      ticket.status(),
      ticket.subject(),
      ticket.content(),
      assignedTo,
      ticket.serviceName(),
      ticket.createdAt(),
      now,
      appendComment(ticket.comments(), new TicketComment(actor, "Zugewiesen an " + assignedTo, true, now)));
    this.audit(ticket.id(), "ASSIGN", actor, "Zugewiesen an " + assignedTo);
    this.replace(updated);
    return updated;
  }

  public synchronized TicketEntry addComment(String id, String author, String message, boolean internal) {
    var ticket = this.require(id);
    var now = Instant.now().toString();
    var updated = new TicketEntry(
      ticket.id(),
      ticket.creatorName(),
      ticket.creatorUniqueId(),
      ticket.category(),
      ticket.priority(),
      ticket.status(),
      ticket.subject(),
      ticket.content(),
      ticket.assignedTo(),
      ticket.serviceName(),
      ticket.createdAt(),
      now,
      appendComment(ticket.comments(), new TicketComment(author, message, internal, now)));
    this.audit(ticket.id(), internal ? "INTERNAL_COMMENT" : "COMMENT", author, message);
    this.replace(updated);
    return updated;
  }

  public synchronized TicketEntry get(String id) {
    return this.require(id);
  }

  private TicketEntry require(String id) {
    return this.tickets.stream()
      .filter(ticket -> ticket.id().equals(id))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Das Ticket wurde nicht gefunden."));
  }

  private void replace(TicketEntry updated) {
    for (int index = 0; index < this.tickets.size(); index++) {
      if (this.tickets.get(index).id().equals(updated.id())) {
        this.tickets.set(index, updated);
        this.save();
        return;
      }
    }
    throw new IllegalArgumentException("Das Ticket wurde nicht gefunden.");
  }

  private List<TicketComment> appendComment(List<TicketComment> comments, TicketComment comment) {
    var updated = new ArrayList<TicketComment>();
    if (comments != null) {
      updated.addAll(comments);
    }
    updated.add(comment);
    return List.copyOf(updated);
  }

  private void audit(String ticketId, String action, String actor, String message) {
    this.auditLog.add(new TicketAuditEntry(
      UUID.randomUUID().toString(),
      ticketId,
      action,
      actor,
      message,
      Instant.now().toString()));
  }

  private void load() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      if (Files.notExists(this.storagePath)) {
        this.save();
        return;
      }

      var document = DocumentFactory.json().parse(this.storagePath);
      var data = document.toInstanceOf(TicketStoreData.class);
      if (data != null && data.tickets() != null) {
        this.tickets.clear();
        this.tickets.addAll(data.tickets());
      }
      if (data != null && data.auditLog() != null) {
        this.auditLog.clear();
        this.auditLog.addAll(data.auditLog());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Tickets konnten nicht geladen werden.", exception);
    }
  }

  private void save() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      DocumentFactory.json()
        .newDocument()
        .appendTree(new TicketStoreData(List.copyOf(this.tickets), List.copyOf(this.auditLog)))
        .writeTo(this.storagePath);
    } catch (Exception exception) {
      throw new IllegalStateException("Tickets konnten nicht gespeichert werden.", exception);
    }
  }

  private static String nullDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }
}
