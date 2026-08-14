package cn.fctweb.adminapproval;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

public final class DangerousCommandPolicy {
    private static final Set<String> DANGEROUS_DEFAULT = Set.of(
            "op", "deop", "stop", "restart", "reload", "ban", "pardon", "whitelist", "give", "item", "execute"
    );

    private final Set<String> dangerousCommands;
    private final Set<String> commandWhitelist;
    private final Supplier<CommandMap> commandMapSupplier;

    public DangerousCommandPolicy(Set<String> dangerousCommands, Set<String> initialWhitelist) {
        this(dangerousCommands, initialWhitelist, DangerousCommandPolicy::resolveCommandMap);
    }

    public DangerousCommandPolicy(Set<String> dangerousCommands, Set<String> initialWhitelist,
                                  Supplier<CommandMap> commandMapSupplier) {
        Set<String> dangerous = new LinkedHashSet<>();
        if (dangerousCommands == null || dangerousCommands.isEmpty()) {
            dangerous.addAll(DANGEROUS_DEFAULT);
        } else {
            for (String entry : dangerousCommands) {
                String normalized = normalizeLabel(entry);
                if (!normalized.isEmpty()) {
                    dangerous.add(normalized);
                }
            }
        }
        this.dangerousCommands = Collections.synchronizedSet(dangerous);
        this.commandWhitelist = Collections.synchronizedSet(new LinkedHashSet<>());
        if (initialWhitelist != null) {
            for (String entry : initialWhitelist) {
                String normalized = normalizeLabel(entry);
                if (!normalized.isEmpty()) {
                    this.commandWhitelist.add(normalized);
                }
            }
        }
        this.commandMapSupplier = commandMapSupplier == null ? DangerousCommandPolicy::resolveCommandMap : commandMapSupplier;
    }

    public boolean isDangerous(String fullCommandLine) {
        String label = extractLabel(fullCommandLine);
        if (label == null || isInternalCommand(label)) {
            return false;
        }

        String normalizedInput = normalizeLabel(label);
        if (this.dangerousCommands.contains(normalizedInput)) {
            return true;
        }

        String primary = resolvePrimaryLabel(label);
        return primary != null && this.dangerousCommands.contains(primary);
    }

    public boolean requiresApproval(String fullCommandLine) {
        if (!isDangerous(fullCommandLine)) {
            return false;
        }

        String label = extractLabel(fullCommandLine);
        if (label == null) {
            return false;
        }

        String normalizedInput = normalizeLabel(label);
        if (isWhitelisted(normalizedInput)) {
            return false;
        }

        String primary = resolvePrimaryLabel(label);
        return primary == null || !isWhitelisted(primary);
    }

    public boolean addWhitelist(String commandLabel) {
        String normalized = normalizeLabel(commandLabel);
        if (normalized.isEmpty()) {
            return false;
        }
        return this.commandWhitelist.add(normalized);
    }

    public boolean removeWhitelist(String commandLabel) {
        String normalized = normalizeLabel(commandLabel);
        if (normalized.isEmpty()) {
            return false;
        }
        return this.commandWhitelist.remove(normalized);
    }

    public Set<String> getCommandWhitelist() {
        synchronized (this.commandWhitelist) {
            return Set.copyOf(this.commandWhitelist);
        }
    }

    public List<String> listWhitelist() {
        synchronized (this.commandWhitelist) {
            return this.commandWhitelist.stream().sorted().toList();
        }
    }

    private boolean isWhitelisted(String commandLabel) {
        return commandLabel != null && !commandLabel.isEmpty() && this.commandWhitelist.contains(commandLabel);
    }

    private boolean isInternalCommand(String label) {
        String normalized = normalizeLabel(label);
        return normalized.equals("adminrequest")
                || normalized.equals("adminapprove")
                || normalized.equals("adminreject")
                || normalized.equals("adminrequests")
                || normalized.equals("adminhistory")
                || normalized.equals("adminapproval");
    }

    private String extractLabel(String fullCommandLine) {
        if (fullCommandLine == null) {
            return null;
        }

        String value = fullCommandLine.trim();
        if (value.isEmpty()) {
            return null;
        }

        if (value.charAt(0) == '/') {
            value = value.substring(1);
        }

        if (value.isEmpty()) {
            return null;
        }

        int firstSpace = value.indexOf(' ');
        if (firstSpace == -1) {
            return value;
        }
        return value.substring(0, firstSpace);
    }

    private String resolvePrimaryLabel(String label) {
        CommandMap commandMap = this.commandMapSupplier.get();
        if (commandMap == null) {
            return null;
        }

        String direct = label.toLowerCase(Locale.ROOT);
        Command command = commandMap.getCommand(direct);

        if (command == null) {
            command = commandMap.getCommand(normalizeLabel(label));
        }

        if (command == null) {
            return null;
        }

        return normalizeLabel(command.getName());
    }

    private static CommandMap resolveCommandMap() {
        try {
            Server server = Bukkit.getServer();
            Method method = server.getClass().getMethod("getCommandMap");
            Object value = method.invoke(server);
            if (value instanceof CommandMap commandMap) {
                return commandMap;
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }

    public static String normalizeLabel(String label) {
        if (label == null) {
            return "";
        }
        String normalized = label.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex >= 0 && separatorIndex < normalized.length() - 1) {
            return normalized.substring(separatorIndex + 1);
        }
        return normalized;
    }
}