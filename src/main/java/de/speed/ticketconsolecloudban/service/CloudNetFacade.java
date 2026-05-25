package de.speed.ticketconsolecloudban.service;

import de.speed.ticketconsolecloudban.auth.PanelPermission;
import de.speed.ticketconsolecloudban.auth.SmtpMailService;
import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.settings.PanelSettings;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import eu.cloudnetservice.driver.cluster.NodeInfoSnapshot;
import eu.cloudnetservice.driver.cluster.NetworkClusterNode;
import eu.cloudnetservice.driver.document.Document;
import eu.cloudnetservice.driver.provider.CloudServiceFactory;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.driver.provider.ClusterNodeProvider;
import eu.cloudnetservice.driver.provider.ServiceTaskProvider;
import eu.cloudnetservice.driver.provider.SpecificCloudServiceProvider;
import eu.cloudnetservice.driver.service.ServiceConfiguration;
import eu.cloudnetservice.driver.service.ServiceCreateResult;
import eu.cloudnetservice.driver.service.ServiceEnvironmentType;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.driver.service.ServiceLifeCycle;
import eu.cloudnetservice.driver.service.ServiceTask;
import eu.cloudnetservice.node.command.CommandProvider;
import eu.cloudnetservice.node.command.source.CommandSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class CloudNetFacade {

  private static final List<String> ENVIRONMENT_CHOICES = List.of(
    ServiceEnvironmentType.MINECRAFT_SERVER.name(),
    ServiceEnvironmentType.MODDED_MINECRAFT_SERVER.name(),
    ServiceEnvironmentType.VELOCITY.name(),
    ServiceEnvironmentType.BUNGEECORD.name(),
    ServiceEnvironmentType.MINESTOM.name(),
    ServiceEnvironmentType.LIMBO_LOOHP.name(),
    ServiceEnvironmentType.NUKKIT.name(),
    ServiceEnvironmentType.WATERDOG_PE.name());

  private final CloudServiceProvider cloudServiceProvider;
  private final ServiceTaskProvider serviceTaskProvider;
  private final CloudServiceFactory cloudServiceFactory;
  private final ClusterNodeProvider clusterNodeProvider;
  private final CommandProvider commandProvider;
  private final PanelConfiguration configuration;
  private final PanelSettingsStore settingsStore;
  private final CloudNetRestConsoleClient cloudNetRestConsoleClient;

  public CloudNetFacade(
    CloudServiceProvider cloudServiceProvider,
    ServiceTaskProvider serviceTaskProvider,
    CloudServiceFactory cloudServiceFactory,
    ClusterNodeProvider clusterNodeProvider,
    CommandProvider commandProvider,
    PanelConfiguration configuration,
    PanelSettingsStore settingsStore
  ) {
    this.cloudServiceProvider = cloudServiceProvider;
    this.serviceTaskProvider = serviceTaskProvider;
    this.cloudServiceFactory = cloudServiceFactory;
    this.clusterNodeProvider = clusterNodeProvider;
    this.commandProvider = commandProvider;
    this.configuration = configuration;
    this.settingsStore = settingsStore;
    this.cloudNetRestConsoleClient = new CloudNetRestConsoleClient(settingsStore, configuration.consoleLineLimit());
  }

  public MetaView meta() {
    return new MetaView(
      this.brandName(),
      this.brandLogoUrl(),
      ENVIRONMENT_CHOICES,
      List.of("jvm"),
      PanelPermission.catalog(),
      Instant.now().toString());
  }

  public OverviewView overview() {
    var services = this.cloudServiceProvider.services();
    var tasks = this.serviceTaskProvider.serviceTasks();
    var nodes = this.clusterNodeProvider.nodes();
    var snapshots = this.clusterNodeProvider.nodeInfoSnapshots();

    var servicesByEnvironment = new TreeMap<String, Long>();
    for (var service : services) {
      servicesByEnvironment.merge(service.configuration().processConfig().environment(), 1L, Long::sum);
    }

    var tasksByEnvironment = new TreeMap<String, Long>();
    for (var task : tasks) {
      tasksByEnvironment.merge(task.processConfiguration().environment(), 1L, Long::sum);
    }

    long runningServices = services.stream()
      .filter(service -> service.lifeCycle() == ServiceLifeCycle.RUNNING)
      .count();

    return new OverviewView(
      this.brandName(),
      tasks.size(),
      services.size(),
      runningServices,
      nodes.size(),
      snapshots.size(),
      servicesByEnvironment,
      tasksByEnvironment,
      Instant.now().toString());
  }

  public List<TaskView> listTasks() {
    return this.serviceTaskProvider.serviceTasks().stream()
      .sorted(Comparator.comparing(ServiceTask::name))
      .map(this::taskView)
      .toList();
  }

  public TaskView createTask(Document request) {
    var taskName = this.requiredText(request, "name");
    if (this.serviceTaskProvider.serviceTask(taskName) != null) {
      throw new IllegalArgumentException("Eine Task mit diesem Namen existiert bereits.");
    }

    var task = this.buildTask(null, request, taskName);
    if (!this.serviceTaskProvider.addServiceTask(task)) {
      throw new IllegalStateException("Die Task konnte nicht erstellt werden.");
    }

    return this.taskView(task);
  }

  public TaskView updateTask(String taskName, Document request) {
    var existing = this.requireTask(taskName);
    if (request.containsNonNull("name")) {
      var requestedName = request.getString("name");
      if (!taskName.equals(requestedName)) {
        throw new IllegalArgumentException("Das Umbenennen von Tasks ist in diesem MVP nicht aktiviert.");
      }
    }

    var updated = this.buildTask(existing, request, taskName);
    if (!this.serviceTaskProvider.addServiceTask(updated)) {
      throw new IllegalStateException("Die Task konnte nicht aktualisiert werden.");
    }

    return this.taskView(updated);
  }

  public void deleteTask(String taskName) {
    this.requireTask(taskName);
    this.serviceTaskProvider.removeServiceTaskByName(taskName);
  }

  public List<ServiceView> listServices() {
    return this.cloudServiceProvider.services().stream()
      .sorted(Comparator.comparing(ServiceInfoSnapshot::name))
      .map(this::serviceView)
      .toList();
  }

  public ServiceBatchCreateView createServices(Document request) {
    var task = this.requireTask(this.requiredText(request, "taskName"));
    var amount = this.clamp(request.getInt("amount", 1), 1, 20);
    var startImmediately = request.getBoolean("startImmediately", true);

    var results = new ArrayList<ServiceCreateView>();
    for (int index = 0; index < amount; index++) {
      var builder = ServiceConfiguration.builder(task);

      if (request.containsNonNull("environment")) {
        builder.environment(this.resolveEnvironment(request.getString("environment")));
      }
      if (request.contains("startPort")) {
        builder.startPort(request.getInt("startPort"));
      }
      if (request.contains("maxHeapMemory")) {
        builder.maxHeapMemory(request.getInt("maxHeapMemory"));
      }
      if (request.containsNonNull("node")) {
        builder.node(this.nullableText(request.getString("node")));
      }
      if (request.contains("staticService")) {
        builder.staticService(request.getBoolean("staticService"));
      }
      if (request.contains("autoDeleteOnStop")) {
        builder.autoDeleteOnStop(request.getBoolean("autoDeleteOnStop"));
      }
      if (request.contains("jvmOptions")) {
        builder.jvmOptions(this.stringValues(request, "jvmOptions"));
      }
      if (request.contains("processParameters")) {
        builder.processParameters(this.stringValues(request, "processParameters"));
      }

      var result = this.cloudServiceFactory.createCloudService(builder.build());
      if (startImmediately && result.state() == ServiceCreateResult.State.CREATED) {
        result.serviceInfo().provider().start();
      }
      results.add(this.createView(result, startImmediately));
    }

    return new ServiceBatchCreateView(task.name(), amount, startImmediately, results);
  }

  public ServiceView startService(String serviceName) {
    var provider = this.requireServiceProvider(serviceName);
    provider.start();
    return this.serviceView(this.requireService(serviceName));
  }

  public ServiceView stopService(String serviceName) {
    var provider = this.requireServiceProvider(serviceName);
    provider.stop();
    return this.serviceView(this.requireService(serviceName));
  }

  public ServiceView restartService(String serviceName) {
    var provider = this.requireServiceProvider(serviceName);
    provider.restart();
    return this.serviceView(this.requireService(serviceName));
  }

  public void deleteService(String serviceName) {
    var provider = this.requireServiceProvider(serviceName);
    provider.delete();
  }

  public ServiceConsoleView console(String serviceName, int requestedLimit) {
    var provider = this.requireServiceProvider(serviceName);
    var service = this.requireService(serviceName);
    var effectiveLimit = this.clamp(requestedLimit, 25, this.configuration.consoleLineLimit());

    return new ServiceConsoleView(
      service.name(),
      service.lifeCycle().name(),
      effectiveLimit,
      this.tail(provider.cachedLogMessages(), effectiveLimit),
      Instant.now().toString());
  }

  public void runServiceCommand(String serviceName, Document request) {
    var command = this.requiredText(request, "command");
    var provider = this.requireServiceProvider(serviceName);
    provider.runCommand(command);
  }

  public CloudNetConsoleView cloudNetConsole(int requestedLimit) {
    var effectiveLimit = this.clamp(requestedLimit, 25, this.configuration.consoleLineLimit());
    var restConsole = this.cloudNetRestConsoleClient.console(effectiveLimit);
    if (restConsole.handled()) {
      return new CloudNetConsoleView(
        effectiveLimit,
        restConsole.lines(),
        restConsole.source(),
        Instant.now().toString());
    }

    var screenName = this.nullableText(this.settingsStore.current().cloudNetScreenName());
    if (screenName == null) {
      return new CloudNetConsoleView(
        effectiveLimit,
        List.of("Keine CloudNet-Konsole konfiguriert. Bitte CloudNet REST oder einen GNU-Screen-Namen in den Einstellungen eintragen."),
        null,
        Instant.now().toString());
    }

    var screenCapture = this.cloudNetScreenOutput(screenName, effectiveLimit);
    if (screenCapture.success()) {
      return new CloudNetConsoleView(
        effectiveLimit,
        screenCapture.lines(),
        screenCapture.source(),
        Instant.now().toString());
    }
    return new CloudNetConsoleView(
      effectiveLimit,
      List.of("GNU Screen '" + screenName + "' konnte nicht gelesen werden: " + screenCapture.message()),
      "screen:" + screenName,
      Instant.now().toString());
  }

  public void close() {
    this.cloudNetRestConsoleClient.close();
  }

  public CloudNetCommandView runCloudNetCommand(Document request) {
    var command = this.requiredText(request, "command");
    var screenOutput = this.serviceScreenOutput(command);
    if (screenOutput != null) {
      return new CloudNetCommandView(command, screenOutput, Instant.now().toString());
    }

    try {
      this.commandProvider.execute(CommandSource.console(), command).join();
    } catch (CompletionException exception) {
      throw new IllegalArgumentException(this.cloudNetCommandErrorMessage(exception), exception);
    }
    return new CloudNetCommandView(
      command,
      this.cloudNetConsole(this.configuration.consoleLineLimit()).lines(),
      Instant.now().toString());
  }

  public List<NodeView> listNodes() {
    var nodeInfos = this.clusterNodeProvider.nodeInfoSnapshots().stream()
      .collect(Collectors.toMap(info -> info.node().uniqueId(), info -> info));

    return this.clusterNodeProvider.nodes().stream()
      .sorted(Comparator.comparing(NetworkClusterNode::uniqueId))
      .map(node -> this.nodeView(node, nodeInfos.get(node.uniqueId())))
      .toList();
  }

  public SettingsView settings() {
    return this.settingsView(this.settingsStore.current());
  }

  public SettingsView updateSettings(Document request) {
    return this.settingsView(this.settingsStore.update(request));
  }

  public TestMailView sendTestMail(Document request) {
    var recipient = this.requiredText(request, "recipient");
    var mailService = new SmtpMailService(this.configuration, this.settingsStore);
    if (!mailService.enabled()) {
      throw new IllegalArgumentException("SMTP ist deaktiviert.");
    }
    mailService.sendHtml(
      recipient,
      this.brandName() + " Testmail",
      "Deine SMTP-Einstellungen funktionieren. Diese Mail wurde direkt aus dem Einstellungstab gesendet.",
      this.craftplayMailHtml(
        "Network Control",
        "Testmail erfolgreich",
        "<p style=\"margin:0;color:#d7e2ea;line-height:1.55;\">Deine SMTP-Einstellungen funktionieren. Diese Mail wurde direkt aus dem Einstellungstab gesendet.</p>",
        null,
        null,
        "Craftplay Panel"));
    return new TestMailView("Testmail wurde versendet.", recipient);
  }

  private SettingsView settingsView(PanelSettings settings) {
    return new SettingsView(
      settings.brandName(),
      settings.brandLogoUrl(),
      settings.cloudNetScreenName(),
      settings.cloudNetRestBaseUrl(),
      settings.cloudNetRestUsername(),
      settings.cloudNetRestPassword() != null && !settings.cloudNetRestPassword().isBlank(),
      settings.cloudNetRestThreshold(),
      this.configuration.panelStorageBackend(),
      this.configuration.panelSqlJdbcUrl(),
      this.configuration.panelSqlUsername(),
      this.configuration.panelSqlPassword() != null && !this.configuration.panelSqlPassword().isBlank(),
      this.configuration.panelSqlTable(),
      settings.smtpEnabled(),
      settings.smtpHost(),
      settings.smtpPort(),
      settings.smtpUsername(),
      settings.smtpPassword() != null && !settings.smtpPassword().isBlank(),
      settings.smtpFrom(),
      settings.smtpStartTls(),
      settings.smtpSsl());
  }

  private String craftplayMailHtml(String eyebrow, String title, String bodyHtml, String buttonUrl, String buttonLabel, String footer) {
    return this.craftplayMailHtml(this.brandName(), eyebrow, title, bodyHtml, buttonUrl, buttonLabel, footer);
  }

  private String craftplayMailHtml(String headerBrandName, String eyebrow, String title, String bodyHtml, String buttonUrl, String buttonLabel, String footer) {
    var logo = this.brandLogoUrl();
    var logoHtml = logo == null || logo.isBlank()
      ? ""
      : "<img src=\"" + escapeHtml(logo) + "\" alt=\"\" style=\"width:46px;height:46px;object-fit:contain;border-radius:12px;margin-right:12px;vertical-align:middle;\">";
    var buttonHtml = buttonUrl == null || buttonUrl.isBlank()
      ? ""
      : "<p style=\"margin:24px 0 0;\"><a href=\"" + escapeHtml(buttonUrl) + "\" style=\"display:inline-block;background:linear-gradient(135deg,#f4bc46,#ff9f43);color:#1d1406;text-decoration:none;font-weight:800;padding:13px 18px;border-radius:14px;\">"
        + escapeHtml(buttonLabel == null || buttonLabel.isBlank() ? "Öffnen" : buttonLabel)
        + "</a></p>";
    return """
      <html>
        <body style="margin:0;background:#07131d;color:#f5f0e7;font-family:Segoe UI,Arial,sans-serif;">
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#07131d;padding:30px;background-image:radial-gradient(circle at top left,rgba(244,188,70,.18),transparent 34%%),linear-gradient(180deg,#07131d,#081019);">
            <tr>
              <td align="center">
                <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;background:#0f1e2b;border:1px solid rgba(244,188,70,.35);border-radius:24px;overflow:hidden;box-shadow:0 24px 80px rgba(0,0,0,.35);">
                  <tr>
                    <td style="height:5px;background:linear-gradient(90deg,#f4bc46,#ff9f43,#46c4a6);"></td>
                  </tr>
                  <tr>
                    <td style="padding:30px;">
                      <div style="margin:0 0 18px;">%s<span style="font-size:19px;font-weight:900;vertical-align:middle;">%s</span></div>
                      <p style="margin:0 0 10px;color:#f4bc46;letter-spacing:.18em;text-transform:uppercase;font-size:12px;font-weight:800;">%s</p>
                      <h1 style="margin:0 0 16px;font-size:30px;line-height:1.05;color:#f5f0e7;">%s</h1>
                      %s
                      %s
                      <p style="margin:26px 0 0;color:#9eb0bc;font-size:13px;">%s</p>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>
        </body>
      </html>
      """.formatted(
        logoHtml,
        escapeHtml(headerBrandName),
        escapeHtml(eyebrow),
        escapeHtml(title),
        bodyHtml,
        buttonHtml,
        escapeHtml(footer));
  }

  private TaskView taskView(ServiceTask task) {
    return new TaskView(
      task.name(),
      task.runtime(),
      task.processConfiguration().environment(),
      task.startPort(),
      task.minServiceCount(),
      task.processConfiguration().maxHeapMemorySize(),
      task.maintenance(),
      task.staticServices(),
      task.autoDeleteOnStop(),
      task.nameSplitter(),
      task.hostAddress(),
      task.javaCommand(),
      List.copyOf(task.groups()),
      List.copyOf(task.associatedNodes()),
      List.copyOf(task.deletedFilesAfterStop()),
      List.copyOf(task.jvmOptions()),
      List.copyOf(task.processParameters()));
  }

  private ServiceView serviceView(ServiceInfoSnapshot service) {
    var configuration = service.configuration();
    var serviceId = configuration.serviceId();

    return new ServiceView(
      service.name(),
      serviceId.taskName(),
      configuration.processConfig().environment(),
      serviceId.nodeUniqueId(),
      service.lifeCycle().name(),
      service.address().host(),
      service.address().port(),
      service.connected(),
      configuration.staticService(),
      configuration.autoDeleteOnStop(),
      configuration.processConfig().maxHeapMemorySize(),
      List.copyOf(configuration.groups()));
  }

  private NodeView nodeView(NetworkClusterNode node, NodeInfoSnapshot snapshot) {
    if (snapshot == null) {
      return new NodeView(
        node.uniqueId(),
        false,
        node.listeners().stream().map(Object::toString).toList(),
        0,
        0,
        0,
        0,
        false,
        null);
    }

    return new NodeView(
      node.uniqueId(),
      true,
      node.listeners().stream().map(Object::toString).toList(),
      snapshot.currentServicesCount(),
      snapshot.maxMemory(),
      snapshot.usedMemory(),
      snapshot.memoryUsagePercentage(),
      snapshot.draining(),
      snapshot.version().toString());
  }

  private ServiceCreateView createView(ServiceCreateResult result, boolean startImmediately) {
    return switch (result.state()) {
      case CREATED -> new ServiceCreateView(
        result.state().name(),
        result.serviceInfo().name(),
        result.serviceInfo().serviceId().uniqueId().toString(),
        startImmediately);
      case DEFERRED -> new ServiceCreateView(
        result.state().name(),
        null,
        result.creationId().toString(),
        false);
      case FAILED -> new ServiceCreateView(
        result.state().name(),
        null,
        null,
        false);
    };
  }

  private ServiceTask buildTask(ServiceTask baseTask, Document request, String fallbackName) {
    var builder = baseTask == null ? ServiceTask.builder() : ServiceTask.builder(baseTask);

    builder.name(this.requiredText(request, "name", fallbackName));
    builder.runtime(this.textOrDefault(request, "runtime", baseTask == null ? "jvm" : baseTask.runtime()));
    builder.nameSplitter(this.textOrDefault(request, "nameSplitter", baseTask == null ? "-" : baseTask.nameSplitter()));
    builder.maintenance(this.booleanOrDefault(request, "maintenance", baseTask != null && baseTask.maintenance()));
    builder.staticServices(this.booleanOrDefault(request, "staticServices", baseTask != null && baseTask.staticServices()));
    builder.autoDeleteOnStop(this.booleanOrDefault(request, "autoDeleteOnStop", baseTask == null || baseTask.autoDeleteOnStop()));
    builder.disableIpRewrite(this.booleanOrDefault(request, "disableIpRewrite", baseTask != null && baseTask.disableIpRewrite()));

    var defaultEnvironment = baseTask == null
      ? ServiceEnvironmentType.MINECRAFT_SERVER.name()
      : baseTask.processConfiguration().environment();
    var environment = this.resolveEnvironment(this.textOrDefault(request, "environment", defaultEnvironment));
    builder.serviceEnvironmentType(environment);

    var defaultStartPort = baseTask == null ? environment.defaultStartPort() : baseTask.startPort();
    builder.startPort(request.getInt("startPort", defaultStartPort));

    var defaultMemory = baseTask == null ? 1024 : baseTask.processConfiguration().maxHeapMemorySize();
    builder.maxHeapMemory(request.getInt("maxHeapMemory", defaultMemory));
    builder.minServiceCount(request.getInt("minServiceCount", baseTask == null ? 0 : baseTask.minServiceCount()));

    if (request.contains("groups") || baseTask == null) {
      builder.groups(this.stringValues(request, "groups", baseTask == null ? List.of() : baseTask.groups()));
    }
    if (request.contains("associatedNodes") || baseTask == null) {
      builder.associatedNodes(this.stringValues(request, "associatedNodes", baseTask == null ? List.of() : baseTask.associatedNodes()));
    }
    if (request.contains("deletedFilesAfterStop") || baseTask == null) {
      builder.deletedFilesAfterStop(this.stringValues(
        request,
        "deletedFilesAfterStop",
        baseTask == null ? List.of() : baseTask.deletedFilesAfterStop()));
    }
    if (request.contains("jvmOptions") || baseTask == null) {
      builder.jvmOptions(this.stringValues(request, "jvmOptions", baseTask == null ? List.of() : baseTask.jvmOptions()));
    }
    if (request.contains("processParameters") || baseTask == null) {
      builder.processParameters(this.stringValues(
        request,
        "processParameters",
        baseTask == null ? List.of() : baseTask.processParameters()));
    }

    if (request.contains("hostAddress") || baseTask == null) {
      builder.hostAddress(this.nullableText(request.getString("hostAddress")));
    }
    if (request.contains("javaCommand") || baseTask == null) {
      builder.javaCommand(this.nullableText(request.getString("javaCommand")));
    }

    return builder.build();
  }

  private ServiceTask requireTask(String taskName) {
    var task = this.serviceTaskProvider.serviceTask(taskName);
    if (task == null) {
      throw new IllegalArgumentException("Die angeforderte Task wurde nicht gefunden.");
    }
    return task;
  }

  private ServiceInfoSnapshot requireService(String serviceName) {
    var service = this.cloudServiceProvider.serviceByName(serviceName);
    if (service == null) {
      throw new IllegalArgumentException("Der angeforderte Service wurde nicht gefunden.");
    }
    return service;
  }

  private SpecificCloudServiceProvider requireServiceProvider(String serviceName) {
    this.requireService(serviceName);
    return this.cloudServiceProvider.serviceProviderByName(serviceName);
  }

  private String requiredText(Document request, String key) {
    var value = request.getString(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Feld '" + key + "' ist erforderlich.");
    }
    return value.trim();
  }

  private String requiredText(Document request, String key, String fallback) {
    if (!request.containsNonNull(key)) {
      return fallback;
    }
    return this.requiredText(request, key);
  }

  private String textOrDefault(Document request, String key, String fallback) {
    if (!request.containsNonNull(key)) {
      return fallback;
    }
    var value = request.getString(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private boolean booleanOrDefault(Document request, String key, boolean fallback) {
    return request.contains(key) ? request.getBoolean(key) : fallback;
  }

  private List<String> stringValues(Document request, String key) {
    return this.stringValues(request, key, List.of());
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

  private String nullableText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private List<String> tail(Queue<String> lines, int limit) {
    if (lines.isEmpty()) {
      return List.of();
    }

    var skip = Math.max(0, lines.size() - limit);
    return lines.stream()
      .skip(skip)
      .toList();
  }

  private ServiceEnvironmentType resolveEnvironment(String name) {
    return switch (name.toUpperCase()) {
      case "MINECRAFT_SERVER" -> ServiceEnvironmentType.MINECRAFT_SERVER;
      case "MODDED_MINECRAFT_SERVER" -> ServiceEnvironmentType.MODDED_MINECRAFT_SERVER;
      case "VELOCITY" -> ServiceEnvironmentType.VELOCITY;
      case "BUNGEECORD" -> ServiceEnvironmentType.BUNGEECORD;
      case "MINESTOM" -> ServiceEnvironmentType.MINESTOM;
      case "LIMBO_LOOHP" -> ServiceEnvironmentType.LIMBO_LOOHP;
      case "NUKKIT" -> ServiceEnvironmentType.NUKKIT;
      case "WATERDOG_PE" -> ServiceEnvironmentType.WATERDOG_PE;
      default -> ServiceEnvironmentType.builder().name(name.toUpperCase()).build();
    };
  }

  private int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private String brandName() {
    var settings = this.settingsStore.current();
    return settings.brandName() == null || settings.brandName().isBlank()
      ? this.configuration.brandName()
      : settings.brandName();
  }

  private String brandLogoUrl() {
    var settings = this.settingsStore.current();
    return settings.brandLogoUrl() == null ? "" : settings.brandLogoUrl();
  }

  private static String escapeHtml(String value) {
    return String.valueOf(value)
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;");
  }

  private String cloudNetCommandErrorMessage(Throwable exception) {
    var current = exception;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }

    for (var cursor = current; cursor != null; cursor = cursor.getCause()) {
      var message = cursor.getMessage();
      if (message != null && !message.isBlank()) {
        if ("That service doesn't exist".equalsIgnoreCase(message)) {
          return "Dieser Service existiert nicht. Bitte nutze den exakten Servicenamen aus der aktiven Service-Liste.";
        }
        return message;
      }
    }
    return "CloudNet-Befehl konnte nicht ausgeführt werden.";
  }

  private ScreenCapture cloudNetScreenOutput(String screenName, int limit) {
    var candidates = new LinkedHashSet<String>();
    candidates.add(screenName);

    var sessions = this.availableScreenSessions();
    for (var session : sessions) {
      if (this.screenSessionMatches(session, screenName)) {
        candidates.add(session);
      }
    }

    var errors = new ArrayList<String>();
    for (var candidate : candidates) {
      var capture = this.cloudNetScreenHardcopy(candidate, limit);
      if (capture.success()) {
        return capture;
      }
      errors.add(candidate + ": " + capture.message());
    }

    if (!sessions.isEmpty()) {
      errors.add("Gefundene Screen-Sessions: " + String.join(", ", sessions));
    }
    return ScreenCapture.failed(errors.isEmpty()
      ? "Keine passende Screen-Session gefunden."
      : String.join(" | ", errors));
  }

  private ScreenCapture cloudNetScreenHardcopy(String screenSession, int limit) {
    Path tempFile = null;
    try {
      tempFile = Files.createTempFile("tccb-cloudnet-screen-", ".log");
      var result = this.runProcess(List.of(
        "screen",
        "-S",
        screenSession,
        "-X",
        "hardcopy",
        "-h",
        tempFile.toString()), 3);
      if (result.exitCode() != 0) {
        return ScreenCapture.failed(result.output().isBlank() ? "screen hat mit Exit-Code " + result.exitCode() + " beendet." : result.output());
      }
      if (!Files.isRegularFile(tempFile) || Files.size(tempFile) == 0) {
        return ScreenCapture.failed(result.output().isBlank() ? "Screen-Hardcopy ist leer." : result.output());
      }
      return ScreenCapture.success(this.readFileTail(tempFile, limit), "screen:" + screenSession);
    } catch (IOException exception) {
      return ScreenCapture.failed(exception.getMessage());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return ScreenCapture.failed("Screen-Auslesen wurde unterbrochen.");
    } finally {
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
      }
    }
  }

  private List<String> availableScreenSessions() {
    try {
      var result = this.runProcess(List.of("screen", "-ls"), 3);
      return result.output().lines()
        .map(String::trim)
        .map(line -> line.split("\\s+", 2)[0])
        .filter(this::isScreenSessionToken)
        .distinct()
        .toList();
    } catch (IOException exception) {
      return List.of();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return List.of();
    }
  }

  private ProcessResult runProcess(List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
    var process = new ProcessBuilder(command)
      .redirectErrorStream(true)
      .start();
    var finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      return new ProcessResult(124, "Timeout beim Ausführen von: " + String.join(" ", command));
    }
    return new ProcessResult(
      process.exitValue(),
      new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
  }

  private boolean screenSessionMatches(String session, String requested) {
    return session.equalsIgnoreCase(requested)
      || this.screenShortName(session).equalsIgnoreCase(requested)
      || session.endsWith("." + requested);
  }

  private String screenShortName(String session) {
    var separator = session.indexOf('.');
    return separator < 0 || separator + 1 >= session.length()
      ? session
      : session.substring(separator + 1);
  }

  private boolean isScreenSessionToken(String value) {
    var separator = value.indexOf('.');
    if (separator <= 0 || separator + 1 >= value.length()) {
      return false;
    }
    for (int index = 0; index < separator; index++) {
      if (!Character.isDigit(value.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  private String exceptionMessage(Throwable exception) {
    var message = exception.getMessage();
    if (message != null && !message.isBlank()) {
      return message;
    }
    return exception.getClass().getSimpleName();
  }

  private List<String> readFileTail(Path path, int limit) {
    try {
      var decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE);
      var content = decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
      var allLines = content.lines().toList();
      if (allLines.isEmpty()) {
        return List.of("CloudNet-Logdatei ist aktuell leer: " + path.toAbsolutePath().normalize());
      }
      var skip = Math.max(0, allLines.size() - limit);
      return allLines.stream()
        .skip(skip)
        .toList();
    } catch (IOException exception) {
      return List.of("Console-Datei konnte nicht gelesen werden: " + this.exceptionMessage(exception));
    } catch (RuntimeException exception) {
      return List.of("Console-Datei konnte nicht gelesen werden: " + this.exceptionMessage(exception));
    }
  }

  private List<String> serviceScreenOutput(String command) {
    var parts = List.of(command.trim().split("\\s+"));
    if (parts.size() < 3 || !this.isServiceCommand(parts.get(0))) {
      return null;
    }

    var selector = "";
    if ("screen".equalsIgnoreCase(parts.get(2))) {
      selector = parts.get(1);
    } else if ("screen".equalsIgnoreCase(parts.get(1))) {
      selector = parts.get(2);
    }
    if (selector.isBlank()) {
      return null;
    }

    var limit = this.clamp(this.configuration.consoleLineLimit(), 25, 500);
    if ("*".equals(selector)) {
      var output = new ArrayList<String>();
      var services = this.cloudServiceProvider.services().stream()
        .sorted(Comparator.comparing(ServiceInfoSnapshot::name))
        .toList();
      if (services.isEmpty()) {
        return List.of("Keine aktiven Services gefunden.");
      }
      for (var service : services) {
        output.add("===== " + service.name() + " =====");
        var lines = this.tail(this.cloudServiceProvider.serviceProviderByName(service.name()).cachedLogMessages(), limit);
        output.addAll(lines.isEmpty() ? List.of("Keine Log-Ausgabe im Cache.") : lines);
      }
      return output;
    }

    var provider = this.requireServiceProvider(selector);
    var lines = this.tail(provider.cachedLogMessages(), limit);
    if (lines.isEmpty()) {
      return List.of("Keine Log-Ausgabe im Cache für " + selector + ".");
    }
    return lines;
  }

  private boolean isServiceCommand(String command) {
    return switch (command.toLowerCase()) {
      case "service", "services", "ser", "srv" -> true;
      default -> false;
    };
  }

  private record ProcessResult(
    int exitCode,
    String output
  ) {
  }

  private record ScreenCapture(
    boolean success,
    List<String> lines,
    String message,
    String source
  ) {

    private static ScreenCapture success(List<String> lines, String source) {
      return new ScreenCapture(true, lines, "", source);
    }

    private static ScreenCapture failed(String message) {
      return new ScreenCapture(false, List.of(), message == null || message.isBlank() ? "Unbekannter Fehler." : message, null);
    }
  }

  public record MetaView(
    String brandName,
    String brandLogoUrl,
    List<String> environments,
    List<String> runtimes,
    List<PanelPermission.PermissionView> availablePermissions,
    String generatedAt
  ) {
  }

  public record OverviewView(
    String brandName,
    int taskCount,
    int serviceCount,
    long runningServiceCount,
    int registeredNodeCount,
    int onlineNodeCount,
    Map<String, Long> servicesByEnvironment,
    Map<String, Long> tasksByEnvironment,
    String generatedAt
  ) {
  }

  public record TaskView(
    String name,
    String runtime,
    String environment,
    int startPort,
    int minServiceCount,
    int maxHeapMemory,
    boolean maintenance,
    boolean staticServices,
    boolean autoDeleteOnStop,
    String nameSplitter,
    String hostAddress,
    String javaCommand,
    List<String> groups,
    List<String> associatedNodes,
    List<String> deletedFilesAfterStop,
    List<String> jvmOptions,
    List<String> processParameters
  ) {
  }

  public record ServiceView(
    String name,
    String taskName,
    String environment,
    String node,
    String state,
    String host,
    int port,
    boolean connected,
    boolean staticService,
    boolean autoDeleteOnStop,
    int maxHeapMemory,
    List<String> groups
  ) {
  }

  public record ServiceCreateView(
    String state,
    String serviceName,
    String referenceId,
    boolean started
  ) {
  }

  public record ServiceBatchCreateView(
    String taskName,
    int amount,
    boolean startImmediately,
    List<ServiceCreateView> results
  ) {
  }

  public record ServiceConsoleView(
    String serviceName,
    String state,
    int limit,
    List<String> lines,
    String generatedAt
  ) {
  }

  public record CloudNetConsoleView(
    int limit,
    List<String> lines,
    String logPath,
    String generatedAt
  ) {
  }

  public record CloudNetCommandView(
    String command,
    List<String> output,
    String executedAt
  ) {
  }

  public record NodeView(
    String uniqueId,
    boolean connected,
    List<String> listeners,
    int currentServicesCount,
    int maxMemory,
    int usedMemory,
    int memoryUsagePercentage,
    boolean draining,
    String version
  ) {
  }

  public record SettingsView(
    String brandName,
    String brandLogoUrl,
    String cloudNetScreenName,
    String cloudNetRestBaseUrl,
    String cloudNetRestUsername,
    boolean cloudNetRestPasswordConfigured,
    String cloudNetRestThreshold,
    String panelStorageBackend,
    String panelSqlJdbcUrl,
    String panelSqlUsername,
    boolean panelSqlPasswordConfigured,
    String panelSqlTable,
    boolean smtpEnabled,
    String smtpHost,
    int smtpPort,
    String smtpUsername,
    boolean smtpPasswordConfigured,
    String smtpFrom,
    boolean smtpStartTls,
    boolean smtpSsl
  ) {
  }

  public record TestMailView(
    String message,
    String recipient
  ) {
  }

}
