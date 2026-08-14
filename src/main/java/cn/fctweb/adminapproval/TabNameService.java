package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Tab 玩家列表名字颜色：用 Paper 的 playerListName(Component) 覆盖原版「OP 名字红色」，
 * 服主与管理员可分别配置颜色（如管理员用 WHITE 去掉红色）。
 */
public final class TabNameService {
    private final AccessControl accessControl;
    private final boolean enabled;
    private final String ownerColor;
    private final String adminColor;
    private final Supplier<Collection<? extends Player>> onlinePlayers;

    public TabNameService(AccessControl accessControl, boolean enabled, String ownerColor, String adminColor) {
        this(accessControl, enabled, ownerColor, adminColor, Bukkit::getOnlinePlayers);
    }

    public TabNameService(AccessControl accessControl, boolean enabled, String ownerColor, String adminColor,
                          Supplier<Collection<? extends Player>> onlinePlayers) {
        this.accessControl = accessControl;
        this.enabled = enabled;
        this.ownerColor = ownerColor == null ? "GOLD" : ownerColor;
        this.adminColor = adminColor == null ? "WHITE" : adminColor;
        this.onlinePlayers = onlinePlayers == null ? Bukkit::getOnlinePlayers : onlinePlayers;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void applyAll() {
        if (!this.enabled) {
            return;
        }
        for (Player player : this.onlinePlayers.get()) {
            apply(player);
        }
    }

    public void apply(Player player) {
        if (!this.enabled || player == null) {
            return;
        }
        String color;
        if (this.accessControl.isOwner(player)) {
            color = this.ownerColor;
        } else if (this.accessControl.isAdmin(player)) {
            color = this.adminColor;
        } else {
            return;
        }
        if (color == null || color.isEmpty() || "NONE".equalsIgnoreCase(color)) {
            return;
        }
        try {
            Object component = buildComponent(player.getName(), color);
            if (component == null) {
                return;
            }
            Method method = player.getClass().getMethod("playerListName", component.getClass());
            method.invoke(player, component);
        } catch (Exception ignored) {
        }
    }

    private static Object buildComponent(String name, String colorName) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> namedColorClass = Class.forName("net.kyori.adventure.text.format.NamedTextColor");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object color = Enum.valueOf((Class<Enum>) namedColorClass, colorName.toUpperCase(Locale.ROOT));
            Object component = componentClass.getMethod("text", String.class).invoke(null, name);
            return componentClass.getMethod("color", namedColorClass).invoke(component, color);
        } catch (Exception ignored) {
            return null;
        }
    }
}
