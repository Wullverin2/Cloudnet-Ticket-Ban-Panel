package de.speed.ticketconsolecloudban.purpur;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class TicketConsolePurpurPlugin extends JavaPlugin {

  private PurpurPluginConfig config;
  private PanelApiClient panelApi;
  private LuckPermsBridge luckPermsBridge;
  private BukkitTask syncTask;

  @Override
  public void onEnable() {
    this.saveDefaultConfig();
    this.reloadBridge();
    this.getLogger().info("TicketConsoleCloudBan Purpur Plugin gestartet.");
  }

  @Override
  public void onDisable() {
    if (this.syncTask != null) {
      this.syncTask.cancel();
      this.syncTask = null;
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!"tccbpurpur".equalsIgnoreCase(command.getName())) {
      return false;
    }
    if (!sender.hasPermission(this.config.reloadPermission())) {
      sender.sendMessage("Dafuer hast du keine Berechtigung.");
      return true;
    }
    if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
      sender.sendMessage("Nutzung: /tccbpurpur reload");
      return true;
    }
    this.reloadBridge();
    sender.sendMessage("TicketConsoleCloudBan Purpur Bridge neu geladen.");
    return true;
  }

  private void reloadBridge() {
    this.reloadConfig();
    this.config = PurpurPluginConfig.load(this);
    this.panelApi = new PanelApiClient(this.config);
    this.luckPermsBridge = new LuckPermsBridge(this.getLogger());
    this.luckPermsBridge.load();

    if (!this.config.hasPanelToken()) {
      this.getLogger().warning("panel.api-token ist noch CHANGE_ME. LuckPerms-Sync startet erst nach Konfiguration.");
    }

    if (this.syncTask != null) {
      this.syncTask.cancel();
    }
    this.syncTask = this.getServer().getScheduler().runTaskTimerAsynchronously(
      this,
      this::syncLuckPermsAndActions,
      20L,
      this.config.syncIntervalSeconds() * 20L);
    this.getServer().getScheduler().runTaskAsynchronously(this, this::syncLuckPermsAndActions);
  }

  private void syncLuckPermsAndActions() {
    if (!this.config.hasPanelToken() || !this.config.syncEnabled()) {
      return;
    }

    try {
      this.panelApi.syncPermissionSubjects(this.luckPermsBridge.subjects(this.config.serverId()));
      for (var action : this.panelApi.pendingPermissionActions()) {
        this.processPermissionAction(action);
      }
    } catch (Exception exception) {
      this.getLogger().warning("LuckPerms-Sync fehlgeschlagen: " + exception.getMessage());
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
}
