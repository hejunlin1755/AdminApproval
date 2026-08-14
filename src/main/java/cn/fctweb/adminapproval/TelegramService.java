package cn.fctweb.adminapproval;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Telegram Bot 集成：
 * - 新的审批请求推送给服主
 * - 服主在聊天里回复 /approve &lt;编号&gt; 或 /reject &lt;编号&gt; 即可处理（校验 chat-id，防他人冒充）
 */
public final class TelegramService {
    private static final String API = "https://api.telegram.org/bot";

    private final String botToken;
    private final String chatId;
    private final boolean enabled;
    private final RequestStore requestStore;
    private final Function<String, Boolean> dispatcher;
    private final Function<UUID, Player> playerLookup;
    private final Logger logger;
    private final HttpClient http;

    private volatile boolean running;
    private Thread pollerThread;
    private int lastUpdateId;

    public TelegramService(TelegramSettings settings, RequestStore requestStore,
                           Function<String, Boolean> dispatcher,
                           Function<UUID, Player> playerLookup,
                           Logger logger) {
        this.botToken = settings == null ? "" : settings.botToken();
        this.chatId = settings == null ? "" : settings.chatId();
        this.enabled = settings != null && settings.enabled()
                && !this.botToken.isEmpty() && !this.chatId.isEmpty();
        this.requestStore = requestStore;
        this.dispatcher = dispatcher == null ? commandLine -> false : dispatcher;
        this.playerLookup = playerLookup == null ? uuid -> null : playerLookup;
        this.logger = logger;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public static TelegramService disabled() {
        return new TelegramService(TelegramSettings.disabled(), null, null, null, null);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void start() {
        if (!this.enabled) {
            return;
        }
        this.running = true;
        this.pollerThread = new Thread(this::pollLoop, "AdminApproval-Telegram");
        this.pollerThread.setDaemon(true);
        this.pollerThread.start();
        this.logger.info("Telegram 审批通知已启用");
    }

    public void stop() {
        this.running = false;
        if (this.pollerThread != null) {
            this.pollerThread.interrupt();
        }
    }

    public void notifyApprovalRequest(ApprovalRequest request) {
        if (!this.enabled || request == null) {
            return;
        }
        sendMessageAsync(formatApprovalRequest(request));
    }

    public static String formatApprovalRequest(ApprovalRequest request) {
        return "[AdminApproval] 新的审批请求\n"
                + "编号:#" + request.id() + "\n"
                + "申请人:" + request.requesterName() + "\n"
                + "命令:" + request.command() + "\n"
                + "回复 /approve " + request.id() + " 批准，/reject " + request.id() + " 拒绝";
    }

    private void sendMessageAsync(String text) {
        String url = API + this.botToken + "/sendMessage";
        String body = "chat_id=" + enc(this.chatId) + "&text=" + enc(text);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        this.http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        this.logger.warning("Telegram sendMessage 失败: " + err.getMessage());
                    } else if (resp.statusCode() != 200) {
                        this.logger.warning("Telegram sendMessage HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                });
    }

    private void pollLoop() {
        int conflictStreak = 0;
        while (this.running) {
            try {
                JsonObject updates = fetchUpdates();
                if (updates != null && updates.has("ok") && updates.get("ok").getAsBoolean()) {
                    conflictStreak = 0;
                    JsonArray result = updates.getAsJsonArray("result");
                    for (JsonElement element : result) {
                        handleUpdate(element.getAsJsonObject());
                    }
                }
            } catch (InterruptedException ie) {
                return;
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? "" : ex.getMessage();
                if (message.contains("409") || message.contains("Conflict")) {
                    conflictStreak++;
                    if (conflictStreak >= 5) {
                        this.logger.warning("Telegram getUpdates 持续冲突(409)，已停止轮询（可能有其他客户端在轮询同一 bot）");
                        return;
                    }
                } else {
                    conflictStreak = 0;
                    this.logger.warning("Telegram getUpdates 失败: " + message);
                }
                sleepSafe(3000);
            }
        }
    }

    private JsonObject fetchUpdates() throws Exception {
        String url = API + this.botToken + "/getUpdates?timeout=50&offset=" + (this.lastUpdateId + 1);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(70))
                .GET()
                .build();
        HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + resp.body());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private void handleUpdate(JsonObject update) {
        if (!update.has("update_id")) {
            return;
        }
        int updateId = update.get("update_id").getAsInt();
        if (updateId > this.lastUpdateId) {
            this.lastUpdateId = updateId;
        }

        JsonElement messageElement = update.get("message");
        if (messageElement == null || !messageElement.isJsonObject()) {
            return;
        }
        JsonObject message = messageElement.getAsJsonObject();
        JsonElement chatElement = message.get("chat");
        if (chatElement == null || !chatElement.isJsonObject()) {
            return;
        }
        JsonObject chat = chatElement.getAsJsonObject();
        if (!chat.has("id")) {
            return;
        }

        long senderChatId = chat.get("id").getAsLong();
        if (!String.valueOf(senderChatId).equals(this.chatId)) {
            return;
        }

        JsonElement textElement = message.get("text");
        if (textElement != null && textElement.isJsonPrimitive()) {
            handleOwnerCommand(textElement.getAsString().trim());
        }
    }

    private void handleOwnerCommand(String text) {
        if (text.isEmpty()) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int id = parseId(text);
        if (lower.startsWith("/approve ") && id > 0) {
            approve(id);
            return;
        }
        if (lower.startsWith("/reject ") && id > 0) {
            reject(id);
            return;
        }
        sendMessageAsync("用法: /approve <编号> 或 /reject <编号>");
    }

    private int parseId(String text) {
        String[] parts = text.split("\\s+");
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void approve(int id) {
        ApprovalRequest request = this.requestStore.getPending(id);
        if (request == null) {
            sendMessageAsync("找不到待审批请求 #" + id);
            return;
        }
        this.requestStore.removePending(id);
        boolean success = this.dispatcher.apply(request.command());
        this.requestStore.recordApproved(request, "Telegram", success);
        sendMessageAsync("审批请求 #" + id
                + (success ? " 已批准，命令已由控制台执行: /" + request.command() : " 已批准，但命令执行失败"));

        Player requester = this.playerLookup.apply(request.requesterId());
        if (requester != null) {
            if (success) {
                requester.sendMessage("§a你的审批请求 #" + id + " 已被批准并执行（Telegram）。");
            } else {
                requester.sendMessage("§e你的审批请求 #" + id + " 已被批准，但执行失败。");
            }
        }
    }

    private void reject(int id) {
        ApprovalRequest request = this.requestStore.removePending(id);
        if (request == null) {
            sendMessageAsync("找不到待审批请求 #" + id);
            return;
        }
        this.requestStore.recordRejected(request, "Telegram");
        sendMessageAsync("审批请求 #" + id + " 已拒绝。");

        Player requester = this.playerLookup.apply(request.requesterId());
        if (requester != null) {
            requester.sendMessage("§c你的审批请求 #" + id + " 已被拒绝（Telegram）。");
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void sleepSafe(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
