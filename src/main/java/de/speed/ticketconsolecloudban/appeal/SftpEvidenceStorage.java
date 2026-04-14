package de.speed.ticketconsolecloudban.appeal;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.UUID;

public final class SftpEvidenceStorage implements EvidenceStorage {

  private final AppealEvidenceConfiguration configuration;

  public SftpEvidenceStorage(PanelConfiguration configuration) {
    this(AppealEvidenceConfiguration.from(configuration));
  }

  public SftpEvidenceStorage(AppealEvidenceConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public StoredEvidence store(String appealId, AppealMultipartForm.UploadFile file) {
    if (this.configuration.sftpHost().isBlank() || this.configuration.sftpUsername().isBlank()) {
      throw new IllegalStateException("SFTP-Speicher ist nicht vollstaendig konfiguriert.");
    }

    Session session = null;
    ChannelSftp channel = null;
    try {
      var jsch = new JSch();
      if (!this.configuration.sftpPrivateKeyPath().isBlank()) {
        jsch.addIdentity(this.configuration.sftpPrivateKeyPath());
      }
      session = jsch.getSession(
        this.configuration.sftpUsername(),
        this.configuration.sftpHost(),
        this.configuration.sftpPort());
      if (!this.configuration.sftpPassword().isBlank()) {
        session.setPassword(this.configuration.sftpPassword());
      }
      var properties = new Properties();
      properties.setProperty("StrictHostKeyChecking", "no");
      session.setConfig(properties);
      session.connect(10_000);
      channel = (ChannelSftp) session.openChannel("sftp");
      channel.connect(10_000);

      var directory = remotePath(this.configuration.sftpRemoteDirectory(), safeName(appealId));
      mkdirs(channel, directory);
      var storedName = UUID.randomUUID() + "-" + safeName(file.fileName());
      var target = remotePath(directory, storedName);
      channel.put(new ByteArrayInputStream(file.content()), target);
      return new StoredEvidence("SFTP", target);
    } catch (Exception exception) {
      throw new IllegalStateException("Beweisdatei konnte per SFTP nicht gespeichert werden: " + exception.getMessage(), exception);
    } finally {
      if (channel != null) {
        channel.disconnect();
      }
      if (session != null) {
        session.disconnect();
      }
    }
  }

  private static void mkdirs(ChannelSftp channel, String directory) throws Exception {
    var current = "";
    for (var part : directory.split("/")) {
      if (part.isBlank()) {
        continue;
      }
      current += "/" + part;
      try {
        channel.cd(current);
      } catch (Exception exception) {
        channel.mkdir(current);
      }
    }
  }

  private static String remotePath(String first, String second) {
    var base = first == null || first.isBlank() ? "/appeals" : first.trim();
    return base.replaceAll("/+$", "") + "/" + second;
  }

  private static String safeName(String value) {
    var safe = value == null || value.isBlank() ? "file" : value.trim();
    return safe.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
