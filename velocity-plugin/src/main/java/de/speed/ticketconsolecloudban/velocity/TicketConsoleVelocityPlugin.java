package de.speed.ticketconsolecloudban.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

@Plugin(
  id = "ticketconsolecloudban-velocity",
  name = "TicketConsoleCloudBan Velocity",
  version = "0.1.0-SNAPSHOT",
  authors = {"speed"},
  dependencies = {
    @Dependency(id = "luckperms", optional = true),
    @Dependency(id = "litebans", optional = true)
  }
)
public final class TicketConsoleVelocityPlugin {

  private static final String PREFIX = "[Panel] ";
  private static final List<String> DEFAULT_TICKET_CATEGORIES = List.of("SUPPORT", "BUG", "MELDEN", "SONSTIGES");
  private static final long TICKET_CATEGORY_CACHE_MILLIS = 30_000L;

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  private VelocityPluginConfig config;
  private PanelApiClient panelApi;
  private LuckPermsBridge luckPermsBridge;
  private LiteBansBridge liteBansBridge;
  private LiteBansPunishmentBridgeServer punishmentBridgeServer;
  private volatile List<String> ticketCategories = DEFAULT_TICKET_CATEGORIES;
  private volatile long ticketCategoriesLoadedAt;

  @Inject
  public TicketConsoleVelocityPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onInitialize(ProxyInitializeEvent event) {
    this.reload();
    this.registerCommands();
    this.scheduleLiteBansBridge();
    this.scheduleLuckPermsBridge();
    this.schedulePanelActions();
    this.logger.info("TicketConsoleCloudBan Velocity Plugin gestartet.");
  }

  @Subscribe
  public void onShutdown(ProxyShutdownEvent event) {
    if (this.punishmentBridgeServer != null) {
      this.punishmentBridgeServer.stop();
      this.punishmentBridgeServer = null;
    }
    this.executor.shutdownNow();
  }

  @Subscribe
  public void onLogin(LoginEvent event) {
    if (!this.config.liteBansEnabled() || !this.config.liteBansJoinCheck() || !this.liteBansBridge.available()) {
      return;
    }

    var player = event.getPlayer();
    var address = player.getRemoteAddress() == null || player.getRemoteAddress().getAddress() == null
      ? null
      : player.getRemoteAddress().getAddress().getHostAddress();
    var ban = this.liteBansBridge.activeBan(player.getUniqueId(), address, this.config.liteBansServerScope());
    if (ban != null) {
      event.setResult(ResultedEvent.ComponentResult.denied(Component.text(this.liteBansBridge.describe(ban), NamedTextColor.RED)));
    }
  }

  private void reload() {
    if (this.punishmentBridgeServer != null) {
      this.punishmentBridgeServer.stop();
      this.punishmentBridgeServer = null;
    }

    this.config = VelocityPluginConfig.load(this.dataDirectory);
    this.panelApi = new PanelApiClient(this.config);
    this.luckPermsBridge = new LuckPermsBridge(this.logger);
    this.luckPermsBridge.load();
    this.liteBansBridge = new LiteBansBridge(this.logger);
    this.punishmentBridgeServer = new LiteBansPunishmentBridgeServer(this.logger, this.config, this.liteBansBridge.randomIdResolver());
    this.punishmentBridgeServer.start();

    if (!this.config.hasPanelToken()) {
      this.logger.warn("panel.api-token ist noch CHANGE_ME. Ticket-Befehle funktionieren erst nach Konfiguration.");
    }
    this.refreshTicketCategoriesAsync();
    if (this.config.liteBansEnabled() && !this.liteBansBridge.available()) {
      this.logger.warn("LiteBans API ist nicht verfuegbar. Ban-Pruefung und BanInfo sind eingeschraenkt.");
    }
  }

  private void registerCommands() {
    this.register("ticket", List.of("support"), new TicketCommand());
    this.register("tickets", List.of("mytickets"), new OwnTicketsCommand());
    this.register("teamtickets", List.of("opentickets"), new TeamTicketsCommand());
    this.register("teamticket", List.of("tticket", "staffticket"), new TicketTeamCommand());
    this.register("ticketclose", List.of(), new TicketCloseCommand());
    this.register("ticketcomment", List.of(), new TicketCommentCommand());
    this.register("cloudban", List.of("cban"), new CloudBanCommand());
    this.register("cloudunban", List.of("cunban"), new CloudUnbanCommand());
    this.register("baninfo", List.of("cbaninfo"), new BanInfoCommand());
    this.register("tccbvelocity", List.of("panelvelocity"), new ReloadCommand());
  }

  private void register(String command, List<String> aliases, SimpleCommand handler) {
    var metaBuilder = this.server.getCommandManager().metaBuilder(command).plugin(this);
    if (!aliases.isEmpty()) {
      metaBuilder.aliases(aliases.toArray(String[]::new));
    }
    this.server.getCommandManager().register(metaBuilder.build(), handler);
  }

  private List<String> ticketCategories() {
    var now = System.currentTimeMillis();
    if (now - this.ticketCategoriesLoadedAt > TICKET_CATEGORY_CACHE_MILLIS) {
      this.ticketCategoriesLoadedAt = now;
      this.refreshTicketCategoriesAsync();
    }
    return this.ticketCategories;
  }

  private void refreshTicketCategoriesAsync() {
    if (this.panelApi == null || !this.config.hasPanelToken()) {
      return;
    }
    this.executor.execute(() -> {
      try {
        var categories = this.panelApi.ticketCategories();
        if (!categories.isEmpty()) {
          this.ticketCategories = categories;
        }
      } catch (Exception exception) {
        this.logger.debug("Ticket-Arten konnten nicht aus dem Panel geladen werden: {}", exception.getMessage());
      }
    });
  }

  private void scheduleLiteBansBridge() {
    this.server.getScheduler()
      .buildTask(this, () -> this.executor.execute(this::syncLiteBansAndActions))
      .repeat(this.config.liteBansSyncIntervalSeconds(), TimeUnit.SECONDS)
      .schedule();
    this.executor.execute(this::syncLiteBansAndActions);
  }

  private void scheduleLuckPermsBridge() {
    this.server.getScheduler()
      .buildTask(this, () -> this.executor.execute(this::syncLuckPermsAndActions))
      .repeat(this.config.luckPermsSyncIntervalSeconds(), TimeUnit.SECONDS)
      .schedule();
    this.executor.execute(this::syncLuckPermsAndActions);
  }

  private void schedulePanelActions() {
    this.server.getScheduler()
      .buildTask(this, () -> this.executor.execute(this::syncPlayerActions))
      .repeat(this.config.panelActionIntervalSeconds(), TimeUnit.SECONDS)
      .schedule();
    this.executor.execute(this::syncPlayerActions);
  }

  private void syncLiteBansAndActions() {
    if (!this.config.hasPanelToken() || !this.config.liteBansEnabled() || !this.config.liteBansSyncEnabled()) {
      return;
    }

    if (this.liteBansBridge.available()) {
      this.panelApi.syncLiteBans(this.liteBansBridge.activeBans(
        this.config.liteBansServerScope(),
        this.config.liteBansPublicIdColumn()));
    }

    for (var action : this.panelApi.pendingBanActions()) {
      this.processBanAction(action);
    }
  }

  private void processBanAction(PanelBanAction action) {
    var command = switch (action.action()) {
      case "UNBAN" -> applyBanActionTemplate(this.config.liteBansUnbanCommand(), action);
      case "EXTEND" -> applyBanActionTemplate(this.config.liteBansExtendCommand(), action);
      default -> null;
    };

    if (command == null || command.isBlank()) {
      this.panelApi.completeBanAction(action.id(), false, "Unbekannte Ban-Aktion: " + action.action());
      return;
    }

    this.server.getCommandManager()
      .executeAsync(this.server.getConsoleCommandSource(), command)
      .thenAccept(success -> this.panelApi.completeBanAction(
        action.id(),
        success,
        success ? "Velocity hat ausgefuehrt: " + command : "Velocity konnte nicht ausfuehren: " + command))
      .exceptionally(throwable -> {
        this.panelApi.completeBanAction(action.id(), false, throwable.getMessage());
        return null;
      });
  }

  private void syncLuckPermsAndActions() {
    if (!this.config.hasPanelToken() || !this.config.luckPermsSyncEnabled()) {
      return;
    }

    this.panelApi.syncPermissionSubjects(this.luckPermsBridge.subjects(this.config.luckPermsServerId(), "velocity"));
    for (var action : this.panelApi.pendingPermissionActions()) {
      this.processPermissionAction(action);
    }
  }

  private void processPermissionAction(PanelPermissionAction action) {
    try {
      var message = this.luckPermsBridge.apply(action);
      this.panelApi.completePermissionAction(action.id(), true, message);
    } catch (Exception exception) {
      this.panelApi.completePermissionAction(action.id(), false, exception.getMessage());
    }
  }

  private void syncPlayerActions() {
    if (!this.config.hasPanelToken()) {
      return;
    }

    for (var action : this.panelApi.pendingPlayerActions()) {
      this.processPlayerAction(action);
    }
  }

  private void processPlayerAction(PanelPlayerAction action) {
    if (!"TELEPORT_TO_PLAYER".equals(action.type())) {
      this.panelApi.completePlayerAction(action.id(), false, "Unbekannte Spieler-Aktion: " + action.type());
      return;
    }
    if (!this.config.teleportEnabled()) {
      this.panelApi.completePlayerAction(action.id(), false, "Teleport ist in der Velocity-Config deaktiviert.");
      return;
    }

    var staff = this.server.getPlayer(action.staffName()).orElse(null);
    var target = this.findTarget(action);
    if (staff == null) {
      this.panelApi.completePlayerAction(action.id(), false, "Teamler ist nicht online: " + action.staffName());
      return;
    }
    if (target == null) {
      this.panelApi.completePlayerAction(action.id(), false, "Spieler ist nicht online: " + action.targetName());
      return;
    }

    var targetServer = target.getCurrentServer().orElse(null);
    if (targetServer == null) {
      this.panelApi.completePlayerAction(action.id(), false, "Spieler ist aktuell auf keinem Server verbunden.");
      return;
    }

    var connection = staff.getCurrentServer().orElse(null);
    var sameServer = connection != null
      && connection.getServerInfo().getName().equalsIgnoreCase(targetServer.getServerInfo().getName());
    var connectFuture = sameServer
      ? java.util.concurrent.CompletableFuture.completedFuture(null)
      : staff.createConnectionRequest(targetServer.getServer()).connect().thenApply(result -> null);

    connectFuture.thenCompose(ignored -> {
      staff.sendMessage(Component.text(PREFIX + "Teleportiere zu " + target.getUsername() + " auf " + targetServer.getServerInfo().getName(), NamedTextColor.GOLD));
      var command = applyTeleportTemplate(this.config.teleportCommand(), staff.getUsername(), target.getUsername(), targetServer.getServerInfo().getName(), action.ticketId());
      if (command == null || command.isBlank()) {
        return java.util.concurrent.CompletableFuture.completedFuture(true);
      }
      return this.server.getCommandManager().executeAsync(this.server.getConsoleCommandSource(), command);
    }).thenAccept(success -> this.panelApi.completePlayerAction(
      action.id(),
      success,
      success
        ? "Teamler wurde verbunden und Teleport-Befehl wurde ausgefuehrt."
        : "Teamler wurde verbunden, aber der Teleport-Befehl konnte nicht ausgefuehrt werden."))
      .exceptionally(throwable -> {
        this.panelApi.completePlayerAction(action.id(), false, throwable.getMessage());
        return null;
      });
  }

  private Player findTarget(PanelPlayerAction action) {
    if (action.targetUniqueId() != null && !action.targetUniqueId().isBlank()) {
      try {
        var target = this.server.getPlayer(UUID.fromString(action.targetUniqueId())).orElse(null);
        if (target != null) {
          return target;
        }
      } catch (IllegalArgumentException ignored) {
      }
    }
    return this.server.getPlayer(action.targetName()).orElse(null);
  }

  private final class TicketCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!(invocation.source() instanceof Player player)) {
        send(invocation.source(), "Dieser Befehl ist nur fuer Spieler.", NamedTextColor.RED);
        return;
      }
      if (invocation.arguments().length == 0) {
        send(player, "Nutzung: /ticket create [Art] <Grund>, /ticket list, /ticket view <id>", NamedTextColor.YELLOW);
        return;
      }

      var args = invocation.arguments();
      var subCommand = args[0].toLowerCase(Locale.ROOT);
      if ("list".equals(subCommand)) {
        if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketListOwn())) {
          noPermission(invocation.source());
          return;
        }
        runAsync(invocation.source(), () -> {
          var tickets = TicketConsoleVelocityPlugin.this.panelApi.ownTickets(player.getUniqueId());
          sendTicketList(player, "Deine Tickets", tickets);
        });
        return;
      }

      if ("view".equals(subCommand)) {
        if (args.length < 2) {
          send(player, "Nutzung: /ticket view <id>", NamedTextColor.YELLOW);
          return;
        }
        var canViewTeamTickets = TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketTeam())
          || TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketManage());
        if (!canViewTeamTickets
          && !TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketListOwn())) {
          noPermission(invocation.source());
          return;
        }
        var id = args[1];
        runAsync(invocation.source(), () -> {
          var ticket = TicketConsoleVelocityPlugin.this.panelApi.ticket(id);
          if (!canViewTeamTickets && !isOwnTicket(ticket, player)) {
            send(player, "Dieses Ticket gehoert nicht zu deinem Minecraft-Account.", NamedTextColor.RED);
            return;
          }
          sendTicketDetails(player, ticket, canViewTeamTickets);
        });
        return;
      }

      var startIndex = "create".equals(subCommand) ? 1 : 0;
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketCreate())) {
        noPermission(invocation.source());
        return;
      }
      if (args.length <= startIndex) {
        send(player, "Nutzung: /ticket create [Art] <Grund>", NamedTextColor.YELLOW);
        return;
      }

      var input = TicketConsoleVelocityPlugin.this.parseTicketCreate(args, startIndex);
      if (input.message().isBlank()) {
        send(player, "Bitte gib einen Grund bzw. eine Beschreibung fuer das Ticket an.", NamedTextColor.YELLOW);
        return;
      }
      var sourceServer = currentServer(player);
      runAsync(invocation.source(), () -> {
        var ticket = TicketConsoleVelocityPlugin.this.panelApi.createTicket(
          player.getUsername(),
          player.getUniqueId(),
          sourceServer,
          input.category(),
          input.message());
        send(player, "Ticket erstellt: " + ticket.id() + " auf " + sourceServer + ". Mit /ticket view " + ticket.id() + " kannst du Antworten lesen.", NamedTextColor.GREEN);
      });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
      var args = invocation.arguments();
      if (args.length == 0) {
        return List.of("create", "list", "view");
      }
      if (args.length == 1) {
        var suggestions = new java.util.ArrayList<String>();
        suggestions.addAll(suggestMatching(List.of("create", "list", "view"), args[0]));
        suggestions.addAll(suggestMatching(TicketConsoleVelocityPlugin.this.ticketCategories(), args[0]));
        return suggestions;
      }
      if ("create".equalsIgnoreCase(args[0]) && args.length == 2) {
        return suggestMatching(TicketConsoleVelocityPlugin.this.ticketCategories(), args[1]);
      }
      return List.of();
    }
  }

  private final class OwnTicketsCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!(invocation.source() instanceof Player player)) {
        send(invocation.source(), "Dieser Befehl ist nur fuer Spieler.", NamedTextColor.RED);
        return;
      }
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketListOwn())) {
        noPermission(invocation.source());
        return;
      }

      runAsync(invocation.source(), () -> {
        var tickets = TicketConsoleVelocityPlugin.this.panelApi.ownTickets(player.getUniqueId());
        sendTicketList(player, "Deine Tickets", tickets);
      });
    }
  }

  private final class TeamTicketsCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketTeam())) {
        noPermission(invocation.source());
        return;
      }

      runAsync(invocation.source(), () -> {
        var tickets = TicketConsoleVelocityPlugin.this.panelApi.openTickets();
        sendTicketList(invocation.source(), "Offene Tickets", tickets);
      });
    }
  }

  private final class TicketTeamCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketTeam())) {
        noPermission(invocation.source());
        return;
      }
      if (invocation.arguments().length == 0 || "list".equalsIgnoreCase(invocation.arguments()[0])) {
        runAsync(invocation.source(), () -> {
          var tickets = TicketConsoleVelocityPlugin.this.panelApi.openTickets();
          sendTicketList(invocation.source(), "Offene Tickets", tickets);
        });
        return;
      }

      var args = invocation.arguments();
      var action = args[0].toLowerCase(Locale.ROOT);
      if ("view".equals(action)) {
        if (args.length < 2) {
          send(invocation.source(), "Nutzung: /teamticket view <id>", NamedTextColor.YELLOW);
          return;
        }
        runAsync(invocation.source(), () -> sendTicketDetails(
          invocation.source(),
          TicketConsoleVelocityPlugin.this.panelApi.ticket(args[1]),
          true));
        return;
      }

      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketManage())) {
        noPermission(invocation.source());
        return;
      }

      var actor = actorName(invocation.source());
      if ("close".equals(action) || "open".equals(action) || "progress".equals(action)) {
        if (args.length < 2) {
          send(invocation.source(), "Nutzung: /teamticket " + action + " <id>", NamedTextColor.YELLOW);
          return;
        }
        var status = "close".equals(action) ? "CLOSED" : "progress".equals(action) ? "IN_PROGRESS" : "OPEN";
        runAsync(invocation.source(), () -> {
          TicketConsoleVelocityPlugin.this.panelApi.setTicketStatus(args[1], status, actor);
          send(invocation.source(), "Ticket " + args[1] + " ist jetzt " + status + ".", NamedTextColor.GREEN);
        });
        return;
      }

      if ("assign".equals(action)) {
        if (args.length < 3) {
          send(invocation.source(), "Nutzung: /teamticket assign <id> <teamler>", NamedTextColor.YELLOW);
          return;
        }
        runAsync(invocation.source(), () -> {
          TicketConsoleVelocityPlugin.this.panelApi.assignTicket(args[1], args[2], actor);
          send(invocation.source(), "Ticket " + args[1] + " wurde " + args[2] + " zugewiesen.", NamedTextColor.GREEN);
        });
        return;
      }

      if ("comment".equals(action)) {
        if (args.length < 3) {
          send(invocation.source(), "Nutzung: /teamticket comment <id> <nachricht>", NamedTextColor.YELLOW);
          return;
        }
        var message = join(args, 2);
        runAsync(invocation.source(), () -> {
          TicketConsoleVelocityPlugin.this.panelApi.addTicketComment(args[1], actor, message, false);
          send(invocation.source(), "Antwort zu Ticket " + args[1] + " gespeichert.", NamedTextColor.GREEN);
        });
        return;
      }

      send(invocation.source(), "Nutzung: /teamticket list|view|open|progress|close|assign|comment", NamedTextColor.YELLOW);
    }
  }

  private final class TicketCloseCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketManage())) {
        noPermission(invocation.source());
        return;
      }
      if (invocation.arguments().length < 1) {
        send(invocation.source(), "Nutzung: /ticketclose <id>", NamedTextColor.YELLOW);
        return;
      }

      var id = invocation.arguments()[0];
      var actor = actorName(invocation.source());
      runAsync(invocation.source(), () -> {
        TicketConsoleVelocityPlugin.this.panelApi.setTicketStatus(id, "CLOSED", actor);
        send(invocation.source(), "Ticket " + id + " geschlossen.", NamedTextColor.GREEN);
      });
    }
  }

  private final class TicketCommentCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketManage())) {
        noPermission(invocation.source());
        return;
      }
      if (invocation.arguments().length < 2) {
        send(invocation.source(), "Nutzung: /ticketcomment <id> <Nachricht>", NamedTextColor.YELLOW);
        return;
      }

      var id = invocation.arguments()[0];
      var message = join(invocation.arguments(), 1);
      var actor = actorName(invocation.source());
      runAsync(invocation.source(), () -> {
        TicketConsoleVelocityPlugin.this.panelApi.addTicketComment(id, actor, message, false);
        send(invocation.source(), "Kommentar zu Ticket " + id + " gespeichert.", NamedTextColor.GREEN);
      });
    }
  }

  private final class CloudBanCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionBanManage())) {
        noPermission(invocation.source());
        return;
      }
      if (invocation.arguments().length < 3) {
        send(invocation.source(), "Nutzung: /cloudban <spieler> <dauer> <grund>", NamedTextColor.YELLOW);
        return;
      }

      var player = invocation.arguments()[0];
      var duration = invocation.arguments()[1];
      var reason = join(invocation.arguments(), 2);
      var command = applyTemplate(TicketConsoleVelocityPlugin.this.config.liteBansBanCommand(), player, duration, reason);
      dispatchConsoleCommand(invocation.source(), command, "LiteBans-Ban ausgefuehrt fuer " + player + ".");
    }
  }

  private final class CloudUnbanCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionBanManage())) {
        noPermission(invocation.source());
        return;
      }
      if (invocation.arguments().length < 1) {
        send(invocation.source(), "Nutzung: /cloudunban <spieler> [grund]", NamedTextColor.YELLOW);
        return;
      }

      var player = invocation.arguments()[0];
      var reason = invocation.arguments().length > 1 ? join(invocation.arguments(), 1) : "Unban durch " + actorName(invocation.source());
      var command = applyTemplate(TicketConsoleVelocityPlugin.this.config.liteBansUnbanCommand(), player, "", reason);
      dispatchConsoleCommand(invocation.source(), command, "LiteBans-Unban ausgefuehrt fuer " + player + ".");
    }
  }

  private final class BanInfoCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionBanManage())) {
        noPermission(invocation.source());
        return;
      }
      if (invocation.arguments().length < 1) {
        send(invocation.source(), "Nutzung: /baninfo <spieler>", NamedTextColor.YELLOW);
        return;
      }

      var targetName = invocation.arguments()[0];
      var target = TicketConsoleVelocityPlugin.this.server.getPlayer(targetName).orElse(null);
      if (target == null) {
        send(invocation.source(), "BanInfo per API ist aktuell nur fuer online Spieler moeglich.", NamedTextColor.YELLOW);
        return;
      }

      runAsync(invocation.source(), () -> {
        var address = target.getRemoteAddress() == null || target.getRemoteAddress().getAddress() == null
          ? null
          : target.getRemoteAddress().getAddress().getHostAddress();
        var ban = TicketConsoleVelocityPlugin.this.liteBansBridge.activeBan(
          target.getUniqueId(),
          address,
          TicketConsoleVelocityPlugin.this.config.liteBansServerScope());
        send(invocation.source(), TicketConsoleVelocityPlugin.this.liteBansBridge.describe(ban), ban == null ? NamedTextColor.GREEN : NamedTextColor.RED);
      });
    }
  }

  private final class ReloadCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionReload())) {
        noPermission(invocation.source());
        return;
      }
      TicketConsoleVelocityPlugin.this.reload();
      send(invocation.source(), "Velocity-Plugin neu geladen.", NamedTextColor.GREEN);
    }
  }

  private void sendTicketList(CommandSource source, String title, List<PanelTicket> tickets) {
    send(source, title + ":", NamedTextColor.GOLD);
    if (tickets.isEmpty()) {
      send(source, "Keine Tickets gefunden.", NamedTextColor.GRAY);
      return;
    }

    tickets.stream()
      .limit(this.config.ticketListLimit())
      .forEach(ticket -> send(
        source,
        "#" + nullDash(ticket.id()) + " [" + ticket.status() + "] "
          + ticket.subject() + " | " + nullDash(ticket.sourceServer()),
        NamedTextColor.YELLOW));
  }

  private void sendTicketDetails(CommandSource source, PanelTicket ticket, boolean includeInternal) {
    send(source, "Ticket " + ticket.id() + " [" + ticket.status() + "]", NamedTextColor.GOLD);
    send(source, ticket.subject() + " | " + ticket.category() + " | " + nullDash(ticket.sourceServer()), NamedTextColor.YELLOW);
    send(source, "Ersteller: " + nullDash(ticket.creatorName()) + " | Zustaendig: " + nullDash(ticket.assignedTo()), NamedTextColor.GRAY);
    if (ticket.content() != null && !ticket.content().isBlank()) {
      send(source, "Beschreibung: " + ticket.content(), NamedTextColor.WHITE);
    }

    var visibleComments = ticket.comments() == null
      ? List.<PanelTicketComment>of()
      : ticket.comments().stream()
        .filter(comment -> includeInternal || !comment.internal())
        .toList();
    if (visibleComments.isEmpty()) {
      send(source, "Noch keine Antworten vorhanden.", NamedTextColor.GRAY);
      return;
    }
    send(source, "Antworten:", NamedTextColor.GOLD);
    visibleComments.stream()
      .limit(8)
      .forEach(comment -> send(
        source,
        nullDash(comment.author()) + ": " + nullDash(comment.message()) + (comment.internal() ? " (intern)" : ""),
        comment.internal() ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA));
  }

  private void dispatchConsoleCommand(CommandSource source, String command, String successMessage) {
    this.server.getCommandManager()
      .executeAsync(this.server.getConsoleCommandSource(), command)
      .thenAccept(success -> send(source, success ? successMessage : "Befehl konnte nicht ausgefuehrt werden.", success ? NamedTextColor.GREEN : NamedTextColor.RED))
      .exceptionally(throwable -> {
        send(source, "Befehl fehlgeschlagen: " + throwable.getMessage(), NamedTextColor.RED);
        return null;
      });
  }

  private void runAsync(CommandSource source, Runnable runnable) {
    this.executor.execute(() -> {
      try {
        runnable.run();
      } catch (Exception exception) {
        send(source, exception.getMessage(), NamedTextColor.RED);
      }
    });
  }

  private boolean hasPermission(CommandSource source, String permission) {
    if (source instanceof Player player) {
      return this.luckPermsBridge.hasPermission(player, permission);
    }
    return true;
  }

  private static void send(CommandSource source, String message, NamedTextColor color) {
    source.sendMessage(Component.text(PREFIX + message, color));
  }

  private static void noPermission(CommandSource source) {
    send(source, "Dafuer hast du keine Berechtigung.", NamedTextColor.RED);
  }

  private static String join(String[] arguments, int startIndex) {
    return String.join(" ", Arrays.copyOfRange(arguments, startIndex, arguments.length)).trim();
  }

  private static String join(String[] arguments, int startIndex, int endIndex) {
    return String.join(" ", Arrays.copyOfRange(arguments, startIndex, endIndex)).trim();
  }

  private TicketCreateInput parseTicketCreate(String[] arguments, int startIndex) {
    var categories = this.ticketCategories();
    var firstCategory = category(arguments[startIndex], categories);
    if (firstCategory != null && arguments.length > startIndex + 1) {
      return new TicketCreateInput(firstCategory, join(arguments, startIndex + 1));
    }

    var lastCategory = category(arguments[arguments.length - 1], categories);
    if (lastCategory != null && arguments.length > startIndex + 1) {
      return new TicketCreateInput(lastCategory, join(arguments, startIndex, arguments.length - 1));
    }

    return new TicketCreateInput(null, join(arguments, startIndex));
  }

  private static String category(String value, List<String> categories) {
    if (value == null) {
      return null;
    }
    var normalized = normalizeCategory(value);
    for (var category : categories) {
      if (normalizeCategory(category).equals(normalized)) {
        return category;
      }
    }
    var aliases = switch (normalized) {
      case "FEHLER" -> List.of("BUG");
      case "MELDEN", "REPORT", "MELDUNG" -> List.of("MELDEN", "REPORT");
      case "SONSTIGES", "OTHER" -> List.of("SONSTIGES", "OTHER");
      default -> List.<String>of();
    };
    for (var alias : aliases) {
      var match = categories.stream()
        .filter(category -> normalizeCategory(category).equals(alias))
        .findFirst();
      if (match.isPresent()) {
        return match.get();
      }
    }
    return null;
  }

  private static List<String> suggestMatching(List<String> values, String prefix) {
    var normalizedPrefix = normalizeCategory(prefix);
    return values.stream()
      .filter(value -> normalizeCategory(value).startsWith(normalizedPrefix))
      .toList();
  }

  private static String normalizeCategory(String value) {
    return String.valueOf(value)
      .trim()
      .replace(" ", "_")
      .replace("-", "_")
      .toUpperCase(Locale.ROOT)
      .replaceAll("[^A-Z0-9_]", "");
  }

  private static boolean isOwnTicket(PanelTicket ticket, Player player) {
    if (ticket.creatorUniqueId() != null && ticket.creatorUniqueId().equalsIgnoreCase(player.getUniqueId().toString())) {
      return true;
    }
    return ticket.creatorName() != null && ticket.creatorName().equalsIgnoreCase(player.getUsername());
  }

  private static String currentServer(Player player) {
    return player.getCurrentServer()
      .map(connection -> connection.getServerInfo().getName())
      .orElse("proxy");
  }

  private static String actorName(CommandSource source) {
    return source instanceof Player player ? player.getUsername() : "Console";
  }

  private static String applyTemplate(String template, String player, String duration, String reason) {
    return template
      .replace("{player}", player)
      .replace("{duration}", duration)
      .replace("{reason}", reason);
  }

  private static String applyBanActionTemplate(String template, PanelBanAction action) {
    var player = firstNonBlank(action.targetName(), action.publicId(), action.banId());
    return template
      .replace("{player}", nullDash(player))
      .replace("{duration}", nullDash(action.duration()))
      .replace("{reason}", nullDash(action.reason()))
      .replace("{id}", nullDash(action.publicId()))
      .replace("{banId}", nullDash(action.banId()))
      .replace("{uuid}", nullDash(action.targetUniqueId()))
      .replace("{ip}", nullDash(action.targetAddress()))
      .replace("{actor}", nullDash(action.actor()));
  }

  private static String applyTeleportTemplate(String template, String staff, String target, String serverName, String ticketId) {
    return template == null ? "" : template
      .replace("{staff}", nullDash(staff))
      .replace("{target}", nullDash(target))
      .replace("{server}", nullDash(serverName))
      .replace("{ticketId}", nullDash(ticketId));
  }

  private static String nullDash(String value) {
    return value == null || value.isBlank() ? "-" : value;
  }

  private static String firstNonBlank(String... values) {
    for (var value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private record TicketCreateInput(String category, String message) {
  }
}
