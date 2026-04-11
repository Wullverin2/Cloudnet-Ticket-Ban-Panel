package de.speed.ticketconsolecloudban.velocity;

import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
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

  public List<PermissionSubjectSnapshot> subjects() {
    var snapshots = new ArrayList<PermissionSubjectSnapshot>();
    if (this.luckPerms == null) {
      return List.of();
    }

    for (var group : this.luckPerms.getGroupManager().getLoadedGroups()) {
      snapshots.add(this.snapshot("GROUP", group.getName(), group.getName(), group));
    }
    for (var user : this.luckPerms.getUserManager().getLoadedUsers()) {
      snapshots.add(this.snapshot("USER", user.getUniqueId().toString(), user.getFriendlyName(), user));
    }
    return List.copyOf(snapshots);
  }

  public String apply(PanelPermissionAction action) {
    if (this.luckPerms == null) {
      throw new IllegalStateException("LuckPerms API ist nicht verfuegbar.");
    }

    var holder = this.loadHolder(action.subjectType(), action.subjectId());
    var node = switch (action.action()) {
      case "ADD_PERMISSION", "REMOVE_PERMISSION" -> Node.builder(required(action.permission(), "Permission")).build();
      case "ADD_PARENT", "REMOVE_PARENT" -> InheritanceNode.builder(required(action.parent(), "Parent-Gruppe")).build();
      default -> throw new IllegalArgumentException("Unbekannte Permission-Aktion: " + action.action());
    };

    switch (action.action()) {
      case "ADD_PERMISSION", "ADD_PARENT" -> holder.data().add(node);
      case "REMOVE_PERMISSION", "REMOVE_PARENT" -> holder.data().remove(node);
      default -> throw new IllegalArgumentException("Unbekannte Permission-Aktion: " + action.action());
    }
    this.saveHolder(holder);
    return action.action() + " fuer " + action.subjectType() + " " + action.subjectId() + " ausgefuehrt.";
  }

  private PermissionSubjectSnapshot snapshot(String type, String id, String name, PermissionHolder holder) {
    var permissions = new ArrayList<String>();
    var parents = new ArrayList<String>();
    for (var node : holder.getNodes()) {
      if (node instanceof InheritanceNode inheritanceNode) {
        parents.add(inheritanceNode.getGroupName());
      } else {
        permissions.add(node.getKey());
      }
    }
    return new PermissionSubjectSnapshot(type, id, name, List.copyOf(permissions), List.copyOf(parents), "velocity", null);
  }

  private PermissionHolder loadHolder(String subjectType, String subjectId) {
    if ("GROUP".equalsIgnoreCase(subjectType)) {
      return this.luckPerms.getGroupManager()
        .loadGroup(subjectId)
        .join()
        .orElseThrow(() -> new IllegalArgumentException("LuckPerms-Gruppe nicht gefunden: " + subjectId));
    }

    if ("USER".equalsIgnoreCase(subjectType)) {
      return this.luckPerms.getUserManager().loadUser(UUID.fromString(subjectId)).join();
    }

    throw new IllegalArgumentException("Unbekannter Subject-Typ: " + subjectType);
  }

  private void saveHolder(PermissionHolder holder) {
    if (holder instanceof Group group) {
      this.luckPerms.getGroupManager().saveGroup(group);
      return;
    }
    if (holder instanceof User user) {
      this.luckPerms.getUserManager().saveUser(user);
    }
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " ist erforderlich.");
    }
    return value.trim();
  }
}
