package de.speed.ticketconsolecloudban;

import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import de.speed.ticketconsolecloudban.ban.LiteBansDatabaseSyncService;
import de.speed.ticketconsolecloudban.auth.PanelSecurityService;
import de.speed.ticketconsolecloudban.appeal.BanAppealService;
import de.speed.ticketconsolecloudban.appeal.EvidenceStorageFactory;
import de.speed.ticketconsolecloudban.http.BanAppealHttpServer;
import de.speed.ticketconsolecloudban.http.PanelHttpServer;
import de.speed.ticketconsolecloudban.service.CloudNetFacade;
import de.speed.ticketconsolecloudban.settings.PanelSettingsStore;
import de.speed.ticketconsolecloudban.store.BanAppealStore;
import de.speed.ticketconsolecloudban.store.BanStore;
import de.speed.ticketconsolecloudban.store.PanelDataBackendFactory;
import de.speed.ticketconsolecloudban.store.PanelUserStore;
import de.speed.ticketconsolecloudban.store.PermissionBridgeStore;
import de.speed.ticketconsolecloudban.store.PlayerActionStore;
import de.speed.ticketconsolecloudban.store.TicketStore;
import eu.cloudnetservice.driver.document.DocumentFactory;
import eu.cloudnetservice.driver.module.ModuleLifeCycle;
import eu.cloudnetservice.driver.module.ModuleTask;
import eu.cloudnetservice.driver.module.driver.DriverModule;
import eu.cloudnetservice.driver.provider.CloudServiceFactory;
import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.driver.provider.ClusterNodeProvider;
import eu.cloudnetservice.driver.provider.ServiceTaskProvider;
import eu.cloudnetservice.node.command.CommandProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TicketConsoleCloudBanModule extends DriverModule {

  private static final Logger LOGGER = LoggerFactory.getLogger(TicketConsoleCloudBanModule.class);

  private PanelHttpServer httpServer;
  private BanAppealHttpServer appealHttpServer;

  @ModuleTask
  public void startModule(
    CloudServiceProvider cloudServiceProvider,
    ServiceTaskProvider serviceTaskProvider,
    CloudServiceFactory cloudServiceFactory,
    ClusterNodeProvider clusterNodeProvider,
    CommandProvider commandProvider
  ) {
    var configuration = this.readConfig(PanelConfiguration.class, PanelConfiguration::createDefault, DocumentFactory.json())
      .normalize();
    this.writeConfig(DocumentFactory.json().newDocument().appendTree(configuration));

    var panelDataBackend = PanelDataBackendFactory.create(configuration);
    var settingsStore = new PanelSettingsStore(this.moduleWrapper().dataDirectory(), configuration, panelDataBackend);
    var userStore = new PanelUserStore(this.moduleWrapper().dataDirectory(), panelDataBackend);
    var banStore = new BanStore(this.moduleWrapper().dataDirectory(), panelDataBackend);
    var banAppealStore = new BanAppealStore(this.moduleWrapper().dataDirectory(), panelDataBackend);
    var playerActionStore = new PlayerActionStore(this.moduleWrapper().dataDirectory(), panelDataBackend);
    var liteBansDatabaseSyncService = new LiteBansDatabaseSyncService(configuration, banStore, settingsStore);
    liteBansDatabaseSyncService.syncNow("module-start");
    var security = new PanelSecurityService(userStore, configuration, settingsStore);
    var facade = new CloudNetFacade(
      cloudServiceProvider,
      serviceTaskProvider,
      cloudServiceFactory,
      clusterNodeProvider,
      commandProvider,
      configuration,
      this.moduleWrapper().dataDirectory(),
      new TicketStore(this.moduleWrapper().dataDirectory(), panelDataBackend),
      banStore,
      banAppealStore,
      liteBansDatabaseSyncService,
      settingsStore,
      new PermissionBridgeStore(this.moduleWrapper().dataDirectory(), panelDataBackend),
      playerActionStore);
    var appealService = new BanAppealService(
      configuration,
      banStore,
      banAppealStore,
      liteBansDatabaseSyncService,
      EvidenceStorageFactory.create(configuration, this.moduleWrapper().dataDirectory()),
      settingsStore,
      this.moduleWrapper().dataDirectory());

    this.stopServer();
    this.httpServer = new PanelHttpServer(configuration, facade, security);
    this.httpServer.start();
    this.appealHttpServer = new BanAppealHttpServer(configuration, appealService);
    this.appealHttpServer.start();

    LOGGER.info(
      "TicketConsoleCloudBan started on http://{}:{}",
      configuration.bindHost(),
      configuration.bindPort());
    LOGGER.info("TicketConsoleCloudBan API token: {}", configuration.apiTokens().get(0));
    if (configuration.appealEnabled()) {
      LOGGER.info(
        "TicketConsoleCloudBan appeal form started on http://{}:{}",
        configuration.appealBindHost(),
        configuration.appealBindPort());
    }
    userStore.initialAdminPassword()
      .ifPresent(password -> LOGGER.warn("TicketConsoleCloudBan Panel login created: user=admin password={}", password));
  }

  @ModuleTask(lifecycle = ModuleLifeCycle.RELOADING)
  public void reloadModule(
    CloudServiceProvider cloudServiceProvider,
    ServiceTaskProvider serviceTaskProvider,
    CloudServiceFactory cloudServiceFactory,
    ClusterNodeProvider clusterNodeProvider,
    CommandProvider commandProvider
  ) {
    this.startModule(cloudServiceProvider, serviceTaskProvider, cloudServiceFactory, clusterNodeProvider, commandProvider);
  }

  @ModuleTask(lifecycle = ModuleLifeCycle.STOPPED)
  public void stopModule() {
    this.stopServer();
  }

  private void stopServer() {
    if (this.httpServer != null) {
      this.httpServer.stop();
      this.httpServer = null;
    }
    if (this.appealHttpServer != null) {
      this.appealHttpServer.stop();
      this.appealHttpServer = null;
    }
  }
}
