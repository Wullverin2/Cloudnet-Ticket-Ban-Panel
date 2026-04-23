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
  QUEST_EDITOR_VIEW: "quests.editor.view",
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
  questEditorConfig: null,
  questEditorServers: [],
  questEditorStatus: null,
  questEditorSchema: {},
  questEditorCategories: [],
  questEditorQuests: [],
  selectedQuestServerId: localStorage.getItem("tccb-quest-server") || "",
  selectedQuestId: localStorage.getItem("tccb-quest-id") || "",
  settings: null,
  selectedPermissionServer: localStorage.getItem("tccb-lp-server") || "proxy",
  selectedPermissionSubject: localStorage.getItem("tccb-lp-subject") || "",
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
  oneDrivePollTimer: null,
  oneDriveFolders: [],
  oneDriveFoldersLoading: false,
  oneDriveFoldersLoaded: false,
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
  elements.homeOpenTickets = document.getElementById("home-open-tickets");
  elements.homeClosedTickets = document.getElementById("home-closed-tickets");
  elements.homeOpenAppeals = document.getElementById("home-open-appeals");
  elements.homeArchivedAppeals = document.getElementById("home-archived-appeals");
  elements.homeActiveLiteBans = document.getElementById("home-active-litebans");

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

  elements.ticketForm = document.getElementById("ticket-form");
  elements.ticketStatus = document.getElementById("ticket-status");
  elements.ticketRefresh = document.getElementById("ticket-refresh");
  elements.ticketTable = document.getElementById("ticket-table");
  elements.ticketArchiveTable = document.getElementById("ticket-archive-table");
  elements.ticketAuditTable = document.getElementById("ticket-audit-table");
  elements.ticketCategorySelect = document.getElementById("ticket-category-select");
  elements.ticketPrioritySelect = document.getElementById("ticket-priority-select");
  elements.ticketCategorySettingsList = document.getElementById("ticket-category-settings-list");
  elements.serviceNameList = document.getElementById("service-name-list");

  elements.banForm = document.getElementById("ban-form");
  elements.banStatus = document.getElementById("ban-status");
  elements.banTable = document.getElementById("ban-table");
  elements.liteBanTable = document.getElementById("liteban-table");
  elements.banAppealRefresh = document.getElementById("ban-appeal-refresh");
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
  elements.questRefresh = document.getElementById("quest-refresh");
  elements.questServerSelect = document.getElementById("quest-server-select");
  elements.questConnectionState = document.getElementById("quest-connection-state");
  elements.questApiBaseUrl = document.getElementById("quest-api-base-url");
  elements.questApiTokenState = document.getElementById("quest-api-token-state");
  elements.questCount = document.getElementById("quest-count");
  elements.questCategoryCount = document.getElementById("quest-category-count");
  elements.questSearch = document.getElementById("quest-search");
  elements.questCategoryFilter = document.getElementById("quest-category-filter");
  elements.questList = document.getElementById("quest-list");
  elements.questDetailTitle = document.getElementById("quest-detail-title");
  elements.questDetailSubtitle = document.getElementById("quest-detail-subtitle");
  elements.questLoadRaw = document.getElementById("quest-load-raw");
  elements.questSave = document.getElementById("quest-save");
  elements.questFieldId = document.getElementById("quest-field-id");
  elements.questFieldName = document.getElementById("quest-field-name");
  elements.questFieldType = document.getElementById("quest-field-type");
  elements.questFieldCategory = document.getElementById("quest-field-category");
  elements.questFieldIcon = document.getElementById("quest-field-icon");
  elements.questFieldBedrockName = document.getElementById("quest-field-bedrock-name");
  elements.questFieldBedrockIcon = document.getElementById("quest-field-bedrock-icon");
  elements.questFieldResetProfile = document.getElementById("quest-field-reset-profile");
  elements.questTaskTable = document.getElementById("quest-task-table");
  elements.questRewardTable = document.getElementById("quest-reward-table");
  elements.questRawYaml = document.getElementById("quest-raw-yaml");
  elements.settingsForm = document.getElementById("settings-form");
  elements.settingsStatus = document.getElementById("settings-status");
  elements.questServerSettingsList = document.getElementById("quest-server-settings-list");
  elements.questServerAdd = document.getElementById("quest-server-add");
  elements.oneDriveConnect = document.getElementById("onedrive-connect");
  elements.oneDriveDisconnect = document.getElementById("onedrive-disconnect");
  elements.oneDriveFoldersRefresh = document.getElementById("onedrive-folders-refresh");
  elements.oneDriveLoginLink = document.getElementById("onedrive-login-link");
  elements.oneDriveStatus = document.getElementById("onedrive-status");
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
  elements.homePanel.addEventListener("click", handleHomePanelClick);
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

  elements.ticketForm.addEventListener("submit", handleTicketSubmit);
  elements.ticketRefresh.addEventListener("click", refreshTicketsOnly);
  elements.ticketTable.addEventListener("click", handleTicketTableClick);
  document.querySelectorAll("button[data-ticket-tab]").forEach(button => {
    button.addEventListener("click", () => switchTicketTab(button.dataset.ticketTab));
  });

  elements.banForm.addEventListener("submit", handleBanSubmit);
  elements.banTable.addEventListener("click", handleBanTableClick);
  elements.liteBanTable.addEventListener("click", handleLiteBanTableClick);
  elements.banAppealRefresh.addEventListener("click", refreshBanAppealsOnly);
  elements.banAppealTable.addEventListener("click", handleBanAppealTableClick);
  elements.banAppealArchiveTable.addEventListener("click", handleBanAppealTableClick);
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
  elements.questRefresh.addEventListener("click", refreshQuestEditor);
  elements.questServerSelect.addEventListener("change", () => {
    state.selectedQuestServerId = elements.questServerSelect.value || "";
    localStorage.setItem("tccb-quest-server", state.selectedQuestServerId);
    clearQuestEditorDetail();
    loadQuestEditorOverview(true);
  });
  elements.questSearch.addEventListener("input", renderQuestEditorList);
  elements.questCategoryFilter.addEventListener("change", renderQuestEditorList);
  elements.questLoadRaw.addEventListener("click", loadQuestRawYaml);
  elements.settingsForm.addEventListener("submit", handleSettingsSubmit);
  elements.questServerAdd.addEventListener("click", addQuestServerSettingsRow);
  elements.questServerSettingsList.addEventListener("click", handleQuestServerSettingsClick);
  elements.oneDriveConnect.addEventListener("click", handleOneDriveConnect);
  elements.oneDriveDisconnect.addEventListener("click", handleOneDriveDisconnect);
  elements.oneDriveFoldersRefresh.addEventListener("click", () => loadOneDriveFolders(true));
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
  state.questEditorConfig = null;
  state.questEditorServers = [];
  state.questEditorStatus = null;
  state.questEditorSchema = {};
  state.questEditorCategories = [];
  state.questEditorQuests = [];
  state.selectedQuestId = "";
  state.settings = null;
  state.selectedPermissionSubject = "";
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
  if (button.dataset.pageTarget === "quest-editor") {
    refreshQuestEditor();
  }
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

  if (hasPermission(PERMISSIONS.QUEST_EDITOR_VIEW)) {
    await loadQuestEditorOverview(false);
  }

  if (hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    state.settings = await api("/api/settings");
    syncTicketCategoriesFromSettings();
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

async function refreshTicketsOnly() {
  if (!hasPermission(PERMISSIONS.TICKETS_VIEW)) {
    return;
  }

  try {
    const [tickets, ticketAudit] = await Promise.all([
      api("/api/tickets"),
      api("/api/tickets/audit"),
    ]);
    state.tickets = asArray(tickets);
    state.ticketAudit = asArray(ticketAudit);
    renderTickets();
    renderTicketArchive();
    renderTicketAudit();
    renderHome();
    setStatus(elements.ticketStatus, "Tickets wurden aktualisiert.", false);
  } catch (error) {
    handleApiError(error, elements.ticketStatus);
  }
}

async function refreshBanAppealsOnly() {
  if (!hasPermission(PERMISSIONS.BANS_VIEW)) {
    return;
  }

  try {
    state.banAppeals = asArray(await api("/api/ban-appeals"));
    renderBanAppeals();
    renderBanAppealArchive();
    renderHome();
    setStatus(elements.banStatus, "Entbannungsanträge wurden aktualisiert.", false);
  } catch (error) {
    handleApiError(error, elements.banStatus);
  }
}

async function refreshQuestEditor() {
  await loadQuestEditorOverview(true);
}

async function loadQuestEditorOverview(showStatus) {
  if (!hasPermission(PERMISSIONS.QUEST_EDITOR_VIEW)) {
    return;
  }

  try {
    if (showStatus) {
      setStatus(elements.questConnectionState, "Verbinde mit CraftplayQuests ...", false);
    }

    state.questEditorConfig = await api("/api/quest-editor/config");
    state.questEditorServers = responseArray(state.questEditorConfig.servers, "servers");
    renderQuestEditorServerSelect();
    const selectedServer = ensureQuestEditorServerSelection();
    renderQuestEditorConfig();
    if (!selectedServer?.enabled) {
      state.questEditorStatus = state.questEditorConfig;
      state.questEditorSchema = {};
      state.questEditorCategories = [];
      state.questEditorQuests = [];
      renderQuestEditorOverview();
      clearQuestEditorDetail();
      setStatus(elements.questConnectionState, "Bitte einen aktiven Quest-Server in den Einstellungen hinterlegen.", true);
      return;
    }

    const [status, schema, categories, quests] = await Promise.all([
      api(questEditorApiPath("status")),
      api(questEditorApiPath("schema")),
      api(questEditorApiPath("categories")),
      api(questEditorApiPath("quests")),
    ]);

    state.questEditorStatus = status;
    if (status?.serverName) {
      state.questEditorServers = state.questEditorServers.map(server => (
        server.id === state.selectedQuestServerId
          ? { ...server, pluginServerName: status.serverName }
          : server
      ));
      renderQuestEditorServerSelect();
    }
    state.questEditorSchema = schema || {};
    state.questEditorCategories = responseArray(categories, "categories");
    state.questEditorQuests = responseArray(quests, "quests");

    renderQuestEditorOverview();
    if (state.selectedQuestId && state.questEditorQuests.some(quest => quest.id === state.selectedQuestId)) {
      await selectQuestEditorQuest(state.selectedQuestId);
    } else {
      clearQuestEditorDetail();
    }
    setStatus(elements.questConnectionState, questEditorStatusText(), false);
  } catch (error) {
    handleApiError(error, elements.questConnectionState);
    renderQuestEditorOverview();
  }
}

function renderQuestEditorConfig() {
  const server = selectedQuestEditorServer();
  elements.questApiBaseUrl.textContent = server?.baseUrl || "-";
  elements.questApiTokenState.textContent = server?.tokenConfigured ? "Token gesetzt" : "Kein Token gesetzt";
}

function renderQuestEditorServerSelect() {
  const selected = state.selectedQuestServerId || elements.questServerSelect.value;
  elements.questServerSelect.innerHTML = "";
  if (!state.questEditorServers.length) {
    appendOption(elements.questServerSelect, "", "Kein Quest-Server");
    return;
  }
  state.questEditorServers.forEach(server => {
    appendOption(elements.questServerSelect, server.id || "", questEditorServerLabel(server));
  });
  const selectedServer = state.questEditorServers.find(server => server.id === selected)
    || state.questEditorServers.find(server => server.enabled)
    || state.questEditorServers[0];
  elements.questServerSelect.value = selectedServer?.id || "";
  state.selectedQuestServerId = elements.questServerSelect.value;
  if (state.selectedQuestServerId) {
    localStorage.setItem("tccb-quest-server", state.selectedQuestServerId);
  }
}

function renderQuestEditorOverview() {
  elements.questCount.textContent = String(state.questEditorQuests.length);
  elements.questCategoryCount.textContent = String(state.questEditorCategories.length);
  renderQuestEditorCategoryFilter();
  renderQuestEditorFieldOptions();
  renderQuestEditorList();
}

function renderQuestEditorCategoryFilter() {
  const selected = elements.questCategoryFilter.value;
  elements.questCategoryFilter.innerHTML = "";
  appendOption(elements.questCategoryFilter, "", "Alle Kategorien");
  state.questEditorCategories.forEach(category => {
    const id = questCategoryId(category);
    appendOption(elements.questCategoryFilter, id, questCategoryName(category));
  });
  elements.questCategoryFilter.value = selected;
}

function renderQuestEditorFieldOptions() {
  fillSelectWithFallback(elements.questFieldType, state.questEditorSchema.questTypes || ["STANDARD", "DAILY", "WEEKLY", "COMMUNITY"], elements.questFieldType.value);
  fillSelectWithFallback(
    elements.questFieldCategory,
    state.questEditorCategories.map(category => questCategoryId(category)),
    elements.questFieldCategory.value);
  fillSelectWithFallback(elements.questFieldResetProfile, state.questEditorSchema.resetProfiles || ["NONE", "DAILY", "WEEKLY"], elements.questFieldResetProfile.value);
}

function renderQuestEditorList() {
  const query = String(elements.questSearch.value || "").trim().toLowerCase();
  const categoryFilter = elements.questCategoryFilter.value;
  const quests = state.questEditorQuests
    .filter(quest => {
      const category = questCategoryValue(quest);
      const text = [
        quest.id,
        quest.name,
        quest.displayName,
        quest.bedrockName,
        category,
        quest.type,
      ].join(" ").toLowerCase();
      return (!categoryFilter || category === categoryFilter)
        && (!query || text.includes(query));
    });

  elements.questList.innerHTML = "";
  if (!selectedQuestEditorServer()?.enabled) {
    elements.questList.innerHTML = `<div class="empty-state">Aktiviere mindestens einen Quest-Server in den Einstellungen.</div>`;
    return;
  }
  if (!quests.length) {
    elements.questList.innerHTML = `<div class="empty-state">Keine Quests gefunden.</div>`;
    return;
  }

  quests.forEach(quest => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `quest-card-button${quest.id === state.selectedQuestId ? " active" : ""}`;
    button.innerHTML = `
      <strong>${escapeHtml(quest.name || quest.displayName || quest.id)}</strong>
      <span>${escapeHtml(quest.id || "-")} · ${escapeHtml(questCategoryValue(quest) || "ohne Kategorie")}</span>
    `;
    button.addEventListener("click", () => selectQuestEditorQuest(quest.id));
    elements.questList.append(button);
  });
}

async function selectQuestEditorQuest(id) {
  if (!id) {
    return;
  }
  state.selectedQuestId = id;
  localStorage.setItem("tccb-quest-id", id);
  renderQuestEditorList();
  setQuestEditorDetailLoading(id);

  try {
    const quest = await api(questEditorApiPath(`quests/${encodeURIComponent(id)}`));
    renderQuestEditorDetail(quest);
  } catch (error) {
    handleApiError(error, elements.questConnectionState);
    elements.questDetailTitle.textContent = "Quest konnte nicht geladen werden";
    elements.questDetailSubtitle.textContent = error.message;
  }
}

function renderQuestEditorDetail(quest) {
  const bedrock = quest.bedrock || {};
  elements.questDetailTitle.textContent = quest.name || quest.displayName || quest.id || "Quest";
  elements.questDetailSubtitle.textContent = `${quest.id || "-"} · ${questCategoryValue(quest) || "ohne Kategorie"}`;
  setFormValue(elements, "questFieldId", quest.id || "");
  setFormValue(elements, "questFieldName", quest.name || quest.displayName || "");
  setSelectValueWithFallback(elements.questFieldType, quest.type || "STANDARD");
  setSelectValueWithFallback(elements.questFieldCategory, questCategoryValue(quest));
  setFormValue(elements, "questFieldIcon", quest.icon || quest.item || quest.material || "");
  setFormValue(elements, "questFieldBedrockName", bedrock.name || quest.bedrockName || "");
  setFormValue(elements, "questFieldBedrockIcon", bedrock.icon || quest.bedrockIcon || "");
  setSelectValueWithFallback(elements.questFieldResetProfile, quest.resetProfile || quest.reset || "NONE");
  renderQuestEditorTasks(responseArray(quest.tasks, "tasks"));
  renderQuestEditorRewards(responseArray(quest.rewards, "rewards"));
  elements.questRawYaml.value = "";
  elements.questLoadRaw.disabled = false;
  elements.questSave.disabled = true;
}

function renderQuestEditorTasks(tasks) {
  elements.questTaskTable.innerHTML = "";
  if (!tasks.length) {
    elements.questTaskTable.innerHTML = `<tr><td colspan="5">Keine Aufgaben hinterlegt.</td></tr>`;
    return;
  }
  elements.questTaskTable.innerHTML = tasks.map(task => `
    <tr>
      <td>${escapeHtml(task.id || "-")}</td>
      <td>${escapeHtml(task.type || "-")}</td>
      <td>${escapeHtml(task.target || task.item || task.entity || "-")}</td>
      <td>${escapeHtml(task.amount ?? task.required ?? "-")}</td>
      <td>${escapeHtml(task.text || task.displayText || task.description || "-")}</td>
    </tr>
  `).join("");
}

function renderQuestEditorRewards(rewards) {
  elements.questRewardTable.innerHTML = "";
  if (!rewards.length) {
    elements.questRewardTable.innerHTML = `<tr><td colspan="3">Keine Belohnungen hinterlegt.</td></tr>`;
    return;
  }
  elements.questRewardTable.innerHTML = rewards.map(reward => `
    <tr>
      <td>${escapeHtml(reward.type || "-")}</td>
      <td>${escapeHtml(reward.value || reward.command || reward.item || reward.amount || "-")}</td>
      <td>${escapeHtml(reward.text || reward.displayText || reward.description || "-")}</td>
    </tr>
  `).join("");
}

async function loadQuestRawYaml() {
  if (!state.selectedQuestId) {
    return;
  }

  elements.questLoadRaw.disabled = true;
  elements.questRawYaml.value = "Lade YAML ...";
  try {
    const raw = await api(questEditorApiPath(`raw/quests/${encodeURIComponent(state.selectedQuestId)}`));
    elements.questRawYaml.value = raw.content || raw.yaml || JSON.stringify(raw, null, 2);
  } catch (error) {
    elements.questRawYaml.value = `YAML konnte nicht geladen werden: ${error.message}`;
    handleApiError(error, elements.questConnectionState);
  } finally {
    elements.questLoadRaw.disabled = false;
  }
}

function setQuestEditorDetailLoading(id) {
  elements.questDetailTitle.textContent = "Lade Quest ...";
  elements.questDetailSubtitle.textContent = id;
  elements.questTaskTable.innerHTML = "";
  elements.questRewardTable.innerHTML = "";
  elements.questRawYaml.value = "";
  elements.questLoadRaw.disabled = true;
}

function clearQuestEditorDetail() {
  state.selectedQuestId = "";
  localStorage.removeItem("tccb-quest-id");
  elements.questDetailTitle.textContent = "Keine Quest ausgewählt";
  elements.questDetailSubtitle.textContent = "Wähle links eine Quest aus.";
  setFormValue(elements, "questFieldId", "");
  setFormValue(elements, "questFieldName", "");
  setFormValue(elements, "questFieldIcon", "");
  setFormValue(elements, "questFieldBedrockName", "");
  setFormValue(elements, "questFieldBedrockIcon", "");
  elements.questRawYaml.value = "";
  renderQuestEditorTasks([]);
  renderQuestEditorRewards([]);
  elements.questLoadRaw.disabled = true;
  elements.questSave.disabled = true;
}

function questEditorStatusText() {
  const status = state.questEditorStatus || {};
  const questCount = status.questCount ?? status.quests ?? state.questEditorQuests.length;
  const categoryCount = status.categoryCount ?? status.categories ?? state.questEditorCategories.length;
  const serverName = status.serverName || selectedQuestEditorServer()?.name || "Quest-Server";
  return `${serverName} verbunden · ${questCount} Quests · ${categoryCount} Kategorien`;
}

function questCategoryId(category) {
  return String(category?.id || category?.key || category?.name || "");
}

function ensureQuestEditorServerSelection() {
  const selected = state.questEditorServers.find(server => server.id === state.selectedQuestServerId)
    || state.questEditorServers.find(server => server.enabled)
    || state.questEditorServers[0]
    || null;
  state.selectedQuestServerId = selected?.id || "";
  if (state.selectedQuestServerId) {
    localStorage.setItem("tccb-quest-server", state.selectedQuestServerId);
  }
  if (elements.questServerSelect) {
    elements.questServerSelect.value = state.selectedQuestServerId;
  }
  return selected;
}

function selectedQuestEditorServer() {
  return state.questEditorServers.find(server => server.id === state.selectedQuestServerId)
    || state.questEditorServers.find(server => server.enabled)
    || state.questEditorServers[0]
    || null;
}

function questEditorApiPath(path) {
  const server = ensureQuestEditorServerSelection();
  const normalizedPath = String(path || "").replace(/^\/+/, "");
  return `/api/quest-editor/${encodeURIComponent(server?.id || "")}/${normalizedPath}`;
}

function questEditorServerLabel(server) {
  const configuredName = server?.name || server?.id || "Quest-Server";
  const pluginName = String(server?.pluginServerName || "").trim();
  const suffix = server?.enabled ? "" : " (deaktiviert)";
  if (pluginName && pluginName !== configuredName) {
    return `${pluginName} (${configuredName})${suffix}`;
  }
  return `${configuredName}${suffix}`;
}

function questCategoryName(category) {
  return String(category?.name || category?.displayName || category?.id || category?.key || "-");
}

function questCategoryValue(quest) {
  return String(quest?.category || quest?.categoryId || "");
}

function responseArray(value, key) {
  if (Array.isArray(value)) {
    return value;
  }
  if (Array.isArray(value?.[key])) {
    return value[key];
  }
  return asArray(value);
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
    <button data-ticket-action="open" data-ticket-id="${escapeAttr(ticket.id)}" type="button">Öffnen</button>
    <button data-ticket-action="progress" data-ticket-id="${escapeAttr(ticket.id)}" type="button">In Bearbeitung</button>
    <button data-ticket-action="close" data-ticket-id="${escapeAttr(ticket.id)}" type="button">Schließen</button>
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
    elements.liteBanTable.innerHTML = `<tr><td colspan="7" class="muted">Noch keine LiteBans synchronisiert.</td></tr>`;
    return;
  }

  elements.liteBanTable.innerHTML = state.liteBans.map(ban => `
    <tr>
      <td>${liteBanIdCell(ban)}</td>
      <td>
        <strong>${escapeHtml(ban.targetName || "-")}</strong><br>
        <span class="muted">${escapeHtml(ban.targetUniqueId || ban.targetAddress || "-")}</span>
      </td>
      <td>${escapeHtml(ban.issuedBy || "-")}</td>
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
    <span class="muted">Bridge prüfen, intern: ${escapeHtml(internalId || "-")}</span>
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
      <button data-liteban-action="extend" data-ban-id="${escapeAttr(ban.id)}" type="button">Verlängern</button>
    </div>
  `;
}

function renderBanAppeals() {
  const activeAppeals = state.banAppeals.filter(appeal => !isArchivedAppeal(appeal));
  if (!activeAppeals.length) {
    elements.banAppealTable.innerHTML = `<tr><td colspan="8" class="muted">Keine offenen Entbannungsanträge vorhanden.</td></tr>`;
    return;
  }

  elements.banAppealTable.innerHTML = activeAppeals.map(appeal => `
    <tr>
      <td>
        <span class="badge ${appealStatusClass(appeal.status)}">${escapeHtml(appealStatusLabel(appeal.status))}</span><br>
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
    elements.banAppealArchiveTable.innerHTML = `<tr><td colspan="8" class="muted">Noch keine abgeschlossenen Entbannungsanträge im Archiv.</td></tr>`;
    return;
  }

  elements.banAppealArchiveTable.innerHTML = archivedAppeals.map(appeal => `
    <tr>
      <td>
        <span class="badge ${appealStatusClass(appeal.status)}">${escapeHtml(appealStatusLabel(appeal.status))}</span><br>
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
      <td>${escapeHtml(appeal.updatedBy || "-")}</td>
    </tr>
  `).join("");
}

function isArchivedAppeal(appeal) {
  return ["ACCEPTED", "REJECTED", "CLOSED"].includes(String(appeal.status || "").toUpperCase());
}

function appealStatusClass(status) {
  const normalized = String(status || "").toUpperCase();
  return normalized === "ACCEPTED" ? "badge-success" : normalized === "REJECTED" ? "badge-danger" : "";
}

function appealStatusLabel(status) {
  const settings = state.settings || {};
  switch (String(status || "").toUpperCase()) {
    case "IN_REVIEW":
      return settings.appealStatusInReviewLabel || "In Prüfung";
    case "ACCEPTED":
      return settings.appealStatusAcceptedLabel || "Angenommen";
    case "REJECTED":
      return settings.appealStatusRejectedLabel || "Abgelehnt";
    case "CLOSED":
      return settings.appealStatusClosedLabel || "Geschlossen";
    default:
      return settings.appealStatusOpenLabel || "Offen";
  }
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
    links.push(appealAttachmentLink(appeal, attachment));
  });
  return links.join("<br>") || `<span class="muted">-</span>`;
}

function appealAttachmentLink(appeal, attachment) {
  const label = attachment.fileName || attachment.storageType || "Beweisdatei";
  const reference = String(attachment.storageReference || "").trim();
  if (isHttpUrl(reference)) {
    return `<a href="${escapeAttr(reference)}" target="_blank" rel="noreferrer">${escapeHtml(label)}</a>`;
  }
  const url = `/api/ban-appeals/${encodeURIComponent(appeal.id)}/attachments/${encodeURIComponent(attachment.id)}`;
  return `
    <a
      href="${escapeAttr(url)}"
      data-evidence-url="${escapeAttr(url)}"
      data-evidence-name="${escapeAttr(label)}"
      title="${escapeAttr(reference)}">${escapeHtml(label)}</a>
  `;
}

function banAppealActions(appeal) {
  if (!hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return `<span class="muted">Nur Ansicht</span>`;
  }
  return `
    <button data-appeal-action="IN_REVIEW" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Prüfung</button>
    <button data-appeal-action="ACCEPTED" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Annehmen</button>
    <button data-appeal-action="REJECTED" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Ablehnen</button>
    <button data-appeal-action="CLOSED" data-appeal-id="${escapeAttr(appeal.id)}" type="button">Schließen</button>
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

function renderPermissionSubjects() {
  const subjects = filteredPermissionSubjects();
  if (!subjects.length) {
    elements.permissionSubjectList.innerHTML = `<p class="muted">Noch keine Subjects für diesen Server synchronisiert.</p>`;
    elements.permissionSelectedSummary.textContent = "Wähle einen Server mit synchronisierten LuckPerms-Daten aus.";
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
  const ticketCategories = asArray(settings.ticketCategories).length ? asArray(settings.ticketCategories) : asArray(state.meta?.ticketCategories);
  setFormValue(form, "ticketCategories", ticketCategories.join("\n"));
  elements.ticketCategorySettingsList.innerHTML = ticketCategories.map(category => (
    `<span class="badge">${escapeHtml(category)}</span>`
  )).join("") || `<span class="muted">Keine Ticket-Arten hinterlegt.</span>`;
  setFormValue(form, "cloudNetScreenName", settings.cloudNetScreenName || "");
  setFormValue(form, "cloudNetRestBaseUrl", settings.cloudNetRestBaseUrl || "");
  setFormValue(form, "cloudNetRestUsername", settings.cloudNetRestUsername || "");
  setFormValue(form, "cloudNetRestPassword", "");
  if (form.cloudNetRestPassword) {
    form.cloudNetRestPassword.placeholder = settings.cloudNetRestPasswordConfigured ? "gesetzt, leer lassen = behalten" : "nicht gesetzt";
  }
  setFormValue(form, "cloudNetRestThreshold", settings.cloudNetRestThreshold || "INFO");
  renderQuestServerSettings(settings.questEditorServers || []);
  setFormValue(form, "appealBrandName", settings.appealBrandName || settings.brandName || "Craftplay.de");
  setFormValue(form, "appealTitle", settings.appealTitle || "Entbannungsantrag");
  setFormValue(form, "appealStatusTitle", settings.appealStatusTitle || "Dein Entbannungsantrag");
  setFormValue(form, "appealStatusOpenLabel", settings.appealStatusOpenLabel || "Offen");
  setFormValue(form, "appealStatusInReviewLabel", settings.appealStatusInReviewLabel || "In Prüfung");
  setFormValue(form, "appealStatusAcceptedLabel", settings.appealStatusAcceptedLabel || "Angenommen");
  setFormValue(form, "appealStatusRejectedLabel", settings.appealStatusRejectedLabel || "Abgelehnt");
  setFormValue(form, "appealStatusClosedLabel", settings.appealStatusClosedLabel || "Geschlossen");
  setFormValue(form, "appealStatusOpenText", settings.appealStatusOpenText || "");
  setFormValue(form, "appealStatusInReviewText", settings.appealStatusInReviewText || "");
  setFormValue(form, "appealStatusAcceptedText", settings.appealStatusAcceptedText || "");
  setFormValue(form, "appealStatusRejectedText", settings.appealStatusRejectedText || "");
  setFormValue(form, "appealStatusClosedText", settings.appealStatusClosedText || "");
  setFormValue(form, "appealPublicBaseUrl", settings.appealPublicBaseUrl || "");
  setFormValue(form, "appealMaxFiles", settings.appealMaxFiles ?? 3);
  setFormValue(form, "appealMaxFileBytes", settings.appealMaxFileBytes ?? 10485760);
  setFormValue(form, "appealEvidenceStorage", settings.appealEvidenceStorage || "LOCAL");
  setFormValue(form, "appealEvidenceLocalDirectory", settings.appealEvidenceLocalDirectory || "appeal-evidence");
  setFormValue(form, "appealEvidenceSftpHost", settings.appealEvidenceSftpHost || "");
  setFormValue(form, "appealEvidenceSftpPort", settings.appealEvidenceSftpPort || 22);
  setFormValue(form, "appealEvidenceSftpUsername", settings.appealEvidenceSftpUsername || "");
  setFormValue(form, "appealEvidenceSftpPassword", "");
  if (form.appealEvidenceSftpPassword) {
    form.appealEvidenceSftpPassword.placeholder = settings.appealEvidenceSftpPasswordConfigured ? "gesetzt, leer lassen = behalten" : "nicht gesetzt";
  }
  setFormValue(form, "appealEvidenceSftpPrivateKeyPath", settings.appealEvidenceSftpPrivateKeyPath || "");
  setFormValue(form, "appealEvidenceSftpRemoteDirectory", settings.appealEvidenceSftpRemoteDirectory || "/appeals");
  setFormValue(form, "appealEvidenceOneDriveUploadUrlTemplate", settings.appealEvidenceOneDriveUploadUrlTemplate || "");
  setFormValue(form, "appealEvidenceOneDriveTenant", settings.appealEvidenceOneDriveTenant || "common");
  setFormValue(form, "appealEvidenceOneDriveClientId", settings.appealEvidenceOneDriveClientId || "");
  renderOneDriveFolderOptions(settings.appealEvidenceOneDriveFolderPath || "Entbannungsantraege");
  setFormValue(
    form,
    "appealEvidenceOneDriveConnection",
    settings.appealEvidenceOneDriveRefreshTokenConfigured ? "Verbunden" : "Nicht verbunden"
  );
  setFormValue(form, "appealEvidenceOneDriveBearerToken", "");
  if (form.appealEvidenceOneDriveBearerToken) {
    form.appealEvidenceOneDriveBearerToken.placeholder = settings.appealEvidenceOneDriveBearerTokenConfigured ? "gesetzt, leer lassen = behalten" : "nicht gesetzt";
  }
  if (elements.oneDriveStatus && !state.oneDrivePollTimer) {
    setStatus(
      elements.oneDriveStatus,
      settings.appealEvidenceOneDriveRefreshTokenConfigured
        ? "OneDrive OAuth ist verbunden."
        : "Noch keine OneDrive OAuth-Verbindung eingerichtet.",
      false
    );
  }
  if (elements.oneDriveLoginLink && !state.oneDrivePollTimer) {
    elements.oneDriveLoginLink.classList.add("hidden");
    elements.oneDriveLoginLink.removeAttribute("href");
  }
  if (settings.appealEvidenceOneDriveRefreshTokenConfigured && !state.oneDriveFoldersLoaded && !state.oneDriveFoldersLoading) {
    loadOneDriveFolders(false);
  }
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

function renderQuestServerSettings(servers) {
  const values = asArray(servers);
  const effectiveServers = values.length ? values : [{
    id: "default",
    name: "Craftplay Server",
    host: "127.0.0.1",
    port: 8095,
    enabled: false,
    basePath: "/api/craftplayquests/v1",
    connectTimeoutMillis: 3000,
    readTimeoutMillis: 5000,
    tokenConfigured: false,
  }];

  elements.questServerSettingsList.innerHTML = effectiveServers.map((server, index) => questServerSettingsRow(server, index)).join("");
}

function questServerSettingsRow(server, index) {
  const tokenPlaceholder = server.tokenConfigured ? "gesetzt, leer lassen = behalten" : "Token eintragen";
  return `
    <article class="quest-server-settings-row" data-quest-server-row>
      <div class="quest-server-settings-head">
        <label class="inline-label">
          <input data-quest-server-field="enabled" type="checkbox" ${server.enabled ? "checked" : ""}>
          <span>Aktiv</span>
        </label>
        <button data-quest-server-remove type="button" class="ghost-button">Entfernen</button>
      </div>
      <div class="form-grid">
        <label>
          <span>ID</span>
          <input data-quest-server-field="id" type="text" value="${escapeAttr(server.id || `server-${index + 1}`)}" placeholder="survival-1">
        </label>
        <label>
          <span>Name im Panel</span>
          <input data-quest-server-field="name" type="text" value="${escapeAttr(server.name || "Craftplay Server")}" placeholder="Survival-1">
        </label>
        <label>
          <span>IP oder Host</span>
          <input data-quest-server-field="host" type="text" value="${escapeAttr(server.host || "127.0.0.1")}" placeholder="127.0.0.1">
        </label>
        <label>
          <span>Port</span>
          <input data-quest-server-field="port" type="number" min="1" max="65535" value="${escapeAttr(server.port || 8095)}">
        </label>
        <label>
          <span>API-Pfad</span>
          <input data-quest-server-field="basePath" type="text" value="${escapeAttr(server.basePath || "/api/craftplayquests/v1")}">
        </label>
        <label>
          <span>Token</span>
          <input data-quest-server-field="token" type="password" placeholder="${escapeAttr(tokenPlaceholder)}">
        </label>
        <label>
          <span>Connect Timeout ms</span>
          <input data-quest-server-field="connectTimeoutMillis" type="number" min="500" max="30000" value="${escapeAttr(server.connectTimeoutMillis || 3000)}">
        </label>
        <label>
          <span>Read Timeout ms</span>
          <input data-quest-server-field="readTimeoutMillis" type="number" min="500" max="60000" value="${escapeAttr(server.readTimeoutMillis || 5000)}">
        </label>
      </div>
    </article>
  `;
}

function readQuestServerSettings() {
  return [...elements.questServerSettingsList.querySelectorAll("[data-quest-server-row]")].map((row, index) => {
    const value = field => row.querySelector(`[data-quest-server-field="${field}"]`);
    return {
      id: String(value("id")?.value || `server-${index + 1}`).trim(),
      name: String(value("name")?.value || "").trim(),
      host: String(value("host")?.value || "127.0.0.1").trim(),
      port: Number(value("port")?.value || 8095),
      enabled: Boolean(value("enabled")?.checked),
      basePath: String(value("basePath")?.value || "/api/craftplayquests/v1").trim(),
      token: String(value("token")?.value || ""),
      connectTimeoutMillis: Number(value("connectTimeoutMillis")?.value || 3000),
      readTimeoutMillis: Number(value("readTimeoutMillis")?.value || 5000),
    };
  });
}

function addQuestServerSettingsRow() {
  const servers = readQuestServerSettings();
  servers.push({
    id: `server-${servers.length + 1}`,
    name: `Quest-Server ${servers.length + 1}`,
    host: "127.0.0.1",
    port: 8095,
    enabled: true,
    basePath: "/api/craftplayquests/v1",
    tokenConfigured: false,
    connectTimeoutMillis: 3000,
    readTimeoutMillis: 5000,
  });
  renderQuestServerSettings(servers);
}

function handleQuestServerSettingsClick(event) {
  const button = event.target.closest("[data-quest-server-remove]");
  if (!button) {
    return;
  }
  const row = button.closest("[data-quest-server-row]");
  row?.remove();
  if (!elements.questServerSettingsList.querySelector("[data-quest-server-row]")) {
    renderQuestServerSettings([]);
  }
}

function renderOneDriveFolderOptions(selectedPath) {
  const select = elements.settingsForm?.elements?.appealEvidenceOneDriveFolderPath;
  if (!select) {
    return;
  }

  const selected = String(selectedPath || "Entbannungsantraege").trim();
  const options = new Map();
  if (selected) {
    options.set(selected, `${selected} (aktuell)`);
  }
  state.oneDriveFolders.forEach(folder => {
    const path = String(folder.path || folder.name || "").trim();
    if (path) {
      options.set(path, path);
    }
  });
  if (!options.size) {
    options.set("Entbannungsantraege", "Entbannungsantraege");
  }

  select.innerHTML = [...options.entries()]
    .map(([value, label]) => `<option value="${escapeAttr(value)}">${escapeHtml(label)}</option>`)
    .join("");
  select.value = options.has(selected) ? selected : [...options.keys()][0];
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
    elements.permissionSelectedSummary.textContent = "Wähle links eine Gruppe oder einen Spieler aus.";
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
    : `<p class="muted">Noch keine Nodes für dieses Subject synchronisiert.</p>`;
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
    issuedBy: currentActor(),
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
    setStatus(elements.banStatus, `Ban für ${created.targetName} angelegt.`, false);
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
    actor: currentActor(),
  };

  if (!subject) {
    setStatus(elements.permissionStatus, "Bitte zuerst links ein Subject auswählen.", true);
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
    ticketCategories: splitLinesOrCsv(form.get("ticketCategories")),
    cloudNetScreenName: String(form.get("cloudNetScreenName") || "").trim(),
    cloudNetRestBaseUrl: String(form.get("cloudNetRestBaseUrl") || "").trim(),
    cloudNetRestUsername: String(form.get("cloudNetRestUsername") || "").trim(),
    cloudNetRestThreshold: String(form.get("cloudNetRestThreshold") || "INFO").trim(),
    questEditorServers: readQuestServerSettings(),
    appealBrandName: String(form.get("appealBrandName") || "").trim(),
    appealTitle: String(form.get("appealTitle") || "").trim(),
    appealStatusTitle: String(form.get("appealStatusTitle") || "").trim(),
    appealStatusOpenLabel: String(form.get("appealStatusOpenLabel") || "").trim(),
    appealStatusInReviewLabel: String(form.get("appealStatusInReviewLabel") || "").trim(),
    appealStatusAcceptedLabel: String(form.get("appealStatusAcceptedLabel") || "").trim(),
    appealStatusRejectedLabel: String(form.get("appealStatusRejectedLabel") || "").trim(),
    appealStatusClosedLabel: String(form.get("appealStatusClosedLabel") || "").trim(),
    appealStatusOpenText: String(form.get("appealStatusOpenText") || "").trim(),
    appealStatusInReviewText: String(form.get("appealStatusInReviewText") || "").trim(),
    appealStatusAcceptedText: String(form.get("appealStatusAcceptedText") || "").trim(),
    appealStatusRejectedText: String(form.get("appealStatusRejectedText") || "").trim(),
    appealStatusClosedText: String(form.get("appealStatusClosedText") || "").trim(),
    appealPublicBaseUrl: String(form.get("appealPublicBaseUrl") || "").trim(),
    appealMaxFiles: Number(form.get("appealMaxFiles") || 3),
    appealMaxFileBytes: Number(form.get("appealMaxFileBytes") || 10485760),
    appealEvidenceStorage: String(form.get("appealEvidenceStorage") || "LOCAL").trim(),
    appealEvidenceLocalDirectory: String(form.get("appealEvidenceLocalDirectory") || "").trim(),
    appealEvidenceSftpHost: String(form.get("appealEvidenceSftpHost") || "").trim(),
    appealEvidenceSftpPort: Number(form.get("appealEvidenceSftpPort") || 22),
    appealEvidenceSftpUsername: String(form.get("appealEvidenceSftpUsername") || "").trim(),
    appealEvidenceSftpPrivateKeyPath: String(form.get("appealEvidenceSftpPrivateKeyPath") || "").trim(),
    appealEvidenceSftpRemoteDirectory: String(form.get("appealEvidenceSftpRemoteDirectory") || "").trim(),
    appealEvidenceOneDriveUploadUrlTemplate: String(form.get("appealEvidenceOneDriveUploadUrlTemplate") || "").trim(),
    appealEvidenceOneDriveTenant: String(form.get("appealEvidenceOneDriveTenant") || "common").trim(),
    appealEvidenceOneDriveClientId: String(form.get("appealEvidenceOneDriveClientId") || "").trim(),
    appealEvidenceOneDriveFolderPath: String(form.get("appealEvidenceOneDriveFolderPath") || "").trim(),
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
  const cloudNetRestPassword = String(form.get("cloudNetRestPassword") || "");
  const appealEvidenceSftpPassword = String(form.get("appealEvidenceSftpPassword") || "");
  const appealEvidenceOneDriveBearerToken = String(form.get("appealEvidenceOneDriveBearerToken") || "");
  const liteBansDatabasePassword = String(form.get("liteBansDatabasePassword") || "");
  const liteBansBridgeSecret = String(form.get("liteBansBridgeSecret") || "");
  if (cloudNetRestPassword) {
    payload.cloudNetRestPassword = cloudNetRestPassword;
  }
  if (smtpPassword) {
    payload.smtpPassword = smtpPassword;
  }
  if (appealEvidenceSftpPassword) {
    payload.appealEvidenceSftpPassword = appealEvidenceSftpPassword;
  }
  if (appealEvidenceOneDriveBearerToken) {
    payload.appealEvidenceOneDriveBearerToken = appealEvidenceOneDriveBearerToken;
  }
  if (liteBansDatabasePassword) {
    payload.liteBansDatabasePassword = liteBansDatabasePassword;
  }
  if (liteBansBridgeSecret) {
    payload.liteBansBridgeSecret = liteBansBridgeSecret;
  }

  try {
    state.settings = await api("/api/settings", { method: "PUT", body: payload });
    syncTicketCategoriesFromSettings();
    renderSettings();
    setStatus(elements.settingsStatus, "Einstellungen gespeichert.", false);
    await refreshAll();
  } catch (error) {
    handleApiError(error, elements.settingsStatus);
  }
}

async function handleOneDriveConnect(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    return;
  }

  const form = new FormData(elements.settingsForm);
  const clientId = String(form.get("appealEvidenceOneDriveClientId") || "").trim();
  if (!clientId) {
    setStatus(elements.oneDriveStatus, "Bitte zuerst die OneDrive Client ID eintragen.", true);
    return;
  }

  try {
    clearTimeout(state.oneDrivePollTimer);
    state.oneDrivePollTimer = null;
    setStatus(elements.oneDriveStatus, "Speichere OneDrive-Einstellungen und starte Microsoft-Anmeldung ...", false);
    state.settings = await api("/api/settings", {
      method: "PUT",
      body: {
        appealEvidenceStorage: String(form.get("appealEvidenceStorage") || "ONEDRIVE").trim(),
        appealEvidenceOneDriveUploadUrlTemplate: String(form.get("appealEvidenceOneDriveUploadUrlTemplate") || "").trim(),
        appealEvidenceOneDriveTenant: String(form.get("appealEvidenceOneDriveTenant") || "common").trim(),
        appealEvidenceOneDriveClientId: clientId,
        appealEvidenceOneDriveFolderPath: String(form.get("appealEvidenceOneDriveFolderPath") || "").trim(),
      },
    });
    renderSettings();

    const deviceCode = await api("/api/settings/onedrive/device-code", { method: "POST" });
    const loginUrl = deviceCode.verificationUriComplete || deviceCode.verificationUri;
    if (loginUrl) {
      elements.oneDriveLoginLink.href = loginUrl;
      elements.oneDriveLoginLink.classList.remove("hidden");
    }
    const codeHint = deviceCode.userCode ? ` Code: ${deviceCode.userCode}` : "";
    setStatus(
      elements.oneDriveStatus,
      `${deviceCode.message || "Bitte Microsoft-Anmeldung über den Button öffnen und abschließen."}${codeHint}`,
      false
    );
    pollOneDriveConnection(
      deviceCode.deviceCode,
      Number(deviceCode.interval || 5),
      Date.now() + Number(deviceCode.expiresIn || 900) * 1000
    );
  } catch (error) {
    clearTimeout(state.oneDrivePollTimer);
    state.oneDrivePollTimer = null;
    handleApiError(error, elements.oneDriveStatus);
  }
}

function pollOneDriveConnection(deviceCode, intervalSeconds, expiresAt) {
  clearTimeout(state.oneDrivePollTimer);
  const poll = async () => {
    if (Date.now() > expiresAt) {
      state.oneDrivePollTimer = null;
      setStatus(elements.oneDriveStatus, "Der Microsoft-Anmeldecode ist abgelaufen. Bitte erneut verbinden.", true);
      return;
    }

    try {
      const result = await api("/api/settings/onedrive/complete", {
        method: "POST",
        body: { deviceCode },
      });
      if (result.connected) {
        state.oneDrivePollTimer = null;
        state.settings = await api("/api/settings");
        elements.oneDriveLoginLink.classList.add("hidden");
        elements.oneDriveLoginLink.removeAttribute("href");
        state.oneDriveFoldersLoaded = false;
        renderSettings();
        setStatus(elements.oneDriveStatus, result.message || "OneDrive wurde erfolgreich verbunden.", false);
        await loadOneDriveFolders(false);
        return;
      }
      const waitSeconds = Number(result.interval || intervalSeconds || 5);
      setStatus(elements.oneDriveStatus, result.message || "Warte auf Abschluss der Microsoft-Anmeldung ...", false);
      state.oneDrivePollTimer = setTimeout(poll, waitSeconds * 1000);
    } catch (error) {
      state.oneDrivePollTimer = null;
      handleApiError(error, elements.oneDriveStatus);
    }
  };
  state.oneDrivePollTimer = setTimeout(poll, Math.max(1, intervalSeconds || 5) * 1000);
}

async function handleOneDriveDisconnect(event) {
  event.preventDefault();
  if (!hasPermission(PERMISSIONS.SETTINGS_MANAGE)) {
    return;
  }

  try {
    clearTimeout(state.oneDrivePollTimer);
    state.oneDrivePollTimer = null;
    const result = await api("/api/settings/onedrive/disconnect", { method: "POST" });
    state.settings = await api("/api/settings");
    state.oneDriveFolders = [];
    state.oneDriveFoldersLoaded = false;
    elements.oneDriveLoginLink.classList.add("hidden");
    elements.oneDriveLoginLink.removeAttribute("href");
    renderSettings();
    setStatus(elements.oneDriveStatus, result.message || "OneDrive-Verbindung wurde getrennt.", false);
  } catch (error) {
    handleApiError(error, elements.oneDriveStatus);
  }
}

async function loadOneDriveFolders(showStatus) {
  if (!state.settings?.appealEvidenceOneDriveRefreshTokenConfigured) {
    if (showStatus) {
      setStatus(elements.oneDriveStatus, "Bitte OneDrive zuerst verbinden.", true);
    }
    return;
  }
  if (state.oneDriveFoldersLoading) {
    return;
  }

  try {
    state.oneDriveFoldersLoading = true;
    if (showStatus) {
      setStatus(elements.oneDriveStatus, "OneDrive-Ordner werden geladen ...", false);
    }
    const result = await api("/api/settings/onedrive/folders");
    state.oneDriveFolders = asArray(result.folders);
    state.oneDriveFoldersLoaded = true;
    renderOneDriveFolderOptions(elements.settingsForm.elements.appealEvidenceOneDriveFolderPath.value || state.settings.appealEvidenceOneDriveFolderPath);
    setStatus(elements.oneDriveStatus, `${state.oneDriveFolders.length} OneDrive-Ordner geladen.`, false);
  } catch (error) {
    handleApiError(error, elements.oneDriveStatus);
  } finally {
    state.oneDriveFoldersLoading = false;
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
    setStatus(elements.permissionStatus, "Bitte zuerst ein Subject auswählen.", true);
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
    actor: currentActor(),
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

async function handleTicketTableClick(event) {
  const button = event.target.closest("button[data-ticket-action]");
  if (!button || !hasPermission(PERMISSIONS.TICKETS_MANAGE)) {
    return;
  }

  const ticketId = button.dataset.ticketId;
  const action = button.dataset.ticketAction;

  try {
    if (action === "assign") {
      const assignedTo = prompt("An wen soll das Ticket zugewiesen werden?", currentActor());
      if (!assignedTo) {
        return;
      }
      await api(`/api/tickets/${encodeURIComponent(ticketId)}/assign`, {
        method: "POST",
        body: { assignedTo, actor: currentActor() },
      });
    } else if (action === "comment") {
      const message = prompt("Kommentartext?");
      const internal = confirm("Interner Kommentar?");
      if (!message) {
        return;
      }
      await api(`/api/tickets/${encodeURIComponent(ticketId)}/comments`, {
        method: "POST",
        body: { author: currentActor(), message, internal },
      });
    } else if (action === "teleport") {
      const ticket = state.tickets.find(entry => entry.id === ticketId);
      const staffName = currentMinecraftName();
      if (!ticket || !staffName) {
        setStatus(elements.ticketStatus, "Bitte hinterlege in deinem Profil deinen Minecraft-Namen.", true);
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
          actor: currentActor(),
        },
      });
      setStatus(elements.ticketStatus, "Teleport wurde an Velocity übergeben.", false);
    } else {
      const status = action === "open" ? "OPEN" : action === "progress" ? "IN_PROGRESS" : "CLOSED";
      await api(`/api/tickets/${encodeURIComponent(ticketId)}/status`, {
        method: "POST",
        body: { actor: currentActor(), status },
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
  if (button.dataset.homePage === "cloudnet") {
    switchCloudNetSection("cloud");
  }
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

  const reason = prompt("Notiz/Grund für das Aufheben?", "Panel-Ban aufgehoben");
  if (reason === null) {
    return;
  }

  try {
    await api(`/api/bans/${encodeURIComponent(banId)}/deactivate`, {
      method: "POST",
      body: { reason },
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
  const actor = currentActor();

  try {
    if (action === "unban") {
      const reason = prompt("Grund für das Aufheben?", "Unban via Panel");
      if (reason === null) {
        return;
      }
      await api(`/api/bans/litebans/${encodeURIComponent(banId)}/unban`, {
        method: "POST",
        body: { actor, reason },
      });
      setStatus(elements.banStatus, "Unban wurde an Velocity übergeben.", false);
    }

    if (action === "extend") {
      const duration = prompt("Neue/weitere Dauer für LiteBans, z.B. 7d, 30d, 1mo");
      if (!duration) {
        return;
      }
      const reason = prompt("Grund für die Verlängerung?", "Ban via Panel verlängert");
      if (reason === null) {
        return;
      }
      await api(`/api/bans/litebans/${encodeURIComponent(banId)}/extend`, {
        method: "POST",
        body: { actor, duration, reason },
      });
      setStatus(elements.banStatus, "Verlängerung wurde an Velocity übergeben.", false);
    }

    await refreshAll();
    switchBanTab("litebans");
  } catch (error) {
    handleApiError(error, elements.banStatus);
  }
}

async function handleBanAppealTableClick(event) {
  const evidenceLink = event.target.closest("a[data-evidence-url]");
  if (evidenceLink) {
    event.preventDefault();
    await openEvidenceLink(evidenceLink);
    return;
  }

  const button = event.target.closest("button[data-appeal-action]");
  if (!button || !hasPermission(PERMISSIONS.BANS_MANAGE)) {
    return;
  }

  const teamNote = prompt("Team-Notiz/Grund für diese Entscheidung? Leer lassen, wenn keine Notiz gesetzt werden soll.", "");
  if (teamNote === null) {
    return;
  }

  try {
    await api(`/api/ban-appeals/${encodeURIComponent(button.dataset.appealId)}/status`, {
      method: "POST",
      body: {
        status: button.dataset.appealAction,
        teamNote,
        actor: currentActor(),
      },
    });
    await refreshAll();
    switchBanTab(isArchivedAppeal({ status: button.dataset.appealAction }) ? "appeal-archive" : "appeals");
  } catch (error) {
    handleApiError(error, elements.banStatus);
  }
}

async function openEvidenceLink(link) {
  let popup = null;
  try {
    popup = window.open("", "_blank", "noopener");
    if (popup) {
      popup.document.write("<p>Beweisdatei wird geladen...</p>");
    }

    const headers = {};
    if (state.token) {
      headers.Authorization = `Bearer ${state.token}`;
    }
    const response = await fetch(link.dataset.evidenceUrl, { headers });
    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data.error || `HTTP ${response.status}`);
    }

    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    if (popup) {
      popup.location.href = objectUrl;
    } else {
      const download = document.createElement("a");
      download.href = objectUrl;
      download.download = link.dataset.evidenceName || "beweisdatei";
      document.body.append(download);
      download.click();
      download.remove();
    }
    setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  } catch (error) {
    if (popup && !popup.closed) {
      popup.close();
    }
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

function splitLinesOrCsv(value) {
  return String(value || "")
    .split(/[\n,]+/)
    .map(entry => entry.trim())
    .filter(Boolean);
}

function syncTicketCategoriesFromSettings() {
  const categories = asArray(state.settings?.ticketCategories);
  if (!categories.length) {
    return;
  }
  state.meta = {
    ...(state.meta || {}),
    ticketCategories: categories,
  };
  const selected = elements.ticketCategorySelect.value || categories[0];
  populateSelect(elements.ticketCategorySelect, categories, selected);
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
