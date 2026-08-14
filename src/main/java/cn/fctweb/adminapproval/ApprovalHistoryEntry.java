package cn.fctweb.adminapproval;

import java.time.Instant;

public record ApprovalHistoryEntry(int requestId,
                                   String requesterName,
                                   String command,
                                   String reviewerName,
                                   String action,
                                   boolean executionSuccess,
                                   Instant processedAt) {
}
