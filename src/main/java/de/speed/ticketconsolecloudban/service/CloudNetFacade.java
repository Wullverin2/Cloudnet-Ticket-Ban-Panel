package de.speed.ticketconsolecloudban.service;

import de.speed.ticketconsolecloudban.auth.PanelPermission;
import de.speed.ticketconsolecloudban.appeal.BanAppealEntry;
import de.speed.ticketconsolecloudban.ban.BanActionRequest;
import de.speed.ticketconsolecloudban.ban.BanAuditEntry;
import de.speed.ticketconsolecloudban.ban.CloudBanEntry;
import de.speed.ticketconsolecloudban.ban.LiteBansDatabaseSyncService;
import de.speed.ticketconsolecloudban.ban.LiteBanEntry;
import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.permission.PermissionActionRequest;
import de.speed.ticketconsolecloudban.permission.PermissionAuditEntry;
import de.speed.ticketconsolecloudban.permission.PermissionSubject;
import de.speed.ticketconsolecloudban.store.BanStore;
import de.speed.ticketconsolecloudban.store.BanAppealStore;
import de.speed.ticketconsolecloudban.store.PermissionBridgeStore;
import de.speed.ticketconsolecloudban.store.TicketStore;
import de.speed.ticketconsolecloudban.ticket.TicketComment;
import de.speed.ticketconsolecloudban.ticket.TicketAuditEntry;
import de.speed.ticketconsolecloudban.ticket.TicketEntry;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
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
  private final PanelConfiguration configuration;
  private final TicketStore ticketStore;
  private final BanStore banStore;
  private final BanAppealStore banAppealStore;
  private final LiteBansDatabaseSyncService liteBansDatabaseSyncService;
  private final PermissionBridgeStore permissionBridgeStore;

  public CloudNetFacade(
    CloudServiceProvider cloudServiceProvider,
    ServiceTaskProvider serviceTaskProvider,
    CloudServiceFactory cloudServiceFactory,
    ClusterNodeProvider clusterNodeProvider,
    PanelConfiguration configuration,
    TicketStore ticketStore,
    BanStore banStore,
    BanAppealStore banAppealStore,
    LiteBansDatabaseSyncService liteBansDatabaseSyncService,
    PermissionBridgeStore permissionBridgeStore
  ) {
    this.cloudServiceProvider = cloudServiceProvider;
    this.serviceTaskProvider = serviceTaskProvider;
    this.cloudServiceFactory = cloudServiceFactory;
    this.clusterNodeProvider = clusterNodeProvider;
    this.configuration = configuration;
    this.ticketStore = ticketStore;
    this.banStore = banStore;
    this.banAppealStore = banAppealStore;
    this.liteBansDatabaseSyncService = liteBansDatabaseSyncService;
    this.permissionBridgeStore = permissionBridgeStore;
  }

  public MetaView meta() {
    return new MetaView(
      this.configuration.brandName(),
      ENVIRONMENT_CHOICES,
      List.of("jvm"),
      List.of("OPEN", "IN_PROGRESS", "CLOSED"),
      List.of("LOW", "NORMAL", "HIGH", "URGENT"),
      List.of("SUPPORT", "BUG", "REPORT", "BAN_APPEAL", "OTHER"),
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
      this.configuration.brandName(),
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

  public List<NodeView> listNodes() {
    var nodeInfos = this.clusterNodeProvider.nodeInfoSnapshots().stream()
      .collect(Collectors.toMap(info -> info.node().uniqueId(), info -> info));

    return this.clusterNodeProvider.nodes().stream()
      .sorted(Comparator.comparing(NetworkClusterNode::uniqueId))
      .map(node -> this.nodeView(node, nodeInfos.get(node.uniqueId())))
      .toList();
  }

  public List<TicketView> listTickets() {
    return this.ticketStore.list().stream()
      .map(this::ticketView)
      .toList();
  }

  public List<TicketView> listTickets(String creatorUniqueId, String creatorName, String status) {
    var normalizedUniqueId = this.nullableText(creatorUniqueId);
    var normalizedName = this.nullableText(creatorName);
    var normalizedStatus = this.nullableText(status);

    return this.ticketStore.list().stream()
      .filter(ticket -> normalizedUniqueId == null
        || (ticket.creatorUniqueId() != null && ticket.creatorUniqueId().equalsIgnoreCase(normalizedUniqueId)))
      .filter(ticket -> normalizedName == null
        || (ticket.creatorName() != null && ticket.creatorName().equalsIgnoreCase(normalizedName)))
      .filter(ticket -> normalizedStatus == null
        || (ticket.status() != null && ticket.status().equalsIgnoreCase(normalizedStatus)))
      .map(this::ticketView)
      .toList();
  }

  public List<TicketAuditEntry> ticketAuditLog() {
    return this.ticketStore.auditLog();
  }

  public TicketView createTicket(Document request) {
    var creatorName = this.requiredText(request, "creatorName");
    var subject = this.requiredText(request, "subject");
    var content = this.requiredText(request, "content");
    var category = this.textOrDefault(request, "category", "SUPPORT").toUpperCase();
    var priority = this.textOrDefault(request, "priority", "NORMAL").toUpperCase();
    var serviceName = this.nullableText(request.getString("sourceServer"));
    if (serviceName == null) {
      serviceName = this.nullableText(request.getString("serviceName"));
    }
    var creatorUniqueId = this.nullableText(request.getString("creatorUniqueId"));

    return this.ticketView(this.ticketStore.create(
      creatorName,
      creatorUniqueId,
      category,
      priority,
      subject,
      content,
      serviceName));
  }

  public TicketView updateTicketStatus(String id, Document request) {
    var status = this.requiredText(request, "status").toUpperCase();
    var actor = this.requiredText(request, "actor");
    return this.ticketView(this.ticketStore.updateStatus(id, status, actor));
  }

  public TicketView assignTicket(String id, Document request) {
    var assignedTo = this.requiredText(request, "assignedTo");
    var actor = this.requiredText(request, "actor");
    return this.ticketView(this.ticketStore.assign(id, assignedTo, actor));
  }

  public TicketView addTicketComment(String id, Document request) {
    var author = this.requiredText(request, "author");
    var message = this.requiredText(request, "message");
    var internal = request.getBoolean("internal", false);
    return this.ticketView(this.ticketStore.addComment(id, author, message, internal));
  }

  public List<BanView> listBans() {
    return this.banStore.list().stream()
      .map(this::banView)
      .toList();
  }

  public BanView createBan(Document request) {
    var targetName = this.requiredText(request, "targetName");
    var reason = this.requiredText(request, "reason");
    var issuedBy = this.requiredText(request, "issuedBy");
    var targetUniqueId = this.nullableText(request.getString("targetUniqueId"));
    var targetAddress = this.nullableText(request.getString("targetAddress"));
    var durationMinutes = request.getLong("durationMinutes", 0);
    var expiresAt = durationMinutes > 0
      ? Instant.now().plus(durationMinutes, ChronoUnit.MINUTES).toString()
      : null;

    return this.banView(this.banStore.create(
      targetName,
      targetUniqueId,
      targetAddress,
      reason,
      issuedBy,
      expiresAt));
  }

  public BanView deactivateBan(String id, Document request) {
    var removedBy = this.requiredText(request, "removedBy");
    return this.banView(this.banStore.deactivate(id, removedBy));
  }

  public List<LiteBanEntry> listLiteBans() {
    this.liteBansDatabaseSyncService.syncNow("litebans-mysql");
    return this.banStore.listLiteBans();
  }

  public List<LiteBanEntry> syncLiteBans(Document request) {
    var entries = request.readObject("bans", LiteBanEntry[].class, new LiteBanEntry[0]);
    var actor = this.textOrDefault(request, "actor", "velocity-sync");
    return this.banStore.syncLiteBans(List.of(entries), actor);
  }

  public BanActionRequest requestLiteBanUnban(String banId, Document request) {
    return this.banStore.requestLiteBanUnban(
      banId,
      this.textOrDefault(request, "actor", "Panel"),
      this.textOrDefault(request, "reason", "Unban via Panel"));
  }

  public BanActionRequest requestLiteBanExtend(String banId, Document request) {
    return this.banStore.requestLiteBanExtend(
      banId,
      this.textOrDefault(request, "actor", "Panel"),
      this.requiredText(request, "duration"),
      this.textOrDefault(request, "reason", "Ban via Panel verlaengert"));
  }

  public List<BanActionRequest> pendingBanActions() {
    return this.banStore.pendingActionRequests();
  }

  public BanActionRequest completeBanAction(String actionId, Document request) {
    return this.banStore.completeActionRequest(
      actionId,
      request.getBoolean("success", true),
      this.textOrDefault(request, "message", "Aktion verarbeitet"));
  }

  public List<BanAuditEntry> banAuditLog() {
    return this.banStore.auditLog();
  }

  public List<BanAppealEntry> listBanAppeals() {
    return this.banAppealStore.list();
  }

  public BanAppealEntry updateBanAppealStatus(String appealId, Document request) {
    return this.banAppealStore.updateStatus(
      appealId,
      this.requiredText(request, "status"),
      this.nullableText(request.getString("teamNote")));
  }

  public List<PermissionSubject> listPermissionSubjects() {
    return this.permissionBridgeStore.listSubjects();
  }

  public List<PermissionSubject> syncPermissionSubjects(Document request) {
    var subjects = request.readObject("subjects", PermissionSubject[].class, new PermissionSubject[0]);
    return this.permissionBridgeStore.syncSubjects(
      List.of(subjects),
      this.textOrDefault(request, "actor", "velocity-sync"),
      this.textOrDefault(request, "serverId", "proxy"));
  }

  public PermissionActionRequest requestPermissionAction(Document request) {
    return this.permissionBridgeStore.requestAction(
      this.textOrDefault(request, "serverId", "proxy"),
      this.requiredText(request, "action"),
      this.requiredText(request, "subjectType"),
      this.requiredText(request, "subjectId"),
      this.nullableText(request.getString("permission")),
      this.nullableText(request.getString("parent")),
      request.contains("value") ? request.getBoolean("value") : null,
      this.textOrDefault(request, "actor", "Panel"));
  }

  public List<PermissionActionRequest> pendingPermissionActions(String serverId) {
    return this.permissionBridgeStore.pendingActionRequests(serverId);
  }

  public PermissionActionRequest completePermissionAction(String actionId, Document request) {
    return this.permissionBridgeStore.completeActionRequest(
      actionId,
      request.getBoolean("success", true),
      this.textOrDefault(request, "message", "Aktion verarbeitet"));
  }

  public List<PermissionAuditEntry> permissionAuditLog() {
    return this.permissionBridgeStore.auditLog();
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

  private TicketView ticketView(TicketEntry ticket) {
    return new TicketView(
      ticket.id(),
      ticket.creatorName(),
      ticket.category(),
      ticket.priority(),
      ticket.status(),
      ticket.subject(),
      ticket.content(),
      ticket.assignedTo(),
      ticket.serviceName(),
      ticket.serviceName(),
      ticket.createdAt(),
      ticket.updatedAt(),
      ticket.comments() == null
        ? List.of()
        : ticket.comments().stream().map(this::ticketCommentView).toList());
  }

  private TicketCommentView ticketCommentView(TicketComment comment) {
    return new TicketCommentView(
      comment.author(),
      comment.message(),
      comment.internal(),
      comment.createdAt());
  }

  private BanView banView(CloudBanEntry entry) {
    var expired = entry.expiresAt() != null && Instant.parse(entry.expiresAt()).isBefore(Instant.now());
    var effectiveActive = entry.active() && !expired;

    return new BanView(
      entry.id(),
      entry.targetName(),
      entry.targetUniqueId(),
      entry.targetAddress(),
      entry.reason(),
      entry.issuedBy(),
      entry.createdAt(),
      entry.expiresAt(),
      effectiveActive,
      expired,
      entry.removedBy(),
      entry.removedAt());
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

  public record MetaView(
    String brandName,
    List<String> environments,
    List<String> runtimes,
    List<String> ticketStatuses,
    List<String> ticketPriorities,
    List<String> ticketCategories,
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

  public record TicketView(
    String id,
    String creatorName,
    String category,
    String priority,
    String status,
    String subject,
    String content,
    String assignedTo,
    String serviceName,
    String sourceServer,
    String createdAt,
    String updatedAt,
    List<TicketCommentView> comments
  ) {
  }

  public record TicketCommentView(
    String author,
    String message,
    boolean internal,
    String createdAt
  ) {
  }

  public record BanView(
    String id,
    String targetName,
    String targetUniqueId,
    String targetAddress,
    String reason,
    String issuedBy,
    String createdAt,
    String expiresAt,
    boolean active,
    boolean expired,
    String removedBy,
    String removedAt
  ) {
  }
}
