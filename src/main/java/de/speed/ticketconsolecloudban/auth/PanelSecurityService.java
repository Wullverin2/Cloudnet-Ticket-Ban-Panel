package de.speed.ticketconsolecloudban.auth;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.store.PanelUserStore;
import eu.cloudnetservice.driver.document.Document;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PanelSecurityService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PanelSecurityService.class);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PanelUserStore userStore;
  private final PanelConfiguration configuration;
  private final SmtpMailService mailService;
  private final Map<String, String> sessions = new ConcurrentHashMap<>();

  public PanelSecurityService(PanelUserStore userStore, PanelConfiguration configuration) {
    this.userStore = userStore;
    this.configuration = configuration;
    this.mailService = new SmtpMailService(configuration);
  }

  public PanelPrincipal authenticate(String token, PanelConfiguration configuration) {
    if (configuration.acceptsToken(token)) {
      return new PanelPrincipal(
        "api-token",
        "API Token",
        List.of(PanelPermission.ALL),
        true,
        null);
    }

    if (token == null || token.isBlank()) {
      return null;
    }

    var username = this.sessions.get(token);
    if (username == null) {
      return null;
    }

    var user = this.userStore.findUser(username)
      .filter(PanelUser::enabled)
      .orElse(null);
    if (user == null) {
      this.sessions.remove(token);
      return null;
    }

    return this.principal(token, user);
  }

  public LoginView login(Document request) {
    var user = this.userStore.authenticate(
      this.requiredText(request, "username"),
      this.requiredText(request, "password"));
    var token = this.newToken();
    this.sessions.put(token, user.username());
    return new LoginView(token, this.userView(user), PanelPermission.catalog());
  }

  public PasswordResetRequestView requestPasswordReset(Document request) {
    var identifier = this.requiredText(request, "usernameOrEmail");
    var issue = this.userStore.createPasswordReset(identifier, this.configuration.passwordResetTokenMinutes());
    issue.ifPresent(reset -> {
      var resetUrl = this.configuration.publicBaseUrl() + "/?resetToken=" + reset.rawToken();
      if (this.mailService.enabled()) {
        this.mailService.sendPasswordReset(reset.email(), resetUrl, reset.expiresAt());
      } else {
        LOGGER.warn("Password reset requested for {} but SMTP is disabled. Reset URL: {}", reset.username(), resetUrl);
      }
    });

    return new PasswordResetRequestView(
      "Wenn ein passender Benutzer mit E-Mail existiert, wurde ein Reset-Link versendet.",
      this.mailService.enabled());
  }

  public UserView completePasswordReset(Document request) {
    var user = this.userStore.resetPassword(
      this.requiredText(request, "token"),
      this.requiredText(request, "newPassword"));
    return this.userView(user);
  }

  public void logout(PanelPrincipal principal) {
    if (principal != null && principal.sessionToken() != null) {
      this.sessions.remove(principal.sessionToken());
    }
  }

  public CurrentSessionView currentSession(PanelPrincipal principal) {
    return new CurrentSessionView(
      principal.apiToken(),
      principal.apiToken()
        ? new UserView(
          principal.username(),
          principal.displayName(),
          null,
          null,
          null,
          List.of("api-token"),
          true,
          principal.permissions(),
          null,
          null,
          null)
        : this.userView(this.userStore.findUser(principal.username()).orElseThrow()),
      PanelPermission.catalog(),
      Instant.now().toString());
  }

  public List<UserView> listUsers() {
    return this.userStore.listUsers().stream()
      .map(this::userView)
      .toList();
  }

  public UserView createUser(Document request) {
    var user = this.userStore.createUser(
      this.requiredText(request, "username"),
      this.textOrNull(request, "displayName"),
      this.requiredText(request, "password"),
      this.stringValues(request, "groups", List.of()),
      request.getBoolean("enabled", true));
    return this.userView(user);
  }

  public UserView updateUser(String username, Document request) {
    var user = this.userStore.updateUser(
      username,
      this.textOrNull(request, "displayName"),
      this.textOrNull(request, "password"),
      request.contains("groups") ? this.stringValues(request, "groups", List.of()) : null,
      request.contains("enabled") ? request.getBoolean("enabled") : null);
    return this.userView(user);
  }

  public UserView updateOwnProfile(PanelPrincipal principal, Document request) {
    if (principal.apiToken()) {
      throw new IllegalArgumentException("API-Token-Sessions haben kein Benutzerprofil.");
    }
    var user = this.userStore.updateProfile(
      principal.username(),
      this.textOrNull(request, "email"),
      this.textOrNull(request, "minecraftName"),
      this.textOrNull(request, "minecraftUniqueId"));
    return this.userView(user);
  }

  public UserView changeOwnPassword(PanelPrincipal principal, Document request) {
    if (principal.apiToken()) {
      throw new IllegalArgumentException("API-Token-Sessions haben kein Passwort.");
    }
    var user = this.userStore.changePassword(
      principal.username(),
      this.requiredText(request, "currentPassword"),
      this.requiredText(request, "newPassword"));
    return this.userView(user);
  }

  public void deleteUser(String username) {
    this.userStore.deleteUser(username);
    this.sessions.entrySet().removeIf(entry -> entry.getValue().equalsIgnoreCase(username));
  }

  public List<GroupView> listGroups() {
    return this.userStore.listGroups().stream()
      .map(this::groupView)
      .toList();
  }

  public GroupView upsertGroup(Document request) {
    var group = this.userStore.upsertGroup(
      this.requiredText(request, "name"),
      this.stringValues(request, "permissions", List.of()));
    return this.groupView(group);
  }

  public GroupView updateGroup(String name, Document request) {
    var group = this.userStore.upsertGroup(
      name,
      this.stringValues(request, "permissions", List.of()));
    return this.groupView(group);
  }

  public void deleteGroup(String name) {
    this.userStore.deleteGroup(name);
  }

  private PanelPrincipal principal(String token, PanelUser user) {
    return new PanelPrincipal(
      user.username(),
      user.displayName(),
      this.userStore.permissionsFor(user),
      false,
      token);
  }

  private UserView userView(PanelUser user) {
    return new UserView(
      user.username(),
      user.displayName(),
      user.email(),
      user.minecraftName(),
      user.minecraftUniqueId(),
      user.groups() == null ? List.of() : user.groups(),
      user.enabled(),
      this.userStore.permissionsFor(user),
      user.createdAt(),
      user.updatedAt(),
      user.lastLoginAt());
  }

  private GroupView groupView(PanelGroup group) {
    return new GroupView(
      group.name(),
      group.permissions() == null ? List.of() : group.permissions(),
      group.system(),
      group.createdAt(),
      group.updatedAt());
  }

  private List<String> stringValues(Document request, String key, Collection<String> fallback) {
    if (!request.contains(key)) {
      return List.copyOf(fallback);
    }

    var values = request.readObject(key, String[].class, new String[0]);
    var ordered = new LinkedHashSet<String>();
    for (var value : values) {
      if (value != null && !value.isBlank()) {
        ordered.add(value.trim());
      }
    }
    return List.copyOf(ordered);
  }

  private String requiredText(Document request, String key) {
    var value = request.getString(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Feld '" + key + "' ist erforderlich.");
    }
    return value.trim();
  }

  private String textOrNull(Document request, String key) {
    if (!request.containsNonNull(key)) {
      return null;
    }
    var value = request.getString(key);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String newToken() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  public record LoginView(
    String token,
    UserView user,
    List<PanelPermission.PermissionView> availablePermissions
  ) {
  }

  public record PasswordResetRequestView(
    String message,
    boolean mailEnabled
  ) {
  }

  public record CurrentSessionView(
    boolean apiToken,
    UserView user,
    List<PanelPermission.PermissionView> availablePermissions,
    String generatedAt
  ) {
  }

  public record UserView(
    String username,
    String displayName,
    String email,
    String minecraftName,
    String minecraftUniqueId,
    List<String> groups,
    boolean enabled,
    List<String> permissions,
    String createdAt,
    String updatedAt,
    String lastLoginAt
  ) {
  }

  public record GroupView(
    String name,
    List<String> permissions,
    boolean system,
    String createdAt,
    String updatedAt
  ) {
  }
}
