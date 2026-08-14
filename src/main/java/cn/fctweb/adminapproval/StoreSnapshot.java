package cn.fctweb.adminapproval;

import java.util.List;
import java.util.Set;

public record StoreSnapshot(int nextId, List<ApprovalRequest> pending, List<ApprovalHistoryEntry> history,
                            Set<String> whitelist) {

    public StoreSnapshot(int nextId, List<ApprovalRequest> pending, List<ApprovalHistoryEntry> history) {
        this(nextId, pending, history, Set.of());
    }
}