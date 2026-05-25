package de.speed.ticketconsolecloudban.auth;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import de.speed.ticketconsolecloudban.store.PanelUserStore;
import eu.cloudnetservice.driver.document.Document;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  private final Map<String, TwoFactorChallenge> twoFactorChallenges = new ConcurrentHashMap<>();
  private final Map<String, PendingTotpSetup> pendingTotpSetups = new ConcurrentHashMap<>();

  public PanelSecurityService(PanelUserStore userStore, PanelConfiguration configuration) {
    this(userStore, configuration, null);
  }

  public PanelSecurityService(PanelUserStore userStore, PanelConfiguration configuration, PanelSettingsStore settingsStore) {
    this.userStore = userStore;
    this.configuration = configuration;
    this.mailService = settingsStore == null
      ? new SmtpMailService(configuration)
      : new SmtpMailService(configuration, settingsStore);
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

    return this.userStore.findUserBySessionToken(token)
      .map(user -> this.principal(token, user))
      .orElse(null);
  }

  public LoginView login(Document request) {
    var user = this.userStore.verifyCredentials(
      this.requiredText(request, "username"),
      this.requiredText(request, "password"));
    var method = TwoFactorMethod.parse(user.twoFactorMethod());
    if (method.enabled()) {
      return this.beginTwoFactorLogin(user, method);
    }
    return this.createSessionLoginView(this.userStore.recordLogin(user.username()));
  }

  public LoginView verifyTwoFactor(Document request) {
    var challengeId = this.requiredText(request, "challengeId");
    var code = this.requiredText(request, "code").replace(" ", "");
    var challenge = this.twoFactorChallenges.get(challengeId);
    if (challenge == null) {
      throw new IllegalArgumentException("2FA-Anfrage ist ungültig oder abgelaufen.");
    }
    if (Instant.parse(challenge.expiresAt()).isBefore(Instant.now())) {
      this.twoFactorChallenges.remove(challengeId);
      throw new IllegalArgumentException("2FA-Code ist abgelaufen. Bitte melde dich erneut an.");
    }
    if (challenge.attempts() >= 5) {
      this.twoFactorChallenges.remove(challengeId);
      throw new IllegalArgumentException("Zu viele falsche 2FA-Versuche. Bitte melde dich erneut an.");
    }

    var user = this.userStore.findUser(challenge.username())
      .filter(PanelUser::enabled)
      .orElseThrow(() -> new IllegalArgumentException("Benutzer wurde nicht gefunden."));
    var valid = switch (challenge.method()) {
      case EMAIL -> secureEquals(challenge.codeHash(), sha256(code));
      case TOTP -> TotpService.verify(code, user.twoFactorSecret());
      case NONE -> false;
    };
    if (!valid) {
      this.twoFactorChallenges.put(challengeId, challenge.incrementAttempts());
      throw new IllegalArgumentException("2FA-Code ist falsch.");
    }

    this.twoFactorChallenges.remove(challengeId);
    return this.createSessionLoginView(this.userStore.recordLogin(user.username()));
  }

  public TotpSetupView prepareTotpSetup(PanelPrincipal principal) {
    if (principal.apiToken()) {
      throw new IllegalArgumentException("API-Token-Sessions können keine 2FA einrichten.");
    }

    var user = this.userStore.findUser(principal.username()).orElseThrow();
    var secret = TotpService.newSecret();
    var expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES).toString();
    this.pendingTotpSetups.put(user.username(), new PendingTotpSetup(user.username(), secret, expiresAt));
    return new TotpSetupView(
      secret,
      TotpService.otpauthUri(this.configuration.brandName(), user.username(), secret),
      expiresAt);
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
      this.userStore.deleteSession(principal.sessionToken());
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
          TwoFactorMethod.NONE.name(),
          false,
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
      this.textOrNull(request, "minecraftUniqueId"),
      this.textOrNull(request, "twoFactorMethod"),
      this.resolveTwoFactorSecret(principal.username(), request));
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
    this.userStore.deleteSessionsForUser(username);
    this.pendingTotpSetups.remove(username.toLowerCase());
    this.twoFactorChallenges.entrySet().removeIf(entry -> entry.getValue().username().equalsIgnoreCase(username));
  }

  private LoginView beginTwoFactorLogin(PanelUser user, TwoFactorMethod method) {
    this.cleanupTwoFactorChallenges();
    var challengeId = this.newToken();
    var expiresAt = Instant.now().plus(10, ChronoUnit.MINUTES).toString();
    var destination = "";
    var message = method == TwoFactorMethod.TOTP
      ? "Bitte gib den Code aus deiner Authenticator-App ein."
      : "Bitte gib den Code ein, den wir dir per E-Mail gesendet haben.";
    var codeHash = "";

    if (method == TwoFactorMethod.EMAIL) {
      if (user.email() == null || user.email().isBlank()) {
        throw new IllegalArgumentException("E-Mail-2FA ist aktiv, aber für diesen Benutzer ist keine E-Mail hinterlegt.");
      }
      if (!this.mailService.enabled()) {
        throw new IllegalArgumentException("E-Mail-2FA ist aktiv, aber der Mailserver ist nicht verfügbar. Bitte kontaktiere das Team.");
      }
      var code = this.randomSixDigitCode();
      codeHash = sha256(code);
      destination = maskedEmail(user.email());
      this.mailService.sendTwoFactorCode(user.email(), code, expiresAt);
    } else if (method == TwoFactorMethod.TOTP) {
      if (user.twoFactorSecret() == null || user.twoFactorSecret().isBlank()) {
        throw new IllegalArgumentException("Authenticator-2FA ist aktiv, aber nicht vollständig eingerichtet.");
      }
      destination = "Authenticator-App";
    }

    this.twoFactorChallenges.put(challengeId, new TwoFactorChallenge(
      challengeId,
      user.username(),
      method,
      codeHash,
      expiresAt,
      0));
    return new LoginView(
      null,
      null,
      PanelPermission.catalog(),
      true,
      challengeId,
      method.name(),
      destination,
      message);
  }

  private LoginView createSessionLoginView(PanelUser user) {
    var session = this.userStore.createSession(user.username());
    return new LoginView(
      session.token(),
      this.userView(user),
      PanelPermission.catalog(),
      false,
      null,
      TwoFactorMethod.NONE.name(),
      null,
      null);
  }

  private String resolveTwoFactorSecret(String username, Document request) {
    var method = request.containsNonNull("twoFactorMethod")
      ? TwoFactorMethod.parse(request.getString("twoFactorMethod"))
      : null;
    if (method != TwoFactorMethod.TOTP) {
      return null;
    }

    var current = this.userStore.findUser(username).orElseThrow();
    var setup = this.pendingTotpSetups.get(username.toLowerCase());
    var hasExistingTotp = TwoFactorMethod.parse(current.twoFactorMethod()) == TwoFactorMethod.TOTP
      && current.twoFactorSecret() != null
      && !current.twoFactorSecret().isBlank();
    var code = this.textOrNull(request, "twoFactorTotpCode");
    if ((setup == null || Instant.parse(setup.expiresAt()).isBefore(Instant.now()) || code == null) && hasExistingTotp) {
      return current.twoFactorSecret();
    }
    if (setup == null || Instant.parse(setup.expiresAt()).isBefore(Instant.now())) {
      this.pendingTotpSetups.remove(username.toLowerCase());
      throw new IllegalArgumentException("Bitte bereite zuerst die Authenticator-App vor.");
    }

    if (!TotpService.verify(code, setup.secret())) {
      throw new IllegalArgumentException("Authenticator-Code ist falsch.");
    }

    this.pendingTotpSetups.remove(username.toLowerCase());
    return setup.secret();
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
      TwoFactorMethod.parse(user.twoFactorMethod()).name(),
      TwoFactorMethod.parse(user.twoFactorMethod()).enabled(),
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

  private void cleanupTwoFactorChallenges() {
    var now = Instant.now();
    this.twoFactorChallenges.entrySet().removeIf(entry -> Instant.parse(entry.getValue().expiresAt()).isBefore(now));
  }

  private String randomSixDigitCode() {
    return String.format("%06d", RANDOM.nextInt(1_000_000));
  }

  private static String maskedEmail(String email) {
    if (email == null || !email.contains("@")) {
      return "hinterlegte E-Mail";
    }
    var parts = email.split("@", 2);
    var local = parts[0];
    if (local.isBlank()) {
      return "***@" + parts[1];
    }
    var maskedLocal = local.length() <= 2
      ? local.charAt(0) + "*"
      : local.substring(0, 2) + "*".repeat(Math.min(6, local.length() - 2));
    return maskedLocal + "@" + parts[1];
  }

  private static String sha256(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("Code konnte nicht verarbeitet werden.", exception);
    }
  }

  private static boolean secureEquals(String left, String right) {
    if (left == null || right == null) {
      return false;
    }
    return MessageDigest.isEqual(
      left.getBytes(StandardCharsets.UTF_8),
      right.getBytes(StandardCharsets.UTF_8));
  }

  private String newToken() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  public record LoginView(
    String token,
    UserView user,
    List<PanelPermission.PermissionView> availablePermissions,
    boolean twoFactorRequired,
    String twoFactorChallengeId,
    String twoFactorMethod,
    String twoFactorDestination,
    String message
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
    String twoFactorMethod,
    boolean twoFactorEnabled,
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

  public record TotpSetupView(
    String secret,
    String otpauthUri,
    String expiresAt
  ) {
  }

  private record TwoFactorChallenge(
    String id,
    String username,
    TwoFactorMethod method,
    String codeHash,
    String expiresAt,
    int attempts
  ) {

    private TwoFactorChallenge incrementAttempts() {
      return new TwoFactorChallenge(this.id, this.username, this.method, this.codeHash, this.expiresAt, this.attempts + 1);
    }
  }

  private record PendingTotpSetup(
    String username,
    String secret,
    String expiresAt
  ) {
  }
}
