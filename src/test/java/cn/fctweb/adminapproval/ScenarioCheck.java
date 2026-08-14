package cn.fctweb.adminapproval;

import com.google.gson.JsonObject;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 场景自检程序（不依赖真实 Paper 服务器，仅验证审批核心逻辑）：
 * 场景1: 管理员OP执行 /fill -> 直接执行（无需审批）
 * 场景2: 管理员OP执行 /give -> 生成审批请求
 * 场景3: 腐竹批准 /give -> 控制台执行
 * 场景4: 普通管理员尝试管理白名单 -> 拒绝
 */
public final class ScenarioCheck {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        UUID ownerUuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID adminUuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        FakePlayer owner = new FakePlayer(ownerUuid, "Owner", true);
        FakePlayer admin = new FakePlayer(adminUuid, "Steve", true);
        List<Player> online = List.of(owner, admin);

        AccessControl accessControl = new AccessControl(Set.of(ownerUuid));

        Map<String, Command> commandEntries = new LinkedHashMap<>();
        commandEntries.put("fill", new FakeCommand("fill"));
        commandEntries.put("clone", new FakeCommand("clone"));
        commandEntries.put("setblock", new FakeCommand("setblock"));
        commandEntries.put("give", new FakeCommand("give"));
        FakeCommandMap fakeCommandMap = new FakeCommandMap(commandEntries);

        Set<String> dangerous = Set.of("op", "deop", "stop", "restart", "reload",
                "ban", "pardon", "whitelist", "give", "item", "execute");
        Set<String> whitelist = Set.of("fill", "clone", "setblock");
        DangerousCommandPolicy policy = new DangerousCommandPolicy(dangerous, whitelist, () -> fakeCommandMap);

        // ---------- 场景1：管理员 OP 执行 /fill -> 直接执行 ----------
        check("场景1: /fill 免审批直接执行", !policy.requiresApproval("/fill"), null);
        check("场景1b: /minecraft:fill 免审批直接执行", !policy.requiresApproval("/minecraft:fill"), null);
        check("场景1c: /clone /setblock 同样免审批",
                !policy.requiresApproval("/clone") && !policy.requiresApproval("/setblock"), null);
        check("场景1d: 普通命令 /say 不受影响", !policy.requiresApproval("/say hello"), null);

        // ---------- 防绕过：/minecraft:give 不能绕过拦截 ----------
        check("防绕过: /minecraft:give 需要审批", policy.requiresApproval("/minecraft:give"), null);
        check("防绕过: /give Steve diamond 64 需要审批", policy.requiresApproval("/give Steve diamond 64"), null);

        // ---------- 场景2：管理员 OP 执行 /give -> 生成审批请求 ----------
        RequestStore requestStore = new RequestStore(500);
        DangerousCommandListener listener = new DangerousCommandListener(policy, requestStore, accessControl, () -> online);
        FakeCommandEvent event = new FakeCommandEvent(admin, "/give Steve diamond 64");
        listener.onPlayerCommand(event);
        check("场景2: /give 事件被取消（未直接执行）", event.isCancelled(), null);
        check("场景2: 生成了审批请求", requestStore.listPending().size() == 1,
                "pending=" + requestStore.listPending());
        if (!requestStore.listPending().isEmpty()) {
            ApprovalRequest request = requestStore.listPending().get(0);
            check("场景2: 审批编号格式 #" + request.id(), ("#" + request.id()).startsWith("#"), null);
            check("场景2: 申请人 Steve", "Steve".equals(request.requesterName()), request.requesterName());
            check("场景2: 命令 give Steve diamond 64", "give Steve diamond 64".equals(request.command()), request.command());
        }
        check("场景2: 腐竹收到编号:# 通知", owner.messages.stream().anyMatch(m -> m.contains("编号:#")),
                owner.messages.toString());

        // ---------- 场景3：腐竹批准 /give -> 控制台执行 ----------
        List<String> dispatched = new ArrayList<>();
        AdminApproveCommand approve = new AdminApproveCommand(requestStore, accessControl,
                cmd -> {
                    dispatched.add(cmd);
                    return true;
                },
                uuid -> uuid.equals(adminUuid) ? admin : null);
        int requestId = requestStore.listPending().get(0).id();
        approve.onCommand(owner, null, "adminapprove", new String[]{String.valueOf(requestId)});
        check("场景3: 控制台执行了 give 命令", dispatched.contains("give Steve diamond 64"), dispatched.toString());
        check("场景3: 待审批请求已移除", requestStore.listPending().isEmpty(), requestStore.listPending().toString());
        check("场景3: 审批历史已记录 APPROVED",
                requestStore.listHistory().stream().anyMatch(e -> "APPROVED".equals(e.action())),
                requestStore.listHistory().toString());
        check("场景3: 申请人收到已批准通知",
                admin.messages.stream().anyMatch(m -> m.contains("已被批准")), admin.messages.toString());

        // ---------- 防绕过：管理员不能批准自己的请求 ----------
        RequestStore store2 = new RequestStore(500);
        DangerousCommandListener listener2 = new DangerousCommandListener(policy, store2, accessControl, () -> online);
        FakeCommandEvent event2 = new FakeCommandEvent(admin, "/give Steve diamond 64");
        listener2.onPlayerCommand(event2);
        ApprovalRequest ownRequest = store2.listPending().get(0);
        List<String> dispatched2 = new ArrayList<>();
        AdminApproveCommand approve2 = new AdminApproveCommand(store2, accessControl,
                cmd -> {
                    dispatched2.add(cmd);
                    return true;
                },
                uuid -> uuid.equals(adminUuid) ? admin : null);
        admin.messages.clear();
        approve2.onCommand(admin, null, "adminapprove", new String[]{String.valueOf(ownRequest.id())});
        check("防绕过: 管理员不能批准自己的请求（未执行命令）", dispatched2.isEmpty(), dispatched2.toString());
        check("防绕过: 自己的请求仍在待审批列表", store2.listPending().size() == 1, store2.listPending().toString());

        // ---------- 场景4：普通管理员尝试管理白名单 -> 拒绝 ----------
        AdminApprovalCommand whitelistCommand = new AdminApprovalCommand(accessControl, policy, () -> { });
        admin.messages.clear();
        whitelistCommand.onCommand(admin, null, "adminapproval", new String[]{"whitelist", "add", "give"});
        check("场景4: 普通管理员被拒绝管理白名单",
                admin.messages.stream().anyMatch(m -> m.contains("只有服主")), admin.messages.toString());
        check("场景4: 白名单未被管理员修改", !policy.listWhitelist().contains("give"),
                policy.listWhitelist().toString());

        // ---------- 腐竹管理白名单 ----------
        owner.messages.clear();
        whitelistCommand.onCommand(owner, null, "adminapproval", new String[]{"whitelist", "add", "give"});
        check("腐竹: whitelist add give 成功", policy.listWhitelist().contains("give"),
                policy.listWhitelist().toString());
        check("腐竹: give 加入白名单后不再审批", !policy.requiresApproval("/give Steve diamond 64"), null);
        check("腐竹: minecraft:give 加入白名单后不再审批", !policy.requiresApproval("/minecraft:give"), null);

        whitelistCommand.onCommand(owner, null, "adminapproval", new String[]{"whitelist", "remove", "give"});
        check("腐竹: whitelist remove give 成功", !policy.listWhitelist().contains("give"),
                policy.listWhitelist().toString());
        check("腐竹: 移除后 give 恢复审批", policy.requiresApproval("/give Steve diamond 64"), null);

        owner.messages.clear();
        whitelistCommand.onCommand(owner, null, "adminapproval", new String[]{"whitelist", "list"});
        check("腐竹: whitelist list 显示当前免审批命令",
                owner.messages.stream().anyMatch(m -> m.contains("当前免审批命令")), owner.messages.toString());

        // ---------- 升级路径：data.yml 尚未配置 whitelist 时返回 null，由插件用 command-settings 种子补齐 ----------
        Path freshDir = Files.createTempDirectory("adminapproval-fresh");
        try {
            DataFileStore freshStore = new DataFileStore(freshDir.resolve("data.yml"));
            check("升级路径: 无 data.yml 时 whitelist 为 null（走 command-settings 种子）",
                    freshStore.load().whitelist() == null, String.valueOf(freshStore.load().whitelist()));
        } finally {
            deleteRecursively(freshDir);
        }

        // ---------- data.yml 白名单持久化往返 ----------
        Path tempDir = Files.createTempDirectory("adminapproval-check");
        try {
            DataFileStore dataFileStore = new DataFileStore(tempDir.resolve("data.yml"));
            StoreSnapshot snap = new StoreSnapshot(42, List.of(), List.of(), Set.of("fill", "clone", "setblock"));
            dataFileStore.save(snap);
            StoreSnapshot loaded = dataFileStore.load();
            check("持久化: data.yml whitelist 往返一致",
                    loaded.whitelist().equals(Set.of("fill", "clone", "setblock")), loaded.whitelist().toString());
            check("持久化: next-id 往返一致", loaded.nextId() == 42, String.valueOf(loaded.nextId()));
        } finally {
            deleteRecursively(tempDir);
        }

        // ---------- Telegram：配置往返 + 通知格式 ----------
        Path telegramDir = Files.createTempDirectory("adminapproval-telegram");
        try {
            ApprovalConfigStore configStore = new ApprovalConfigStore(telegramDir.resolve("config.yml"));
            TelegramSettings tg = new TelegramSettings(true, "123456:TESTTOKEN", "987654321");
            configStore.save(new ApprovalConfig(Set.of(ownerUuid), tg));
            ApprovalConfig loadedConfig = configStore.load();
            check("Telegram: 配置往返一致",
                    loadedConfig.telegram().enabled()
                            && "123456:TESTTOKEN".equals(loadedConfig.telegram().botToken())
                            && "987654321".equals(loadedConfig.telegram().chatId()),
                    loadedConfig.telegram().toString());
        } finally {
            deleteRecursively(telegramDir);
        }

        ApprovalRequest tgRequest = new ApprovalRequest(1001, adminUuid, "Steve", "give Steve diamond 64",
                java.time.Instant.now());
        String tgText = TelegramService.formatApprovalRequest(tgRequest);
        check("Telegram: 通知含 编号:#1001", tgText.contains("编号:#1001"), tgText);
        check("Telegram: 通知含 申请人:Steve", tgText.contains("申请人:Steve"), tgText);
        check("Telegram: 通知含 命令:give Steve diamond 64", tgText.contains("命令:give Steve diamond 64"), tgText);
        check("Telegram: 提示回复 /approve 1001", tgText.contains("/approve 1001"), tgText);

        // ---------- FAWE 禁用模式（//set tnt 等） ----------
        DangerousCommandPolicy fawePolicy = new DangerousCommandPolicy(dangerous, whitelist,
                Set.of("set tnt", "replace tnt", "set lava", "replace lava"), () -> fakeCommandMap);
        check("FAWE: //set tnt 被拦截需审批", fawePolicy.requiresApproval("//set tnt"), null);
        check("FAWE: //set tnt 2 也被拦截", fawePolicy.requiresApproval("//set tnt 2"), null);
        check("FAWE: //replace tnt 被拦截", fawePolicy.requiresApproval("//replace tnt"), null);
        check("FAWE: //set lava 被拦截", fawePolicy.requiresApproval("//set lava"), null);
        check("FAWE: //set stone 不受影响", !fawePolicy.requiresApproval("//set stone"), null);
        check("FAWE: //set grass 不受影响", !fawePolicy.requiresApproval("//set grass"), null);
        check("FAWE: //pos1 不受影响", !fawePolicy.requiresApproval("//pos1"), null);
        check("FAWE: 管理员不能直接执行 //set tnt（命中禁用模式）",
                fawePolicy.matchesBlockedPattern("//set tnt"), null);

        // ---------- Telegram 内联批准/拒绝按钮 ----------
        JsonObject tgPayload = TelegramService.buildRequestPayload("123", tgRequest);
        String tgPayloadJson = tgPayload.toString();
        check("TG按钮: 通知含 inline_keyboard", tgPayloadJson.contains("inline_keyboard"), null);
        check("TG按钮: 有批准按钮 approve:1001", tgPayloadJson.contains("\"approve:1001\""), tgPayloadJson);
        check("TG按钮: 有拒绝按钮 reject:1001", tgPayloadJson.contains("\"reject:1001\""), tgPayloadJson);

        System.out.println(failures == 0 ? "ALL SCENARIOS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            System.out.println("[PASS] " + name);
        } else {
            failures++;
            System.out.println("[FAIL] " + name + (detail == null ? "" : " :: " + detail));
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static final class FakeCommand extends Command {
        private final String name;

        FakeCommand(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    private static final class FakeCommandMap implements CommandMap {
        private final Map<String, Command> commands;

        FakeCommandMap(Map<String, Command> commands) {
            this.commands = commands;
        }

        @Override
        public Command getCommand(String name) {
            return this.commands.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        }
    }

    private static final class FakePlayer implements Player {
        private final UUID uuid;
        private final String name;
        private final boolean op;
        private final List<String> messages = new ArrayList<>();

        FakePlayer(UUID uuid, String name, boolean op) {
            this.uuid = uuid;
            this.name = name;
            this.op = op;
        }

        @Override
        public UUID getUniqueId() {
            return this.uuid;
        }

        @Override
        public boolean isOp() {
            return this.op;
        }

        @Override
        public boolean hasPermission(String permission) {
            return this.op || "adminapproval.approve".equals(permission);
        }

        @Override
        public void sendMessage(String message) {
            this.messages.add(message);
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    private static final class FakeCommandEvent extends PlayerCommandPreprocessEvent {
        private final Player player;
        private final String message;
        private boolean cancelled;

        FakeCommandEvent(Player player, String message) {
            this.player = player;
            this.message = message;
        }

        @Override
        public String getMessage() {
            return this.message;
        }

        @Override
        public Player getPlayer() {
            return this.player;
        }

        @Override
        public void setCancelled(boolean cancel) {
            this.cancelled = cancel;
        }

        boolean isCancelled() {
            return this.cancelled;
        }
    }
}
