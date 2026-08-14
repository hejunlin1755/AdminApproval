package org.bukkit.plugin.java;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;

import java.io.File;
import java.util.logging.Logger;

public abstract class JavaPlugin {
    public void onEnable() {
    }

    public void onDisable() {
    }

    public Server getServer() {
        throw new UnsupportedOperationException();
    }

    public PluginCommand getCommand(String name) {
        throw new UnsupportedOperationException();
    }

    public File getDataFolder() {
        throw new UnsupportedOperationException();
    }

    public Logger getLogger() {
        throw new UnsupportedOperationException();
    }
}
