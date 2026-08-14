package cn.fctweb.adminapproval;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class AccessControl {
    private final Set<UUID> ownerUuids;

    public AccessControl(Set<UUID> ownerUuids) {
        this.ownerUuids = ownerUuids == null ? Set.of() : Set.copyOf(ownerUuids);
    }

    public boolean isOwner(CommandSender sender) {
        return sender instanceof Player player && isOwner(player);
    }

    public boolean isOwner(Player player) {
        return player != null && this.ownerUuids.contains(player.getUniqueId());
    }

    public boolean isAdmin(Player player) {
        return isOwner(player) || (player != null && player.isOp());
    }

    public Set<UUID> ownerUuids() {
        return this.ownerUuids;
    }
}