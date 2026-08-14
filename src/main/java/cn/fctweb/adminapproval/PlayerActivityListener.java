package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * 玩家进服/退服推送到 Telegram（可开关）。
 */
public final class PlayerActivityListener implements Listener {
    private final TelegramService telegramService;
    private final boolean notifyJoinLeave;
    private final Supplier<Collection<? extends Player>> onlinePlayers;

    public PlayerActivityListener(TelegramService telegramService, boolean notifyJoinLeave) {
        this(telegramService, notifyJoinLeave, Bukkit::getOnlinePlayers);
    }

    public PlayerActivityListener(TelegramService telegramService, boolean notifyJoinLeave,
                                  Supplier<Collection<? extends Player>> onlinePlayers) {
        this.telegramService = telegramService == null ? TelegramService.disabled() : telegramService;
        this.notifyJoinLeave = notifyJoinLeave;
        this.onlinePlayers = onlinePlayers == null ? Bukkit::getOnlinePlayers : onlinePlayers;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!this.notifyJoinLeave || event.getPlayer() == null) {
            return;
        }
        this.telegramService.notifyJoinLeave(true, event.getPlayer().getName(), this.onlinePlayers.get().size());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!this.notifyJoinLeave || event.getPlayer() == null) {
            return;
        }
        this.telegramService.notifyJoinLeave(false, event.getPlayer().getName(), this.onlinePlayers.get().size());
    }
}
