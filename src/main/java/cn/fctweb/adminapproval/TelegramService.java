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
 * - 新的审批请求推送给服主，附带「批准/拒绝」内联按钮
 * - 服主点按钮，或在聊天里回复 /approve &lt;编号&gt; / /reject &lt;编号&gt; 均可处理
 * - 只接受配置 chat-id 发来的指令/回调，防他人冒充
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
        sendJsonAsync("sendMessage", buildRequestPayload(this.chatId, request));
    }

    public static String formatApprovalRequest(ApprovalRequest request) {
        return "[AdminApproval] 新的审批请求\n"
                + "编号:#" + request.id() + "\n"
                + "申请人:" + request.requesterName() + "\n"
                + "命令:" + request.command() + "\n"
                + "点击下方按钮处理，或回复 /approve " + request.id() + " / /reject " + request.id();
    }

    /**
     * 构建带「批准/拒绝」内联按钮的 sendMessage 请求体（可测试）。
     */
    public static JsonObject buildRequestPayload(String chatId, ApprovalRequest request) {
        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", chatId);
        payload.addProperty("text", formatApprovalRequest(request));

        JsonArray rows = new JsonArray();
        JsonArray row = new JsonArray();
        row.add(button("✅ 批准 #" + request.id(), "approve:" + request.id()));
        row.add(button("❌ 拒绝 #" + request.id(), "reject:" + request.id()));
        rows.add(row);

        JsonObject keyboard = new JsonObject();
        keyboard.add("inline_keyboard", rows);
        payload.add("reply_markup", keyboard);
        return payload;
    }

    private static JsonObject button(String text, String callbackData) {
        JsonObject b = new JsonObject();
        b.addProperty("text", text);
        b.addProperty("callback_data", callbackData);
        return b;
    }

    private void sendJsonAsync(String method, JsonObject payload) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + this.botToken + "/" + method))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        this.http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        this.logger.warning("Telegram " + method + " 失败: " + err.getMessage());
                    } else if (resp.statusCode() != 200) {
                        this.logger.warning("Telegram " + method + " HTTP " + resp.statusCode() + ": " + resp.body());
                    }
                });
    }

    private void sendMessageAsync(String text) {
        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", this.chatId);
        payload.addProperty("text", text);
        sendJsonAsync("sendMessage", payload);
    }

    private void answerCallback(String callbackId, String text) {
        JsonObject payload = new JsonObject();
        payload.addProperty("callback_query_id", callbackId);
        payload.addProperty("text", text);
        sendJsonAsync("answerCallbackQuery", payload);
    }

    private void editMessage(int messageId, String text) {
        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", this.chatId);
        payload.addProperty("message_id", messageId);
        payload.addProperty("text", text);
        sendJsonAsync("editMessageText", payload);
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

        JsonElement callbackElement = update.get("callback_query");
        if (callbackElement != null && callbackElement.isJsonObject()) {
            handleCallbackQuery(callbackElement.getAsJsonObject());
            return;
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

    private void handleCallbackQuery(JsonObject callback) {
        if (!callback.has("id") || !callback.has("data")) {
            return;
        }
        String callbackId = callback.get("id").getAsString();
        String data = callback.get("data").getAsString();

        long senderId = -1;
        JsonElement fromElement = callback.get("from");
        if (fromElement != null && fromElement.isJsonObject() && fromElement.getAsJsonObject().has("id")) {
            senderId = fromElement.getAsJsonObject().get("id").getAsLong();
        }
        if (!String.valueOf(senderId).equals(this.chatId)) {
            answerCallback(callbackId, "无权操作");
            return;
        }

        int messageId = -1;
        JsonElement messageElement = callback.get("message");
        if (messageElement != null && messageElement.isJsonObject()
                && messageElement.getAsJsonObject().has("message_id")) {
            messageId = messageElement.getAsJsonObject().get("message_id").getAsInt();
        }

        if (data.startsWith("approve:")) {
            String result = approveWithMessage(parseIdPart(data));
            answerCallback(callbackId, result);
            if (messageId > 0) {
                editMessage(messageId, result);
            }
        } else if (data.startsWith("reject:")) {
            String result = rejectWithMessage(parseIdPart(data));
            answerCallback(callbackId, result);
            if (messageId > 0) {
                editMessage(messageId, result);
            }
        } else {
            answerCallback(callbackId, "未知操作");
        }
    }

    private void handleOwnerCommand(String text) {
        if (text.isEmpty()) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int id = parseId(text);
        if (lower.startsWith("/approve ") && id > 0) {
            sendMessageAsync(approveWithMessage(id));
            return;
        }
        if (lower.startsWith("/reject ") && id > 0) {
            sendMessageAsync(rejectWithMessage(id));
            return;
        }
        sendMessageAsync("用法: 点审批消息下方的按钮，或回复 /approve <编号> / /reject <编号>");
    }

    private int parseIdPart(String data) {
        int separator = data.indexOf(':');
        if (separator < 0 || separator == data.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(data.substring(separator + 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
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

    private String approveWithMessage(int id) {
        ApprovalRequest request = this.requestStore.getPending(id);
        if (request == null) {
            return "找不到待审批请求 #" + id;
        }
        this.requestStore.removePending(id);
        boolean success = this.dispatcher.apply(request.command());
        this.requestStore.recordApproved(request, "Telegram", success);

        String result = "✅ 审批请求 #" + id
                + (success ? " 已批准，控制台已执行: /" + request.command() : " 已批准，但命令执行失败");
        notifyRequester(request, success);
        return result;
    }

    private String rejectWithMessage(int id) {
        ApprovalRequest request = this.requestStore.removePending(id);
        if (request == null) {
            return "找不到待审批请求 #" + id;
        }
        this.requestStore.recordRejected(request, "Telegram");
        notifyRequester(request, false);
        return "❌ 审批请求 #" + id + " 已拒绝";
    }

    private void notifyRequester(ApprovalRequest request, boolean success) {
        Player requester = this.playerLookup.apply(request.requesterId());
        if (requester == null) {
            return;
        }
        if (success) {
            requester.sendMessage("§a你的审批请求 #" + request.id() + " 已被批准并执行（Telegram）。");
        } else {
            requester.sendMessage("§e你的审批请求 #" + request.id() + " 已被拒绝（Telegram）。");
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
