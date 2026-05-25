package de.speed.ticketconsolecloudban.auth;

import java.util.List;

public record PanelUserStoreData(
  List<PanelUser> users,
  List<PanelGroup> groups,
  List<PasswordResetToken> passwordResetTokens,
  List<PanelSession> sessions
) {
}
