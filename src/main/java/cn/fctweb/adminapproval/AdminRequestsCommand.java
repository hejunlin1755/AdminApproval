package cn.fctweb.adminapproval;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class AdminRequestsCommand implements CommandExecutor {
    private static final String PERMISSION_APPROVE = "adminapproval.approve";

    private final RequestStore requestStore;
    private final AccessControl accessControl;

    public AdminRequestsCommand(RequestStore requestStore, AccessControl accessControl) {
        this.requestStore = requestStore;
        this.accessControl = accessControl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!this.accessControl.isOwner(sender) && !sender.hasPermission(PERMISSION_APPROVE)) {
            sender.sendMessage("§c没有权限执行此命令。");
            return true;
        }

        List<ApprovalRequest> requests = this.requestStore.listPending();
        if (requests.isEmpty()) {
            sender.sendMessage("§a当前没有待处理的审批请求。");
            return true;
        }

        sender.sendMessage("§6当前待审批请求:");
        Instant now = Instant.now();
        for (ApprovalRequest request : requests) {
            long minutes = Duration.between(request.createdAt(), now).toMinutes();
            sender.sendMessage("§e#" + request.id() + "§r " + request.requesterName()
                    + " -> /" + request.command() + " §7(" + minutes + " 分钟前)§r");
        }
        return true;
    }
}