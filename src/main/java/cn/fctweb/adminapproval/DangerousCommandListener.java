package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

public final class DangerousCommandListener implements Listener {
    private final DangerousCommandPolicy policy;
    private final RequestStore requestStore;
    private final AccessControl accessControl;
    private final Supplier<Collection<? extends Player>> onlinePlayers;
    private final TelegramService telegramService;
    private final boolean notifyAllAdminCommands;
    private final boolean notifyPlayerCommands;
    private final Set<String> sensitiveCommands;
    private final Set<String> sensitiveKeywords;

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl) {
        this(policy, requestStore, accessControl, Bukkit::getOnlinePlayers, TelegramService.disabled(),
                false, false, Set.of(), Set.of());
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    Supplier<Collection<? extends Player>> onlinePlayers) {
        this(policy, requestStore, accessControl, onlinePlayers, TelegramService.disabled(),
                false, false, Set.of(), Set.of());
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    TelegramService telegramService) {
        this(policy, requestStore, accessControl, Bukkit::getOnlinePlayers, telegramService,
                false, false, Set.of(), Set.of());
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    Supplier<Collection<? extends Player>> onlinePlayers,
                                    TelegramService telegramService) {
        this(policy, requestStore, accessControl, onlinePlayers, telegramService,
                false, false, Set.of(), Set.of());
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    TelegramService telegramService, boolean notifyAllAdminCommands,
                                    Set<String> sensitiveCommands, Set<String> sensitiveKeywords) {
        this(policy, requestStore, accessControl, Bukkit::getOnlinePlayers, telegramService,
                notifyAllAdminCommands, false, sensitiveCommands, sensitiveKeywords);
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    TelegramService telegramService, boolean notifyAllAdminCommands,
                                    boolean notifyPlayerCommands, Set<String> sensitiveCommands,
                                    Set<String> sensitiveKeywords) {
        this(policy, requestStore, accessControl, Bukkit::getOnlinePlayers, telegramService,
                notifyAllAdminCommands, notifyPlayerCommands, sensitiveCommands, sensitiveKeywords);
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    Supplier<Collection<? extends Player>> onlinePlayers,
                                    TelegramService telegramService, boolean notifyAllAdminCommands,
                                    Set<String> sensitiveCommands, Set<String> sensitiveKeywords) {
        this(policy, requestStore, accessControl, onlinePlayers, telegramService,
                notifyAllAdminCommands, false, sensitiveCommands, sensitiveKeywords);
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    Supplier<Collection<? extends Player>> onlinePlayers,
                                    TelegramService telegramService, boolean notifyAllAdminCommands,
                                    boolean notifyPlayerCommands, Set<String> sensitiveCommands,
                                    Set<String> sensitiveKeywords) {
        this.policy = policy;
        this.requestStore = requestStore;
        this.accessControl = accessControl;
        this.onlinePlayers = onlinePlayers == null ? Bukkit::getOnlinePlayers : onlinePlayers;
        this.telegramService = telegramService == null ? TelegramService.disabled() : telegramService;
        this.notifyAllAdminCommands = notifyAllAdminCommands;
        this.notifyPlayerCommands = notifyPlayerCommands;
        this.sensitiveCommands = sensitiveCommands == null ? Set.of() : sensitiveCommands;
        this.sensitiveKeywords = sensitiveKeywords == null ? Set.of() : sensitiveKeywords;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage() == null ? "" : event.getMessage();
        if (!this.policy.requiresApproval(message)) {
            notifyAdminCommand(event, message);
            return;
        }

        Player player = event.getPlayer();
        if (this.accessControl.isOwner(player)) {
            return;
        }

        event.setCancelled(true);

        if (!this.accessControl.isAdmin(player)) {
            player.sendMessage("§c你没有权限执行此命令，已拦截。");
            return;
        }

        String requested = message.trim();
        if (requested.startsWith("/")) {
            requested = requested.substring(1);
        }

        ApprovalRequest request = this.requestStore.create(player.getUniqueId(), player.getName(), requested);
        player.sendMessage("§a命令已被拦截并创建审批请求 #" + request.id() + "，等待腐竹处理。");
        this.telegramService.notifyApprovalRequest(request);

        this.onlinePlayers.get().stream()
                .filter(this.accessControl::isOwner)
                .forEach(owner -> {
                    owner.sendMessage("§6[AdminApproval] §e新的审批请求");
                    owner.sendMessage("§e编号:#" + request.id());
                    owner.sendMessage("§e申请人:" + request.requesterName());
                    owner.sendMessage("§e命令:" + request.command());
                });
    }

    private void notifyAdminCommand(PlayerCommandPreprocessEvent event, String message) {
        Player player = event.getPlayer();
        if (this.accessControl.isOwner(player)) {
            return;
        }
        boolean admin = this.accessControl.isAdmin(player);
        if (!this.notifyPlayerCommands && !(this.notifyAllAdminCommands && admin)) {
            return;
        }
        String label = labelOf(message);
        if (label != null && (label.equals("adminrequest") || label.equals("adminapprove")
                || label.equals("adminreject") || label.equals("adminrequests")
                || label.equals("adminhistory") || label.equals("adminapproval"))) {
            return;
        }
        String redacted = TelegramService.redactCommand(message, this.sensitiveCommands, this.sensitiveKeywords);
        if (admin && this.notifyAllAdminCommands) {
            this.telegramService.notifyAdminCommand(player.getName(), redacted);
        } else {
            this.telegramService.notifyPlayerCommand(player.getName(), redacted);
        }
    }

    private String labelOf(String commandLine) {
        if (commandLine == null) {
            return null;
        }
        String value = commandLine.trim();
        while (!value.isEmpty() && value.charAt(0) == '/') {
            value = value.substring(1);
        }
        if (value.isEmpty()) {
            return null;
        }
        int space = value.indexOf(' ');
        return space == -1 ? value.toLowerCase(Locale.ROOT) : value.substring(0, space).toLowerCase(Locale.ROOT);
    }
}
