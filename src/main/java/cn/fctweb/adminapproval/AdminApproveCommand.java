package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Function;

public final class AdminApproveCommand implements CommandExecutor {
    private static final String PERMISSION_APPROVE = "adminapproval.approve";

    private final RequestStore requestStore;
    private final AccessControl accessControl;
    private final Function<String, Boolean> dispatcher;
    private final Function<UUID, Player> playerLookup;

    public AdminApproveCommand(RequestStore requestStore, AccessControl accessControl) {
        this(requestStore, accessControl,
                commandLine -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandLine),
                Bukkit::getPlayer);
    }

    public AdminApproveCommand(RequestStore requestStore, AccessControl accessControl,
                               Function<String, Boolean> dispatcher, Function<UUID, Player> playerLookup) {
        this.requestStore = requestStore;
        this.accessControl = accessControl;
        this.dispatcher = dispatcher == null ? commandLine -> false : dispatcher;
        this.playerLookup = playerLookup == null ? uuid -> null : playerLookup;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!this.accessControl.isOwner(sender) && !sender.hasPermission(PERMISSION_APPROVE)) {
            sender.sendMessage("§c没有权限执行此命令。");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§e用法: /adminapprove <审批编号>");
            return true;
        }

        int requestId;
        try {
            requestId = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("§c审批编号必须是数字。");
            return true;
        }

        ApprovalRequest request = this.requestStore.getPending(requestId);
        if (request == null) {
            sender.sendMessage("§c找不到待审批请求 #" + requestId);
            return true;
        }

        if (sender instanceof Player player
                && request.requesterId().equals(player.getUniqueId())
                && !this.accessControl.isOwner(player)) {
            sender.sendMessage("§c管理员不能批准自己的审批请求。");
            return true;
        }

        this.requestStore.removePending(requestId);

        boolean success = this.dispatcher.apply(request.command());
        this.requestStore.recordApproved(request, sender.getName(), success);

        if (success) {
            sender.sendMessage("§a审批请求 #" + request.id() + " 已批准，命令已由控制台执行: /" + request.command());
        } else {
            sender.sendMessage("§e审批请求 #" + request.id() + " 已批准，但命令执行失败。");
        }

        Player requester = this.playerLookup.apply(request.requesterId());
        if (requester != null) {
            if (success) {
                requester.sendMessage("§a你的审批请求 #" + request.id() + " 已被批准并执行。");
            } else {
                requester.sendMessage("§e你的审批请求 #" + request.id() + " 已被批准，但执行失败。");
            }
        }
        return true;
    }
}