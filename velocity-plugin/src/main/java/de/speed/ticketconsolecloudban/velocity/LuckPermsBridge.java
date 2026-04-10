package de.speed.ticketconsolecloudban.velocity;

import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

public final class LuckPermsBridge {

  private final Logger logger;
  private LuckPerms luckPerms;

  public LuckPermsBridge(Logger logger) {
    this.logger = logger;
  }

  public void load() {
    try {
      this.luckPerms = LuckPermsProvider.get();
      this.logger.info("LuckPerms API erkannt. Velocity-Permissions werden ueber LuckPerms ausgewertet.");
    } catch (IllegalStateException exception) {
      this.luckPerms = null;
      this.logger.warn("LuckPerms API nicht verfuegbar. Es wird Velocity hasPermission als Fallback genutzt.");
    }
  }

  public boolean hasPermission(Player player, String permission) {
    if (permission == null || permission.isBlank()) {
      return true;
    }

    if (this.luckPerms != null) {
      var user = this.luckPerms.getUserManager().getUser(player.getUniqueId());
      if (user != null) {
        var result = user.getCachedData().getPermissionData().checkPermission(permission);
        return result.asBoolean();
      }
    }

    return player.hasPermission(permission);
  }
}
