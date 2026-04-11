package de.speed.ticketconsolecloudban.velocity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class PanelApiClient {

  private static final Gson GSON = new Gson();

  private final HttpClient httpClient;
  private final VelocityPluginConfig config;

  public PanelApiClient(VelocityPluginConfig config) {
    this.config = config;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();
  }

  public PanelTicket createTicket(String creatorName, UUID creatorUniqueId, String sourceServer, String message) {
    return this.createTicket(creatorName, creatorUniqueId, sourceServer, this.config.ticketDefaultCategory(), message);
  }

  public PanelTicket createTicket(String creatorName, UUID creatorUniqueId, String sourceServer, String category, String message) {
    var request = new JsonObject();
    request.addProperty("creatorName", creatorName);
    request.addProperty("creatorUniqueId", creatorUniqueId.toString());
    request.addProperty("category", category == null || category.isBlank() ? this.config.ticketDefaultCategory() : category);
    request.addProperty("priority", this.config.ticketDefaultPriority());
    request.addProperty("subject", shorten(message, 64));
    request.addProperty("content", message);
    request.addProperty("sourceServer", sourceServer);
    request.addProperty("serviceName", sourceServer);

    return this.toTicket(this.send("POST", "/api/tickets", request).getAsJsonObject());
  }

  public PanelTicket ticket(String id) {
    return this.toTicket(this.send("GET", "/api/tickets/" + encode(id), null).getAsJsonObject());
  }

  public List<PanelTicket> ownTickets(UUID uniqueId) {
    return this.tickets("/api/tickets?creatorUniqueId=" + encode(uniqueId.toString()));
  }

  public List<PanelTicket> openTickets() {
    return this.tickets("/api/tickets").stream()
      .filter(ticket -> ticket.status() == null || !"CLOSED".equalsIgnoreCase(ticket.status()))
      .toList();
  }

  public PanelTicket setTicketStatus(String id, String status, String actor) {
    var request = new JsonObject();
    request.addProperty("status", status);
    request.addProperty("actor", actor);
    return this.toTicket(this.send("POST", "/api/tickets/" + encode(id) + "/status", request).getAsJsonObject());
  }

  public PanelTicket assignTicket(String id, String assignedTo, String actor) {
    var request = new JsonObject();
    request.addProperty("assignedTo", assignedTo);
    request.addProperty("actor", actor);
    return this.toTicket(this.send("POST", "/api/tickets/" + encode(id) + "/assign", request).getAsJsonObject());
  }

  public PanelTicket addTicketComment(String id, String author, String message, boolean internal) {
    var request = new JsonObject();
    request.addProperty("author", author);
    request.addProperty("message", message);
    request.addProperty("internal", internal);
    return this.toTicket(this.send("POST", "/api/tickets/" + encode(id) + "/comments", request).getAsJsonObject());
  }

  public void syncLiteBans(List<LiteBanSnapshot> bans) {
    var request = new JsonObject();
    request.addProperty("actor", "velocity-sync");
    request.add("bans", GSON.toJsonTree(bans));
    this.send("POST", "/api/bans/litebans-sync", request);
  }

  public List<PanelBanAction> pendingBanActions() {
    var response = this.send("GET", "/api/bans/actions", null);
    return Arrays.asList(GSON.fromJson(response, PanelBanAction[].class));
  }

  public void completeBanAction(String actionId, boolean success, String message) {
    var request = new JsonObject();
    request.addProperty("success", success);
    request.addProperty("message", message);
    this.send("POST", "/api/bans/actions/" + encode(actionId) + "/complete", request);
  }

  public void syncPermissionSubjects(List<PermissionSubjectSnapshot> subjects) {
    var request = new JsonObject();
    request.addProperty("actor", "velocity-luckperms-sync");
    request.addProperty("serverId", this.config.luckPermsServerId());
    request.add("subjects", GSON.toJsonTree(subjects));
    this.send("POST", "/api/permissions/sync", request);
  }

  public List<PanelPermissionAction> pendingPermissionActions() {
    var response = this.send("GET", "/api/permissions/actions?serverId=" + encode(this.config.luckPermsServerId()), null);
    return Arrays.asList(GSON.fromJson(response, PanelPermissionAction[].class));
  }

  public void completePermissionAction(String actionId, boolean success, String message) {
    var request = new JsonObject();
    request.addProperty("success", success);
    request.addProperty("message", message);
    this.send("POST", "/api/permissions/actions/" + encode(actionId) + "/complete", request);
  }

  public List<PanelPlayerAction> pendingPlayerActions() {
    var response = this.send("GET", "/api/player-actions", null);
    return Arrays.asList(GSON.fromJson(response, PanelPlayerAction[].class));
  }

  public void completePlayerAction(String actionId, boolean success, String message) {
    var request = new JsonObject();
    request.addProperty("success", success);
    request.addProperty("message", message);
    this.send("POST", "/api/player-actions/" + encode(actionId) + "/complete", request);
  }

  private List<PanelTicket> tickets(String path) {
    var response = this.send("GET", path, null).getAsJsonArray();
    var tickets = new ArrayList<PanelTicket>();
    for (var element : response) {
      tickets.add(this.toTicket(element.getAsJsonObject()));
    }
    return List.copyOf(tickets);
  }

  private com.google.gson.JsonElement send(String method, String path, JsonObject body) {
    if (!this.config.hasPanelToken()) {
      throw new IllegalStateException("panel.api-token ist noch nicht gesetzt.");
    }

    try {
      var builder = HttpRequest.newBuilder()
        .uri(URI.create(this.config.panelUrl().replaceAll("/+$", "") + path))
        .timeout(Duration.ofSeconds(8))
        .header("Authorization", "Bearer " + this.config.panelApiToken())
        .header("Accept", "application/json");

      if (body == null) {
        builder.method(method, HttpRequest.BodyPublishers.noBody());
      } else {
        builder.header("Content-Type", "application/json");
        builder.method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
      }

      var response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      var responseBody = response.body() == null || response.body().isBlank() ? "{}" : response.body();
      var json = GSON.fromJson(responseBody, com.google.gson.JsonElement.class);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(errorMessage(json, response.statusCode()));
      }
      return json == null ? new JsonArray() : json;
    } catch (IOException exception) {
      throw new IllegalStateException("Panel-API ist nicht erreichbar: " + exception.getMessage(), exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Panel-API Anfrage wurde unterbrochen.", exception);
    }
  }

  private PanelTicket toTicket(JsonObject object) {
    var sourceServer = string(object, "sourceServer");
    if (sourceServer == null || sourceServer.isBlank()) {
      sourceServer = string(object, "serviceName");
    }
    var comments = new ArrayList<PanelTicketComment>();
    if (object.has("comments") && object.get("comments").isJsonArray()) {
      for (var element : object.getAsJsonArray("comments")) {
        if (element.isJsonObject()) {
          var comment = element.getAsJsonObject();
          comments.add(new PanelTicketComment(
            string(comment, "author"),
            string(comment, "message"),
            bool(comment, "internal"),
            string(comment, "createdAt")));
        }
      }
    }
    return new PanelTicket(
      string(object, "id"),
      string(object, "creatorName"),
      string(object, "creatorUniqueId"),
      string(object, "category"),
      string(object, "priority"),
      string(object, "status"),
      string(object, "subject"),
      string(object, "content"),
      string(object, "assignedTo"),
      sourceServer,
      string(object, "createdAt"),
      string(object, "updatedAt"),
      List.copyOf(comments));
  }

  private static String errorMessage(com.google.gson.JsonElement json, int statusCode) {
    if (json != null && json.isJsonObject() && json.getAsJsonObject().has("error")) {
      return json.getAsJsonObject().get("error").getAsString();
    }
    return "Panel-API HTTP " + statusCode;
  }

  private static String string(JsonObject object, String key) {
    return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
  }

  private static boolean bool(JsonObject object, String key) {
    return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
  }

  private static String shorten(String value, int maxLength) {
    var trimmed = value == null ? "" : value.trim();
    if (trimmed.length() <= maxLength) {
      return trimmed;
    }
    return trimmed.substring(0, maxLength - 3) + "...";
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
