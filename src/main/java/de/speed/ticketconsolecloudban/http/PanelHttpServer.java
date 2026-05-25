package de.speed.ticketconsolecloudban.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.speed.ticketconsolecloudban.auth.PanelPermission;
import de.speed.ticketconsolecloudban.auth.PanelPrincipal;
import de.speed.ticketconsolecloudban.auth.PanelSecurityService;
import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.quest.CraftplayQuestEditorClient;
import de.speed.ticketconsolecloudban.service.CloudNetFacade;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PanelHttpServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(PanelHttpServer.class);

  private final PanelConfiguration configuration;
  private final CloudNetFacade facade;
  private final PanelSecurityService security;
  private final CraftplayQuestEditorClient questEditorClient;

  private HttpServer server;
  private ExecutorService executor;

  public PanelHttpServer(
    PanelConfiguration configuration,
    CloudNetFacade facade,
    PanelSecurityService security,
    PanelSettingsStore settingsStore
  ) {
    this.configuration = configuration;
    this.facade = facade;
    this.security = security;
    this.questEditorClient = new CraftplayQuestEditorClient(configuration, settingsStore);
  }

  public void start() {
    if (this.server != null) {
      return;
    }

    try {
      this.server = HttpServer.create(
        new InetSocketAddress(this.configuration.bindHost(), this.configuration.bindPort()),
        0);
      this.executor = Executors.newVirtualThreadPerTaskExecutor();
      this.server.setExecutor(this.executor);
      this.server.createContext("/", this::handleRequest);
      this.server.start();
    } catch (IOException exception) {
      throw new IllegalStateException("Der HTTP-Server konnte nicht gestartet werden.", exception);
    }
  }

  public void stop() {
    if (this.server != null) {
      this.server.stop(0);
      this.server = null;
    }
    if (this.executor != null) {
      this.executor.shutdownNow();
      this.executor = null;
    }
  }

  private void handleRequest(HttpExchange exchange) throws IOException {
    HttpExchangeUtils.allowCommonHeaders(exchange.getResponseHeaders());

    if (HttpExchangeUtils.matchesMethod(exchange, "OPTIONS")) {
      HttpExchangeUtils.sendNoContent(exchange);
      return;
    }

    try {
      var segments = HttpExchangeUtils.pathSegments(exchange);
      if (!segments.isEmpty() && "api".equals(segments.get(0))) {
        this.handleApi(exchange, segments);
        return;
      }

      this.handleStatic(exchange, segments);
    } catch (IllegalArgumentException exception) {
      HttpExchangeUtils.writeJson(exchange, 400, new HttpExchangeUtils.ApiError(exception.getMessage()));
    } catch (Exception exception) {
      LOGGER.error("Unbehandelter Fehler im TicketConsoleCloudBan HTTP-Handler", exception);
      HttpExchangeUtils.writeJson(exchange, 500, new HttpExchangeUtils.ApiError("Interner Serverfehler"));
    }
  }

  private void handleApi(HttpExchange exchange, List<String> segments) throws IOException {
    if (segments.size() == 2 && "meta".equals(segments.get(1)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.meta());
      return;
    }

    if (segments.size() == 3
      && "auth".equals(segments.get(1))
      && "login".equals(segments.get(2))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.security.login(HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 4
      && "auth".equals(segments.get(1))
      && "2fa".equals(segments.get(2))
      && "verify".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.security.verifyTwoFactor(HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 4
      && "auth".equals(segments.get(1))
      && "password-reset".equals(segments.get(2))
      && "request".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.security.requestPasswordReset(HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 4
      && "auth".equals(segments.get(1))
      && "password-reset".equals(segments.get(2))
      && "complete".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.security.completePasswordReset(HttpExchangeUtils.readJson(exchange)));
      return;
    }

    var principal = this.security.authenticate(HttpExchangeUtils.bearerToken(exchange), this.configuration);
    if (principal == null) {
      HttpExchangeUtils.writeJson(exchange, 401, new HttpExchangeUtils.ApiError("Nicht autorisiert"));
      return;
    }

    if (segments.size() == 3 && "auth".equals(segments.get(1))) {
      var action = segments.get(2);
      if ("session".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.security.currentSession(principal));
        return;
      }
      if ("logout".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        this.security.logout(principal);
        HttpExchangeUtils.sendNoContent(exchange);
        return;
      }
      if ("profile".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "PUT")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.security.updateOwnProfile(principal, HttpExchangeUtils.readJson(exchange)));
        return;
      }
      if ("password".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.security.changeOwnPassword(principal, HttpExchangeUtils.readJson(exchange)));
        return;
      }
    }

    if (segments.size() == 4
      && "auth".equals(segments.get(1))
      && "2fa".equals(segments.get(2))
      && "setup".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.security.prepareTotpSetup(principal));
      return;
    }

    if (segments.size() >= 2 && "security".equals(segments.get(1))) {
      this.handleSecurityApi(exchange, segments, principal);
      return;
    }

    if (segments.size() >= 2 && "permissions".equals(segments.get(1))) {
      this.handlePermissionApi(exchange, segments, principal);
      return;
    }

    if (segments.size() >= 2 && "settings".equals(segments.get(1))) {
      this.handleSettingsApi(exchange, segments, principal);
      return;
    }

    if (segments.size() >= 2 && "quest-editor".equals(segments.get(1))) {
      this.handleQuestEditorApi(exchange, segments, principal);
      return;
    }

    if (segments.size() == 2 && "overview".equals(segments.get(1)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_VIEW)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.overview());
      return;
    }

    if (segments.size() == 3
      && "cloudnet".equals(segments.get(1))
      && "console".equals(segments.get(2))
      && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_CONSOLE)) {
        return;
      }
      var limit = 200;
      var query = exchange.getRequestURI().getQuery();
      if (query != null && query.startsWith("limit=")) {
        limit = Integer.parseInt(query.substring("limit=".length()));
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.cloudNetConsole(limit));
      return;
    }

    if (segments.size() == 3
      && "cloudnet".equals(segments.get(1))
      && "command".equals(segments.get(2))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_COMMAND)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.runCloudNetCommand(HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 2 && "tasks".equals(segments.get(1))) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_VIEW)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.listTasks());
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 201, this.facade.createTask(HttpExchangeUtils.readJson(exchange)));
        return;
      }
    }

    if (segments.size() == 3 && "tasks".equals(segments.get(1))) {
      var taskName = segments.get(2);
      if (HttpExchangeUtils.matchesMethod(exchange, "PUT")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.updateTask(taskName, HttpExchangeUtils.readJson(exchange)));
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "DELETE")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
          return;
        }
        this.facade.deleteTask(taskName);
        HttpExchangeUtils.sendNoContent(exchange);
        return;
      }
    }

    if (segments.size() == 2 && "services".equals(segments.get(1))) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requireAnyPermission(exchange, principal, PanelPermission.CLOUDNET_VIEW, PanelPermission.CLOUDNET_CONSOLE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.listServices());
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 201, this.facade.createServices(HttpExchangeUtils.readJson(exchange)));
        return;
      }
    }

    if (segments.size() == 4 && "services".equals(segments.get(1))) {
      var serviceName = segments.get(2);
      var action = segments.get(3);

      if ("console".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_CONSOLE)) {
          return;
        }
        var limit = 150;
        var query = exchange.getRequestURI().getQuery();
        if (query != null && query.startsWith("limit=")) {
          limit = Integer.parseInt(query.substring("limit=".length()));
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.console(serviceName, limit));
        return;
      }

      if ("command".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_COMMAND)) {
          return;
        }
        this.facade.runServiceCommand(serviceName, HttpExchangeUtils.readJson(exchange));
        HttpExchangeUtils.sendNoContent(exchange);
        return;
      }

      if ("start".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.startService(serviceName));
        return;
      }
      if ("stop".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.stopService(serviceName));
        return;
      }
      if ("restart".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.restartService(serviceName));
        return;
      }
    }

    if (segments.size() == 3 && "services".equals(segments.get(1)) && HttpExchangeUtils.matchesMethod(exchange, "DELETE")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_MANAGE)) {
        return;
      }
      this.facade.deleteService(segments.get(2));
      HttpExchangeUtils.sendNoContent(exchange);
      return;
    }

    if (segments.size() == 2 && "nodes".equals(segments.get(1)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.CLOUDNET_VIEW)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.listNodes());
      return;
    }

    if (segments.size() == 2 && "tickets".equals(segments.get(1))) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_VIEW)) {
          return;
        }
        var query = HttpExchangeUtils.queryParameters(exchange);
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.listTickets(
          query.get("creatorUniqueId"),
          query.get("creatorName"),
          query.get("status")));
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_CREATE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 201, this.facade.createTicket(HttpExchangeUtils.readJson(exchange)));
        return;
      }
    }

    if (segments.size() == 3
      && "tickets".equals(segments.get(1))
      && "audit".equals(segments.get(2))
      && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_VIEW)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.ticketAuditLog());
      return;
    }

    if (segments.size() == 3 && "tickets".equals(segments.get(1)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_VIEW)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.getTicket(segments.get(2)));
      return;
    }

    if (segments.size() == 4 && "tickets".equals(segments.get(1))) {
      var ticketId = segments.get(2);
      var action = segments.get(3);

      if ("status".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.updateTicketStatus(ticketId, HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
      if ("assign".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.assignTicket(ticketId, HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
      if (this.isTicketCommentAction(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_VIEW)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.getTicket(ticketId).comments());
        return;
      }
      if ("comments".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.addTicketComment(ticketId, HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
      if (("reply".equals(action) || "replies".equals(action)) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.addTicketComment(ticketId, HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
    }

    if (segments.size() == 2 && "player-actions".equals(segments.get(1)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_MANAGE)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.pendingPlayerActions());
      return;
    }

    if (segments.size() == 3
      && "player-actions".equals(segments.get(1))
      && "teleport".equals(segments.get(2))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_MANAGE)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 202, this.facade.requestTeleportToPlayer(HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
      return;
    }

    if (segments.size() == 4
      && "player-actions".equals(segments.get(1))
      && "complete".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.TICKETS_MANAGE)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.completePlayerAction(segments.get(2), HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 2 && "bans".equals(segments.get(1))) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_VIEW)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.listBans());
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 201, this.facade.createBan(HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
    }

    if (segments.size() == 3 && "bans".equals(segments.get(1))) {
      var action = segments.get(2);
      if ("litebans".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_VIEW)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.listLiteBans());
        return;
      }
      if ("litebans-sync".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.syncLiteBans(HttpExchangeUtils.readJson(exchange)));
        return;
      }
      if ("actions".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.pendingBanActions());
        return;
      }
      if ("audit".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_VIEW)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.banAuditLog());
        return;
      }
    }

    if (segments.size() == 5 && "bans".equals(segments.get(1)) && "litebans".equals(segments.get(2))) {
      var banId = segments.get(3);
      var action = segments.get(4);
      if ("unban".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 202, this.facade.requestLiteBanUnban(banId, HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
      if ("extend".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 202, this.facade.requestLiteBanExtend(banId, HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
    }

    if (segments.size() == 5
      && "bans".equals(segments.get(1))
      && "actions".equals(segments.get(2))
      && "complete".equals(segments.get(4))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.completeBanAction(segments.get(3), HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 2 && "ban-appeals".equals(segments.get(1)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.BANS_VIEW)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.listBanAppeals());
      return;
    }

    if (segments.size() == 5
      && "ban-appeals".equals(segments.get(1))
      && "attachments".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.BANS_VIEW)) {
        return;
      }
      var download = this.facade.downloadBanAppealAttachment(segments.get(2), segments.get(4));
      exchange.getResponseHeaders().set("Content-Disposition", this.inlineContentDisposition(download.fileName()));
      exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
      HttpExchangeUtils.writeBinary(exchange, 200, download.bytes(), download.contentType());
      return;
    }

    if (segments.size() == 4
      && "ban-appeals".equals(segments.get(1))
      && "status".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
        return;
      }
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.updateBanAppealStatus(segments.get(2), HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
      return;
    }

    if (segments.size() == 4 && "bans".equals(segments.get(1))) {
      var banId = segments.get(2);
      var action = segments.get(3);

      if ("deactivate".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        if (!this.requirePermission(exchange, principal, PanelPermission.BANS_MANAGE)) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.deactivateBan(banId, HttpExchangeUtils.readJson(exchange), this.actorName(principal)));
        return;
      }
    }

    HttpExchangeUtils.writeJson(exchange, 404, new HttpExchangeUtils.ApiError("API-Endpunkt nicht gefunden"));
  }

  private void handleQuestEditorApi(HttpExchange exchange, List<String> segments, PanelPrincipal principal) throws IOException {
    if (!this.requirePermission(exchange, principal, PanelPermission.QUEST_EDITOR_VIEW)) {
      return;
    }

    if (segments.size() == 3 && "config".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.questEditorClient.configView());
      return;
    }

    if (segments.size() == 3 && "servers".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.questEditorClient.servers());
      return;
    }

    if (segments.size() == 3 && "status".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      if (!this.questEditorClient.enabled()) {
        HttpExchangeUtils.writeJson(exchange, 200, this.questEditorClient.configView());
        return;
      }
      this.proxyQuestEditor(exchange, "/status");
      return;
    }

    if (segments.size() == 3 && "schema".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      this.proxyQuestEditor(exchange, "/schema");
      return;
    }

    if (segments.size() == 3 && "categories".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      this.proxyQuestEditor(exchange, "/categories");
      return;
    }

    if (segments.size() == 3 && "quests".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      this.proxyQuestEditor(exchange, "/quests");
      return;
    }

    if (segments.size() == 4 && "quests".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      this.proxyQuestEditor(exchange, "/quests/" + CraftplayQuestEditorClient.pathSegment(segments.get(3)));
      return;
    }

    if (segments.size() == 5
      && "raw".equals(segments.get(2))
      && "quests".equals(segments.get(3))
      && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      this.proxyQuestEditor(exchange, "/raw/quests/" + CraftplayQuestEditorClient.pathSegment(segments.get(4)));
      return;
    }

    if (segments.size() >= 4) {
      var serverId = segments.get(2);
      var action = segments.get(3);

      if (segments.size() == 4 && "status".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        this.proxyQuestEditor(exchange, serverId, "/status");
        return;
      }

      if (segments.size() == 4 && "schema".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        this.proxyQuestEditor(exchange, serverId, "/schema");
        return;
      }

      if (segments.size() == 4 && "categories".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        this.proxyQuestEditor(exchange, serverId, "/categories");
        return;
      }

      if (segments.size() == 4 && "quests".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        this.proxyQuestEditor(exchange, serverId, "/quests");
        return;
      }

      if (segments.size() == 5 && "quests".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        this.proxyQuestEditor(exchange, serverId, "/quests/" + CraftplayQuestEditorClient.pathSegment(segments.get(4)));
        return;
      }

      if (segments.size() == 6
        && "raw".equals(action)
        && "quests".equals(segments.get(4))
        && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        this.proxyQuestEditor(exchange, serverId, "/raw/quests/" + CraftplayQuestEditorClient.pathSegment(segments.get(5)));
        return;
      }
    }

    HttpExchangeUtils.writeJson(exchange, 404, new HttpExchangeUtils.ApiError("Quest-Editor-Endpunkt nicht gefunden"));
  }

  private void proxyQuestEditor(HttpExchange exchange, String remotePath) throws IOException {
    var response = this.questEditorClient.getFirstEnabled(remotePath);
    HttpExchangeUtils.writeText(exchange, response.statusCode(), response.body(), "application/json; charset=utf-8");
  }

  private void proxyQuestEditor(HttpExchange exchange, String serverId, String remotePath) throws IOException {
    var response = this.questEditorClient.get(serverId, remotePath);
    HttpExchangeUtils.writeText(exchange, response.statusCode(), response.body(), "application/json; charset=utf-8");
  }

  private void handleSecurityApi(HttpExchange exchange, List<String> segments, PanelPrincipal principal) throws IOException {
    if (!this.requirePermission(exchange, principal, PanelPermission.USERS_MANAGE)) {
      return;
    }

    if (segments.size() == 3 && "permissions".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      HttpExchangeUtils.writeJson(exchange, 200, PanelPermission.catalog());
      return;
    }

    if (segments.size() == 3 && "users".equals(segments.get(2))) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.security.listUsers());
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        HttpExchangeUtils.writeJson(exchange, 201, this.security.createUser(HttpExchangeUtils.readJson(exchange)));
        return;
      }
    }

    if (segments.size() == 4 && "users".equals(segments.get(2))) {
      var username = segments.get(3);
      if (HttpExchangeUtils.matchesMethod(exchange, "PUT")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.security.updateUser(username, HttpExchangeUtils.readJson(exchange)));
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "DELETE")) {
        this.security.deleteUser(username);
        HttpExchangeUtils.sendNoContent(exchange);
        return;
      }
    }

    if (segments.size() == 3 && "groups".equals(segments.get(2))) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.security.listGroups());
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        HttpExchangeUtils.writeJson(exchange, 201, this.security.upsertGroup(HttpExchangeUtils.readJson(exchange)));
        return;
      }
    }

    if (segments.size() == 4 && "groups".equals(segments.get(2))) {
      var groupName = segments.get(3);
      if (HttpExchangeUtils.matchesMethod(exchange, "PUT")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.security.updateGroup(groupName, HttpExchangeUtils.readJson(exchange)));
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "DELETE")) {
        this.security.deleteGroup(groupName);
        HttpExchangeUtils.sendNoContent(exchange);
        return;
      }
    }

    HttpExchangeUtils.writeJson(exchange, 404, new HttpExchangeUtils.ApiError("Security-Endpunkt nicht gefunden"));
  }

  private void handlePermissionApi(HttpExchange exchange, List<String> segments, PanelPrincipal principal) throws IOException {
    if (!this.requireAnyPermission(exchange, principal, PanelPermission.PROXY_PERMISSIONS_MANAGE, PanelPermission.SERVER_PERMISSIONS_MANAGE)) {
      return;
    }

    if (segments.size() == 3 && "subjects".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.listPermissionSubjects());
      return;
    }

    if (segments.size() == 3 && "sync".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.syncPermissionSubjects(HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 3 && "actions".equals(segments.get(2))) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        var query = HttpExchangeUtils.queryParameters(exchange);
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.pendingPermissionActions(query.get("serverId")));
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        var request = HttpExchangeUtils.readJson(exchange);
        if (!this.requirePermissionForServer(exchange, principal, request.getString("serverId"))) {
          return;
        }
        HttpExchangeUtils.writeJson(exchange, 202, this.facade.requestPermissionAction(request));
        return;
      }
    }

    if (segments.size() == 5
      && "actions".equals(segments.get(2))
      && "complete".equals(segments.get(4))
      && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.completePermissionAction(segments.get(3), HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 3 && "audit".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.permissionAuditLog());
      return;
    }

    HttpExchangeUtils.writeJson(exchange, 404, new HttpExchangeUtils.ApiError("Permission-Endpunkt nicht gefunden"));
  }

  private void handleSettingsApi(HttpExchange exchange, List<String> segments, PanelPrincipal principal) throws IOException {
    if (!this.requirePermission(exchange, principal, PanelPermission.SETTINGS_MANAGE)) {
      return;
    }

    if (segments.size() == 2) {
      if (HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.settings());
        return;
      }
      if (HttpExchangeUtils.matchesMethod(exchange, "PUT")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.updateSettings(HttpExchangeUtils.readJson(exchange)));
        return;
      }
    }

    if (segments.size() == 3 && "test-mail".equals(segments.get(2)) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 200, this.facade.sendTestMail(HttpExchangeUtils.readJson(exchange)));
      return;
    }

    if (segments.size() == 4 && "onedrive".equals(segments.get(2))) {
      var action = segments.get(3);
      if ("device-code".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.startOneDriveDeviceCode());
        return;
      }
      if ("complete".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        var result = this.facade.completeOneDriveDeviceCode(HttpExchangeUtils.readJson(exchange));
        HttpExchangeUtils.writeJson(exchange, result.connected() ? 200 : 202, result);
        return;
      }
      if ("disconnect".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "POST")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.disconnectOneDrive());
        return;
      }
      if ("folders".equals(action) && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.facade.oneDriveFolders());
        return;
      }
    }

    HttpExchangeUtils.writeJson(exchange, 404, new HttpExchangeUtils.ApiError("Settings-Endpunkt nicht gefunden"));
  }

  private boolean requirePermissionForServer(HttpExchange exchange, PanelPrincipal principal, String serverId) throws IOException {
    var permission = serverId == null || serverId.isBlank() || "proxy".equalsIgnoreCase(serverId)
      ? PanelPermission.PROXY_PERMISSIONS_MANAGE
      : PanelPermission.SERVER_PERMISSIONS_MANAGE;
    return this.requirePermission(exchange, principal, permission);
  }

  private boolean isTicketCommentAction(String action) {
    return "comments".equals(action) || "replies".equals(action) || "answers".equals(action);
  }

  private boolean requireAnyPermission(HttpExchange exchange, PanelPrincipal principal, String... permissions) throws IOException {
    for (var permission : permissions) {
      if (principal.hasPermission(permission)) {
        return true;
      }
    }
    HttpExchangeUtils.writeJson(exchange, 403, new HttpExchangeUtils.ApiError("Keine Berechtigung für diese Funktion"));
    return false;
  }

  private boolean requirePermission(HttpExchange exchange, PanelPrincipal principal, String permission) throws IOException {
    if (!principal.hasPermission(permission)) {
      HttpExchangeUtils.writeJson(exchange, 403, new HttpExchangeUtils.ApiError("Keine Berechtigung für diese Funktion"));
      return false;
    }
    return true;
  }

  private String actorName(PanelPrincipal principal) {
    if (principal == null) {
      return "Panel";
    }
    if (principal.displayName() != null && !principal.displayName().isBlank()) {
      return principal.displayName();
    }
    return principal.username() == null || principal.username().isBlank() ? "Panel" : principal.username();
  }

  private String inlineContentDisposition(String fileName) {
    var safeName = fileName == null || fileName.isBlank()
      ? "beweisdatei"
      : fileName.replace("\r", "").replace("\n", "").replace("\"", "'");
    var encodedName = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
    return "inline; filename=\"" + safeName + "\"; filename*=UTF-8''" + encodedName;
  }

  private void handleStatic(HttpExchange exchange, List<String> segments) throws IOException {
    if (segments.isEmpty()) {
      this.writeResource(exchange, "web/index.html", "text/html; charset=utf-8");
      return;
    }

    if (segments.size() == 1 && "app.js".equals(segments.get(0))) {
      this.writeResource(exchange, "web/app.js", "application/javascript; charset=utf-8");
      return;
    }

    if (segments.size() == 1 && "styles.css".equals(segments.get(0))) {
      this.writeResource(exchange, "web/styles.css", "text/css; charset=utf-8");
      return;
    }

    HttpExchangeUtils.writeText(exchange, 404, "Nicht gefunden", "text/plain; charset=utf-8");
  }

  private void writeResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
    try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (stream == null) {
        HttpExchangeUtils.writeText(exchange, 404, "Nicht gefunden", "text/plain; charset=utf-8");
        return;
      }
      HttpExchangeUtils.writeBinary(exchange, 200, stream.readAllBytes(), contentType);
    }
  }
}
