package cn.fctweb.adminapproval;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class AdminApprovalCommand implements CommandExecutor {
    private final AccessControl accessControl;
    private final DangerousCommandPolicy policy;
    private final Runnable onWhitelistChanged;

    public AdminApprovalCommand(AccessControl accessControl, DangerousCommandPolicy policy, Runnable onWhitelistChanged) {
        this.accessControl = accessControl;
        this.policy = policy;
        this.onWhitelistChanged = onWhitelistChanged == null ? () -> {
        } : onWhitelistChanged;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !this.accessControl.isOwner(player)) {
            sender.sendMessage("§c只有服主(owner)可以使用此命令。");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("whitelist")) {
            sender.sendMessage("§e用法: /adminapproval whitelist <add|remove|list> [命令]");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage("§e用法: /adminapproval whitelist add <命令>");
                    return true;
                }
                String normalized = DangerousCommandPolicy.normalizeLabel(args[2]);
                if (this.policy.addWhitelist(normalized)) {
                    sender.sendMessage("§a已将命令加入免审批白名单: " + normalized);
                    this.onWhitelistChanged.run();
                } else {
                    sender.sendMessage("§e命令已在白名单中或无效: " + normalized);
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§e用法: /adminapproval whitelist remove <命令>");
                    return true;
                }
                String normalized = DangerousCommandPolicy.normalizeLabel(args[2]);
                if (this.policy.removeWhitelist(normalized)) {
                    sender.sendMessage("§a已从免审批白名单移除: " + normalized);
                    this.onWhitelistChanged.run();
                } else {
                    sender.sendMessage("§e命令不在白名单中: " + normalized);
                }
            }
            case "list" -> {
                List<String> whitelist = this.policy.listWhitelist();
                sender.sendMessage("§6当前免审批命令:");
                if (whitelist.isEmpty()) {
                    sender.sendMessage("§7(空)");
                } else {
                    whitelist.forEach(entry -> sender.sendMessage("§e- " + entry));
                }
            }
            default -> sender.sendMessage("§e用法: /adminapproval whitelist <add|remove|list> [命令]");
        }
        return true;
    }
}