package cn.fctweb.adminapproval;

import java.util.Set;

public record CommandSettings(Set<String> dangerous, Set<String> whitelist, Set<String> blockedPatterns,
                              boolean notifyAllAdminCommands, Set<String> sensitiveCommands,
                              Set<String> sensitiveKeywords, boolean notifyPlayerCommands,
                              boolean notifyJoinLeave) {

    public CommandSettings(Set<String> dangerous, Set<String> whitelist) {
        this(dangerous, whitelist, Set.of(), false, Set.of(), Set.of(), false, false);
    }

    public CommandSettings(Set<String> dangerous, Set<String> whitelist, Set<String> blockedPatterns) {
        this(dangerous, whitelist, blockedPatterns, false, Set.of(), Set.of(), false, false);
    }
}
