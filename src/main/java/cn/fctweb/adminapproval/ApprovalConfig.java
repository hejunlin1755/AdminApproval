package cn.fctweb.adminapproval;

import java.util.Set;
import java.util.UUID;

public record ApprovalConfig(Set<UUID> ownerUuids) {
}