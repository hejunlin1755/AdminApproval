package org.bukkit.command;

public interface CommandSender {
    boolean hasPermission(String permission);

    void sendMessage(String message);

    String getName();
}
