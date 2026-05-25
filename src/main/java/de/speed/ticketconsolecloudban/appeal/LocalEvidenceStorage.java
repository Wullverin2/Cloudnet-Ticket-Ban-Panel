package de.speed.ticketconsolecloudban.appeal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class LocalEvidenceStorage implements EvidenceStorage {

  private final Path baseDirectory;

  public LocalEvidenceStorage(Path baseDirectory) {
    this.baseDirectory = baseDirectory;
  }

  @Override
  public StoredEvidence store(String appealId, AppealMultipartForm.UploadFile file) {
    try {
      var directory = this.baseDirectory.resolve(safeName(appealId));
      Files.createDirectories(directory);
      var storedName = UUID.randomUUID() + "-" + safeName(file.fileName());
      var target = directory.resolve(storedName);
      Files.write(target, file.content());
      return new StoredEvidence("LOCAL", this.baseDirectory.relativize(target).toString().replace('\\', '/'));
    } catch (Exception exception) {
      throw new IllegalStateException("Beweisdatei konnte lokal nicht gespeichert werden: " + exception.getMessage(), exception);
    }
  }

  private static String safeName(String value) {
    var safe = value == null || value.isBlank() ? "file" : value.trim();
    return safe.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
