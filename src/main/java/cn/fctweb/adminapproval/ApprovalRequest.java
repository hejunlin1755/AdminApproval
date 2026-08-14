package cn.fctweb.adminapproval;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRequest(int id, UUID requesterId, String requesterName, String command, Instant createdAt) {
}
