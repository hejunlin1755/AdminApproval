package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminRequestCommand implements CommandExecutor {
    private final DangerousCommandPolicy policy;
    private final RequestStore requestStore;
    private final AccessControl accessControl;
    private final TelegramService telegramService;

    public AdminRequestCommand(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl) {
        this(policy, requestStore, accessControl, TelegramService.disabled());
    }

    public AdminRequestCommand(DangerousCommandPolicy policy, RequestStore requestStore, AccessControl accessControl,
                               TelegramService telegramService) {
        this.policy = policy;
        this.requestStore = requestStore;
        this.accessControl = accessControl;
        this.telegramService = telegramService == null ? TelegramService.disabled() : telegramService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c只有玩家可以提交审批请求。");
            return true;
        }

        if (this.accessControl.isOwner(player)) {
            player.sendMessage("§a你是服主，所有命令直接执行，无需审批。");
            return true;
        }

        if (!this.accessControl.isAdmin(player)) {
            player.sendMessage("§c你没有权限执行此命令。");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§e用法: /adminrequest <危险命令...>");
            return true;
        }

        String requested = String.join(" ", args).trim();
        if (requested.startsWith("/")) {
            requested = requested.substring(1);
        }

        if (!this.policy.isDangerous(requested)) {
            player.sendMessage("§a该命令不属于危险命令，无需审批。");
            return true;
        }

        if (!this.policy.requiresApproval(requested)) {
            player.sendMessage("§a该命令已在免审批白名单中，直接执行即可。");
            return true;
        }

        ApprovalRequest request = this.requestStore.create(player.getUniqueId(), player.getName(), requested);
        player.sendMessage("§a审批请求已提交，等待腐竹处理。编号:#" + request.id());
        this.telegramService.notifyApprovalRequest(request);

        Bukkit.getOnlinePlayers().stream()
                .filter(this.accessControl::isOwner)
                .forEach(online -> {
                    online.sendMessage("§6[AdminApproval] §e新的审批请求");
                    online.sendMessage("§e编号:#" + request.id());
                    online.sendMessage("§e申请人:" + request.requesterName());
                    online.sendMessage("§e命令:" + request.command());
                });

        return true;
    }
}
