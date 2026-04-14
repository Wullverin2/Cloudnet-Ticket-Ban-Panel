package de.speed.ticketconsolecloudban.appeal;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.nio.file.Path;

public final class EvidenceStorageFactory {

  private EvidenceStorageFactory() {
  }

  public static EvidenceStorage create(PanelConfiguration configuration, Path dataDirectory) {
    return create(AppealEvidenceConfiguration.from(configuration), dataDirectory);
  }

  public static EvidenceStorage create(AppealEvidenceConfiguration configuration, Path dataDirectory) {
    return switch (configuration.storage()) {
      case "SFTP" -> new SftpEvidenceStorage(configuration);
      case "ONEDRIVE" -> new OneDriveEvidenceStorage(configuration);
      default -> new LocalEvidenceStorage(resolveLocalDirectory(configuration, dataDirectory));
    };
  }

  private static Path resolveLocalDirectory(AppealEvidenceConfiguration configuration, Path dataDirectory) {
    var configured = Path.of(configuration.localDirectory());
    return configured.isAbsolute() ? configured : dataDirectory.resolve(configured);
  }
}
