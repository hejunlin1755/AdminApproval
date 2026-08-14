package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AdminApprovalPlugin extends JavaPlugin {
    private RequestStore requestStore;
    private DataFileStore dataFileStore;
    private DangerousCommandPolicy policy;
    private AccessControl accessControl;
    private TelegramService telegramService;

    @Override
    public void onEnable() {
        Path dataFolder = this.getDataFolder().toPath();

        ApprovalConfigStore configStore = new ApprovalConfigStore(dataFolder.resolve("config.yml"));
        ApprovalConfig config = configStore.load();
        this.accessControl = new AccessControl(config.ownerUuids());

        CommandSettingsStore settingsStore = new CommandSettingsStore(dataFolder.resolve("command-settings.yml"));
        CommandSettings settings = settingsStore.load();

        this.dataFileStore = new DataFileStore(dataFolder.resolve("data.yml"));
        StoreSnapshot snapshot = loadSnapshot();

        Set<String> initialWhitelist;
        if (snapshot.whitelist() != null) {
            initialWhitelist = new LinkedHashSet<>(snapshot.whitelist());
        } else {
            initialWhitelist = new LinkedHashSet<>(settings.whitelist());
        }

        this.policy = new DangerousCommandPolicy(settings.dangerous(), initialWhitelist, settings.blockedPatterns());

        this.requestStore = new RequestStore(500);
        this.requestStore.load(snapshot);
        this.requestStore.setSaveHook(this::saveData);

        this.telegramService = new TelegramService(
                config.telegram(),
                this.requestStore,
                commandLine -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine),
                Bukkit::getPlayer,
                this.getLogger()
        );

        this.getServer().getPluginManager().registerEvents(
                new DangerousCommandListener(this.policy, this.requestStore, this.accessControl, this.telegramService,
                        settings.notifyAllAdminCommands(), settings.sensitiveCommands(), settings.sensitiveKeywords()), this);

        registerRequiredCommand("adminrequest", new AdminRequestCommand(this.policy, this.requestStore, this.accessControl, this.telegramService));
        registerRequiredCommand("adminapprove", new AdminApproveCommand(this.requestStore, this.accessControl));
        registerRequiredCommand("adminreject", new AdminRejectCommand(this.requestStore, this.accessControl));
        registerRequiredCommand("adminrequests", new AdminRequestsCommand(this.requestStore, this.accessControl));
        registerRequiredCommand("adminhistory", new AdminHistoryCommand(this.requestStore, this.accessControl));
        registerRequiredCommand("adminapproval", new AdminApprovalCommand(this.accessControl, this.policy, this::saveData));

        saveData();
        if (this.telegramService.isEnabled()) {
            this.telegramService.start();
        }
        this.getLogger().info("AdminApproval 已启用，服主数量: " + this.accessControl.ownerUuids().size());
    }

    @Override
    public void onDisable() {
        if (this.telegramService != null) {
            this.telegramService.stop();
        }
        saveData();
    }

    private StoreSnapshot loadSnapshot() {
        try {
            return this.dataFileStore.load();
        } catch (Exception ex) {
            this.getLogger().warning("无法加载 data.yml，已使用空数据启动: " + ex.getMessage());
            return new StoreSnapshot(1, List.of(), List.of(), Set.of());
        }
    }

    private void saveData() {
        if (this.requestStore == null || this.dataFileStore == null || this.policy == null) {
            return;
        }

        try {
            StoreSnapshot snapshot = this.requestStore.snapshot();
            StoreSnapshot withWhitelist = new StoreSnapshot(
                    snapshot.nextId(),
                    snapshot.pending(),
                    snapshot.history(),
                    this.policy.getCommandWhitelist()
            );
            this.dataFileStore.save(withWhitelist);
        } catch (Exception ex) {
            this.getLogger().warning("保存 data.yml 失败: " + ex.getMessage());
        }
    }

    private void registerRequiredCommand(String commandName, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = this.getCommand(commandName);
        if (command == null) {
            throw new IllegalStateException("Command not defined in plugin.yml: " + commandName);
        }
        command.setExecutor(executor);
    }
}
