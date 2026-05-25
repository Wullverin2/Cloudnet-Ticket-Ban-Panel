package de.speed.ticketconsolecloudban.store;

import de.speed.ticketconsolecloudban.auth.PanelGroup;
import de.speed.ticketconsolecloudban.auth.PanelPermission;
import de.speed.ticketconsolecloudban.auth.PanelSession;
import de.speed.ticketconsolecloudban.auth.PanelUser;
import de.speed.ticketconsolecloudban.auth.PanelUserStoreData;
import de.speed.ticketconsolecloudban.auth.PasswordResetToken;
import de.speed.ticketconsolecloudban.auth.TwoFactorMethod;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PanelUserStore {

  private static final int PASSWORD_ITERATIONS = 160_000;
  private static final int PASSWORD_KEY_LENGTH = 256;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Path storagePath;
  private final PanelDataBackend backend;
  private final List<PanelUser> users = new ArrayList<>();
  private final List<PanelGroup> groups = new ArrayList<>();
  private final List<PasswordResetToken> passwordResetTokens = new ArrayList<>();
  private final List<PanelSession> sessions = new ArrayList<>();
  private String initialAdminPassword;

  public PanelUserStore(Path dataDirectory) {
    this(dataDirectory, new LocalPanelDataBackend());
  }

  public PanelUserStore(Path dataDirectory, PanelDataBackend backend) {
    this.storagePath = dataDirectory.resolve("panel-users.json");
    this.backend = backend;
    this.load();
  }

  public synchronized Optional<String> initialAdminPassword() {
    return Optional.ofNullable(this.initialAdminPassword);
  }

  public synchronized List<PanelUser> listUsers() {
    return this.users.stream()
      .sorted(Comparator.comparing(PanelUser::username))
      .toList();
  }

  public synchronized List<PanelGroup> listGroups() {
    return this.groups.stream()
      .sorted(Comparator.comparing(PanelGroup::name))
      .toList();
  }

  public synchronized Optional<PanelUser> findUser(String username) {
    var normalized = normalizeName(username);
    return this.users.stream()
      .filter(user -> user.username().equals(normalized))
      .findFirst();
  }

  public synchronized PanelUser authenticate(String username, String password) {
    var user = this.verifyCredentials(username, password);
    return this.recordLogin(user.username());
  }

  public synchronized PanelUser verifyCredentials(String username, String password) {
    var user = this.findUser(username)
      .filter(PanelUser::enabled)
      .orElseThrow(() -> new IllegalArgumentException("Login fehlgeschlagen."));

    if (!this.verifyPassword(password, user)) {
      throw new IllegalArgumentException("Login fehlgeschlagen.");
    }

    return user;
  }

  public synchronized PanelUser recordLogin(String username) {
    var user = this.requireUser(username);
    var now = Instant.now().toString();
    var updated = new PanelUser(
      user.username(),
      user.displayName(),
      user.email(),
      user.minecraftName(),
      user.minecraftUniqueId(),
      normalizedTwoFactorMethod(user.twoFactorMethod()).name(),
      user.twoFactorSecret(),
      user.passwordHash(),
      user.passwordSalt(),
      user.passwordIterations(),
      user.groups(),
      user.enabled(),
      user.createdAt(),
      now,
      now);
    this.replaceUser(updated);
    return updated;
  }

  public synchronized SessionIssue createSession(String username) {
    var user = this.requireUser(username);
    var rawToken = HexFormat.of().formatHex(randomBytes(32));
    var now = Instant.now();
    var expiresAt = now.plus(30, ChronoUnit.DAYS).toString();
    this.cleanupExpiredSessions(now);
    var session = new PanelSession(
      sha256(rawToken),
      user.username(),
      now.toString(),
      now.toString(),
      expiresAt);
    this.sessions.add(session);
    this.save();
    return new SessionIssue(rawToken, expiresAt);
  }

  public synchronized Optional<PanelUser> findUserBySessionToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }

    var now = Instant.now();
    var changed = this.cleanupExpiredSessions(now);
    var tokenHash = sha256(rawToken.trim());
    PanelSession matchedSession = null;
    for (var session : this.sessions) {
      if (secureEquals(session.tokenHash(), tokenHash)) {
        matchedSession = session;
        break;
      }
    }

    if (matchedSession == null) {
      if (changed) {
        this.save();
      }
      return Optional.empty();
    }

    var user = this.findUser(matchedSession.username())
      .filter(PanelUser::enabled)
      .orElse(null);
    if (user == null) {
      this.sessions.remove(matchedSession);
      this.save();
      return Optional.empty();
    }

    if (this.shouldRefreshLastSeen(matchedSession, now)) {
      this.replaceSession(matchedSession.withLastSeenAt(now.toString()));
      this.save();
    } else if (changed) {
      this.save();
    }

    return Optional.of(user);
  }

  public synchronized void deleteSession(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }

    var tokenHash = sha256(rawToken.trim());
    if (this.sessions.removeIf(session -> secureEquals(session.tokenHash(), tokenHash))) {
      this.save();
    }
  }

  public synchronized void deleteSessionsForUser(String username) {
    var normalized = normalizeName(username);
    if (this.sessions.removeIf(session -> session.username() != null && session.username().equals(normalized))) {
      this.save();
    }
  }

  public synchronized PanelUser createUser(
    String username,
    String displayName,
    String password,
    List<String> requestedGroups,
    boolean enabled
  ) {
    var normalized = normalizeName(username);
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Benutzername ist erforderlich.");
    }
    if (this.findUser(normalized).isPresent()) {
      throw new IllegalArgumentException("Dieser Benutzer existiert bereits.");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("Passwort ist erforderlich.");
    }

    var passwordMaterial = this.hashPassword(password);
    var now = Instant.now().toString();
    var user = new PanelUser(
      normalized,
      displayName == null || displayName.isBlank() ? normalized : displayName.trim(),
      null,
      null,
      null,
      TwoFactorMethod.NONE.name(),
      null,
      passwordMaterial.hash(),
      passwordMaterial.salt(),
      passwordMaterial.iterations(),
      this.normalizeGroups(requestedGroups),
      enabled,
      now,
      now,
      null);

    var nextUsers = new ArrayList<>(this.users);
    nextUsers.add(user);
    this.ensureAdminAccess(nextUsers, this.groups);
    this.users.add(user);
    this.save();
    return user;
  }

  public synchronized PanelUser updateUser(
    String username,
    String displayName,
    String password,
    List<String> requestedGroups,
    Boolean enabled
  ) {
    var user = this.requireUser(username);
    var passwordHash = user.passwordHash();
    var passwordSalt = user.passwordSalt();
    var passwordIterations = user.passwordIterations();

    if (password != null && !password.isBlank()) {
      var material = this.hashPassword(password);
      passwordHash = material.hash();
      passwordSalt = material.salt();
      passwordIterations = material.iterations();
    }

    var updated = new PanelUser(
      user.username(),
      displayName == null || displayName.isBlank() ? user.displayName() : displayName.trim(),
      user.email(),
      user.minecraftName(),
      user.minecraftUniqueId(),
      normalizedTwoFactorMethod(user.twoFactorMethod()).name(),
      user.twoFactorSecret(),
      passwordHash,
      passwordSalt,
      passwordIterations,
      requestedGroups == null ? user.groups() : this.normalizeGroups(requestedGroups),
      enabled == null ? user.enabled() : enabled,
      user.createdAt(),
      Instant.now().toString(),
      user.lastLoginAt());

    var nextUsers = this.replacePreview(this.users, updated);
    this.ensureAdminAccess(nextUsers, this.groups);
    this.replaceUser(updated);
    return updated;
  }

  public synchronized PanelUser updateProfile(
    String username,
    String email,
    String minecraftName,
    String minecraftUniqueId,
    String twoFactorMethod,
    String twoFactorSecret
  ) {
    var user = this.requireUser(username);
    var normalizedEmail = this.nullableText(email);
    var method = twoFactorMethod == null
      ? normalizedTwoFactorMethod(user.twoFactorMethod())
      : TwoFactorMethod.parse(twoFactorMethod);
    if (method == TwoFactorMethod.EMAIL && normalizedEmail == null) {
      throw new IllegalArgumentException("Für E-Mail-2FA muss im Profil eine E-Mail-Adresse hinterlegt sein.");
    }
    var nextSecret = method == TwoFactorMethod.TOTP
      ? this.nullableText(twoFactorSecret) == null ? user.twoFactorSecret() : this.nullableText(twoFactorSecret)
      : null;
    if (method == TwoFactorMethod.TOTP && (nextSecret == null || nextSecret.isBlank())) {
      throw new IllegalArgumentException("Für die Authenticator-App muss zuerst ein Schlüssel vorbereitet und bestätigt werden.");
    }

    var updated = new PanelUser(
      user.username(),
      user.displayName(),
      normalizedEmail,
      this.nullableText(minecraftName),
      this.nullableText(minecraftUniqueId),
      method.name(),
      nextSecret,
      user.passwordHash(),
      user.passwordSalt(),
      user.passwordIterations(),
      user.groups(),
      user.enabled(),
      user.createdAt(),
      Instant.now().toString(),
      user.lastLoginAt());
    this.replaceUser(updated);
    return updated;
  }

  public synchronized PanelUser changePassword(String username, String currentPassword, String newPassword) {
    var user = this.requireUser(username);
    if (!this.verifyPassword(currentPassword, user)) {
      throw new IllegalArgumentException("Aktuelles Passwort ist falsch.");
    }
    if (newPassword == null || newPassword.length() < 8) {
      throw new IllegalArgumentException("Das neue Passwort muss mindestens 8 Zeichen haben.");
    }

    var material = this.hashPassword(newPassword);
    var updated = new PanelUser(
      user.username(),
      user.displayName(),
      user.email(),
      user.minecraftName(),
      user.minecraftUniqueId(),
      normalizedTwoFactorMethod(user.twoFactorMethod()).name(),
      user.twoFactorSecret(),
      material.hash(),
      material.salt(),
      material.iterations(),
      user.groups(),
      user.enabled(),
      user.createdAt(),
      Instant.now().toString(),
      user.lastLoginAt());
    this.replaceUser(updated);
    return updated;
  }

  public synchronized Optional<PasswordResetIssue> createPasswordReset(String usernameOrEmail, int validMinutes) {
    var lookup = usernameOrEmail == null ? "" : usernameOrEmail.trim();
    if (lookup.isBlank()) {
      return Optional.empty();
    }

    var user = this.users.stream()
      .filter(PanelUser::enabled)
      .filter(entry -> entry.username().equalsIgnoreCase(lookup)
        || (entry.email() != null && entry.email().equalsIgnoreCase(lookup)))
      .findFirst()
      .orElse(null);
    if (user == null || user.email() == null || user.email().isBlank()) {
      return Optional.empty();
    }

    this.passwordResetTokens.removeIf(token -> token.username().equals(user.username()) && token.usedAt() == null);
    var rawToken = HexFormat.of().formatHex(randomBytes(32));
    var now = Instant.now();
    var token = new PasswordResetToken(
      sha256(rawToken),
      user.username(),
      user.email(),
      now.toString(),
      now.plus(validMinutes, ChronoUnit.MINUTES).toString(),
      null);
    this.passwordResetTokens.add(token);
    this.save();
    return Optional.of(new PasswordResetIssue(user.username(), user.email(), rawToken, token.expiresAt()));
  }

  public synchronized PanelUser resetPassword(String rawToken, String newPassword) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("Reset-Token ist erforderlich.");
    }
    if (newPassword == null || newPassword.length() < 8) {
      throw new IllegalArgumentException("Das neue Passwort muss mindestens 8 Zeichen haben.");
    }

    var tokenHash = sha256(rawToken.trim());
    var token = this.passwordResetTokens.stream()
      .filter(entry -> entry.tokenHash().equals(tokenHash))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Reset-Token ist ungültig."));
    if (token.usedAt() != null) {
      throw new IllegalArgumentException("Reset-Token wurde bereits benutzt.");
    }
    if (Instant.parse(token.expiresAt()).isBefore(Instant.now())) {
      throw new IllegalArgumentException("Reset-Token ist abgelaufen.");
    }

    var user = this.requireUser(token.username());
    var material = this.hashPassword(newPassword);
    var updated = new PanelUser(
      user.username(),
      user.displayName(),
      user.email(),
      user.minecraftName(),
      user.minecraftUniqueId(),
      normalizedTwoFactorMethod(user.twoFactorMethod()).name(),
      user.twoFactorSecret(),
      material.hash(),
      material.salt(),
      material.iterations(),
      user.groups(),
      user.enabled(),
      user.createdAt(),
      Instant.now().toString(),
      user.lastLoginAt());

    var used = new PasswordResetToken(
      token.tokenHash(),
      token.username(),
      token.email(),
      token.createdAt(),
      token.expiresAt(),
      Instant.now().toString());
    this.replaceResetToken(used);
    this.replaceUser(updated);
    return updated;
  }

  public synchronized void deleteUser(String username) {
    var user = this.requireUser(username);
    var nextUsers = this.users.stream()
      .filter(entry -> !entry.username().equals(user.username()))
      .toList();
    this.ensureAdminAccess(nextUsers, this.groups);
    this.users.removeIf(entry -> entry.username().equals(user.username()));
    this.save();
  }

  public synchronized PanelGroup upsertGroup(String name, List<String> permissions) {
    var normalized = normalizeName(name);
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Gruppenname ist erforderlich.");
    }

    var existing = this.groups.stream()
      .filter(group -> group.name().equals(normalized))
      .findFirst()
      .orElse(null);
    var now = Instant.now().toString();
    var group = new PanelGroup(
      normalized,
      this.normalizePermissions(permissions),
      existing != null && existing.system(),
      existing == null ? now : existing.createdAt(),
      now);

    var nextGroups = existing == null
      ? this.addPreview(this.groups, group)
      : this.replacePreview(this.groups, group);
    this.ensureAdminAccess(this.users, nextGroups);

    if (existing == null) {
      this.groups.add(group);
    } else {
      this.replaceGroup(group);
    }
    this.save();
    return group;
  }

  public synchronized void deleteGroup(String name) {
    var group = this.requireGroup(name);
    if (group.system()) {
      throw new IllegalArgumentException("Systemgruppen können nicht gelöscht werden.");
    }

    var nextGroups = this.groups.stream()
      .filter(entry -> !entry.name().equals(group.name()))
      .toList();
    this.ensureAdminAccess(this.users, nextGroups);
    this.groups.removeIf(entry -> entry.name().equals(group.name()));
    this.users.replaceAll(user -> new PanelUser(
      user.username(),
      user.displayName(),
      user.email(),
      user.minecraftName(),
      user.minecraftUniqueId(),
      normalizedTwoFactorMethod(user.twoFactorMethod()).name(),
      user.twoFactorSecret(),
      user.passwordHash(),
      user.passwordSalt(),
      user.passwordIterations(),
      user.groups().stream().filter(entry -> !entry.equals(group.name())).toList(),
      user.enabled(),
      user.createdAt(),
      Instant.now().toString(),
      user.lastLoginAt()));
    this.save();
  }

  public synchronized List<String> permissionsFor(PanelUser user) {
    return this.permissionsFor(user, this.groups);
  }

  private List<String> permissionsFor(PanelUser user, List<PanelGroup> sourceGroups) {
    var permissions = new LinkedHashSet<String>();
    for (var groupName : safeList(user.groups())) {
      sourceGroups.stream()
        .filter(group -> group.name().equals(groupName))
        .findFirst()
        .ifPresent(group -> permissions.addAll(safeList(group.permissions())));
    }
    return List.copyOf(permissions);
  }

  private void ensureAdminAccess(List<PanelUser> sourceUsers, List<PanelGroup> sourceGroups) {
    var hasAdmin = sourceUsers.stream()
      .filter(PanelUser::enabled)
      .anyMatch(user -> this.permissionsFor(user, sourceGroups).contains(PanelPermission.ALL));
    if (!hasAdmin) {
      throw new IllegalArgumentException("Mindestens ein aktivierter Benutzer muss Vollzugriff behalten.");
    }
  }

  private PanelUser requireUser(String username) {
    return this.findUser(username)
      .orElseThrow(() -> new IllegalArgumentException("Der Benutzer wurde nicht gefunden."));
  }

  private PanelGroup requireGroup(String name) {
    var normalized = normalizeName(name);
    return this.groups.stream()
      .filter(group -> group.name().equals(normalized))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Die Gruppe wurde nicht gefunden."));
  }

  private void replaceUser(PanelUser updated) {
    for (int index = 0; index < this.users.size(); index++) {
      if (this.users.get(index).username().equals(updated.username())) {
        this.users.set(index, updated);
        this.save();
        return;
      }
    }
    throw new IllegalArgumentException("Der Benutzer wurde nicht gefunden.");
  }

  private PanelUser withTwoFactorDefaults(PanelUser user) {
    var method = normalizedTwoFactorMethod(user.twoFactorMethod());
    var secret = method == TwoFactorMethod.TOTP ? this.nullableText(user.twoFactorSecret()) : null;
    return new PanelUser(
      user.username(),
      user.displayName(),
      user.email(),
      user.minecraftName(),
      user.minecraftUniqueId(),
      method.name(),
      secret,
      user.passwordHash(),
      user.passwordSalt(),
      user.passwordIterations(),
      user.groups(),
      user.enabled(),
      user.createdAt(),
      user.updatedAt(),
      user.lastLoginAt());
  }

  private void replaceGroup(PanelGroup updated) {
    for (int index = 0; index < this.groups.size(); index++) {
      if (this.groups.get(index).name().equals(updated.name())) {
        this.groups.set(index, updated);
        return;
      }
    }
    throw new IllegalArgumentException("Die Gruppe wurde nicht gefunden.");
  }

  private List<PanelUser> replacePreview(List<PanelUser> source, PanelUser updated) {
    return source.stream()
      .map(user -> user.username().equals(updated.username()) ? updated : user)
      .toList();
  }

  private List<PanelGroup> replacePreview(List<PanelGroup> source, PanelGroup updated) {
    return source.stream()
      .map(group -> group.name().equals(updated.name()) ? updated : group)
      .toList();
  }

  private List<PanelGroup> addPreview(List<PanelGroup> source, PanelGroup group) {
    var preview = new ArrayList<>(source);
    preview.add(group);
    return List.copyOf(preview);
  }

  private List<String> normalizeGroups(List<String> requestedGroups) {
    var normalized = new LinkedHashSet<String>();
    for (var group : safeList(requestedGroups)) {
      var groupName = normalizeName(group);
      if (groupName.isBlank()) {
        continue;
      }
      this.requireGroup(groupName);
      normalized.add(groupName);
    }
    return List.copyOf(normalized);
  }

  private List<String> normalizePermissions(List<String> permissions) {
    var normalized = new LinkedHashSet<String>();
    for (var permission : safeList(permissions)) {
      if (permission != null && !permission.isBlank()) {
        normalized.add(permission.trim());
      }
    }
    return List.copyOf(normalized);
  }

  private String nullableText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static TwoFactorMethod normalizedTwoFactorMethod(String value) {
    return value == null || value.isBlank() ? TwoFactorMethod.NONE : TwoFactorMethod.parse(value);
  }

  private boolean verifyPassword(String password, PanelUser user) {
    if (password == null) {
      return false;
    }

    var calculated = this.hashPassword(password, user.passwordSalt(), user.passwordIterations());
    return MessageDigest.isEqual(
      calculated.getBytes(StandardCharsets.UTF_8),
      user.passwordHash().getBytes(StandardCharsets.UTF_8));
  }

  private PasswordMaterial hashPassword(String password) {
    var salt = new byte[16];
    RANDOM.nextBytes(salt);
    return new PasswordMaterial(
      this.hashPassword(password, Base64.getEncoder().encodeToString(salt), PASSWORD_ITERATIONS),
      Base64.getEncoder().encodeToString(salt),
      PASSWORD_ITERATIONS);
  }

  private String hashPassword(String password, String salt, int iterations) {
    try {
      var spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), iterations, PASSWORD_KEY_LENGTH);
      var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
    } catch (Exception exception) {
      throw new IllegalStateException("Passwort konnte nicht verarbeitet werden.", exception);
    }
  }

  private void load() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      var data = this.backend.load("panel-users", this.storagePath, PanelUserStoreData.class, null);
      if (data == null) {
        this.bootstrap();
        return;
      }

      this.groups.clear();
      this.groups.addAll(data.groups() == null ? this.defaultGroups(Instant.now().toString()) : data.groups());
      this.users.clear();
      if (data.users() != null) {
        this.users.addAll(data.users());
        this.users.replaceAll(this::withTwoFactorDefaults);
      }
      this.passwordResetTokens.clear();
      if (data.passwordResetTokens() != null) {
        this.passwordResetTokens.addAll(data.passwordResetTokens());
      }
      this.sessions.clear();
      if (data.sessions() != null) {
        this.sessions.addAll(data.sessions());
      }
      this.cleanupExpiredSessions(Instant.now());

      if (this.users.isEmpty()) {
        this.bootstrap();
      } else {
        this.save();
      }
    } catch (Exception exception) {
      throw new IllegalStateException("Panel-Benutzer konnten nicht geladen werden.", exception);
    }
  }

  private void bootstrap() {
    var now = Instant.now().toString();
    this.groups.clear();
    this.groups.addAll(this.defaultGroups(now));

    this.initialAdminPassword = HexFormat.of().formatHex(randomBytes(12));
    var passwordMaterial = this.hashPassword(this.initialAdminPassword);
    this.users.clear();
    this.users.add(new PanelUser(
      "admin",
      "Administrator",
      null,
      null,
      null,
      TwoFactorMethod.NONE.name(),
      null,
      passwordMaterial.hash(),
      passwordMaterial.salt(),
      passwordMaterial.iterations(),
      List.of("admin"),
      true,
      now,
      now,
      null));
    this.save();
  }

  private List<PanelGroup> defaultGroups(String now) {
    return List.of(
      new PanelGroup("admin", List.of(PanelPermission.ALL), true, now, now),
      new PanelGroup("team", List.of(
        PanelPermission.CLOUDNET_VIEW,
        PanelPermission.CLOUDNET_CONSOLE,
        PanelPermission.TICKETS_VIEW,
        PanelPermission.TICKETS_CREATE,
        PanelPermission.TICKETS_MANAGE,
        PanelPermission.BANS_VIEW,
        PanelPermission.BANS_MANAGE,
        PanelPermission.QUEST_EDITOR_VIEW), false, now, now),
      new PanelGroup("viewer", List.of(
        PanelPermission.CLOUDNET_VIEW,
        PanelPermission.TICKETS_VIEW,
        PanelPermission.BANS_VIEW), false, now, now));
  }

  private void save() {
    try {
      Files.createDirectories(this.storagePath.getParent());
      this.backend.save(
        "panel-users",
        this.storagePath,
        new PanelUserStoreData(
          List.copyOf(this.users),
          List.copyOf(this.groups),
          List.copyOf(this.passwordResetTokens),
          List.copyOf(this.sessions)));
    } catch (Exception exception) {
      throw new IllegalStateException("Panel-Benutzer konnten nicht gespeichert werden.", exception);
    }
  }

  private static byte[] randomBytes(int length) {
    var bytes = new byte[length];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  private static String normalizeName(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private static List<String> safeList(List<String> values) {
    return values == null ? List.of() : values;
  }

  private void replaceResetToken(PasswordResetToken updated) {
    for (int index = 0; index < this.passwordResetTokens.size(); index++) {
      if (this.passwordResetTokens.get(index).tokenHash().equals(updated.tokenHash())) {
        this.passwordResetTokens.set(index, updated);
        return;
      }
    }
    throw new IllegalArgumentException("Reset-Token ist ungültig.");
  }

  private static String sha256(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("Token konnte nicht verarbeitet werden.", exception);
    }
  }

  private boolean cleanupExpiredSessions(Instant now) {
    return this.sessions.removeIf(session -> this.sessionExpired(session, now));
  }

  private boolean sessionExpired(PanelSession session, Instant now) {
    if (session == null || session.expiresAt() == null || session.expiresAt().isBlank()) {
      return true;
    }

    try {
      return Instant.parse(session.expiresAt()).isBefore(now);
    } catch (Exception exception) {
      return true;
    }
  }

  private boolean shouldRefreshLastSeen(PanelSession session, Instant now) {
    if (session.lastSeenAt() == null || session.lastSeenAt().isBlank()) {
      return true;
    }

    try {
      return Instant.parse(session.lastSeenAt()).plus(15, ChronoUnit.MINUTES).isBefore(now);
    } catch (Exception exception) {
      return true;
    }
  }

  private void replaceSession(PanelSession updated) {
    for (int index = 0; index < this.sessions.size(); index++) {
      if (secureEquals(this.sessions.get(index).tokenHash(), updated.tokenHash())) {
        this.sessions.set(index, updated);
        return;
      }
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

  private record PasswordMaterial(String hash, String salt, int iterations) {
  }

  public record PasswordResetIssue(
    String username,
    String email,
    String rawToken,
    String expiresAt
  ) {
  }

  public record SessionIssue(
    String token,
    String expiresAt
  ) {
  }
}
