package cn.fctweb.adminapproval;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 控制台日志监听：尾随 logs/latest.log，匹配反作弊告警并推送到 Telegram。
 * 带频率限制，避免刷屏。
 */
public final class ConsoleWatchService {
    private final Path logFile;
    private final TelegramService telegramService;
    private final List<Pattern> patterns;
    private final long minIntervalMs;
    private final int maxPerMinute;
    private final Logger logger;

    private volatile boolean running;
    private Thread thread;
    private long lastOffset;
    private long lastSentAt;
    private int sentThisMinute;
    private long minuteStart;

    public ConsoleWatchService(Path logFile, TelegramService telegramService, List<Pattern> patterns,
                               long minIntervalMs, int maxPerMinute, Logger logger) {
        this.logFile = logFile;
        this.telegramService = telegramService == null ? TelegramService.disabled() : telegramService;
        this.patterns = patterns == null ? List.of() : patterns;
        this.minIntervalMs = Math.max(500, minIntervalMs);
        this.maxPerMinute = Math.max(1, maxPerMinute);
        this.logger = logger;
    }

    public void start() {
        if (this.running || this.patterns.isEmpty() || !this.telegramService.isEnabled()) {
            return;
        }
        this.running = true;
        this.thread = new Thread(this::loop, "AdminApproval-ConsoleWatch");
        this.thread.setDaemon(true);
        this.thread.start();
        this.logger.info("反作弊告警监听已启用");
    }

    public void stop() {
        this.running = false;
        if (this.thread != null) {
            this.thread.interrupt();
        }
    }

    private void loop() {
        while (this.running) {
            try {
                tick();
            } catch (Exception ignored) {
            }
            sleepSafe(2000);
        }
    }

    private void tick() {
        if (this.logFile == null || !Files.exists(this.logFile)) {
            return;
        }
        long size;
        try {
            size = Files.size(this.logFile);
        } catch (IOException ignored) {
            return;
        }
        if (size < this.lastOffset) {
            this.lastOffset = 0; // 日志轮转
        }
        if (size == this.lastOffset) {
            return;
        }

        long start = this.lastOffset;
        this.lastOffset = size;
        try (RandomAccessFile raf = new RandomAccessFile(this.logFile.toFile(), "r")) {
            raf.seek(start);
            byte[] buffer = new byte[(int) (size - start)];
            raf.readFully(buffer);
            String text = new String(buffer, StandardCharsets.UTF_8);
            for (String line : text.split("\r?\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                for (Pattern pattern : this.patterns) {
                    if (pattern.matcher(trimmed).find()) {
                        sendLimited(trimmed);
                        break;
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void sendLimited(String line) {
        long now = System.currentTimeMillis();
        if (now - this.minuteStart >= 60000) {
            this.minuteStart = now;
            this.sentThisMinute = 0;
        }
        if (this.sentThisMinute >= this.maxPerMinute || now - this.lastSentAt < this.minIntervalMs) {
            return;
        }
        this.lastSentAt = now;
        this.sentThisMinute++;
        this.telegramService.notifyAntiCheat(line);
    }

    private static void sleepSafe(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
