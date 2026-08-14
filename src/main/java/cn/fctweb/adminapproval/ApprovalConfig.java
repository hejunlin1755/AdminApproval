package cn.fctweb.adminapproval;

import java.util.Set;
import java.util.UUID;

public record ApprovalConfig(Set<UUID> ownerUuids, TelegramSettings telegram) {

    public ApprovalConfig(Set<UUID> ownerUuids) {
        this(ownerUuids, TelegramSettings.disabled());
    }
}