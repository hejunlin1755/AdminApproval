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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ApprovalConfigStore {
    private final Path configFile;

    public ApprovalConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    public ApprovalConfig load() {
        if (!Files.exists(this.configFile)) {
            writeDefaultFile();
        }

        LoaderOptions loaderOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));

        try (InputStream input = Files.newInputStream(this.configFile)) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> root)) {
                return new ApprovalConfig(Set.of());
            }
            return new ApprovalConfig(parseOwners(root.get("owner-uuid")));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + this.configFile, ex);
        }
    }

    public void save(ApprovalConfig config) {
        Map<String, Object> root = new HashMap<>();
        List<String> owners = config.ownerUuids().stream().map(UUID::toString).sorted().toList();
        root.put("owner-uuid", owners);
        writeYaml(root);
    }

    private Set<UUID> parseOwners(Object value) {
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }

        Set<UUID> owners = new HashSet<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            try {
                owners.add(UUID.fromString(String.valueOf(item).trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Set.copyOf(owners);
    }

    private void writeDefaultFile() {
        Map<String, Object> root = new HashMap<>();
        root.put("owner-uuid", List.of("填写服主UUID"));
        writeYaml(root);
    }

    private void writeYaml(Map<String, Object> root) {
        try {
            Files.createDirectories(this.configFile.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create config folder", ex);
        }

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);

        Yaml yaml = new Yaml(dumperOptions);
        try (Writer writer = Files.newBufferedWriter(this.configFile)) {
            yaml.dump(root, writer);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write " + this.configFile, ex);
        }
    }
}