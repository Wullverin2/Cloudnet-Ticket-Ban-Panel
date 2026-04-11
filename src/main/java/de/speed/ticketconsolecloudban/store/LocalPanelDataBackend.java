package de.speed.ticketconsolecloudban.store;

import eu.cloudnetservice.driver.document.DocumentFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalPanelDataBackend implements PanelDataBackend {

  @Override
  public <T> T load(String storeKey, Path localPath, Class<T> type, T fallback) {
    try {
      Files.createDirectories(localPath.getParent());
      if (Files.notExists(localPath)) {
        return fallback;
      }

      var data = DocumentFactory.json().parse(localPath).toInstanceOf(type);
      return data == null ? fallback : data;
    } catch (Exception exception) {
      throw new IllegalStateException("Lokaler Panel-Speicher konnte nicht geladen werden: " + localPath, exception);
    }
  }

  @Override
  public void save(String storeKey, Path localPath, Object data) {
    try {
      Files.createDirectories(localPath.getParent());
      DocumentFactory.json()
        .newDocument()
        .appendTree(data)
        .writeTo(localPath);
    } catch (Exception exception) {
      throw new IllegalStateException("Lokaler Panel-Speicher konnte nicht geschrieben werden: " + localPath, exception);
    }
  }
}
