package cn.fctweb.adminapproval;

public record TelegramSettings(boolean enabled, String botToken, String chatId) {
    public static TelegramSettings disabled() {
        return new TelegramSettings(false, "", "");
    }
}