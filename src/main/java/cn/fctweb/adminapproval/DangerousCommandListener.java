package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Collection;
import java.util.function.Supplier;

public final class DangerousCommandListener implements Listener {
    private final DangerousCommandPolicy policy;
    private final RequestStore requestStore;
    private final AccessControl accessControl;
    private final Supplier<Collection<? extends Player>> onlinePlayers;
    private final TelegramService telegramService;

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl) {
        this(policy, requestStore, accessControl, Bukkit::getOnlinePlayers, TelegramService.disabled());
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    Supplier<Collection<? extends Player>> onlinePlayers) {
        this(policy, requestStore, accessControl, onlinePlayers, TelegramService.disabled());
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    TelegramService telegramService) {
        this(policy, requestStore, accessControl, Bukkit::getOnlinePlayers, telegramService);
    }

    public DangerousCommandListener(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                                    Supplier<Collection<? extends Player>> onlinePlayers,
                                    TelegramService telegramService) {
        this.policy = policy;
        this.requestStore = requestStore;
        this.accessControl = accessControl;
        this.onlinePlayers = onlinePlayers == null ? Bukkit::getOnlinePlayers : onlinePlayers;
        this.telegramService = telegramService == null ? TelegramService.disabled() : telegramService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage() == null ? "" : event.getMessage();
        if (!this.policy.requiresApproval(message)) {
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
}
