package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PanelDataBackendFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(PanelDataBackendFactory.class);

  private PanelDataBackendFactory() {
  }

  public static PanelDataBackend create(PanelConfiguration configuration) {
    if (!"LOCAL".equalsIgnoreCase(configuration.panelStorageBackend())) {
      try {
        return new SqlPanelDataBackend(configuration);
      } catch (RuntimeException exception) {
        LOGGER.warn(
          "SQL Panel-Speicher ist nicht verfuegbar, nutze lokalen JSON-Speicher als Fallback: {}",
          rootCauseMessage(exception),
          exception);
      }
    }
    return new LocalPanelDataBackend();
  }

  private static String rootCauseMessage(Throwable throwable) {
    var current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getClass().getSimpleName() + ": " + current.getMessage();
  }
}
