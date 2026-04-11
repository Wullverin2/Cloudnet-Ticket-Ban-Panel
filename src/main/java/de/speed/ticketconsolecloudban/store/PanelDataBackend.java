package de.speed.ticketconsolecloudban.store;

import java.nio.file.Path;

public interface PanelDataBackend {

  <T> T load(String storeKey, Path localPath, Class<T> type, T fallback);

  void save(String storeKey, Path localPath, Object data);
}
