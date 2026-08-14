package org.bukkit;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class Bukkit {
    private Bukkit() {
    }

    public static Server getServer() {
        throw new UnsupportedOperationException();
    }

    public static Collection<? extends Player> getOnlinePlayers() {
        throw new UnsupportedOperationException();
    }

    public static boolean dispatchCommand(CommandSender sender, String commandLine) {
        throw new UnsupportedOperationException();
    }

    public static CommandSender getConsoleSender() {
        throw new UnsupportedOperationException();
    }

    public static Player getPlayer(UUID uuid) {
        throw new UnsupportedOperationException();
    }
}
