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
                return new ApprovalConfig(Set.of(), TelegramSettings.disabled());
            }
            return new ApprovalConfig(parseOwners(root.get("owner-uuid")), parseTelegram(root.get("telegram")));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load " + this.configFile, ex);
        }
    }

    public void save(ApprovalConfig config) {
        Map<String, Object> root = new HashMap<>();
        List<String> owners = config.ownerUuids().stream().map(UUID::toString).sorted().toList();
        root.put("owner-uuid", owners);
        root.put("telegram", telegramMap(config.telegram()));
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

    private TelegramSettings parseTelegram(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return TelegramSettings.disabled();
        }
        boolean enabled = readBoolean(map.get("enabled"), false);
        String botToken = readString(map.get("bot-token"));
        String chatId = readString(map.get("chat-id"));
        return new TelegramSettings(enabled, botToken, chatId);
    }

    private Map<String, Object> telegramMap(TelegramSettings telegram) {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", telegram.enabled());
        map.put("bot-token", telegram.botToken());
        map.put("chat-id", telegram.chatId());
        return map;
    }

    private void writeDefaultFile() {
        Map<String, Object> root = new HashMap<>();
        root.put("owner-uuid", List.of("填写服主UUID"));

        Map<String, Object> telegram = new HashMap<>();
        telegram.put("enabled", false);
        telegram.put("bot-token", "填写BotToken");
        telegram.put("chat-id", "填写接收消息的Telegram用户/群ID");
        root.put("telegram", telegram);

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

    private boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private String readString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
