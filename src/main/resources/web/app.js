const PERMISSIONS = {
  CLOUDNET_VIEW: "cloudnet.view",
  CLOUDNET_MANAGE: "cloudnet.manage",
  CLOUDNET_CONSOLE: "cloudnet.console",
  CLOUDNET_COMMAND: "cloudnet.command",
  USERS_MANAGE: "users.manage",
  SETTINGS_MANAGE: "settings.manage",
};

const CONSOLE_REFRESH_INTERVALS = [5, 10, 15, 30, 60];

const state = {
  token: localStorage.getItem("tccb-session") || "",
  meta: null,
  session: null,
  currentUser: null,
  overview: null,
  tasks: [],
  services: [],
  nodes: [],
  securityUsers: [],
  securityGroups: [],
  settings: null,
  selectedTaskName: localStorage.getItem("tccb-task") || "",
  activeCloudNetSection: localStorage.getItem("tccb-cloudnet-section") || "cloud",
  activeCloudNetView: "overview",
  selectedService: null,
  twoFactorChallengeId: "",
  activePage: localStorage.getItem("tccb-page") || "home",
  consoleTimer: null,
  consoleRequestInFlight: false,
  lastConsoleText: "",
  lastConsoleRefreshAt: 0,
  consoleAutoRefresh: true,
  consoleRefreshIntervalSeconds: consoleRefreshIntervalValue(localStorage.getItem("tccb-console-refresh-interval")),
  cloudConsoleRequestInFlight: false,
  lastCloudConsoleText: "",
  lastCloudConsoleRefreshAt: 0,
  cloudConsoleAutoRefresh: true,
  cloudConsoleRefreshIntervalSeconds: consoleRefreshIntervalValue(localStorage.getItem("tccb-cloud-console-refresh-interval")),
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
  elements.twoFactorForm = document.getElementById("two-factor-form");
  elements.twoFactorCode = document.getElementById("two-factor-code");
  elements.twoFactorHint = document.getElementById("two-factor-hint");
  elements.twoFactorCancel = document.getElementById("two-factor-cancel");
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
  elements.twoFactorMethod = document.getElementById("two-factor-method");
  elements.totpSetupButton = document.getElementById("totp-setup-button");
  elements.totpSetupCard = document.getElementById("totp-setup-card");
  elements.totpQrCode = document.getElementById("totp-qr-code");
  elements.totpSecret = document.getElementById("totp-secret");
  elements.totpUri = document.getElementById("totp-uri");
  elements.pageNav = document.getElementById("page-nav");
  elements.summaryGrid = document.getElementById("summary-grid");

  elements.metricTasks = document.getElementById("metric-tasks");
  elements.metricServices = document.getElementById("metric-services");
  elements.metricRunning = document.getElementById("metric-running");
  elements.metricNodes = document.getElementById("metric-nodes");
  elements.homePanel = document.getElementById("home-panel");
  elements.homeRefresh = document.getElementById("home-refresh");

  elements.environmentSelect = document.getElementById("environment-select");
  elements.runtimeSelect = document.getElementById("runtime-select");
  elements.taskForm = document.getElementById("task-form");
  elements.taskStatus = document.getElementById("task-status");
  elements.taskReset = document.getElementById("task-reset");
  elements.taskSubmit = document.getElementById("task-submit");
  elements.taskFormTitle = document.getElementById("task-form-title");
  elements.taskSelect = document.getElementById("task-select");
  elements.taskSelectedSummary = document.getElementById("task-selected-summary");
  elements.taskEditOpen = document.getElementById("task-edit-open");
  elements.taskCreateOpen = document.getElementById("task-create-open");
  elements.taskSpawnSelected = document.getElementById("task-spawn-selected");
  elements.taskDeleteSelected = document.getElementById("task-delete-selected");
  elements.cloudCommandForm = document.getElementById("cloud-command-form");
  elements.cloudCommandInput = document.getElementById("cloud-command-input");
  elements.cloudCommandOutput = document.getElementById("cloud-command-output");
  elements.cloudConsoleAutoRefresh = document.getElementById("cloud-console-auto-refresh");
  elements.cloudConsoleInterval = document.getElementById("cloud-console-interval");
  elements.cloudConsoleRefresh = document.getElementById("cloud-console-refresh");

  elements.serviceCreateForm = document.getElementById("service-create-form");
  elements.serviceCreateStatus = document.getElementById("service-create-status");
  elements.serviceTaskSelect = document.getElementById("service-task-select");
  elements.serviceTable = document.getElementById("service-table");
  elements.serviceRefresh = document.getElementById("service-refresh");

  elements.consoleServiceSelect = document.getElementById("console-service-select");
  elements.consoleAutoRefresh = document.getElementById("console-auto-refresh");
  elements.consoleInterval = document.getElementById("console-interval");
  elements.consoleRefresh = document.getElementById("console-refresh");
  elements.consoleOutput = document.getElementById("console-output");
  elements.consoleCommandForm = document.getElementById("console-command-form");
  elements.consoleCommandInput = document.getElementById("console-command-input");

  elements.nodeGrid = document.getElementById("node-grid");

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
  elements.settingsForm = document.getElementById("settings-form");
  elements.settingsStatus = document.getElementById("settings-status");
  elements.testMailForm = document.getElementById("test-mail-form");
  elements.testMailStatus = document.getElementById("test-mail-status");
}

function bindEvents() {
  elements.cloudConsoleInterval.value = String(state.cloudConsoleRefreshIntervalSeconds);
  elements.consoleInterval.value = String(state.consoleRefreshIntervalSeconds);

  elements.loginForm.addEventListener("submit", handleLoginSubmit);
  elements.twoFactorForm.addEventListener("submit", handleTwoFactorSubmit);
  elements.twoFactorCancel.addEventListener("click", cancelTwoFactorLogin);
  elements.resetRequestForm.addEventListener("submit", handleResetRequestSubmit);
  elements.resetCompleteForm.addEventListener("submit", handleResetCompleteSubmit);
  elements.logoutButton.addEventListener("click", handleLogout);
  elements.profileForm.addEventListener("submit", handleProfileSubmit);
  elements.passwordForm.addEventListener("submit", handlePasswordSubmit);
  elements.twoFactorMethod.addEventListener("change", updateTwoFactorProfileControls);
  elements.totpSetupButton.addEventListener("click", prepareTotpSetup);
  elements.pageNav.addEventListener("click", handlePageNavClick);
  elements.homeRefresh.addEventListener("click", refreshAll);

  elements.taskForm.addEventListener("submit", handleTaskSubmit);
  elements.taskReset.addEventListener("click", handleTaskResetClick);
  elements.taskSelect.addEventListener("change", handleTaskSelectChange);
  elements.taskEditOpen.addEventListener("click", openSelectedTaskEditor);
  elements.taskCreateOpen.addEventListener("click", openTaskCreate);
  elements.taskSpawnSelected.addEventListener("click", spawnSelectedTask);
  elements.taskDeleteSelected.addEventListener("click", deleteSelectedTask);
  document.querySelectorAll("button[data-cloudnet-section-target]").forEach(button => {
    button.addEventListener("click", () => switchCloudNetSection(button.dataset.cloudnetSectionTarget));
  });
  document.querySelectorAll("button[data-cloudnet-view-target]").forEach(button => {
    button.addEventListener("click", () => switchCloudNetView(button.dataset.cloudnetViewTarget));
  });

  elements.cloudCommandForm.addEventListener("submit", handleCloudCommandSubmit);
  elements.cloudConsoleRefresh.addEventListener("click", loadCloudConsole);
  elements.cloudConsoleAutoRefresh.addEventListener("change", () => {
    state.cloudConsoleAutoRefresh = elements.cloudConsoleAutoRefresh.checked;
    if (state.cloudConsoleAutoRefresh) {
      loadCloudConsole();
    }
  });
  elements.cloudConsoleInterval.addEventListener("change", () => {
    state.cloudConsoleRefreshIntervalSeconds = consoleRefreshIntervalValue(elements.cloudConsoleInterval.value);
    elements.cloudConsoleInterval.value = String(state.cloudConsoleRefreshIntervalSeconds);
    localStorage.setItem("tccb-cloud-console-refresh-interval", String(state.cloudConsoleRefreshIntervalSeconds));
    state.lastCloudConsoleRefreshAt = 0;
    if (state.cloudConsoleAutoRefresh) {
      loadCloudConsole();
    }
  });
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
  elements.consoleInterval.addEventListener("change", () => {
    state.consoleRefreshIntervalSeconds = consoleRefreshIntervalValue(elements.consoleInterval.value);
    elements.consoleInterval.value = String(state.consoleRefreshIntervalSeconds);
    localStorage.setItem("tccb-console-refresh-interval", String(state.consoleRefreshIntervalSeconds));
    state.lastConsoleRefreshAt = 0;
    if (state.consoleAutoRefresh) {
      loadConsole();
    }
  });
  elements.consoleCommandForm.addEventListener("submit", handleConsoleCommandSubmit);

  elements.groupForm.addEventListener("submit", handleGroupSubmit);
  elements.groupReset.addEventListener("click", resetGroupForm);
  elements.groupTable.addEventListener("click", handleGroupTableClick);

  elements.userForm.addEventListener("submit", handleUserSubmit);
  elements.userReset.addEventListener("click", resetUserForm);
  elements.userTable.addEventListener("click", handleUserTableClick);
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
    elements.loginPassword.value = "";
    if (result.twoFactorRequired) {
      showTwoFactorPrompt(result);
      return;
    }
    await completeLogin(result);
  } catch (error) {
    clearSession();
    showLogin(error.message, true);
  }
}

async function handleTwoFactorSubmit(event) {
  event.preventDefault();
  if (!state.twoFactorChallengeId) {
    showLogin("Bitte melde dich erneut an.", true);
    return;
  }

  try {
    const result = await api("/api/auth/2fa/verify", {
      method: "POST",
      auth: false,
      body: {
        challengeId: state.twoFactorChallengeId,
        code: elements.twoFactorCode.value.trim(),
      },
    });
    elements.twoFactorCode.value = "";
    state.twoFactorChallengeId = "";
    await completeLogin(result);
  } catch (error) {
    setStatus(elements.authStatus, error.message, true);
  }
}

async function completeLogin(result) {
  state.token = result.token;
  localStorage.setItem("tccb-session", state.token);
  applySession({ apiToken: false, user: result.user, availablePermissions: result.availablePermissions });
  showPanel();
  await refreshAll();
  startConsolePolling();
  setStatus(elements.authStatus, "Login erfolgreich. Panel ist verbunden.", false);
}

function showTwoFactorPrompt(result) {
  state.twoFactorChallengeId = result.twoFactorChallengeId || "";
  elements.loginForm.classList.add("hidden");
  elements.twoFactorForm.classList.remove("hidden");
  elements.twoFactorCode.value = "";
  elements.twoFactorCode.focus();
  const method = result.twoFactorMethod === "EMAIL" ? "E-Mail" : "Authenticator-App";
  const destination = result.twoFactorDestination ? ` (${result.twoFactorDestination})` : "";
  elements.twoFactorHint.textContent = `${result.message || "Bitte gib deinen 2FA-Code ein."} Methode: ${method}${destination}.`;
  setStatus(elements.authStatus, "2FA-Code erforderlich.", false);
}

function cancelTwoFactorLogin() {
  state.twoFactorChallengeId = "";
  elements.twoFactorCode.value = "";
  elements.twoFactorForm.classList.add("hidden");
  elements.loginForm.classList.remove("hidden");
  setStatus(elements.authStatus, "Bitte mit einem Panel-Benutzer anmelden.", false);
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
        twoFactorMethod: String(form.get("twoFactorMethod") || "NONE").trim(),
        twoFactorTotpCode: String(form.get("twoFactorTotpCode") || "").trim(),
      },
    });
    applySession({ ...state.session, user });
    elements.totpSetupCard.classList.add("hidden");
    elements.totpSecret.value = "";
    elements.totpUri.value = "";
    elements.totpQrCode.innerHTML = "";
    elements.profileForm.elements.twoFactorTotpCode.value = "";
    setStatus(elements.profileStatus, "Profil gespeichert.", false);
  } catch (error) {
    handleApiError(error, elements.profileStatus);
  }
}

async function prepareTotpSetup() {
  try {
    const setup = await api("/api/auth/2fa/setup", { method: "POST" });
    elements.totpSecret.value = setup.secret || "";
    elements.totpUri.value = setup.otpauthUri || "";
    renderTotpQrCode(setup.otpauthUri || "");
    elements.totpSetupCard.classList.remove("hidden");
    elements.profileForm.elements.twoFactorTotpCode.focus();
    setStatus(elements.profileStatus, "Authenticator vorbereitet. Code aus der App eintragen und Profil speichern.", false);
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
    setStatus(elements.profileStatus, "Passwort geändert.", false);
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
  elements.profileForm.elements.twoFactorMethod.value = session.user.twoFactorMethod || "NONE";
  elements.profileForm.elements.twoFactorTotpCode.value = "";
  updateTwoFactorProfileControls();
}

function updateTwoFactorProfileControls() {
  const method = elements.twoFactorMethod.value || "NONE";
  const currentMethod = state.currentUser?.twoFactorMethod || "NONE";
  const needsTotpSetup = method === "TOTP" && currentMethod !== "TOTP";
  elements.totpSetupButton.classList.toggle("hidden", method !== "TOTP");
  elements.totpSetupButton.textContent = currentMethod === "TOTP"
    ? "Authenticator neu einrichten"
    : "Authenticator vorbereiten";
  if (method !== "TOTP") {
    elements.totpSetupCard.classList.add("hidden");
    elements.totpSecret.value = "";
    elements.totpUri.value = "";
    elements.totpQrCode.innerHTML = "";
    elements.profileForm.elements.twoFactorTotpCode.value = "";
  } else if (needsTotpSetup && !elements.totpSecret.value) {
    elements.totpSetupCard.classList.add("hidden");
  }
}

function renderTotpQrCode(uri) {
  if (!uri) {
    elements.totpQrCode.innerHTML = "";
    return;
  }

  try {
    elements.totpQrCode.innerHTML = createQrSvg(uri);
  } catch (error) {
    elements.totpQrCode.innerHTML = `<p class="muted">QR-Code konnte nicht erstellt werden. Bitte nutze den Schlüssel.</p>`;
  }
}

function twoFactorMethodLabel(method) {
  switch (String(method || "NONE").toUpperCase()) {
    case "EMAIL":
      return "E-Mail";
    case "TOTP":
      return "Authenticator-App";
    default:
      return "aus";
  }
}

function showLogin(message, isError) {
  elements.authForm.classList.remove("hidden");
  elements.loginForm.classList.remove("hidden");
  elements.twoFactorForm.classList.add("hidden");
  elements.userCard.classList.add("hidden");
  elements.pageNav.classList.add("hidden");
  elements.summaryGrid.classList.add("hidden");
  document.querySelectorAll(".page-panel").forEach(panel => panel.classList.add("hidden"));
  setStatus(elements.authStatus, message, isError);
}

function showPanel() {
  elements.authForm.classList.add("hidden");
  elements.twoFactorForm.classList.add("hidden");
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
  state.securityUsers = [];
  state.securityGroups = [];
  state.settings = null;
  state.twoFactorChallengeId = "";
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
  if (button.dataset.pageTarget === "cloudnet") {
    switchCloudNetSection(state.activeCloudNetSection || "cloud");
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
    button.classList.toggle("active", button.dataset.pageTarget === target);
  });
  if (target === "cloudnet") {
    updateCloudNetVisibility();
  }
}

function switchCloudNetView(view) {
  const target = view || "overview";
  state.activeCloudNetView = target;
  updateCloudNetVisibility();
  if (target === "overview") {
    setStatus(elements.taskStatus, "", false);
  }
}

function switchCloudNetSection(section) {
  const target = section || "cloud";
  state.activeCloudNetSection = target;
  state.activeCloudNetView = "overview";
  localStorage.setItem("tccb-cloudnet-section", target);
  document.querySelectorAll("button[data-cloudnet-section-target]").forEach(button => {
    button.classList.toggle("active", button.dataset.cloudnetSectionTarget === target);
  });
  updateCloudNetVisibility();
}

function updateCloudNetVisibility() {
  const section = state.activeCloudNetSection || "cloud";
  const view = state.activeCloudNetView || "overview";
  document.querySelectorAll("button[data-cloudnet-section-target]").forEach(button => {
    button.classList.toggle("active", button.dataset.cloudnetSectionTarget === section);
  });
  document.querySelectorAll(".cloudnet-view").forEach(panel => {
    const sectionMatches = !panel.dataset.cloudnetSection || panel.dataset.cloudnetSection === section;
    const viewMatches = !panel.dataset.cloudnetView || panel.dataset.cloudnetView === view;
    panel.classList.toggle("hidden", !sectionMatches || !viewMatches);
  });
  if (state.activePage === "cloudnet" && section === "cloud" && view === "overview") {
    loadCloudConsole();
  }
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

function selectedTask() {
  return state.tasks.find(task => task.name === state.selectedTaskName) || null;
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
  } else if (hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)) {
    state.services = asArray(await api("/api/services"));
    renderServices();
    refreshServiceSelectors();
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

  if (hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    state.settings = await api("/api/settings");
    renderSettings();
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

function fillSelectWithFallback(select, values, selected) {
  select.innerHTML = "";
  const normalized = [...new Set(asArray(values).map(value => String(value || "")).filter(Boolean))];
  normalized.forEach(value => appendOption(select, value, value));
  setSelectValueWithFallback(select, selected);
}

function setSelectValueWithFallback(select, value) {
  const normalized = String(value || "");
  if (normalized && ![...select.options].some(option => option.value === normalized)) {
    appendOption(select, normalized, normalized);
  }
  select.value = normalized;
}

function appendOption(select, value, label) {
  const option = document.createElement("option");
  option.value = value;
  option.textContent = label;
  select.append(option);
}

function renderHome() {
  // Startseite enthält aktuell nur statische Panel-Hinweise.
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
    elements.taskSelect.innerHTML = `<option value="">Keine Tasks vorhanden</option>`;
    state.selectedTaskName = "";
    localStorage.removeItem("tccb-task");
    renderSelectedTaskSummary();
    return;
  }

  if (!state.tasks.some(task => task.name === state.selectedTaskName)) {
    state.selectedTaskName = state.tasks[0].name;
    localStorage.setItem("tccb-task", state.selectedTaskName);
  }

  elements.taskSelect.innerHTML = state.tasks.map(task => `
    <option value="${escapeAttr(task.name)}">${escapeHtml(task.name)} (${escapeHtml(task.environment)})</option>
  `).join("");
  elements.taskSelect.value = state.selectedTaskName;
  renderSelectedTaskSummary();
}

function renderSelectedTaskSummary() {
  const task = selectedTask();
  if (!task) {
    elements.taskSelectedSummary.innerHTML = "Noch kein Task ausgewählt.";
    return;
  }

  elements.taskSelectedSummary.innerHTML = `
    <div>
      <strong>${escapeHtml(task.name)}</strong>
      <span>${escapeHtml(task.environment)} | Runtime ${escapeHtml(task.runtime || "-")} | RAM ${task.maxHeapMemory} MB</span>
    </div>
    <div class="task-summary-grid">
      <span>Min. Services: <strong>${escapeHtml(task.minServiceCount)}</strong></span>
      <span>Start-Port: <strong>${escapeHtml(task.startPort)}</strong></span>
      <span>Gruppen: <strong>${escapeHtml((task.groups || []).join(", ") || "-")}</strong></span>
      <span>Nodes: <strong>${escapeHtml((task.associatedNodes || []).join(", ") || "alle")}</strong></span>
      <span>Maintenance: <strong>${task.maintenance ? "Ja" : "Nein"}</strong></span>
      <span>Statisch: <strong>${task.staticServices ? "Ja" : "Nein"}</strong></span>
      <span>Auto-Löschen: <strong>${task.autoDeleteOnStop ? "Ja" : "Nein"}</strong></span>
      <span>Host: <strong>${escapeHtml(task.hostAddress || "-")}</strong></span>
    </div>
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
    actions.push(`<button data-service-action="delete" data-service-name="${escapeAttr(service.name)}" type="button">Löschen</button>`);
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
        ${group.system ? "" : `<button data-group-action="delete" data-group-name="${escapeAttr(group.name)}" type="button">Löschen</button>`}
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
        </span><br>
        <span class="muted">2FA: ${escapeHtml(twoFactorMethodLabel(user.twoFactorMethod))}</span>
      </td>
      <td>${escapeHtml(user.lastLoginAt || "-")}</td>
      <td>
        <button data-user-action="edit" data-user-name="${escapeAttr(user.username)}" type="button">Bearbeiten</button>
        <button data-user-action="delete" data-user-name="${escapeAttr(user.username)}" type="button">Löschen</button>
      </td>
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
  setFormValue(form, "cloudNetScreenName", settings.cloudNetScreenName || "");
  setFormValue(form, "cloudNetRestBaseUrl", settings.cloudNetRestBaseUrl || "");
  setFormValue(form, "cloudNetRestUsername", settings.cloudNetRestUsername || "");
  setFormValue(form, "cloudNetRestPassword", "");
  if (form.cloudNetRestPassword) {
    form.cloudNetRestPassword.placeholder = settings.cloudNetRestPasswordConfigured ? "gesetzt, leer lassen = behalten" : "nicht gesetzt";
  }
  setFormValue(form, "cloudNetRestThreshold", settings.cloudNetRestThreshold || "INFO");
  setFormValue(form, "panelStorageBackend", settings.panelStorageBackend || "SQL");
  setFormValue(form, "panelSqlJdbcUrl", settings.panelSqlJdbcUrl || "");
  setFormValue(form, "panelSqlUsername", settings.panelSqlUsername || "");
  setFormValue(form, "panelSqlTable", settings.panelSqlTable || "");
  setFormChecked(form, "smtpEnabled", settings.smtpEnabled);
  setFormValue(form, "smtpHost", settings.smtpHost || "");
  setFormValue(form, "smtpPort", settings.smtpPort || 587);
  setFormValue(form, "smtpUsername", settings.smtpUsername || "");
  setFormValue(form, "smtpPassword", "");
  if (form.smtpPassword) {
    form.smtpPassword.placeholder = settings.smtpPasswordConfigured ? "gesetzt, leer lassen = behalten" : "nicht gesetzt";
  }
  setFormValue(form, "smtpFrom", settings.smtpFrom || "");
  setFormChecked(form, "smtpStartTls", settings.smtpStartTls);
  setFormChecked(form, "smtpSsl", settings.smtpSsl);
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

async function loadConsole() {
  if (!hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)) {
    return;
  }

  if (!state.selectedService) {
    state.lastConsoleText = "Kein Service ausgewählt.";
    elements.consoleOutput.textContent = state.lastConsoleText;
    scrollToBottom(elements.consoleOutput);
    return;
  }

  if (state.consoleRequestInFlight) {
    return;
  }

  try {
    state.consoleRequestInFlight = true;
    state.lastConsoleRefreshAt = Date.now();
    const consoleData = await api(`/api/services/${encodeURIComponent(state.selectedService)}/console?limit=120`);
    const nextText = consoleData.lines.length
      ? consoleData.lines.join("\n")
      : "Noch keine Log-Ausgabe für diesen Service.";

    if (nextText !== state.lastConsoleText) {
      state.lastConsoleText = nextText;
      elements.consoleOutput.textContent = nextText;
      scrollToBottom(elements.consoleOutput);
    }
    scrollToBottom(elements.consoleOutput);
  } catch (error) {
    handleApiError(error, elements.authStatus);
    elements.consoleOutput.textContent = error.message;
    scrollToBottom(elements.consoleOutput);
  } finally {
    state.consoleRequestInFlight = false;
  }
}

async function loadCloudConsole() {
  if (!hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)) {
    return;
  }

  if (state.cloudConsoleRequestInFlight) {
    return;
  }

  try {
    state.cloudConsoleRequestInFlight = true;
    state.lastCloudConsoleRefreshAt = Date.now();
    const consoleData = await api("/api/cloudnet/console?limit=220");
    const nextText = asArray(consoleData.lines).length
      ? asArray(consoleData.lines).join("\n")
      : "Noch keine CloudNet-Console-Ausgabe gefunden.";

    if (nextText !== state.lastCloudConsoleText) {
      state.lastCloudConsoleText = nextText;
      elements.cloudCommandOutput.textContent = nextText;
      scrollToBottom(elements.cloudCommandOutput);
    }
    scrollToBottom(elements.cloudCommandOutput);
  } catch (error) {
    handleApiError(error, elements.authStatus);
    elements.cloudCommandOutput.textContent = error.message;
    scrollToBottom(elements.cloudCommandOutput);
  } finally {
    state.cloudConsoleRequestInFlight = false;
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
    nameSplitter: String(form.get("nameSplitter") || "-").trim() || "-",
    startPort: Number(form.get("startPort") || 25565),
    minServiceCount: Number(form.get("minServiceCount") || 0),
    maxHeapMemory: Number(form.get("maxHeapMemory") || 1024),
    hostAddress: String(form.get("hostAddress") || "").trim(),
    javaCommand: String(form.get("javaCommand") || "").trim(),
    groups: splitCsv(form.get("groups")),
    associatedNodes: splitCsv(form.get("associatedNodes")),
    jvmOptions: splitCsv(form.get("jvmOptions")),
    processParameters: splitCsv(form.get("processParameters")),
    deletedFilesAfterStop: splitCsv(form.get("deletedFilesAfterStop")),
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
      state.selectedTaskName = payload.name;
      localStorage.setItem("tccb-task", state.selectedTaskName);
    }
    resetTaskForm();
    await refreshAll();
    switchCloudNetView("overview");
  } catch (error) {
    handleApiError(error, elements.taskStatus);
  }
}

function handleTaskSelectChange() {
  state.selectedTaskName = elements.taskSelect.value || "";
  if (state.selectedTaskName) {
    localStorage.setItem("tccb-task", state.selectedTaskName);
  } else {
    localStorage.removeItem("tccb-task");
  }
  renderSelectedTaskSummary();
}

function handleTaskResetClick(event) {
  event.preventDefault();
  const editingTask = elements.taskForm.dataset.editingTask || "";
  const task = state.tasks.find(entry => entry.name === editingTask);
  if (task) {
    fillTaskForm(task);
  } else {
    resetTaskForm();
  }
}

function openTaskCreate() {
  if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return;
  }
  resetTaskForm();
  switchCloudNetSection("tasks");
  switchCloudNetView("task-form");
}

function openSelectedTaskEditor() {
  if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return;
  }
  const task = selectedTask();
  if (!task) {
    setStatus(elements.taskStatus, "Bitte zuerst einen Task auswählen.", true);
    return;
  }
  fillTaskForm(task);
  switchCloudNetSection("tasks");
  switchCloudNetView("task-form");
}

async function spawnSelectedTask() {
  if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return;
  }
  const task = selectedTask();
  if (!task) {
    return;
  }

  try {
    await api("/api/services", {
      method: "POST",
      body: { taskName: task.name, amount: 1, startImmediately: true },
    });
    await refreshAll();
    setStatus(elements.serviceCreateStatus, `Service für ${task.name} erstellt.`, false);
  } catch (error) {
    handleApiError(error, elements.serviceCreateStatus);
  }
}

async function deleteSelectedTask() {
  if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
    return;
  }
  const task = selectedTask();
  if (!task || !confirm(`Task ${task.name} wirklich löschen?`)) {
    return;
  }

  try {
    await api(`/api/tasks/${encodeURIComponent(task.name)}`, { method: "DELETE" });
    if (state.selectedTaskName === task.name) {
      state.selectedTaskName = "";
      localStorage.removeItem("tccb-task");
    }
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
    elements.consoleOutput.textContent = "Bitte zuerst einen Service auswählen.";
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
    scrollToBottom(elements.consoleOutput);
  }
}

async function handleCloudCommandSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.CLOUDNET_COMMAND)) {
    return;
  }

  const command = elements.cloudCommandInput.value.trim();
  if (!command) {
    return;
  }

  try {
    elements.cloudCommandOutput.textContent = "CloudNet-Befehl wird ausgeführt...";
    scrollToBottom(elements.cloudCommandOutput);
    const result = await api("/api/cloudnet/command", {
      method: "POST",
      body: { command },
    });
    elements.cloudCommandInput.value = "";
    const nextText = asArray(result.output).length
      ? asArray(result.output).join("\n")
      : `Befehl ausgeführt: ${result.command || command}`;
    state.lastCloudConsoleText = nextText;
    elements.cloudCommandOutput.textContent = nextText;
    scrollToBottom(elements.cloudCommandOutput);
    setTimeout(loadCloudConsole, 700);
  } catch (error) {
    handleApiError(error, elements.authStatus);
    elements.cloudCommandOutput.textContent = error.message;
    scrollToBottom(elements.cloudCommandOutput);
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

async function handleSettingsSubmit(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    return;
  }

  const form = new FormData(elements.settingsForm);
  const payload = {
    brandName: String(form.get("brandName") || "").trim(),
    brandLogoUrl: String(form.get("brandLogoUrl") || "").trim(),
    cloudNetScreenName: String(form.get("cloudNetScreenName") || "").trim(),
    cloudNetRestBaseUrl: String(form.get("cloudNetRestBaseUrl") || "").trim(),
    cloudNetRestUsername: String(form.get("cloudNetRestUsername") || "").trim(),
    cloudNetRestThreshold: String(form.get("cloudNetRestThreshold") || "INFO").trim(),
    smtpEnabled: Boolean(form.get("smtpEnabled")),
    smtpHost: String(form.get("smtpHost") || "").trim(),
    smtpPort: Number(form.get("smtpPort") || 587),
    smtpUsername: String(form.get("smtpUsername") || "").trim(),
    smtpFrom: String(form.get("smtpFrom") || "").trim(),
    smtpStartTls: Boolean(form.get("smtpStartTls")),
    smtpSsl: Boolean(form.get("smtpSsl")),
  };

  const cloudNetRestPassword = String(form.get("cloudNetRestPassword") || "");
  const smtpPassword = String(form.get("smtpPassword") || "");
  if (cloudNetRestPassword) {
    payload.cloudNetRestPassword = cloudNetRestPassword;
  }
  if (smtpPassword) {
    payload.smtpPassword = smtpPassword;
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
    setStatus(elements.testMailStatus, "Bitte Empfänger eintragen.", true);
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

async function reloadSessionAndData() {
  const session = await api("/api/auth/session");
  applySession(session);
  applyPermissions();
  await refreshAll();
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
      switchCloudNetSection("service-consoles");
      await loadConsole();
      return;
    }

    if (!hasPermission(PERMISSIONS.CLOUDNET_MANAGE)) {
      return;
    }

    if (action === "delete") {
      if (!confirm(`Service ${serviceName} wirklich löschen?`)) {
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

  if (button.dataset.groupAction === "delete" && confirm(`Gruppe ${groupName} wirklich löschen?`)) {
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

  if (button.dataset.userAction === "delete" && confirm(`Benutzer ${username} wirklich löschen?`)) {
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
  elements.taskFormTitle.textContent = `Task ${task.name} bearbeiten`;

  const form = elements.taskForm.elements;
  form.name.value = task.name;
  form.name.readOnly = true;
  form.environment.value = task.environment;
  form.runtime.value = task.runtime;
  form.nameSplitter.value = task.nameSplitter || "-";
  form.startPort.value = task.startPort;
  form.minServiceCount.value = task.minServiceCount;
  form.maxHeapMemory.value = task.maxHeapMemory;
  form.hostAddress.value = task.hostAddress || "";
  form.javaCommand.value = task.javaCommand || "";
  form.groups.value = (task.groups || []).join(", ");
  form.associatedNodes.value = (task.associatedNodes || []).join(", ");
  form.jvmOptions.value = (task.jvmOptions || []).join(", ");
  form.processParameters.value = (task.processParameters || []).join(", ");
  form.deletedFilesAfterStop.value = (task.deletedFilesAfterStop || []).join(", ");
  form.maintenance.checked = Boolean(task.maintenance);
  form.staticServices.checked = Boolean(task.staticServices);
  form.autoDeleteOnStop.checked = Boolean(task.autoDeleteOnStop);
  setStatus(elements.taskStatus, "", false);
}

function resetTaskForm() {
  elements.taskForm.reset();
  elements.taskForm.dataset.editingTask = "";
  elements.taskSubmit.textContent = "Task speichern";
  elements.taskFormTitle.textContent = "Neuen Task anlegen";
  elements.taskForm.elements.name.readOnly = false;

  if (state.meta) {
    elements.taskForm.elements.environment.value = state.meta.environments[0] || "";
    elements.taskForm.elements.runtime.value = state.meta.runtimes[0] || "";
  }

  elements.taskForm.elements.nameSplitter.value = "-";
  elements.taskForm.elements.startPort.value = 25565;
  elements.taskForm.elements.minServiceCount.value = 1;
  elements.taskForm.elements.maxHeapMemory.value = 1024;
  elements.taskForm.elements.hostAddress.value = "";
  elements.taskForm.elements.javaCommand.value = "";
  elements.taskForm.elements.deletedFilesAfterStop.value = "";
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

function populateSelect(select, values, preferred) {
  if (!select) {
    return;
  }
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
  if (!datalist) {
    return;
  }
  datalist.innerHTML = "";
  values.forEach(value => {
    const option = document.createElement("option");
    option.value = value;
    datalist.append(option);
  });
}

function checkedValues(container, name) {
  if (!container) {
    return [];
  }
  return [...container.querySelectorAll(`input[name="${cssEscape(name)}"]:checked`)]
    .map(input => input.value)
    .filter(Boolean);
}

function setCheckedValues(container, values) {
  if (!container) {
    return;
  }
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

function splitLinesOrCsv(value) {
  return String(value || "")
    .split(/[\n,]+/)
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

function scrollToBottom(element) {
  if (!element) {
    return;
  }
  const scroll = () => {
    element.scrollTop = element.scrollHeight;
  };
  requestAnimationFrame(() => {
    scroll();
    requestAnimationFrame(scroll);
    setTimeout(scroll, 60);
  });
}

function consoleRefreshIntervalValue(value) {
  const interval = Number(value);
  return CONSOLE_REFRESH_INTERVALS.includes(interval) ? interval : 5;
}

function consoleRefreshDue(lastRefreshAt, intervalSeconds) {
  return !lastRefreshAt || Date.now() - lastRefreshAt >= intervalSeconds * 1000;
}

function createQrSvg(text) {
  const modules = createQrMatrix(text);
  const border = 4;
  const viewSize = modules.length + border * 2;
  const dark = "#07131d";
  let path = "";
  modules.forEach((row, y) => {
    row.forEach((enabled, x) => {
      if (enabled) {
        path += `M${x + border},${y + border}h1v1h-1z`;
      }
    });
  });
  return `
    <svg viewBox="0 0 ${viewSize} ${viewSize}" role="img" aria-label="Authenticator QR-Code" xmlns="http://www.w3.org/2000/svg">
      <rect width="${viewSize}" height="${viewSize}" fill="#fff"></rect>
      <path d="${path}" fill="${dark}"></path>
    </svg>
  `;
}

function createQrMatrix(text) {
  const version = 10;
  const size = version * 4 + 17;
  const mask = 0;
  const dataCodewords = 274;
  const eccCodewordsPerBlock = 18;
  const blockDataLengths = [68, 68, 69, 69];
  const modules = Array.from({ length: size }, () => Array(size).fill(false));
  const reserved = Array.from({ length: size }, () => Array(size).fill(false));

  function setFunction(x, y, dark) {
    if (x < 0 || y < 0 || x >= size || y >= size) {
      return;
    }
    modules[y][x] = Boolean(dark);
    reserved[y][x] = true;
  }

  function drawFinder(cx, cy) {
    for (let dy = -4; dy <= 4; dy++) {
      for (let dx = -4; dx <= 4; dx++) {
        const distance = Math.max(Math.abs(dx), Math.abs(dy));
        setFunction(cx + dx, cy + dy, distance !== 2 && distance !== 4);
      }
    }
  }

  function drawAlignment(cx, cy) {
    for (let dy = -2; dy <= 2; dy++) {
      for (let dx = -2; dx <= 2; dx++) {
        setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
      }
    }
  }

  drawFinder(3, 3);
  drawFinder(size - 4, 3);
  drawFinder(3, size - 4);
  [6, 28, 50].forEach(x => {
    [6, 28, 50].forEach(y => {
      const overlapsFinder = (x === 6 && y === 6) || (x === 6 && y === 50) || (x === 50 && y === 6);
      if (!overlapsFinder) {
        drawAlignment(x, y);
      }
    });
  });
  for (let i = 8; i < size - 8; i++) {
    setFunction(i, 6, i % 2 === 0);
    setFunction(6, i, i % 2 === 0);
  }
  setFunction(8, size - 8, true);
  drawFormatBits(mask);
  drawVersionBits();

  const codewords = addQrErrorCorrection(
    createQrDataCodewords(text, dataCodewords, version),
    blockDataLengths,
    eccCodewordsPerBlock);
  let bitIndex = 0;
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) {
      right = 5;
    }
    for (let vert = 0; vert < size; vert++) {
      for (let j = 0; j < 2; j++) {
        const x = right - j;
        const y = ((right + 1) & 2) === 0 ? size - 1 - vert : vert;
        if (reserved[y][x]) {
          continue;
        }
        const bit = bitIndex < codewords.length * 8
          && ((codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) !== 0;
        modules[y][x] = bit;
        bitIndex++;
      }
    }
  }

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      if (!reserved[y][x] && (x + y) % 2 === 0) {
        modules[y][x] = !modules[y][x];
      }
    }
  }
  drawFormatBits(mask);
  return modules;

  function drawFormatBits(maskPattern) {
    const data = (1 << 3) | maskPattern;
    let remainder = data;
    for (let i = 0; i < 10; i++) {
      remainder = (remainder << 1) ^ (((remainder >>> 9) & 1) ? 0x537 : 0);
    }
    const bits = ((data << 10) | remainder) ^ 0x5412;
    for (let i = 0; i <= 5; i++) {
      setFunction(8, i, getQrBit(bits, i));
    }
    setFunction(8, 7, getQrBit(bits, 6));
    setFunction(8, 8, getQrBit(bits, 7));
    setFunction(7, 8, getQrBit(bits, 8));
    for (let i = 9; i < 15; i++) {
      setFunction(14 - i, 8, getQrBit(bits, i));
    }
    for (let i = 0; i < 8; i++) {
      setFunction(size - 1 - i, 8, getQrBit(bits, i));
    }
    for (let i = 8; i < 15; i++) {
      setFunction(8, size - 15 + i, getQrBit(bits, i));
    }
    setFunction(8, size - 8, true);
  }

  function drawVersionBits() {
    let remainder = version;
    for (let i = 0; i < 12; i++) {
      remainder = (remainder << 1) ^ (((remainder >>> 11) & 1) ? 0x1f25 : 0);
    }
    const bits = (version << 12) | remainder;
    for (let i = 0; i < 18; i++) {
      const bit = getQrBit(bits, i);
      const a = size - 11 + (i % 3);
      const b = Math.floor(i / 3);
      setFunction(a, b, bit);
      setFunction(b, a, bit);
    }
  }
}

function createQrDataCodewords(text, dataCodewords, version) {
  const bytes = Array.from(new TextEncoder().encode(text));
  const lengthBits = version < 10 ? 8 : 16;
  const capacityBits = dataCodewords * 8;
  if (bytes.length * 8 + 4 + lengthBits > capacityBits) {
    throw new Error("QR payload is too long");
  }

  const bits = [];
  appendQrBits(bits, 0x4, 4);
  appendQrBits(bits, bytes.length, lengthBits);
  bytes.forEach(byte => appendQrBits(bits, byte, 8));
  appendQrBits(bits, 0, Math.min(4, capacityBits - bits.length));
  while (bits.length % 8 !== 0) {
    bits.push(0);
  }

  const result = [];
  for (let i = 0; i < bits.length; i += 8) {
    let value = 0;
    for (let j = 0; j < 8; j++) {
      value = (value << 1) | bits[i + j];
    }
    result.push(value);
  }
  for (let pad = 0; result.length < dataCodewords; pad ^= 1) {
    result.push(pad ? 0x11 : 0xec);
  }
  return result;
}

function addQrErrorCorrection(data, blockDataLengths, eccCodewordsPerBlock) {
  const blocks = [];
  let offset = 0;
  blockDataLengths.forEach(length => {
    const block = data.slice(offset, offset + length);
    offset += length;
    blocks.push({
      data: block,
      ecc: createQrReedSolomonRemainder(block, eccCodewordsPerBlock),
    });
  });

  const result = [];
  const maxDataLength = Math.max(...blockDataLengths);
  for (let i = 0; i < maxDataLength; i++) {
    blocks.forEach(block => {
      if (i < block.data.length) {
        result.push(block.data[i]);
      }
    });
  }
  for (let i = 0; i < eccCodewordsPerBlock; i++) {
    blocks.forEach(block => result.push(block.ecc[i]));
  }
  return result;
}

function createQrReedSolomonRemainder(data, degree) {
  const generator = createQrReedSolomonGenerator(degree);
  const result = Array(degree).fill(0);
  data.forEach(byte => {
    const factor = byte ^ result.shift();
    result.push(0);
    for (let i = 0; i < degree; i++) {
      result[i] ^= qrGfMultiply(generator[i], factor);
    }
  });
  return result;
}

function createQrReedSolomonGenerator(degree) {
  let result = [1];
  for (let i = 0; i < degree; i++) {
    const next = Array(result.length + 1).fill(0);
    result.forEach((coefficient, index) => {
      next[index] ^= coefficient;
      next[index + 1] ^= qrGfMultiply(coefficient, QR_GF_EXP[i]);
    });
    result = next;
  }
  return result.slice(1);
}

function qrGfMultiply(left, right) {
  return left === 0 || right === 0 ? 0 : QR_GF_EXP[QR_GF_LOG[left] + QR_GF_LOG[right]];
}

function appendQrBits(bits, value, length) {
  for (let i = length - 1; i >= 0; i--) {
    bits.push((value >>> i) & 1);
  }
}

function getQrBit(value, index) {
  return ((value >>> index) & 1) !== 0;
}

const QR_GF_EXP = (() => {
  const result = Array(512).fill(0);
  let value = 1;
  for (let i = 0; i < 255; i++) {
    result[i] = value;
    value <<= 1;
    if (value & 0x100) {
      value ^= 0x11d;
    }
  }
  for (let i = 255; i < 512; i++) {
    result[i] = result[i - 255];
  }
  return result;
})();

const QR_GF_LOG = (() => {
  const result = Array(256).fill(0);
  for (let i = 0; i < 255; i++) {
    result[QR_GF_EXP[i]] = i;
  }
  return result;
})();

function startConsolePolling() {
  if (state.consoleTimer) {
    clearInterval(state.consoleTimer);
  }

  state.consoleTimer = setInterval(() => {
    if (state.consoleAutoRefresh
      && hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)
      && !document.hidden
      && state.token
      && state.selectedService
      && consoleRefreshDue(state.lastConsoleRefreshAt, state.consoleRefreshIntervalSeconds)) {
      loadConsole();
    }
    if (state.cloudConsoleAutoRefresh
      && hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)
      && !document.hidden
      && state.token
      && state.activePage === "cloudnet"
      && state.activeCloudNetSection === "cloud"
      && consoleRefreshDue(state.lastCloudConsoleRefreshAt, state.cloudConsoleRefreshIntervalSeconds)) {
      loadCloudConsole();
    }
  }, 1000);
}

function handleVisibilityChange() {
  if (state.consoleAutoRefresh
    && hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)
    && !document.hidden
    && state.token
    && state.selectedService) {
    loadConsole();
  }
  if (state.cloudConsoleAutoRefresh
    && hasPermission(PERMISSIONS.CLOUDNET_CONSOLE)
    && !document.hidden
    && state.token
    && state.activePage === "cloudnet"
    && state.activeCloudNetSection === "cloud") {
    loadCloudConsole();
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

function currentActor() {
  return state.currentUser?.displayName || state.currentUser?.username || "Panel";
}

function currentMinecraftName() {
  return state.currentUser?.minecraftName || "";
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
    showLogin("Session ungültig oder abgelaufen. Bitte neu einloggen.", true);
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

function isHttpUrl(value) {
  const normalized = String(value || "").trim().toLowerCase();
  return normalized.startsWith("https://") || normalized.startsWith("http://");
}
