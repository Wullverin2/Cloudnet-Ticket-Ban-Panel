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

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  private VelocityPluginConfig config;
  private PanelApiClient panelApi;
  private LuckPermsBridge luckPermsBridge;
  private LiteBansBridge liteBansBridge;

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
    this.logger.info("TicketConsoleCloudBan Velocity Plugin gestartet.");
  }

  @Subscribe
  public void onShutdown(ProxyShutdownEvent event) {
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
    this.config = VelocityPluginConfig.load(this.dataDirectory);
    this.panelApi = new PanelApiClient(this.config);
    this.luckPermsBridge = new LuckPermsBridge(this.logger);
    this.luckPermsBridge.load();
    this.liteBansBridge = new LiteBansBridge(this.logger);

    if (!this.config.hasPanelToken()) {
      this.logger.warn("panel.api-token ist noch CHANGE_ME. Ticket-Befehle funktionieren erst nach Konfiguration.");
    }
    if (this.config.liteBansEnabled() && !this.liteBansBridge.available()) {
      this.logger.warn("LiteBans API ist nicht verfuegbar. Ban-Pruefung und BanInfo sind eingeschraenkt.");
    }
  }

  private void registerCommands() {
    this.register("ticket", List.of("support"), new TicketCommand());
    this.register("tickets", List.of("mytickets"), new OwnTicketsCommand());
    this.register("teamtickets", List.of("opentickets"), new TeamTicketsCommand());
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

  private final class TicketCommand implements SimpleCommand {
    @Override
    public void execute(Invocation invocation) {
      if (!(invocation.source() instanceof Player player)) {
        send(invocation.source(), "Dieser Befehl ist nur fuer Spieler.", NamedTextColor.RED);
        return;
      }
      if (!TicketConsoleVelocityPlugin.this.hasPermission(invocation.source(), TicketConsoleVelocityPlugin.this.config.permissionTicketCreate())) {
        noPermission(invocation.source());
        return;
      }
      if (invocation.arguments().length == 0) {
        send(player, "Nutzung: /ticket <Nachricht>", NamedTextColor.YELLOW);
        return;
      }

      var message = join(invocation.arguments(), 0);
      var sourceServer = currentServer(player);
      runAsync(invocation.source(), () -> {
        var ticket = TicketConsoleVelocityPlugin.this.panelApi.createTicket(
          player.getUsername(),
          player.getUniqueId(),
          sourceServer,
          message);
        send(player, "Ticket erstellt: " + ticket.id() + " auf " + sourceServer, NamedTextColor.GREEN);
      });
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
        "#" + shortId(ticket.id()) + " [" + ticket.status() + "] "
          + ticket.subject() + " | " + nullDash(ticket.sourceServer()),
        NamedTextColor.YELLOW));
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

  private static String shortId(String id) {
    if (id == null) {
      return "-";
    }
    return id.length() <= 8 ? id : id.substring(0, 8).toLowerCase(Locale.ROOT);
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
}
