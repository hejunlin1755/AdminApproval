package cn.fctweb.adminapproval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class RequestStore {
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, ApprovalRequest> pending = new ConcurrentHashMap<>();
    private final List<ApprovalHistoryEntry> history = new ArrayList<>();
    private final int historyLimit;

    private Runnable saveHook = () -> {
    };

    public RequestStore(int historyLimit) {
        this.historyLimit = Math.max(1, historyLimit);
    }

    public void setSaveHook(Runnable saveHook) {
        this.saveHook = saveHook == null ? () -> {
        } : saveHook;
    }

    public synchronized void load(StoreSnapshot snapshot) {
        this.pending.clear();
        this.history.clear();

        for (ApprovalRequest request : snapshot.pending()) {
            this.pending.put(request.id(), request);
        }

        this.history.addAll(snapshot.history());
        this.history.sort(Comparator.comparing(ApprovalHistoryEntry::processedAt).reversed());

        int maxSeenId = this.pending.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int nextId = Math.max(snapshot.nextId(), maxSeenId + 1);
        this.nextId.set(Math.max(1, nextId));
    }

    public synchronized StoreSnapshot snapshot() {
        return new StoreSnapshot(
                this.nextId.get(),
                listPending(),
                listHistory()
        );
    }

    public synchronized ApprovalRequest create(UUID requesterId, String requesterName, String command) {
        int id = this.nextId.getAndIncrement();
        ApprovalRequest request = new ApprovalRequest(id, requesterId, requesterName, command, Instant.now());
        this.pending.put(id, request);
        this.saveHook.run();
        return request;
    }

    public synchronized ApprovalRequest getPending(int id) {
        return this.pending.get(id);
    }

    public synchronized ApprovalRequest removePending(int id) {
        ApprovalRequest removed = this.pending.remove(id);
        if (removed != null) {
            this.saveHook.run();
        }
        return removed;
    }

    public synchronized void recordApproved(ApprovalRequest request, String reviewerName, boolean executionSuccess) {
        this.history.add(0, new ApprovalHistoryEntry(
                request.id(),
                request.requesterName(),
                request.command(),
                reviewerName,
                "APPROVED",
                executionSuccess,
                Instant.now()
        ));
        trimHistory();
        this.saveHook.run();
    }

    public synchronized void recordRejected(ApprovalRequest request, String reviewerName) {
        this.history.add(0, new ApprovalHistoryEntry(
                request.id(),
                request.requesterName(),
                request.command(),
                reviewerName,
                "REJECTED",
                false,
                Instant.now()
        ));
        trimHistory();
        this.saveHook.run();
    }

    public synchronized List<ApprovalRequest> listPending() {
        List<ApprovalRequest> result = new ArrayList<>(this.pending.values());
        result.sort(Comparator.comparingInt(ApprovalRequest::id));
        return result;
    }

    public synchronized List<ApprovalHistoryEntry> listHistory() {
        return new ArrayList<>(this.history);
    }

    private void trimHistory() {
        if (this.history.size() <= this.historyLimit) {
            return;
        }
        this.history.subList(this.historyLimit, this.history.size()).clear();
    }
}