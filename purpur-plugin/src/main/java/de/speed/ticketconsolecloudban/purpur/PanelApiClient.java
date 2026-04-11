package de.speed.ticketconsolecloudban.purpur;

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
import java.util.Arrays;
import java.util.List;

public final class PanelApiClient {

  private static final Gson GSON = new Gson();

  private final HttpClient httpClient;
  private final PurpurPluginConfig config;

  public PanelApiClient(PurpurPluginConfig config) {
    this.config = config;
    this.httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build();
  }

  public void syncPermissionSubjects(List<PermissionSubjectSnapshot> subjects) {
    var request = new JsonObject();
    request.addProperty("actor", "purpur-luckperms-sync");
    request.addProperty("serverId", this.config.serverId());
    request.add("subjects", GSON.toJsonTree(subjects));
    this.send("POST", "/api/permissions/sync", request);
  }

  public List<PanelPermissionAction> pendingPermissionActions() {
    var response = this.send("GET", "/api/permissions/actions?serverId=" + encode(this.config.serverId()), null);
    return Arrays.asList(GSON.fromJson(response, PanelPermissionAction[].class));
  }

  public void completePermissionAction(String actionId, boolean success, String message) {
    var request = new JsonObject();
    request.addProperty("success", success);
    request.addProperty("message", message);
    this.send("POST", "/api/permissions/actions/" + encode(actionId) + "/complete", request);
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

  private static String errorMessage(com.google.gson.JsonElement json, int statusCode) {
    if (json != null && json.isJsonObject() && json.getAsJsonObject().has("error")) {
      return json.getAsJsonObject().get("error").getAsString();
    }
    return "Panel-API HTTP " + statusCode;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
