package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminRejectCommand implements CommandExecutor {
    private static final String PERMISSION_APPROVE = "adminapproval.approve";

    private final RequestStore requestStore;
    private final AccessControl accessControl;

    public AdminRejectCommand(RequestStore requestStore, AccessControl accessControl) {
        this.requestStore = requestStore;
        this.accessControl = accessControl;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!this.accessControl.isOwner(sender) && !sender.hasPermission(PERMISSION_APPROVE)) {
            sender.sendMessage("§c没有权限执行此命令。");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§e用法: /adminreject <审批编号>");
            return true;
        }

        int requestId;
        try {
            requestId = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c审批编号必须是数字。");
            return true;
        }

        ApprovalRequest request = this.requestStore.removePending(requestId);
        if (request == null) {
            sender.sendMessage("§c找不到待审批请求 #" + requestId);
            return true;
        }

        this.requestStore.recordRejected(request, sender.getName());
        sender.sendMessage("§e已拒绝审批请求 #" + request.id());

        Player requester = Bukkit.getPlayer(request.requesterId());
        if (requester != null) {
            requester.sendMessage("§c你的审批请求 #" + request.id() + " 已被拒绝。");
        }

        return true;
    }
}