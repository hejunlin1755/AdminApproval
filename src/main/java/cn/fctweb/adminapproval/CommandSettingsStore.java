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
            "op", "deop", "stop", "restart", "reload", "ban", "pardon", "whitelist", "give", "item", "execute"
    );
    public static final Set<String> DEFAULT_WHITELIST = Set.of("fill", "clone", "setblock");
    public static final Set<String> DEFAULT_BLOCKED_PATTERNS = Set.of(
            "set tnt", "replace tnt", "set lava", "replace lava",
            "set bedrock", "replace bedrock", "set command_block", "replace command_block",
            "set barrier", "replace barrier"
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
                return new CommandSettings(DEFAULT_DANGEROUS, DEFAULT_WHITELIST, DEFAULT_BLOCKED_PATTERNS);
            }
            Set<String> dangerous = parseList(root.get("dangerous"));
            if (dangerous.isEmpty()) {
                dangerous = DEFAULT_DANGEROUS;
            }
            return new CommandSettings(dangerous, parseList(root.get("whitelist")), parseList(root.get("blocked-patterns")));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + this.settingsFile, ex);
        }
    }

    public void save(CommandSettings settings) {
        Map<String, Object> root = new HashMap<>();
        root.put("dangerous", settings.dangerous().stream().sorted().toList());
        root.put("whitelist", settings.whitelist().stream().sorted().toList());
        root.put("blocked-patterns", settings.blockedPatterns().stream().sorted().toList());
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
        writeYaml(root);
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
