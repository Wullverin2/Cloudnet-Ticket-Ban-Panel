package de.speed.ticketconsolecloudban.velocity;

import java.lang.reflect.Method;
import java.util.UUID;
import org.slf4j.Logger;

public final class LiteBansBridge {

  private final Logger logger;
  private Object database;
  private Method getBanMethod;

  public LiteBansBridge(Logger logger) {
    this.logger = logger;
  }

  public boolean available() {
    try {
      this.database();
      return true;
    } catch (Throwable throwable) {
      return false;
    }
  }

  public LiteBanResult activeBan(UUID uniqueId, String address, String serverScope) {
    try {
      var entry = this.getBanMethod().invoke(this.database(), uniqueId, address, normalizeScope(serverScope));
      return entry == null ? null : this.toResult(entry);
    } catch (Throwable throwable) {
      this.logger.warn("LiteBans Ban-Pruefung fehlgeschlagen: {}", throwable.getMessage());
      return null;
    }
  }

  public String describe(LiteBanResult ban) {
    if (ban == null) {
      return "Kein aktiver LiteBans-Ban gefunden.";
    }

    return "Aktiver Ban: " + ban.reason() + " | Verbleibend: " + ban.remaining();
  }

  private LiteBanResult toResult(Object entry) throws ReflectiveOperationException {
    var entryClass = entry.getClass();
    var reason = String.valueOf(entryClass.getMethod("getReason").invoke(entry));
    var permanent = Boolean.TRUE.equals(entryClass.getMethod("isPermanent").invoke(entry));
    var remaining = permanent
      ? "permanent"
      : String.valueOf(entryClass.getMethod("getRemainingDurationString", long.class).invoke(entry, System.currentTimeMillis()));
    return new LiteBanResult(reason == null || reason.isBlank() || "null".equals(reason) ? "Kein Grund angegeben" : reason, remaining);
  }

  private Object database() throws ReflectiveOperationException {
    if (this.database == null) {
      var databaseClass = Class.forName("litebans.api.Database");
      this.database = databaseClass.getMethod("get").invoke(null);
    }
    return this.database;
  }

  private Method getBanMethod() throws ReflectiveOperationException {
    if (this.getBanMethod == null) {
      this.getBanMethod = this.database().getClass().getMethod("getBan", UUID.class, String.class, String.class);
    }
    return this.getBanMethod;
  }

  private static String normalizeScope(String serverScope) {
    return serverScope == null || serverScope.isBlank() || "*".equals(serverScope.trim())
      ? "__ALL__"
      : serverScope.trim();
  }

  public record LiteBanResult(String reason, String remaining) {
  }
}
