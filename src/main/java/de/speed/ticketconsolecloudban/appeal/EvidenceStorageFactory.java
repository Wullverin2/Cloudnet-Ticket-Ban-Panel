package de.speed.ticketconsolecloudban.appeal;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.nio.file.Path;

public final class EvidenceStorageFactory {

  private EvidenceStorageFactory() {
  }

  public static EvidenceStorage create(PanelConfiguration configuration, Path dataDirectory) {
    return switch (configuration.appealEvidenceStorage()) {
      case "SFTP" -> new SftpEvidenceStorage(configuration);
      case "ONEDRIVE" -> new OneDriveEvidenceStorage(configuration);
      default -> new LocalEvidenceStorage(resolveLocalDirectory(configuration, dataDirectory));
    };
  }

  private static Path resolveLocalDirectory(PanelConfiguration configuration, Path dataDirectory) {
    var configured = Path.of(configuration.appealEvidenceLocalDirectory());
    return configured.isAbsolute() ? configured : dataDirectory.resolve(configured);
  }
}
