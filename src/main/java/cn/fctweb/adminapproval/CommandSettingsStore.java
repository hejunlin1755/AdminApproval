package cn.fctweb.adminapproval;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CommandSettingsStore {
    public static final Set<String> DEFAULT_DANGEROUS = Set.of(
            "op", "deop", "stop", "restart", "reload", "ban", "pardon", "whitelist", "give", "item", "execute",
            "gamemode", "gm", "gmc", "gms", "gma", "gmsp",
            "tp", "teleport", "tpo", "tphere", "tppos", "tpall"
    );
    public static final Set<String> DEFAULT_WHITELIST = Set.of("fill", "clone", "setblock");
    public static final Set<String> DEFAULT_BLOCKED_PATTERNS = Set.of(
            "set tnt", "replace tnt", "set lava", "replace lava",
            "set bedrock", "replace bedrock", "set command_block", "replace command_block",
            "set barrier", "replace barrier"
    );
    public static final Set<String> DEFAULT_SENSITIVE_COMMANDS = Set.of(
            "login", "register", "changepassword", "changeemail", "email",
            "authme", "unregister", "password", "pass", "token", "code", "recovery"
    );
    public static final Set<String> DEFAULT_SENSITIVE_KEYWORDS = Set.of(
            "password", "pass", "token", "密码", "密钥"
    );
    public static final Set<String> DEFAULT_ANTICHEAT_PATTERNS = Set.of(
            "(?i)\\[Vulcan\\].*(flag|violation)",
            "(?i)\\[Grim(AC)?\\].*(flag|violation)",
            "(?i)\\[HeuristicNoFall\\].*(flag|violation)",
            "(?i)\\[XrayDetect\\].*(detect|violation)"
    );

    private final Path settingsFile;

    public CommandSettingsStore(Path settingsFile) {
        this.settingsFile = settingsFile;
    }

    public CommandSettings load() {
        if (!Files.exists(this.settingsFile)) {
            writeDefaultFile();
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));

        try (InputStream input = Files.newInputStream(this.settingsFile)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> root)) {
                return new CommandSettings(DEFAULT_DANGEROUS, DEFAULT_WHITELIST, DEFAULT_BLOCKED_PATTERNS,
                        false, DEFAULT_SENSITIVE_COMMANDS, DEFAULT_SENSITIVE_KEYWORDS, false, false,
                        false, DEFAULT_ANTICHEAT_PATTERNS, false, "GOLD", "WHITE");
            }
            Set<String> dangerous = parseList(root.get("dangerous"));
            if (dangerous.isEmpty()) {
                dangerous = DEFAULT_DANGEROUS;
            }
            Set<String> sensitiveCommands = parseList(root.get("sensitive-commands"));
            if (sensitiveCommands.isEmpty()) {
                sensitiveCommands = DEFAULT_SENSITIVE_COMMANDS;
            }
            Set<String> sensitiveKeywords = parseList(root.get("sensitive-keywords"));
            if (sensitiveKeywords.isEmpty()) {
                sensitiveKeywords = DEFAULT_SENSITIVE_KEYWORDS;
            }
            return new CommandSettings(
                    dangerous,
                    parseList(root.get("whitelist")),
                    parseList(root.get("blocked-patterns")),
                    readBoolean(root.get("notify-all-admin-commands"), false),
                    sensitiveCommands,
                    sensitiveKeywords,
                    readBoolean(root.get("notify-player-commands"), false),
                    readBoolean(root.get("notify-join-leave"), false),
                    readBoolean(root.get("notify-anticheat"), false),
                    parseList(root.get("anticheat-patterns")),
                    readBoolean(root.get("tab-name-color-enabled"), false),
                    readString(root.get("tab-name-owner-color"), "GOLD"),
                    readString(root.get("tab-name-admin-color"), "WHITE")
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + this.settingsFile, ex);
        }
    }

    public void save(CommandSettings settings) {
        Map<String, Object> root = new HashMap<>();
        root.put("dangerous", settings.dangerous().stream().sorted().toList());
        root.put("whitelist", settings.whitelist().stream().sorted().toList());
        root.put("blocked-patterns", settings.blockedPatterns().stream().sorted().toList());
        root.put("notify-all-admin-commands", settings.notifyAllAdminCommands());
        root.put("sensitive-commands", settings.sensitiveCommands().stream().sorted().toList());
        root.put("sensitive-keywords", settings.sensitiveKeywords().stream().sorted().toList());
        root.put("notify-player-commands", settings.notifyPlayerCommands());
        root.put("notify-join-leave", settings.notifyJoinLeave());
        root.put("notify-anticheat", settings.notifyAntiCheat());
        root.put("anticheat-patterns", settings.antiCheatPatterns().stream().sorted().toList());
        root.put("tab-name-color-enabled", settings.tabNameColorEnabled());
        root.put("tab-name-owner-color", settings.tabNameOwnerColor());
        root.put("tab-name-admin-color", settings.tabNameAdminColor());
        writeYaml(root);
    }

    private Set<String> parseList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new LinkedHashSet<>();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = String.valueOf(item).trim().toLowerCase(Locale.ROOT);
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private void writeDefaultFile() {
        Map<String, Object> root = new HashMap<>();
        root.put("dangerous", DEFAULT_DANGEROUS.stream().sorted().toList());
        root.put("whitelist", DEFAULT_WHITELIST.stream().sorted().toList());
        root.put("blocked-patterns", DEFAULT_BLOCKED_PATTERNS.stream().sorted().toList());
        root.put("notify-all-admin-commands", false);
        root.put("sensitive-commands", DEFAULT_SENSITIVE_COMMANDS.stream().sorted().toList());
        root.put("sensitive-keywords", DEFAULT_SENSITIVE_KEYWORDS.stream().sorted().toList());
        root.put("notify-player-commands", false);
        root.put("notify-join-leave", false);
        root.put("notify-anticheat", false);
        root.put("anticheat-patterns", DEFAULT_ANTICHEAT_PATTERNS.stream().sorted().toList());
        root.put("tab-name-color-enabled", false);
        root.put("tab-name-owner-color", "GOLD");
        root.put("tab-name-admin-color", "WHITE");
        writeYaml(root);
    }

    private String readString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private void writeYaml(Map<String, Object> root) {
        try {
            Files.createDirectories(this.settingsFile.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create settings folder", ex);
        }

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);

        Yaml yaml = new Yaml(dumperOptions);
        try (Writer writer = Files.newBufferedWriter(this.settingsFile)) {
            yaml.dump(root, writer);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write " + this.settingsFile, ex);
        }
    }
}
