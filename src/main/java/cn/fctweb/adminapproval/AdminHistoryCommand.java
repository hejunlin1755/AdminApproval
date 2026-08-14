package cn.fctweb.adminapproval;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class AdminHistoryCommand implements CommandExecutor {
    private static final String PERMISSION_APPROVE = "adminapproval.approve";

    private final RequestStore requestStore;
    private final AccessControl accessControl;

    public AdminHistoryCommand(RequestStore requestStore, AccessControl accessControl) {
        this.requestStore = requestStore;
        this.accessControl = accessControl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!this.accessControl.isOwner(sender) && !sender.hasPermission(PERMISSION_APPROVE)) {
            sender.sendMessage("§c没有权限执行此命令。");
            return true;
        }

        int limit = 10;
        if (args.length > 0) {
            try {
                limit = Math.max(1, Math.min(50, Integer.parseInt(args[0])));
            } catch (NumberFormatException ex) {
                sender.sendMessage("§c数量必须是数字。");
                return true;
            }
        }

        List<ApprovalHistoryEntry> history = this.requestStore.listHistory();
        if (history.isEmpty()) {
            sender.sendMessage("§a暂无审批历史。");
            return true;
        }

        sender.sendMessage("§6审批历史:");
        Instant now = Instant.now();
        for (int i = 0; i < Math.min(limit, history.size()); i++) {
            ApprovalHistoryEntry entry = history.get(i);
            long minutes = Duration.between(entry.processedAt(), now).toMinutes();
            String result = entry.action().equals("REJECTED")
                    ? "§c已拒绝"
                    : (entry.executionSuccess() ? "§a已执行" : "§e执行失败");
            sender.sendMessage("§e#" + entry.requestId() + "§r " + entry.requesterName()
                    + " -> /" + entry.command()
                    + " | " + result + "§r"
                    + " | 处理人: " + entry.reviewerName()
                    + " §7(" + minutes + " 分钟前)§r");
        }
        return true;
    }
}