package org.bukkit.command;

public abstract class PluginCommand extends Command {
    public abstract void setExecutor(CommandExecutor executor);
}
