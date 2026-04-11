const PERMISSIONS = {
  CLOUDNET_VIEW: "cloudnet.view",
  CLOUDNET_MANAGE: "cloudnet.manage",
  CLOUDNET_CONSOLE: "cloudnet.console",
  CLOUDNET_COMMAND: "cloudnet.command",
  TICKETS_VIEW: "tickets.view",
  TICKETS_CREATE: "tickets.create",
  TICKETS_MANAGE: "tickets.manage",
  BANS_VIEW: "bans.view",
  BANS_MANAGE: "bans.manage",
  USERS_MANAGE: "users.manage",
  PROXY_PERMISSIONS_MANAGE: "permissions.proxy.manage",
  SERVER_PERMISSIONS_MANAGE: "permissions.server.manage",
  SETTINGS_MANAGE: "settings.manage",
};

const state = {
  token: localStorage.getItem("tccb-session") || "",
  meta: null,
  session: null,
  currentUser: null,
  overview: null,
  tasks: [],
  services: [],
  nodes: [],
  tickets: [],
  ticketAudit: [],
  bans: [],
  liteBans: [],
  banAppeals: [],
  banAudit: [],
  securityUsers: [],
  securityGroups: [],
  permissionSubjects: [],
  permissionAudit: [],
  settings: null,
  selectedPermissionServer: localStorage.getItem("tccb-lp-server") || "proxy",
  selectedPermissionSubject: localStorage.getItem("tccb-lp-subject") || "",
  selectedService: null,
  activePage: localStorage.getItem("tccb-page") || "home",
  consoleTimer: null,
  consoleRequestInFlight: false,
  lastConsoleText: "",
  consoleAutoRefresh: true,
};

const elements = {};

document.addEventListener("DOMContentLoaded", () => {
  bindElements();
  bindEvents();
  boot();
});

function bindElements() {
  elements.brandName = document.getElementById("brand-name");
  elements.brandLogo = document.getElementById("brand-logo");
  elements.authForm = document.getElementById("auth-form");
  elements.loginForm = document.getElementById("login-form");
  elements.authStatus = document.getElementById("auth-status");
  elements.loginUsername = document.getElementById("login-username");
  elements.loginPassword = document.getElementById("login-password");
  elements.resetRequestForm = document.getElementById("reset-request-form");
  elements.resetCompleteForm = document.getElementById("reset-complete-form");
  elements.resetStatus = document.getElementById("reset-status");
  elements.userCard = document.getElementById("user-card");
  elements.currentUser = document.getElementById("current-user");
  elements.currentPermissions = document.getElementById("current-permissions");
  elements.logoutButton = document.getElementById("logout-button");
  elements.profileForm = document.getElementById("profile-form");
  elements.passwordForm = document.getElementById("password-form");
  elements.profileStatus = document.getElementById("profile-status");
  elements.pageNav = document.getElementById("page-nav");
  elements.summaryGrid = document.getElementById("summary-grid");

  elements.metricTasks = document.getElementById("metric-tasks");
  elements.metricServices = document.getElementById("metric-services");
  elements.metricRunning = document.getElementById("metric-running");
  elements.metricNodes = document.getElementById("metric-nodes");
  elements.homePanel = document.getElementById("home-panel");
  elements.homeRefresh = document.getElementById("home-refresh");
  elements.homeOpenTickets = document.getElementById("home-open-tickets");
  elements.homeClosedTickets = document.getElementById("home-closed-tickets");
  elements.homeOpenAppeals = document.getElementById("home-open-appeals");
  elements.homeArchivedAppeals = document.getElementById("home-archived-appeals");
  elements.homeActiveLiteBans = document.getElementById("home-active-litebans");
  elements.homeRunningServices = document.getElementById("home-running-services");

  elements.environmentSelect = document.getElementById("environment-select");
  elements.runtimeSelect = document.getElementById("runtime-select");
  elements.taskForm = document.getElementById("task-form");
  elements.taskStatus = document.getElementById("task-status");
  elements.taskTable = document.getElementById("task-table");
  elements.taskReset = document.getElementById("task-reset");
  elements.taskSubmit = document.getElementById("task-submit");

  elements.serviceCreateForm = document.getElementById("service-create-form");
  elements.serviceCreateStatus = document.getElementById("service-create-status");
  elements.serviceTaskSelect = document.getElementById("service-task-select");
  elements.serviceTable = document.getElementById("service-table");
  elements.serviceRefresh = document.getElementById("service-refresh");

  elements.consoleServiceSelect = document.getElementById("console-service-select");
  elements.consoleAutoRefresh = document.getElementById("console-auto-refresh");
  elements.consoleRefresh = document.getElementById("console-refresh");
  elements.consoleOutput = document.getElementById("console-output");
  elements.consoleCommandForm = document.getElementById("console-command-form");
  elements.consoleCommandInput = document.getElementById("console-command-input");

  elements.nodeGrid = document.getElementById("node-grid");

  elements.ticketForm = document.getElementById("ticket-form");
  elements.ticketStatus = document.getElementById("ticket-status");
  elements.ticketTable = document.getElementById("ticket-table");
  elements.ticketArchiveTable = document.getElementById("ticket-archive-table");
  elements.ticketAuditTable = document.getElementById("ticket-audit-table");
  elements.ticketCategorySelect = document.getElementById("ticket-category-select");
  elements.ticketPrioritySelect = document.getElementById("ticket-priority-select");
  elements.serviceNameList = document.getElementById("service-name-list");

  elements.banForm = document.getElementById("ban-form");
  elements.banStatus = document.getElementById("ban-status");
  elements.banTable = document.getElementById("ban-table");
  elements.liteBanTable = document.getElementById("liteban-table");
  elements.banAppealTable = document.getElementById("ban-appeal-table");
  elements.banAppealArchiveTable = document.getElementById("ban-appeal-archive-table");
  elements.banAuditTable = document.getElementById("ban-audit-table");

  elements.groupForm = document.getElementById("group-form");
  elements.groupReset = document.getElementById("group-reset");
  elements.groupSubmit = document.getElementById("group-submit");
  elements.groupStatus = document.getElementById("group-status");
  elements.permissionGrid = document.getElementById("permission-grid");
  elements.groupTable = document.getElementById("group-table");

  elements.userForm = document.getElementById("user-form");
  elements.userReset = document.getElementById("user-reset");
  elements.userSubmit = document.getElementById("user-submit");
  elements.userStatus = document.getElementById("user-status");
  elements.userGroupGrid = document.getElementById("user-group-grid");
  elements.userTable = document.getElementById("user-table");

  elements.permissionActionForm = document.getElementById("permission-action-form");
  elements.permissionRefresh = document.getElementById("permission-refresh");
  elements.permissionServerSelect = document.getElementById("permission-server-select");
  elements.permissionSubjectSearch = document.getElementById("permission-subject-search");
  elements.permissionSubjectList = document.getElementById("permission-subject-list");
  elements.permissionSelectedSummary = document.getElementById("permission-selected-summary");
  elements.permissionNodeList = document.getElementById("permission-node-list");
  elements.permissionAuditTable = document.getElementById("permission-audit-table");
  elements.permissionStatus = document.getElementById("permission-status");
  elements.settingsForm = document.getElementById("settings-form");
  elements.settingsStatus = document.getElementById("settings-status");
  elements.testMailForm = document.getElementById("test-mail-form");
  elements.testMailStatus = document.getElementById("test-mail-status");
}

function bindEvents() {
  elements.loginForm.addEventListener("submit", handleLoginSubmit);
  elements.resetRequestForm.addEventListener("submit", handleResetRequestSubmit);
  elements.resetCompleteForm.addEventListener("submit", handleResetCompleteSubmit);
  elements.logoutButton.addEventListener("click", handleLogout);
  elements.profileForm.addEventListener("submit", handleProfileSubmit);
  elements.passwordForm.addEventListener("submit", handlePasswordSubmit);
  elements.pageNav.addEventListener("click", handlePageNavClick);
  elements.homePanel.addEventListener("click", handleHomePanelClick);
  elements.homeRefresh.addEventListener("click", refreshAll);

  elements.taskForm.addEventListener("submit", handleTaskSubmit);
  elements.taskReset.addEventListener("click", resetTaskForm);
  elements.taskTable.addEventListener("click", handleTaskTableClick);

  elements.serviceCreateForm.addEventListener("submit", handleServiceCreateSubmit);
  elements.serviceRefresh.addEventListener("click", refreshAll);
  elements.serviceTable.addEventListener("click", handleServiceTableClick);

  elements.consoleRefresh.addEventListener("click", loadConsole);
  elements.consoleServiceSelect.addEventListener("change", () => {
    state.selectedService = elements.consoleServiceSelect.value || null;
    loadConsole();
  });
  elements.consoleAutoRefresh.addEventListener("change", () => {
    state.consoleAutoRefresh = elements.consoleAutoRefresh.checked;
    if (state.consoleAutoRefresh) {
      loadConsole();
    }
  });
  elements.consoleCommandForm.addEventListener("submit", handleConsoleCommandSubmit);

  elements.ticketForm.addEventListener("submit", handleTicketSubmit);
  elements.ticketTable.addEventListener("click", handleTicketTableClick);
  document.querySelectorAll("button[data-ticket-tab]").forEach(button => {
    button.addEventListener("click", () => switchTicketTab(button.dataset.ticketTab));
  });

  elements.banForm.addEventListener("submit", handleBanSubmit);
  elements.banTable.addEventListener("click", handleBanTableClick);
  elements.liteBanTable.addEventListener("click", handleLiteBanTableClick);
  elements.banAppealTable.addEventListener("click", handleBanAppealTableClick);
  document.querySelectorAll("button[data-ban-tab]").forEach(button => {
    button.addEventListener("click", () => switchBanTab(button.dataset.banTab));
  });

  elements.groupForm.addEventListener("submit", handleGroupSubmit);
  elements.groupReset.addEventListener("click", resetGroupForm);
  elements.groupTable.addEventListener("click", handleGroupTableClick);

  elements.userForm.addEventListener("submit", handleUserSubmit);
  elements.userReset.addEventListener("click", resetUserForm);
  elements.userTable.addEventListener("click", handleUserTableClick);
  elements.permissionActionForm.addEventListener("submit", handlePermissionActionSubmit);
  elements.permissionRefresh.addEventListener("click", refreshAll);
  elements.permissionServerSelect.addEventListener("change", () => {
    state.selectedPermissionServer = elements.permissionServerSelect.value || "proxy";
    state.selectedPermissionSubject = "";
    localStorage.setItem("tccb-lp-server", state.selectedPermissionServer);
    localStorage.removeItem("tccb-lp-subject");
    renderPermissionSubjects();
  });
  elements.permissionSubjectSearch.addEventListener("input", renderPermissionSubjects);
  elements.permissionSubjectList.addEventListener("click", handlePermissionSubjectClick);
  elements.permissionNodeList.addEventListener("click", handlePermissionNodeClick);
  elements.settingsForm.addEventListener("submit", handleSettingsSubmit);
  elements.testMailForm.addEventListener("submit", handleTestMailSubmit);

  document.addEventListener("visibilitychange", handleVisibilityChange);
}

async function boot() {
  localStorage.removeItem("tccb-token");

  try {
    state.meta = await api("/api/meta", { auth: false });
    applyBranding(state.meta);
    populateSelect(elements.environmentSelect, state.meta.environments);
    populateSelect(elements.runtimeSelect, state.meta.runtimes);
    populateSelect(elements.ticketCategorySelect, state.meta.ticketCategories);
    populateSelect(elements.ticketPrioritySelect, state.meta.ticketPriorities);
    renderPermissionGrid();
    resetTaskForm();
    resetGroupForm();
    resetUserForm();
    prefillResetToken();

    if (state.token) {
      await restoreSession();
      setStatus(elements.authStatus, "Automatisch verbunden.", false);
    } else {
      showLogin("Bitte mit einem Panel-Benutzer anmelden.", false);
    }
  } catch (error) {
    clearSession();
    showLogin(error.message, true);
  }
}

async function restoreSession() {
  const session = await api("/api/auth/session");
  applySession(session);
  showPanel();
  await refreshAll();
  startConsolePolling();
}

async function handleLoginSubmit(event) {
  event.preventDefault();

  try {
    const result = await api("/api/auth/login", {
      method: "POST",
      auth: false,
      body: {
        username: elements.loginUsername.value.trim(),
        password: elements.loginPassword.value,
      },
    });
    state.token = result.token;
    localStorage.setItem("tccb-session", state.token);
    elements.loginPassword.value = "";
    applySession({ apiToken: false, user: result.user, availablePermissions: result.availablePermissions });
    showPanel();
    await refreshAll();
    startConsolePolling();
    setStatus(elements.authStatus, "Login erfolgreich. Panel ist verbunden.", false);
  } catch (error) {
    clearSession();
    showLogin(error.message, true);
  }
}

async function handleLogout() {
  try {
    if (state.token) {
      await api("/api/auth/logout", { method: "POST" });
    }
  } catch (error) {
    // Logout should still clear the browser state when the server token is already invalid.
  }
  clearSession();
  showLogin("Abgemeldet.", false);
}

async function handleResetRequestSubmit(event) {
  event.preventDefault();
  const form = new FormData(elements.resetRequestForm);

  try {
    const result = await api("/api/auth/password-reset/request", {
      method: "POST",
      auth: false,
      body: { usernameOrEmail: String(form.get("usernameOrEmail") || "").trim() },
    });
    setStatus(elements.resetStatus, result.message || "Reset angefordert.", false);
  } catch (error) {
    setStatus(elements.resetStatus, error.message, true);
  }
}

async function handleResetCompleteSubmit(event) {
  event.preventDefault();
  const form = new FormData(elements.resetCompleteForm);

  try {
    await api("/api/auth/password-reset/complete", {
      method: "POST",
      auth: false,
      body: {
        token: String(form.get("token") || "").trim(),
        newPassword: String(form.get("newPassword") || ""),
      },
    });
    elements.resetCompleteForm.reset();
    setStatus(elements.resetStatus, "Passwort wurde gesetzt. Du kannst dich jetzt einloggen.", false);
  } catch (error) {
    setStatus(elements.resetStatus, error.message, true);
  }
}

function prefillResetToken() {
  const token = new URLSearchParams(window.location.search).get("resetToken");
  if (token) {
    elements.resetCompleteForm.elements.token.value = token;
  }
}

async function handleProfileSubmit(event) {
  event.preventDefault();
  const form = new FormData(elements.profileForm);

  try {
    const user = await api("/api/auth/profile", {
      method: "PUT",
      body: {
        email: String(form.get("email") || "").trim(),
        minecraftName: String(form.get("minecraftName") || "").trim(),
        minecraftUniqueId: String(form.get("minecraftUniqueId") || "").trim(),
      },
    });
    applySession({ ...state.session, user });
    setStatus(elements.profileStatus, "Profil gespeichert.", false);
  } catch (error) {
    handleApiError(error, elements.profileStatus);
  }
}

async function handlePasswordSubmit(event) {
  event.preventDefault();
  const form = new FormData(elements.passwordForm);

  try {
    const user = await api("/api/auth/password", {
      method: "POST",
      body: {
        currentPassword: String(form.get("currentPassword") || ""),
        newPassword: String(form.get("newPassword") || ""),
      },
    });
    elements.passwordForm.reset();
    applySession({ ...state.session, user });
    setStatus(elements.profileStatus, "Passwort geaendert.", false);
  } catch (error) {
    handleApiError(error, elements.profileStatus);
  }
}

function applySession(session) {
  state.session = session;
  state.currentUser = session.user;
  state.meta.availablePermissions = session.availablePermissions || state.meta.availablePermissions || [];
  elements.currentUser.textContent = `${session.user.displayName || session.user.username} (${session.user.username})`;
  elements.currentPermissions.textContent = summarizePermissions(session.user.permissions || []);
  elements.profileForm.elements.email.value = session.user.email || "";
  elements.profileForm.elements.minecraftName.value = session.user.minecraftName || "";
  elements.profileForm.elements.minecraftUniqueId.value = session.user.minecraftUniqueId || "";
}

function showLogin(message, isError) {
  elements.authForm.classList.remove("hidden");
  elements.userCard.classList.add("hidden");
  elements.pageNav.classList.add("hidden");
  elements.summaryGrid.classList.add("hidden");
  document.querySelectorAll(".page-panel").forEach(panel => panel.classList.add("hidden"));
  setStatus(elements.authStatus, message, isError);
}

function showPanel() {
  elements.authForm.classList.add("hidden");
  elements.userCard.classList.remove("hidden");
  elements.pageNav.classList.remove("hidden");
  elements.summaryGrid.classList.remove("hidden");
  applyPermissions();
  switchPage(state.activePage);
}

function clearSession() {
  state.token = "";
  state.session = null;
  state.currentUser = null;
  state.tasks = [];
  state.services = [];
  state.nodes = [];
  state.tickets = [];
  state.ticketAudit = [];
  state.bans = [];
  state.liteBans = [];
  state.banAppeals = [];
  state.banAudit = [];
  state.securityUsers = [];
  state.securityGroups = [];
  state.permissionSubjects = [];
  state.permissionAudit = [];
  state.settings = null;
  state.selectedPermissionSubject = "";
  localStorage.removeItem("tccb-session");
  if (state.consoleTimer) {
    clearInterval(state.consoleTimer);
    state.consoleTimer = null;
  }
}

function applyPermissions() {
  document.querySelectorAll("[data-permission]").forEach(element => {
    element.classList.toggle("permission-hidden", !hasPermission(element.dataset.permission));
  });
}

function handlePageNavClick(event) {
  const button = event.target.closest("button[data-page-target]");
  if (!button) {
    return;
  }
  switchPage(button.dataset.pageTarget);
  if (button.dataset.banTabTarget) {
    switchBanTab(button.dataset.banTabTarget);
    elements.pageNav.querySelectorAll("button[data-page-target]").forEach(entry => entry.classList.remove("active"));
    button.classList.add("active");
  }
}

function switchPage(page) {
  const target = allowedPage(page) ? page : firstAllowedPage();
  if (!target) {
    return;
  }

  state.activePage = target;
  localStorage.setItem("tccb-page", target);

  document.querySelectorAll(".page-panel").forEach(panel => {
    panel.classList.toggle("hidden", panel.dataset.page !== target);
  });
  elements.pageNav.querySelectorAll("button[data-page-target]").forEach(button => {
    button.classList.toggle("active", button.dataset.pageTarget === target && !button.dataset.banTabTarget);
  });
}

function firstAllowedPage() {
  const button = [...elements.pageNav.querySelectorAll("button[data-page-target]")]
    .find(entry => !entry.dataset.permission || hasPermission(entry.dataset.permission));
  return button?.dataset.pageTarget || null;
}

function allowedPage(page) {
  const button = elements.pageNav.querySelector(`button[data-page-target="${cssEscape(page)}"]`);
  return Boolean(button && (!button.dataset.permission || hasPermission(button.dataset.permission)));
}

async function refreshAll() {
  if (!state.currentUser) {
    return;
  }

  if (hasPermission(PERMISSIONS.CLOUDNET_VIEW)) {
    const [overview, tasks, services, nodes] = await Promise.all([
      api("/api/overview"),
      api("/api/tasks"),
      api("/api/services"),
      api("/api/nodes"),
    ]);
    state.overview = overview;
    state.tasks = asArray(tasks);
    state.services = asArray(services);
    state.nodes = asArray(nodes);
    renderOverview();
    renderTasks();
    renderServices();
    renderNodes();
    refreshServiceSelectors();
  }

  if (hasPermission(PERMISSIONS.TICKETS_VIEW)) {
    const [tickets, ticketAudit] = await Promise.all([
      api("/api/tickets"),
      api("/api/tickets/audit"),
    ]);
    state.tickets = asArray(tickets);
    state.ticketAudit = asArray(ticketAudit);
    renderTickets();
    renderTicketArchive();
    renderTicketAudit();
  }

  if (hasPermission(PERMISSIONS.BANS_VIEW)) {
    const [bans, liteBans, banAppeals, banAudit] = await Promise.all([
      api("/api/bans"),
      api("/api/bans/litebans"),
      api("/api/ban-appeals"),
      api("/api/bans/audit"),
    ]);
    state.bans = asArray(bans);
    state.liteBans = asArray(liteBans);
    state.banAppeals = asArray(banAppeals);
    state.banAudit = asArray(banAudit);
    renderBans();
    renderLiteBans();
    renderBanAppeals();
    renderBanAppealArchive();
    renderBanAudit();
  }

  if (hasPermission(PERMISSIONS.USERS_MANAGE)) {
    const [groups, users] = await Promise.all([
      api("/api/security/groups"),
      api("/api/security/users"),
    ]);
    state.securityGroups = asArray(groups);
    state.securityUsers = asArray(users);
    renderPermissionGrid();
    renderUserGroupGrid();
    if (!elements.userForm.dataset.editingUser) {
      setCheckedValues(elements.userGroupGrid, ["viewer"]);
    }
    renderGroups();
    renderUsers();
  }

  if (hasPermission(`${PERMISSIONS.PROXY_PERMISSIONS_MANAGE},${PERMISSIONS.SERVER_PERMISSIONS_MANAGE}`)) {
    const [subjects, audit] = await Promise.all([
      api("/api/permissions/subjects"),
      api("/api/permissions/audit"),
    ]);
    state.permissionSubjects = asArray(subjects);
    state.permissionAudit = asArray(audit);
    refreshPermissionServerSelect();
    renderPermissionSubjects();
    renderPermissionAudit();
  }

  if (hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    state.settings = await api("/api/settings");
    renderSettings();
    renderBanAppeals();
    renderBanAppealArchive();
  }

  if (hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)) {
    if (!state.selectedService && state.services.length) {
      state.selectedService = state.services[0].name;
      elements.consoleServiceSelect.value = state.selectedService;
    }
    await loadConsole();
  }

  renderHome();
  applyPermissions();
  switchPage(state.activePage);
}

function renderHome() {
  setHomeMetric(
    elements.homeOpenTickets,
    hasPermission(PERMISSIONS.TICKETS_VIEW),
    state.tickets.filter(ticket => !isClosedTicket(ticket)).length);
  setHomeMetric(
    elements.homeClosedTickets,
    hasPermission(PERMISSIONS.TICKETS_VIEW),
    state.tickets.filter(isClosedTicket).length);
  setHomeMetric(
    elements.homeOpenAppeals,
    hasPermission(PERMISSIONS.BANS_VIEW),
    state.banAppeals.filter(appeal => !isArchivedAppeal(appeal)).length);
  setHomeMetric(
    elements.homeArchivedAppeals,
    hasPermission(PERMISSIONS.BANS_VIEW),
    state.banAppeals.filter(isArchivedAppeal).length);
  setHomeMetric(
    elements.homeActiveLiteBans,
    hasPermission(PERMISSIONS.BANS_VIEW),
    state.liteBans.filter(ban => ban.active).length);
  setHomeMetric(
    elements.homeRunningServices,
    hasPermission(PERMISSIONS.CLOUDNET_VIEW),
    state.overview?.runningServiceCount ?? 0);
}

function setHomeMetric(element, allowed, value) {
  element.textContent = allowed ? String(value) : "-";
}

function renderOverview() {
  if (!state.overview) {
    return;
  }

  elements.metricTasks.textContent = state.overview.taskCount;
  elements.metricServices.textContent = state.overview.serviceCount;
  elements.metricRunning.textContent = state.overview.runningServiceCount;
  elements.metricNodes.textContent = `${state.overview.onlineNodeCount}/${state.overview.registeredNodeCount}`;
}

function renderTasks() {
  if (!state.tasks.length) {
    elements.taskTable.innerHTML = `<tr><td colspan="5" class="muted">Keine Tasks vorhanden.</td></tr>`;
    return;
  }

  elements.taskTable.innerHTML = state.tasks.map(task => `
    <tr>
      <td>${escapeHtml(task.name)}</td>
      <td>${escapeHtml(task.environment)}</td>
      <td>${task.maxHeapMemory} MB</td>
      <td>${task.minServiceCount}</td>
      <td>${taskActions(task)}</td>
    </tr>
  `).join("");
}

function taskActions(task) {
  if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return `<span class="muted">Nur Ansicht</span>`;
  }

  return `
    <button data-task-action="edit" data-task-name="${escapeAttr(task.name)}" type="button">Bearbeiten</button>
    <button data-task-action="spawn" data-task-name="${escapeAttr(task.name)}" type="button">Service +1</button>
    <button data-task-action="delete" data-task-name="${escapeAttr(task.name)}" type="button">Loeschen</button>
  `;
}

function renderServices() {
  if (!state.services.length) {
    elements.serviceTable.innerHTML = `<tr><td colspan="6" class="muted">Keine Services vorhanden.</td></tr>`;
    return;
  }

  elements.serviceTable.innerHTML = state.services.map(service => `
    <tr>
      <td>${escapeHtml(service.name)}</td>
      <td>${escapeHtml(service.taskName)}</td>
      <td>${escapeHtml(service.state)}</td>
      <td>${escapeHtml(service.node || "-")}</td>
      <td>${escapeHtml(service.host)}:${service.port}</td>
      <td>${serviceActions(service)}</td>
    </tr>
  `).join("");
}

function serviceActions(service) {
  const actions = [];
  if (hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    actions.push(`<button data-service-action="start" data-service-name="${escapeAttr(service.name)}" type="button">Start</button>`);
    actions.push(`<button data-service-action="stop" data-service-name="${escapeAttr(service.name)}" type="button">Stop</button>`);
    actions.push(`<button data-service-action="restart" data-service-name="${escapeAttr(service.name)}" type="button">Restart</button>`);
    actions.push(`<button data-service-action="delete" data-service-name="${escapeAttr(service.name)}" type="button">Loeschen</button>`);
  }
  if (hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)) {
    actions.push(`<button data-service-action="console" data-service-name="${escapeAttr(service.name)}" type="button">Konsole</button>`);
  }
  return actions.join("") || `<span class="muted">Nur Ansicht</span>`;
}

function renderNodes() {
  if (!state.nodes.length) {
    elements.nodeGrid.innerHTML = `<p class="muted">Keine Node-Daten vorhanden.</p>`;
    return;
  }

  elements.nodeGrid.innerHTML = state.nodes.map(node => `
    <article class="node-card">
      <h3>${escapeHtml(node.uniqueId)}</h3>
      <p class="badge ${node.connected ? "badge-success" : "badge-danger"}">
        ${node.connected ? "verbunden" : "offline"}
      </p>
      <p class="muted">Listener: ${escapeHtml((node.listeners || []).join(", ") || "-")}</p>
      <p class="muted">Services: ${node.currentServicesCount}</p>
      <p class="muted">RAM: ${node.usedMemory}/${node.maxMemory} MB</p>
      <p class="muted">Auslastung: ${node.memoryUsagePercentage}%</p>
      <p class="muted">Version: ${escapeHtml(node.version || "-")}</p>
    </article>
  `).join("");
}

function renderTickets() {
  const activeTickets = state.tickets.filter(ticket => !isClosedTicket(ticket));
  if (!activeTickets.length) {
    elements.ticketTable.innerHTML = `<tr><td colspan="7" class="muted">Keine aktiven Tickets vorhanden.</td></tr>`;
    return;
  }

  elements.ticketTable.innerHTML = activeTickets.map(ticket => `
    <tr>
      <td>
        <strong>${escapeHtml(ticket.subject)}</strong><br>
        <span class="muted">${escapeHtml(ticket.category)} | ${escapeHtml(ticket.createdAt || "-")}</span>
      </td>
      <td>${escapeHtml(ticket.creatorName)}</td>
      <td>${escapeHtml(ticket.sourceServer || ticket.serviceName || "-")}</td>
      <td>${escapeHtml(ticket.status)}</td>
      <td>${escapeHtml(ticket.priority)}</td>
      <td>${escapeHtml(ticket.assignedTo || "-")}</td>
      <td>${ticketActions(ticket)}</td>
    </tr>
  `).join("");
}

function renderTicketArchive() {
  const archivedTickets = state.tickets.filter(isClosedTicket);
  if (!archivedTickets.length) {
    elements.ticketArchiveTable.innerHTML = `<tr><td colspan="7" class="muted">Noch keine geschlossenen Tickets im Archiv.</td></tr>`;
    return;
  }

  elements.ticketArchiveTable.innerHTML = archivedTickets.map(ticket => `
    <tr>
      <td>
        <strong>${escapeHtml(ticket.subject)}</strong><br>
        <span class="muted">${escapeHtml(ticket.category)} | ${escapeHtml(ticket.createdAt || "-")}</span>
      </td>
      <td>${escapeHtml(ticket.creatorName)}</td>
      <td>${escapeHtml(ticket.sourceServer || ticket.serviceName || "-")}</td>
      <td>${escapeHtml(ticket.status)}</td>
      <td>${escapeHtml(ticket.priority)}</td>
      <td>${escapeHtml(ticket.assignedTo || "-")}</td>
      <td>${escapeHtml(ticket.updatedAt || "-")}</td>
    </tr>
  `).join("");
}

function ticketActions(ticket) {
  if (!hasPermission(PERMISSIONS.TICKETS_MANAGE)) {
    return `<span class="muted">Nur Ansicht</span>`;
  }

  return `
    <button data-ticket-action="teleport" data-ticket-id="${escapeAttr(ticket.id)}" type="button">Teleport</button>
    <button data-ticket-action="open" data-ticket-id="${escapeAttr(ticket.id)}" type="button">Open</button>
    <button data-ticket-action="progress" data-ticket-id="${escapeAttr(ticket.id)}" type="button">In Progress</button>
    <button data-ticket-action="close" data-ticket-id="${escapeAttr(ticket.id)}" type="button">Close</button>
    <button data-ticket-action="assign" data-ticket-id="${escapeAttr(ticket.id)}" type="button">Zuweisen</button>
    <button data-ticket-action="comment" data-ticket-id="${escapeAttr(ticket.id)}" type="button">Kommentar</button>
  `;
}

function renderTicketAudit() {
  if (!state.ticketAudit.length) {
    elements.ticketAuditTable.innerHTML = `<tr><td colspan="5" class="muted">Noch kein Ticket-Audit vorhanden.</td></tr>`;
    return;
  }

  elements.ticketAuditTable.innerHTML = state.ticketAudit.slice(0, 80).map(entry => `
    <tr>
      <td>${escapeHtml(entry.createdAt || "-")}</td>
      <td>${escapeHtml(shortId(entry.ticketId))}</td>
      <td>${escapeHtml(entry.action)}</td>
      <td>${escapeHtml(entry.actor || "-")}</td>
      <td>${escapeHtml(entry.message || "-")}</td>
    </tr>
  `).join("");
}

function renderBans() {
  if (!state.bans.length) {
    elements.banTable.innerHTML = `<tr><td colspan="5" class="muted">Keine Bans vorhanden.</td></tr>`;
    return;
  }

  elements.banTable.innerHTML = state.bans.map(ban => `
    <tr>
      <td>
        <strong>${escapeHtml(ban.targetName)}</strong><br>
        <span class="muted">${escapeHtml(ban.targetUniqueId || ban.targetAddress || "-")}</span>
      </td>
      <td>
        <span class="badge ${ban.active ? "badge-danger" : "badge-success"}">
          ${ban.active ? (ban.expired ? "abgelaufen" : "aktiv") : "entbannt"}
        </span>
      </td>
      <td>${escapeHtml(ban.reason)}</td>
      <td>${escapeHtml(ban.expiresAt || "permanent")}</td>
      <td>${banActions(ban)}</td>
    </tr>
  `).join("");
}

function banActions(ban) {
  if (!hasPermission(PERMISSIONS.BANS_MANAGE) || !ban.active) {
    return `<span class="muted">-</span>`;
  }
  return `<button data-ban-action="deactivate" data-ban-id="${escapeAttr(ban.id)}" type="button">Deaktivieren</button>`;
}

function renderLiteBans() {
  if (!state.liteBans.length) {
    elements.liteBanTable.innerHTML = `<tr><td colspan="6" class="muted">Noch keine LiteBans synchronisiert.</td></tr>`;
    return;
  }

  elements.liteBanTable.innerHTML = state.liteBans.map(ban => `
    <tr>
      <td>${liteBanIdCell(ban)}</td>
      <td>
        <strong>${escapeHtml(ban.targetName || "-")}</strong><br>
        <span class="muted">${escapeHtml(ban.targetUniqueId || ban.targetAddress || "-")}</span>
      </td>
      <td>
        <span class="badge ${ban.active ? "badge-danger" : "badge-success"}">
          ${ban.active ? "aktiv" : "inaktiv"}
        </span>
      </td>
      <td>
        <span>${escapeHtml(formatDateTime(ban.createdAt))}</span><br>
        <span class="muted">bis ${escapeHtml(formatDateTime(ban.expiresAt) || "Permanent")}</span>
      </td>
      <td>${escapeHtml(ban.reason || "-")}</td>
      <td class="actions-cell">${liteBanActions(ban)}</td>
    </tr>
  `).join("");
}

function liteBanIdCell(ban) {
  const publicId = String(ban.publicId || "").trim();
  const internalId = String(ban.id || "").trim();
  if (isResolvedPublicBanId(publicId, internalId)) {
    return `
      <strong>${escapeHtml(publicId)}</strong><br>
      <span class="muted">intern: ${escapeHtml(internalId || "-")}</span>
    `;
  }
  return `
    <strong class="danger-text">Random-ID fehlt</strong><br>
    <span class="muted">Bridge pruefen, intern: ${escapeHtml(internalId || "-")}</span>
  `;
}

function isClosedTicket(ticket) {
  return String(ticket.status || "").toUpperCase() === "CLOSED";
}

function liteBanActions(ban) {
  if (!hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return `<span class="muted">bans.manage fehlt</span>`;
  }

  if (!ban.active) {
    return `
      <div class="action-buttons">
        <button data-liteban-action="extend" data-ban-id="${escapeAttr(ban.id)}" type="button">Neu setzen</button>
      </div>
    `;
  }

  return `
    <div class="action-buttons">
      <button data-liteban-action="unban" data-ban-id="${escapeAttr(ban.id)}" type="button">Aufheben</button>
      <button data-liteban-action="extend" data-ban-id="${escapeAttr(ban.id)}" type="button">Verlaengern</button>
    </div>
  `;
}

function renderBanAppeals() {
  const activeAppeals = state.banAppeals.filter(appeal => !isArchivedAppeal(appeal));
  if (!activeAppeals.length) {
    elements.banAppealTable.innerHTML = `<tr><td colspan="8" class="muted">Keine offenen Entbannungsantraege vorhanden.</td></tr>`;
    return;
  }

  elements.banAppealTable.innerHTML = activeAppeals.map(appeal => `
    <tr>
      <td>
        <span class="badge ${appealStatusClass(appeal.status)}">${escapeHtml(appeal.status || "-")}</span><br>
        <span class="muted">${escapeHtml(appealStatusText(appeal.status))}</span>
      </td>
      <td>
        <strong>${escapeHtml(appeal.publicBanId || "-")}</strong><br>
        <span class="muted">intern: ${escapeHtml(appeal.liteBanId || "-")}</span>
      </td>
      <td>${escapeHtml(appeal.playerName || "-")}</td>
      <td>${escapeHtml(appeal.email || "-")}</td>
      <td>${escapeHtml(shortText(appeal.reason, 90))}</td>
      <td>${appealEvidence(appeal)}</td>
      <td>${escapeHtml(appeal.createdAt || "-")}</td>
      <td>${banAppealActions(appeal)}</td>
    </tr>
  `).join("");
}

function renderBanAppealArchive() {
  const archivedAppeals = state.banAppeals.filter(isArchivedAppeal);
  if (!archivedAppeals.length) {
    elements.banAppealArchiveTable.innerHTML = `<tr><td colspan="7" class="muted">Noch keine abgeschlossenen Entbannungsantraege im Archiv.</td></tr>`;
    return;
  }

  elements.banAppealArchiveTable.innerHTML = archivedAppeals.map(appeal => `
    <tr>
      <td>
        <span class="badge ${appealStatusClass(appeal.status)}">${escapeHtml(appeal.status || "-")}</span><br>
        <span class="muted">${escapeHtml(appealStatusText(appeal.status))}</span>
      </td>
      <td>
        <strong>${escapeHtml(appeal.publicBanId || "-")}</strong><br>
        <span class="muted">intern: ${escapeHtml(appeal.liteBanId || "-")}</span>
      </td>
      <td>${escapeHtml(appeal.playerName || "-")}</td>
      <td>${escapeHtml(appeal.email || "-")}</td>
      <td>${escapeHtml(shortText(appeal.reason, 90))}</td>
      <td>${appealEvidence(appeal)}</td>
      <td>${escapeHtml(appeal.updatedAt || appeal.createdAt || "-")}</td>
    </tr>
  `).join("");
}

function isArchivedAppeal(appeal) {
  return ["ACCEPTED", "REJECTED", "CLOSED"].includes(String(appeal.status || "").toUpperCase());
}

function appealStatusClass(status) {
  return status === "ACCEPTED" ? "badge-success" : status === "REJECTED" ? "badge-danger" : "";
}

function appealStatusText(status) {
  const settings = state.settings || {};
  switch (String(status || "").toUpperCase()) {
    case "IN_REVIEW":
      return settings.appealStatusInReviewText || "";
    case "ACCEPTED":
      return settings.appealStatusAcceptedText || "";
    case "REJECTED":
      return settings.appealStatusRejectedText || "";
    case "CLOSED":
      return settings.appealStatusClosedText || "";
    default:
      return settings.appealStatusOpenText || "";
  }
}

function appealEvidence(appeal) {
  const links = [];
  if (appeal.videoLink) {
    links.push(`<a href="${escapeAttr(appeal.videoLink)}" target="_blank" rel="noreferrer">Video</a>`);
  }
  (appeal.attachments || []).forEach(attachment => {
    links.push(`<span title="${escapeAttr(attachment.storageReference || "")}">${escapeHtml(attachment.fileName || attachment.storageType || "Datei")}</span>`);
  });
  return links.join("<br>") || `<span class="muted">-</span>`;
}

function banAppealActions(appeal) {
  if (!hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return `<span class="muted">Nur Ansicht</span>`;
  }
  return `
    <button data-appeal-action="IN_REVIEW" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Pruefung</button>
    <button data-appeal-action="ACCEPTED" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Annehmen</button>
    <button data-appeal-action="REJECTED" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Ablehnen</button>
    <button data-appeal-action="CLOSED" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Schliessen</button>
  `;
}

function renderBanAudit() {
  if (!state.banAudit.length) {
    elements.banAuditTable.innerHTML = `<tr><td colspan="6" class="muted">Noch kein Ban-Audit vorhanden.</td></tr>`;
    return;
  }

  elements.banAuditTable.innerHTML = state.banAudit.slice(0, 120).map(entry => `
    <tr>
      <td>${escapeHtml(entry.createdAt || "-")}</td>
      <td>${escapeHtml(entry.source || "-")}</td>
      <td>${escapeHtml(entry.publicId || entry.banId || "-")}</td>
      <td>${escapeHtml(entry.action || "-")}</td>
      <td>${escapeHtml(entry.actor || "-")}</td>
      <td>${escapeHtml(entry.message || "-")}</td>
    </tr>
  `).join("");
}

function renderPermissionGrid() {
  const permissions = state.meta?.availablePermissions || [];
  elements.permissionGrid.innerHTML = permissions.map(permission => `
    <label class="check-card">
      <input name="permissions" type="checkbox" value="${escapeAttr(permission.id)}">
      <span>
        <strong>${escapeHtml(permission.label)}</strong>
        <small>${escapeHtml(permission.category)} | ${escapeHtml(permission.id)}</small>
        <small>${escapeHtml(permission.description)}</small>
      </span>
    </label>
  `).join("");
}

function renderUserGroupGrid() {
  elements.userGroupGrid.innerHTML = state.securityGroups.map(group => `
    <label class="check-card compact">
      <input name="groups" type="checkbox" value="${escapeAttr(group.name)}">
      <span>
        <strong>${escapeHtml(group.name)}</strong>
        <small>${escapeHtml((group.permissions || []).join(", ") || "-")}</small>
      </span>
    </label>
  `).join("");
}

function renderGroups() {
  if (!state.securityGroups.length) {
    elements.groupTable.innerHTML = `<tr><td colspan="4" class="muted">Keine Gruppen vorhanden.</td></tr>`;
    return;
  }

  elements.groupTable.innerHTML = state.securityGroups.map(group => `
    <tr>
      <td>${escapeHtml(group.name)}</td>
      <td>${escapeHtml((group.permissions || []).join(", "))}</td>
      <td>${group.system ? "Ja" : "Nein"}</td>
      <td>
        <button data-group-action="edit" data-group-name="${escapeAttr(group.name)}" type="button">Bearbeiten</button>
        ${group.system ? "" : `<button data-group-action="delete" data-group-name="${escapeAttr(group.name)}" type="button">Loeschen</button>`}
      </td>
    </tr>
  `).join("");
}

function renderUsers() {
  if (!state.securityUsers.length) {
    elements.userTable.innerHTML = `<tr><td colspan="5" class="muted">Keine Benutzer vorhanden.</td></tr>`;
    return;
  }

  elements.userTable.innerHTML = state.securityUsers.map(user => `
    <tr>
      <td>
        <strong>${escapeHtml(user.displayName || user.username)}</strong><br>
        <span class="muted">${escapeHtml(user.username)}</span>
      </td>
      <td>${escapeHtml((user.groups || []).join(", ") || "-")}</td>
      <td>
        <span class="badge ${user.enabled ? "badge-success" : "badge-danger"}">
          ${user.enabled ? "aktiv" : "deaktiviert"}
        </span>
      </td>
      <td>${escapeHtml(user.lastLoginAt || "-")}</td>
      <td>
        <button data-user-action="edit" data-user-name="${escapeAttr(user.username)}" type="button">Bearbeiten</button>
        <button data-user-action="delete" data-user-name="${escapeAttr(user.username)}" type="button">Loeschen</button>
      </td>
    </tr>
  `).join("");
}

function renderPermissionSubjects() {
  const subjects = filteredPermissionSubjects();
  if (!subjects.length) {
    elements.permissionSubjectList.innerHTML = `<p class="muted">Noch keine Subjects fuer diesen Server synchronisiert.</p>`;
    elements.permissionSelectedSummary.textContent = "Waehle einen Server mit synchronisierten LuckPerms-Daten aus.";
    elements.permissionNodeList.innerHTML = "";
    return;
  }

  if (!subjects.some(subject => permissionSubjectValue(subject) === state.selectedPermissionSubject)) {
    state.selectedPermissionSubject = permissionSubjectValue(subjects[0]);
    localStorage.setItem("tccb-lp-subject", state.selectedPermissionSubject);
  }

  elements.permissionSubjectList.innerHTML = subjects.map(subject => {
    const value = permissionSubjectValue(subject);
    const active = value === state.selectedPermissionSubject ? "active" : "";
    const permissionCount = (subject.permissions || []).length;
    const parentCount = (subject.parents || []).length;
    return `
      <button class="subject-card ${active}" data-subject-value="${escapeAttr(value)}" type="button">
        <span class="badge ${subject.type === "GROUP" ? "badge-success" : "badge-danger"}">${escapeHtml(subject.type || "-")}</span>
        <strong>${escapeHtml(subject.name || subject.id || "-")}</strong>
        <small>${permissionCount} Permissions | ${parentCount} Parents</small>
      </button>
    `;
  }).join("");

  renderSelectedPermissionSubject();
}

function renderPermissionAudit() {
  if (!state.permissionAudit.length) {
    elements.permissionAuditTable.innerHTML = `<tr><td colspan="6" class="muted">Noch kein LuckPerms-Audit vorhanden.</td></tr>`;
    return;
  }

  elements.permissionAuditTable.innerHTML = state.permissionAudit.slice(0, 120).map(entry => `
    <tr>
      <td>${escapeHtml(entry.serverId || "proxy")}</td>
      <td>${escapeHtml(entry.createdAt || "-")}</td>
      <td>${escapeHtml(entry.subjectType || "-")}:${escapeHtml(entry.subjectId || "-")}</td>
      <td>${escapeHtml(entry.action || "-")}</td>
      <td>${escapeHtml(entry.actor || "-")}</td>
      <td>${escapeHtml(entry.message || "-")}</td>
    </tr>
  `).join("");
}

function renderSettings() {
  const settings = state.settings;
  if (!settings || !elements.settingsForm) {
    return;
  }

  const form = elements.settingsForm.elements;
  setFormValue(form, "brandName", settings.brandName || state.meta?.brandName || "Network Control");
  setFormValue(form, "brandLogoUrl", settings.brandLogoUrl || "");
  setFormValue(form, "appealStatusOpenText", settings.appealStatusOpenText || "");
  setFormValue(form, "appealStatusInReviewText", settings.appealStatusInReviewText || "");
  setFormValue(form, "appealStatusAcceptedText", settings.appealStatusAcceptedText || "");
  setFormValue(form, "appealStatusRejectedText", settings.appealStatusRejectedText || "");
  setFormValue(form, "appealStatusClosedText", settings.appealStatusClosedText || "");
  setFormValue(form, "panelStorageBackend", settings.panelStorageBackend || "SQL");
  setFormValue(form, "panelSqlJdbcUrl", settings.panelSqlJdbcUrl || "");
  setFormValue(form, "panelSqlUsername", settings.panelSqlUsername || "");
  setFormValue(form, "panelSqlTable", settings.panelSqlTable || "");
  setFormChecked(form, "smtpEnabled", settings.smtpEnabled);
  setFormValue(form, "smtpHost", settings.smtpHost || "");
  setFormValue(form, "smtpPort", settings.smtpPort || 587);
  setFormValue(form, "smtpUsername", settings.smtpUsername || "");
  setFormValue(form, "smtpPassword", "");
  form.smtpPassword.placeholder = settings.smtpPasswordConfigured ? "gesetzt, leer lassen = behalten" : "nicht gesetzt";
  setFormValue(form, "smtpFrom", settings.smtpFrom || "");
  setFormChecked(form, "smtpStartTls", settings.smtpStartTls);
  setFormChecked(form, "smtpSsl", settings.smtpSsl);
  setFormChecked(form, "liteBansDatabaseEnabled", settings.liteBansDatabaseEnabled);
  setFormValue(form, "liteBansJdbcUrl", settings.liteBansJdbcUrl || "");
  setFormValue(form, "liteBansDatabaseUsername", settings.liteBansDatabaseUsername || "");
  setFormValue(form, "liteBansDatabasePassword", "");
  form.liteBansDatabasePassword.placeholder = settings.liteBansDatabasePasswordConfigured ? "gesetzt, leer lassen = behalten" : "nicht gesetzt";
  setFormValue(form, "liteBansTablePrefix", settings.liteBansTablePrefix || "litebans_");
  setFormValue(form, "liteBansDatabaseMaxRows", settings.liteBansDatabaseMaxRows || 1000);
  setFormValue(form, "liteBansBridgeBaseUrl", settings.liteBansBridgeBaseUrl || "");
  setFormValue(form, "liteBansBridgeSecret", "");
  form.liteBansBridgeSecret.placeholder = settings.liteBansBridgeSecretConfigured ? "gesetzt, leer lassen = behalten" : "leer = API Token";
  setFormValue(form, "liteBansBridgeConnectTimeoutMillis", settings.liteBansBridgeConnectTimeoutMillis || 2500);
  setFormValue(form, "liteBansBridgeReadTimeoutMillis", settings.liteBansBridgeReadTimeoutMillis || 5000);
  applyBranding(settings);
}

function applyBranding(source) {
  const brandName = source?.brandName || "Network Control";
  const logoUrl = source?.brandLogoUrl || "";
  elements.brandName.textContent = brandName;
  document.title = brandName;
  if (logoUrl) {
    elements.brandLogo.src = logoUrl;
    elements.brandLogo.classList.remove("hidden");
  } else {
    elements.brandLogo.removeAttribute("src");
    elements.brandLogo.classList.add("hidden");
  }
}

function setFormValue(form, name, value) {
  if (form[name]) {
    form[name].value = value;
  }
}

function setFormChecked(form, name, value) {
  if (form[name]) {
    form[name].checked = Boolean(value);
  }
}

function renderSelectedPermissionSubject() {
  const subject = selectedPermissionSubject();
  if (!subject) {
    elements.permissionSelectedSummary.textContent = "Waehle links eine Gruppe oder einen Spieler aus.";
    elements.permissionNodeList.innerHTML = "";
    return;
  }

  elements.permissionSelectedSummary.innerHTML = `
    <div>
      <span class="badge ${subject.type === "GROUP" ? "badge-success" : "badge-danger"}">${escapeHtml(subject.type || "-")}</span>
      <strong>${escapeHtml(subject.name || subject.id || "-")}</strong>
      <span class="muted">${escapeHtml(subject.serverId || "proxy")} | ${escapeHtml(subject.source || "LuckPerms")} | ${escapeHtml(subject.lastSyncedAt || "-")}</span>
    </div>
  `;

  const parents = (subject.parents || []).map(parent => nodeCard("parent", parent, true));
  const permissions = (subject.permissions || []).map(permission => {
    const parsed = parsePermissionNode(permission);
    return nodeCard("permission", parsed.key, parsed.value);
  });
  const nodes = [...parents, ...permissions];
  elements.permissionNodeList.innerHTML = nodes.length
    ? nodes.join("")
    : `<p class="muted">Noch keine Nodes fuer dieses Subject synchronisiert.</p>`;
}

function nodeCard(type, value, enabled) {
  const action = type === "parent" ? "REMOVE_PARENT" : "REMOVE_PERMISSION";
  const label = type === "parent" ? "Parent" : "Permission";
  return `
    <article class="node-card-editor ${enabled ? "" : "node-negative"}">
      <div>
        <span class="eyebrow">${label}</span>
        <strong>${escapeHtml(value)}</strong>
        <small>${type === "permission" ? (enabled ? "true / erlauben" : "false / verweigern") : "Gruppe geerbt"}</small>
      </div>
      <button
        class="ghost-button"
        data-node-action="${action}"
        data-node-value="${escapeAttr(value)}"
        data-node-state="${enabled ? "true" : "false"}"
        type="button">Entfernen</button>
    </article>
  `;
}

async function loadConsole() {
  if (!hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)) {
    return;
  }

  if (!state.selectedService) {
    state.lastConsoleText = "Kein Service ausgewaehlt.";
    elements.consoleOutput.textContent = state.lastConsoleText;
    return;
  }

  if (state.consoleRequestInFlight) {
    return;
  }

  try {
    state.consoleRequestInFlight = true;
    const consoleData = await api(`/api/services/${encodeURIComponent(state.selectedService)}/console?limit=120`);
    const nextText = consoleData.lines.length
      ? consoleData.lines.join("\n")
      : "Noch keine Log-Ausgabe fuer diesen Service.";

    if (nextText !== state.lastConsoleText) {
      state.lastConsoleText = nextText;
      elements.consoleOutput.textContent = nextText;
    }
  } catch (error) {
    handleApiError(error, elements.authStatus);
    elements.consoleOutput.textContent = error.message;
  } finally {
    state.consoleRequestInFlight = false;
  }
}

async function handleTaskSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return;
  }

  const form = new FormData(elements.taskForm);
  const editingTask = elements.taskForm.dataset.editingTask || "";
  const payload = {
    name: String(form.get("name") || "").trim(),
    environment: String(form.get("environment") || "").trim(),
    runtime: String(form.get("runtime") || "").trim(),
    startPort: Number(form.get("startPort") || 25565),
    minServiceCount: Number(form.get("minServiceCount") || 0),
    maxHeapMemory: Number(form.get("maxHeapMemory") || 1024),
    groups: splitCsv(form.get("groups")),
    associatedNodes: splitCsv(form.get("associatedNodes")),
    jvmOptions: splitCsv(form.get("jvmOptions")),
    processParameters: splitCsv(form.get("processParameters")),
    maintenance: form.get("maintenance") === "on",
    staticServices: form.get("staticServices") === "on",
    autoDeleteOnStop: form.get("autoDeleteOnStop") === "on",
  };

  try {
    if (editingTask) {
      await api(`/api/tasks/${encodeURIComponent(editingTask)}`, { method: "PUT", body: payload });
      setStatus(elements.taskStatus, `Task ${editingTask} aktualisiert.`, false);
    } else {
      await api("/api/tasks", { method: "POST", body: payload });
      setStatus(elements.taskStatus, `Task ${payload.name} erstellt.`, false);
    }
    resetTaskForm();
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.taskStatus);
  }
}

async function handleServiceCreateSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return;
  }

  const form = new FormData(elements.serviceCreateForm);
  const payload = {
    taskName: String(form.get("taskName") || "").trim(),
    amount: Number(form.get("amount") || 1),
    node: String(form.get("node") || "").trim(),
    startImmediately: form.get("startImmediately") === "on",
  };

  if (!payload.node) {
    delete payload.node;
  }

  try {
    const result = await api("/api/services", { method: "POST", body: payload });
    const created = asArray(result.results).filter(entry => entry.serviceName).map(entry => entry.serviceName).join(", ");
    setStatus(elements.serviceCreateStatus, created || `Ergebnis: ${asArray(result.results).map(entry => entry.state).join(", ")}`, false);
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.serviceCreateStatus);
  }
}

async function handleConsoleCommandSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.CLOUDNET_COMMAND)) {
    return;
  }

  if (!state.selectedService) {
    elements.consoleOutput.textContent = "Bitte zuerst einen Service auswaehlen.";
    return;
  }

  const command = elements.consoleCommandInput.value.trim();
  if (!command) {
    return;
  }

  try {
    await api(`/api/services/${encodeURIComponent(state.selectedService)}/command`, {
      method: "POST",
      body: { command },
    });
    elements.consoleCommandInput.value = "";
    await loadConsole();
  } catch (error) {
    handleApiError(error, elements.authStatus);
    elements.consoleOutput.textContent = error.message;
  }
}

async function handleTicketSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.TICKETS_CREATE)) {
    return;
  }

  const form = new FormData(elements.ticketForm);
  const sourceServer = String(form.get("sourceServer") || "").trim();
  const payload = {
    creatorName: String(form.get("creatorName") || "").trim(),
    creatorUniqueId: String(form.get("creatorUniqueId") || "").trim(),
    category: String(form.get("category") || "").trim(),
    priority: String(form.get("priority") || "").trim(),
    subject: String(form.get("subject") || "").trim(),
    sourceServer,
    serviceName: sourceServer,
    content: String(form.get("content") || "").trim(),
  };

  if (!payload.creatorUniqueId) {
    delete payload.creatorUniqueId;
  }
  if (!payload.sourceServer) {
    delete payload.sourceServer;
    delete payload.serviceName;
  }

  try {
    const created = await api("/api/tickets", { method: "POST", body: payload });
    elements.ticketForm.reset();
    populateSelect(elements.ticketCategorySelect, state.meta.ticketCategories, payload.category);
    populateSelect(elements.ticketPrioritySelect, state.meta.ticketPriorities, payload.priority);
    setStatus(elements.ticketStatus, `Ticket ${created.subject} erstellt.`, false);
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.ticketStatus);
  }
}

async function handleBanSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return;
  }

  const form = new FormData(elements.banForm);
  const payload = {
    targetName: String(form.get("targetName") || "").trim(),
    targetUniqueId: String(form.get("targetUniqueId") || "").trim(),
    targetAddress: String(form.get("targetAddress") || "").trim(),
    issuedBy: String(form.get("issuedBy") || "").trim(),
    durationMinutes: Number(form.get("durationMinutes") || 0),
    reason: String(form.get("reason") || "").trim(),
  };

  if (!payload.targetUniqueId) {
    delete payload.targetUniqueId;
  }
  if (!payload.targetAddress) {
    delete payload.targetAddress;
  }

  try {
    const created = await api("/api/bans", { method: "POST", body: payload });
    elements.banForm.reset();
    elements.banForm.elements.durationMinutes.value = 0;
    setStatus(elements.banStatus, `Ban fuer ${created.targetName} angelegt.`, false);
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.banStatus);
  }
}

async function handleGroupSubmit(event) {
  event.preventDefault();
  const editingGroup = elements.groupForm.dataset.editingGroup || "";
  const payload = {
    name: String(new FormData(elements.groupForm).get("name") || "").trim(),
    permissions: checkedValues(elements.permissionGrid, "permissions"),
  };

  try {
    if (editingGroup) {
      await api(`/api/security/groups/${encodeURIComponent(editingGroup)}`, {
        method: "PUT",
        body: { permissions: payload.permissions },
      });
      setStatus(elements.groupStatus, `Gruppe ${editingGroup} aktualisiert.`, false);
    } else {
      await api("/api/security/groups", { method: "POST", body: payload });
      setStatus(elements.groupStatus, `Gruppe ${payload.name} gespeichert.`, false);
    }
    resetGroupForm();
    await reloadSessionAndData();
  } catch (error) {
    handleApiError(error, elements.groupStatus);
  }
}

async function handleUserSubmit(event) {
  event.preventDefault();
  const form = new FormData(elements.userForm);
  const editingUser = elements.userForm.dataset.editingUser || "";
  const payload = {
    username: String(form.get("username") || "").trim(),
    displayName: String(form.get("displayName") || "").trim(),
    password: String(form.get("password") || ""),
    groups: checkedValues(elements.userGroupGrid, "groups"),
    enabled: form.get("enabled") === "on",
  };

  if (editingUser && !payload.password) {
    delete payload.password;
  }

  try {
    if (editingUser) {
      await api(`/api/security/users/${encodeURIComponent(editingUser)}`, {
        method: "PUT",
        body: payload,
      });
      setStatus(elements.userStatus, `Benutzer ${editingUser} aktualisiert.`, false);
    } else {
      await api("/api/security/users", { method: "POST", body: payload });
      setStatus(elements.userStatus, `Benutzer ${payload.username} gespeichert.`, false);
    }
    resetUserForm();
    await reloadSessionAndData();
  } catch (error) {
    handleApiError(error, elements.userStatus);
  }
}

async function handlePermissionActionSubmit(event) {
  event.preventDefault();
  if (!hasPermission(`${PERMISSIONS.PROXY_PERMISSIONS_MANAGE},${PERMISSIONS.SERVER_PERMISSIONS_MANAGE}`)) {
    return;
  }

  const form = new FormData(elements.permissionActionForm);
  const subject = selectedPermissionSubject();
  const nodeType = String(form.get("nodeType") || "permission");
  const nodeValue = String(form.get("nodeValue") || "").trim();
  const payload = {
    action: nodeType === "parent" ? "ADD_PARENT" : "ADD_PERMISSION",
    serverId: subject?.serverId || state.selectedPermissionServer || "proxy",
    subjectType: subject?.type,
    subjectId: subject?.id,
    permission: nodeType === "permission" ? nodeValue : "",
    parent: nodeType === "parent" ? nodeValue : "",
    value: String(form.get("nodeValueState") || "true") === "true",
    actor: state.currentUser?.displayName || state.currentUser?.username || "Panel",
  };

  if (!subject) {
    setStatus(elements.permissionStatus, "Bitte zuerst links ein Subject auswaehlen.", true);
    return;
  }

  if (!nodeValue) {
    setStatus(elements.permissionStatus, "Bitte einen Node-Wert eintragen.", true);
    return;
  }

  if (!canManagePermissionServer(payload.serverId)) {
    setStatus(elements.permissionStatus, "Dir fehlt das passende Proxy- oder Unterserver-Recht.", true);
    return;
  }

  try {
    await api("/api/permissions/actions", { method: "POST", body: payload });
    elements.permissionActionForm.elements.nodeValue.value = "";
    setStatus(elements.permissionStatus, "Node wurde in die LuckPerms-Queue gelegt.", false);
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.permissionStatus);
  }
}

async function handleSettingsSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    return;
  }

  const form = new FormData(elements.settingsForm);
  const payload = {
    brandName: String(form.get("brandName") || "").trim(),
    brandLogoUrl: String(form.get("brandLogoUrl") || "").trim(),
    appealStatusOpenText: String(form.get("appealStatusOpenText") || "").trim(),
    appealStatusInReviewText: String(form.get("appealStatusInReviewText") || "").trim(),
    appealStatusAcceptedText: String(form.get("appealStatusAcceptedText") || "").trim(),
    appealStatusRejectedText: String(form.get("appealStatusRejectedText") || "").trim(),
    appealStatusClosedText: String(form.get("appealStatusClosedText") || "").trim(),
    smtpEnabled: Boolean(form.get("smtpEnabled")),
    smtpHost: String(form.get("smtpHost") || "").trim(),
    smtpPort: Number(form.get("smtpPort") || 587),
    smtpUsername: String(form.get("smtpUsername") || "").trim(),
    smtpFrom: String(form.get("smtpFrom") || "").trim(),
    smtpStartTls: Boolean(form.get("smtpStartTls")),
    smtpSsl: Boolean(form.get("smtpSsl")),
    liteBansDatabaseEnabled: Boolean(form.get("liteBansDatabaseEnabled")),
    liteBansJdbcUrl: String(form.get("liteBansJdbcUrl") || "").trim(),
    liteBansDatabaseUsername: String(form.get("liteBansDatabaseUsername") || "").trim(),
    liteBansTablePrefix: String(form.get("liteBansTablePrefix") || "").trim(),
    liteBansDatabaseMaxRows: Number(form.get("liteBansDatabaseMaxRows") || 1000),
    liteBansBridgeBaseUrl: String(form.get("liteBansBridgeBaseUrl") || "").trim(),
    liteBansBridgeConnectTimeoutMillis: Number(form.get("liteBansBridgeConnectTimeoutMillis") || 2500),
    liteBansBridgeReadTimeoutMillis: Number(form.get("liteBansBridgeReadTimeoutMillis") || 5000),
  };

  const smtpPassword = String(form.get("smtpPassword") || "");
  const liteBansDatabasePassword = String(form.get("liteBansDatabasePassword") || "");
  const liteBansBridgeSecret = String(form.get("liteBansBridgeSecret") || "");
  if (smtpPassword) {
    payload.smtpPassword = smtpPassword;
  }
  if (liteBansDatabasePassword) {
    payload.liteBansDatabasePassword = liteBansDatabasePassword;
  }
  if (liteBansBridgeSecret) {
    payload.liteBansBridgeSecret = liteBansBridgeSecret;
  }

  try {
    state.settings = await api("/api/settings", { method: "PUT", body: payload });
    renderSettings();
    setStatus(elements.settingsStatus, "Einstellungen gespeichert.", false);
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.settingsStatus);
  }
}

async function handleTestMailSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    return;
  }

  const recipient = String(new FormData(elements.testMailForm).get("recipient") || "").trim();
  if (!recipient) {
    setStatus(elements.testMailStatus, "Bitte Empfaenger eintragen.", true);
    return;
  }

  try {
    const result = await api("/api/settings/test-mail", {
      method: "POST",
      body: { recipient },
    });
    setStatus(elements.testMailStatus, result.message || "Testmail wurde versendet.", false);
  } catch (error) {
    handleApiError(error, elements.testMailStatus);
  }
}

function handlePermissionSubjectClick(event) {
  const button = event.target.closest("button[data-subject-value]");
  if (!button) {
    return;
  }
  state.selectedPermissionSubject = button.dataset.subjectValue;
  localStorage.setItem("tccb-lp-subject", state.selectedPermissionSubject);
  renderPermissionSubjects();
}

async function handlePermissionNodeClick(event) {
  const button = event.target.closest("button[data-node-action]");
  if (!button) {
    return;
  }

  const subject = selectedPermissionSubject();
  if (!subject) {
    setStatus(elements.permissionStatus, "Bitte zuerst ein Subject auswaehlen.", true);
    return;
  }
  if (!canManagePermissionServer(subject.serverId)) {
    setStatus(elements.permissionStatus, "Dir fehlt das passende Proxy- oder Unterserver-Recht.", true);
    return;
  }

  const action = button.dataset.nodeAction;
  const value = button.dataset.nodeValue;
  const payload = {
    action,
    serverId: subject.serverId || "proxy",
    subjectType: subject.type,
    subjectId: subject.id,
    permission: action === "REMOVE_PERMISSION" ? value : "",
    parent: action === "REMOVE_PARENT" ? value : "",
    value: button.dataset.nodeState !== "false",
    actor: state.currentUser?.displayName || state.currentUser?.username || "Panel",
  };

  try {
    await api("/api/permissions/actions", { method: "POST", body: payload });
    setStatus(elements.permissionStatus, "Entfernen wurde in die LuckPerms-Queue gelegt.", false);
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.permissionStatus);
  }
}

async function reloadSessionAndData() {
  const session = await api("/api/auth/session");
  applySession(session);
  applyPermissions();
  await refreshAll();
}

async function handleTaskTableClick(event) {
  const button = event.target.closest("button[data-task-action]");
  if (!button || !hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return;
  }

  const action = button.dataset.taskAction;
  const taskName = button.dataset.taskName;
  const task = state.tasks.find(entry => entry.name === taskName);
  if (!task) {
    return;
  }

  if (action === "edit") {
    fillTaskForm(task);
    return;
  }

  if (action === "spawn") {
    try {
      await api("/api/services", {
        method: "POST",
        body: { taskName, amount: 1, startImmediately: true },
      });
      await refreshAll();
      setStatus(elements.serviceCreateStatus, `Service fuer ${taskName} erstellt.`, false);
    } catch (error) {
      handleApiError(error, elements.serviceCreateStatus);
    }
    return;
  }

  if (action === "delete" && confirm(`Task ${taskName} wirklich loeschen?`)) {
    try {
      await api(`/api/tasks/${encodeURIComponent(taskName)}`, { method: "DELETE" });
      if (elements.taskForm.dataset.editingTask === taskName) {
        resetTaskForm();
      }
      await refreshAll();
    } catch (error) {
      handleApiError(error, elements.taskStatus);
    }
  }
}

async function handleServiceTableClick(event) {
  const button = event.target.closest("button[data-service-action]");
  if (!button) {
    return;
  }

  const action = button.dataset.serviceAction;
  const serviceName = button.dataset.serviceName;

  try {
    if (action === "console") {
      if (!hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)) {
        return;
      }
      state.selectedService = serviceName;
      elements.consoleServiceSelect.value = serviceName;
      switchPage("cloudnet");
      await loadConsole();
      return;
    }

    if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
      return;
    }

    if (action === "delete") {
      if (!confirm(`Service ${serviceName} wirklich loeschen?`)) {
        return;
      }
      await api(`/api/services/${encodeURIComponent(serviceName)}`, { method: "DELETE" });
    } else {
      await api(`/api/services/${encodeURIComponent(serviceName)}/${action}`, { method: "POST" });
    }

    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.serviceCreateStatus);
  }
}

async function handleTicketTableClick(event) {
  const button = event.target.closest("button[data-ticket-action]");
  if (!button || !hasPermission(PERMISSIONS.TICKETS_MANAGE)) {
    return;
  }

  const ticketId = button.dataset.ticketId;
  const action = button.dataset.ticketAction;

  try {
    if (action === "assign") {
      const assignedTo = prompt("An wen soll das Ticket zugewiesen werden?");
      const actor = prompt("Wer fuehrt die Zuweisung aus?", state.currentUser?.displayName || state.currentUser?.username || "");
      if (!assignedTo || !actor) {
        return;
      }
      await api(`/api/tickets/${encodeURIComponent(ticketId)}/assign`, {
        method: "POST",
        body: { assignedTo, actor },
      });
    } else if (action === "comment") {
      const author = prompt("Autor des Kommentars?", state.currentUser?.displayName || state.currentUser?.username || "");
      const message = prompt("Kommentartext?");
      const internal = confirm("Interner Kommentar?");
      if (!author || !message) {
        return;
      }
      await api(`/api/tickets/${encodeURIComponent(ticketId)}/comments`, {
        method: "POST",
        body: { author, message, internal },
      });
    } else if (action === "teleport") {
      const ticket = state.tickets.find(entry => entry.id === ticketId);
      const staffName = prompt(
        "Welcher Teamler soll teleportiert werden? Das muss dein Minecraft-Name sein.",
        state.currentUser?.minecraftName || state.currentUser?.displayName || state.currentUser?.username || "");
      if (!ticket || !staffName) {
        return;
      }
      await api("/api/player-actions/teleport", {
        method: "POST",
        body: {
          staffName,
          targetName: ticket.creatorName,
          targetUniqueId: ticket.creatorUniqueId || "",
          targetServer: ticket.sourceServer || ticket.serviceName || "",
          ticketId,
          actor: state.currentUser?.displayName || state.currentUser?.username || staffName,
        },
      });
      setStatus(elements.ticketStatus, "Teleport wurde an Velocity uebergeben.", false);
    } else {
      const actor = prompt("Wer aendert den Ticket-Status?", state.currentUser?.displayName || state.currentUser?.username || "");
      if (!actor) {
        return;
      }
      const status = action === "open" ? "OPEN" : action === "progress" ? "IN_PROGRESS" : "CLOSED";
      await api(`/api/tickets/${encodeURIComponent(ticketId)}/status`, {
        method: "POST",
        body: { actor, status },
      });
    }

    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.ticketStatus);
  }
}

function switchTicketTab(tab) {
  document.querySelectorAll(".ticket-tab-panel").forEach(panel => {
    panel.classList.toggle("hidden", panel.dataset.ticketPanel !== tab);
  });
  document.querySelectorAll("button[data-ticket-tab]").forEach(button => {
    button.classList.toggle("active", button.dataset.ticketTab === tab);
  });
}

function handleHomePanelClick(event) {
  const button = event.target.closest("button[data-home-page]");
  if (!button) {
    return;
  }

  switchPage(button.dataset.homePage);
  if (button.dataset.ticketTabTarget) {
    switchTicketTab(button.dataset.ticketTabTarget);
  }
  if (button.dataset.banTabTarget) {
    switchBanTab(button.dataset.banTabTarget);
  }
}

async function handleBanTableClick(event) {
  const button = event.target.closest("button[data-ban-action]");
  if (!button || !hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return;
  }

  const banId = button.dataset.banId;
  if (button.dataset.banAction !== "deactivate") {
    return;
  }

  const removedBy = prompt("Wer hebt den Ban auf?", state.currentUser?.displayName || state.currentUser?.username || "");
  if (!removedBy) {
    return;
  }

  try {
    await api(`/api/bans/${encodeURIComponent(banId)}/deactivate`, {
      method: "POST",
      body: { removedBy },
    });
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.banStatus);
  }
}

async function handleLiteBanTableClick(event) {
  const button = event.target.closest("button[data-liteban-action]");
  if (!button || !hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return;
  }

  const banId = button.dataset.banId;
  const action = button.dataset.litebanAction;
  const actor = state.currentUser?.displayName || state.currentUser?.username || "Panel";

  try {
    if (action === "unban") {
      const reason = prompt("Grund fuer das Aufheben?", "Unban via Panel");
      if (reason === null) {
        return;
      }
      await api(`/api/bans/litebans/${encodeURIComponent(banId)}/unban`, {
        method: "POST",
        body: { actor, reason },
      });
      setStatus(elements.banStatus, "Unban wurde an Velocity uebergeben.", false);
    }

    if (action === "extend") {
      const duration = prompt("Neue/weitere Dauer fuer LiteBans, z.B. 7d, 30d, 1mo");
      if (!duration) {
        return;
      }
      const reason = prompt("Grund fuer die Verlaengerung?", "Ban via Panel verlaengert");
      if (reason === null) {
        return;
      }
      await api(`/api/bans/litebans/${encodeURIComponent(banId)}/extend`, {
        method: "POST",
        body: { actor, duration, reason },
      });
      setStatus(elements.banStatus, "Verlaengerung wurde an Velocity uebergeben.", false);
    }

    await refreshAll();
    switchBanTab("litebans");
  } catch (error) {
    handleApiError(error, elements.banStatus);
  }
}

async function handleBanAppealTableClick(event) {
  const button = event.target.closest("button[data-appeal-action]");
  if (!button || !hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return;
  }

  const teamNote = prompt("Team-Notiz fuer die Statusseite? Leer lassen, wenn keine Notiz gesetzt werden soll.", "");
  if (teamNote === null) {
    return;
  }

  try {
    await api(`/api/ban-appeals/${encodeURIComponent(button.dataset.appealId)}/status`, {
      method: "POST",
      body: {
        status: button.dataset.appealAction,
        teamNote,
      },
    });
    await refreshAll();
    switchBanTab(isArchivedAppeal({ status: button.dataset.appealAction }) ? "appeal-archive" : "appeals");
  } catch (error) {
    handleApiError(error, elements.banStatus);
  }
}

function switchBanTab(tab) {
  document.querySelectorAll(".ban-tab-panel").forEach(panel => {
    panel.classList.toggle("hidden", panel.dataset.banPanel !== tab);
  });
  document.querySelectorAll("button[data-ban-tab]").forEach(button => {
    button.classList.toggle("active", button.dataset.banTab === tab);
  });
}

async function handleGroupTableClick(event) {
  const button = event.target.closest("button[data-group-action]");
  if (!button) {
    return;
  }

  const groupName = button.dataset.groupName;
  const group = state.securityGroups.find(entry => entry.name === groupName);
  if (!group) {
    return;
  }

  if (button.dataset.groupAction === "edit") {
    fillGroupForm(group);
    return;
  }

  if (button.dataset.groupAction === "delete" && confirm(`Gruppe ${groupName} wirklich loeschen?`)) {
    try {
      await api(`/api/security/groups/${encodeURIComponent(groupName)}`, { method: "DELETE" });
      resetGroupForm();
      await reloadSessionAndData();
    } catch (error) {
      handleApiError(error, elements.groupStatus);
    }
  }
}

async function handleUserTableClick(event) {
  const button = event.target.closest("button[data-user-action]");
  if (!button) {
    return;
  }

  const username = button.dataset.userName;
  const user = state.securityUsers.find(entry => entry.username === username);
  if (!user) {
    return;
  }

  if (button.dataset.userAction === "edit") {
    fillUserForm(user);
    return;
  }

  if (button.dataset.userAction === "delete" && confirm(`Benutzer ${username} wirklich loeschen?`)) {
    try {
      await api(`/api/security/users/${encodeURIComponent(username)}`, { method: "DELETE" });
      resetUserForm();
      await reloadSessionAndData();
    } catch (error) {
      handleApiError(error, elements.userStatus);
    }
  }
}

function fillTaskForm(task) {
  elements.taskForm.dataset.editingTask = task.name;
  elements.taskSubmit.textContent = `Task ${task.name} aktualisieren`;

  const form = elements.taskForm.elements;
  form.name.value = task.name;
  form.name.readOnly = true;
  form.environment.value = task.environment;
  form.runtime.value = task.runtime;
  form.startPort.value = task.startPort;
  form.minServiceCount.value = task.minServiceCount;
  form.maxHeapMemory.value = task.maxHeapMemory;
  form.groups.value = (task.groups || []).join(", ");
  form.associatedNodes.value = (task.associatedNodes || []).join(", ");
  form.jvmOptions.value = (task.jvmOptions || []).join(", ");
  form.processParameters.value = (task.processParameters || []).join(", ");
  form.maintenance.checked = Boolean(task.maintenance);
  form.staticServices.checked = Boolean(task.staticServices);
  form.autoDeleteOnStop.checked = Boolean(task.autoDeleteOnStop);
}

function resetTaskForm() {
  elements.taskForm.reset();
  elements.taskForm.dataset.editingTask = "";
  elements.taskSubmit.textContent = "Task speichern";
  elements.taskForm.elements.name.readOnly = false;

  if (state.meta) {
    elements.taskForm.elements.environment.value = state.meta.environments[0] || "";
    elements.taskForm.elements.runtime.value = state.meta.runtimes[0] || "";
  }

  elements.taskForm.elements.startPort.value = 25565;
  elements.taskForm.elements.minServiceCount.value = 1;
  elements.taskForm.elements.maxHeapMemory.value = 1024;
  elements.taskForm.elements.autoDeleteOnStop.checked = true;
  setStatus(elements.taskStatus, "", false);
}

function fillGroupForm(group) {
  elements.groupForm.dataset.editingGroup = group.name;
  elements.groupSubmit.textContent = `Gruppe ${group.name} aktualisieren`;
  elements.groupForm.elements.name.value = group.name;
  elements.groupForm.elements.name.readOnly = true;
  setCheckedValues(elements.permissionGrid, group.permissions || []);
  setStatus(elements.groupStatus, "", false);
}

function resetGroupForm() {
  elements.groupForm.reset();
  elements.groupForm.dataset.editingGroup = "";
  elements.groupSubmit.textContent = "Gruppe speichern";
  elements.groupForm.elements.name.readOnly = false;
  setCheckedValues(elements.permissionGrid, []);
  setStatus(elements.groupStatus, "", false);
}

function fillUserForm(user) {
  elements.userForm.dataset.editingUser = user.username;
  elements.userSubmit.textContent = `Benutzer ${user.username} aktualisieren`;
  const form = elements.userForm.elements;
  form.username.value = user.username;
  form.username.readOnly = true;
  form.displayName.value = user.displayName || "";
  form.password.value = "";
  form.enabled.checked = Boolean(user.enabled);
  setCheckedValues(elements.userGroupGrid, user.groups || []);
  setStatus(elements.userStatus, "", false);
}

function resetUserForm() {
  elements.userForm.reset();
  elements.userForm.dataset.editingUser = "";
  elements.userSubmit.textContent = "Benutzer speichern";
  elements.userForm.elements.username.readOnly = false;
  elements.userForm.elements.enabled.checked = true;
  setCheckedValues(elements.userGroupGrid, ["viewer"]);
  setStatus(elements.userStatus, "", false);
}

function refreshServiceSelectors() {
  const selectedTask = elements.serviceTaskSelect.value;
  const selectedConsole = state.selectedService || elements.consoleServiceSelect.value;

  populateSelect(elements.serviceTaskSelect, state.tasks.map(task => task.name), selectedTask);
  populateSelect(elements.consoleServiceSelect, state.services.map(service => service.name), selectedConsole);
  populateDatalist(elements.serviceNameList, state.services.map(service => service.name));

  if (state.services.some(service => service.name === selectedConsole)) {
    state.selectedService = selectedConsole;
    elements.consoleServiceSelect.value = selectedConsole;
  } else if (state.services.length) {
    state.selectedService = state.services[0].name;
    elements.consoleServiceSelect.value = state.selectedService;
  } else {
    state.selectedService = null;
  }
}

function refreshPermissionServerSelect() {
  const servers = permissionServers();
  if (!servers.includes(state.selectedPermissionServer)
    || (state.selectedPermissionServer === "proxy" && !hasPermissionSubjectsForServer("proxy") && servers.some(server => hasPermissionSubjectsForServer(server)))) {
    state.selectedPermissionServer = servers.find(server => hasPermissionSubjectsForServer(server)) || "proxy";
  }
  populateSelect(elements.permissionServerSelect, servers, state.selectedPermissionServer);
  state.selectedPermissionServer = elements.permissionServerSelect.value || "proxy";
  localStorage.setItem("tccb-lp-server", state.selectedPermissionServer);
}

function permissionServers() {
  const servers = new Set(["proxy"]);
  state.permissionSubjects.forEach(subject => servers.add(subject.serverId || "proxy"));
  return [...servers].sort((left, right) => left.localeCompare(right));
}

function hasPermissionSubjectsForServer(serverId) {
  return state.permissionSubjects.some(subject => (subject.serverId || "proxy") === serverId);
}

function filteredPermissionSubjects() {
  const query = String(elements.permissionSubjectSearch.value || "").trim().toLowerCase();
  return state.permissionSubjects
    .filter(subject => (subject.serverId || "proxy") === state.selectedPermissionServer)
    .filter(subject => !query || permissionSubjectSearchText(subject).includes(query))
    .sort((left, right) => `${left.type}:${left.name}`.localeCompare(`${right.type}:${right.name}`));
}

function permissionSubjectValue(subject) {
  return `${subject.serverId || "proxy"}|${subject.type || ""}|${subject.id || subject.name || ""}`;
}

function selectedPermissionSubject() {
  return state.permissionSubjects.find(subject => permissionSubjectValue(subject) === state.selectedPermissionSubject) || null;
}

function permissionSubjectSearchText(subject) {
  return [
    subject.serverId,
    subject.type,
    subject.id,
    subject.name,
    ...(subject.permissions || []),
    ...(subject.parents || []),
  ].join(" ").toLowerCase();
}

function parsePermissionNode(node) {
  const value = String(node || "");
  if (value.startsWith("-")) {
    return { key: value.slice(1), value: false };
  }
  return { key: value, value: true };
}

function canManagePermissionServer(serverId) {
  if (!serverId || serverId === "proxy") {
    return hasPermission(PERMISSIONS.PROXY_PERMISSIONS_MANAGE);
  }
  return hasPermission(PERMISSIONS.SERVER_PERMISSIONS_MANAGE);
}

function populateSelect(select, values, preferred) {
  select.innerHTML = "";
  values.forEach(value => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = value;
    select.append(option);
  });

  if (preferred && values.includes(preferred)) {
    select.value = preferred;
  } else if (values.length) {
    select.value = values[0];
  }
}

function populateDatalist(datalist, values) {
  datalist.innerHTML = "";
  values.forEach(value => {
    const option = document.createElement("option");
    option.value = value;
    datalist.append(option);
  });
}

function checkedValues(container, name) {
  return [...container.querySelectorAll(`input[name="${cssEscape(name)}"]:checked`)]
    .map(input => input.value)
    .filter(Boolean);
}

function setCheckedValues(container, values) {
  const selected = new Set(values);
  container.querySelectorAll("input[type='checkbox']").forEach(input => {
    input.checked = selected.has(input.value);
  });
}

function splitCsv(value) {
  return String(value || "")
    .split(",")
    .map(entry => entry.trim())
    .filter(Boolean);
}

function asArray(value) {
  if (Array.isArray(value)) {
    return value;
  }

  if (Array.isArray(value?.items)) {
    return value.items;
  }

  return [];
}

function setStatus(element, message, isError) {
  element.textContent = message;
  element.style.color = isError ? "var(--danger)" : "var(--muted)";
}

function startConsolePolling() {
  if (state.consoleTimer) {
    clearInterval(state.consoleTimer);
  }

  state.consoleTimer = setInterval(() => {
    if (state.consoleAutoRefresh
      && hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)
      && !document.hidden
      && state.token
      && state.selectedService) {
      loadConsole();
    }
  }, 5000);
}

function handleVisibilityChange() {
  if (state.consoleAutoRefresh
    && hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)
    && !document.hidden
    && state.token
    && state.selectedService) {
    loadConsole();
  }
}

function hasPermission(permission) {
  const permissions = state.currentUser?.permissions || [];
  const allowed = String(permission || "")
    .split(",")
    .map(entry => entry.trim())
    .filter(Boolean);
  return permissions.includes("*") || allowed.some(entry => permissions.includes(entry));
}

function summarizePermissions(permissions) {
  if (permissions.includes("*")) {
    return "Vollzugriff";
  }
  if (!permissions.length) {
    return "Keine Rechte";
  }
  return `${permissions.length} Rechte aktiv`;
}

function shortId(id) {
  const value = String(id || "-");
  return value.length <= 8 ? value : value.slice(0, 8);
}

function shortText(value, maxLength) {
  const text = String(value || "-");
  return text.length <= maxLength ? text : `${text.slice(0, maxLength - 3)}...`;
}

function isResolvedPublicBanId(publicId, internalId) {
  const value = String(publicId || "").trim();
  if (!value) {
    return false;
  }
  if (String(internalId || "").trim().toLowerCase() === value.toLowerCase()) {
    return false;
  }
  return !/^\d+$/.test(value);
}

function formatDateTime(value) {
  if (!value) {
    return "";
  }
  const time = Date.parse(value);
  if (Number.isNaN(time)) {
    return String(value);
  }
  return new Intl.DateTimeFormat("de-DE", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(time));
}

function handleApiError(error, statusElement) {
  if (String(error.message).includes("Nicht autorisiert")) {
    clearSession();
    showLogin("Session ungueltig oder abgelaufen. Bitte neu einloggen.", true);
    return;
  }
  if (statusElement) {
    setStatus(statusElement, error.message, true);
  }
}

async function api(path, options = {}) {
  const {
    method = "GET",
    body = undefined,
    auth = true,
  } = options;

  const headers = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (auth && state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) {
    return null;
  }

  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || `HTTP ${response.status}`);
  }
  return data;
}

function cssEscape(value) {
  if (window.CSS && typeof window.CSS.escape === "function") {
    return window.CSS.escape(String(value || ""));
  }
  return String(value || "").replaceAll('"', '\\"');
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttr(value) {
  return escapeHtml(value);
}
